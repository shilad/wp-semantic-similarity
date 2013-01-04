package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.*;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Writes an ARFF file suitable for weka analysis.
 * Does not actually generate predictions, but useful for exploring ensemble learners via Weka.
 */
public class WekaEnsemble implements Ensemble {
    private List<SimilarityMetric> components;
    private FeatureGenerator generator = new FeatureGenerator();

    StringBuffer outputBuffer = new StringBuffer();

    public WekaEnsemble() {}

    @Override
    public void setComponents(List<SimilarityMetric> components) {
        this.components = components;
        this.generator.setComponents(components);

    }

    @Override
    public void train(List<Example> examples) {
        generator.train(examples);

        try {
            this.writeArff(examples);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeArff(List<Example> examples) throws IOException {
        outputBuffer.setLength(0);
        outputBuffer.append("@relation similarities\n\n");
        for (String name : generator.getFeatureNames()) {
            outputBuffer.append("@attribute " + name + " real\n");
    }
        outputBuffer.append("@attribute sim real\n");

        outputBuffer.append("@data\n");
        for (Example x : examples) {
            Map<Integer, Double> features = generator.generate(x);
            int maxIndex = Collections.max(features.keySet());
            for (int i = 0; i <= maxIndex; i++) {
                Double val = features.get(i);
                if (val == null || Double.isNaN(val)) {
                    outputBuffer.append("?");
                } else {
                    outputBuffer.append(val);
                }
                outputBuffer.append(",");
            }
            outputBuffer.append("" + x.label.similarity + "\n");
        }
    }

    @Override
    public double predict(Example ex, boolean truncate) { throw new NoSuchMethodError(); }

    @Override
    public void write(File directory) throws IOException {
        FileUtils.write(new File(directory, "problem.arff"), outputBuffer);
    }

    @Override
    public void read(File directory) throws IOException {}
}
