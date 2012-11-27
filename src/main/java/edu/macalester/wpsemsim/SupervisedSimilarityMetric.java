package edu.macalester.wpsemsim;

import edu.macalester.wpsemsim.utils.KnownSim;
import edu.macalester.wpsemsim.sim.SimilarityMetric;

import java.util.List;

public interface SupervisedSimilarityMetric extends SimilarityMetric {
    // Train the similarity() function
    public void train(List<KnownSim> labeled);

    // Train the mostSimilar() function
    public void train(List<KnownSim> labeled, int numResults);
}
