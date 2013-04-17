package edu.macalester.wpsemsim.sim.utils;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.ensemble.EnsembleSimilarity;
import edu.macalester.wpsemsim.utils.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class SimilarityConsole {

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, DatabaseException, ParseException {
        Options options = new Options();
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .isRequired()
                .withLongOpt("name")
                .withDescription("Name of similarity metrics that should be included.")
                .create('n'));

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

        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();
        SimilarityMetric metric = conf.loadMetric(cmd.getOptionValue("n"), true);

        System.out.println("Please enter:\n" +
                "\tone phrase for mostSimilar()\n" +
                "\tor two phrases separated by commas for similarity()" +
                "\tor exit to stop");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String line = in.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.equalsIgnoreCase("exit")) {
                break;
            }
            String phrases[] = line.split(",");
            if (phrases.length == 1) {
                DocScoreList results = metric.mostSimilar(phrases[0].trim(), 2000);
                if (results.numDocs() > env.getNumMostSimilarResults()) {
                    results.truncate(env.getNumMostSimilarResults());
                }
                System.out.println("top results for " + phrases[0]);
                for (int i = 0; i < results.numDocs(); i++) {
                    String title = env.getMainIndex().wpIdToTitle(results.getId(i));
                    System.out.println("" + (i+1) + ". " + results.getScore(i) + ": " + title);
                }
            } else if (phrases.length == 2) {
                double sim = metric.similarity(phrases[0].trim(), phrases[1].trim());
                System.out.println("similarity between " + phrases[0] + " and " + phrases[1] + " is " + sim);
            } else {
                System.err.println("expected at most one comma, found " + (phrases.length - 1));
            }
        }
    }
}
