package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.Disambiguator;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.normalize.IdentityNormalizer;
import edu.macalester.wpsemsim.normalize.Normalizer;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.set.TIntSet;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Provides basic functionality that similarity metric implementations should extend.
 *
 * TODO: separate similarity() and mostSimilar() normalizers.
 */
public abstract class BaseSimilarityMetric implements SimilarityMetric {
    private static Logger LOG = Logger.getLogger(BaseSimilarityMetric.class.getName());

    private ConceptMapper mapper;
    private IndexHelper helper;
    private String name = this.getClass().getSimpleName();
    private Disambiguator disambiguator;
    protected int numThreads = Runtime.getRuntime().availableProcessors();

    private Normalizer mostSimilarNormalizer = new IdentityNormalizer();
    private Normalizer similarityNormalizer = new IdentityNormalizer();

    protected SparseMatrix mostSimilarMatrix;

    public BaseSimilarityMetric(ConceptMapper mapper, IndexHelper helper) {
        this.mapper = mapper;
        this.helper = helper;

        if (mapper == null) {
            LOG.warning("ConceptMapper is null. Will not be able to resolve phrases to concepts.");
        }
        if (helper == null) {
            LOG.warning("IndexHelper is null. Will not be able to resolve phrases to concepts.");
        }
        this.disambiguator = new Disambiguator(mapper, this, helper, 5);
    }

    public void setMostSimilarMatrix(SparseMatrix matrix) {
        this.mostSimilarMatrix = matrix;
    }

    public boolean hasCachedMostSimilar(int wpId) throws IOException {
        return mostSimilarMatrix != null && mostSimilarMatrix.getRow(wpId) != null;
    }

    public DocScoreList getCachedMostSimilar(int wpId, int numResults, TIntSet validIds) throws IOException {
        if (mostSimilarMatrix == null) {
            return null;
        }
        SparseMatrixRow row = mostSimilarMatrix.getRow(wpId);
        if (row == null) {
            return null;
        }
        int n = 0;
        DocScoreList dsl = new DocScoreList(row.getNumCols());
        for (int i = 0;i < row.getNumCols() &&  n < numResults; i++) {
            int wpId2 = row.getColIndex(i);
            if (validIds == null || validIds.contains(wpId2)) {
                dsl.set(n++, row.getColIndex(i), row.getColValue(i));
            }
        }
        dsl.truncate(n);
        return dsl;
    }

    /**
     * Normalizers translate similarity scores to more meaningful values.
     * @param n
     */
    public void setMostSimilarNormalizer(Normalizer n){
        mostSimilarNormalizer = n;
    }

    @Override
    public void trainSimilarity(List<KnownSim> labeled){
        trainSimilarityNormalizer(labeled);
    }

    /**
     * Trains the mostSimilarNormalizer to support the similarity() method.
     * @param labeled
     */
    protected synchronized void trainSimilarityNormalizer(List<KnownSim> labeled) {
        final Normalizer trainee = similarityNormalizer;
        this.similarityNormalizer = new IdentityNormalizer();
        ParallelForEach.loop(labeled, numThreads, new Procedure<KnownSim>() {
            public void call(KnownSim ks) throws IOException, ParseException {
                double sim = similarity(ks.phrase1, ks.phrase2);
                trainee.observe(sim, ks.similarity);
            }
        });
        trainee.observationsFinished();
        similarityNormalizer = trainee;
        LOG.info("trained most similarityNormalizer for " + getName() + ": " + trainee.dump());
    }

