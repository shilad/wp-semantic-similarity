package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.set.TIntSet;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.search.Filter;

import java.io.IOException;

/**
 * Estimates the similarity between Wikipedia pages, phrases, or both.
 */
public interface SimilarityMetric {

    /**
     * A unique, human-readable name for the metric.
     * @return
     */
    public String getName();

    /**
     * Sets the name for the metric.
     * @param name
     */
    public void setName(String name);

    /**
     * Computes the similarity between two Wikipedia pages.
     * Similarity results should be between 0 and 1.
     * TODO: ensure all metrics return results in this range.
     * @param wpId1
     * @param wpId2
     * @return
     * @throws IOException
     */
    public double similarity(int wpId1, int wpId2) throws IOException;

    /**
     * Computes the similarity between two textual phrases.
     * Similarity results should be between 0 and 1.
     * TODO: ensure all metrics return results in this range.
     * @param phrase1
     * @param phrase2
     * @return
     * @throws IOException
     * @throws ParseException
     */
    double similarity(String phrase1, String phrase2) throws IOException, ParseException;

    /**
     * Computes the most similar pages to a specified page.
     * Similarity results should be between 0 and 1.
     * TODO: ensure all metrics return results in this range.
     * @param wpId1
     * @param maxResults
     * @return
     * @throws IOException
     */
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException;

    /**
     * Computes the most similar pages to a specified page.
     * @param wpId1
     * @param maxResults
     * @param possibleWpIds Only consider these ids
     * @return
     * @throws IOException
     */
    public DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException;

    /**
     * Computes the most similar wikipedia ids to a particular phrase
     * @param phrase
     * @param maxResults
     * @return
     * @throws IOException
     */
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException;

    /**
     * Computes the most similar wikipedia ids to a particular phrase.
     * @param phrase
     * @param maxResults
     * @param possibleWpIds Only consider these ids.
     * @return
     * @throws IOException
     */
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException;

}