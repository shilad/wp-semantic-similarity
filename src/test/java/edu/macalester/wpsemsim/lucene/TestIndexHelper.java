package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class TestIndexHelper {

    private File indexPath;
    private DirectoryReader reader;
    private IndexHelper helper;

    @Before
    public void createIndex() throws IOException, InterruptedException {
        this.indexPath = TestUtils.buildIndex();
        this.helper = new IndexHelper(new File(indexPath, "cats"), true);
        this.reader = this.helper.getReader();
    }

    @Test
    public void testLuceneToWpId() throws IOException {
        assertEquals(helper.luceneIdToWpId(0), 12);
        assertEquals(helper.luceneIdToWpId(1), 25);
    }

    @Test
    public void testLuceneToTitle() throws IOException {
        assertEquals(helper.luceneIdToTitle(0), "Anarchism");
        assertEquals(helper.luceneIdToTitle(1), "Autism");
    }

    @Test
    public void testWpIdToTitle() throws IOException {
        assertEquals(helper.wpIdToTitle(12), "Anarchism");
        assertEquals(helper.wpIdToTitle(25), "Autism");
    }

    @After
    public void deleteIndex() throws IOException {
        FileUtils.deleteDirectory(this.indexPath);
    }

}
