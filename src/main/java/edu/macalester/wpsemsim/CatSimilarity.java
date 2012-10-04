package edu.macalester.wpsemsim;

import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntHashSet;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CatSimilarity extends SimilarityMetric {
    private AtomicInteger counter = new AtomicInteger();

    private static final Logger LOG = Logger.getLogger(CatSimilarity.class.getName());

    private Map<String,Integer> catIndexes;
    private Set<Integer> topLevelCategories;

    private double[] catPageRanks;
    private int[][] catParents;
    private int[][] catPages;
    private int[][] catChildren;
    private String[] cats;

    private void loadCategories() throws IOException {
        this.catIndexes = new HashMap<String, Integer>();
        List<String> catList = new ArrayList<String>();
        for (int i=0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            if (isCat(d)) {
                String cat = d.get("title");
                if (!catIndexes.containsKey(cat)) {
                    catIndexes.put(cat, catIndexes.size());
                    catList.add(cat);
                }
            }
            for (IndexableField f : d.getFields("cats")) {
                String cat = f.stringValue();
                if (!catIndexes.containsKey(cat)) {
                    catIndexes.put(cat, catIndexes.size());
                    catList.add(cat);
                }
            }
        }
        cats = catList.toArray(new String[0]);
    }

    private void buildGraph() throws IOException {
        this.catPages = new int[catIndexes.size()][];
        this.catParents = new int[catIndexes.size()][];
        this.catChildren = new int[catIndexes.size()][];
        this.catPageRanks = new double[catIndexes.size()];

        Arrays.fill(catPages, new int[0]);
        Arrays.fill(catParents, new int[0]);
        Arrays.fill(catChildren, new int[0]);

        // count reverse edges
        int numCatChildren[] = new int[catIndexes.size()];
        int numCatPages[] = new int[catIndexes.size()];
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            int catId1 = -1;
            if (d.getField("ns").numericValue().intValue() == 14) {
                catId1 = catIndexes.get(d.get("title"));
            }
            for (IndexableField f : d.getFields("cats")) {
                int catId2 = catIndexes.get(f.stringValue());
                if (catId1 >= 0) {
                    numCatChildren[catId2]++;
                } else {
                    numCatPages[catId2]++;
                }
            }
        }

        // allocate space
        for (int i = 0; i < catIndexes.size(); i++) {
            catPages[i] = new int[numCatPages[i]];
            catChildren[i] = new int[numCatChildren[i]];
        }

        // fill it
        Arrays.fill(numCatChildren, 0);
        Arrays.fill(numCatPages, 0);
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            IndexableField[] catFields = d.getFields("cats");
            int catId1 = -1;
            if (isCat(d)) {
                catId1 = catIndexes.get(d.get("title"));
                catParents[catId1] = new int[catFields.length];
            }
            for (int j = 0; j < catFields.length; j++) {
                int catId2 = catIndexes.get(catFields[j].stringValue());
                if (catId1 >= 0) {
                    catChildren[catId2][numCatChildren[catId2]++] = catId1;
                    catParents[catId1][j] = catId2;
                } else {
                    catPages[catId2][numCatPages[catId2]++] = catId1;
                }
            }
        }
    }

    public void computePageRanks() {
        LOG.info("computing category page ranks...");

        // initialize page rank
        long sumCredits = catPages.length;    // each category gets 1 credit to start
        for (int i = 0; i < catPages.length; i++) {
            sumCredits += catPages[i].length; // one more credit per page that references it.
        }
        for (int i = 0; i < catPages.length; i++) {
            catPageRanks[i] = (1.0 + catPages[i].length) / sumCredits;
        }

        for (int i = 0; i < 20; i++) {
            LOG.log(Level.INFO, "performing page ranks iteration {0}.", i);
            double error = onePageRankIteration();
            LOG.log(Level.INFO, "Error for iteration is {0}.", error);
            if (error == 0) {
                break;
            }
        }
        Integer sortedIndexes[] = new Integer[catPageRanks.length];
        for (int i = 0; i < catParents.length; i++) {
            catPageRanks[i] = 1.0/-Math.log(catPageRanks[i]);
            sortedIndexes[i] = i;
        }
        LOG.info("finished computing page ranks...");
        Arrays.sort(sortedIndexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer i1, Integer i2) {
                Double pr1 = catPageRanks[i1];
                Double pr2 = catPageRanks[i2];
                return -1 * pr1.compareTo(pr2);
            }
        });

        StringBuffer b = new StringBuffer("Top page ranks:");
        for (int i = 0; i < 10; i++) {
            int j = sortedIndexes[i];
            b.append("" + i + ". " + cats[j] + "=" + catPageRanks[j]);
            b.append(", ");
        }
        LOG.info("Top page ranks: " + b.toString());
    }

    private static final double DAMPING_FACTOR = 0.85;
    protected double onePageRankIteration() {
        double nextRanks [] = new double[catPageRanks.length];
        Arrays.fill(nextRanks, (1.0 - DAMPING_FACTOR) / catPageRanks.length);
        for (int i = 0; i < catParents.length; i++) {
            int d = catParents[i].length;   // degree
            double pr = catPageRanks[i];    // current page-rank
            for (int j : catParents[i]) {
                nextRanks[j] += DAMPING_FACTOR * pr / d;
            }
        }
        double diff = 0.0;
        for (int i = 0; i < catParents.length; i++) {
            diff += Math.abs(catPageRanks[i] - nextRanks[i]);
        }
        catPageRanks = nextRanks;
        return diff;
    }


    public int getCategoryIndex(String cat) {
        if (catIndexes.containsKey(cat)) {
            return catIndexes.get(cat);
        } else {
            return -1;
        }
    }

    private void calculateTopLevelCategories() {
        LOG.info("marking top level categories off-limits.");
        int numSecondLevel = 0;
        topLevelCategories = new HashSet<Integer>();
        for (String name : TOP_LEVEL_CATS) {
            int index = getCategoryIndex(name);
            if (index >= 0) {
                topLevelCategories.add(index);
                for (int ci : catChildren[index]) {
                    topLevelCategories.add(ci);
                    numSecondLevel++;
                }
            }
        }
        LOG.log(Level.INFO, "marked {0} top-level and {1} second-level categories.",
                new Object[] {TOP_LEVEL_CATS.length, numSecondLevel} );
    }

    @Override
    protected void calculatePairwiseSims(int mod, int offset, int maxSimsPerDoc) throws IOException {
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            LinkedHashMap<Integer, Double> sims = getDocumentSimilarities(d, maxSimsPerDoc);
            int simPageIds[] = new int[sims.size()];
            float simPageScores[] = new float[sims.size()];
            int j = 0;
            for (int id : sims.keySet()) {
                simPageIds[j] = id;
                simPageScores[j] = sims.get(id).floatValue();
                j++;
            }
            writeOutput(getWikipediaId(i), simPageIds, simPageScores);
            if (counter.incrementAndGet() % 100 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
        }
    }

    private LinkedHashMap<Integer, Double> getDocumentSimilarities(Document doc, int maxSimsPerDoc) {
        int pageId = doc.getField("id").numericValue().intValue();
        TIntDoubleHashMap catDistances = new TIntDoubleHashMap();
        LinkedHashMap<Integer, Double> pageDistances = new LinkedHashMap<Integer, Double>();
        pageDistances.put(pageId, 0.000000);

        TIntHashSet closedCats = new TIntHashSet();
        PriorityQueue<CategoryDistance> openCats = new PriorityQueue<CategoryDistance>();
        for (IndexableField f : doc.getFields("cats")) {
            int ci = getCategoryIndex(f.stringValue());
            openCats.add(new CategoryDistance(ci, catPageRanks[ci], (byte)+1));
        }

        while (openCats.size() > 0 && pageDistances.size() < maxSimsPerDoc) {
            CategoryDistance cs = openCats.poll();

            // already processed better match
            if (closedCats.contains(cs.getCatIndex())) {
                continue;
            }
            closedCats.add(cs.getCatIndex());

            // add directly linked pages
            for (int i : catPages[cs.getCatIndex()]) {
                if (!pageDistances.containsKey(i) || pageDistances.get(i) > cs.getDistance()) {
                    pageDistances.put(i, cs.getDistance());
                }
                if (pageDistances.size() >= maxSimsPerDoc) {
                    break;  // may be an issue for huge categories
                }
            }

            // next steps downwards
            for (int i : catChildren[cs.getCatIndex()]) {
                if (!closedCats.contains(i) && !catDistances.containsKey(i)) {
                    double d = cs.getDistance() + catPageRanks[cs.getCatIndex()];
                    catDistances.put(i, d);
                    openCats.add(new CategoryDistance(i, d, (byte)-1));
                }
            }

            // next steps upwards (if still possible)
            if (cs.getDirection() == +1) {
                for (int i : catParents[cs.getCatIndex()]) {
                    double d = cs.getDistance() + catPageRanks[cs.getCatIndex()];
                    if (!closedCats.contains(i) && (!catDistances.containsKey(i) || catDistances.get(i) > d)) {
                        catDistances.put(i, d);
                        openCats.add(new CategoryDistance(i, d, (byte)-1));
                    }
                }
            }

        }

        return pageDistances;
    }

    private boolean isCat(Document d) {
        return (d.getField("ns").numericValue().intValue() == 14);
    }


    public static String [] TOP_LEVEL_CATS = {
            "Agriculture", "Applied Sciences", "Arts", "Belief", "Business", "Chronology", "Computers",
            "Culture", "Education", "Environment", "Geography", "Health", "History", "Humanities",
            "Language", "Law", "Life", "Mathematics", "Nature", "People", "Politics", "Science", "Society",

            "Concepts", "Life", "Matter", "Society",

    };
    /**
     *
     * @author shilad
     */
    private static final class CategoryDistance implements Comparable<CategoryDistance> {
        private int catIndex;
        private double distance;
        private byte direction; // +1 or -1

        public CategoryDistance(int catIndex, double distance, byte direction) {
            this.catIndex = catIndex;
            this.distance = distance;
            this.direction = direction;
        }

        public final int getCatIndex() {
            return catIndex;
        }

        public final byte getDirection() {
            return direction;
        }

        public final double getDistance() {
            return distance;
        }

        public final int compareTo(CategoryDistance t) {
            if (distance < t.distance)
                return -1;
            else if (distance > t.distance)
                return 1;
            else
                return catIndex * direction - t.catIndex * t.direction;
        }
    }

    public static void main(String args[]) throws IOException, InterruptedException, CompressorException {
        if (args.length != 3 && args.length != 4) {
            System.err.println("usage: java " +
                    TextSimilarity.class.getName() +
                    " lucene-cat-index-dir output-file num-results [num-threads]");

        }
        CatSimilarity cs = new CatSimilarity();
        cs.openIndex(new File(args[0]), true);
        cs.loadCategories();
        cs.buildGraph();
        cs.computePageRanks();
        cs.calculateTopLevelCategories();
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        cs.openOutput(new File(args[1]));
        cs.calculatePairwiseSims(cores, Integer.valueOf(args[2]));
    }
}
