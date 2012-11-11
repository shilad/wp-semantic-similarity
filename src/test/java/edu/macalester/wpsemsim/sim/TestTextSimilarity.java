package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.TestUtils;
import gnu.trove.map.hash.TIntDoubleHashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.util.Bits;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

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
        DirectoryReader reader = helper.getReader();
        Map<Integer, TIntDoubleHashMap> sims = new HashMap<Integer, TIntDoubleHashMap>();
        TextSimilarity sim = new TextSimilarity(helper, "text");
        sim.setMaxPercentage(100);

        Bits bits = MultiFields.getLiveDocs(reader);
        for (int i = 0; i < reader.numDocs(); i++) {
            if (bits != null && !bits.get(i)) { continue; }
            int wpId = Integer.valueOf(reader.document(i).get("id"));
            sims.put(wpId, new TIntDoubleHashMap());
            for (DocScore score : sim.mostSimilar(wpId, Integer.MAX_VALUE)) {
                sims.get(wpId).put(score.getId(), score.getScore());
            }
        }
        for (int i = 0; i < reader.numDocs(); i++) {
            if (bits != null && !bits.get(i)) { continue; }
            for (int j = 0; j < reader.numDocs(); j++) {
                if (bits != null && !bits.get(j)) { continue; }
                Document doc1 = reader.document(i);
                Document doc2 = reader.document(j);
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
