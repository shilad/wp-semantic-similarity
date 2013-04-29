package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.normalize.Normalizer;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Estimates the similarity between Wikipedia pages, phrases, or both.
 */
public interface SimilarityMetric {

    /**
     * Train the similarity() function.
     * The KnownSims may already be associated with Wikipedia ids (check wpId1 and wpId2)
     */
    public void trainSimilarity(List<KnownSim> labeled);

    /**
     * Train the mostSimilar() function
     * The KnownSims may already be associated with Wikipedia ids (check wpId1 and wpId2)
     */
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds);

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

    /**
     * Writes the metric to a directory.
     * @param directory
     * @throws IOException
     */
    public void write(File directory) throws IOException;

    /**
     * Reads the metric from a directory.
     * @param directory
     * @throws IOException
     */
    public void read(File directory) throws IOException;
}