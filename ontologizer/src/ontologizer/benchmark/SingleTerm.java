package ontologizer.benchmark;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import ontologizer.ByteString;
import ontologizer.GODOTWriter;
import ontologizer.GOTermEnumerator;
import ontologizer.GlobalPreferences;
import ontologizer.IDotNodeAttributesProvider;
import ontologizer.OntologizerThreadGroups;
import ontologizer.PopulationSet;
import ontologizer.StudySet;
import ontologizer.association.AssociationContainer;
import ontologizer.calculation.AbstractGOTermProperties;
import ontologizer.calculation.EnrichedGOTermsResult;
import ontologizer.calculation.ICalculation;
import ontologizer.calculation.TermForTermCalculation;
import ontologizer.go.GOGraph;
import ontologizer.go.Term;
import ontologizer.go.TermID;
import ontologizer.gui.swt.result.EnrichedGOTermsResultLatexWriter;
import ontologizer.statistics.Bonferroni;

class B2GTestParameter
{
	static double ALPHA = 0.20;
	static double BETA = 0.20;
	static int MCMC_STEPS = 1020000;
}

/**
 * This class implements an model-based analysis.
 * The description of this method can be found at XXXX.
 *
 * @author Sebastian Bauer
 */
public class SingleTerm
{
	private static AssociationContainer assoc;
	private static GOGraph graph;

	/**
	 * A test procedure.
	 *
	 * @param args
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public static void main(String[] args) throws InterruptedException, IOException
	{
		GlobalPreferences.setProxyPort(888);
		GlobalPreferences.setProxyHost("realproxy.charite.de");

		String oboPath = "http://www.geneontology.org/ontology/gene_ontology_edit.obo";
		String assocPath = "http://cvsweb.geneontology.org/cgi-bin/cvsweb.cgi/go/gene-associations/gene_association.sgd.gz?rev=HEAD";

		Datafiles df = new Datafiles(oboPath,assocPath);
		assoc = df.assoc;
		graph = df.graph;

		/* Restrict the graph */
		graph.setRelevantSubontology("biological_process");

		/* ***************************************************************** */

		Set<ByteString> allAnnotatedGenes = assoc.getAllAnnotatedGenes();

		final PopulationSet completePop = new PopulationSet();
		completePop.setName("AllAnnotated");
		for (ByteString gene : allAnnotatedGenes)
			completePop.addGene(gene,"None");
		completePop.filterOutAssociationlessGenes(assoc);
//		completePop.countGOTerms(graph, assoc);

		/* ***************************************************************** */

		final GOTermEnumerator completePopEnumerator = completePop.enumerateGOTerms(graph, assoc);

//		for (TermID tid : completePopEnumerator)
//		{
////			if (tid.id == 8150)
//				System.out.println(tid + " " + completePopEnumerator.getAnnotatedGenes(tid).totalAnnotatedCount() + " " + graph.getGOTerm(tid).getNamespaceAsString());
//		}

		final ByteString [] allGenesArray = completePop.getGenes();
		final TermID root = graph.getGOTerm("GO:0008150").getID();
		final int rootGenes = completePopEnumerator.getAnnotatedGenes(root).totalAnnotatedCount();

		System.out.println("Population set consits of " + allGenesArray.length + " genes. Root term has " + rootGenes + " associated genes");
		if (allGenesArray.length != rootGenes)
		{
			System.out.println("Gene count doesn't match! Aborting.");
			System.exit(-1);
		}

		/* ***************************************************************** */

		final HashMap<TermID,Double> wantedActiveTerms = new HashMap<TermID,Double>(); /* Terms that are active */


//		wantedActiveTerms.put(new TermID("GO:0007049"), B2GTestParameter.BETA2); /* cell cycle */
//		wantedActiveTerms.put(new TermID("GO:0043473"), B2GTestParameter.BETA2); /* pigmentation */
//		wantedActiveTerms.put(new TermID("GO:0001505"), B2GTestParameter.BETA); /* regulation of neuro transmitter levels */
//////		wantedActiveTerms.put(new TermID("GO:0008078"), B2GTestParameter.BETA); /* mesodermal cell migration */
//////		wantedActiveTerms.put(new TermID("GO:0051208"), B2GTestParameter.BETA); /* sequestering of calcium ion */
//		wantedActiveTerms.put(new TermID("GO:0030011"), B2GTestParameter.BETA); /* maintenace of cell polarity */
//////		wantedActiveTerms.put(new TermID("GO:0035237"), B2GTestParameter.BETA); /* corazonin receptor activity */

