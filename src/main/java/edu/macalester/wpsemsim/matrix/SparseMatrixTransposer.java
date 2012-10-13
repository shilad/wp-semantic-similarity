package edu.macalester.wpsemsim.matrix;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class SparseMatrixTransposer {
    final static Logger LOG = Logger.getLogger(SparseMatrixTransposer.class.getName());

    private SparseMatrixWriter writer;
    private SparseMatrix matrix;
    private int colIds[];
    private TIntIntHashMap colCounts = new TIntIntHashMap();
    private int bufferMb;
    private int numColsTransposed = 0;


    public SparseMatrixTransposer(SparseMatrix m, File f, int bufferMb) throws IOException {
        this.matrix = m;
        this.writer = new SparseMatrixWriter(f);
        this.bufferMb = bufferMb;
        this.numColsTransposed = 0;
    }

    public void transpose() throws IOException {
        countCellsPerColumn();
        while (numColsTransposed < colIds.length) {
            Map<Integer, LinkedHashMap<Integer, Float>> batch = accumulateBatch();
            writeBatch(batch);
        }
        this.writer.finish();
    }

    private void countCellsPerColumn() {
        for (int id : matrix.getRowIds()) {
            SparseMatrixRow row = matrix.getRow(id);
            for (int i = 0; i < row.getNumCols(); i++) {
                colCounts.adjustOrPutValue(row.getColIndex(i), 1, 1);
            }
        }

        colIds = colCounts.keys();
        LOG.info("found " + colIds.length + " unique column ids in matrix");
        Arrays.sort(colIds);
    }

    public Map<Integer, LinkedHashMap<Integer, Float>> accumulateBatch() {
        Map<Integer, LinkedHashMap<Integer, Float>> transposedBatch =
                new LinkedHashMap<Integer, LinkedHashMap<Integer,Float>>();

        // figure out which columns we are tracking
        double mbs = 0;
        TIntHashSet colIdsInBatch = new TIntHashSet();
        for (int i = numColsTransposed; i  < colIds.length; i++) {
            int colId = colIds[i];
            int colSize = colCounts.get(colId);
            double rowMbs = getSizeInMbOfRowDataStructure(colSize);
            if (mbs + rowMbs > bufferMb) {
                break;
            }
            colIdsInBatch.add(colId);
            mbs += rowMbs;
        }
        numColsTransposed += colIdsInBatch.size();
        LOG.info("processing " + colIdsInBatch.size() + " columns in batch (total=" + numColsTransposed + " of " + colCounts.size() + ")");

        for (int rowId : matrix.getRowIds()) {
            SparseMatrixRow row = matrix.getRow(rowId);
            for (int i = 0; i < row.getNumCols(); i++) {
                int colId = row.getColIndex(i);
                if (!colIdsInBatch.contains(colId)) {
                    continue;
                }
                float colValue = row.getColValue(i);
                if (!transposedBatch.containsKey(colId)) {
                    transposedBatch.put(colId, new LinkedHashMap<Integer, Float>());
                }
                transposedBatch.get(colId).put(rowId, colValue);
            }
        }

        for (int id : transposedBatch.keySet()) {
            if (colCounts.get(id) != transposedBatch.get(id).size()) {
                throw new AssertionError("row size unexpected!");
            }
        }

        return transposedBatch;
    }

    public void writeBatch(Map<Integer, LinkedHashMap<Integer, Float>> batch) throws IOException {
        for (int id : batch.keySet()) {
            writer.writeRow(new SparseMatrixRow(id, batch.get(id)));
        }
    }

    private static final int BYTES_PER_REF =
            Integer.valueOf(System.getProperty("sun.arch.data.model")) / 8;
    private static final int BYTES_PER_OBJECT = 40;     // a guess

    private double getSizeInMbOfRowDataStructure(int numEntries) {
        return (
            // Hashmap
            BYTES_PER_OBJECT + 20 + numEntries * BYTES_PER_REF +
            // Hashmap entries
            numEntries * (BYTES_PER_OBJECT + 3 *  BYTES_PER_REF + 4) +
            // Keys
            numEntries * (BYTES_PER_OBJECT + 4) +
            // Values
            numEntries * (BYTES_PER_OBJECT + 4)
        ) / (1024.0 * 1024.0);
    }
}
