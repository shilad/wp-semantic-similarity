package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

public class RegressionFitter {
    private static final Logger LOG = Logger.getLogger(RegressionFitter.class.getName());

    private List<KnownSim> gold;
    private ConceptMapper mapper;
    private IndexHelper helper;

    public RegressionFitter(File goldStandard, ConceptMapper mapper, IndexHelper helper) throws IOException {
        this.mapper = mapper;
        this.helper = helper;
        this.gold = readGoldStandard(goldStandard);
    }

    public void calculateCorrelation(SparseMatrix matrix) {
        double X[] = new double[gold.size()];
        double Y[] = new double[gold.size()];
        Arrays.fill(Y, -1.0);
        for (int i = 0; i < gold.size(); i++) {
            KnownSim ks = gold.get(i);
            X[i] = ks.similarity;
            LinkedHashMap<String, Float> concept1s = mapper.map(ks.phrase1);
            LinkedHashMap<String, Float> concept2s= mapper.map(ks.phrase2);

            if (concept1s.isEmpty()) {
                LOG.info("no concepts for phrase " + ks.phrase1);
            }
            if (concept2s.isEmpty()) {
                LOG.info("no concepts for phrase " + ks.phrase2);
            }
            if (concept1s.isEmpty() || concept2s.isEmpty()) {
                continue;
            }
            // for, now choose the first concepts
            String article1 = concept1s.keySet().iterator().next();
            String article2 = concept2s.keySet().iterator().next();
        }
    }

    private List<KnownSim> readGoldStandard(File path) throws IOException {
        List<KnownSim> result = new ArrayList<KnownSim>();
        BufferedReader reader = new BufferedReader(new FileReader(path));
        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;
            String tokens[] = line.split("\t");
            if (tokens.length == 3) {
                result.add(new KnownSim(
                        tokens[0],
                        tokens[1],
                        Double.valueOf(tokens[2])
                ));
            } else {
                LOG.info("invalid line in gold standard file " + path + ": " +
                        "'" + StringEscapeUtils.escapeJava(line) + "'");
            }
        }
        return result;
    }


    class KnownSim {
        String phrase1;
        String phrase2;
        double similarity;

        KnownSim(String phrase1, String phrase2, double similarity) {
            this.phrase1 = phrase1;
            this.phrase2 = phrase2;
            this.similarity = similarity;
        }
    }
}