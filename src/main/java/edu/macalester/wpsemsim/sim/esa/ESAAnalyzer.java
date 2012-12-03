package edu.macalester.wpsemsim.sim.esa;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.wikipedia.WikipediaTokenizer;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ESAAnalyzer extends Analyzer {

    /** An unmodifiable set containing some common English words that are not usually useful
     for searching.*/
    public final CharArraySet ENGLISH_STOP_WORDS_SET;

    public ESAAnalyzer() {
        InputStream is = ESAAnalyzer.class.getResourceAsStream("/stopwords.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        List<String> stopWords = new ArrayList<String>();
        try {
            while (true) {
                String line = br.readLine();
                if (line == null) break;
                stopWords.add(line.trim());
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        ENGLISH_STOP_WORDS_SET = StopFilter.makeStopSet(Version.LUCENE_40, stopWords);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer wt = new WikipediaTokenizer(reader);
        TokenFilter filter = new LowerCaseFilter(Version.LUCENE_40, wt);
        filter = new StopFilter(Version.LUCENE_40, filter, ENGLISH_STOP_WORDS_SET);
        filter = new SnowballFilter(filter, "English");
        return new TokenStreamComponents(wt, filter);

    }
}