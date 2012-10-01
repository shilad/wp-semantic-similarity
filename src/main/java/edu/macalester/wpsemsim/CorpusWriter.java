package edu.macalester.wpsemsim;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
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

public class CorpusWriter {
    private static final Logger LOG = Logger.getLogger(CorpusWriter.class.getName());
    private AtomicInteger numDocs = new AtomicInteger();
    private IndexWriter writer = null;
    private File outputDir;
    private File inputDir;

    public CorpusWriter(File inputDir, File outputDir) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
    }

    public void openIndex(int bufferMB) throws IOException {
        Directory dir = FSDirectory.open(outputDir);
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        iwc.setRAMBufferSizeMB(bufferMB);
        writer = new IndexWriter(dir, iwc);
    }

    public void write() throws IOException, InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(8);

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
        if (args.length != 3) {
            System.err.println("usage: java " + CorpusWriter.class.getCanonicalName() + " path-in path-out memory-cache-in-MB");
        }
        File inputPath = new File(args[0]);
        File outputPath = new File(args[1]);
        CorpusWriter writer = new CorpusWriter(inputPath, outputPath);
        writer.openIndex(Integer.valueOf(args[2]));
        writer.write();
    }
}
