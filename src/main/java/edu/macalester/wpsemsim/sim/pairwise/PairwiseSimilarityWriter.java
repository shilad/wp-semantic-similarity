package edu.macalester.wpsemsim.sim.pairwise;

import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrixWriter;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.DocScoreList;

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
    private AtomicInteger idCounter = new AtomicInteger();
    private long numCells;

    public PairwiseSimilarityWriter(SimilarityMetric metric, File outputFile) throws IOException {
        this.metric = metric;
        this.writer = new SparseMatrixWriter(outputFile);
    }

    public void writeSims(final int wpIds[], final int threads, final int maxSimsPerDoc) throws IOException, InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                final int i2 = i;
                exec.submit(new Runnable() {
                    public void run() {
                        try {
                            writeSims(wpIds, threads, i2, maxSimsPerDoc);
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
        LOG.info("wrote " + numCells + " non-zero similarity cells");
        this.writer.finish();
    }

    private void writeSims(int[] wpIds, int nthreads, int offset, int maxSimsPerDoc) throws IOException {
        for (int i = offset; i < wpIds.length; i += nthreads) {
            if (idCounter.incrementAndGet() % 10000 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + idCounter.get());
            }
            int wpId = wpIds[i];
            DocScoreList scores = metric.mostSimilar(wpId, maxSimsPerDoc);
            synchronized (this) {
                numCells += scores.getIds().length;
            }
            writer.writeRow(new SparseMatrixRow(wpId, scores.getIds(), scores.getScoresAsFloat()));
        }
    }
}
