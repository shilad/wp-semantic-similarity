package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.ConfigurationFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;
import static java.util.concurrent.TimeUnit.HOURS;


/**
 * Parses a Wikipedia dump and constructs one or more lucene indexes.
 * The dump should be one or more files in standard Wikipedia XML format in a single directory.
 * Each file in the directory is processed in parallel.
 */
public class AllIndexBuilder {
    private static final Logger LOG = Logger.getLogger(AllIndexBuilder.class.getName());


    private File outputDir;
    private File inputPath;
    private ConfigurationFile conf;
    private List<IndexGenerator> generators = new ArrayList<IndexGenerator>();
    private PageInfo info = new PageInfo();
    private AtomicInteger numDocs = new AtomicInteger();


    public AllIndexBuilder(ConfigurationFile conf, List<String> keys) throws ConfigurationException {
        this.inputPath = requireDirectory(conf.get("indexes"), "inputDir");
        this.outputDir = requireDirectory(conf.get("indexes"), "outputDir");
        this.conf = conf;
        IndexGeneratorConfigurator builder = new IndexGeneratorConfigurator();
        generators.addAll(builder.loadGenerators(info, conf, keys));
    }

    /**
     * Process the Wikipedia dump and write all lucene indexes.
     * @param numThreads
     * @param bufferMB
     * @throws IOException
     * @throws InterruptedException
     */
    public void write(int numThreads, int bufferMB) throws IOException, InterruptedException {
        open(bufferMB);
        process(numThreads);
        close();
    }

    protected void open(int bufferMB) throws IOException {
        for (IndexGenerator g : generators) {
            g.open(new File(outputDir, g.getName()), bufferMB / generators.size());
        }
    }

    protected void process(int numThreads) throws InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(numThreads);

        try {
            for (final File path : getInputFiles()) {
                String ext = FilenameUtils.getExtension(path.toString());
                if (!ext.equals("bz2") && !ext.equals("xml")) {
                    LOG.info("skipping non-dump file " + path);
                    continue;
                }
                exec.submit(new Runnable() {
                    public void run() {
                        try {
                            processOneFile(path);
                        } catch (IOException e) {
                            LOG.log(Level.SEVERE, "error processing " + path, e);
                        }
                    }
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(60, HOURS);
        }
    }

    protected void close() throws IOException {
        for (IndexGenerator g : generators) {
            g.close();
        }
    }

    /**
     * Get all the dump input files.
     * @return
     */
    protected List<File> getInputFiles() {
        List<File> inputs = new ArrayList<File>();
        if (inputPath.isFile()) {
            inputs.add(inputPath);
        } else if (inputPath.isDirectory()) {
            for (final String path : inputPath.list()) {
                inputs.add(new File(inputPath, path));
            }
        } else {
            throw new IllegalArgumentException(inputPath + " is not a file or directory");
        }

        // Sort by decrease size to optimize threaded completion time
        Collections.sort(inputs, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return - (int) (FileUtils.sizeOf(file1) - FileUtils.sizeOf(file2));
            }
        });
        return inputs;
    }

    /**
     * Process a single XML dump file.
     * @param path
     * @throws IOException
     */
    public void processOneFile(File path) throws IOException {
        LOG.info("reading input file " + path);
        for (Page p : new PageReader(path)) {
            storePage(p);
            if (numDocs.incrementAndGet() % 10000 == 0) {
                LOG.info("read doc " + numDocs + " from " + path + ": " + p.getTitle());
            }
//            if (numDocs.get() > 5000) {
//                break;
//            }
        }
    }

    public void storePage(Page p) throws IOException {
        info.update(p);
        for (IndexGenerator g : generators) {
            g.storePage(p);
        }
    }

    public List<IndexGenerator> getGenerators() {
        return generators;
    }

    private void info(String message) {
        LOG.info("configurator for " + conf.getPath() + ": " + message);
    }

    public static void main(String args[]) throws IOException, InterruptedException, ConfigurationException {
        if (args.length < 2) {
            System.err.println("usage: java " + AllIndexBuilder.class.getCanonicalName() + " path/to/conf.txt memory-cache-in-MB {index1 index2 ...}");
        }
        int cores = Runtime.getRuntime().availableProcessors();
        LOG.info("using " + cores + " threads");
        List<String> keys = null;
        if (args.length > 2) {
            keys = Arrays.asList(ArrayUtils.subarray(args, 2, args.length));
        }
        ConfigurationFile conf = new ConfigurationFile(new File(args[0]));
        File outputPath = new File(requireString(conf.get("indexes"), "outputDir"));
        if (keys == null) {
            FileUtils.deleteDirectory(outputPath);
        }
        outputPath.mkdirs();
        AllIndexBuilder writer = new AllIndexBuilder(conf, keys);
        writer.write(cores, Integer.valueOf(args[1]));
    }
}
