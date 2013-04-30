package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.Disambiguator;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.cli.*;
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
import java.util.List;
import java.util.logging.Logger;

public class SimilarityAnalyzer {
    private static final Logger LOG = Logger.getLogger(SimilarityAnalyzer.class.getName());
    private static final int MODE_MOST_SIMILAR = 1;
    private static final int MODE_SIMILARITY = 2;

    private List<KnownSim> gold;
    private ConceptMapper mapper;
    private IndexHelper helper;
    private int mode = MODE_SIMILARITY;
    private int numThreads = Runtime.getRuntime().availableProcessors();

    public SimilarityAnalyzer(int mode, List<KnownSim> gold, ConceptMapper mapper, IndexHelper helper) throws IOException {
        this.mode = mode;
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
            LOG.info("analyzing " + metric.getName());
            Object[] r = (mode == MODE_SIMILARITY) ?
                    calculateSimilarityCorrelation(metric) :
                    calculateMostSimilarCorrelation(metric);
            if (r == null) {
                continue;       // metric failed to produce enough observations.
            }
            Double pearson = (Double) r[0];
            Double spearman = (Double) r[1];
            Double coverage = (Double) r[2];
            double X[] = (double[]) r[3];
            writer.write("analyzing metric: " + metric.getName() + "\n");
            writer.write("\tcoverage=" + (coverage*100) + "%\n");
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
        System.out.println("intercept is " + beta[0]);
        for (int i = 0; i < metrics.size(); i++) {
            System.out.println("coefficient for " + metrics.get(i).getName() + " is " + beta[i+1]);
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
        final Disambiguator dab = new Disambiguator(mapper, metric, helper, 1);
        return accumulateScores(metric, new Function<KnownSim, Double>() {
            @Override
            public Double call(KnownSim ks) throws Exception {
                double sim = Double.NaN;
                DocScoreList dsl;
                if (ks.wpId1 >= 0 && ks.wpId2 >= 0) {
                    dsl = metric.mostSimilar(ks.wpId1, 500);
                } else {
                    dsl = metric.mostSimilar(ks.phrase1, 500);
                }
                if (dsl != null) {
                    int wpId2 = ks.wpId2;
                    if (wpId2 < 0) {
                        wpId2 = dab.bestNaiveDisambiguation(ks.phrase2);
                    }
                    if (wpId2 < 0) {
                        LOG.warning("no mapping for gold standard " + ks.phrase2 + "; skipping it");
                    } else {
                        sim = dsl.getScoreForId(wpId2);
                        if (Double.isNaN(sim)) {
                            sim = dsl.getMissingScore();
                        }
                    }
                }
                return sim;
            }
        });
    }
    /**
     * Calculates the pearson correlation between the metric and the gold standard
     * @param metric
     * @return [pearson-correlation, coverage between 0 and 1.0, all y values]
     * @throws IOException
     */
    public Object[] calculateSimilarityCorrelation(final SimilarityMetric metric) throws IOException, ParseException {
        return accumulateScores(metric, new Function<KnownSim, Double>() {
            @Override
            public Double call(KnownSim ks) throws Exception {
                double sim;
                if (ks.wpId1 >= 0 && ks.wpId2 >= 0) {
                    sim = metric.similarity(ks.wpId1, ks.wpId2);
                } else {
                    sim = metric.similarity(ks.phrase1, ks.phrase2);
                }
                return sim;
            }
        });
    }

    protected Object[] accumulateScores(SimilarityMetric metric, final Function<KnownSim, Double> fn) {
        // Gather X values from similarity metric
        final TDoubleList X = new TDoubleArrayList();
        for (int i = 0; i < gold.size(); i++) { X.add(Double.NaN); }

        // If exceptions occur in the parallel for each, the NaN will stick around.
        ParallelForEach.range(0, gold.size(), numThreads, new Procedure<Integer>() {
            public void call(Integer i) throws Exception {
                double x = fn.call(gold.get(i));
                synchronized (X) { X.set(i, x); }
            }
        });

        // Gather Y values from gold standard
        final TDoubleList Y = new TDoubleArrayList();
        for (KnownSim ks : gold) Y.add(ks.similarity);

        if (X.size() != Y.size()) {
            throw new IllegalStateException("sizes of X and Y do not match!");
        }
        double pruned[][] = MathUtils.removeNotNumberPoints(X.toArray(), Y.toArray());
        double prunedX[] = pruned[0];
        double prunedY[] = pruned[1];
        if (prunedX.length < 4) {
            LOG.info("metric " + metric.getName() + " only produced " + X.size() + " similarity scores.");
            LOG.info("skipping calculation of Pearson, etc.");
            return null;
        } else {
            double pearson = new PearsonsCorrelation().correlation(prunedX, prunedY);
            double spearman = new SpearmansCorrelation().correlation(prunedX, prunedY);
            return new Object[] { pearson, spearman, 1.0 * prunedX.length / gold.size(), X.toArray() };
        }
    }

    public void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public class MyOLS extends OLSMultipleLinearRegression {
        public double[] getPredictions() {
            return this.getX().operate(this.calculateBeta()).toArray();
        }
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException {
        Options options = new Options();
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("mode")
                .withDescription("Mode: similarity or mostSimilar.")
                .create('m'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("name")
                .withDescription("Name of similarity metric that should be built.")
                .hasArgs()
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output file (defaults to stdout).")
                .hasArgs()
                .create('o'));


        EnvConfigurator conf;
        try {
            conf = new EnvConfigurator(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println( "Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SimilarityAnalyzer", options);
            return;
        }

        CommandLine cmd = conf.getCommandLine();
        conf.setShouldReadModels(true);
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();

        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (cmd.getOptionValues("n") == null) {
            metrics.addAll(conf.loadMetrics(true));
        } else {
            for (String name : cmd.getOptionValues("n")) {
                metrics.add(conf.loadMetric(name, true));
            }
        }
        System.err.println("metrics are " + metrics);

        String modeStr = cmd.hasOption("m") ? cmd.getOptionValue("m") : "similarity";
        List<KnownSim> gold;
        int mode;
        if (modeStr.equals("mostSimilar")) {
            mode = MODE_MOST_SIMILAR;
            gold = env.getMostSimilarGold();
        } else if (modeStr.equals("similarity")) {
            mode = MODE_SIMILARITY;
            gold = env.getSimilarityGold();
        } else {
            throw new IllegalArgumentException("invalid mode: " + modeStr);
        }
        BufferedWriter writer;
        if (cmd.hasOption("o")) {
            writer = new BufferedWriter(new FileWriter(cmd.getOptionValue("o")));
        } else {
            writer = new BufferedWriter(new OutputStreamWriter(System.out));
        }

        SimilarityAnalyzer analyzer = new SimilarityAnalyzer(
                mode, gold, env.getMainMapper(), env.getMainIndex());
        analyzer.setNumThreads(env.getNumThreads());
        analyzer.analyzeMetrics(metrics, writer);
        writer.close();
    }
}
