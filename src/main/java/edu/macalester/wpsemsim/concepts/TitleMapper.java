package edu.macalester.wpsemsim.concepts;

import com.sleepycat.je.DatabaseException;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.lucene.Page;
import edu.macalester.wpsemsim.sim.esa.ESAAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maps concepts by searching for a phrase in the "text" field of a lucene index.
 */
public class TitleMapper implements ConceptMapper {
    private static final Logger LOG = Logger.getLogger(TitleMapper.class.getName());

    private IndexHelper helper;

    public TitleMapper(IndexHelper helper) {
        this.helper = helper;
    }

    @Override
    public LinkedHashMap<String, Float> map(String text, int maxConcepts) {
        LinkedHashMap<String, Float> result = null;
        try {
            String title = helper.followRedirects(text);
            if (title != null) {
                result = new LinkedHashMap<String, Float>();
                result.put(title, 1.0f);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "lookup of title '" + text + "' failed", e);
        }
        return result;
    }

    public static void main(String args[]) throws IOException, DatabaseException {
        IndexHelper helper = new IndexHelper(new File(args[0]), true);
        ConceptMapper mapper = new TitleMapper(helper);
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