		wantedActiveTerms.put(new TermID("GO:0051179"),B2GTestParameter.BETA); // localization
//		wantedActiveTerms.put(new TermID("GO:0009880"),B2GTestParameter.BETA);



//		createInternalOntology(1);
//		wantedActiveTerms.add(new TermID("GO:0000010"));
//		wantedActiveTerms.add(new TermID("GO:0000004"));

		/* ***************************************************************** */

//		Random rnd = new Random(10);
//		Random rnd = new Random(11); /* Produces a set */
		Random rnd = new Random(11);

		/* Simulation */

//		PopulationSet allGenes = new PopulationSet("all");
//		for (ByteString gene : assoc.getAllAnnotatedGenes())
//			allGenes.addGene(gene, "");
//
//		System.out.println("Total number of genes " + allGenes);
//
		HashMap<TermID,StudySet> wantedActiveTerm2StudySet = new HashMap<TermID,StudySet>();
//
//		final GOTermEnumerator allEnumerator = allGenes.enumerateGOTerms(graph, assoc);
//
//		System.out.println("Considering a total of " + allEnumerator.getAllAnnotatedTermsAsList().size() + " terms");

		for (TermID t : wantedActiveTerms.keySet())
		{
			if (graph.getGOTerm(t) == null)
				throw new IllegalArgumentException("Term " + t.toString() + " not found");
			StudySet termStudySet = new StudySet("study");
			for (ByteString g : completePopEnumerator.getAnnotatedGenes(t).totalAnnotated)
				termStudySet.addGene(g, "");
			termStudySet.filterOutDuplicateGenes(assoc);
			wantedActiveTerm2StudySet.put(t, termStudySet);
		}

		StudySet newStudyGenes = new StudySet("study");
		for (TermID t : wantedActiveTerms.keySet())
		{
			System.out.println(t.toString() + " genes=" + wantedActiveTerm2StudySet.get(t).getGeneCount() + " beta=" + wantedActiveTerms.get(t));

			newStudyGenes.addGenes(wantedActiveTerm2StudySet.get(t));
		}

		newStudyGenes.filterOutDuplicateGenes(assoc);

		System.out.println("Number of genes in study set " + newStudyGenes.getGeneCount());

		double alphaStudySet = B2GTestParameter.ALPHA;
		double betaStudySet = B2GTestParameter.BETA;

		int tp = newStudyGenes.getGeneCount();
		int tn = completePop.getGeneCount();

		/* Obfuscate the study set, i.e., create the observed state */

		/* false -> true (alpha, false positive) */
		HashSet<ByteString>  fp = new HashSet<ByteString>();
		for (ByteString gene : completePop)
		{
			if (newStudyGenes.contains(gene)) continue;
			if (rnd.nextDouble() < alphaStudySet) fp.add(gene);
		}

		/* true -> false (beta, false negative) */
		HashSet<ByteString>  fn = new HashSet<ByteString>();
		for (ByteString gene : newStudyGenes)
		{
			if (rnd.nextDouble() < betaStudySet) fn.add(gene);
		}
		newStudyGenes.addGenes(fp);
		newStudyGenes.removeGenes(fn);

		double realAlpha = ((double)fp.size())/tn;
		double realBeta = ((double)fn.size())/tp;

		System.out.println("Study set has " + fp.size() + " false positives (alpha=" + realAlpha +")");
		System.out.println("Study set has " + fn.size() + " false negatives (beta=" + realBeta +")");
		System.out.println("Study set has a total of " +  newStudyGenes.getGeneCount() + " genes");

		/**** Write out the graph ****/
		//{
			HashSet<TermID> allTermIDs = new HashSet<TermID>();
			for (Term t : graph)
				allTermIDs.add(t.getID());

			final GOTermEnumerator studySetEnumerator = newStudyGenes.enumerateGOTerms(graph, assoc);

			GODOTWriter.writeDOT(graph, new File("toy-all.dot"), null, allTermIDs, new IDotNodeAttributesProvider()
			{
				public String getDotNodeAttributes(TermID id)
				{
					StringBuilder str = new StringBuilder(200);
					str.append("label=\"");
					str.append(graph.getGOTerm(id).getName());
					str.append("\\n");
					str.append(studySetEnumerator.getAnnotatedGenes(id).totalAnnotatedCount() + "/" + completePopEnumerator.getAnnotatedGenes(id).totalAnnotatedCount());
					str.append("\"");
					if (wantedActiveTerms.containsKey(id))
					{
						str.append("style=\"filled\" color=\"gray\"");
					}
					return str.toString();
				}
			});

