package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.MathUtils;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.stack.TDoubleStack;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * A linear regression implementation of a supervised ensemble of similarity metrics.
 */
public class LinearEnsemble implements Ensemble {
    private static final Logger LOG = Logger.getLogger(LinearEnsemble.class.getName());

    // feature generators for similarity / mostSimilar
    protected FeatureGenerator similarityGenerator = new SimilarityFeatureGenerator();
    protected FeatureGenerator mostSimilarGenerator = new MostSimilarFeatureGenerator();

    // underlying similarity metrics
    protected List<SimilarityMetric> components;

    // coefficients for linear equations.
    // the first value in the array is the constant term.
    // the length of each array should equal the number of features returned
    // by the generator + 1 because of the constant term.
    // initialize coefficients to the constant 0.
    protected double[] mostSimilarCoefficients = null;
    protected double[] similarityCoefficients = null;

    public LinearEnsemble() throws IOException {
        this(new ArrayList<SimilarityMetric>());
    }

    public LinearEnsemble(List<SimilarityMetric> components) throws IOException {
        this.components = components;
    }

    @Override
    public void setComponents(List<SimilarityMetric> components) {
        this.components = components;
        mostSimilarGenerator.setComponents(components);
        similarityGenerator.setComponents(components);
    }

    @Override
    public void trainSimilarity(List<Example> examples) {
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("no examples to train on!");
        }
        similarityGenerator.train(examples);

        double X[][] = new double[examples.size()][];
        double Y[] = new double[examples.size()];

        int numFeatures = similarityGenerator.getNumFeatures();
        int rowNum = 0;
        for (Example ex : examples) {
            X[rowNum] = similarityGenerator.generate(ex);
            Y[rowNum] = ex.label.similarity;
            if (X[rowNum].length != numFeatures) {
                throw new IllegalStateException(
                        "num features returned by similarity generator is inconsistent");
            }
            //System.out.println("row is y=" + Y[rowNum] + ", x=" + Arrays.toString(X[rowNum]));
            rowNum++;
        }

        // find colinear columns
        int colinearCols[][] = MathUtils.findColinearColumns(X);
        TIntList isColinear = new TIntArrayList();
        for (int colIds[] : colinearCols) {
            isColinear.addAll(colIds);
        }
        isColinear.sort();

        // create a pruned matrix without colinear columns
        double prunedX[][] = new double[X.length][];
        for (int i = 0; i < X.length; i++) {
            TDoubleList prunedRow = new TDoubleArrayList();
            for (int j = 0; j < X[i].length; j++) {
                if (!isColinear.contains(j)) {
                    prunedRow.add(X[i][j]);
                }
            }
            prunedX[i] = prunedRow.toArray();
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(Y, prunedX);

        // reconstruct unpruned coefficients by inserting (in order) 0 for colinear columns
        TDoubleList coeffs = new TDoubleArrayList(regression.estimateRegressionParameters());
        for (int colId : isColinear.toArray()) {
            coeffs.insert(colId + 1, 0.0);  // +1 is for constant coef at index 0
        }
        this.similarityCoefficients = coeffs.toArray();
        double pearson = Math.sqrt(regression.calculateRSquared());
        LOG.info("equation is " + getSimilarityEquationString());
        LOG.info("pearson for multiple regression is " + pearson);
    }

    @Override
    public void trainMostSimilar(List<Example> examples) {
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("no examples to train on!");
        }
        mostSimilarGenerator.train(examples);

        double X[][] = new double[examples.size()][];
        double Y[] = new double[examples.size()];

        int numFeatures = mostSimilarGenerator.getNumFeatures();
        int rowNum = 0;
        for (Example ex : examples) {
            X[rowNum] = mostSimilarGenerator.generate(ex);
            Y[rowNum] = ex.label.similarity;
            if (X[rowNum].length != numFeatures) {
                throw new IllegalStateException(
                        "num features returned by mostSimilar generator is inconsistent");
            }
            rowNum++;
        }
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(Y, X);

