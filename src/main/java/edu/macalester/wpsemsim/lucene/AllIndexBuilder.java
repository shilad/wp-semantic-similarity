package edu.macalester.wpsemsim.lucene;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.sim.*;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;
import static java.util.concurrent.TimeUnit.HOURS;

public class AllIndexBuilder {
    private static final Logger LOG = Logger.getLogger(AllIndexBuilder.class.getName());
    private AtomicInteger numDocs = new AtomicInteger();

    private File outputDir;
    private File inputPath;
    private ConfigurationFile conf;
    private List<BaseIndexGenerator> generators = new ArrayList<BaseIndexGenerator>();

    public AllIndexBuilder(ConfigurationFile conf, List<String> keys) throws ConfigurationException {
        this.inputPath = requireDirectory(conf.get("indexes"), "inputDir");
        this.outputDir = requireDirectory(conf.get("indexes"), "outputDir");
        this.conf = conf;

        for (String key : conf.getKeys("indexes")) {
            if (key.equals("inputDir") || key.equals("outputDir")) {
                continue;
            }
            if (keys == null || keys.contains(key)) {
                JSONObject params = conf.get("indexes", key);
                generators.add(loadGenerator(key, params));
            }
        }
    }

    private BaseIndexGenerator loadGenerator(String name, JSONObject params) throws ConfigurationFile.ConfigurationException {
        info("loading metric " + name);
        String type = requireString(params, "type");

        BaseIndexGenerator g;
        if (type.equals("main")) {
            g = new MainIndexGenerator();
        } else if (type.equals("fields")) {
            String fields[] = requireListOfStrings(params, "fields").toArray(new String[0]);
            FieldsIndexGenerator fg = new FieldsIndexGenerator(fields);
            if (params.containsKey("minLinks")) {
                fg.setMinLinks(requireInteger(params, "minLinks"));
                System.err.println("here 1");
            }
            if (params.containsKey("minWords")) {
                fg.setMinWords(requireInteger(params, "minWords"));
                System.err.println("here 2");
            }
            if (params.containsKey("titleMultiplier")) {
                fg.setTitleMultiplier(requireInteger(params, "titleMultiplier"));
                System.err.println("here 3");
            }
            if (params.containsKey("addInLinksToText")) {
                fg.setAddInLinksToText(requireBoolean(params, "addInLinksToText"));
                System.err.println("here 4");
            }
            if (params.containsKey("similarity")) {
                System.err.println("here 5");
                String sim = requireString(params, "similarity");
                if (sim.equals("ESA")) {
                    fg.setSimilarity(new ESASimilarity.LuceneSimilarity());
                } else {
                    throw new ConfigurationFile.ConfigurationException("unknown similarity type: " + sim);
                }
            }
            if (params.containsKey("analyzer")) {
                System.err.println("here 6");
                String analyzer = requireString(params, "analyzer");
                if (analyzer.equals("ESA")) {
                    fg.setAnalyzer(new ESAAnalyzer());
                } else {
                    throw new ConfigurationFile.ConfigurationException("unknown analyzer type: " + analyzer);
                }
            }
            if (params.containsKey("booster")) {
                System.err.println("here 7");
                String booster = requireString(params, "booster");
                if (booster.equals("ESA")) {
                    fg.setBooster(new ESASimilarity.ESABooster());
                } else {
                    throw new ConfigurationFile.ConfigurationException("unknown booster type: " + booster);
                }
            }
            g = fg;
        } else {
            throw new ConfigurationFile.ConfigurationException("unknown index type: " + type);
        }
        g.setName(name);
        return g;
    }


    public void write(int numThreads, int bufferMB) throws IOException, InterruptedException {
        open(bufferMB);
        process(numThreads);
        close();
    }

    protected void open(int bufferMB) throws IOException {
        for (BaseIndexGenerator g : generators) {
            g.openIndex(new File(outputDir, g.getName()), bufferMB / generators.size());
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
        for (BaseIndexGenerator g : generators) {
            g.close();
        }
    }

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

    public void processOneFile(File path) throws IOException {
        LOG.info("reading input file " + path);
        for (Page p : new PageReader(path)) {
            storePage(p);
            if (numDocs.incrementAndGet() % 1000 == 0) {
                LOG.info("read doc " + numDocs + " from " + path + ": " + p.getTitle());
            }
//            if (numDocs.get() > 10000) {
//                break;
//            }
        }
    }

    public void storePage(Page p) throws IOException {
        for (BaseIndexGenerator g : generators) {
            g.storePage(p);
        }
    }

    public List<BaseIndexGenerator> getGenerators() {
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
