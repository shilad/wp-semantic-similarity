package edu.macalester.wpsemsim;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.MMapDirectory;

import java.io.File;
import java.io.IOException;

public class TestUtils {
    public static File TEST_INPUT_FILE = new File("dat/test/dump/wp.test.xml");
    public static File TEST_LUCENE_DIR = new File("dat/test/lucene");
    public static File TEST_TMP_DIR = new File("dat/test/tmp");

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

    public static DirectoryReader openReader(File parent, String type) throws IOException {
        File subdir = new File(parent, type);
        return DirectoryReader.open(MMapDirectory.open(subdir));
    }
}
