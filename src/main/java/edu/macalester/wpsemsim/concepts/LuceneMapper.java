package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.sim.ESAAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.ScoreCachingWrappingScorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LuceneMapper implements ConceptMapper {
    private static final Logger LOG = Logger.getLogger(LuceneMapper.class.getName());

    private IndexHelper helper;

    public LuceneMapper(IndexHelper helper) {
        this.helper = helper;
    }

    @Override
    public LinkedHashMap<String, Float> map(String text, int maxConcepts) {
        QueryParser qp = new QueryParser(Version.LUCENE_40, "text", new ESAAnalyzer());
//        QueryParser qp = new QueryParser(Version.LUCENE_40, "text", new StandardAnalyzer(Version.LUCENE_40));
        try {
            TopDocs docs = helper.getSearcher().search(qp.parse(text), maxConcepts * 10);
            for (ScoreDoc sd : docs.scoreDocs) {
                Document d = helper.getReader().document(sd.doc);
                double boost = Math.log(1 + d.getFields(Page.FIELD_INLINKS).length);
                sd.score = (float)(sd.score * boost);
            }
            Arrays.sort(docs.scoreDocs, new Comparator<ScoreDoc>() {
                @Override
                public int compare(ScoreDoc sd1, ScoreDoc sd2) {
                    return -1 * new Float(sd1.score).compareTo(new Float(sd2.score));
                }
            });
            final LinkedHashMap<String, Float> result = new LinkedHashMap<String, Float>();
            double sum = 0.0;
            for (int i = 0; i < docs.scoreDocs.length && i < maxConcepts; i++) {
                sum += docs.scoreDocs[i].score;
            }
            for (int i = 0; i < docs.scoreDocs.length && i < maxConcepts; i++) {
                result.put(
                        helper.luceneIdToTitle(docs.scoreDocs[i].doc),
                        (float)(docs.scoreDocs[i].score / sum)
                    );
            }
            return result;
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            LOG.log(Level.WARNING, "parsing of phrase " + text + " failed", e);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "parsing of phrase " + text + " failed", e);
        }
        return null;
    }

    public static void main(String args[]) throws IOException, DatabaseException {
        IndexHelper helper = new IndexHelper(new File(args[0]), true);
        ConceptMapper mapper = new LuceneMapper(helper);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Enter phrase: ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            System.out.println("results for '" + line + "'");
            for (Map.Entry<String, Float> entry : mapper.map(line, 100).entrySet()) {
                System.out.println("\t" + entry.getKey() +  ": " + entry.getValue());
            }
        }
    }
}
