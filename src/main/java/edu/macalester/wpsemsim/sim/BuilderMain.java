package edu.macalester.wpsemsim.sim;

import org.apache.commons.cli.*;

import java.util.logging.Logger;

public class BuilderMain {
    private static final Logger LOG = Logger.getLogger(BuilderMain.class.getName());

    public static final int DEFAULT_NUM_THREADS = Runtime.getRuntime().availableProcessors();
    public static final int DEFAULT_BUFFER_MBS = 100;

    public static void main(String args[]) {
        Options options = new Options();
        options.addOption("t", "threads", true, "number of threads");
        options.addOption("b", "buffer", true, "max I/O buffer size in MBs");

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

        int numThreads = DEFAULT_NUM_THREADS;
        if (cmd.hasOption("threads")) {
            numThreads = Integer.valueOf(cmd.getOptionValue("t"));
        }
        LOG.info("using up to " + numThreads + " threads");

        int bufferMBs = DEFAULT_BUFFER_MBS;
        if (cmd.hasOption("buffer")) {
            bufferMBs = Integer.valueOf(cmd.getOptionValue("b"));
        }
        LOG.info("using up to " + bufferMBs + "MBs for I/O buffer");
    }
}
