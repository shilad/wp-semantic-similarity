package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.Env;
import edu.macalester.wpsemsim.utils.KnownSim;
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

    public SimilarityAnalyzer(File goldStandard, ConceptMapper mapper, IndexHelper helper) throws IOException {
        this.mapper = mapper;
        this.helper = helper;
        this.gold = KnownSim.read(goldStandard);
    }

    public void analyzeMetrics(List<SimilarityMetric> metrics, BufferedWriter writer) throws IOException, ParseException {
        NumberFormat format = DecimalFormat.getPercentInstance();
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        RealMatrix simMatrix = new Array2DRowRealMatrix(gold.size(), metrics.size());

        int i = 0;
        for (SimilarityMetric metric : metrics) {
            Object[] r = calculateCorrelation(metric);
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
    public Object[] calculateCorrelation(final SimilarityMetric metric) throws IOException, ParseException {
        final TDoubleArrayList X = new TDoubleArrayList();
        final TDoubleArrayList Y = new TDoubleArrayList();
        final double allX[] = new double[gold.size()];
        Arrays.fill(allX, Double.NaN);
        ExecutorService exec = Executors.newFixedThreadPool(
//                1);
                Runtime.getRuntime().availableProcessors());
//        SimpleRegression reg = new SimpleRegression();
        try {
            for (int i = 0; i < gold.size(); i++) {
                final KnownSim ks = gold.get(i);
                final int finalI = i;
                exec.submit(new Runnable() {
                    public void run() {
                        try {
                            if (finalI % 50 == 0) {
                                LOG.info("calculating metric " + metric.getName() + " gold results for number " + finalI);
                            }
                            double sim = metric.similarity(ks.phrase1, ks.phrase2);
                            if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                                synchronized (X) {
                                    allX[finalI] = sim;
                                    X.add(ks.similarity);
                                    Y.add(sim);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            LOG.log(Level.SEVERE, "error processing similarity entry  " + ks, e);
                        }
                    }
                });
            }
        } finally {
            try {
                Thread.sleep(5000);
                exec.shutdown();
                exec.awaitTermination(60, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                LOG.log(Level.WARNING, "error while awaiting termination:", e);
            }
        }

//        System.err.println("rsquared for fit is " + reg.getRSquare());
        double pearson = new PearsonsCorrelation().correlation(X.toArray(), Y.toArray());
        double spearman = new SpearmansCorrelation().correlation(X.toArray(), Y.toArray());
//        LOG.info("correlation for " + metric.getName() + " is " + pearson);
        return new Object[] { pearson, spearman, 1.0 * X.size() / gold.size(), allX };
    }

    public class MyOLS extends OLSMultipleLinearRegression {
        public double[] getPredictions() {
            return this.getX().operate(this.calculateBeta()).toArray();
        }
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException {
        if (args.length < 3) {
            System.err.println(
                    "usage: java " + SimilarityAnalyzer.class.toString() +
                    " path/to/sim/metric/conf.txt" +
                    " path/to/gold/standard.txt" +
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
                new File(args[1]), env.getMainMapper(), env.getMainIndex());
        BufferedWriter writer = new BufferedWriter(new FileWriter(args[2]));
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (args.length == 3) {
            metrics = conf.loadMetrics();
        } else {
            for (String name : ArrayUtils.subarray(args, 3, args.length)) {
                metrics.add(conf.loadMetric(name));
            }
        }
        analyzer.analyzeMetrics(metrics, writer);
        writer.close();
    }
}