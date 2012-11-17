package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.lucene.IndexHelper;
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
        try {
            TopDocs docs = helper.getSearcher().search(qp.parse(text), 200);
            for (ScoreDoc d : docs.scoreDocs) {
                d.score *= getBoost(d.doc);
            }
            Arrays.sort(docs.scoreDocs, new Comparator<ScoreDoc>() {
                @Override
                public int compare(ScoreDoc sd1, ScoreDoc sd2) {
                    if (sd1.score > sd2.score) {
                        return -1;
                    } else if (sd1.score < sd2.score) {
                        return +1;
                    } else {
                        return sd1.doc - sd2.doc;
                    }
                }
            });

            LinkedHashMap<String, Float> result = new LinkedHashMap<String, Float>();
            for (int i = 0; i < docs.scoreDocs.length && i < maxConcepts; i++) {
                result.put(
                        helper.luceneIdToTitle(docs.scoreDocs[i].doc),
                        docs.scoreDocs[i].score
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


    private double getBoost(int luceneId) throws IOException {
        Document d = helper.getReader().document(luceneId, new HashSet<String>(Arrays.asList("ninlinks")));
        return Math.log(d.getField("ninlinks").numericValue().intValue());
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
            for (Map.Entry<String, Float> entry : mapper.map(line, 50).entrySet()) {
                System.out.println("\t" + entry.getKey() +  ": " + entry.getValue());
            }
        }
    }
}
