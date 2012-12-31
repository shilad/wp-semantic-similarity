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

    // list of normalizers for each component.
    // Each component's normalizers are a map of normalize name to normalizer.
    private List<Map<String, Normalizer>> normalizers = new ArrayList<Map<String, Normalizer>>();
    StringBuffer outputBuffer = new StringBuffer();

    public WekaEnsemble() {}

    @Override
    public void setComponents(List<SimilarityMetric> components) {
        this.components = components;

        // setup all the normalizers
        for (SimilarityMetric metric : components) {
            Map<String, Normalizer> map = new LinkedHashMap<String, Normalizer>();
            map.put("i", new IdentityNormalizer());
            map.put("r", new RangeNormalizer(-1, +1, false));
            map.put("p", new PercentileNormalizer());
            normalizers.add(map);
        }
    }

    @Override
    public void train(List<Example> examples) {
        for (Example ex : examples) {
            List<ComponentSim> allSims = new ArrayList<ComponentSim>(ex.sims);
            if (ex.hasReverse()) allSims.addAll(ex.reverseSims);
            for (ComponentSim s : allSims) {
                if (s.scores != null) {
                    for (float x : s.scores) {
                        for (Normalizer n : normalizers.get(s.component).values()) {
                            n.observe(x);
                        }
                    }
                }
            }
        }

        for (Map<String, Normalizer> map : normalizers) {
            for (Normalizer n : map.values()) {
                n.observationsFinished();
            }
        }

        try {
            this.writeArff(examples);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeArff(List<Example> examples) throws IOException {
        outputBuffer.setLength(0);
        outputBuffer.append("@relation similarities\n\n");
        for (SimilarityMetric m : components) {
            String metricName = m.getName().toLowerCase().replaceAll("[^a-zA-Z]+", "");
            writeArffHeader(metricName + "1");
            if (examples.get(0).hasReverse()) {
                writeArffHeader(metricName + "2");
            }
    }
        outputBuffer.append("@attribute sim real\n");

        outputBuffer.append("@data\n");
        for (Example x : examples) {
            assert(x.sims.size() == components.size());
            for (int i = 0; i < x.sims.size(); i++) {
                writeArffEntry(x.sims.get(i), normalizers.get(i));
                if (x.hasReverse()) {
                    writeArffEntry(x.reverseSims.get(i), normalizers.get(i));
                }
            }
            outputBuffer.append("" + x.label.similarity + "\n");
        }
    }

    public void writeArffHeader(String metricName) throws IOException {
        outputBuffer.append("@attribute " + metricName + "length integer\n");
        outputBuffer.append("@attribute " + metricName + "rank integer\n");
        outputBuffer.append("@attribute " + metricName + "min real\n");
        outputBuffer.append("@attribute " + metricName + "max real\n");
        for (String nkey : normalizers.get(0).keySet()) {
            outputBuffer.append("@attribute " + metricName + nkey + " real\n");
        }
    }

    public void writeArffEntry(ComponentSim sim, Map<String, Normalizer> nmap) throws IOException {
        outputBuffer.append(sim.length + ",");
        outputBuffer.append("" + (sim.rank == -1 ? "?" : sim.rank) + ",");
        outputBuffer.append("" + (Double.isNaN(sim.minSim) ? "?" : sim.minSim) + ",");
        outputBuffer.append("" + (Double.isNaN(sim.maxSim) ? "?" : sim.maxSim) + ",");
        for (Normalizer n: nmap.values()) {
            outputBuffer.append("" + (Double.isNaN(sim.sim) ? "?" : n.normalize(sim.sim)) + ",");
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
