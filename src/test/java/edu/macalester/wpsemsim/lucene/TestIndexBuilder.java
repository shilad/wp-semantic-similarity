package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.utils.TestUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class TestIndexBuilder {

    @Test
    public void testBuilder() throws IOException, InterruptedException {
        File f = TestUtils.buildIndex();
        f.delete();
    }

    @Test
    public void walkBuilder() throws IOException, InterruptedException {
        File dir = TestUtils.buildIndex();
        for (BaseIndexGenerator g : new AllIndexBuilder(null, null).generators) {
            DirectoryReader reader = TestUtils.openReader(dir, g.getName());
            int count = 0;
            for (int j = 0; j < reader.maxDoc(); j++) {
                Document d = reader.document(j);
                count++;
            }
            if (g.getName().equals("main")) {
                assertEquals(83, count);
            } else {
                assertEquals(17, count);    // ignores redirects
            }
        }
        dir.delete();
    }

}
