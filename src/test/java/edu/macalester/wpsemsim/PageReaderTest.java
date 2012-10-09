package edu.macalester.wpsemsim;

import org.junit.Test;
import static org.junit.Assert.* ;

import java.util.ArrayList;
import java.util.List;

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

        assertEquals(first.getId(), 10);
        assertEquals(second.getId(), 12);
        assertEquals(last.getId(), 340);
    }
}
