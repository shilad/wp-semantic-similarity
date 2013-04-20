package edu.macalester.wpsemsim.lucene;

import edu.macalester.wpsemsim.sim.esa.ESAAnalyzer;
import edu.macalester.wpsemsim.sim.esa.ESASimilarity;
import edu.macalester.wpsemsim.utils.ConfigurationFile;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import static edu.macalester.wpsemsim.utils.ConfigurationFile.*;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class WpQuery {
    public static String readStdin() {
        StringBuffer buffer = new StringBuffer();
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            buffer.append(scanner.nextLine());
        }
        return buffer.toString();
    }

    public static class CustomQueryParser extends QueryParser {
        public CustomQueryParser(Version matchVersion, String f, Analyzer a) {
            super(matchVersion, f, a);
        }

        @Override
        protected Query getRangeQuery(final String field, final String part1, final String part2, final boolean begInclusive, final boolean endInclusive) throws ParseException {
            if (Page.FIELD_NINLINKS.equals(field)) {
                return NumericRangeQuery.newIntRange(field, Integer.parseInt(part1), Integer.parseInt(part2), begInclusive, endInclusive);
            }
            return super.getRangeQuery(field, part1, part2, begInclusive, endInclusive);
        }
    }

    public static void main(String args[]) throws IOException, ConfigurationFile.ConfigurationException, ParseException {
        if (args.length < 2) {
            System.err.println(
                    "usage: java " + WpQuery.class.getName() +
                    " path/to/configuration/file indexName numResults < query.txt"
            );
            System.exit(1);
        }
        ConfigurationFile file = new ConfigurationFile(new File(args[0]));
        File luceneParentDir = requireDirectory(file.get("indexes"), "outputDir");
        File luceneDir = new File(luceneParentDir, args[1]);
        IndexHelper helper = new IndexHelper(luceneDir, true);
        String query = readStdin();
        int numResults = args.length == 2 ? Integer.MAX_VALUE : Integer.valueOf(args[2]);

        Map<String, Analyzer> otherAnalyzers = new HashMap<String, Analyzer>();
        if (args[1].equals("esa")) { // HACK!!!
            otherAnalyzers.put(Page.FIELD_TEXT, new StandardAnalyzer(Version.LUCENE_42));
        } else {
            otherAnalyzers.put(Page.FIELD_TEXT, new ESAAnalyzer());
        }

        PerFieldAnalyzerWrapper analyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), otherAnalyzers);
        QueryParser parser = new CustomQueryParser(Version.LUCENE_42, Page.FIELD_TEXT, analyzer);
        TopDocs docs = helper.getSearcher().search(parser.parse(query), numResults);
        DecimalFormat format = new DecimalFormat("#.###");
        for (ScoreDoc doc : docs.scoreDocs) {
            Document d = helper.getReader().document(doc.doc);
            System.out.println(format.format(doc.score) + "\t" + d.get(Page.FIELD_WPID) + "\t" + d.get(Page.FIELD_TITLE));
        }

    }
}
