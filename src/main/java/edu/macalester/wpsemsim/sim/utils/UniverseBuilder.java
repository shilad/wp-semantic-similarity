package edu.macalester.wpsemsim.sim.utils;

import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.sim.pairwise.PairwiseSimilarityWriter;
import edu.macalester.wpsemsim.utils.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.File;
import java.io.IOException;

/**
 * Builds the most similar and pairwise similarity matrix for a set of phrases.
 */
public class UniverseBuilder {
    class PhraseInfo {
        String phrase;
        int wpId;
        DocScoreList mostSimilar;
        float otherPhraseScores[];
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, InterruptedException {
        Options options = new Options();              options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("name")
                .withDescription("Name of similarity metric that should be used.")
                .create('n'));
        options.addOption(new DefaultOptionBuilder()
                .isRequired()
                .hasArg()
                .withLongOpt("output")
                .withDescription("Output directory.")
                .create('o'));
        options.addOption(new DefaultOptionBuilder()
                .hasArg()
                .withLongOpt("phrases")
                .withDescription("File listing phrases in the universe.")
                .create('p'));

        EnvConfigurator conf;
        try {
            conf = new EnvConfigurator(options, args);
        } catch (org.apache.commons.cli.ParseException e) {
            System.err.println( "Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SimilarityAnalyzer", options);
            return;
        }

        CommandLine cmd = conf.getCommandLine();
        conf.setShouldLoadMetrics(false);
        Env env = conf.loadEnv();

        File outputFile = new File(cmd.getOptionValue("o"));
        SimilarityMetric m = conf.loadMetric(cmd.getOptionValue("n"), true);



        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(m, outputFile);
        if (env.getValidIds() != null) {
            writer.setValidIds(env.getValidIds());
        }
        writer.writeSims(env.getMainIndex().getWpIds(), env.getNumThreads(), env.getNumMostSimilarResults());
    }
}
