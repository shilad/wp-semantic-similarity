package edu.macalester.wpsemsim.sim.ensemble;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.sim.SupervisedSimilarityMetric;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.utils.SimilarityMetricConfigurator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.map.hash.TIntDoubleHashMap;
import libsvm.*;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnsembleSimilarity extends BaseSimilarityMetric implements SupervisedSimilarityMetric {
    private static final Logger LOG = Logger.getLogger(EnsembleSimilarity.class.getName());

    private int numThreads = Runtime.getRuntime().availableProcessors();;
    private ConceptMapper mapper;
    private IndexHelper helper;
    private List<SimilarityMetric> components = new ArrayList<SimilarityMetric>();

    SvmEnsemble svm;

    public EnsembleSimilarity(ConceptMapper mapper, IndexHelper helper) throws IOException {
        super(mapper, helper);
        this.mapper = mapper;
        this.helper = helper;
        this.svm = new SvmEnsemble(0);
    }

    public void setMinComponents(int n) {
        this.svm.setMinComponents(n);
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        return svm.predictPair(getComponentSimilarities(phrase1, phrase2, -1), true);
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException {
        Map<Integer, List<ComponentSim>> features = new HashMap<Integer, List<ComponentSim>>();
        List<ComponentSim> missing = new ArrayList<ComponentSim>();

        for (int i = 0; i < components.size(); i++) {
            DocScoreList top = components.get(i).mostSimilar(wpId, maxResults * 2);
            if (top == null) {
                return null;
            }
            missing.add(new ComponentSim(i, top, -1));
            for (int j = 0; j < top.numDocs(); j++) {
                DocScore ds = top.get(j);
                if (!features.containsKey(ds.getId())) {
                    features.put(ds.getId(), new ArrayList<ComponentSim>());
                }
                features.get(ds.getId()).add(new ComponentSim(i, top, j));
            }
        }
        DocScoreList list = new DocScoreList(features.size());
        int n = 0;
        for (int wpId2 : features.keySet()) {
            List<ComponentSim> sparseSims = features.get(wpId);
            List<ComponentSim> denseSims = sparseSimsToDense(missing, sparseSims);
            double pred = svm.predict(denseSims, false);
            if (!Double.isNaN(pred)) {
                list.set(n++, wpId2, pred);
            }
        }
        list.truncate(n);
        list.sort();
        if (list.numDocs() > maxResults) {
            list.truncate(maxResults);
        }
        return list;
    }

    private List<ComponentSim> sparseSimsToDense(List<ComponentSim> missing, List<ComponentSim> sparseSims) {
        List<ComponentSim> denseSims = new ArrayList<ComponentSim>();
        int j = 0;
        for (int i = 0; i < components.size(); i++) {
            if (j < sparseSims.size() && sparseSims.get(j).component == i) {
                denseSims.add(sparseSims.get(j++));
            } else {
                denseSims.add(missing.get(i));
            }
        }
        assert(j == sparseSims.size());
        return denseSims;
    }

    public void setNumThreads(int n) {
        this.numThreads = n;
    }

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
        this.svm.setComponents(components);
    }

    public void trainSimilarity(List<KnownSim> gold) {
        trainMostSimilar(gold, -1);
    }

    @Override
    public void trainMostSimilar(List<KnownSim> gold, int numResults) {
        train(gold, numResults);
    }

    public void write(File directory) throws IOException {
        svm.write(directory);
    }

    public void read(File directory) throws IOException {
        svm.read(directory);
    }

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
                        List<Pair<ComponentSim, ComponentSim>> sims = getComponentSimilarities(ks.phrase1, ks.phrase2, numResults);
                        examples.add(new Example(ks, sims));
                    } catch (Exception e) {
                        e.printStackTrace();
                        LOG.log(Level.SEVERE, "error processing similarity entry  " + ks, e);
                    }
                    }
                });
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
        svm.train(examples);
        try {
            writeArff(new File("examples.arff"), examples);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private void writeArff(File file, List<Example> examples) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write("@relation similarities\n\n");
        for (int i = 0; i < components.size(); i++) {
            SimilarityMetric m = components.get(i);
            String name = m.getName().toLowerCase().replaceAll("[^a-zA-Z]+", "");
            writer.write(ComponentSim.getArffHeader(name + "1"));
            writer.write(ComponentSim.getArffHeader(name + "2"));
        }
        writer.write("@attribute sim real\n");

        writer.write("@data\n");
        for (Example x : examples) {
            for (Pair<ComponentSim, ComponentSim> pair : x.simPairs) {
                writer.write(pair.getLeft().getArffEntry());
                writer.write(pair.getRight().getArffEntry());
            }
            writer.write("" + x.label.similarity + "\n");
        }
    }

    protected List<Pair<ComponentSim, ComponentSim>> getComponentSimilarities(String phrase1, String phrase2, int numResults) throws IOException, ParseException {
        List<Pair<ComponentSim, ComponentSim>> pairs = new ArrayList<Pair<ComponentSim, ComponentSim>>();
        for (int i = 0; i < components.size(); i++) {
            SimilarityMetric m = components.get(i);
            ComponentSim cs1;
            ComponentSim cs2;
            if (numResults <= 0) {
                cs1 = new ComponentSim(i, m.similarity(phrase1, phrase2));
                cs2 = cs1;  // hack, for now.
            } else {
                cs1 = getComponentSim(i, m, phrase1, phrase2, numResults);
                cs2 = getComponentSim(i, m, phrase2, phrase1, numResults);
            }
            pairs.add(Pair.of(cs1, cs2));
        }
        return pairs;
    }

    /**
     * Gets most similar for phrase 1, looks for concepts mapped to phrase 2
     * @param metric
     * @param phrase1
     * @param phrase2
     * @param numResults
     * @return
     * @throws IOException
     */
    protected ComponentSim getComponentSim(int ci, SimilarityMetric metric, String phrase1, String phrase2, int numResults) throws IOException {
        TIntDoubleHashMap concepts = new TIntDoubleHashMap();
        for (Map.Entry<String, Float> entry : mapper.map(phrase2, 10).entrySet()) {
            Document d = helper.titleToLuceneDoc(entry.getKey());
            if (d != null && (d.getFields(Page.FIELD_TYPE).length == 0 || d.get(Page.FIELD_TYPE).equals("normal"))) {
                int wpId = Integer.valueOf(d.get(Page.FIELD_WPID));
                concepts.put(wpId, entry.getValue());
            }
        }
        DocScoreList top =  metric.mostSimilar(phrase1, numResults);
        if (top == null || top.numDocs() == 0) {
            return new ComponentSim(ci, new DocScoreList(0), 0);
        }
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
//        System.out.println("for " + phrase1 + ", " + phrase2 + " metric " + metric.getName() + " returning " + bestSim);
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
        SimilarityMetricConfigurator conf = new SimilarityMetricConfigurator(
                new ConfigurationFile(new File(args[0])));
        File modelPath = new File(args[2]);
        if (modelPath.exists()) {
            FileUtils.forceDelete(modelPath);
        }
        modelPath.mkdirs();
        EnsembleSimilarity ensemble = new EnsembleSimilarity(
                conf.getMapper(),
                conf.getHelper()
        );
//        ensemble.setNumThreads(1);
        ensemble.setMinComponents(0);
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (args.length == 4) {
            metrics = conf.loadAllMetrics();
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