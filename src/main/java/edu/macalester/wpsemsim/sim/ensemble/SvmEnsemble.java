package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.RangeNormalizer;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.normalize.BaseNormalizer;
import gnu.trove.map.hash.TIntDoubleHashMap;
import libsvm.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;


/**
 * An SVM implementation of a supervised ensemble of similarity metrics.
 */
public class SvmEnsemble implements Ensemble {
    private static final Logger LOG = Logger.getLogger(SvmEnsemble.class.getName());
    private static final Double MIN_VALUE = -1.0;
    private static final Double MAX_VALUE = +1.0;
    private static final double P_EPSIONS[] = { 1.0, 0.5, 0.1, 0.01, 0.001 };
    private static final double P_CS[] = { 16, 8, 4, 2, 1, 0.5, 0.2, 0.1 };

    private List<SimilarityMetric> components;

    private BaseNormalizer componentStats[];
    private BaseNormalizer yStats = new RangeNormalizer(MIN_VALUE, MAX_VALUE, false);

    private svm_model model;
    private svm_parameter param;
    private BufferedWriter svmLog;
    private List<Example> examples = new ArrayList<Example>();

    public SvmEnsemble() throws IOException {
        this(new ArrayList<SimilarityMetric>());
    }
    public SvmEnsemble(List<SimilarityMetric> components) throws IOException {
        this.components = components;
        this.svmLog = new BufferedWriter(new FileWriter(new File("svm.log"), true));
        svm.svm_set_print_string_function(new svm_print_interface() {
            @Override
            public void print(String s) {
                try {
                    svmLog.write(new Date() + ": " + s + "\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void setComponents(List<SimilarityMetric> components) {
        this.components = components;
    }

    @Override
    public void train(List<Example> examples) {
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("no examples to train on!");
        }
        this.examples = examples;

        calculateComponentStats();

        for (int i = 0; i < componentStats.length; i++) {
            BaseNormalizer s = componentStats[i];
            LOG.info("component " + components.get(i).getName() + ": " + s);
        }
        LOG.info("Y: " + yStats);
        svm_problem prob = makeProblem();
        svm_parameter param = getParams(prob);

        String error_msg = svm.svm_check_parameter(prob, param);
        if(error_msg != null) {
            throw new IllegalArgumentException(error_msg);
        }
        double bestP = -1.0;
        double bestC = -1.0;
        double smallestError = Double.MAX_VALUE;
        double largestPearson = Double.MIN_VALUE;

        for (double p : P_EPSIONS) {
            for (double c : P_CS) {
                param.p = p;
                param.C = c;
                double stats[] = do_cross_validation(prob, param, 7);
                double error2 = stats[0];
                double pearson = stats[1];

//                System.err.println("CROSSFOLD FOR p=" + p + ", c=" + c + " is " + correlation);
                if (error2 < smallestError) {
                    smallestError = error2;
                    largestPearson = pearson;
                    bestP = p;
                    bestC = c;
                }
            }
        }
        param.p = bestP;
        param.C = bestC;
        this.param = param;
        LOG.info("Choosing p=" + bestP + ", c=" + bestC + " with error=" + smallestError + ", pearson=" + largestPearson);
        this.model = svm.svm_train(prob, param);
    }

    @Override
    public double predict(Example ex, boolean truncate) {
        assert(ex.sims.size() == components.size());
        svm_node nodes[] = simsToNodes(ex, truncate);
        double p = svm.svm_predict(model, nodes);
        return yStats.unnormalize(p);
    }


    public svm_problem makeProblem() {
        svm_problem prob = new svm_problem();
        prob.l = examples.size();
        prob.x = new svm_node[prob.l][];
        prob.y = new double[prob.l];
        int i = 0;
        int num_cells = 0;
        boolean hasReverse = examples.get(0).hasReverse();
        for (Example x : examples) {
            assert(hasReverse == x.hasReverse());
            num_cells += x.getNumNotNan();
            prob.x[i] = simsToNodes(x, false);
            prob.y[i] = yStats.normalize(x.label.similarity);
            i++;
        }
        LOG.info("overall sparsity is " + 1.0 * num_cells / (examples.size() * components.size()));

        return prob;
    }

    protected svm_node[] simsToNodes(Example ex, boolean truncate) {
//        assert(ex.sims.size() == components.size());
        if (ex.hasReverse()) { assert(ex.sims.size() == ex.reverseSims.size()); }

//        svm_node nodes[] = new svm_node[components.size()];
        List<svm_node> nodes = new ArrayList<svm_node>();
        for (int si = 0; si < ex.sims.size(); si++) {
            ComponentSim cs1 = ex.sims.get(si);
            ComponentSim cs2 = (ex.reverseSims == null) ? null : ex.reverseSims.get(si);
            assert(cs2 == null || cs1.component == cs2.component);

            svm_node n = new svm_node();
            n.index = cs1.component;
            n.value = Double.NaN;
            BaseNormalizer s = componentStats[n.index];

            if (ex.hasReverse()) {
                if (cs1.hasValue() || cs2.hasValue()) {
                    double s1 = cs1.hasValue() ? cs1.sim : s.min;
                    double s2 = cs2.hasValue() ? cs2.sim : s.min;
                    n.value = s.normalize((s1 + s2) / 2.0);
                }
            } else {
                if (cs1.hasValue()) {
                    n.value = s.normalize(cs1.sim);
                }
            }
            if (!Double.isNaN(n.value)) {
                nodes.add(n);
            }
        }
        return nodes.toArray(new svm_node[0]);

        /* TODO: try imputing missing values
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null) {
                Stats s = componentStats[i];

            }
        }
        return nodes;
        */
    }

    protected void calculateComponentStats() {
        componentStats = new BaseNormalizer[components.size()];
        for (int i = 0; i < components.size(); i++) {
            componentStats[i] = new RangeNormalizer(MIN_VALUE, MAX_VALUE, false);
        }
        yStats = new RangeNormalizer(MIN_VALUE, MAX_VALUE, false);

        for (Example x : examples) {
            yStats.observe(x.label.similarity);
            for (int i = 0; i < components.size(); i++) {
                ComponentSim cs = x.sims.get(i);
                componentStats[i].observe(cs.maxSim);
                componentStats[i].observe(cs.minSim);
                if (x.hasReverse()) {
                    cs = x.reverseSims.get(i);
                    componentStats[i].observe(cs.maxSim);
                    componentStats[i].observe(cs.minSim);
                }
            }
        }
        yStats.observationsFinished();
        for (BaseNormalizer normalizer : componentStats) {
            normalizer.observationsFinished();
        }
    }

    public svm_parameter getParams(svm_problem problem) {
        svm_parameter param = new svm_parameter();
        // default values
        param.svm_type = svm_parameter.NU_SVR;
        param.kernel_type = svm_parameter.LINEAR;
        param.degree = 2;
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

    private double[] do_cross_validation(svm_problem prob, svm_parameter param, int nr_fold)
    {
        int i;
        double total_error = 0;
        double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
        double[] target = new double[prob.l];

        svm.svm_cross_validation(prob, param, nr_fold, target);
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
//        System.err.println("total error is " + total_error + ", l=" + prob.l);
        return new double[] { total_error / prob.l, pearson}; // return mse
    }

    @Override
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
                new FileOutputStream(new File(directory, "stats.X")));
        out.writeObject(componentStats);
        out.close();

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "stats.Y")));
        out.writeObject(yStats);
        out.close();

        String names = StringUtils.join(getComponentNames(), ", ");
        FileUtils.write(new File(directory, "component_names.txt"), names);
    }

