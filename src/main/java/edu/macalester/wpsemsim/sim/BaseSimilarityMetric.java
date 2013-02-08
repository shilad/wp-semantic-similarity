package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.concepts.ConceptMapper;
import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.normalize.IdentityNormalizer;
import edu.macalester.wpsemsim.normalize.Normalizer;
import edu.macalester.wpsemsim.utils.DocScoreList;
import edu.macalester.wpsemsim.utils.KnownSim;
import gnu.trove.set.TIntSet;
import org.apache.lucene.queryparser.surround.parser.ParseException;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

public abstract class BaseSimilarityMetric implements SimilarityMetric {
    private static Logger LOG = Logger.getLogger(BaseSimilarityMetric.class.getName());

    private ConceptMapper mapper;
    private IndexHelper helper;
    private String name = this.getClass().getSimpleName();

    public BaseSimilarityMetric(ConceptMapper mapper, IndexHelper helper) {
        this.mapper = mapper;
        this.helper = helper;
        if (mapper == null) {
            LOG.warning("ConceptMapper is null. Will not be able to resolve phrases to concepts.");
        }
        if (helper == null) {
            LOG.warning("IndexHelper is null. Will not be able to resolve phrases to concepts.");
        }
    }

    private Normalizer normalizer= new IdentityNormalizer();

    public void trainNormalizer(List<KnownSim> gold) throws IOException{
        if (!normalizer.needsTraining()) return;

        //TODO:Move this code to trainMostSimilar() and trainSimilarity().
        //TODO:Specific implementations (SVMEnsemble) should call super.trainMostSimilar().
        //TODO:Make sure to train using the correct method (similarity() for trainSimilarity() and mostSimilar()
        // for trainMostSimilar()). The reasoning is that a metric may output different scores for each.

        if (!normalizer.isSupervised()){
            trainNormalizer();
        }
        for (KnownSim ks:gold){
            try{
                double sim = rawSimilarity(ks.phrase1,ks.phrase2);
                if (!Double.isNaN(sim) && !Double.isInfinite(sim)){
                    normalizer.observe(sim, ks.similarity);
                }
            } catch (Exception e){}
        }
        normalizer.observationsFinished();
    };

    public void trainNormalizer() throws IOException{
        if (!normalizer.needsTraining()) return;
        if (normalizer.isSupervised()) {
            //TODO: don't hardcode path, get from config file?
            trainNormalizer(KnownSim.read(new File("dat/gold/combined.filtered.txt")));
        }
        int[] wpids = helper.getWpIds();
        for (int i=0; i<wpids.length;i++){
            for (int j=0;j<i;j++){
                normalizer.observe(rawSimilarity(wpids[i],wpids[j]));
            }
        }
        normalizer.observationsFinished();
    }

    public void setNormalizer(Normalizer n){
        normalizer =n;
    }

    @Override
    public void trainSimilarity(List<KnownSim> labeled){
        throw new UnsupportedOperationException();
    }

    @Override
    public void trainMostSimilar(List<KnownSim> labeled, int numResults, TIntSet validIds){
        throw new UnsupportedOperationException();
    }

    @Override
    public double similarity(String phrase1, String phrase2) throws IOException, ParseException {
        return normalizer.normalize(rawSimilarity(phrase1,phrase2));
    }

    public double rawSimilarity(String phrase1, String phrase2) throws IOException, ParseException {
        if (mapper == null) {
            throw new UnsupportedOperationException("Mapper must be non-null to resolve phrases");
        }
        LinkedHashMap<String, Float> concept1s = mapper.map(phrase1, 5);
        LinkedHashMap<String, Float> concept2s= mapper.map(phrase2, 5);

        if (concept1s.isEmpty()) {
            LOG.info("no concepts for phrase " + phrase1);
        }
        if (concept2s.isEmpty()) {
            LOG.info("no concepts for phrase " + phrase2);
        }
        if (concept1s.isEmpty() || concept2s.isEmpty()) {
            return Double.NaN;
        }

        double top1 = Double.NEGATIVE_INFINITY;
        double top2 = Double.NEGATIVE_INFINITY;
        double bestScore = Double.NEGATIVE_INFINITY;
        double bestSim = Double.NaN;
        String bestPair = null;

        for (String article1 : concept1s.keySet()) {
            double score1 = concept1s.get(article1);
//            if (score1 < 0.01) {
//                break;
//            }
            int wpId1 = helper.titleToWpId(article1);
            if (wpId1 < 0) {
                continue;
            }
            for (String article2 : concept2s.keySet()) {
//                if (!article1.equals("Life") || !article2.equals("Stock")) {
//                    continue;
//                }
                double score2 = concept2s.get(article2);
//                if (score2 < 0.01) {
//                    break;
//                }
                int wpId2 = helper.titleToWpId(article2);
                if (wpId2 < 0) {
                    continue;
                }
                double sim = rawSimilarity(wpId1, wpId2);
                if (Double.isInfinite(sim) || Double.isNaN(sim)) {
//                        LOG.info("sim between '" + article1 + "' and '" + article2 + "' is NAN or INF");
                    continue;
                }

                double score = score1 * score2 * sim;

//                System.out.println("for " + phrase1 + ", " + phrase2 + " is " +
//                        "" + article1 + ", " + article2 +
//                        ": " + score1 + ", " + score2 + ", " + sim);
                if (score > bestScore) {
                    bestSim = sim;
                    bestScore = score;
                    bestPair = article1 + ", " + article2;
                }
            }
        }
        System.out.println("for " + phrase1 + ", " + phrase2 + " best is " +
                bestPair + ": " + bestSim);
        return bestSim;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public double similarity(int wpId1, int wpId2) throws IOException{
        return normalizer.normalize(rawSimilarity(wpId1,wpId2));
    }

    public abstract double rawSimilarity(int wpId1, int wpId2) throws IOException;

    @Override
    public abstract DocScoreList mostSimilar(int wpId1, int maxResults, TIntSet possibleWpIds) throws IOException;

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults, TIntSet possibleWpIds) throws IOException {
        if (mapper == null) {
            throw new UnsupportedOperationException("Mapper must be non-null to resolve phrases");
        }
        LinkedHashMap<String, Float> concepts = mapper.map(phrase, 5);
        if (concepts.isEmpty()) {
            LOG.info("no concepts for phrase " + phrase);
            return new DocScoreList(0);
        }

        for (String article : concepts.keySet()) {
            int wpId = helper.titleToWpId(article);
            if (wpId < 0) {
                LOG.info("couldn't find article with title '" + article + "'");
            } else {
//                System.out.println("calling mostSimilar of " + article + " (" + wpId + ")");
                DocScoreList top = mostSimilar(wpId, maxResults, possibleWpIds);
                if (top != null) {
                    return top;
                }
            }
        }
        return null;
    }

    @Override
    public DocScoreList mostSimilar(int wpId1, int maxResults) throws IOException {
        return mostSimilar(wpId1,  maxResults, null);
    }

    @Override
    public DocScoreList mostSimilar(String phrase, int maxResults) throws IOException {
        return mostSimilar(phrase, maxResults, null);
    }

    public IndexHelper getHelper() {
        return this.helper;
    }

}
