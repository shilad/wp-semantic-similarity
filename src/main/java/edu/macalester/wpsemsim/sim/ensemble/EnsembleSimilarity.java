package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimScore;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A supervised machine learner that predicts semantic similarity based
 * on a collection of underlying component similarity metrics.
 *
 * TODO:
 * For now we only train mostSimilar.
 * Training similarity() would not be difficult, but it is unimplemented.
 *
 */
public class EnsembleSimilarity extends BaseSimilarityMetric implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(EnsembleSimilarity.class.getName());

    private int numThreads = Runtime.getRuntime().availableProcessors();
    private ConceptMapper mapper;
    private IndexHelper helper;
    private int minComponents = 0;

    private List<SimilarityMetric> components = new ArrayList<SimilarityMetric>();

    Ensemble ensemble;

    public EnsembleSimilarity(Ensemble ensemble, ConceptMapper mapper, IndexHelper helper) throws IOException {
        super(mapper, helper);
        this.mapper = mapper;
        this.helper = helper;
        this.ensemble = ensemble;
    }

    @Override
    public void trainSimilarity(List<KnownSim> gold) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        throw new UnsupportedOperationException();

        /*
        Example ex = getComponentSimilarities(phrase1, phrase2, -1, null);
        if (ex.getNumNotNan() >= minComponents) {
            return ensemble.predict(ex, true);
        } else {
            return Double.NaN;
        }
        */
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults, TIntSet validIds) throws IOException {

        // build up example objects for each related page.
        Map<Integer, Example> features = new HashMap<Integer, Example>();
        for (int i = 0; i < components.size(); i++) {
            DocScoreList top = components.get(i).mostSimilar(wpId, maxResults * 2, validIds);
            if (top == null) {
                return null;
            }
            for (int j = 0; j < top.numDocs(); j++) {
                DocScore ds = top.get(j);
                if (validIds == null || validIds.contains(ds.getId())) {
                    if (!features.containsKey(ds.getId())) {
//                        features.put(ds.getId(), Example.makeEmpty());
                        features.put(ds.getId(), Example.makeEmptyWithReverse());
                    }
//                    features.get(ds.getId()).add(new SimScore(i, top, j));
                    // HACK!
                    features.get(ds.getId()).add(new SimScore(i, top, j), new SimScore(i, top, j));
                }
            }
        }

        // Generate predictions for all pages that have enough component scores
        int n = 0;
        DocScoreList list = new DocScoreList(features.size());
        for (int wpId2 : features.keySet()) {
            Example ex = features.get(wpId);
            if (ex.getNumNotNan() >= minComponents) {
                double pred = ensemble.predict(ex, false);
                if (!Double.isNaN(pred)) {
                    list.set(n++, wpId2, pred);
                }
            }
        }

        // Truncate and sort the list.
        list.truncate(n);
        list.sort();
        if (list.numDocs() > maxResults) {
            list.truncate(maxResults);
        }
        return normalize(list);
    }

    /**
     * Sets the minimum number of components that must generate a similarity score
     * for the ensemble to output a prediction. This is particularly important for
     * mostSimilar() where a page may appear in one similarity metric's mostSimilar()
     * list but not in others.
     *
     * @param n Minimum number of similarity metric components that must generate
     *          a similarity score for the ensemble to generate a similarity score.
     */
    public void setMinComponents(int n) {
        this.minComponents = n;
    }

    public void setNumThreads(int n) {
        this.numThreads = n;
    }

    /**
     * Sets the similarity metric components.
     * @param components
     */
    public void setComponents(List<SimilarityMetric> components) {
        components = new ArrayList<SimilarityMetric>(components);
        // make sure the order is deterministic
        Collections.sort(components, new Comparator<SimilarityMetric>() {
            @Override
            public int compare(SimilarityMetric m1, SimilarityMetric m2) {
                return m1.getName().compareTo(m2.getName());
            }
        });
        this.components = components;
        this.ensemble.setComponents(components);
    }


    /**
     * Writes the ensemble to a directory.
     * @param directory
     * @throws IOException
     */
    public void write(File directory) throws IOException {
        ensemble.write(directory);
    }

    /**
     * Reads the ensemble from a directory.
     * @param directory
     * @throws IOException
     */
    public void read(File directory) throws IOException {
        ensemble.read(directory);
    }

    /**
     * Trains the ensemble on a dataset.
     * @param gold Labeled training data.
     * @param numResults if less than or equal to 0 train similarity(),
     * @param validIds
     */
    @Override
    public void trainMostSimilar(final List<KnownSim> gold, final int numResults, final TIntSet validIds) {
        final List<Example> examples = Collections.synchronizedList(new ArrayList<Example>());
        ParallelForEach.loop(gold, numThreads, new Function<KnownSim>() {
            @Override
            public void call(KnownSim ks) throws Exception {
                Example ex = getComponentSimilarities(ks, numResults, validIds);
                ex.label = ks;
                if (ex.getNumNotNan() >= minComponents) {
                    examples.add(ex);
                }
            }
        });
        ensemble.trainMostSimilar(examples);

        // train the normalizer
        super.trainMostSimilar(gold, numResults, validIds);
    }


    /**
     * Collects the similarities scores for a pair of phrases from all metrics.
     * We are training mostSimilar iff numResults > 0.
     *
     * @param ks
     * @param numResults
     * @param validIds
     * @return
     * @throws IOException
     * @throws ParseException
     */
    protected Example getComponentSimilarities(KnownSim ks, int numResults, TIntSet validIds) throws IOException, ParseException {
        Example result = (numResults > 0) ? Example.makeEmptyWithReverse() : Example.makeEmpty();
        for (int i = 0; i < components.size(); i++) {
            SimilarityMetric m = components.get(i);

            boolean hasWpIds = (ks.wpId1 >= 0 && ks.wpId2 >= 0);
            if (numResults <= 0) {      // similarity
                if (hasWpIds) {
                    result.add(new SimScore(i, m.similarity(ks.wpId1, ks.wpId2)),
                               new SimScore(i, m.similarity(ks.wpId2, ks.wpId1)));
                } else {
                    result.add(new SimScore(i, m.similarity(ks.phrase1, ks.phrase2)),
                               new SimScore(i, m.similarity(ks.phrase2, ks.phrase1)));
                }
            } else {                    // mostSimilar
                if (hasWpIds) {
                    DocScoreList dsl = m.mostSimilar(ks.wpId1, numResults, validIds);
                    result.add(new SimScore(i, dsl, dsl.getIndexForId(ks.wpId2)));
                } else {
                    result.add(getComponentSim(i, m, ks.phrase1, ks.phrase2, numResults, validIds));
                }
            }
        }
        return result;
    }

    /**
     * Gets most similar for phrase 1, looks for concepts mapped to phrase 2
     *
     * @param ci Index of the similarity metric in the components
     * @param metric
     * @param phrase1
     * @param phrase2
     * @param numResults
     * @param validIds
     * @return
     * @throws IOException
     */
    protected SimScore getComponentSim(int ci, SimilarityMetric metric, String phrase1, String phrase2, int numResults, TIntSet validIds) throws IOException {
        // get most similar lucene ids for phrase 1
        DocScoreList top =  metric.mostSimilar(phrase1, numResults, validIds);
        if (top == null || top.numDocs() == 0) {
            return new SimScore(ci, new DocScoreList(0), 0);
        }

        // build up mapping between lucene ids and likelihood of id representing phrase2.
        TIntDoubleHashMap concepts = new TIntDoubleHashMap();
        for (Map.Entry<String, Float> entry : mapper.map(phrase2, 10).entrySet()) {
            Document d = helper.titleToLuceneDoc(entry.getKey());
            if (d != null) {
                IndexableField types[] = d.getFields(Page.FIELD_TYPE);
                if (types.length == 0 || types[0].stringValue().equals("normal")) {
                    int wpId = Integer.valueOf(d.get(Page.FIELD_WPID));
                    concepts.put(wpId, entry.getValue());
                }
            }
        }

        // finds the document with the highest product of semantic relatedness and concept likelihood
        double bestScore = -Double.MAX_VALUE;
        int bestRank = -1;
        for (int i = 0; i < top.numDocs(); i++) {
            DocScore ds = top.get(i);
            if (concepts.containsKey(ds.getId())) {
                double score = ds.getScore() * concepts.get(ds.getId());
                if (score > bestScore) {
                    bestRank = i;
                    bestScore = score;
                }
            }
        }
        return new SimScore(ci, top, bestRank);
    }


}