    @Override
    public void read(File directory) throws IOException {
        if (!directory.isDirectory()) {
            throw new FileNotFoundException(directory.toString());
        }

        String expected = StringUtils.join(getComponentNames(), ", ");
        String actual = FileUtils.readFileToString(new File(directory, "component_names.txt"));
        if (!expected.trim().equals(actual.trim())) {
            throw new IOException(
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
                    new FileInputStream(new File(directory, "stats.X")));
            componentStats = (BaseNormalizer[]) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "stats.Y")));
            yStats = (BaseNormalizer) in.readObject();
            in.close();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }

        debug();
    }

    protected void debug() {
        TIntDoubleHashMap coeffs = new TIntDoubleHashMap();
        assert(model.sv_coef.length == 1);
        assert(model.SV.length == model.l);
        assert(model.sv_coef[0].length == model.l);

        for (int i = 0; i < model.l; i++) {
            svm_node vector[] = model.SV[i];
            double w = model.sv_coef[0][i];
            for (svm_node n : vector) {
                coeffs.adjustOrPutValue(n.index, w * n.value, w * n.value);
            }
        }

        System.err.println("SVs:");
        System.err.println("\tintercept:" + model.rho[0]);
        for (int i = 0; i < components.size(); i++) {
            double w = coeffs.contains(i) ? coeffs.get(i) : 0;
            System.err.println("\t" + w + " * " + components.get(i).getName());
        }
    }

    protected List<String> getComponentNames() {
        List<String> names = new ArrayList<String>();
        for (SimilarityMetric m : components) {
            names.add(m.getName());
        }
        return names;
    }

}
