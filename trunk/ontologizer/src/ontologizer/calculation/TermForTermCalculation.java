package ontologizer.calculation;

import ontologizer.GOTermCounter;
import ontologizer.GOTermEnumerator;
import ontologizer.PopulationSet;
import ontologizer.StudySet;
import ontologizer.association.AssociationContainer;
import ontologizer.go.GOGraph;
import ontologizer.go.TermID;
import ontologizer.statistics.AbstractTestCorrection;
import ontologizer.statistics.IPValueCalculation;
import ontologizer.statistics.PValue;

/**
 *
 * Significant test using a simple independent Fisher Exact
 * calculation for every single term.
 *
 * @author Sebastian Bauer
 */
public class TermForTermCalculation extends AbstractHypergeometricCalculation
{
	public String getName()
	{
		return "Term-For-Term";
	}

	public String getDescription()
	{
		return "No description yet";
	}

	/**
	 * Start calculation based on fisher exact test of the given study.
	 * @param graph
	 * @param goAssociations
	 * @param populationSet
	 * @param studySet
	 *
	 * @return
	 */
	public EnrichedGOTermsResult calculateStudySet(
			GOGraph graph,
			AssociationContainer goAssociations,
			PopulationSet populationSet,
			StudySet studySet,
			AbstractTestCorrection testCorrection)
	{
		EnrichedGOTermsResult studySetResult = new EnrichedGOTermsResult(graph, goAssociations, studySet, populationSet.getGeneCount());
		studySetResult.setCalculationName(this.getName());
		studySetResult.setCorrectionName(testCorrection.getName());

		/**
		 *
		 * This class hides all the details about how the p values are calculated
		 * from the multiple test correction.
		 *
		 * @author Sebastian Bauer
		 *
		 */
		class SinglePValuesCalculation implements IPValueCalculation
		{
			public PopulationSet populationSet;
			public StudySet observedStudySet;
			public AssociationContainer goAssociations;
			public GOGraph graph;

			private PValue [] calculatePValues(StudySet studySet)
			{
//				GOTermCounter studyTermCounter = studySet.countGOTerms(graph,goAssociations);
//				GOTermCounter populationTermCounter = populationSet.countGOTerms(graph,goAssociations);

				GOTermEnumerator studyTermEnumerator = studySet.enumerateGOTerms(graph, goAssociations);
				GOTermEnumerator populationTermEnumerator = populationSet.enumerateGOTerms(graph, goAssociations);

//				System.out.println(" studyCount="+studyTermCounter.getTotalNumberOfAnnotatedTerms() + " studyEnum=" + studyTermEnumerator.getTotalNumberOfAnnotatedTerms());

				int i = 0;

				PValue p [] = new PValue[studyTermEnumerator.getTotalNumberOfAnnotatedTerms()];//populationTermCounter.getTotalNumberOfAnnotatedTerms()];
				TermForTermGOTermProperties myP;

				for(TermID term : studyTermEnumerator)//populationTermCounter)
				{
					int goidAnnotatedPopGeneCount = populationTermEnumerator.getAnnotatedGenes(term).totalAnnotatedCount();//populationTermCounter.getCount(term);
					int popGeneCount = populationSet.getGeneCount();
					int studyGeneCount = studySet.getGeneCount();
					int goidAnnotatedStudyGeneCount = studyTermEnumerator.getAnnotatedGenes(term).totalAnnotatedCount();//studyTermCounter.getCount(term);

					myP = new TermForTermGOTermProperties();
					myP.goTerm = graph.getGOTerm(term);
					myP.annotatedStudyGenes = goidAnnotatedStudyGeneCount;
					myP.annotatedPopulationGenes = goidAnnotatedPopGeneCount;

					if (goidAnnotatedStudyGeneCount != 0)
					{
						/* Imagine the following...
						 *
						 * In an urn you put popGeneCount number of balls where a color of a
						 * ball can be white or black. The number of balls having white color
						 * is goidAnnontatedPopGeneCount (all genes of the population which
						 * are annotated by the current GOID).
						 *
						 * You choose to draw studyGeneCount number of balls without replacement.
						 * How big is the probability, that you got goidAnnotatedStudyGeneCount
						 * white balls after the whole drawing process?
						 */

						myP.p = hyperg.phypergeometric(popGeneCount, (double)goidAnnotatedPopGeneCount / (double)popGeneCount,
								studyGeneCount, goidAnnotatedStudyGeneCount);
						myP.p_min = hyperg.dhyper(
								goidAnnotatedPopGeneCount,
								popGeneCount,
								goidAnnotatedPopGeneCount,
								goidAnnotatedPopGeneCount);
					} else
					{
						/* Mark this p value as irrelevant so it isn't considered in a mtc */
						myP.p = 1.0;
						myP.ignoreAtMTC = true;
						myP.p_min = 1.0;
					}

					p[i++] = myP;
				}
				return p;
			}

			public PValue[] calculateRawPValues()
			{
				return calculatePValues(observedStudySet);
			}

			public int currentStudySetSize()
			{
				return observedStudySet.getGeneCount();
			}

			public PValue[] calculateRandomPValues()
			{
				return calculatePValues(populationSet.generateRandomStudySet(observedStudySet.getGeneCount()));
			}
		};

		SinglePValuesCalculation pValueCalculation = new SinglePValuesCalculation();
		pValueCalculation.goAssociations = goAssociations;
		pValueCalculation.graph = graph;
		pValueCalculation.populationSet = populationSet;
		pValueCalculation.observedStudySet = studySet;
		PValue p[] = testCorrection.adjustPValues(pValueCalculation);

		/* Add the results to the result list and filter out terms
		 * with no annotated genes.
		 */
		for (int i=0;i<p.length;i++)
		{
			/* Entries are SingleGOTermProperties */
			TermForTermGOTermProperties prop = (TermForTermGOTermProperties)p[i];

			/* Within the result ignore terms without any annotation */
			if (prop.annotatedStudyGenes == 0)
				continue;

			studySetResult.addGOTermProperties(prop);
		}

		return studySetResult;
	}

	public boolean supportsTestCorrection() {
		return true;
	}
}