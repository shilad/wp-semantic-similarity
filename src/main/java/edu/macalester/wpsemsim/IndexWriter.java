package edu.macalester.wpsemsim;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndexWriter {
    private static final Logger LOG = Logger.getLogger(IndexWriter.class.getName());
    private AtomicInteger numDocs = new AtomicInteger();
    private org.apache.lucene.index.IndexWriter writer = null;
    private File outputDir;
    private File inputDir;

    public IndexWriter(File inputDir, File outputDir) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
    }

    public void openIndex(int bufferMB) throws IOException {
        FileUtils.deleteDirectory(outputDir);
        Directory dir = FSDirectory.open(outputDir);
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(bufferMB);
        writer = new org.apache.lucene.index.IndexWriter(dir, iwc);
    }

    public void write(int numThreads) throws IOException, InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(numThreads);

        try {
            for (final String path : inputDir.list()) {
                if (!path.endsWith(".bz2")) {
                    LOG.info("skipping non-bz2 file " + path);
                    continue;
                }
                exec.submit(new Runnable() {
                    public void run() {
                        try {
                            processOneFile(new File(inputDir, path));
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

        writer.close();
    }

    public void processOneFile(File path) throws IOException {
        LOG.info("reading input file " + path);
        for (Doc d : new DocReader(path)) {
            if (d.isRedirect()) {
                continue;
            }
            writer.addDocument(d.toLuceneDoc());
            if (numDocs.incrementAndGet() % 1000 == 0) {
                LOG.info("read doc " + numDocs + " from " + path + ": " + d.getTitle());
            }
        }
    }

    public static void main(String args[]) throws IOException, InterruptedException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " + IndexWriter.class.getCanonicalName() + " path-in path-out memory-cache-in-MB [num-threads]");
        }
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        LOG.info("using " + cores + " threads");
        File inputPath = new File(args[0]);
        File outputPath = new File(args[1]);
        IndexWriter writer = new IndexWriter(inputPath, outputPath);
        writer.openIndex(Integer.valueOf(args[2]));
        writer.write(cores);
    }
}
