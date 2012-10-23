package edu.macalester.wpsemsim.matrix;

import edu.macalester.wpsemsim.IndexHelper;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Logger;

public class MatrixSummarizer {
    public static Logger LOG = Logger.getLogger(MatrixSummarizer.class.getName());

    public MatrixSummarizer(SparseMatrix matrix, IndexHelper helper) {
        final TIntIntHashMap counts = new TIntIntHashMap();
        final TIntDoubleHashMap sums = new TIntDoubleHashMap();

        int rowNum = 0;
        for (SparseMatrixRow row : matrix) {
            for (int i = 0; i < row.getNumCols(); i++) {
                int id = row.getColIndex(i);
                double val = row.getColValue(i);
                counts.adjustOrPutValue(id, 1, 1);
                sums.adjustOrPutValue(id, val, val);
                counts.adjustOrPutValue(row.getRowIndex(), 1, 1);
                sums.adjustOrPutValue(row.getRowIndex(), val, val);
            }
            if (rowNum++ % 100000 == 0) {
                LOG.info("reading row " + rowNum + " of " + matrix.getNumRows());
            }
        }

        // sort by counts
        Integer ids[] = ArrayUtils.toObject(counts.keys());
        Arrays.sort(ids, new Comparator<Integer>() {
            @Override
            public int compare(Integer id1, Integer id2) {
                return counts.get(id2) - counts.get(id1);
            }
        });
        System.out.println("top counts:");
        for (int i = 0; i < Math.min(100, ids.length); i++) {
            System.out.println(
                    "" + (i+1) + ". " +
                    helper.wpIdToTitle(ids[i]) +
                    "(id=" + ids[i] + ")" +
                    " count = " + counts.get(ids[i])
            );
        }

        // sort by sums
        Arrays.sort(ids, new Comparator<Integer>() {
            @Override
            public int compare(Integer id1, Integer id2) {
                double s1 = sums.get(id1);
                double s2 = sums.get(id2);
                if (s1 > s2) { return -1; }
                else if (s1 < s2) { return +1; }
                else { return 0; }
            }
        });
        System.out.println("top sums:");
        for (int i = 0; i < Math.min(100, ids.length); i++) {
            System.out.println(
                    "" + (i+1) + ". " +
                            helper.wpIdToTitle(ids[i]) +
                            "(id=" + ids[i] + ")" +
                            " count = " + sums.get(ids[i])
            );
        }
    }

    public static void main(String args[]) throws IOException {
        if (args.length != 2) {
            System.err.println("java " + MatrixSummarizer.class.getName() +
                                " path_matrix path_lucene_index");
            System.exit(1);
        }
        SparseMatrix matrix = new SparseMatrix(new File(args[0]), false, 500*1024*1024);
        IndexHelper helper = new IndexHelper(
                DirectoryReader.open(FSDirectory.open(new File(args[1]))));
    }
}