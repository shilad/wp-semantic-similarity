package edu.macalester.wpsemsim.sim.ensemble;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.sim.SupervisedSimilarityMetric;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.utils.EnvConfigurator;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.TIntSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.*;
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
 */
public class EnsembleSimilarity extends BaseSimilarityMetric implements SupervisedSimilarityMetric {
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
        trainMostSimilar(gold, -1);
    }

    @Override
    public void trainMostSimilar(List<KnownSim> gold, int numResults) {
        train(gold, numResults);
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        Example ex = getComponentSimilarities(phrase1, phrase2, -1);
        if (ex.getNumNotNan() >= minComponents) {
            return ensemble.predict(ex, true);
        } else {
            return Double.NaN;
        }
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
                        features.put(ds.getId(), Example.makeEmpty());
                    }
                    features.get(ds.getId()).add(new ComponentSim(i, top, j));
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
        return list;
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
     *                   if greater than 0 train mostSimilar() with specified size of result lists.
     */
    public void train(final List<KnownSim> gold, final int numResults) {
        final ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        final List<Example> examples = Collections.synchronizedList(new ArrayList<Example>());
        try {
            for (int i = 0; i < gold.size(); i++) {
                final int finalI = i;
                exec.submit(new Runnable() {
                    public void run() {
                        KnownSim ks = gold.get(finalI);
                        try {
                        if (finalI % 50 == 0) {
                            LOG.info("training for number " + finalI + " of " + gold.size());
                        }
                        Example ex = getComponentSimilarities(ks.phrase1, ks.phrase2, numResults);
                        if (ex.getNumNotNan() >= minComponents) {
                            examples.add(ex);
                        }
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "error processing similarity entry  " + ks, e);
                        LOG.log(Level.SEVERE, "stacktrace: " + ExceptionUtils.getStackTrace(e).replaceAll("\n", " ").replaceAll("\\s+", " "));
                    }
                }});
            }
        } finally {
            try {
                Thread.sleep(5000);
                exec.shutdown();
                exec.awaitTermination(60, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                LOG.log(Level.WARNING, "error while awaiting termination:", e);
            }
        }
        ensemble.train(examples);
    }


    /**
     * Collects the similarities scores for a pair of phrases from all metrics.
     * We are training mostSimilar iff numResults > 0.
     *
     * @param phrase1
     * @param phrase2
     * @param numResults
     * @return
     * @throws IOException
     * @throws ParseException
     */
    protected Example getComponentSimilarities(String phrase1, String phrase2, int numResults) throws IOException, ParseException {
        Example result = (numResults > 0) ? Example.makeEmptyWithReverse() : Example.makeEmpty();
        for (int i = 0; i < components.size(); i++) {
            SimilarityMetric m = components.get(i);
            if (numResults <= 0) {
                result.add(new ComponentSim(i, m.similarity(phrase1, phrase2)));
            } else {
                result.add(getComponentSim(i, m, phrase1, phrase2, numResults),
                           getComponentSim(i, m, phrase2, phrase1, numResults));
            }
        }
        return result;
    }

    /**
     * Gets most similar for phrase 1, looks for concepts mapped to phrase 2
     * @param ci Index of the similarity metric in the components
     * @param metric
     * @param phrase1
     * @param phrase2
     * @param numResults
     * @return
     * @throws IOException
     */
    protected ComponentSim getComponentSim(int ci, SimilarityMetric metric, String phrase1, String phrase2, int numResults) throws IOException {
        // get most similar lucene ids for phrase 1
        DocScoreList top =  metric.mostSimilar(phrase1, numResults);
        if (top == null || top.numDocs() == 0) {
            return new ComponentSim(ci, new DocScoreList(0), 0);
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
        return new ComponentSim(ci, top, bestRank);
    }


    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException, ClassNotFoundException {
        if (args.length < 4) {
            System.err.println(
                    "usage: java " + EnsembleSimilarity.class.toString() +
                    " path/to/sim/metric/conf.txt" +
                    " path/to/gold/standard.txt" +
                    " path/to/reg/model_output.txt" +
                    " num_results" +
                    " [sim1 sim2 ...]"
            );
            System.exit(1);
        }
        EnvConfigurator conf = new EnvConfigurator(
                new ConfigurationFile(new File(args[0])));
        File modelPath = new File(args[2]);
        if (modelPath.exists()) {
            FileUtils.forceDelete(modelPath);
        }
        modelPath.mkdirs();
        conf.setShouldLoadMetrics(false);
        conf.setDoEnsembles(false);
        Env env = conf.loadEnv();
        EnsembleSimilarity ensemble = new EnsembleSimilarity(
                new WekaEnsemble(new File("dat/problem.arff")), env.getMainMapper(), env.getMainIndex()
        );
//        ensemble.setNumThreads(1);
        ensemble.setMinComponents(0);
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (args.length == 4) {
            conf.loadMetrics();
            metrics = new ArrayList<SimilarityMetric>(env.getMetrics().values());
        } else {
            for (String name : ArrayUtils.subarray(args, 4, args.length)) {
                metrics.add(conf.loadMetric(name));
            }
        }
        ensemble.setComponents(metrics);
        ensemble.trainMostSimilar(KnownSim.read(new File(args[1])), Integer.valueOf(args[3]));
        ensemble.write(modelPath);

        // test it!
        ensemble.read(new File(args[2]));
    }
}