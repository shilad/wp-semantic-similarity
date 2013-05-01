package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.sim.SimilarityMetric;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: shilad
 * Date: 12/21/12
 * Time: 2:03 PM
 * To change this template use File | Settings | File Templates.
 */
public interface Ensemble {
    void setComponents(List<SimilarityMetric> components);

    void trainMostSimilar(List<Example> examples);

    void trainSimilarity(List<Example> examples);

    double predictSimilarity(Example ex, boolean truncate);

    double predictMostSimilar(Example ex, boolean truncate);

    void write(File directory) throws IOException;

    void read(File directory) throws IOException;
}
