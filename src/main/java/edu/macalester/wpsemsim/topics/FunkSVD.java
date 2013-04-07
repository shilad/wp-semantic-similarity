package edu.macalester.wpsemsim.topics;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class FunkSVD {
    private static final Logger LOG = Logger.getLogger(FunkSVD.class.getName());

    /**
     * Rank of the reduced matrix.
     */
    private int rank;

    /**
     * Low rank approximation for each matrix row
     */
    private double rowApproximations[][];

    /**
     * Low rank approximation for each matrix column
     */
    private double columnApproximations[][];


    /**
     * Mapping from sparse WP ids to a dense int.
     */
    private TIntIntMap columnMap = new TIntIntHashMap();


    /**
     * Mapping from dense mapped column ids to WP ids
     */
    private int reverseColumnMap[];

    private double learningRate = 0.001;
    private double regularization = 0.02;

    private SparseMatrix matrix;

    public FunkSVD(SparseMatrix matrix, int rank) {
        this.rank = rank;
        this.matrix = matrix;
        makeColumnMapping();
        rowApproximations = new double[matrix.getNumRows()][rank];
        columnApproximations = new double[columnMap.size()][rank];
    }

    public void estimate() {
        for (int i = 0; i < rank; i++) {
            LOG.info("doing iteration " + i);
            double rmse = doIteration(i);
            LOG.info("rmse for iteration " + i + " is " + rmse);
        }
    }


    public double doIteration(int dim) {
        double totalErr2 = 0.0;
        long n = 0;
        int r = 0;
        for (SparseMatrixRow row : matrix) {
            double rowV[] = rowApproximations[r++];
            for (int c = 0; c < row.getNumCols(); c++) {
                double colV[] = columnApproximations[columnMap.get(row.getColIndex(c))];
                double pred = dot(rowV, colV, dim);
                double err = row.getColValue(c) - pred;

                double colVV = colV[dim];
                double rowVV = rowV[dim];
                rowV[dim] += learningRate * (err * colVV - regularization * rowVV);
                colV[dim] += learningRate * (err * rowVV - regularization * colVV);
                totalErr2 += err * err;
                n++;
            }
        }
        return Math.sqrt(totalErr2 / n);
    }

    private static final double dot(double X[], double Y[], int maxDim) {
        assert(X.length == Y.length);
        assert(maxDim < X.length);
        double sum = 0.0;
        for (int i = 0; i <= maxDim; i++) {
            sum += X[i] * Y[i];
        }
        return sum;
    }

    private void makeColumnMapping() {
        LOG.info("creating dense indexing for column ids");
        for (SparseMatrixRow row : matrix) {
            for (int i = 0; i < row.getNumCols(); i++) {
                int colId = row.getColIndex(i);
                if (!columnMap.containsKey(colId)) {
                    columnMap.put(colId, columnMap.size());
                }
            }
        }
        reverseColumnMap = new int[columnMap.size()];
        for (int colId : columnMap.keys()) {
            reverseColumnMap[columnMap.get(colId)] = colId;
        }
        LOG.info("finished dense indexing for " + reverseColumnMap.length + " column ids");
    }

    public static void main(String args[]) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: java " + FunkSVD.class.getName() + " path_matrix rank");
            System.exit(1);
        }
        SparseMatrix m = new SparseMatrix(new File(args[0]));
        FunkSVD svd = new FunkSVD(m, Integer.valueOf(args[1]));
        svd.estimate();
    }
}
