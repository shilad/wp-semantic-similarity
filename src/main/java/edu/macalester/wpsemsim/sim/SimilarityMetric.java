package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.utils.DocScoreList;

import java.io.IOException;

public interface SimilarityMetric {
    public String getName();
    public void setName(String name);
    public double similarity(int wpId1, int wpId2) throws IOException;
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException;
}