package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.lucene.IndexBuilder;
import edu.macalester.wpsemsim.utils.TestUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

public class TestIndexBuilder {

    @Test
    public void testBuilder() throws IOException, InterruptedException {
        File f = TestUtils.buildIndex();
        f.delete();
    }

    @Test
    public void walkBuilder() throws IOException, InterruptedException {
        File dir = TestUtils.buildIndex();
        for (int i = 0; i < IndexBuilder.INDEX_INFO.length; i++) {
            String type = IndexBuilder.INDEX_INFO[i][0];
            DirectoryReader reader = TestUtils.openReader(dir, type);
            int count = 0;
            for (int j = 0; j < reader.maxDoc(); j++) {
                Document d = reader.document(j);
                count++;
            }
            assertEquals(17, count);    // ignores redirects
        }
        dir.delete();
    }

}
