package edu.macalester.wpsemsim.sim;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.DictionaryDatabase;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
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

    public void analyzeMetrics(List<SimilarityMetric> metrics, BufferedWriter writer) throws IOException {
        NumberFormat format = DecimalFormat.getPercentInstance();
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        for (SimilarityMetric metric : metrics) {
            double[] r = calculateCorrelation(metric);
            writer.write("analyzing metric: " + metric.getName() + "\n");
            writer.write("\tcoverage=" + format.format(100.0 * r[1]) + "%\n");
            writer.write("\tpearson=" + r[0] + "\n");
        }
    }

    /**
     * Calculates the pearson correlation between the metric and the gold standard
     * @param metric
     * @return [pearson-correlation, coverage between 0 and 1.0]
     * @throws IOException
     */
    public double[] calculateCorrelation(SimilarityMetric metric) throws IOException {
        TDoubleArrayList X = new TDoubleArrayList();
        TDoubleArrayList Y = new TDoubleArrayList();
        for (int i = 0; i < gold.size(); i++) {
            KnownSim ks = gold.get(i);
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

            int wpId1 = helper.titleToWpId(article1);
            if (wpId1 < 0) {
                LOG.info("couldn't find article with title '" + article1 + "'");
                continue;
            }
            int wpId2 = helper.titleToWpId(article2);
            if (wpId2 < 0) {
                LOG.info("couldn't find article with title '" + article2 + "'");
                continue;
            }

            double sim = metric.similarity(wpId1, wpId2);
            if (Double.isInfinite(sim) || Double.isNaN(sim)) {
                LOG.info("sim between '" + article2 + "' and '" + article2 + "' is NAN or INF");
                continue;
            }

            X.add(ks.similarity);
            Y.add(sim);
        }

        return new double[] {
                new PearsonsCorrelation().correlation(X.toArray(), Y.toArray()),
                1.0 * X.size() / gold.size()
        };
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

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException {
        if (args.length != 3) {
            System.err.println(
                    "usage: java " + RegressionFitter.class.toString() +
                    " path/to/sim/metric/conf.txt" +
                    " path/to/gold/standard.txt" +
                    " path/to/reg/model_output.txt"
            );
            System.exit(1);
        }
        SimilarityMetricConfigurator conf = new SimilarityMetricConfigurator(
                new ConfigurationFile(new File(args[0])));
        RegressionFitter fitter = new RegressionFitter(
                new File(args[1]),
                conf.buildConceptMapper(),
                conf.buildIndexHelper()
        );
        BufferedWriter writer = new BufferedWriter(new FileWriter(args[2]));
        fitter.analyzeMetrics(conf.loadAllMetrics(), writer);
        writer.close();
    }
}