        this.mostSimilarCoefficients = regression.estimateRegressionParameters();
        double pearson = Math.sqrt(regression.calculateRSquared());
        LOG.info("equation is " + getMostSimilarEquationString());
        LOG.info("pearson for multiple regression is " + pearson);
    }

    public String getMostSimilarEquationString() {
        return getEquationString(mostSimilarGenerator, mostSimilarCoefficients);
    }

    public String getSimilarityEquationString() {
        return getEquationString(similarityGenerator, similarityCoefficients);
    }

    private String getEquationString(FeatureGenerator generator, final double coefficients[]) {
        if (coefficients == null || coefficients.length == 0) {
            // if we don't have an equation, use one with all 0's
            return getEquationString(generator, new double [generator.getNumFeatures() + 1]);
        }
        

        List<String> names = generator.getFeatureNames();
        List<Integer> indexes = new ArrayList<Integer>();
        for (int i = 0; i < names.size(); i++) {
            indexes.add(i);
        }

        Collections.sort(indexes, new Comparator<Integer>() {
            public int compare(Integer i1, Integer i2) {
                return new Double(coefficients[i1+1]).compareTo(coefficients[i2 + 1]);
            }
        });
        DecimalFormat f = new DecimalFormat("+ ##.####;- ##.####");
        StringBuffer buffer = new StringBuffer(f.format(coefficients[0]));
        for (int i : indexes) {
            buffer.append(" ");
            buffer.append(f.format(coefficients[i+1]));
            buffer.append(" * ");
            buffer.append(names.get(i));
        }
        return buffer.toString();
    }

    @Override
    public double predictMostSimilar(Example ex, boolean truncate) {
        assert(ex.sims.size() == components.size());
        double features[] = mostSimilarGenerator.generate(ex);
        if (features.length != mostSimilarCoefficients.length - 1) {
            throw new IllegalStateException();
        }
        double sum = mostSimilarCoefficients[0];
        for (int i = 0; i < features.length; i++) {
            sum += mostSimilarCoefficients[i+1] * features[i];
        }
        return sum;
    }

    @Override
    public double predictSimilarity(Example ex, boolean truncate) {
        assert(ex.sims.size() == components.size());
        double features[] = similarityGenerator.generate(ex);
        if (features.length!= similarityCoefficients.length - 1) {
            throw new IllegalStateException();
        }
        double sum = similarityCoefficients[0];
        for (int i = 0; i < features.length; i++) {
            sum += similarityCoefficients[i+1] * features[i];
        }
        return sum;
    }

    @Override
    public void write(File directory) throws IOException {
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
        FileUtils.write(new File(directory, "mostSimilarEquation.txt"), getMostSimilarEquationString());
        FileUtils.write(new File(directory, "similarityEquation.txt"), getSimilarityEquationString());

        ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "mostSimilarGenerator")));
        out.writeObject(mostSimilarGenerator);
        out.close();

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "similarityGenerator")));
        out.writeObject(similarityGenerator);
        out.close();

        String names = StringUtils.join(getComponentNames(), ", ");
        FileUtils.write(new File(directory, "component_names.txt"), names);
    }

    @Override
    public void read(File directory) throws IOException {
        if (components == null || components.size() == 0) {
            throw new IOException("setComponents() must be called before reading in the ensemble");
        }
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
                    new FileInputStream(new File(directory, "mostSimilarGenerator")));
            mostSimilarGenerator = FeatureGenerator.read(in, components, true);
            in.close();
            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "similarityGenerator")));
            similarityGenerator = FeatureGenerator.read(in, components, true);
            in.close();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }

        similarityCoefficients= readCoefficients(
                similarityGenerator,
                new File(directory, "similarityEquation.txt"));
        mostSimilarCoefficients = readCoefficients(
                mostSimilarGenerator,
                new File(directory, "mostSimilarEquation.txt"));
    }

    /**
     * Reads and orders coefficients in an equation file to be consistent with the
     * order of the passed-in feature generator.
     *
     * If a feature in the equation file doesn't correspond to a feature in the
     * generator, an error is thrown.
     *
     * If a feature in the generator is not in the equation, it will have a coefficient of 0.
     *
     * @param generator
     * @param eqPath
     * @return
     * @throws IOException
     */
    private double[] readCoefficients(FeatureGenerator generator, File eqPath) throws IOException {
        LinkedHashMap<String, Double> eq = readEquation(FileUtils.readFileToString(eqPath));

        double coeffs[] = new double[generator.getNumFeatures() + 1];  // don't forget constant

        for (String featureName : eq.keySet()) {
            double val = eq.get(featureName);
            if (featureName.equals("C")) {
                coeffs[0] = val;
            } else {
                int i = generator.getFeatureIndex(featureName);
                if (i < 0) {
                    throw new IOException("feature generator " + generator +
                            " does not have have feature named " + featureName);
                }
                if (i >= generator.getNumFeatures()) {
                    throw new IOException("feature index greater than it should be.");
                }
                coeffs[i+1] = val;
            }
        }

        return coeffs;
    }

    // from http://stackoverflow.com/questions/3681242/java-how-to-parse-double-from-regex
    private static final Pattern PAT_DOUBLE =  Pattern.compile("[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?");
    private static final Pattern PAT_VARIABLE = Pattern.compile("[a-zA-Z0-9_-]*[a-zA-Z]");

    /**
     * Parse an equation in the format:
     *
     * -34.5 + 67 * foo + 3.40 * bar - 0.083 * baz
     *
     * @param s The equation string.
     * @return An ordered hashmap with keys variable names (and "C" for the constant)
     * and coefficient the value.
     *
     * @throws IOException
     */
    protected LinkedHashMap<String, Double> readEquation(String s) throws IOException {
        s = s.replaceAll("\\s+", "");   // remove whitespace
        Matcher m = PAT_DOUBLE.matcher(s);
        if (m.lookingAt()) {
        } else {
            throw new IOException("invalid initial constant in: " + s);
        }
        LinkedHashMap<String, Double> eq = new LinkedHashMap<String, Double>();
        eq.put("C", Double.valueOf(m.group()));
        s = s.substring(m.group().length());

        while (!s.isEmpty()) {
            m = PAT_DOUBLE.matcher(s);
            if (!m.lookingAt()) {
                throw new IOException("invalid coefficient starting at: " + s);
            }
            double c = Double.valueOf(m.group());
            s = s.substring(m.group().length());
            if (s.charAt(0) != '*') {
                throw new IOException("missing multiplication asterisk at: " + s);
            }
            s = s.substring(1);
            m = PAT_VARIABLE.matcher(s);
            if (!m.lookingAt()) {
                throw new IOException("invalid variable starting at: " + s);
            }
            String v = m.group();
            s = s.substring(m.group().length());
            eq.put(v, c);
        }
        return eq;
    }

    protected List<String> getComponentNames() {
        List<String> names = new ArrayList<String>();
        for (SimilarityMetric m : components) {
            names.add(m.getName());
        }
        return names;
    }

}
