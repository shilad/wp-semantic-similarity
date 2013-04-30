package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.concepts.Disambiguator;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.*;
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
        final TDoubleArrayList X = new TDoubleArrayList();
        final TDoubleArrayList Y = new TDoubleArrayList();
        final TDoubleArrayList allX = new TDoubleArrayList();
        final Disambiguator dab = new Disambiguator(mapper, metric, helper, 500);

        ParallelForEach.loop(gold, Runtime.getRuntime().availableProcessors() ,
                new Function<KnownSim>() {
                    public void call(KnownSim ks) throws Exception {
                        double sim = Double.NaN;
                        try {
                            Disambiguator.Match m = dab.disambiguateMostSimilar(ks, 500, null);
                            if (m != null) {
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
                            }
                        } finally {
                            allX.add(sim);  // add Double.NaN on errors.
                        }
                    }
                });

        if (X.size() < 4) {
            LOG.info("metric " + metric.getName() + " only produced " + X.size() + " similarity scores.");
            LOG.info("skipping calculation of Pearson, etc.");
            return null;
        } else {
            double pearson = new PearsonsCorrelation().correlation(X.toArray(), Y.toArray());
            double spearman = new SpearmansCorrelation().correlation(X.toArray(), Y.toArray());
            return new Object[] { pearson, spearman, 1.0 * X.size() / gold.size(), allX.toArray() };
        }
    }
    /**
     * Calculates the pearson correlation between the metric and the gold standard
     * @param metric
     * @return [pearson-correlation, coverage between 0 and 1.0, all y values]
     * @throws IOException
     */
    public Object[] calculateSimilarityCorrelation(final SimilarityMetric metric) throws IOException, ParseException {
        final TDoubleArrayList X = new TDoubleArrayList();
        final TDoubleArrayList Y = new TDoubleArrayList();
        final TDoubleArrayList allX = new TDoubleArrayList();
        final Disambiguator dab = new Disambiguator(mapper, metric, helper, 500);

        ParallelForEach.loop(gold, Runtime.getRuntime().availableProcessors() ,
                new Function<KnownSim>() {
                    public void call(KnownSim ks) throws Exception {
                        double sim = Double.NaN;
                        try {
                            Disambiguator.Match m = dab.disambiguateMostSimilar(ks, 500, null);
                            if (m != null && m.hasHintMatch() && m.hasPhraseMatch()) {
                                sim = metric.similarity(m.phraseWpId, m.hintWpId);
                                if (!Double.isInfinite(sim) && !Double.isNaN(sim)) {
                                    synchronized (allX) {
                                        X.add(ks.similarity);
                                        Y.add(sim);
                                    }
                                }
                            }
                        } finally {
                            allX.add(sim);  // add Double.NaN on errors.
                        }
                    }
                });

        if (X.size() < 4) {
            LOG.info("metric " + metric.getName() + " only produced " + X.size() + " similarity scores.");
            LOG.info("skipping calculation of Pearson, etc.");
            return null;
        } else {
            double pearson = new PearsonsCorrelation().correlation(X.toArray(), Y.toArray());
            double spearman = new SpearmansCorrelation().correlation(X.toArray(), Y.toArray());
            return new Object[] { pearson, spearman, 1.0 * X.size() / gold.size(), allX.toArray() };
        }
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
        System.err.println("mode is " + modeStr);

        SimilarityAnalyzer analyzer = new SimilarityAnalyzer(
                mode, gold, env.getMainMapper(), env.getMainIndex());
        BufferedWriter writer = new BufferedWriter(new FileWriter(args[2]));
        analyzer.analyzeMetrics(metrics, writer);
        writer.close();
    }
}
