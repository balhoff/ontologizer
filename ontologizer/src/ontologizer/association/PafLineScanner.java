package ontologizer.association;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import ontologizer.association.AssociationParser.Type;
import ontologizer.go.IParserInput;
import ontologizer.go.PrefixPool;
import ontologizer.go.Term;
import ontologizer.go.TermID;
import ontologizer.go.TermMap;
import ontologizer.types.ByteString;

/**
 * A modified GAF-scanner to better handle phenotype annotation files.
 * 
 * @author Sebastian Koehler
 *
 */
public class PafLineScanner {

	private static Logger logger = Logger.getLogger(GAFByteLineScanner.class.getName());

	/** The wrapped input */
	private IParserInput input;

	/** Contains all items whose associations should gathered or null if all should be gathered */
	private Set<ByteString> names;

	/** All known terms */
	private TermMap terms;

	/** Set of evidences that shall be considered or null if all should be considered */
	private Set<ByteString> evidences;

	/** Monitor progress */
	private IAssociationParserProgress progress;

	private long millis = 0;
	public int good = 0;
	public int bad = 0;
	public int skipped = 0;
	public int nots = 0;
	public int evidenceMismatch = 0;
	public int kept = 0;
	public int obsolete = 0;
	private int symbolWarnings = 0;
	private int dbObjectWarnings = 0;

	/** Mapping from gene (or gene product) names to Association objects */
	private ArrayList<Association> associations = new ArrayList<Association>();

	/** Our prefix pool */
	private PrefixPool prefixPool = new PrefixPool();

	/** Items as identified by the object symbol to the list of associations */
	private HashMap<ByteString, ArrayList<Association>> itemId2associations = new HashMap<ByteString, ArrayList<Association>>();

	/** key: synonym, value: main gene name (dbObject_Symbol) */
	private HashMap<ByteString, ByteString> synonym2gene = new HashMap<ByteString, ByteString>();

	/** key: dbObjectID, value: main gene name (dbObject_Symbol) */
	private HashMap<ByteString, ByteString> itemId2disease = new HashMap<ByteString, ByteString>();

	private HashMap<ByteString, ByteString> itemId2itemName = new HashMap<ByteString, ByteString>();
//	private HashMap<ByteString, ByteString> itemName2itemId = new HashMap<ByteString, ByteString>();
	private HashMap<TermID, Term> altTermID2Term = null;
	private HashSet<TermID> usedTerms = new HashSet<TermID>();

	public PafLineScanner(IParserInput input, byte[] head, Set<ByteString> names, TermMap terms, Set<ByteString> evidences,
			IAssociationParserProgress progress) {

		this.input = input;
		this.names = names;
		this.terms = terms;
		this.evidences = evidences;
		this.progress = progress;
	}

	public void scan() {
		try {
			BufferedReader in = new BufferedReader(new FileReader(input.getFilename()));
			String line = null;
			associations = new ArrayList<Association>();
			synonym2gene = new HashMap<ByteString, ByteString>();
			itemId2disease = new HashMap<ByteString, ByteString>();

			int lineNumber = 0;
			while ((line = in.readLine()) != null) {

			    	++lineNumber;
				try {
					Association assoc = new Association(line, Type.PAF);
					boolean addedAssociation = processParsedAssociation(assoc, lineNumber);

				} catch (Exception e) {
					throw new RuntimeException("processing error for PAF file line: " + line);
				}
			}
			in.close();
		} catch (IOException e) {
			throw new RuntimeException("IOerror processing PAF file: " + e);
		}
	}

