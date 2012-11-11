package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PageReaderTest {
    @Test
    public void testBasicReader() {
        List<Page> pages = new ArrayList<Page>();
        for (Page p : new PageReader(TestUtils.TEST_INPUT_FILE)) {
            pages.add(p);
        }
        assertEquals(pages.size(), 83);
        Page first = pages.get(0);
        Page second = pages.get(1);
        Page last = pages.get(pages.size() - 1);
        assert(first.isRedirect());
        assert(!second.isRedirect());
        assert(!first.isDisambiguation());
        assert(!second.isDisambiguation());
        assert(!last.isDisambiguation());
        assertEquals(first.getRedirect(), "Computer accessibility");

        assertEquals(first.getId(), 10);
        assertEquals(second.getId(), 12);
        assertEquals(last.getId(), 340);
    }

    @Test
    public void testDisambiguation() throws IOException {

        assertFalse(Page.isDisambiguation("FOO"));
        assertTrue(Page.isDisambiguation("FODFS SDSFD {  { dabfooSD"));
        assertTrue(Page.isDisambiguation("FODFS SDSFD {{ dablink  } } \n{  { dabfooSD}}"));
        assertFalse(Page.isDisambiguation("FODFS SDSFD {{ dablink  } } {  { dablink }}"));
        assertTrue(Page.isDisambiguation("FODFS SDSFD {{ dablink  } } {  { dablink }} {{dabFOO}}"));
        assertFalse(Page.isDisambiguation("FODFS SDSFD {{ dablink  } } \n{  { dablink }} {{z dabFOO}}"));

        String text = FileUtils.readFileToString(new File("dat/test/apple.txt"));
        Page p = new Page(0, 0, null, null, text);
        assert(p.isDisambiguation());
        List<String> links = p.getDisambiguationLinksWithFragments();
        assertEquals(links.size(), 47);
        assertEquals(links.get(0), "apple");
        assertEquals(links.get(1), "Cashew apple");
        assertEquals(links.get(46), "The Apple Tree");

    }
}
