package edu.macalester.wpsemsim.utils;

import edu.macalester.wpsemsim.lucene.AllIndexBuilder;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.matrix.*;
import edu.macalester.wpsemsim.sim.*;
import edu.macalester.wpsemsim.sim.utils.SimilarityMetricBuilder;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.MMapDirectory;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

public class TestUtils {
    public static File TEST_INPUT_FILE = new File("dat/test/dump/wp.test.xml");
    public static final File TEST_CATEGORIES = new File("dat/test/article_cats.txt");
    public static final File TEST_CONF = new File("conf/test-configuration.json");

    /**
     * Build a lucene index for the test data.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static File buildIndex() throws IOException, InterruptedException, ConfigurationFile.ConfigurationException {
        return buildIndex(new ArrayList<Page>());
    }
    public static File buildIndex(final List<Page> additional) throws IOException, InterruptedException, ConfigurationFile.ConfigurationException {
        ConfigurationFile conf = makeSandboxConfiguration();
        File outputDir = new File(ConfigurationFile.requireString(conf.get("indexes"), "outputDir"));
        if (outputDir.isFile()) { outputDir.delete(); }
        if (!outputDir.mkdirs()) {
            throw new IllegalArgumentException("couldn't make directory " + outputDir);
        }

        try {
            AllIndexBuilder builder = new AllIndexBuilder(conf, null) {
                @Override
                protected void process(int numThreads) throws InterruptedException {
                    super.process(numThreads);
                    for (Page d : additional) {
                        try {
                            this.storePage(d);
                        } catch (IOException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    }
                }
            };
            builder.write(1, 100);
        } catch (ConfigurationFile.ConfigurationException e) {
            throw new IOException(e);
        }

        return outputDir;
    }

    public static File buildIndexWithCategories() throws IOException, InterruptedException, ConfigurationFile.ConfigurationException {
        // Add fake documents for category structure
        List<Page> cats = new ArrayList<Page>();
        BufferedReader breader = new BufferedReader(new FileReader(TEST_CATEGORIES));
        int id = 1000000;   // larger than any article id in dump file
        while (true) {
            String line = breader.readLine();
            if (line == null) {
                break;
            }
            String tokens[] = line.split(",");
            if (tokens.length >= 2) {
                String title = "Category:" + tokens[0].trim();
                StringBuffer text = new StringBuffer("here comez the cats\n");
                for (int i = 1; i < tokens.length; i++) {
                    text.append(" [[Category:" + tokens[i].trim() + "]]\n");
                }
                Page p = new Page(14, id, null, title, text.toString());
                assert(p.getCategories().size() == tokens.length - 1);
                cats.add(p);
                id++;
            }
        }
        breader.close();
        return buildIndex(cats);
    }

    public static List<SimilarityMetric> buildAllModels() throws IOException, InterruptedException, ConfigurationFile.ConfigurationException {
        File index = buildIndexWithCategories();
        File conf = new File(index.getParent(), "conf.txt");

        SimilarityMetricBuilder builder = new SimilarityMetricBuilder(new ConfigurationFile(conf));
        Env env = builder.build();
        return new ArrayList<SimilarityMetric>(env.getMetrics().values());
    }

    public static ConfigurationFile makeSandboxConfiguration() throws IOException, ConfigurationFile.ConfigurationException {
        File dat = File.createTempFile("wpsemsim-dat", null);
        dat.delete();
        dat.mkdirs();
        File confClone = new File(dat, "conf.txt");
        dat.deleteOnExit();
        confClone.deleteOnExit();
        String content = FileUtils.readFileToString(TEST_CONF);
        content = content.replaceAll("DAT", dat.getAbsolutePath());
        FileUtils.writeStringToFile(confClone, content, false);
        return new ConfigurationFile(confClone);

    }

    /**
     * Open a lucene reader from the lucene directory.
     * Type is from the first column from the AllIndexBuilder info table (i.e. "cats").
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
    public static List<SparseMatrixRow> createSparseTestMatrixRows(int nRows, int maxRowLen, boolean sameIds) throws IOException {
        return createSparseTestMatrixRowsInternal(nRows, maxRowLen, sameIds, null);
    }
    public static List<DenseMatrixRow> createDenseTestMatrixRows(int nRows, int numCols) throws IOException {
        return createDenseTestMatrixRowsInternal(nRows, numCols, null);
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
    public static SparseMatrix createSparseTestMatrix(int nRows, int maxRowLen, boolean sameIds) throws IOException {
        return createSparseTestMatrix(nRows, maxRowLen, SparseMatrix.DEFAULT_LOAD_ALL_PAGES, SparseMatrix.DEFAULT_MAX_PAGE_SIZE, sameIds);
    }
    public static SparseMatrix createSparseTestMatrix(int nRows, int maxRowLen, boolean readAllRows, int pageSize, boolean sameIds) throws IOException {
        File tmpFile = File.createTempFile("matrix", null);
        tmpFile.deleteOnExit();
        SparseMatrixWriter writer = new SparseMatrixWriter(tmpFile, new ValueConf());
        createSparseTestMatrixRowsInternal(nRows, maxRowLen, sameIds, writer);
        writer.finish();
        return new SparseMatrix(tmpFile, readAllRows, pageSize);
    }
    public static DenseMatrix createDenseTestMatrix(int nRows, int numCols) throws IOException {
        return createDenseTestMatrix(nRows, numCols, SparseMatrix.DEFAULT_LOAD_ALL_PAGES, SparseMatrix.DEFAULT_MAX_PAGE_SIZE);
    }
    public static DenseMatrix createDenseTestMatrix(int nRows, int numCols, boolean readAllRows, int pageSize) throws IOException {
        File tmpFile = File.createTempFile("matrix", null);
        tmpFile.deleteOnExit();
        DenseMatrixWriter writer = new DenseMatrixWriter(tmpFile, new ValueConf());
        createDenseTestMatrixRowsInternal(nRows, numCols, writer);
        writer.finish();
        return new DenseMatrix(tmpFile, readAllRows, pageSize);
    }


    /**
     * Either writes or returns the sparse matrix rows depending on whether the writer is passed.
     * @param nRows
     * @param maxRowLen
     * @param sameIds
     * @param writer
     * @return if writer == null the list of rows, else null
     */
    private static List<SparseMatrixRow> createSparseTestMatrixRowsInternal(
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
            SparseMatrixRow row = new SparseMatrixRow(new ValueConf(), id1, data);
            if (writer == null) {
                rows.add(row);
            } else {
                writer.writeRow(row);
            }
        }
        return (writer == null) ? rows : null;
    }

    /**
     * Either writes or returns the sparse matrix rows depending on whether the writer is passed.
     * @param nRows
     * @param numCols
     * @param writer
     * @return if writer == null the list of rows, else null
     */
    private static List<DenseMatrixRow> createDenseTestMatrixRowsInternal(
            int nRows, int numCols, DenseMatrixWriter writer)
            throws IOException {
        Random random = new Random();
        List<DenseMatrixRow> rows = new ArrayList<DenseMatrixRow>();
        int rowIds[] = pickIds(nRows, nRows * 2);
        int colIds[] = pickIds(numCols, numCols * 2);
        for (int id1 : rowIds) {
            LinkedHashMap<Integer, Float> data = new LinkedHashMap<Integer, Float>();
            for (int id2 : colIds) {
                data.put(id2, random.nextFloat());
            }
            DenseMatrixRow row = new DenseMatrixRow(new ValueConf(), id1, data);
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
