package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.Leaderboard;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntFloatHashMap;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class PairwiseCosineSimilarity implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(PairwiseCosineSimilarity.class.getName());

    private SparseMatrix matrix;
    private SparseMatrix transpose;
    private TIntFloatHashMap lengths;   // lengths of each row
    private String name;

    public PairwiseCosineSimilarity(SparseMatrix matrix, SparseMatrix transpose) throws IOException {
        this.matrix = matrix;
        this.transpose = transpose;
        this.name = "pairwise-cosine-similarity (matrix=" +
                matrix.getPath() + ", transpose=" +
                transpose.getPath() + ")";
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
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

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        SparseMatrixRow row1 = matrix.getRow(wpId1);
        if (row1 == null) {
            LOG.info("unknown wpId: " + wpId1);
            return 0;
        }
        SparseMatrixRow row2 = matrix.getRow(wpId2);
        if (row2 == null) {
            LOG.info("unknown wpId: " + wpId2);
            return 0;
        }
        TIntFloatHashMap map1 = row1.asTroveMap();
        TIntFloatHashMap map2 = row2.asTroveMap();
        double xDotX = 0.0;
        double yDotY = 0.0;
        double xDotY = 0.0;

        for (float x: map1.values()) { xDotX += x * x; }
        for (float y: map2.values()) { yDotY += y * y; }
        for (int id : map1.keys()) {
            if (map2.containsKey(id)) {
                xDotY += map1.get(id) * map2.get(id);
            }
        }

        return xDotY / Math.sqrt(xDotX * yDotY);
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException {
        synchronized (this) {
            if (lengths == null) {
                calculateRowLengths();
            }
        }

        SparseMatrixRow row = matrix.getRow(wpId);
        if (row == null) {
            LOG.info("unknown wpId: " + wpId);
            return new DocScoreList(0);
        }
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

        final Leaderboard leaderboard = new Leaderboard(maxResults);
        for (int id : dots.keys()) {
            double l1 = lengths.get(id);
            double l2 = lengths.get(row.getRowIndex());
            double dot = dots.get(id);
            double sim = dot / (l1 * l2);
            leaderboard.tallyScore(id, sim);
        }
        return leaderboard.getTop();
    }

    public static int PAGE_SIZE = 1024*1024*500;    // 500MB
    public static void main(String args[]) throws IOException, InterruptedException {
        if (args.length != 4 && args.length != 5) {
            System.err.println("usage: " + PairwiseCosineSimilarity.class.getName()
                    + " path_matrix path_matrix_transpose path_output maxResultsPerDoc [num-cores]");
            System.exit(1);
        }
        SparseMatrix matrix = new SparseMatrix(new File(args[0]), false, PAGE_SIZE);
        SparseMatrix transpose = new SparseMatrix(new File(args[1]));
        PairwiseCosineSimilarity sim = new PairwiseCosineSimilarity(matrix, transpose);
        int cores = (args.length == 5)
                ? Integer.valueOf(args[4])
                : Runtime.getRuntime().availableProcessors();

        PairwiseSimilarityWriter writer = new PairwiseSimilarityWriter(sim, new File(args[2]));
        writer.writeSims(matrix.getRowIds(), cores, Integer.valueOf(args[3]));
    }
}
