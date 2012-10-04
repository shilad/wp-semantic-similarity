package edu.macalester.wpsemsim;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;

import java.io.*;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(SimilarityMetric.class.getName());

    protected DirectoryReader reader;
    protected IndexSearcher searcher;
    private BufferedOutputStream output;

    public void openIndex(File indexDir, boolean mmap) throws IOException {
        this.reader = DirectoryReader.open(
                mmap ? MMapDirectory.open(indexDir)
                        : FSDirectory.open(indexDir)
        );
        this.searcher = new IndexSearcher(this.reader);
    }

    public void openOutput(File outputFile) throws FileNotFoundException, CompressorException {
        this.output = new BufferedOutputStream(
                new CompressorStreamFactory()
                .createCompressorOutputStream(CompressorStreamFactory.GZIP,
                        new BufferedOutputStream(new FileOutputStream(outputFile))));

    }

    public void calculatePairwiseSims(final int threads, final int maxSimsPerDoc) throws IOException, InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int i2 = i;
                exec.submit(new Runnable() {
                    public void run() {
                    try {
                        calculatePairwiseSims(threads, i2, maxSimsPerDoc);
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "error processing split " + i2, e);
                    }
                    }
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(60, TimeUnit.HOURS);
        }
    }

    abstract protected void calculatePairwiseSims(int mod, int offset, int maxSimsPerDoc) throws IOException;

    public int getWikipediaId(int luceneId) throws IOException {
        Set<String> fields = new HashSet<String>();
        fields.add("id");
        Document d = reader.document(luceneId, fields);
        return d.getField("id").numericValue().intValue();
    }

    public void writeOutput(int targetDocId, int simDocIds[], float simDocScores[]) throws IOException {
        StringBuilder buff = new StringBuilder();
        buff.append(targetDocId);
        DecimalFormat df = new DecimalFormat("#.####");
        for (int i = 0; i < simDocIds.length; i++) {
            if (simDocIds[i] < 0) {
                break;
            }
            buff.append('\t');
            buff.append(simDocIds[i]);
            buff.append('=');
            buff.append(df.format(simDocScores[i]));
        }
        buff.append('\n');
        output.write(buff.toString().getBytes("UTF-8"));
    }
}
