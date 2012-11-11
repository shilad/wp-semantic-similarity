package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.sim.ESAAnalyzer;
import edu.macalester.wpsemsim.sim.ESASimilarity;
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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AllIndexBuilder {
    List<BaseIndexGenerator> generators = Arrays.asList(
//            new MainIndexGenerator(),
//
//            new FieldsIndexGenerator("text", "id", "title"),
//            new FieldsIndexGenerator("links", "id", "title"),
//            new FieldsIndexGenerator("cats", "id", "ns", "title")
//                    .setNamespaces(0, 14),

            new FieldsIndexGenerator("links", "text", "id", "title", "inlinks")
                    .setMinLinks(15).setMinWords(300)
                    .setTitleMultiplier(4)
                    .setAddInLinksToText(true)
                    .setName("esa")
                    .setSimilarity(new ESASimilarity.LuceneSimilarity())
                    .setAnalyzer(new ESAAnalyzer())
//            new FieldsIndexGenerator("links", "text", "id", "title", "inlinks")
//                    .setMinLinks(3).setMinWords(300)
//                    .setTitleMultiplier(4)
//                    .setAddInLinksToText(true)
//                    .setName("esa2")
//                    .setSimilarity(new ESASimilarity.LuceneSimilarity())
//                    .setAnalyzer(new ESAAnalyzer())
    );

    private static final Logger LOG = Logger.getLogger(AllIndexBuilder.class.getName());
    private AtomicInteger numDocs = new AtomicInteger();

    private File outputDir;
    private File inputPath;

    public AllIndexBuilder(File inputPath, File outputDir) {
        this.inputPath = inputPath;
        this.outputDir = outputDir;
    }

    public void openIndex(int bufferMB) throws IOException {
//        FileUtils.deleteDirectory(outputDir);
        outputDir.mkdirs();
        for (BaseIndexGenerator g : generators) {
            g.openIndex(new File(outputDir, g.getName()), bufferMB / generators.size());
        }
    }

    public List<File> getInputFiles() {
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

    public void write(int numThreads) throws IOException, InterruptedException {
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
            exec.awaitTermination(60, TimeUnit.HOURS);
        }

        for (BaseIndexGenerator g : generators) {
            g.close();
        }
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

    public static void main(String args[]) throws IOException, InterruptedException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " + AllIndexBuilder.class.getCanonicalName() + " path-in path-out memory-cache-in-MB [num-threads]");
        }
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        LOG.info("using " + cores + " threads");
        File inputPath = new File(args[0]);
        File outputPath = new File(args[1]);
        AllIndexBuilder writer = new AllIndexBuilder(inputPath, outputPath);
        writer.openIndex(Integer.valueOf(args[2]));
        writer.write(cores);
    }
}
