package edu.macalester.wpsemsim.matrix;

import edu.macalester.wpsemsim.utils.TestUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSparseMatrix {
    private List<SparseMatrixRow> srcRows;

    private int NUM_ROWS = 1000;
    private int MAX_COLS = NUM_ROWS * 2;
    private int MAX_KEY = Math.max(NUM_ROWS, MAX_COLS) * 10;

    @Before
    public void createTestData() throws IOException {
        srcRows = TestUtils.createTestMatrixRows(NUM_ROWS, MAX_COLS, false);
    }

    @Test
    public void testWrite() throws IOException {
        File tmp = File.createTempFile("matrix", null);
        SparseMatrixWriter.write(tmp, srcRows.iterator());
    }

    @Test
    public void testReadWrite() throws IOException {
        File tmp = File.createTempFile("matrix", null);
        SparseMatrixWriter.write(tmp, srcRows.iterator());
        SparseMatrix m1 = new SparseMatrix(tmp, true, NUM_ROWS*20);
        SparseMatrix m2 = new SparseMatrix(tmp, false, NUM_ROWS*20);
    }

    @Test
    public void testTranspose() throws IOException {
        for (boolean loadAllPages : new boolean[] { true, false}) {
            File tmp1 = File.createTempFile("matrix", null);
            File tmp2 = File.createTempFile("matrix", null);
            File tmp3 = File.createTempFile("matrix", null);
            SparseMatrixWriter.write(tmp1, srcRows.iterator());
            SparseMatrix m = new SparseMatrix(tmp1, loadAllPages, MAX_KEY * 50);
            verifyIsSourceMatrix(m);
            new SparseMatrixTransposer(m, tmp2, 1).transpose();
            SparseMatrix m2 = new SparseMatrix(tmp2, loadAllPages, MAX_KEY * 50);
            new SparseMatrixTransposer(m2, tmp3, 1).transpose();
            SparseMatrix m3 = new SparseMatrix(tmp3, loadAllPages, MAX_KEY * 50);
            verifyIsSourceMatrixUnordered(m3, .001);
        }
    }


    @Test
    public void testRows() throws IOException {
        for (boolean loadAllPages : new boolean[] { true, false}) {
            File tmp = File.createTempFile("matrix", null);
            SparseMatrixWriter.write(tmp, srcRows.iterator());
            SparseMatrix m = new SparseMatrix(tmp, loadAllPages, NUM_ROWS * 20);
            verifyIsSourceMatrix(m);
        }
    }


    private void verifyIsSourceMatrix(SparseMatrix m) throws IOException {
        for (SparseMatrixRow srcRow : srcRows) {
            SparseMatrixRow destRow = m.getRow(srcRow.getRowIndex());
            assertEquals(destRow.getRowIndex(), srcRow.getRowIndex());
            assertEquals(destRow.getNumCols(), srcRow.getNumCols());
            for (int i = 0; i < destRow.getNumCols(); i++) {
                assertEquals(srcRow.getColIndex(i), destRow.getColIndex(i));
                assertEquals(srcRow.getColValue(i), destRow.getColValue(i), 0.01);
            }
        }
    }

    private void verifyIsSourceMatrixUnordered(SparseMatrix m, double delta) throws IOException {
        for (SparseMatrixRow srcRow : srcRows) {
            SparseMatrixRow destRow = m.getRow(srcRow.getRowIndex());
            LinkedHashMap<Integer, Float> destRowMap = destRow.asMap();
            assertEquals(destRow.getRowIndex(), srcRow.getRowIndex());
            assertEquals(destRow.getNumCols(), srcRow.getNumCols());
            for (int i = 0; i < srcRow.getNumCols(); i++) {
                int colId = srcRow.getColIndex(i);
                assertTrue(destRowMap.containsKey(colId));
                assertEquals(srcRow.getColValue(i), destRowMap.get(colId), delta);
            }
        }
    }
}
