package edu.macalester.wpsemsim.sim.ensemble;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.utils.EnvConfigurator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.DefaultOptionBuilder;
import edu.macalester.wpsemsim.utils.Env;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class EnsembleMain {
    public static Logger LOG = Logger.getLogger(EnsembleMain.class.getName());

    public static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    public static final int DEFAULT_NUM_RESULTS = 500;

    public static TIntSet readIds(String path) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        TIntSet ids = new TIntHashSet();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            ids.add(Integer.valueOf(line.trim()));
        }
        return ids;
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException, ClassNotFoundException {
        Options options = new Options();
        options.addOption("t", "threads", true, "number of threads");
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("conf")
                .withDescription("Path to configuration file.")
                .create('c'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("names")
                .withDescription("Names of similarity metrics that should be included.")
                .hasArgs()
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("gold")
                .withDescription("Path to gold standard")
                .hasArgs()
                .create('g'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("ensemble")
                .withDescription("Ensemble type ('weka' or 'svm').")
                .hasArgs()
                .create('e'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output file.")
                .create('o'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("results")
                .withDescription("Maximum number of similar wikipedia pages.")
                .create('r'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("validIds")
                .withDescription("Ids that can be included in results list.")
                .create('v'));

        CommandLine cmd;

        // create the parser
        try {
            // parse the command line arguments
            CommandLineParser parser = new PosixParser();
            cmd = parser.parse(options, args);
        } catch( org.apache.commons.cli.ParseException exp ) {
            System.err.println( "Invalid option usage: " + exp.getMessage());
            new HelpFormatter().printHelp( "EnsembleMain", options );
            return;
        }
        String ensembleType = cmd.getOptionValue("e");
        if (!ensembleType.equals("svm") && !ensembleType.equals("weka")) {
            System.err.println( "Invalid ensemble type: " + ensembleType);
            new HelpFormatter().printHelp( "EnsembleMain", options );
            return;
        }

        File pathConf = new File(cmd.getOptionValue("c"));
        String metricNames[] = cmd.getOptionValues("n");
        File outputFile = new File(cmd.getOptionValue("o"));
        File goldStandard = new File(cmd.getOptionValue("g"));

        int numThreads = DEFAULT_NUM_THREADS;
        if (cmd.hasOption("threads")) {
            numThreads = Integer.valueOf(cmd.getOptionValue("t"));
        }

        int numResults = DEFAULT_NUM_RESULTS;
        if (cmd.hasOption("results")) {
            numResults = Integer.valueOf(cmd.getOptionValue("r"));
        }
        TIntSet validIds = null;
        if (cmd.hasOption("validIds")) {
            validIds = readIds(cmd.getOptionValue("v"));
        }

        if (metricNames.length == 0) {
            LOG.info("building all metrics");
        } else {
            LOG.info("building metrics " + Arrays.toString(metricNames));
        }
        LOG.info("using configuration file " + pathConf);
        LOG.info("writing results to file " + outputFile);
        LOG.info("using up to " + numThreads + " threads");
        LOG.info("storing up to " + numResults + " results per page");
        if (validIds != null) {
            LOG.info("considering " + validIds.size() + " valid ids");
        }

        EnvConfigurator conf = new EnvConfigurator(new ConfigurationFile(pathConf));
        if (outputFile.exists()) {
            FileUtils.forceDelete(outputFile);
        }
        outputFile.mkdirs();

        Ensemble e = null;
        if (ensembleType.equals("svm")) {
            e = new SvmEnsemble();
        } else if (ensembleType.equals("weka")) {
            e = new WekaEnsemble();
        } else {
            throw new IllegalStateException();
        }
        conf.setShouldLoadMetrics(false);
        conf.setDoEnsembles(false);
        Env env = conf.loadEnv();

        EnsembleSimilarity ensembleSim = new EnsembleSimilarity(
                e, env.getMainMapper(), env.getMainIndex());
        ensembleSim.setNumThreads(numThreads);
        ensembleSim.setMinComponents(0);
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (metricNames.length == 0) {
            conf.loadMetrics();
            metrics = new ArrayList<SimilarityMetric>(env.getMetrics().values());
        } else {
            for (String name : metricNames) {
                metrics.add(conf.loadMetric(name));
            }
        }

        ensembleSim.setComponents(metrics);
        ensembleSim.trainMostSimilar(KnownSim.read(goldStandard), numResults, validIds);
        ensembleSim.write(outputFile);

        // test it!
        if (ensembleType.equals("weka")) {
            ensembleSim.read(new File(args[2]));
        }
    }
}
