package edu.macalester.wpsemsim.sim.utils;

import edu.macalester.wpsemsim.matrix.DenseMatrix;
import edu.macalester.wpsemsim.matrix.DenseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;

import org.apache.commons.lang3.ArrayUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * A similarity metric that directly uses the output of PhraseAnalyzer.
 * The similarity metric operates on client ids, not Strings or Wikipedia ids.
 */
public class KnownPhraseSimilarity implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(KnownPhraseSimilarity.class.getName());

    private SparseMatrix mostSimilarMatrix;
    private DenseMatrix similarityMatrix;
    private File directory;
    private int numOpenPages;
    private int maxPageSize;

    public KnownPhraseSimilarity(File directory) throws IOException {
        this(directory, 4, 250 * 1024 * 1024);  // 4 * 250MB memory mapped pages
    }

    public KnownPhraseSimilarity(File directory, int numOpenPages, int maxPageSize) throws IOException {
        this.numOpenPages = numOpenPages;
        this.maxPageSize = maxPageSize;
        read(directory);
    }

    @Override
    public String getName() {
        return "phrase-similarity-" + directory;
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException {
        throw new UnsupportedOperationException();
    }

    public static String normalize(String phrase) {
        return PhraseAnalyzer.normalize(phrase);
    }

    @Override
    public void read(File dir) throws IOException {
        this.directory = dir;
        mostSimilarMatrix = new SparseMatrix(new File(dir, "mostSimilar.matrix"), numOpenPages, maxPageSize);
        similarityMatrix = new DenseMatrix(new File(dir, "similarity.matrix"), numOpenPages, maxPageSize);
    }


    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double similarity(int clientId1, int clientId2) throws IOException {
        DenseMatrixRow row1 = similarityMatrix.getRow(clientId1);
        DenseMatrixRow row2 = similarityMatrix.getRow(clientId2);
        if (row1 == null) {
            throw new IllegalArgumentException("unknown client id: " + clientId1);
        }
        if (row2 == null) {
            throw new IllegalArgumentException("unknown client id: " + clientId2);
        }
        float sim1 = row1.getValueForId(clientId2);
        float sim2 = row2.getValueForId(clientId1);
        if (Float.isNaN(sim1) || Float.isNaN(sim2)) {
            throw new IllegalArgumentException();
        }
        return 0.5 * sim1 + 0.5 * sim2;
    }

    public float[][] cosimilarity(int clientRowIds[], int clientColIds[]) throws IOException {
        float cosimilarity[][] = new float[clientRowIds.length][clientColIds.length];

        addCosims(clientRowIds, clientColIds, cosimilarity, false);
        addCosims(clientColIds, clientRowIds, cosimilarity, true);

        return cosimilarity;
    }

    /**
     * Adds cosimilarities to the matrix for a set of row ids and col ids.
     * @param rowIds
     * @param colIds
     * @param cosims
     * @param transpose If true, the cosims matrix is transposed.
     * @throws IOException
     */
    private void addCosims(int rowIds[], int colIds[], float cosims[][], boolean transpose) throws IOException {
        // Compute dense indexes for sparse column ids
        int colIndexes[] = new int[colIds.length];
        for (int i = 0; i < colIds.length; i++) {
            colIndexes[i] = ArrayUtils.indexOf(similarityMatrix.getColIds(), colIds[i]);
        }

        for (int i = 0; i < rowIds.length; i++) {
            DenseMatrixRow row = similarityMatrix.getRow(rowIds[i]);
            if (row == null) continue;
            for (int j = 0; j < colIds.length; j++) {
                int col = colIndexes[j];
                if (col >= 0) {
                    assert(row.getColIndex(col) == colIds[j]);
                    float sim = row.getColValue(col);

                    // Add half of sim to each symmetric entry in the matrix .
                    // The other half comes from the transpose entry.
                    if (transpose) {
                        cosims[j][i] += sim * 0.5;
                    } else {
                        cosims[i][j] += sim * 0.5;
                    }
                }
            }
        }
    }

    @Override
    public DocScoreList mostSimilar(int clientId, int maxResults) throws IOException {
        return mostSimilar(clientId, maxResults, null);
    }

    /**
     * All input and output ids are client ids.
     * @param clientId The client id to find most similar items for.
     * @param maxResults The maximum number of neighbors.
     * @param validIds The Wikipedia ids that should be considered in result sets. Null means all ids.
     * @return
     * @throws IOException
     */
    @Override
    public DocScoreList mostSimilar(int clientId, int maxResults, TIntSet validIds) throws IOException {
        SparseMatrixRow row = mostSimilarMatrix.getRow(clientId);
        if (row == null) {
            return new DocScoreList(0);
        }
        int n = 0;
        DocScoreList top = new DocScoreList(Math.min(maxResults, row.getNumCols()));
        for (int i = 0; i < row.getNumCols() && n < maxResults; i++) {
            int id = row.getColIndex(i);
            if (validIds == null || validIds.contains(id)) {
                top.set(n++, id, row.getColValue(i));
            }
        }
        top.truncate(n);
        return top;
    }
    @Override
    public void trainSimilarity(List<KnownSim> labeled) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds) {
        throw new UnsupportedOperationException();
    }
    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException {
        throw new UnsupportedOperationException("validIds not supported");
    }
    @Override
    public void write(File directory) throws IOException {
        throw new UnsupportedOperationException();
    }
}
