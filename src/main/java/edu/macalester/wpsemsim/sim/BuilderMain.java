package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.sim.utils.SimilarityMetricConfigurator;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.DefaultOptionBuilder;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.logging.Logger;

public class BuilderMain {
    private static final Logger LOG = Logger.getLogger(BuilderMain.class.getName());

    public static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    public static final int DEFAULT_BUFFER_MBS = 100;
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

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, InterruptedException {
        Options options = new Options();
        options.addOption("t", "threads", true, "number of threads");
        options.addOption("b", "buffer", true, "max I/O buffer size in MBs");
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("conf")
                .withDescription("Path to configuration file.")
                .create('c'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("name")
                .withDescription("Name of similarity metric.")
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output file.")
                .create('o'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("results")
                .withDescription("Maximum length of similar wikipedia pages list.")
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
        } catch( ParseException exp ) {
            System.err.println( "Invalid option usage: " + exp.getMessage());
            new HelpFormatter().printHelp( "BuilderMain", options );
            return;
        }

        File pathConf = new File(cmd.getOptionValue("c"));
        String metricName = cmd.getOptionValue("n");
        File outputFile = new File(cmd.getOptionValue("o"));

        int numThreads = DEFAULT_NUM_THREADS;
        if (cmd.hasOption("threads")) {
            numThreads = Integer.valueOf(cmd.getOptionValue("t"));
        }

        int bufferMBs = DEFAULT_BUFFER_MBS;
        if (cmd.hasOption("buffer")) {
            bufferMBs = Integer.valueOf(cmd.getOptionValue("b"));
        }
        int numResults = DEFAULT_NUM_RESULTS;
        if (cmd.hasOption("results")) {
            numResults = Integer.valueOf(cmd.getOptionValue("r"));
        }
        TIntSet validIds = null;
        if (cmd.hasOption("validIds")) {
            validIds = readIds(cmd.getOptionValue("v"));
        }

        LOG.info("building metric " + metricName);
        LOG.info("using configuration file " + pathConf);
        LOG.info("writing results to file " + outputFile);
        LOG.info("using up to " + bufferMBs + "MBs for I/O buffer");
        LOG.info("using up to " + numThreads + " threads");
        LOG.info("storing up to " + numResults + " results per page");
        if (validIds != null) {
            LOG.info("considering " + validIds.size() + " valid ids");
        }

        ConfigurationFile conf = new ConfigurationFile(pathConf);
        SimilarityMetricConfigurator configurator = new SimilarityMetricConfigurator(conf);
        SimilarityMetric m = configurator.loadMetric(metricName);

        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(m, outputFile);
        if (validIds != null) {
            writer.setValidIds(validIds);
        }
        writer.writeSims(configurator.getHelper().getWpIds(), numThreads, numResults);
    }
}
