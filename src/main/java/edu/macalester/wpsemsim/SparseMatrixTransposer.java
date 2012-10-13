package edu.macalester.wpsemsim;

import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class SparseMatrixTransposer {
    final static Logger LOG = Logger.getLogger(SparseMatrixTransposer.class.getName());

    public static void transpose(SparseMatrix m, File f, int maxMB) throws IOException {
        SparseMatrix.write(f, new SMTIterator(m, maxMB));
    }

    static class SMTIterator implements Iterator<SparseMatrixRow> {

        private SparseMatrix matrix;
        private int colIds[];
        private TIntIntHashMap colCounts = new TIntIntHashMap();
        private int maxMB;
        private int numColsTransposed = 0;
        private boolean finished = false;

        private Map<Integer, LinkedHashMap<Integer, Float>> transposedBatch =
                new LinkedHashMap<Integer, LinkedHashMap<Integer,Float>>();

        public SMTIterator(SparseMatrix matrix, int maxMB) {
            this.matrix = matrix;
            this.maxMB = maxMB;

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

        public void accumulate() {
            assert(transposedBatch.size() == 0);
            if (numColsTransposed == colIds.length) {
                LOG.info("finished transposing all " + colIds.length + " columns");
                finished = true;
                return;
            }

            // figure out which columns we are tracking
            double mbs = 0;
            TIntHashSet colIdsInBatch = new TIntHashSet();
            for (int i = numColsTransposed; i  < colIds.length; i++) {
                int colId = colIds[i];
                int colSize = colCounts.get(colId);
                double rowMbs = getSizeInMbOfRowDataStructure(colSize);
                if (mbs + rowMbs > maxMB) {
                    break;
                }
                colIdsInBatch.add(colId);
                mbs += rowMbs;
            }
            numColsTransposed += colIdsInBatch.size();
            LOG.info("processing " + colIdsInBatch.size() + " columns in batch (total=" + numColsTransposed + ")");

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

        @Override
        public boolean hasNext() {
            if (!finished & transposedBatch.size() == 0) {
                accumulate();
            }
            if (finished) {
                return false;
            }
            return !finished;
        }

        @Override
        public SparseMatrixRow next() {
            if (!finished && transposedBatch.size() == 0) {
                accumulate();
            }
            if (finished) {
                return null;
            }
            Integer firstRowId = transposedBatch.keySet().iterator().next();
            LinkedHashMap<Integer, Float> rowData = transposedBatch.remove(firstRowId);
            return new SparseMatrixRow(firstRowId, rowData);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