	private boolean processParsedAssociation(Association assoc, int lineNumber) {
		try {
			TermID currentTermID = assoc.getTermID();

			Term currentTerm;

			good++;

			if (assoc.hasNotQualifier()) {
				skipped++;
				nots++;
				return true;
			}

			if (evidences != null) {
				/*
				 * Skip if evidence of the annotation was not supplied as argument
				 */
				if (!evidences.contains(assoc.getEvidence())) {
					skipped++;
					evidenceMismatch++;
					return true;
				}
			}

			currentTerm = terms.get(currentTermID);
			if (currentTerm == null) {
				if (altTermID2Term == null) {
					/* Create the alternative ID to Term map */
					altTermID2Term = new HashMap<TermID, Term>();

					for (Term t : terms)
						for (TermID altID : t.getAlternatives())
							altTermID2Term.put(altID, t);
				}

				/* Try to find the term among the alternative terms before giving up. */
				currentTerm = altTermID2Term.get(currentTermID);
				if (currentTerm == null) {
					System.err.println("Skipping association of item \"" + assoc.getObjectSymbol() + "\" to " + currentTermID
							+ " because the term was not found!");
					System.err.println("(Are the obo file and the association " + "file both up-to-date?)");
					skipped++;
					return true;
				}
				else {
					/* Okay, found, so set the new attributes */
					currentTermID = currentTerm.getID();
					assoc.setTermID(currentTermID);
				}
			}
			else {
				/* Reset the term id so a unique id is used */
				currentTermID = currentTerm.getID();
				assoc.setTermID(currentTermID);
			}

			usedTerms.add(currentTermID);

			if (currentTerm.isObsolete()) {
				System.err.println(
						"Skipping association of item \"" + assoc.getObjectSymbol() + "\" to " + currentTermID + " because term is obsolete!");
				System.err.println("(Are the obo file and the association file in sync?)");
				skipped++;
				obsolete++;
				return true;
			}

			ByteString[] synonyms;

			/* populate synonym string field */
			if (assoc.getSynonym() != null && assoc.getSynonym().length() > 2) {
				/* Note that there can be multiple synonyms, separated by a pipe */
				synonyms = assoc.getSynonym().splitBySingleChar('|');
			}
			else
				synonyms = null;

			if (names != null) {
				/* We are only interested in associations to given genes */
				boolean keep = false;

				/* Check if synonyms are contained */
				if (synonyms != null) {
					for (int i = 0; i < synonyms.length; i++) {
						if (names.contains(synonyms[i])) {
							keep = true;
							break;
						}
					}
				}

				if (keep || names.contains(assoc.getObjectSymbol()) || names.contains(assoc.getDB_Object())) {
					kept++;
				}
				else {
					skipped++;
					return true;
				}
			}
			else {
				kept++;
			}

			if (synonyms != null) {
				for (int i = 0; i < synonyms.length; i++)
					synonym2gene.put(synonyms[i], assoc.getObjectSymbol());
			}

			{
				ByteString itemName = itemId2itemName.get(assoc.getDB_Object());
				if (itemName == null) {
					itemId2itemName.put(assoc.getDB_Object(), assoc.getObjectSymbol());
				}
				else {
					if (!itemName.equals(assoc.getObjectSymbol())) {
						dbObjectWarnings++;
						if (dbObjectWarnings < 1000) {
							logger.warning("Line " + lineNumber + ": Expected that dbObject \"" + assoc.getDB_Object() + "\" maps to symbol \""
									+ itemName + "\" but it maps to \"" + assoc.getObjectSymbol() + "\"");
						}
					}

				}

			}

			/* Add the Association to ArrayList */
			associations.add(assoc);

			ArrayList<Association> gassociations = itemId2associations.get(assoc.getDB_Object());
			if (gassociations == null) {
				gassociations = new ArrayList<Association>();
				itemId2associations.put(assoc.getDB_Object(), gassociations);
			}
			gassociations.add(assoc);

			/* dbObject2Gene has a mapping from dbObjects to gene names */
			itemId2disease.put(assoc.getDB_Object(), assoc.getObjectSymbol());
			
		} catch (Exception ex) {
			ex.printStackTrace();
			bad++;
			System.err.println("Nonfatal error: " + "malformed line in association file \n" + /* associationFile + */"\nCould not parse line "
					+ lineNumber + "\n" + ex.getMessage() + "\n");
		}

		return true;
	}

	/**
	 * @return the number of terms used by the import.
	 */
	public int getNumberOfUsedTerms() {
		return usedTerms.size();
	}

	public ArrayList<Association> getAssociations() {
		return associations;
	}

	public HashMap<ByteString, ByteString> getSynonym2Gene() {
		return synonym2gene;
	}

	public HashMap<ByteString, ByteString> getItemId2disease() {
		return itemId2disease;
	}
	

}
