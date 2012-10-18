package edu.macalester.wpsemsim;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrixTransposer;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TLongDoubleHashMap;
import org.apache.commons.collections.CollectionUtils;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class TestPairwiseSimilarity {
    int NUM_ROWS = 500;

    private SparseMatrix matrix;
    private SparseMatrix transpose;

    @Before
    public void createTestData() throws IOException {// Create test data and transpose
        matrix = TestUtils.createTestMatrix(NUM_ROWS, NUM_ROWS, false);
        File tmpFile = File.createTempFile("matrix", null);
        tmpFile.deleteOnExit();
        new SparseMatrixTransposer(matrix, tmpFile, 10).transpose();
        transpose = new SparseMatrix(tmpFile);
    }

    @Test
    public void testSimilarity() throws IOException {
        File simPath = File.createTempFile("matrix", null);
        simPath.deleteOnExit();
        PairwiseCosineSimilarity stage2 = new PairwiseCosineSimilarity(matrix, transpose, simPath);
        stage2.calculateRowLengths();
        stage2.calculatePairwiseSims(1, 0, NUM_ROWS);
        stage2.finish();
        SparseMatrix sims = new SparseMatrix(simPath);

        // Calculate similarities by hand
        TLongDoubleHashMap dot = new TLongDoubleHashMap();
        TIntDoubleHashMap len2 = new TIntDoubleHashMap();

        for (SparseMatrixRow row1 : matrix) {
            Map<Integer, Float> data1 = row1.asMap();
            int id1 = row1.getRowIndex();

            // Calculate the length^2
            double len = 0.0;
            for (double val : data1.values()) {
                len += val * val;
            }
            len2.put(id1, len);

            for (SparseMatrixRow row2 : matrix) {
                int id2 = row2.getRowIndex();
                Map<Integer, Float> data2 = row2.asMap();
                double sim = 0.0;

                for (Object key : CollectionUtils.intersection(data1.keySet(), data2.keySet())) {
                    sim += data1.get(key) * data2.get(key);
                }
                if (sim != 0) {
                    dot.put(pack(id1, id2), sim);
                }
            }
        }

        int numCells = 0;
        for (SparseMatrixRow row : sims) {
            for (int i = 0; i < row.getNumCols(); i++) {
                if (row.getColValue(i) != 0) {
                    int id1 = row.getRowIndex();
                    int id2 = row.getColIndex(i);
                    numCells++;
                    double xDotX = len2.get(id1);
                    double yDotY = len2.get(id2);
                    double xDotY = dot.get(pack(id1, id2));
                    assertEquals(row.getColValue(i), xDotY / Math.sqrt(xDotX * yDotY), 0.001);
                }
            }
        }
        assertEquals(numCells, dot.size());
    }

    private long pack(int x, int y) {
        return ByteBuffer.wrap(new byte[8]).putInt(x).putInt(y).getLong(0);
    }
}
