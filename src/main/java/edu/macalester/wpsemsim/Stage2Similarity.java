package edu.macalester.wpsemsim;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrixWriter;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntFloatHashMap;
import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Stage2Similarity {
    private static final Logger LOG = Logger.getLogger(Stage2Similarity.class.getName());

    private SparseMatrixWriter writer;
    private SparseMatrix matrix;
    private int[] rowIds;
    private SparseMatrix transpose;
    private TIntFloatHashMap lengths;   // lengths of each row

    public Stage2Similarity(SparseMatrix matrix, SparseMatrix transpose, File output) throws IOException {
        this.matrix = matrix;
        this.transpose = transpose;
        this.rowIds = matrix.getRowIds();
        this.writer = new SparseMatrixWriter(output);
    }

    public void calculateRowLengths() {
        LOG.info("calculating row lengths");
        lengths = new TIntFloatHashMap();
        for (SparseMatrixRow row : matrix) {
            double length = 0.0;
            for (int i = 0; i < row.getNumCols(); i++) {
                length += row.getColValue(i) * row.getColValue(i);
            }
            lengths.put(row.getRowIndex(), (float) Math.sqrt(length));
        }
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

    void finish() throws IOException {
        writer.finish();
    }

    void calculatePairwiseSims(int mod, int offset, int maxSimsPerDoc) throws IOException {
        for (int i = offset; i < rowIds.length; i += mod) {
            SparseMatrixRow row = matrix.getRow(rowIds[i]);
            findSimilarities(row, maxSimsPerDoc);
        }
    }

    public void findSimilarities(SparseMatrixRow row, int maxSimsPerDoc) throws IOException {
        TIntDoubleHashMap dots = new TIntDoubleHashMap();

        for (int i = 0; i < row.getNumCols(); i++) {
            int id = row.getColIndex(i);
            float val1 = row.getColValue(i);
            SparseMatrixRow row2 = transpose.getRow(id);
            for (int j = 0; j < row2.getNumCols(); j++) {
                int id2 = row2.getColIndex(j);
                float val2 = row2.getColValue(j);
                dots.adjustOrPutValue(id2, val1 * val2, val1 * val2);
            }
        }

        final Leaderboard leaderboard = new Leaderboard(maxSimsPerDoc);
        for (int id : dots.keys()) {
            double l1 = lengths.get(id);
            double l2 = lengths.get(row.getRowIndex());
            double dot = dots.get(id);
            double sim = dot / (l1 * l2);
            leaderboard.tallyScore(id, sim);
        }

        LinkedHashMap<Integer, Double> top = leaderboard.getTop();
        int simDocIds[] = ArrayUtils.toPrimitive(top.keySet().toArray(new Integer[0]));
        float simDocScores[] = new float[simDocIds.length];
        for (int i = 0; i < simDocIds.length; i++) {
            simDocScores[i] = top.get(simDocIds[i]).floatValue();
        }
        writeOutput(row.getRowIndex(), simDocIds, simDocScores);

    }

    public void writeOutput(int targetDocId, int simDocIds[], float simDocScores[]) throws IOException {
        writer.writeRow(new SparseMatrixRow(targetDocId, simDocIds, simDocScores));
    }

    public static void main(String args[]) {

    }
}
