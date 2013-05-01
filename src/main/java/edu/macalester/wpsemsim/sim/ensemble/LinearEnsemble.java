package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.sim.SimilarityMetric;
import gnu.trove.map.hash.TIntDoubleHashMap;
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
 * An linear regression implementation of a supervised ensemble of similarity metrics.
 */
public class LinearEnsemble implements Ensemble {
    private static final Logger LOG = Logger.getLogger(LinearEnsemble.class.getName());

    protected FeatureGenerator similarityGenerator = new SimilarityFeatureGenerator();
    protected FeatureGenerator mostSimilarGenerator = new MostSimilarFeatureGenerator();
    protected List<SimilarityMetric> components;
    protected int numFeatures = -1;
    protected double[] coefficients;

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
        throw new UnsupportedOperationException();
    }

    @Override
    public void trainMostSimilar(List<Example> examples) {
        if (examples.isEmpty()) {
            throw new IllegalArgumentException("no examples to train on!");
        }
        mostSimilarGenerator.train(examples);

        double X[][] = new double[examples.size()][];
        double Y[] = new double[examples.size()];

        this.numFeatures = -1;
        int rowNum = 0;
        for (Example ex : examples) {
            X[rowNum] = exampleFeatures(ex);
            Y[rowNum] = ex.label.similarity;
            rowNum++;
        }
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(Y, X);

        this.coefficients = regression.estimateRegressionParameters();
        double pearson = Math.sqrt(regression.calculateRSquared());
        LOG.info("equation is " + getEquationString());
        LOG.info("pearson for multiple regression is " + pearson);
    }

    public String getEquationString() {
        List<String> names = mostSimilarGenerator.getFeatureNames();
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

    private double[] exampleFeatures(Example ex) {
        double features[] = mostSimilarGenerator.generate(ex);
        if (numFeatures < 0) {
            numFeatures = features.length;
        }
        if (features.length != numFeatures) {
            throw new IllegalArgumentException("expected numFeatures to be " + numFeatures + ", but was " + features.length);
        }

        double result[] = new double[numFeatures];
        for (int i = 0; i < numFeatures; i++) {
            result[i] = features[i];
        }
        return result;
    }

    @Override
    public double predict(Example ex, boolean truncate) {
        assert(ex.sims.size() == components.size());
        double features[] = exampleFeatures(ex);
        double sum = coefficients[0];
        if (features.length!= coefficients.length - 1) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < features.length; i++) {
            sum += coefficients[i+1] * features[i];
        }
        return sum;
    }

    @Override
    public void write(File directory) throws IOException {
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }

        ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "coefficients")));
        out.writeObject(coefficients);
        out.close();

        FileUtils.write(new File(directory, "equation.txt"), getEquationString());

        out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "mostSimilarGenerator")));
        out.writeObject(mostSimilarGenerator);
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
                    new FileInputStream(new File(directory, "coefficients")));
            coefficients = (double[]) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "mostSimilarGenerator")));
            mostSimilarGenerator = FeatureGenerator.read(in, components, true);
            in.close();

            LinkedHashMap<String, Double> eq = readEquation(
                    FileUtils.readFileToString(new File(directory, "equation.txt")));

            int maxIndex = -1;
            TIntDoubleHashMap indexCoeffs = new TIntDoubleHashMap();
            for (String featureName : eq.keySet()) {
                if (!featureName.equals("C")) {
                    int i = mostSimilarGenerator.getFeatureIndex(featureName);
                    if (i < 0) {
                        throw new IOException("feature generator in " +
                                new File(directory, "mostSimilarGenerator") +
                                " does not have have feature named " + featureName);
                    }
                    maxIndex = Math.max(i, maxIndex);
                    indexCoeffs.put(i, eq.get(featureName));
                }
            }


            coefficients = new double[maxIndex+2];  // don't forget constant
            coefficients[0] = eq.get("C");
            for (int i = 0; i <= maxIndex; i++) {
                coefficients[i+1] = indexCoeffs.get(i);
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
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
