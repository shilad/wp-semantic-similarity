package edu.macalester.wpsemsim.utils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class TestConfigurationFile {

    @Test
    public void testTraversal() throws ConfigurationFile.ConfigurationException, IOException {
        ConfigurationFile conf = new ConfigurationFile(TestUtils.TEST_CONF);
        assertEquals(6, conf.getKeys().size());
        assertEquals(
                new HashSet<String>(Arrays.asList("category-similarity", "article-text", "article-links", "pairwise-text", "pairwise-links", "pairwise-cats")),
                conf.getKeys()
        );
        assertEquals(conf.get("category-similarity").get("type"), "category");
        assertEquals(conf.get("category-similarity").get("test-list"), Arrays.asList("foo", "bar"));
    }
}
