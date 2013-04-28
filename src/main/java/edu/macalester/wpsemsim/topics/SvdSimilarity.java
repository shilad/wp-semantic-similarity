package edu.macalester.wpsemsim.topics;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.DenseMatrix;
import edu.macalester.wpsemsim.matrix.DenseMatrixRow;
import edu.macalester.wpsemsim.sim.BaseSimilarityMetric;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.DocScoreList;
import gnu.trove.set.TIntSet;

import java.io.File;
import java.io.IOException;

public class SvdSimilarity extends BaseSimilarityMetric {
    private DenseMatrix matrix = null;
    private SimilarityMetric mostSimilarMetric = null;

    public SvdSimilarity(ConceptMapper mapper, IndexHelper helper, DenseMatrix matrix) {
        super(mapper, helper);
        this.matrix = matrix;
    }

    public void setMostSimilarMetric(SimilarityMetric metric) {
        this.mostSimilarMetric = metric;
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        DenseMatrixRow row1 = matrix.getRow(wpId1);
        DenseMatrixRow row2 = matrix.getRow(wpId2);
        if (row1 == null || row2 == null) {
            return 0.0;
        }
        return cosine(row1, row2);
    }

    /**
     * Computes the cosimilarity matrix for a set of wpIds.
     * @param wpIds
     * @return
     * @throws IOException
     */
    /*
    public float[][] getCosimilarities(int wpIds[]) throws IOException {
        float rows[][] = new float[wpIds.length][];
        for (int i = 0; i < wpIds.length; i++) {
            DenseMatrixRow row = matrix.getRow(wpIds[i]);
            rows[i] = row == null ? null : row.getValues();
        }

        float cosimilarity[][] = new float[wpIds.length][wpIds.length];
        for (int i = 0; i < wpIds.length; i++) {
            rows[i][i] = 1.0f;
            for (int j = 0; j < i; j++) {
                double sim = cosine(rows[i], rows[j]);
                cosimilarity[i][j] = (float) sim;
                cosimilarity[j][i] = (float) sim;
            }
        }
        return cosimilarity;
    } */

    private double cosine(DenseMatrixRow X, DenseMatrixRow Y) {
        double xx = 0.0;
        double yy = 0.0;
        double xy = 0.0;

        if (X.getNumCols() != Y.getNumCols()) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < X.getNumCols(); i++) {
            double x = X.getColValue(i);
            double y = Y.getColValue(i);
            xx += x * x;
            yy += y * y;
            xy += x * y;
        }
        if (xx == 0 || yy == 0) {
            return 0.0;
        } else {
            return xy / Math.sqrt(xx * yy);
        }
    }

    private double cosine(float X[], float Y[]) {
        double xx = 0.0;
        double yy = 0.0;
        double xy = 0.0;

        if (X.length != Y.length) {
            throw new IllegalArgumentException();
        }
        for (int i = 0; i < X.length; i++) {
            double x = X[i];
            double y = Y[i];
            xx += x * x;
            yy += y * y;
            xy += x * y;
        }
        if (xx == 0 || yy == 0) {
            return 0.0;
        } else {
            return xy / Math.sqrt(xx * yy);
        }
    }

    @Override
    public void read(File path) {
        // do nothing, for now.
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException {
        if (mostSimilarMetric == null) {
            throw new UnsupportedOperationException();
        } else {
            return mostSimilarMetric.mostSimilar(wpId1, maxResults, possibleWpIds);
        }
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException {
        if (mostSimilarMetric == null) {
            throw new UnsupportedOperationException();
        } else {
            return mostSimilarMetric.mostSimilar(phrase, maxResults, possibleWpIds);
        }
    }
}
