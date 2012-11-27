package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.utils.DocScoreList;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.IOException;

public interface SimilarityMetric {
    public String getName();
    public void setName(String name);
    public double similarity(int wpId1, int wpId2) throws IOException;
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException;
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException;
    double similarity(String phrase1, String phrase2) throws IOException, ParseException;

}