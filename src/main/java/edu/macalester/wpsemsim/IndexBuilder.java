package edu.macalester.wpsemsim;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IndexBuilder {
    public static final String INDEX_INFO[][] = new String[][] {
            // { index name, namespaces, field1, field2, ... }
            { "text",  "0",    "id", "title", "text"},
            { "cats",  "0,14", "id", "ns", "title", "cats"},
            { "links", "0",    "id", "title", "links"},
    };

    private static final Logger LOG = Logger.getLogger(IndexBuilder.class.getName());
    private AtomicInteger numDocs = new AtomicInteger();
    private IndexWriter writers[] = new IndexWriter[INDEX_INFO.length];
    private File outputDir;
    private File inputPath;

    public IndexBuilder(File inputPath, File outputDir) {
        this.inputPath = inputPath;
        this.outputDir = outputDir;
    }

    public void openIndex(int bufferMB) throws IOException {
        FileUtils.deleteDirectory(outputDir);
        outputDir.mkdirs();
        for (int i = 0; i < INDEX_INFO.length; i++) {
            File indexDir = new File(outputDir, INDEX_INFO[i][0]);
            indexDir.mkdirs();
            Directory dir = FSDirectory.open(indexDir);
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
            IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            iwc.setRAMBufferSizeMB(bufferMB / INDEX_INFO.length);
            writers[i] = new IndexWriter(dir, iwc);
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


        for (IndexWriter w : writers) {
            w.close();
        }
    }

    public void processOneFile(File path) throws IOException {
        LOG.info("reading input file " + path);
        for (Page p : new PageReader(path)) {
            if (p.isRedirect()) {
                continue;
            }
            Document d = p.toLuceneDoc();
            for (int i = 0; i < INDEX_INFO.length; i++) {
                storePageInIndex(d, i);
            }
            if (numDocs.incrementAndGet() % 1000 == 0) {
                LOG.info("read doc " + numDocs + " from " + path + ": " + p.getTitle());
            }
//            if (numDocs.get() > 200000) {
//                break;
//            }
        }
    }

    private void storePageInIndex(Document src, int index) throws IOException {
        String info[] = INDEX_INFO[index];
        String nss[] = info[1].split(",");
        if (!contains(nss, src.getField("ns").stringValue())) {
            return;
        }
        Document pruned = new Document();
        for (int j = 2; j < info.length; j++) {
            for (IndexableField f : src.getFields(info[j])) {
                pruned.add(f);
            }
        }
        writers[index].addDocument(pruned);
    }

    private static boolean contains(String A[], String el) {
        for (String s : A) {
            if (s.equals(el)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String args[]) throws IOException, InterruptedException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " + IndexBuilder.class.getCanonicalName() + " path-in path-out memory-cache-in-MB [num-threads]");
        }
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        LOG.info("using " + cores + " threads");
        File inputPath = new File(args[0]);
        File outputPath = new File(args[1]);
        IndexBuilder writer = new IndexBuilder(inputPath, outputPath);
        writer.openIndex(Integer.valueOf(args[2]));
        writer.write(cores);
    }
}
