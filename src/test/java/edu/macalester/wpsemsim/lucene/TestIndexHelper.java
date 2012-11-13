package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TestUtils;
import gnu.trove.list.TIntList;
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
    private DirectoryReader catReader;
    private IndexHelper catHelper;

    private DirectoryReader linkReader;
    private IndexHelper linkHelper;

    @Before
    public void createIndex() throws IOException, InterruptedException {
        this.indexPath = TestUtils.buildIndex();
        this.catHelper = new IndexHelper(new File(indexPath, "cats"), true);
        this.linkHelper = new IndexHelper(new File(indexPath, "links"), true);
        this.catReader = this.catHelper.getReader();
        this.linkReader = this.linkHelper.getReader();
    }

    @Test
    public void testLuceneToWpId() throws IOException {
        assertEquals(catHelper.luceneIdToWpId(0), 12);
        assertEquals(catHelper.luceneIdToWpId(1), 25);
    }

    @Test
    public void testLuceneToTitle() throws IOException {
        assertEquals(catHelper.luceneIdToTitle(0), "Anarchism");
        assertEquals(catHelper.luceneIdToTitle(1), "Autism");
    }

    @Test
    public void testWpIdToTitle() throws IOException {
        assertEquals(catHelper.wpIdToTitle(12), "Anarchism");
        assertEquals(catHelper.wpIdToTitle(25), "Autism");
    }

    @Test
    public void testGetLinkedLuceneIds() throws IOException {
        TIntList otherIds = linkHelper.getLinkedLuceneIdsForWpId(12);
        assertEquals(otherIds.size(), 2);

        // Ayn Rand should come first.
        otherIds.sort();

        assertEquals(linkHelper.luceneIdToTitle(otherIds.get(0)), "Ayn Rand");
        assertEquals(linkHelper.luceneIdToTitle(otherIds.get(1)), "Alain Connes");

    }

    @After
    public void deleteIndex() throws IOException {
        FileUtils.deleteDirectory(this.indexPath);
    }

}
