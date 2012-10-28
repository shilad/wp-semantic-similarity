package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrixWriter;
import edu.macalester.wpsemsim.utils.DocScoreList;
import org.apache.lucene.index.DirectoryReader;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PairwiseSimilarityWriter {
    private static final Logger LOG = Logger.getLogger(PairwiseSimilarityWriter.class.getName());

    private SimilarityMetric metric;
    private SparseMatrixWriter writer;
    private IndexHelper helper;
    private DirectoryReader reader;
    private AtomicInteger counter = new AtomicInteger();

    public PairwiseSimilarityWriter(IndexHelper helper, SimilarityMetric metric, File outputFile) throws IOException {
        this.metric = metric;
        this.writer = new SparseMatrixWriter(outputFile);
        this.helper = helper;
        this.reader = helper.getReader();
    }

    public void writeSims(final int threads, final int maxSimsPerDoc) throws IOException, InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int i2 = i;
                exec.submit(new Runnable() {
                    public void run() {
                        try {
                            writeSims(threads, i2, maxSimsPerDoc);
                        } catch (Exception e) {
                            LOG.log(Level.SEVERE, "error processing split " + i2, e);
                        }
                    }
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(60, TimeUnit.HOURS);
        }
        this.writer.finish();
    }

    private void writeSims(int nthreads, int offset, int maxSimsPerDoc) throws IOException {
        for (int i = offset; i < reader.maxDoc(); i += nthreads) {
            if (counter.incrementAndGet() % 100 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
            int wpId = helper.luceneIdToWpId(i);
            DocScoreList scores = metric.mostSimilar(wpId, maxSimsPerDoc);
            writer.writeRow(new SparseMatrixRow(wpId, scores.getIds(), scores.getScoresAsFloat()));
        }
    }
}
