package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.ConfigurationFile;
import edu.macalester.wpsemsim.utils.TestUtils;
import gnu.trove.list.TIntList;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;
import org.junit.*;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class TestIndexHelper {

    private static File indexPath;
    private static DirectoryReader catReader;
    private static IndexHelper catHelper;

    private static DirectoryReader linkReader;
    private static IndexHelper linkHelper;

    @BeforeClass
    public static void createIndex() throws IOException, InterruptedException, ConfigurationFile.ConfigurationException {
        indexPath = TestUtils.buildIndex();
        catHelper = new IndexHelper(new File(indexPath, "cats"), true);
        linkHelper = new IndexHelper(new File(indexPath, "links"), true);
        catReader = catHelper.getReader();
        linkReader = linkHelper.getReader();
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
    public void testTitleToWpId() throws IOException {
        assertEquals(linkHelper.titleToWpId("Anarchism"), 12);
        assertEquals(linkHelper.titleToWpId("Autism"), 25);
        assertEquals(linkHelper.titleToWpId("Ayn Rand"), 339);
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

    @Test
    public void testGetTermFreq() throws IOException {
        int wpId = linkHelper.titleToWpId("Academy Award");
        assertEquals(linkHelper.getDocFreq(Page.FIELD_LINKS, ""+wpId), 2);
    }

    @AfterClass
    public static void deleteIndex() throws IOException {
        FileUtils.deleteDirectory(indexPath);
    }

}
