package edu.macalester.wpsemsim.sim;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.SupervisedSimilarityMetric;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.hash.TIntDoubleHashMap;
import libsvm.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
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
    private static final double RESCALED_MIN = -1.0f;
    private static final double RESCALED_MAX = +1.0f;
    private static final double P_EPSIONS[] = { 1.0, 0.5, 0.1, 0.01, 0.001 };
    private static final double P_CS[] = { 16, 8, 4, 2, 1, 0.5, 0.2, 0.1 };

    private int numThreads;
    private ConceptMapper mapper;
    private IndexHelper helper;
    private List<SimilarityMetric> components = new ArrayList<SimilarityMetric>();

    // These four attributes can be written and read from disk
    double componentMins[];
    double componentMaxs[];
    private svm_model model;
    private svm_parameter param;
    private BufferedWriter svmLog;

    public EnsembleSimilarity(ConceptMapper mapper, IndexHelper helper) throws IOException {
        super(mapper, helper);
        this.mapper = mapper;
        this.helper = helper;
        this.numThreads =  Runtime.getRuntime().availableProcessors();

        this.svmLog = new BufferedWriter(new FileWriter(new File("svm.log"), true));
        svm.svm_set_print_string_function(new svm_print_interface() {
            @Override
            public void print(String s) {
                try {
                    svmLog.write(new Date() + ": " + s + "\n");
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
        });
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        svm_node[] v = getComponentSimilarities(phrase1, phrase2, -1);
        for (svm_node n : v) {
            normalize(n);
        }

        double p = svm.svm_predict(model, v);
        return unnormalize(components.size(), p);
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void setNumThreads(int n) {
        this.numThreads = n;
    }

    public void setComponents(List<SimilarityMetric> components) {
        // make sure the order is deterministic
        Collections.sort(components, new Comparator<SimilarityMetric>() {
            @Override
            public int compare(SimilarityMetric m1, SimilarityMetric m2) {
                return m1.getName().compareTo(m2.getName());
            }
        });
        this.components = components;
    }

    public void trainSimilarity(List<KnownSim> gold) {
        trainMostSimilar(gold, -1);
    }

    @Override
    public void trainMostSimilar(List<KnownSim> gold, int numResults) {
        svm_problem prob = makeProblem(gold, numResults);
        train(prob);
    }

    private void train(svm_problem prob) {
        svm_parameter param = getParams(prob);

        String error_msg = svm.svm_check_parameter(prob, param);
        if(error_msg != null) {
            throw new IllegalArgumentException(error_msg);
        }
        double bestP = -1.0;
        double bestC = -1.0;
        double bestCorrelation = -Double.MAX_VALUE;

        for (double p : P_EPSIONS) {
            for (double c : P_CS) {
                param.p = p;
                param.C = c;
                double correlation = do_cross_validation(prob, param, 7);

//                System.err.println("CROSSFOLD FOR p=" + p + ", c=" + c + " is " + correlation);
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation;
                    bestP = p;
                    bestC = c;
                }
            }
        }
        param.p = bestP;
        param.C = bestC;
        this.param = param;
        LOG.info("Choosing p=" + bestP + ", c=" + bestC + " with pearson=" + bestCorrelation);
        this.model = svm.svm_train(prob, param);
    }

    public void write(File directory) throws IOException {
        if (directory.isDirectory()) {
            FileUtils.forceDelete(directory);
        }
        directory.mkdirs();

        ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "model.libsvm")));
        out.writeObject(model);
        out.close();

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "params.libsvm")));
        out.writeObject(param);
        out.close();

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "mins.libsvm")));
        out.writeObject(componentMins);
        out.close();

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "maxs.libsvm")));
        out.writeObject(componentMaxs);
        out.close();

        String names = StringUtils.join(getComponentNames(), ", ");
        FileUtils.write(new File(directory, "component_names.txt"), names);
    }

    public void read(File directory) throws IOException {
        if (!directory.isDirectory()) {
            throw new FileNotFoundException(directory.toString());
        }

        String expected = StringUtils.join(getComponentNames(), ", ");
        String actual = FileUtils.readFileToString(new File(directory, "component_names.txt"));
        if (!expected.equals(actual)) {
            new IOException(
                    "Unexpected component similarity metrics: " +
                    "Expected '" + expected + "', found '" + actual + "'"
            );
        }

        try {
            ObjectInputStream in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "model.libsvm")));
            model = (svm_model) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "params.libsvm")));
            param = (svm_parameter) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "mins.libsvm")));
            componentMins = (double[]) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "maxs.libsvm")));
            componentMaxs = (double[]) in.readObject();
            in.close();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    protected List<String> getComponentNames() {
        List<String> names = new ArrayList<String>();
        for (SimilarityMetric m : components) {
            names.add(m.getName());
        }
        return names;
    }

    public svm_problem makeProblem(final List<KnownSim> gold) {
        return makeProblem(gold, -1);
    }

    public svm_problem makeProblem(final List<KnownSim> gold, final int numResults) {
        final TFloatArrayList vy = new TFloatArrayList();
        final List<svm_node[]> vx = new ArrayList<svm_node[]>();
        ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        componentMins = new double[components.size() + 1];  // last is for y values
        componentMaxs = new double[components.size() + 1];  // last is for y values
        Arrays.fill(componentMins, Double.MAX_VALUE);
        Arrays.fill(componentMaxs, -Double.MAX_VALUE);

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
                        svm_node v[] = getComponentSimilarities(ks.phrase1, ks.phrase2, numResults);
                        if (v.length > 0) {
                            synchronized (vy) {
                                vy.add(((float)ks.similarity));
                                vx.add(v);
                                for (svm_node n : v) {
                                    componentMins[n.index] = Math.min(componentMins[n.index], n.value);
                                    componentMaxs[n.index] = Math.max(componentMaxs[n.index], n.value);
                                }
                            }
                        }
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
        List<Float> Y = Arrays.asList(ArrayUtils.toObject(vy.toArray()));
        componentMins[componentMins.length - 1] = Collections.min(Y);
        componentMaxs[componentMaxs.length - 1] = Collections.max(Y);

        for (int i = 0; i < componentMins.length; i++) {
            LOG.info("component " + i + " ranges from " + componentMins[i] + " to " + componentMaxs[i]);
        }
        LOG.info("coverage is " + 1.0 * vy.size() / gold.size());
        for (int i = 0; i < vx.size(); i++) {
            for (svm_node n : vx.get(i)) {
                normalize(n);
            }
            int yIndex = componentMins.length - 1;
            vy.set(yIndex, (float) normalize(yIndex, vy.get(i)));
        }

        assert(vx.size() == vy.size());
        svm_problem prob = new svm_problem();
        prob.l = vy.size();
        prob.x = new svm_node[prob.l][];
        for(int i=0;i<prob.l;i++)
            prob.x[i] = vx.get(i);
        prob.y = new double[prob.l];
        for(int i=0;i<prob.l;i++)
            prob.y[i] = vy.get(i);

        return prob;
    }

    protected svm_node[] getComponentSimilarities(String phrase1, String phrase2, int numResults) throws IOException, ParseException {
        List<svm_node> nodes = new ArrayList<svm_node>();
        for (int i = 0; i < components.size(); i++) {
            SimilarityMetric m = components.get(i);
            double sim = Double.NaN;
            if (numResults <= 0) {
                sim = m.similarity(phrase1, phrase2);
            } else {
                double s1 = getSimilarity(m, phrase1, phrase2, numResults);
                double s2 = getSimilarity(m, phrase2, phrase1, numResults);
                s1 = Double.isNaN(s1) ? 0.0 : s1;
                s2 = Double.isNaN(s2) ? 0.0 : s2;
                sim = (s1 + s2) / 2.0;
            }
            if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                svm_node n = new svm_node();
                n.index = i;
                n.value = (float)sim;
                nodes.add(n);
            }
        }
        return nodes.toArray(new svm_node[0]);
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
    protected double getSimilarity(SimilarityMetric metric, String phrase1, String phrase2, int numResults) throws IOException {
        TIntDoubleHashMap concepts = new TIntDoubleHashMap();
        for (Map.Entry<String, Float> entry : mapper.map(phrase2, 10).entrySet()) {
            Document d = helper.titleToLuceneDoc(entry.getKey());
            if (d == null || d.getFields(Page.FIELD_TYPE).length == 0 || d.get(Page.FIELD_TYPE).equals("normal")) {
                int wpId = Integer.valueOf(d.get(Page.FIELD_WPID));
                concepts.put(wpId, entry.getValue());
            }
        }
        DocScoreList top =  metric.mostSimilar(phrase1, numResults);
        if (top == null) {
            return Double.NaN;
        }
        double bestScore = -Double.MAX_VALUE;
        double bestSim = Double.NaN;
        for (DocScore ds : top) {
            if (concepts.containsKey(ds.getId())) {
                double score = ds.getScore() * concepts.get(ds.getId());
                if (score > bestScore) {
                    bestSim = ds.getScore();
                    bestScore = score;
                }
            }
        }
        return bestSim;
    }

    public svm_parameter getParams(svm_problem problem) {
        svm_parameter param = new svm_parameter();
        // default values
        param.svm_type = svm_parameter.NU_SVR;
        param.kernel_type = svm_parameter.RBF;
        param.degree = 3;
        param.gamma = 1.0 / components.size();
        param.coef0 = 0;
        param.nu = 0.5;
        param.cache_size = 100;
        param.C = 1;
        param.eps = 1e-3;
        param.p = 0.1;
        param.shrinking = 1;
        param.probability = 0;
        param.nr_weight = 0;
        param.weight_label = new int[0];
        param.weight = new double[0];
        return param;
    }

    protected void normalize(svm_node n) {
        n.value = normalize(n.index, n.value);
    }
    protected double normalize(int i, double v) {
        double min = componentMins[i];
        double max = componentMaxs[i];
        v = Math.max(v, min);
        v = Math.min(v, max);
        v = RESCALED_MIN + (RESCALED_MAX - RESCALED_MIN) *
                (v - min) / (max - min);
        return v;
    }

    protected double unnormalize(int i, double v) {
        double min = componentMins[i];
        double max = componentMaxs[i];
        v = Math.max(v, RESCALED_MIN);
        v = Math.min(v, RESCALED_MAX);
        v = min + (max - min) * (v - RESCALED_MIN) / (RESCALED_MAX - RESCALED_MIN);
        return v;
    }

    private double do_cross_validation(svm_problem prob, svm_parameter param, int nr_fold)
    {
        int i;
        double total_error = 0;
        double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
        double[] target = new double[prob.l];

        svm.svm_cross_validation(prob,param,nr_fold,target);
        for(i=0;i<prob.l;i++)
        {
            double y = prob.y[i];
            double v = target[i];
            total_error += (v-y)*(v-y);
            sumv += v;
            sumy += y;
            sumvv += v*v;
            sumyy += y*y;
            sumvy += v*y;
        }
//        LOG.info("Cross Validation Mean squared error = " + total_error / prob.l + "\n");
        double pearson = Math.sqrt(
                (prob.l * sumvy - sumv * sumy) * (prob.l * sumvy - sumv * sumy) /
                ((prob.l * sumvv - sumv * sumv) * (prob.l * sumyy - sumy * sumy)));
//        LOG.info("Cross Validation Squared correlation coefficient = " + pearson + "\n");
        return pearson;
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
        EnsembleSimilarity ensemble = new EnsembleSimilarity(
                conf.getMapper(),
                conf.getHelper()
        );
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
        ensemble.write(new File(args[2]));

        // test it!
        ensemble.read(new File(args[2]));
    }
}