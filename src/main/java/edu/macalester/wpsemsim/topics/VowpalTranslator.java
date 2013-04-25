package edu.macalester.wpsemsim.topics;

import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;

public class VowpalTranslator {
    public static void encode(SparseMatrix matrix, File vowpal) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(vowpal));
        DecimalFormat df = new DecimalFormat("#.######");
        for (SparseMatrixRow row : matrix) {
            for (int i = 0; i < row.getNumCols(); i++) {
                writer.write("|" + row.getColIndex(i) +
                             ":" + df.format(row.getColValue(i)));
            }
            writer.close();
        }
    }

    public static void usage() {
        System.err.println("usage: java " + VowpalTranslator.class.getName() +
                " {encode|decode} input output");
        System.exit(1);
    }

    public static void main(String args[]) throws IOException {
        if (args.length != 3) {
            usage();
        }
        if (args[0].equals("encode")) {
            SparseMatrix m = new SparseMatrix(
                    new File(args[1]), 1, 1*1024*1024*1024);    // 1 x 1GB page
            encode(m, new File(args[2]));
        } else if (args[0].equals("decode")) {
        } else {
            usage();
        }
    }
}
