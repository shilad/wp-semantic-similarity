package edu.macalester.wpsemsim;

import edu.macalester.wpsemsim.utils.KnownSim;
import edu.macalester.wpsemsim.sim.SimilarityMetric;

import java.util.List;

/**
 * TODO: create a general training problem if somebody besides
 */
public interface SupervisedSimilarityMetric extends SimilarityMetric {
    // Train the similarity() function
    public void trainSimilarity(List<KnownSim> labeled);

    // Train the mostSimilar() function
    public void trainMostSimilar(List<KnownSim> labeled, int numResults);
}
