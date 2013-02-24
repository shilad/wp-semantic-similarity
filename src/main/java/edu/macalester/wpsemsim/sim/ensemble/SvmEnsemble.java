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

    // TODO: Plug in SimilarityFeatureGenerator where appropriate
    private FeatureGenerator featureGenerator = new MostSimilarFeatureGenerator();
    private List<SimilarityMetric> components;

    private BaseNormalizer yNorm = new RangeNormalizer(MIN_VALUE, MAX_VALUE, false);

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
        featureGenerator.setComponents(components);
    }

    @Override
    public void trainSimilarity(List<Example> examples) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void trainMostSimilar(List<Example> examples) {
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("no examples to train on!");
        }
        this.examples = examples;

        // train the y normalizer
        yNorm = new RangeNormalizer(MIN_VALUE, MAX_VALUE, false);
        for (Example x : examples) {
            yNorm.observe(x.label.similarity);
        }
        yNorm.observationsFinished();

        featureGenerator.train(examples);
        LOG.info("Y: " + yNorm);

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
        return svm.svm_predict(model, nodes);
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
            prob.y[i] = yNorm.normalize(x.label.similarity);
            i++;
        }
        LOG.info("overall sparsity is " + 1.0 * num_cells / (examples.size() * components.size()));

        return prob;
    }

    protected svm_node[] simsToNodes(Example ex, boolean truncate) {
        if (!ex.hasReverse()) throw new UnsupportedOperationException();

        Map<Integer, Double> features = featureGenerator.generate(ex);
        svm_node nodes[] = new svm_node[features.size()];
        int ni = 0; // node index
        for (int fi : features.keySet()) {
            nodes[ni] = new svm_node();
            nodes[ni].index = fi;
            nodes[ni].value = features.get(fi);
            ni++;
        }

        return nodes;
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
                new FileOutputStream(new File(directory, "stats.Y")));
        out.writeObject(yNorm);
        out.close();

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "featureGenerator")));
        out.writeObject(featureGenerator);
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
                    new FileInputStream(new File(directory, "stats.Y")));
            yNorm = (BaseNormalizer) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "featureGenerator")));
            featureGenerator = FeatureGenerator.read(in, components, false);
            if (components != null) {
                featureGenerator.setComponents(components);
            }
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
