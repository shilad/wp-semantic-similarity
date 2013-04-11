package edu.macalester.wpsemsim.sim.ensemble;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.EnvConfigurator;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class EnsembleMain {
    public static Logger LOG = Logger.getLogger(EnsembleMain.class.getName());

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException, ClassNotFoundException, org.apache.commons.cli.ParseException {
        Options options = new Options();
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("names")
                .withDescription("Names of similarity metrics that should be included.")
                .hasArgs()
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("ensemble")
                .withDescription("Ensemble type ('weka', 'svm', or 'linear').")
                .hasArgs()
                .create('z'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output file.")
                .create('o'));

        EnvConfigurator conf = null;
        CommandLine cmd = null;

        // create the parser
        try {
            conf = new EnvConfigurator(options, args);
            cmd = conf.getCommandLine();
        } catch( org.apache.commons.cli.ParseException exp ) {
            System.err.println( "Invalid option usage: " + exp.getMessage());
            new HelpFormatter().printHelp( "EnsembleMain", options );
            System.exit(1);
        }

        String ensembleType = cmd.getOptionValue("z");
        if (!Arrays.asList("svm", "linear", "weka").contains(ensembleType)) {
            System.err.println( "Invalid ensemble type: " + ensembleType);
            new HelpFormatter().printHelp( "EnsembleMain", options );
            return;
        }

        String metricNames[] = cmd.getOptionValues("n");
        if (metricNames == null || metricNames.length == 0) {
            LOG.info("building all metrics");
        } else {
            LOG.info("building metrics " + Arrays.toString(metricNames));
        }

        File outputFile = new File(cmd.getOptionValue("o"));
        LOG.info("writing results to file " + outputFile);
        if (outputFile.exists()) {
            FileUtils.forceDelete(outputFile);
        }
        outputFile.mkdirs();

        Ensemble e = null;
        if (ensembleType.equals("svm")) {
            e = new SvmEnsemble();
        } else if (ensembleType.equals("weka")) {
            e = new WekaEnsemble();
        } else if (ensembleType.equals("linear")) {
            e = new LinearEnsemble();
        } else {
            throw new IllegalStateException();
        }


        conf.setShouldLoadMetrics(false);
        conf.setDoEnsembles(false);
        Env env = conf.loadEnv();

        EnsembleSimilarity ensembleSim = new EnsembleSimilarity(
                e, env.getMainMapper(), env.getMainIndex());
        ensembleSim.setNumThreads(env.getNumThreads());
        ensembleSim.setMinComponents(0);
        List<SimilarityMetric> metrics = new ArrayList<SimilarityMetric>();
        if (metricNames == null || metricNames.length == 0) {
            conf.loadMetrics();
            metrics = new ArrayList<SimilarityMetric>(env.getMetrics().values());
        } else {
            for (String name : metricNames) {
                metrics.add(conf.loadMetric(name));
            }
        }

        ensembleSim.setComponents(metrics);
        ensembleSim.trainMostSimilar(env.getGold(), env.getNumMostSimilarResults(), env.getValidIds());
        ensembleSim.write(outputFile);

        // test it!
        if (ensembleType.equals("weka")) {
            ensembleSim.read(new File(args[2]));
        }
    }
}
