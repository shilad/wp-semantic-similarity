package edu.macalester.wpsemsim.utils;

import edu.macalester.wpsemsim.lucene.IndexBuilder;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.matrix.SparseMatrixWriter;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.MMapDirectory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

public class TestUtils {
    public static File TEST_INPUT_FILE = new File("dat/test/dump/wp.test.xml");

    /**
     * Build a lucene index for the test data.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static File buildIndex() throws IOException, InterruptedException {
        File outputDir = File.createTempFile("lucene", null);
        if (outputDir.isFile()) { outputDir.delete(); }
        if (!outputDir.mkdir()) {
            throw new AssertionError("couldn't make directory " + outputDir);
        }
        IndexBuilder builder = new IndexBuilder(TEST_INPUT_FILE, outputDir);
        builder.openIndex(10);
        builder.write(1);
        return outputDir;
    }

    /**
     * Open a lucene reader from the lucene directory.
     * Type is from the first column from the IndexBuilder info table (i.e. "cats").
     *
     * @param parent
     * @param type
     * @return
     * @throws IOException
     */
    public static DirectoryReader openReader(File parent, String type) throws IOException {
        File subdir = new File(parent, type);
        return DirectoryReader.open(MMapDirectory.open(subdir));
    }

    /**
     * Creates a new random matrix with nRows rows.
     * Each row has a random length chosen uniformly from 0 to maxRowLen.
     * If sameIds is true, the column ids are chosen from the row ids.
     * @param nRows
     * @param maxRowLen
     * @param sameIds
     * @return
     */
    public static List<SparseMatrixRow> createTestMatrixRows(int nRows, int maxRowLen, boolean sameIds) throws IOException {
        return createTestMatrixRowsInternal(nRows, maxRowLen, sameIds, null);
    }


    /**
     * Creates a new random matrix with nRows rows.
     * Each row has a random length chosen uniformly from 0 to maxRowLen.
     * If sameIds is true, the column ids are chosen from the row ids.
     * @param nRows
     * @param maxRowLen
     * @param sameIds
     * @return
     */
    public static SparseMatrix createTestMatrix(int nRows, int maxRowLen, boolean sameIds) throws IOException {
        return createTestMatrix(nRows, maxRowLen, SparseMatrix.DEFAULT_LOAD_ALL_PAGES, SparseMatrix.DEFAULT_MAX_PAGE_SIZE, sameIds);
    }
    public static SparseMatrix createTestMatrix(int nRows, int maxRowLen, boolean readAllRows, int pageSize, boolean sameIds) throws IOException {
        File tmpFile = File.createTempFile("matrix", null);
        tmpFile.deleteOnExit();
        SparseMatrixWriter writer = new SparseMatrixWriter(tmpFile);
        createTestMatrixRowsInternal(nRows, maxRowLen, sameIds, writer);
        writer.finish();
        return new SparseMatrix(tmpFile, readAllRows, pageSize);
    }


    /**
     * Either writes or returns the sparse matrix rows depending on whether the writer is passed.
     * @param nRows
     * @param maxRowLen
     * @param sameIds
     * @param writer
     * @return if writer == null the list of rows, else null
     */
    private static List<SparseMatrixRow> createTestMatrixRowsInternal(
            int nRows, int maxRowLen, boolean sameIds, SparseMatrixWriter writer)
            throws IOException {
        Random random = new Random();
        List<SparseMatrixRow> rows = new ArrayList<SparseMatrixRow>();
        int rowIds[] = pickIds(nRows, nRows * 2);
        for (int id1 : rowIds) {
            LinkedHashMap<Integer, Float> data = new LinkedHashMap<Integer, Float>();
            int numCols = Math.max(1, random.nextInt(maxRowLen));
            int colIds[] = sameIds ? pickIdsFrom(rowIds, numCols) : pickIds(numCols, maxRowLen * 2);
            for (int id2 : colIds) {
                data.put(id2, random.nextFloat());
            }
            SparseMatrixRow row = new SparseMatrixRow(id1, data);
            if (writer == null) {
                rows.add(row);
            } else {
                writer.writeRow(row);
            }
        }
        return (writer == null) ? rows : null;
    }


    /**
     * Returns a set of n unique ids from 1 through maxId in random order.
     * @param n
     * @param maxId
     * @return
     */
    public static int[] pickIds(int n, int maxId) {
        assert(n < maxId);
        Random random = new Random();
        TIntHashSet picked = new TIntHashSet();
        for (int i = 0; i < n; i++) {
            while (true) {
                int id = random.nextInt(maxId - 1) + 1;
                if (!picked.contains(id)) {
                    picked.add(id);
                    break;
                }
            }
        }
        return picked.toArray();
    }

    /**
     * Selects n random unique ids from the array of ids.
     * @param ids
     * @param n
     * @return
     */
    public static int[] pickIdsFrom(int ids[], int n) {
        assert(ids.length >= n);
        Random random = new Random();
        TIntHashSet picked = new TIntHashSet();
        for (int i = 0; i < n; i++) {
            while (true) {
                int id = ids[random.nextInt(ids.length)];
                if (!picked.contains(id)) {
                    picked.add(id);
                    break;
                }
            }
        }
        return picked.toArray();
    }
}
