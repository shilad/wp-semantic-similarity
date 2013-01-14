package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.sim.SimilarityMetric;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Logger;


/**
 * An linear regression implementation of a supervised ensemble of similarity metrics.
 */
public class LinearEnsemble implements Ensemble {
    private static final Logger LOG = Logger.getLogger(LinearEnsemble.class.getName());

    private FeatureGenerator featureGenerator = new SimilarityFeatureGenerator();
    private List<SimilarityMetric> components;
    private int numFeatures;
    private double[] coefficients;

    public LinearEnsemble() throws IOException {
        this(new ArrayList<SimilarityMetric>());
    }

    public LinearEnsemble(List<SimilarityMetric> components) throws IOException {
        this.components = components;
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
        featureGenerator.train(examples);

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
        List<String> names = getComponentNames();
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
        buffer.append(f.format(coefficients[0]));
        for (int i : indexes) {
            buffer.append(" ");
            buffer.append(f.format(coefficients[i]));
            buffer.append(" * ");
            buffer.append(names.get(i));
        }
        return buffer.toString();
    }

    private double[] exampleFeatures(Example ex) {
        Map<Integer, Double> features = featureGenerator.generate(ex);
        if (features.size() != Collections.max(features.keySet()) + 1) {
            throw new IllegalArgumentException("features array not dense!");
        }
        if (numFeatures < 0) {
            numFeatures = features.size();
        }
        if (features.size() != numFeatures) {
            throw new IllegalArgumentException("expected numFeatures to be " + numFeatures + ", but was " + features.size());
        }

        double result[] = new double[numFeatures];
        for (int i = 0; i < numFeatures; i++) {
            result[i] = features.get(i);
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
        if (directory.isDirectory()) {
            FileUtils.forceDelete(directory);
        }
        directory.mkdirs();

        ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(new File(directory, "coefficients")));
        out.writeObject(coefficients);
        out.close();

        FileUtils.write(new File(directory, "equations.txt"), getEquationString());

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
                    new FileInputStream(new File(directory, "coefficients")));
            coefficients = (double[]) in.readObject();
            in.close();

            in = new ObjectInputStream(
                    new FileInputStream(new File(directory, "featureGenerator")));
            featureGenerator = (FeatureGenerator) in.readObject();
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

}
