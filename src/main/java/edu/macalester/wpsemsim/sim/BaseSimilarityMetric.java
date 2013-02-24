package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.Disambiguator;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.normalize.IdentityNormalizer;
import edu.macalester.wpsemsim.normalize.Normalizer;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides basic functionality that similarity metric implementations should extend.
 */
public abstract class BaseSimilarityMetric implements SimilarityMetric {
    private static Logger LOG = Logger.getLogger(BaseSimilarityMetric.class.getName());

    private ConceptMapper mapper;
    private IndexHelper helper;
    private String name = this.getClass().getSimpleName();
    private Disambiguator disambiguator;

    private Normalizer normalizer = new IdentityNormalizer();
    // turned off while training the normalizer
    private boolean useNormalizer = true;

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

    /**
     * Normalizers translate similarity scores to more meaningful values.
     * @param n
     */
    public void setNormalizer(Normalizer n){
        normalizer = n;
    }

    @Override
    public void trainSimilarity(List<KnownSim> labeled){
        trainSimilarityNormalizer(labeled);
    }

    /**
     * Trains the normalizer to support the similarity() method.
     * @param labeled
     */
    protected void trainSimilarityNormalizer(List<KnownSim> labeled) {
        useNormalizer = false;
        for (KnownSim ks:labeled){
            try{
                double sim = similarity(ks.phrase1,ks.phrase2);
                if (!Double.isNaN(sim) && !Double.isInfinite(sim)){
                    normalizer.observe(sim, ks.similarity);
                }
            } catch (Exception e){
                LOG.log(Level.SEVERE, "similarity training failed", e);
            }
        }
        normalizer.observationsFinished();
        useNormalizer = true;
    }

    @Override
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds){
        trainMostSimilarNormalizer(labeled, numResults, validIds);
    }

    /**
     * Trains the normalizer to support the mostSimilar() method.
     * @param labeled
     */
    protected void trainMostSimilarNormalizer(List<KnownSim> labeled, int numResults, TIntSet validIds) {
        useNormalizer = false;
        for (KnownSim ks:labeled){
            ks.maybeSwap();
            try {
                Disambiguator.Match m = disambiguator.disambiguateMostSimilar(ks.phrase1, ks.phrase2, numResults, validIds);
                DocScoreList dsl = mostSimilar(m.phraseWpId, numResults, validIds);
                double sim = dsl.getScoreForId(m.hintWpId);
                if (!Double.isNaN(sim) && !Double.isInfinite(sim)){
                    normalizer.observe(sim, ks.similarity);
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE,  "disambiguation failed while training most similar:", e);
            }
        }
        normalizer.observationsFinished();
        useNormalizer = true;
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        if (mapper == null) {
            throw new UnsupportedOperationException("Mapper must be non-null to resolve phrases");
        }
        Disambiguator.Match m = disambiguator.disambiguateSimilarity(phrase1, phrase2);
        if (m == null) {
            return Double.NaN;
        } else {
            System.err.println("match is " + m);
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
        if (mapper == null) {
            throw new UnsupportedOperationException("Mapper must be non-null to resolve phrases");
        }
        Disambiguator.Match m = disambiguator.disambiguateMostSimilar(phrase, null, maxResults, possibleWpIds);
        if (m == null) {
            return null;
        }
        System.out.println("for " + phrase + " best is " + m.phraseWpName);
        return mostSimilar(m.phraseWpId, maxResults, possibleWpIds);
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
     * Use the normalizer to normalize a similarity if it's available.
     * @param sim
     * @return
     */
    protected double normalize(double sim) {
        if (normalizer == null || !useNormalizer || Double.isInfinite(sim) || Double.isNaN(sim)) {
            return sim;
        } else {
            return normalizer.normalize(sim);
        }
    }

    /**
     * Use the normalizer to normalize a list of score if possible.
     * @param dsl
     * @return
     */
    protected DocScoreList normalize(DocScoreList dsl) {
        if (normalizer == null || !useNormalizer) {
            return dsl;
        }
        DocScoreList normalized = new DocScoreList(dsl.numDocs());
        for (int i = 0; i < dsl.numDocs(); i++) {
            normalized.set(i, dsl.getId(i), normalize(dsl.getScore(i)));
        }
        return normalized;
    }
}
