package edu.macalester.wpsemsim.topics;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.matrix.*;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.EnvConfigurator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.*;
import java.text.DecimalFormat;
import java.util.logging.Logger;

public class VowpalTranslator {
    private static final Logger LOG = Logger.getLogger(VowpalTranslator.class.getName());

    public void encode(IndexHelper helper, File pathSimMatrix, File vowpalDir) throws IOException {
        SparseMatrix matrix = new SparseMatrix(pathSimMatrix,
                1, 1*1024*1024*1024);    // 1 x 1GB page
        FileUtils.deleteQuietly(vowpalDir);
        vowpalDir.mkdirs();

        encodeRows(helper, new File(vowpalDir, "row_ids.tsv"), matrix);
        TIntIntMap colIdToDenseId = encodeExamples(new File(vowpalDir, "input.vw"), matrix);
        encodeCols(helper, new File(vowpalDir, "col_ids.tsv"), colIdToDenseId);
    }

    private void encodeCols(IndexHelper helper, File path, TIntIntMap colIdToDenseId) throws IOException {
        LOG.info("writing col_ids file");
        BufferedWriter wpWriter = new BufferedWriter(new FileWriter(path));
        for (int wpId : colIdToDenseId.keys()) {
            wpWriter.write(
                    "" + colIdToDenseId.get(wpId) +
                    "\t" + wpId +
                    "\t" + helper.wpIdToTitle(wpId) + "\n"
            );
        }
        wpWriter.close();
    }

    private TIntIntMap encodeExamples(File path, SparseMatrix matrix) throws IOException {
        LOG.info("writing vw input file");
        BufferedWriter vwWriter = new BufferedWriter(new FileWriter(path));
        DecimalFormat df = new DecimalFormat("#.######");
        TIntIntMap colIdToDenseId = new TIntIntHashMap();
        for (SparseMatrixRow row : matrix) {
            vwWriter.write("|");
            for (int i = 0; i < row.getNumCols(); i++) {
                int colId = row.getColIndex(i);
                int denseId = colIdToDenseId.adjustOrPutValue(colId, 0, colIdToDenseId.size());
                vwWriter.write(" " + denseId + ":" + df.format(row.getColValue(i)));
            }
            vwWriter.write("\n");
        }
        vwWriter.close();
        return colIdToDenseId;
    }

    private void encodeRows(IndexHelper helper, File path, SparseMatrix matrix) throws IOException {
        LOG.info("writing row_ids file");
        BufferedWriter rowWriter = new BufferedWriter(new FileWriter(path));
        int rowIds[] = matrix.getRowIds();
        for (int i = 0; i < rowIds.length; i++) {
            rowWriter.write(
                    "" + i +
                    "\t" + rowIds[i] +
                    "\t" + helper.wpIdToTitle(rowIds[i]) + "\n"
            );
        }
        rowWriter.close();
    }

    private void decodeArticles(File vowpalPreds, File destMatrix) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(vowpalPreds));
        ValueConf vconf = new ValueConf(0.0f, 1.0f);
        DenseMatrixWriter writer = new DenseMatrixWriter(destMatrix, vconf);
        int colIds[] = null;
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String tokens[] = line.trim().split("\\s+");
            if (colIds == null) {
                colIds = new int[tokens.length];
                for (int i = 0; i < tokens.length; i++) colIds[i] = i;
            }
            if (tokens.length != colIds.length) {
                throw new IllegalStateException(
                        "expected " + colIds.length +
                                " tokens, found " + tokens.length +
                                " in line " + StringEscapeUtils.escapeJava(line));
            }
            float nums[] = new float[tokens.length];
            double sum = 0.0;
            for (int i = 0; i < tokens.length; i++) {
                nums[i] = Float.valueOf(tokens[i]);
                sum += nums[i];
            }
            if (sum != 0) {
                for (int i = 0; i < nums.length; i++) {
                    nums[i] /= sum;
                }
            }
            int rowId = 1;      // FIXME
            writer.writeRow(new DenseMatrixRow(vconf, rowId, colIds, nums));
        }
        writer.finish();
    }
    
    public static void usage() {
        System.err.println("usage: java " + VowpalTranslator.class.getName() + "\n" +
                "\t\tencode conf_file sparse_matrix vw_output_dir\n");
        System.exit(1);
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException {
        if (args.length < 1) {
            usage();
        }
        VowpalTranslator t = new VowpalTranslator();
        if (args[0].equals("encode")) {
            EnvConfigurator env = new EnvConfigurator(
                    new ConfigurationFile(new File(args[1])));
            IndexHelper helper = env.loadIndex("main");
            t.encode(helper, new File(args[2]), new File(args[3]));
        } else if (args[0].equals("decode")) {
        } else {
            usage();
        }
    }
}
