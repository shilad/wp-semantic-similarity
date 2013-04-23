package edu.macalester.wpsemsim.topics;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.set.TIntSet;

import java.io.IOException;

public class TopicSimilarity extends BaseSimilarityMetric {

    public TopicSimilarity(ConceptMapper mapper, IndexHelper helper) {
        super(mapper, helper);
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
