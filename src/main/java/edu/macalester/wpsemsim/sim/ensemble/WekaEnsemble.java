package edu.macalester.wpsemsim.sim.ensemble;

import edu.macalester.wpsemsim.normalize.*;
import edu.macalester.wpsemsim.sim.SimilarityMetric;

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

    // Output file path
    private File path;

    public WekaEnsemble(File path) {
        this.path = path;
    }

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
                for (float x : s.scores) {
                    for (Normalizer n : normalizers.get(s.component).values()) {
                        n.observe(x);
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
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        writer.write("@relation similarities\n\n");
        for (SimilarityMetric m : components) {
            String metricName = m.getName().toLowerCase().replaceAll("[^a-zA-Z]+", "");
            writeArffHeader(writer, metricName + "1");
            if (examples.get(0).hasReverse()) {
                writeArffHeader(writer, metricName + "2");
            }
    }
        writer.write("@attribute sim real\n");

        writer.write("@data\n");
        for (Example x : examples) {
            assert(x.sims.size() == components.size());
            for (int i = 0; i < x.sims.size(); i++) {
                writeArffEntry(writer, x.sims.get(i), normalizers.get(i));
                if (x.hasReverse()) {
                    writeArffEntry(writer, x.reverseSims.get(i), normalizers.get(i));
                }
            }
            writer.write("" + x.label.similarity + "\n");
        }
    }

    public void writeArffHeader(BufferedWriter writer, String metricName) throws IOException {
        writer.write("@attribute " +  metricName + "length integer\n");
        writer.write("@attribute " +  metricName + "rank integer\n");
        writer.write("@attribute " +  metricName + "min real\n");
        writer.write("@attribute " +  metricName + "max real\n");
        for (String nkey : normalizers.get(0).keySet()) {
            writer.write("@attribute " +  metricName + nkey + " real\n");
        }
    }

    public void writeArffEntry(BufferedWriter writer, ComponentSim sim, Map<String, Normalizer> nmap) throws IOException {
        writer.write(sim.length + ",");
        writer.write("" + (sim.rank == -1 ? "?" : sim.rank) + ",");
        writer.write("" + (Double.isNaN(sim.minSim) ? "?" : sim.minSim) + ",");
        writer.write("" + (Double.isNaN(sim.maxSim) ? "?" : sim.maxSim) + ",");
        for (Normalizer n: nmap.values()) {
            writer.write("" + (Double.isNaN(sim.sim) ? "?" : n.normalize(sim.sim)) + ",");
        }
    }

    @Override
    public double predict(Example ex, boolean truncate) { throw new NoSuchMethodError(); }

    @Override
    public void write(File directory) throws IOException {}

    @Override
    public void read(File directory) throws IOException {}
}
