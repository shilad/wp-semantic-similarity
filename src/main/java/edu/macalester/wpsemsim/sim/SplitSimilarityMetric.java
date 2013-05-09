package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A similarity metric that uses two delegate similarity metrics.
 * One delegate handles mostSimilar, the other handles similarity
 */
public class SplitSimilarityMetric implements SimilarityMetric {
    private SimilarityMetric similarityDelegate;
    private SimilarityMetric mostSimilarDelegate;
    private String name;


    public SplitSimilarityMetric(SimilarityMetric similarityDelegate, SimilarityMetric mostSimilarDelegate) {
        this.similarityDelegate = similarityDelegate;
        this.mostSimilarDelegate = mostSimilarDelegate;
        name = "split-" + similarityDelegate.getName() + "-" + mostSimilarDelegate.getName();
    }

    @Override
    public void trainSimilarity(List<KnownSim> labeled) {
        this.similarityDelegate.trainSimilarity(labeled);
    }

    @Override
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds) {
        this.mostSimilarDelegate.trainMostSimilar(labeled, numResults, validIds);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        return similarityDelegate.similarity(wpId1, wpId2);
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException {
        return similarityDelegate.similarity(phrase1, phrase2);
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException {
        return mostSimilarDelegate.mostSimilar(wpId1, maxResults);
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException {
        return mostSimilarDelegate.mostSimilar(wpId1, maxResults, possibleWpIds);
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException {
        return mostSimilarDelegate.mostSimilar(phrase, maxResults);
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException {
        return mostSimilarDelegate.mostSimilar(phrase, maxResults, possibleWpIds);
    }

    @Override
    public void write(File directory) throws IOException {
        // do nothing
    }

    @Override
    public void read(File directory) throws IOException {
        // do nothing
    }
}
