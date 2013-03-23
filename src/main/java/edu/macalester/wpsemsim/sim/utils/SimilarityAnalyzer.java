package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.Disambiguator;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.normalize.IdentityNormalizer;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimilarityAnalyzer {
    private static final Logger LOG = Logger.getLogger(SimilarityAnalyzer.class.getName());

    private List<KnownSim> gold;
    private ConceptMapper mapper;
    private IndexHelper helper;

    public SimilarityAnalyzer(List<KnownSim> gold, ConceptMapper mapper, IndexHelper helper) throws IOException {
        this.mapper = mapper;
        this.helper = helper;
        this.gold = gold;
    }

    public void analyzeMetrics(List<SimilarityMetric> metrics, BufferedWriter writer) throws IOException, ParseException {
        NumberFormat format = DecimalFormat.getPercentInstance();
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        RealMatrix simMatrix = new Array2DRowRealMatrix(gold.size(), metrics.size());

        int i = 0;
        for (SimilarityMetric metric : metrics) {
            Object[] r = calculateMostSimilarCorrelation(metric);
            Double pearson = (Double) r[0];
            Double spearman = (Double) r[1];
            Double coverage = (Double) r[2];
            double X[] = (double[]) r[3];
            writer.write("analyzing metric: " + metric.getName() + "\n");
            writer.write("\tcoverage=" + coverage + "%\n");
            writer.write("\tpearson=" + pearson + "\n");
            writer.write("\tspearman=" + spearman + "\n");
            simMatrix.setColumn(i++, X);
        }

        fit(metrics, simMatrix);
    }

    private void fit(List<SimilarityMetric> metrics, RealMatrix simMatrix) {

        // calculate indexes that have full coverage
        int numCovered = 0;
        for (int i = 0; i < gold.size(); i++) {
            if (!simMatrix.getRowVector(i).isNaN()) {
                numCovered++;
            }
        }
        LOG.info("full coverage on " + numCovered + " of  " + gold.size());
        RealMatrix covered = new Array2DRowRealMatrix(numCovered, simMatrix.getColumnDimension());
        double Y[] = new double[numCovered];
        int j = 0;
        for (int i = 0; i < gold.size(); i++) {
            if (!simMatrix.getRowVector(i).isNaN()) {
                covered.setRow(j, simMatrix.getRow(i));
                Y[j] = gold.get(i).similarity;
                j++;
            }
        }

        assert(j == numCovered);

        MyOLS ols = new MyOLS();
        ols.newSampleData(Y, covered.getData());

        double beta[] = ols.estimateRegressionParameters();
        for (int i = 0; i < metrics.size(); i++) {
            System.out.println("coefficient for " + metrics.get(i).getName() + " is " + beta[i]);
            System.out.println("pearson is " + pearson(covered.getColumn(i), Y));
            System.out.println("spearman is " + spearman(covered.getColumn(i), Y));
        }
        System.out.println("R-squared is " + ols.calculateRSquared());
        System.out.println("Pearson is " + pearson(Y, ols.getPredictions()));
        System.out.println("Spearman is " + spearman(Y, ols.getPredictions()));
    }

    private double pearson(double X[], double Y[]) {
        return new PearsonsCorrelation().correlation(X, Y);
    }

    private double spearman(double X[], double Y[]) {
        return new SpearmansCorrelation().correlation(X, Y);
    }

    /**
     * Calculates the pearson correlation between the metric and the gold standard
     * @param metric
     * @return [pearson-correlation, coverage between 0 and 1.0, all y values]
     * @throws IOException
     */
    public Object[] calculateMostSimilarCorrelation(final SimilarityMetric metric) throws IOException, ParseException {
        final TDoubleArrayList X = new TDoubleArrayList();
        final TDoubleArrayList Y = new TDoubleArrayList();
        final TDoubleArrayList allX = new TDoubleArrayList();
        final Disambiguator dab = new Disambiguator(mapper, metric, helper, 500);

        ParallelForEach.loop(gold, Runtime.getRuntime().availableProcessors() ,
                new Function<KnownSim>() {
                    public void call(KnownSim ks) throws Exception {
                        double sim = Double.NaN;
                        try {
                            Disambiguator.Match m = dab.disambiguateMostSimilar(ks.phrase1, ks.phrase2, 500, null);
                            DocScoreList dsl = metric.mostSimilar(m.phraseWpId, 500);
                            if (dsl != null) {
                                sim = dsl.getScoreForId(m.hintWpId);
                                if (Double.isNaN(sim)) {
                                    sim = dsl.getMissingScore();
                                }
                            }
                            if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                                synchronized (allX) {
                                    X.add(ks.similarity);
                                    Y.add(sim);
                                }
                            }
                        } finally {
                            allX.add(sim);  // add Double.NaN on errors.
                        }
                    }
                });

        double pearson = new PearsonsCorrelation().correlation(X.toArray(), Y.toArray());
        double spearman = new SpearmansCorrelation().correlation(X.toArray(), Y.toArray());
        return new Object[] { pearson, spearman, 1.0 * X.size() / gold.size(), allX.toArray() };
    }
    /**
     * Calculates the pearson correlation between the metric and the gold standard
     * @param metric
     * @return [pearson-correlation, coverage between 0 and 1.0, all y values]
     * @throws IOException
     */
    public Object[] calculateCorrelation(final SimilarityMetric metric) throws IOException, ParseException {
        final TDoubleArrayList X = new TDoubleArrayList();
        final TDoubleArrayList Y = new TDoubleArrayList();
        final TDoubleArrayList allX = new TDoubleArrayList();

        ParallelForEach.loop(gold, Runtime.getRuntime().availableProcessors() ,
                new Function<KnownSim>() {
                    public void call(KnownSim ks) throws Exception {
                        double sim = Double.NaN;
                        try {
                            sim = metric.similarity(ks.phrase1, ks.phrase2);
                            if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                                synchronized (allX) {
                                    X.add(ks.similarity);
                                    Y.add(sim);
                                }
                            }
                        } finally {
                            allX.add(sim);  // add Double.NaN on errors.
                        }
                    }
                });

        double pearson = new PearsonsCorrelation().correlation(X.toArray(), Y.toArray());
        double spearman = new SpearmansCorrelation().correlation(X.toArray(), Y.toArray());
        return new Object[] { pearson, spearman, 1.0 * X.size() / gold.size(), allX.toArray() };
    }

    public class MyOLS extends OLSMultipleLinearRegression {
        public double[] getPredictions() {
            return this.getX().operate(this.calculateBeta()).toArray();
        }
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException {
        if (args.length < 2) {
            System.err.println(
                    "usage: java " + SimilarityAnalyzer.class.toString() +
                    " path/to/sim/metric/conf.txt" +
                    " path/to/reg/model_output.txt" +
                    " [sim1 sim2 ...]"
            );
            System.exit(1);
        }
        EnvConfigurator conf = new EnvConfigurator(
                new ConfigurationFile(new File(args[0])));
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();

        SimilarityAnalyzer analyzer = new SimilarityAnalyzer(
                env.getGold(), env.getMainMapper(), env.getMainIndex());
        BufferedWriter writer = new BufferedWriter(new FileWriter(args[2]));
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (args.length == 2) {
            metrics = conf.loadMetrics(true);
        } else {
            for (String name : ArrayUtils.subarray(args, 2, args.length)) {
                SimilarityMetric metric = conf.loadMetric(name, true);
                System.err.println("normalizer is: " +
                        ((BaseSimilarityMetric) metric).getNormalizer().dump());
                metrics.add(metric);
            }
        }
        analyzer.analyzeMetrics(metrics, writer);
        writer.close();
    }
}