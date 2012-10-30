package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.TestUtils;
import gnu.trove.map.hash.TIntDoubleHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestCatSimilarity {
    private static final File TEST_CATEGORY_DUMP = new File("dat/test/cat_info.txt");

    static File indexPath;
    private static CategoryGraph graph;
    private static IndexHelper helper;
    private static CatSimilarity catSim;
    private static IndexReader reader;

    @BeforeClass
    public static void createIndex() throws IOException, InterruptedException {
        indexPath = new File(TestUtils.buildIndexWithCategories(), "cats");
        helper = new IndexHelper(indexPath, true);
        graph = new CategoryGraph(helper);
        graph.init();
        helper = graph.helper;
        reader = graph.reader;
        catSim = new CatSimilarity(graph, helper);

    }

    public double pathDistance(String... cats) {
        double distance = 0;
        for (String c : cats) {
            int ci = graph.getCategoryIndex(c);
            assert(ci >= 0);
            distance += graph.catCosts[ci];
        }
        return distance;
    }

    public double pathSimilarity(String... cats) {
        return CatSimilarity.distanceToScore(graph, pathDistance(cats));
    }

    @Test
    public void testPathScores() {
        assertTrue(
                pathDistance("vowel letters", "poetry", "symphonic poems") <
                pathDistance( "people of the trojan war", "people", "1809 births")
        );
        assertTrue(
                pathDistance("vowel letters", "poetry", "symphonic poems") <
                pathDistance("vowel letters", "poetry", "literature", "books", "1986 books")
        );
        assertTrue(
                pathSimilarity("vowel letters", "poetry", "symphonic poems") >
                pathSimilarity( "people of the trojan war", "people", "1809 births")
        );
        assertTrue(
                pathSimilarity("vowel letters", "poetry", "symphonic poems") >
                pathSimilarity("vowel letters", "poetry", "literature", "books", "1986 books")
        );
    }

    @Test
    public void testSimilarity() throws IOException {
        verifySim("Academy Award", "Academy Award for Best Art Direction", "academy awards");
        verifySim("An American in Paris", "A", "vowel letters", "poetry", "symphonic poems");
        verifySim("Achilles", "Abraham Lincoln", "people of the trojan war", "people", "1809 births");
        verifySim("A", "Animalia (book)", "vowel letters", "poetry", "literature", "books", "1986 books");
        verifySim("Anarchism", "Aristotle", "political ideologies", "philosophy", "political philosophers");
    }

    private void verifySim(String article1, String article2, String... path) throws IOException {
        double s1 = pathSimilarity(path);
        assert(s1 >= 0);
        int wpId1 = helper.titleToWpId(article1);
        int wpId2 = helper.titleToWpId(article2);
        assertTrue(wpId1 >= 0);
        assertTrue(wpId2 >= 0);
        double s2 = catSim.similarity(wpId1, wpId2);
        assertEquals(s1, s2, 0.001);
    }

    @Test
    public void testBfs() throws IOException {
        verifyBfsDistance("Academy Award", "Academy Award for Best Art Direction", "academy awards");
        verifyBfsDistance("An American in Paris", "A", "vowel letters", "poetry", "symphonic poems");
        verifyBfsDistance("Achilles", "Abraham Lincoln", "people of the trojan war", "people", "1809 births");
        verifyBfsDistance("A", "Animalia (book)", "vowel letters", "poetry", "literature", "books", "1986 books");
        verifyBfsDistance("Anarchism", "Aristotle", "political ideologies", "philosophy", "political philosophers");
    }

    public void verifyBfsDistance(String title1, String title2, String... path) throws IOException {
        Document d = reader.document(helper.titleToLuceneId(title1));
        CategoryBfs bfs = new CategoryBfs(graph, d, Integer.MAX_VALUE);
        while (bfs.hasMoreResults()) {
            bfs.step();
        }
        int wpId = helper.titleToWpId(title2);
        assertTrue(bfs.hasPageDistance(wpId));
        assertEquals(pathDistance(path), bfs.getPageDistance(wpId), 0.001);

    }

    @Test
    // For debugging
    public void generateDump() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(TEST_CATEGORY_DUMP));
        writer.write("Category information\n");
        graph.dump(writer);
        writer.close();
    }

    @Test
    public void testSimilarityMatchesMostSimilar() throws IOException {

        DirectoryReader reader = helper.getReader();
        Map<Integer, TIntDoubleHashMap> sims = new HashMap<Integer, TIntDoubleHashMap>();
        for (int i = 0; i < reader.numDocs(); i++) {
            int wpId = Integer.valueOf(reader.document(i).get("id"));
            sims.put(wpId, new TIntDoubleHashMap());
            for (DocScore score : catSim.mostSimilar(wpId, Integer.MAX_VALUE)) {
                sims.get(wpId).put(score.getId(), score.getScore());
            }
        }
        for (int i = 0; i < reader.numDocs(); i++) {
            for (int j = 0; j < reader.numDocs(); j++) {
                Document doc1 = reader.document(i);
                Document doc2 = reader.document(j);
                if (graph.isCat(doc1) || graph.isCat(doc2)) {
                    continue;
                }
                int wpId1 = Integer.valueOf(doc1.get("id"));
                int wpId2 = Integer.valueOf(doc2.get("id"));
                double s = catSim.similarity(wpId1, wpId2);
                if (sims.containsKey(wpId1) && sims.get(wpId1).containsKey(wpId2)) {
//                    System.out.println("for present " + wpId1 + ", " + wpId2 + " comparing " + s + " and " + sims.get(wpId1).get(wpId2));
                    assertEquals(s, sims.get(wpId1).get(wpId2), 0.001);
                } else {
//                    System.out.println("for missing " + wpId1 + ", " + wpId2 + ": " + s);
                    assertEquals(s, Double.NEGATIVE_INFINITY, 0.0001);
                }
            }
        }
    }


    @AfterClass
    public static void removeIndex() {
        indexPath.delete();
    }
}
