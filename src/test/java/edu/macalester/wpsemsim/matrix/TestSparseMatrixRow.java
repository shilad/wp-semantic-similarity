package edu.macalester.wpsemsim.matrix;

import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.LinkedHashMap;

public class TestSparseMatrixRow {
    private int[] keys = new int[] { 9, 11, 3, 26, 54 };
    private float[] vals = new float[] {1.0f, 0.7f, 2.0f, 0.1f, -0.1f};
    private int ROW_INDEX = 34;

    @Test
    public void testWrite() {
        SparseMatrixRow row = createRow();
        assertEquals(row.getRowIndex(), ROW_INDEX);
        assertEquals(row.getNumCols(), keys.length);
        for (int i = 0; i < keys.length; i++) {
            int k = row.getColIndex(i);
            float v = row.getColValue(i);
            float expected = vals[i];

            // pinch it
            expected = Math.min(expected, SparseMatrixRow.MAX_SCORE);
            expected = Math.max(expected, SparseMatrixRow.MIN_SCORE);

            assertEquals(k, keys[i]);
            assertEquals(v, expected, 0.0001);
        }
    }

    public SparseMatrixRow createRow() {
        LinkedHashMap<Integer, Float> m = new LinkedHashMap<Integer, Float>();
        assertEquals(keys.length, vals.length);
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], vals[i]);
        }
        return new SparseMatrixRow(ROW_INDEX, m);
    }
}