			GODOTWriter.writeDOT(graph, new File("toy-induced.dot"), null, wantedActiveTerms.keySet(), new IDotNodeAttributesProvider()
			{
				public String getDotNodeAttributes(TermID id)
				{
					StringBuilder str = new StringBuilder(200);
					str.append("label=\"");
					str.append(graph.getGOTerm(id).getName());
					str.append("\\n");
					str.append(studySetEnumerator.getAnnotatedGenes(id).totalAnnotatedCount() + "/" + completePopEnumerator.getAnnotatedGenes(id).totalAnnotatedCount());
					str.append("\"");
					if (wantedActiveTerms.containsKey(id))
					{
						str.append("style=\"filled\" color=\"gray\"");
					}
					return str.toString();
				}
			});

		//}

//		double p = (double)wantedActiveTerms.size() / allEnumerator.getTotalNumberOfAnnotatedTerms();
//
//		ProbabilisticCalculation calc = new ProbabilisticCalculation();
//		calc.setDefaultP(1- realBeta);
//		calc.setDefaultQ(realAlpha);

//		TopologyWeightedCalculation calc = new TopologyWeightedCalculation();
		TermForTermCalculation calc = new TermForTermCalculation();
//		ParentChildCalculation calc = new ParentChildCalculation();
//		Bayes2GOCalculation calc = new Bayes2GOCalculation();
//		calc.setSeed(2); /* Finds a optimum */
//		calc.setSeed(3); /* with basement membrane, score 6826.695 */
//		calc.setSeed(4); /* Optimum, score 6826.039 */

////		calc.setAlpha(B2GParam.Type.MCMC);
////		calc.setBeta(B2GParam.Type.MCMC);
////		calc.setExpectedNumber(B2GParam.Type.MCMC);
//		calc.setAlpha(realAlpha);
//		calc.setBeta(realBeta);
//		calc.setExpectedNumber(wantedActiveTerms.size());

//		calc.setMcmcSteps(520000);
//		calc.setAlpha(B2GParam.Type.MCMC);
//		calc.setBeta(B2GParam.Type.MCMC);
//		calc.setExpectedNumber(B2GParam.Type.MCMC);

//		calc.setAlpha(B2GParam.Type.EM);
//		calc.setBeta(B2GParam.Type.EM);
//		calc.setExpectedNumber(B2GParam.Type.EM);

////	calc.setUsePrior(false);
//		calc.setAlpha(alphaStudySet);
//		calc.setBeta(betaStudySet);


