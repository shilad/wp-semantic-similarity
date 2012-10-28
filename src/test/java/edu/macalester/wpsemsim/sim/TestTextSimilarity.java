package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.TestUtils;
import gnu.trove.map.hash.TIntDoubleHashMap;
import org.apache.lucene.document.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestTextSimilarity {
    static File indexPath;
    static IndexHelper helper;

    @BeforeClass
    public static void createIndex() throws IOException, InterruptedException {
        indexPath = new File(TestUtils.buildIndex(), "text");
        helper = new IndexHelper(indexPath, true);
    }

    @Test
    public void testSimilarity() throws IOException {
        Map<Integer, TIntDoubleHashMap> sims = new HashMap<Integer, TIntDoubleHashMap>();
        TextSimilarity sim = new TextSimilarity("text");
        sim.setMaxPercentage(100);
        sim.openIndex(helper);

        for (int i = 0; i < sim.reader.numDocs(); i++) {
            int wpId = Integer.valueOf(sim.reader.document(i).get("id"));
            sims.put(wpId, new TIntDoubleHashMap());
            for (DocScore score : sim.mostSimilar(wpId, Integer.MAX_VALUE)) {
                sims.get(wpId).put(score.getId(), score.getScore());
            }
        }
        sim.openIndex(helper);
        for (int i = 0; i < sim.reader.numDocs(); i++) {
            for (int j = 0; j < sim.reader.numDocs(); j++) {
                Document doc1 = sim.reader.document(i);
                Document doc2 = sim.reader.document(j);
                int wpId1 = Integer.valueOf(doc1.get("id"));
                int wpId2 = Integer.valueOf(doc2.get("id"));
                double s = sim.similarity(wpId1, wpId2);
                assertEquals(s, sims.get(wpId1).get(wpId2), 0.001);
            }
        }
    }


    @AfterClass
    public static void removeIndex() {
        indexPath.delete();
    }
}