    @Override
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds){
        trainMostSimilarNormalizer(labeled, numResults, validIds);
    }

    /**
     * Trains the mostSimilarNormalizer to support the mostSimilar() method.
     * Also estimates the similarity score for articles that don't appear in top lists.
     * Note that this (probably) is an overestimate, and depends on how well the
     * distribution of scores in your gold standard matches your actual data.
     *
     * @param labeled
     */
    protected synchronized void trainMostSimilarNormalizer(List<KnownSim> labeled, final int numResults, final TIntSet validIds) {
        final Normalizer trainee = mostSimilarNormalizer;
        this.mostSimilarNormalizer = new IdentityNormalizer();
        ParallelForEach.loop(labeled, numThreads, new Procedure<KnownSim>() {
            public void call(KnownSim ks) throws IOException {
                ks.maybeSwap();
                Disambiguator.Match m = disambiguator.disambiguateMostSimilar(ks, numResults, validIds);
                if (m != null) {
                    DocScoreList dsl = mostSimilar(m.phraseWpId, numResults, validIds);
                    if (dsl != null) {
                        trainee.observe(dsl, dsl.getIndexForId(m.hintWpId), ks.similarity);
                    }
                }
            }
        });
        trainee.observationsFinished();
        mostSimilarNormalizer = trainee;
        LOG.info("trained most similar normalizer for " + getName() + ": " + trainee.dump());
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException {
        ensureSimilarityTrained();
        if (mapper == null) {
            throw new UnsupportedOperationException("Mapper must be non-null to resolve phrases");
        }
        Disambiguator.Match m = disambiguator.disambiguateSimilarity(phrase1, phrase2);
        if (m == null) {
            return Double.NaN;
        } else {
            /*System.err.println(
                "metric " + getName() + 
                " mapped " + phrase1 + ", " + phrase2 +
                " to " + m.phraseWpName + ", " + m.hintWpName);*/
                
            return similarity(m.phraseWpId, m.hintWpId);
        }
    }

    @Override
    public abstract double similarity(int wpId1, int wpId2) throws IOException;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Creates a list of similar documents. This implementation calls rawMostSimilar and normalizes the results.
     * @param wpId1
     * @param maxResults
     * @param possibleWpIds Only consider these ids
     * @return
     * @throws IOException
     */
    @Override
    public abstract DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException ;

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException {
        ensureMostSimilarTrained();
        if (mapper == null) {
            throw new UnsupportedOperationException("Mapper must be non-null to resolve phrases");
        }
        Disambiguator.Match m = disambiguator.disambiguateMostSimilar(phrase, null, maxResults, possibleWpIds);
        if (m == null) {
            return null;
        }
        return mostSimilar(m.phraseWpId, maxResults, possibleWpIds);
    }

    /**
     * Throws an IllegalStateException if the model has not been mostSimilarTrained.
     */
    protected void ensureSimilarityTrained() {
        if (!similarityNormalizer.isTrained()) {
            throw new IllegalStateException("Model similarity has not been trained.");
        }
    }
    /**
     * Throws an IllegalStateException if the model has not been mostSimilarTrained.
     */
    protected void ensureMostSimilarTrained() {
        if (!mostSimilarNormalizer.isTrained()) {
            throw new IllegalStateException("Model mostSimilar has not been trained.");
        }
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException {
        return mostSimilar(wpId1,  maxResults, null);
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException {
        return mostSimilar(phrase, maxResults, null);
    }

    public IndexHelper getHelper() {
        return this.helper;
    }

    /**
     * Use the similarityNormalizer to normalize a similarity if it's available.
     * @param sim
     * @return
     */
    protected double normalize(double sim) {
        ensureSimilarityTrained();
        return similarityNormalizer.normalize(sim);
    }

    /**
     * Use the mostSimilarNormalizer to normalize a list of score if possible.
     * @param dsl
     * @return
     */
    protected DocScoreList normalize(DocScoreList dsl) {
        ensureMostSimilarTrained();
        return mostSimilarNormalizer.normalize(dsl);
    }

    /**
     * Writes the metric to a directory.
     * @param directory
     * @throws IOException
     */
    @Override
    public void write(File directory) throws IOException {
        Utils.writeObject(
                new File(directory, "mostSimilarNormalizer"),
                mostSimilarNormalizer);
        Utils.writeObject(
                new File(directory, "similarityNormalizer"),
                similarityNormalizer);
    }
    /**
     * Reads the metric from a directory.
     * @param directory
     * @throws IOException
     */
    @Override
    public void read(File directory) throws IOException {
        this.mostSimilarNormalizer = (Normalizer)Utils.readObject(
                    new File(directory, "mostSimilarNormalizer"));
        this.similarityNormalizer = (Normalizer)Utils.readObject(
                    new File(directory, "similarityNormalizer"));
    }

    /**
     * Sets the number of threads used when training the metric
     * @param n
     */
    public void setNumThreads(int n) {
        this.numThreads = n;
    }

    public void setSimilarityNormalizer(Normalizer similarityNormalizer) {
        this.similarityNormalizer = similarityNormalizer;
    }
}
