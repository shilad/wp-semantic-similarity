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
 * Computes the most similar Wikipedia articles to a target phrase or article.
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
     *
     * @param wpId1 One wikipedia page id.
     * @param wpId2 Another wikipedia page id.
     * @return
     * @throws IOException
     */
    public double similarity(int wpId1, int wpId2) throws IOException;

    /**
     * Computes the similarity between two textual phrases.
     * Similarity results should be between 0 and 1.
     *
     * @param phrase1 One phrase
     * @param phrase2 The other phrase
     * @return A similarity score between 0 and 1.
     * @throws IOException
     */
    double similarity(String phrase1, String phrase2) throws IOException;

    /**
     * Computes the most similar pages to a specified page.
     * Similarity results should be between 0 and 1.
     *
     * @param wpId The Wikipedia page id we want to find neighbors for.
     * @param maxResults The maximum number of neighbors.
     * @return  A list of most similar Wikipedia pages.
     * @throws IOException
     */
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException;

    /**
     * Computes the most similar pages to a specified page.
     * @param wpId The Wikipedia page id we want to find neighbors for.
     * @param maxResults The maximum number of neighbors.
     * @param validIds The Wikipedia ids that should be considered in result sets. Null means all ids.
     * @return A list of most similar Wikipedia pages.
     * @throws IOException
     */
    public DocScoreList mostSimilar(int wpId, int maxResults, TIntSet validIds) throws IOException;

    /**
     * Computes the most similar wikipedia ids to a particular phrase
     * @param phrase  The phrase whose similarity we are examining.
     * @param maxResults The maximum number of results to return.
     * @return A list of most similar Wikipedia pages.
     * @throws IOException
     */
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException;

    /**
     * Computes the most similar wikipedia ids to a particular phrase.
     * @param phrase The phrase whose similarity we are examining.
     * @param maxResults The maximum number of results to return.
     * @param validIds The Wikipedia ids that should be considered in result sets. Null means all ids.
     * @return A list of most similar Wikipedia pages.
     * @throws IOException
     */
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet validIds) throws IOException;

    /**
     * Writes the metric to a directory.
     * @param directory A directory data will be written to.
     *                  Any existing data in the directory may be destroyed.
     * @throws IOException
     */
    public void write(File directory) throws IOException;

    /**
     * Reads the metric from a directory.
     * @param directory A directory data will be read from.
     *                  The directory previously will have been written to by write().
     * @throws IOException
     */
    public void read(File directory) throws IOException;

    /**
     * Train the similarity() function.
     * The KnownSims may already be associated with Wikipedia ids (check wpId1 and wpId2).
     * @param labeled The labeled gold standard dataset.
     */
    public void trainSimilarity(List<KnownSim> labeled);

    /**
     * Train the mostSimilar() function
     * The KnownSims may already be associated with Wikipedia ids (check wpId1 and wpId2)
     * @param labeled The labeled gold standard dataset.
     * @param numResults The maximum number of similar articles computed per phrase.
     * @param validIds The Wikipedia ids that should be considered in result sets. Null means all ids.
     */
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds);

}