package edu.macalester.wpsemsim.sim;

import edu.macalester.wpsemsim.utils.DocScoreList;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.document.Document;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class CatSimilarity extends BaseSimilarityMetric {
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
            DocScoreList neighbors = getClosestDocs(d, maxSimsPerDoc);
            writeOutput(helper.luceneIdToWpId(i), neighbors.getIds(), neighbors.getScoresAsFloat());
            if (counter.incrementAndGet() % 1000 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
        }
    }

    private DocScoreList getClosestDocs(Document doc, int maxSimsPerDoc) {
        CategoryBfs bfs = new CategoryBfs(graph, doc, maxSimsPerDoc);
        while (bfs.hasMoreResults()) {
            bfs.bfsIteration();
        }
        DocScoreList results = new DocScoreList(bfs.getPageDistances().size());
        int i = 0;
        for (int pageId: bfs.getPageDistances().keys()) {
            results.set(i++, pageId, bfs.getPageDistances().get(pageId));
        }
        return results;
    }


    private double similarity(int wpId1, int wpId2) throws IOException {
        int id1 = helper.wpIdToLuceneId(wpId1);
        int id2 = helper.wpIdToLuceneId(wpId2);
        Document d1 = graph.reader.document(id1);
        Document d2 = graph.reader.document(id2);

        CategoryBfs bfs1 = new CategoryBfs(graph, d1, Integer.MAX_VALUE);
        CategoryBfs bfs2 = new CategoryBfs(graph, d2, Integer.MAX_VALUE);

        double distance = 0;
        double maxDist1 = 0;
        double maxDist2 = 0;
        while (bfs1.hasMoreResults() || bfs2.hasMoreResults()) {
            while (maxDist1 < maxDist2 && bfs1.hasMoreResults()) {
                CategoryBfs.BfsDiscoveries discoveries = bfs1.bfsIteration();
                maxDist1 = discoveries.maxCatDistance();
                for (int catId : discoveries.cats.keys()) {
                    if (bfs2.hasCategoryDistance(catId)) {

                    }

                }
            }

            while (maxDist2 < maxDist1 && bfs2.hasMoreResults()) {
            }
        }

        return -1;
    }


    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " lucene-cat-index-dir output-file num-results [num-threads]");

        }
        CategoryGraph g = new CategoryGraph(new File(args[0]));
        g.init();
        CatSimilarity cs = new CatSimilarity(g);
        cs.openIndex(new File(args[0]), true);
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        cs.openOutput(new File(args[1]));
        cs.calculatePairwiseSims(cores, Integer.valueOf(args[2]));
        cs.closeOutput();
    }
}