		evaluate(wantedActiveTerms, completePop, newStudyGenes, completePopEnumerator, studySetEnumerator, calc);

//		/* Draw the basic example figure */
//		final int MAX_ITER = 20;
//
//		long rank_sum = 0;
//		double marg_sum = 0;
//
//		double [] marg = new double[MAX_ITER];
//		int [] rank = new int[MAX_ITER];
//		long [] seed = new long[MAX_ITER];
//
//		final HashMap<TermID,Double> t2marg = new HashMap<TermID,Double>();
////		int [][] allMargs = null;
//
//		Random seedRandom = new Random();
//
//		for (int i=0;i<MAX_ITER;i++)
//		{
//			seed[i] = seedRandom.nextLong();
//
//			Bayes2GOCalculation calc2 = new Bayes2GOCalculation();
//			calc2.setSeed(seed[i]);
//			calc2.setAlpha(realAlpha);
//			calc2.setBeta(realBeta);
//			calc2.setExpectedNumber(wantedActiveTerms.size());
//
//			EnrichedGOTermsResult result = calc2.calculateStudySet(graph, assoc, allGenes, newStudyGenes, new None());
//
//			ArrayList<AbstractGOTermProperties> resultList = new ArrayList<AbstractGOTermProperties>();
//
//			for (AbstractGOTermProperties prop : result)
//			{
//				double tMarg = 1 - prop.p;
//				Double cMarg = t2marg.get(prop.goTerm.getID());
//				if (cMarg != null)
//					tMarg += cMarg;
//				t2marg.put(prop.goTerm.getID(), tMarg);
//
//				resultList.add(prop);
//			}
//			Collections.sort(resultList);
//
//			/* Determine the rank of a special term */
//			int r = 1;
//			for (AbstractGOTermProperties prop : resultList)
//			{
//				if (prop.goTerm.getID().id == 30011)
//				{
//					marg[i] = (1 - prop.p);
//					rank[i] = r;
//					break;
//				}
//				r++;
//			}
//		}
//
//		System.out.println("rank\tmarg\tseed");
//		for (int i=0;i<MAX_ITER;i++)
//			System.out.println(rank[i] + "\t" + marg[i] + "\t" + seed[i]);
//
//		GODOTWriter.writeDOT(graph, new File("toy-result-avg.dot"), null, wantedActiveTerms.keySet(), new IDotNodeAttributesProvider()
//		{
//			public String getDotNodeAttributes(TermID id)
//			{
//				StringBuilder str = new StringBuilder(200);
//				str.append("label=\"");
//
//				str.append(Util.wrapLine(graph.getGOTerm(id).getName(),"\\n",20));
//
//				str.append("\\n");
//				str.append(studySetEnumerator.getAnnotatedGenes(id).totalAnnotatedCount() + "/" + allEnumerator.getAnnotatedGenes(id).totalAnnotatedCount());
//				str.append("\\n");
//				if (t2marg.get(id) != null)
//					str.append(String.format("P(T=1)=%g", ((Double)t2marg.get(id)/ MAX_ITER)));
//
//				str.append("\"");
//				if (wantedActiveTerms.containsKey(id))
//				{
//					str.append("style=\"filled\" color=\"gray\"");
//				}
//
////				if (result.getGOTermProperties(id) != null && result.getGOTermProperties(id).p_adjusted < 0.999)
////				{
////					str.append(" penwidth=\"2\"");
////				}
//				return str.toString();
//			}
//		});
		OntologizerThreadGroups.workerThreadGroup.interrupt();
	}

	private static void evaluate(final HashMap<TermID,Double> wantedActiveTerms,
			PopulationSet allGenes, StudySet newStudyGenes,
			final GOTermEnumerator allEnumerator,
			final GOTermEnumerator studySetEnumerator,
			ICalculation calc)
	{
		final EnrichedGOTermsResult result = calc.calculateStudySet(graph, assoc, allGenes, newStudyGenes, new Bonferroni());

		TermForTermCalculation tft = new TermForTermCalculation();
		EnrichedGOTermsResult r2 = tft.calculateStudySet(graph, assoc, allGenes, newStudyGenes, new Bonferroni());
		HashSet<TermID> s = new HashSet<TermID>();
		for (AbstractGOTermProperties p2 : r2)
			s.add(p2.goTerm.getID());
		int cnt = 0;
		for (AbstractGOTermProperties prop : result)
		{
			if (!s.contains(prop.goTerm.getID()))
			{
//				System.out.println(prop.annotatedPopulationGenes + "  " + prop.annotatedStudyGenes);
				cnt++;
			}
		}
		System.out.println("There are " + cnt + " terms to which none of the genes of the study set are annotated.");
		System.out.println("Method is " + calc.getName());
		System.out.println("We have a statement over a total of " + result.getSize() + " terms.");

		/*** Calculate the score of the given term set ***/

		ArrayList<AbstractGOTermProperties> resultList = new ArrayList<AbstractGOTermProperties>();
		for (AbstractGOTermProperties prop : result)
			resultList.add(prop);
		Collections.sort(resultList);

//		ArrayList<AbstractGOTermProperties> interestingList = new ArrayList<AbstractGOTermProperties>();
//		System.out.println("The overrepresented terms:");
//		for (TermID w : wantedActiveTerms)
//		{
//			AbstractGOTermProperties prop = result.getGOTermProperties(w);
//			if (prop!=null)
//				System.out.println(" " + prop.goTerm.getIDAsString() + "/" + prop.goTerm.getName() + "   " + (/*1.0f - */prop.p_adjusted) + ")");
//			else
//				System.out.println(w.toString() + " not found");
//		}

		{
//			System.out.println("The terms found by the algorithm:");
			HashSet<TermID> terms = new HashSet<TermID>();

			System.out.println("The overrepresented terms:");

			int rank = 1;
			for (AbstractGOTermProperties prop : resultList)
			{
				if (wantedActiveTerms.containsKey(prop.goTerm.getID()))
					System.out.println(" " + prop.goTerm.getIDAsString() + "/" + prop.goTerm.getName() + "   " + (/*1.0f - */prop.p_adjusted) + " rank=" + rank + " beta=" + wantedActiveTerms.get(prop.goTerm.getID()));
				rank++;
			}

			System.out.println("The terms found by the algorithm:");

			int numSig = 0;
			rank = 1;
			for (AbstractGOTermProperties prop : resultList)
			{
				boolean propIsChild = false;

				for (TermID wanted : wantedActiveTerms.keySet())
				{
					if (graph.getTermsDescendants(wanted).contains(prop.goTerm.getID()))
					{
						propIsChild = true;
						break;
					}
				}

				if (prop.isSignificant(0.05))
				{
					if (rank <= 10 || propIsChild)
					{
						terms.add(prop.goTerm.getID());
						System.out.println(" " + prop.goTerm.getIDAsString() + "/" + prop.goTerm.getName() + "   " + (/*1.0f - */prop.p_adjusted) + " rank=" + rank);
					}

					numSig++;
				}
				rank++;
			}

			System.out.println("A total of " + numSig + " terms where significant");

			terms.addAll(wantedActiveTerms.keySet());

			String preamble = "d2tfigpreamble=\"\\pgfdeclareradialshading{verylightsphere}{\\pgfpoint{-0.5cm}{0.5cm}}{rgb(0cm)=(0.99,0.99,0.99);rgb(0.7cm)=(0.96,0.96,0.96);rgb(1cm)=(0.93,0.93,0.93);rgb(1.05cm)=(1,1,1)}"+
			                                   "\\pgfdeclareradialshading{lightsphere}{\\pgfpoint{-0.5cm}{0.5cm}}{rgb(0cm)=(0.99,0.99,0.99);rgb(0.7cm)=(0.95,0.95,0.95);rgb(1cm)=(0.9,0.9,0.9);rgb(1.05cm)=(1,1,1)}"+
			                                   "\\pgfdeclareradialshading{darksphere}{\\pgfpoint{-0.5cm}{0.5cm}}{gray(0cm)=(0.95);gray(0.7cm)=(0.9);gray(1cm)=(0.85);gray(2cm)=(0.85)}"+
			                                   "\\pgfdeclareradialshading{verydarksphere}{\\pgfpoint{-0.5cm}{0.5cm}}{gray(0cm)=(0.9);gray(0.7cm)=(0.85);gray(1cm)=(0.8);gray(2cm)=(0.8)}\"";

			GODOTWriter.writeDOT(graph, new File("localization-tft.dot"), graph.getRelevantSubontology(), terms, new IDotNodeAttributesProvider()
			{
				public String getDotNodeAttributes(TermID id)
				{
					double val = result.getGOTermProperties(id).p_adjusted;

					StringBuilder str = new StringBuilder(200);
					str.append("margin=\"0\" shape=\"box\" label=\"");
					str.append("\\scriptsize$\\begin{array}{c}");

					str.append("\\emph{");
					if (wantedActiveTerms.containsKey(id))
						str.append("\\underline{");
					str.append(graph.getGOTerm(id).getName().replace("_", " "));
					if (wantedActiveTerms.containsKey(id))
						str.append("}");
					str.append("} \\\\\\ ");
					str.append(studySetEnumerator.getAnnotatedGenes(id).totalAnnotatedCount() + "/" + allEnumerator.getAnnotatedGenes(id).totalAnnotatedCount() + " \\\\\\ ");

					AbstractGOTermProperties prop = result.getGOTermProperties(id);
					if (prop != null)
					{
						String valStr = EnrichedGOTermsResultLatexWriter.toLatex(val);
						if (!valStr.startsWith("<")) valStr = "=" + valStr;
						str.append("p");
						str.append(valStr);
					}

					str.append("\\end{array}$");
					str.append("\"");

					if (wantedActiveTerms.containsKey(id))
					{
						str.append("style=\"rounded corners,top color=white,bottom color=black!15,draw=black!50,very thick\"");
					} else
					{
						if (prop.isSignificant(0.05))
						{
							str.append("style=\"rounded corners,top color=white,bottom color=black!15,draw=black!50,very thick\"");
						} else
						{
							str.append("style=\"rounded corners,color=black!50\"");
						}


					}

//					if (result.getGOTermProperties(id) != null && result.getGOTermProperties(id).p_adjusted < 0.999)
//					{
//						str.append(" penwidth=\"2\"");
//					}
					return str.toString();
				}
			},
			"nodesep=0.05; ranksep=0.1;" + preamble,false,false);

		}
	}


}