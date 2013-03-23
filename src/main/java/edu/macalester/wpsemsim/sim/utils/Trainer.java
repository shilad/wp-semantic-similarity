package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.TitleMapper;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.ensemble.*;
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

public class Trainer {
    public static Logger LOG = Logger.getLogger(Trainer.class.getName());

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
                .withLongOpt("name")
                .withDescription("Name of similarity metric that should be built.")
                .hasArgs()
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("gold")
                .withDescription("Path to gold standard")
                .hasArgs()
                .create('g'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output directory.")
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
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("titles")
                .withDescription("Input phrases are article titles (takes path to dictionary database).")
                .create('t'));


        CommandLine cmd;

        // create the parser
        try {
            // parse the command line arguments
            CommandLineParser parser = new PosixParser();
            cmd = parser.parse(options, args);
        } catch( org.apache.commons.cli.ParseException exp ) {
            System.err.println( "Invalid option usage: " + exp.getMessage());
            new HelpFormatter().printHelp( "Trainer", options );
            return;
        }

        File pathConf = new File(cmd.getOptionValue("c"));
        String metricName = cmd.getOptionValue("n");

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

        LOG.info("using configuration file " + pathConf);
        LOG.info("using up to " + numThreads + " threads");
        LOG.info("storing up to " + numResults + " results per page");
        if (validIds != null) {
            LOG.info("considering " + validIds.size() + " valid ids");
        }


        EnvConfigurator conf = new EnvConfigurator(new ConfigurationFile(pathConf));
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();

        if (cmd.hasOption("t")) {
            LOG.info("installing title mapper");
            env.setMainMapper(
                    new TitleMapper(
                        new File(cmd.getOptionValue("t")),
                        env.getMainIndex()));
        }

        SimilarityMetric metric = conf.loadMetric(metricName);

        if (metric instanceof BaseSimilarityMetric) {
            ((BaseSimilarityMetric)metric).setNumThreads(numThreads);
        }

        // override gold standard if necessary
        List<KnownSim> gold = env.getGold();
        if (cmd.hasOption("g")) {
            gold = KnownSim.read(new File(cmd.getOptionValue("g")));
        }
        LOG.info("gold standard size is " + gold.size());

        // override output directory if necessary.
        File outputDirectory = conf.getModelDirectory(metric);
        if (cmd.hasOption("o")) {
            outputDirectory = new File(cmd.getOptionValue("o"));
        }

        LOG.info("writing results to file " + outputDirectory);
        if (outputDirectory.exists()) {
            FileUtils.forceDelete(outputDirectory);
        }
        outputDirectory.mkdirs();

        metric.trainMostSimilar(gold, numResults, validIds);
        metric.write(outputDirectory);

        // test it out
        metric.read(outputDirectory);
    }
}
