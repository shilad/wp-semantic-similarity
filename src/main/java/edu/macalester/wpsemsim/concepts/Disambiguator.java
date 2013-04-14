package edu.macalester.wpsemsim.concepts;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.sim.SimScore;
import edu.macalester.wpsemsim.sim.SimilarityMetric;
import edu.macalester.wpsemsim.utils.DocScore;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

/**
 * Resolves a phrase to a wikipedia page given another "hint" phrase that may have similar meaning.
 */
public class Disambiguator {
    private static Logger LOG = Logger.getLogger(Disambiguator.class.getName());

    /**
     * A mapping between a phrase and a Wikipedia page.
     * There can also be a hint (a possibly related phrase) with an additional mapping.
     */
    public static class Match {
        public String phrase;
        public String hint;
        public int phraseWpId = -1;
        public int hintWpId = -1;
        public String phraseWpName;
        public String hintWpName;

        @Override
        public String toString() {
            return "Match{" +
                    "phrase='" + phrase + '\'' +
                    ", hint='" + hint + '\'' +
                    ", phraseWpId=" + phraseWpId +
                    ", hintWpId=" + hintWpId +
                    ", phraseWpName='" + phraseWpName + '\'' +
                    ", hintWpName='" + hintWpName + '\'' +
                    '}';
        }

        public boolean hasPhraseMatch() { return phraseWpId >= 0; }
        public boolean hasHintMatch() { return hintWpId >= 0; }
    }

    private SimilarityMetric metric;
    private ConceptMapper mapper;
    private IndexHelper helper;
    private int maxConcepts;

    public Disambiguator(ConceptMapper mapper, SimilarityMetric metric, IndexHelper helper, int maxConcepts) {
        this.mapper = mapper;
        this.metric = metric;
        this.helper = helper;
        this.maxConcepts = maxConcepts;
    }

    public Match disambiguateMostSimilar(KnownSim ks, int numResults, TIntSet validIds) throws IOException {
        if (ks.wpId1 >= 0 && ks.wpId2 >= 0) {
            return knownSimToMatch(ks);
        } else {
            return disambiguateMostSimilar(ks.phrase1, ks.phrase2, numResults, validIds);
        }
    }

    private Match knownSimToMatch(KnownSim ks) {
        Match m = new Match();
        m.phrase = ks.phrase1;
        m.hint = ks.phrase2;
        m.phraseWpId = ks.wpId1;
        m.hintWpId = ks.wpId2;
        m.phraseWpName = helper.wpIdToTitle(ks.wpId1);
        m.hintWpName = helper.wpIdToTitle(ks.wpId2);
        if (m.phraseWpName == null || m.hintWpName == null) {
            return null;
        } else {
            return m;
        }
    }

    public Match disambiguateMostSimilar(String phrase, String hint, int numResults, TIntSet validIds) throws IOException {
        return disambiguate(phrase, hint, new MostSimilarScorer(numResults, validIds));
    }

    public Match disambiguateSimilarity(KnownSim ks) throws IOException {
        if (ks.wpId1 >= 0 && ks.wpId2 >= 0) {
            return knownSimToMatch(ks);
        } else {
            return disambiguateSimilarity(ks.phrase1, ks.phrase2);
        }
    }

    public Match disambiguateSimilarity(String phrase, String hint) throws IOException {
        return disambiguate(phrase, hint, new SimilarityScorer());
    }

    protected Match disambiguate(String phrase, String hint, Scorer scorer) throws IOException {
        Match match = new Match();
        match.phrase = phrase;
        match.hint = hint;

        LinkedHashMap<String, Float> concept1s = mapper.map(phrase, maxConcepts);
        if (concept1s.isEmpty()) {
            LOG.info("no concepts for phrase " + phrase);
        }

        if (hint == null) {
            for (String article1 : concept1s.keySet()) {
                int wpId = helper.titleToWpId(article1);
                if (wpId > 0) {
                    match.phraseWpId = wpId;
                    match.phraseWpName = article1;
                    return match;
                }
            }
            return null;
        }

        LinkedHashMap<String, Float> concept2s = mapper.map(hint, maxConcepts);
        if (concept2s.isEmpty()) {
            LOG.info("no concepts for phrase " + hint);
        }

        // hopeless!
        if (concept1s.isEmpty()) {
            return null;
        }

        double bestScore = Double.NEGATIVE_INFINITY;

        for (String article1 : concept1s.keySet()) {
            double score1 = concept1s.get(article1);
            int wpId1 = helper.titleToWpId(article1);
            if (wpId1 < 0) {
                continue;
            }
            // if no hints match, fall back on the most popular phrase match
            if (!match.hasPhraseMatch()) {
                match.phraseWpId = wpId1;
                match.phraseWpName = article1;
            }
            for (String article2 : concept2s.keySet()) {
                double score2 = concept2s.get(article2);
                int wpId2 = helper.titleToWpId(article2);
                if (wpId2 < 0) {
                    continue;
                }
                SimScore ss = scorer.score(wpId1, wpId2);
                if (ss == null || !ss.hasValue()) {
                    continue;
                }

                double score = score1 * score2 * ss.sim;
                if (score > bestScore) {
                    match.phraseWpId = wpId1;
                    match.phraseWpName = article1;
                    match.hintWpId = wpId2;
                    match.hintWpName = article2;
                    bestScore = score;
                }
            }
        }

        if (match.hasPhraseMatch()) {
            return match;
        } else {
            return null;
        }
    }

    protected interface Scorer {
        SimScore score(int wpId1, int wpId2) throws IOException;
    }

    protected class MostSimilarScorer implements Scorer {
        private int numResults;
        private TIntSet validIds;

        protected MostSimilarScorer(int numResults, TIntSet validIds) {
            this.numResults = numResults;
            this.validIds = validIds;
        }

        @Override
        public SimScore score(int wpId1, int wpId2) throws IOException {
            DocScoreList results = metric.mostSimilar(wpId1, numResults, validIds);
            if (results == null || results.numDocs() == 0) {
                return null;
            }
            for (int i = 0; i < results.numDocs(); i++) {
                DocScore ds = results.get(i);
                if (ds.getId() == wpId2) {
                    return new SimScore(0, results, i);
                }
            }
            return new SimScore(0, results, -1);
        }
    }

    protected class SimilarityScorer implements Scorer {
        @Override
        public SimScore score(int wpId1, int wpId2) throws IOException {
            return new SimScore(0, metric.similarity(wpId1, wpId2));
        }
    }
}
