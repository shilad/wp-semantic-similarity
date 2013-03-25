package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.TitleMapper;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.*;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class Trainer {
    public static Logger LOG = Logger.getLogger(Trainer.class.getName());

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException, ClassNotFoundException {
        Options options = new Options();
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("name")
                .withDescription("Name of similarity metric that should be built.")
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output directory.")
                .create('o'));

        EnvConfigurator conf;
        try {
            conf = new EnvConfigurator(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println( "Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp( "Trainer", options );
            return;
        }

        CommandLine cmd = conf.getCommandLine();
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();

        // load metric
        SimilarityMetric metric = conf.loadMetric(cmd.getOptionValue("n"));

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

        metric.trainMostSimilar(env.getGold(), env.getNumMostSimilarResults(), env.getValidIds());
        metric.write(outputDirectory);

        // test it out
        metric.read(outputDirectory);
    }
}
