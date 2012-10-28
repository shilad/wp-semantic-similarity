package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.lucene.IndexHelper;
import edu.macalester.wpsemsim.utils.DocScoreList;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class CatSimilarity extends BaseSimilarityMetric implements SimilarityMetric {
    private AtomicInteger counter = new AtomicInteger();

    private static final Logger LOG = Logger.getLogger(CatSimilarity.class.getName());

    private CategoryGraph graph;

    public CatSimilarity(CategoryGraph graph) {
        this.graph = graph;
    }

    @Override
    protected void calculatePairwiseSims(int mod, int offset, int maxSimsPerDoc) throws IOException {
        for (int i = offset; i < reader.maxDoc(); i += mod) {
            Document d = reader.document(i);
            if (graph.isCat(d)) {
                continue;
            }
            DocScoreList neighbors = mostSimilar(d, maxSimsPerDoc);
            writeOutput(helper.luceneIdToWpId(i), neighbors.getIds(), neighbors.getScoresAsFloat());
            if (counter.incrementAndGet() % 1000 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
        }
    }

    public double distanceToScore(double distance) {
        return distanceToScore(graph, distance);
    }

    public static double distanceToScore(CategoryGraph graph, double distance) {
        distance = Math.max(distance, graph.minCost);
        return  (Math.log(distance) / Math.log(graph.minCost));
    }

    @Override
    public DocScoreList mostSimilar(int wpId, int maxResults) throws IOException {
        int docId = helper.wpIdToLuceneId(wpId);
        return mostSimilar(reader.document(docId), maxResults);
    }

    private DocScoreList mostSimilar(Document doc, int maxSimsPerDoc) {
        CategoryBfs bfs = new CategoryBfs(graph, doc, maxSimsPerDoc);
        while (bfs.hasMoreResults()) {
            bfs.step();
        }
        DocScoreList results = new DocScoreList(bfs.getPageDistances().size());
        int i = 0;
        for (int pageId: bfs.getPageDistances().keys()) {
            results.set(i++, pageId, distanceToScore(bfs.getPageDistances().get(pageId)));
        }
        return results;
    }


    @Override
    public double similarity(int wpId1, int wpId2) throws IOException {
        if (wpId1 == wpId2) { return distanceToScore(0.0); }     // hack

        int id1 = helper.wpIdToLuceneId(wpId1);
        int id2 = helper.wpIdToLuceneId(wpId2);
        Document d1 = graph.reader.document(id1);
        Document d2 = graph.reader.document(id2);

        CategoryBfs bfs1 = new CategoryBfs(graph, d1, Integer.MAX_VALUE);
        CategoryBfs bfs2 = new CategoryBfs(graph, d2, Integer.MAX_VALUE);
        bfs1.setAddPages(false);
        bfs1.setExploreChildren(false);
        bfs2.setAddPages(false);
        bfs2.setExploreChildren(false);

        double shortestDistance = Double.POSITIVE_INFINITY;
        double maxDist1 = 0;
        double maxDist2 = 0;

        while ((bfs1.hasMoreResults() || bfs2.hasMoreResults())
        &&     (maxDist1 + maxDist2 < shortestDistance)) {
            // Search from d1
            while (bfs1.hasMoreResults() && (maxDist1 <= maxDist2 || !bfs2.hasMoreResults())) {
                CategoryBfs.BfsFinished finished = bfs1.step();
                for (int catId : finished.cats.keys()) {
                    if (bfs2.hasCategoryDistance(catId)) {
                        double d = bfs1.getCategoryDistance(catId)
                                + bfs2.getCategoryDistance(catId)
                                - graph.catCosts[catId];    // counted twice
                        shortestDistance = Math.min(d, shortestDistance);
                    }
                }
                maxDist1 = Math.max(maxDist1, finished.maxCatDistance());
            }

            // Search from d2
            while (bfs2.hasMoreResults() && (maxDist2 <= maxDist1 || !bfs1.hasMoreResults())) {
                CategoryBfs.BfsFinished finished = bfs2.step();
                for (int catId : finished.cats.keys()) {
                    if (bfs1.hasCategoryDistance(catId)) {
                        double d = bfs1.getCategoryDistance(catId) +
                                bfs2.getCategoryDistance(catId) + 0
                                - graph.catCosts[catId];    // counted twice;
                        shortestDistance = Math.min(d, shortestDistance);
                    }
                }
                maxDist2 = Math.max(maxDist2, finished.maxCatDistance());
            }
        }

        return distanceToScore(shortestDistance);
    }


    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " lucene-cat-index-dir output-file num-results [num-threads]");

        }
        IndexHelper helper = new IndexHelper(new File(args[0]), true);
        CategoryGraph g = new CategoryGraph(helper);
        g.init();
        CatSimilarity cs = new CatSimilarity(g);
        cs.openIndex(helper);
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        cs.openOutput(new File(args[1]));
        cs.calculatePairwiseSims(cores, Integer.valueOf(args[2]));
        cs.closeOutput();
    }
}
