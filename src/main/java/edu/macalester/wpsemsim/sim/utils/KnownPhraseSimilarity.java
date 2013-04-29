package edu.macalester.wpsemsim.sim.utils;

import edu.macalester.wpsemsim.matrix.DenseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrix;
import edu.macalester.wpsemsim.matrix.SparseMatrixRow;
import edu.macalester.wpsemsim.normalize.Normalizer;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class KnownPhraseSimilarity implements SimilarityMetric {
    private static final Logger LOG = Logger.getLogger(KnownPhraseSimilarity.class.getName());

    private Map<String, Integer> phraseToId = new HashMap<String, Integer>();
    private List<String> idToPhrase = new ArrayList<String>();
    private SparseMatrix mostSimilarMatrix;
    private DenseMatrix similarityMatrix;
    private File directory;
    private int numOpenPages;
    private int maxPageSize;

    public KnownPhraseSimilarity(File directory) throws IOException {
        this(directory, 4, 250 * 1024 * 1024);  // 4 * 250MB memory mapped pages
    }

    public KnownPhraseSimilarity(File directory, int numOpenPages, int maxPageSize) throws IOException {
        this.numOpenPages = numOpenPages;
        this.maxPageSize = maxPageSize;
        read(directory);
    }

    @Override
    public String getName() {
        return "phrase-similarity-" + directory;
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        if (!phraseToId.containsKey(phrase1)) {
            LOG.warning("unknown phrase: " + StringEscapeUtils.escapeJava(phrase1));
            return 0.0;
        }
        if (!phraseToId.containsKey(phrase2)) {
            LOG.warning("unknown phrase: " + StringEscapeUtils.escapeJava(phrase2));
            return 0.0;
        }
        int id1 = phraseToId.get(phrase1);
        int id2 = phraseToId.get(phrase2);
        return similarityMatrix.getRow(id1).getColValue(id2);
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException {
        if (!phraseToId.containsKey(phrase)) {
            LOG.warning("unknown phrase: " + StringEscapeUtils.escapeJava(phrase));
            return new DocScoreList(0);
        }
        int id = phraseToId.get(phrase);
        SparseMatrixRow row = mostSimilarMatrix.getRow(id);
        DocScoreList top = new DocScoreList(row.getNumCols());
        for (int i = 0; i < row.getNumCols(); i++) {
            top.set(i, row.getColIndex(i), row.getColValue(i));
        }
        return top;
    }

    public List<PhraseScore> mostSimilarPhrases(String phrase, int maxResults) throws IOException {
        DocScoreList dsl = mostSimilar(phrase, maxResults);
        List<PhraseScore> phrases = new ArrayList<PhraseScore>();
        for (DocScore ds : dsl) {
        }
        return phrases;
    }

    public static String normalize(String phrase) {
        return PhraseAnalyzer.normalize(phrase);
    }

    @Override
    public void read(File dir) throws IOException {
        this.directory = dir;

        // read phrase mapping
        phraseToId.clear();
        idToPhrase.clear();
        for (String line : FileUtils.readLines(new File(dir, "phrases.tsv"))) {
            String tokens[] = line.split("\t");
            if (tokens.length == 4) {
                int id = Integer.valueOf(tokens[0]);
                int wpId = Integer.valueOf(tokens[1]);
                String phrase = tokens[2].trim();
                String article = tokens[3].trim();
                phraseToId.put(phrase, id);
                while (idToPhrase.size() < id) idToPhrase.add("unknown");
                idToPhrase.add(phrase);
                assert(idToPhrase.size() == id+1);
            } else {
                LOG.warning(
                        "invalid line in '" + dir + "/phrases.tsv': '" +
                        StringEscapeUtils.escapeJava(line)
                );
            }
        }

        mostSimilarMatrix = new SparseMatrix(new File(dir, "mostSimilar.matrix"), numOpenPages, maxPageSize);
        similarityMatrix = new DenseMatrix(new File(dir, "similarity.matrix"), numOpenPages, maxPageSize);
    }


    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        throw new UnsupportedOperationException();
    }
    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException {
        throw new UnsupportedOperationException();
    }
    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException {
        throw new UnsupportedOperationException();
    }
    @Override
    public void trainSimilarity(List<KnownSim> labeled) {
        throw new UnsupportedOperationException();
    }
    @Override
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds) {
        throw new UnsupportedOperationException();
    }
    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException {
        throw new UnsupportedOperationException("possibleWpIds not supported");
    }
    @Override
    public void write(File directory) throws IOException {
        throw new UnsupportedOperationException();
    }

    public static class PhraseScore {
        public String phrase;
        public double score;
    }
}
