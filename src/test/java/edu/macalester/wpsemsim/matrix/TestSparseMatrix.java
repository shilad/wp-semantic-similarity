package edu.macalester.wpsemsim.matrix;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrixTransposer;
import gnu.trove.set.hash.TIntHashSet;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

public class TestSparseMatrix {
    private List<SparseMatrixRow> srcRows;

    private int NUM_ROWS = 1000;
    private int MAX_COLS = NUM_ROWS * 2;
    private int MAX_KEY = Math.max(NUM_ROWS, MAX_COLS) * 10;
    private Random rand = new Random();

    @Before
    public void createTestData() {
        TIntHashSet colIds = new TIntHashSet();
        srcRows = new ArrayList<SparseMatrixRow>();
        for (int id1 : pickIds(NUM_ROWS)) {
            LinkedHashMap<Integer, Float> row = new LinkedHashMap<Integer, Float>();
            int numCols = Math.max(1, rand.nextInt(MAX_COLS));
            for (int id2 : pickIds(numCols)) {
                row.put(id2, rand.nextFloat());
                colIds.add(id2);
            }
            srcRows.add(new SparseMatrixRow(id1, row));
        }
    }

    private int[] pickIds(int numIds) {
        TIntHashSet picked = new TIntHashSet();
        for (int i = 0; i < numIds; i++) {
            while (true) {
                int id = rand.nextInt(MAX_KEY);
                if (!picked.contains(id)) {
                    picked.add(id);
                    break;
                }
            }
        }
        return picked.toArray();
    }

    @Test
    public void testWrite() throws IOException {
        File tmp = File.createTempFile("matrix", null);
        SparseMatrixWriter.write(tmp, srcRows.iterator());
    }

    @Test
    public void testReadWrite() throws IOException {
        SparseMatrix.MAX_PAGE_SIZE = NUM_ROWS * 20;
        File tmp = File.createTempFile("matrix", null);
        SparseMatrixWriter.write(tmp, srcRows.iterator());
        SparseMatrix m = new SparseMatrix(tmp);
    }


    @Test
    public void testTranspose() throws IOException {
        SparseMatrix.MAX_PAGE_SIZE = MAX_KEY * 50;
        File tmp1 = File.createTempFile("matrix", null);
        File tmp2 = File.createTempFile("matrix", null);
        File tmp3 = File.createTempFile("matrix", null);
        SparseMatrixWriter.write(tmp1, srcRows.iterator());
        SparseMatrix m = new SparseMatrix(tmp1);
        verifyIsSourceMatrix(m);
        new SparseMatrixTransposer(m, tmp2, 1).transpose();
        SparseMatrix m2 = new SparseMatrix(tmp2);
        new SparseMatrixTransposer(m2, tmp3, 1).transpose();
        SparseMatrix m3 = new SparseMatrix(tmp3);
        verifyIsSourceMatrixUnordered(m3, .02);
    }


    @Test
    public void testRows() throws IOException {
        SparseMatrix.MAX_PAGE_SIZE = NUM_ROWS * 20;
        File tmp = File.createTempFile("matrix", null);
        SparseMatrixWriter.write(tmp, srcRows.iterator());
        SparseMatrix m = new SparseMatrix(tmp);
        verifyIsSourceMatrix(m);
    }

    private void verifyIsSourceMatrix(SparseMatrix m) {
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

    private void verifyIsSourceMatrixUnordered(SparseMatrix m, double delta) {
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
