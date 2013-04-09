package edu.macalester.wpsemsim.topics;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import gnu.trove.map.TDoubleIntMap;
import gnu.trove.map.TIntObjectMap;

import java.io.File;
import java.util.List;

/**
 * Given a sparse matrix, this class creates a lower-rank dense topic matrix.
 */
public class TopicExtractor {
    private int numTopics;

    private List<TDoubleIntMap> topics;         // for each topic, a list of features
    private TIntObjectMap<double []> documents; // topics for each document.

    public TopicExtractor(int numTopics) {
        this.numTopics = numTopics;
    }

    public void estimate(SparseMatrix matrix) {
    }
}
