package edu.macalester.wpsemsim.sim;

import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.set.hash.TIntHashSet;
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

    private double[] catCosts;  // the cost of travelling through each category
    private int[][] catParents;
    private int[][] catPages;
    private int[][] catChildren;
    private String[] cats;
    private double minCost = -1;

    private void loadCategories() throws IOException {
        LOG.info("loading categories...");
        this.catIndexes = new HashMap<String, Integer>();
        List<String> catList = new ArrayList<String>();
        for (int i=0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            if (isCat(d)) {
                String cat = cleanTitle(d);
                if (!catIndexes.containsKey(cat)) {
                    catIndexes.put(cat, catIndexes.size());
                    catList.add(cat);
                }
            }
            for (IndexableField f : d.getFields("cats")) {
                String cat = cleanTitle(f.stringValue());
                if (!catIndexes.containsKey(cat)) {
                    catIndexes.put(cat, catIndexes.size());
                    catList.add(cat);
                }
            }
        }
        cats = catList.toArray(new String[0]);
        LOG.info("finished loading " + cats.length + " categories");
    }

    private static final String cleanTitle(Document d) {
        return cleanTitle(d.get("title"));
    }

    private static final String cleanTitle(String title) {
        String s = title.replaceAll("_", " ").toLowerCase();
        if (s.startsWith("category:")) {
            s = s.substring("category:".length());
        }
        return s;
    }

    private void buildGraph() throws IOException {
        LOG.info("building category graph");
        this.catPages = new int[catIndexes.size()][];
        this.catParents = new int[catIndexes.size()][];
        this.catChildren = new int[catIndexes.size()][];
        this.catCosts = new double[catIndexes.size()];

        Arrays.fill(catPages, new int[0]);
        Arrays.fill(catParents, new int[0]);
        Arrays.fill(catChildren, new int[0]);

        // count reverse edges
        int totalEdges = 0;
        int numCatChildren[] = new int[catIndexes.size()];
        int numCatPages[] = new int[catIndexes.size()];
        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            int catId1 = -1;
            if (isCat(d)) {
                catId1 = getCategoryIndex(d);
            }
            for (IndexableField f : d.getFields("cats")) {
                int catId2 = getCategoryIndex(f.stringValue());
                if (catId1 >= 0) {
                    numCatChildren[catId2]++;
                } else {
                    numCatPages[catId2]++;
                }
                totalEdges++;
            }
        }

        // allocate space
        for (int i = 0; i < catIndexes.size(); i++) {
            catPages[i] = new int[numCatPages[i]];
            catChildren[i] = new int[numCatChildren[i]];
        }

        // fill it

        for (int i = 0; i < reader.maxDoc(); i++) {
            Document d = reader.document(i);
            IndexableField[] catFields = d.getFields("cats");
            int pageId = Integer.valueOf(d.getField("id").stringValue());
            int catId1 = -1;
            if (isCat(d)) {
                catId1 = getCategoryIndex(d);
                catParents[catId1] = new int[catFields.length];
            }
            for (int j = 0; j < catFields.length; j++) {
                int catId2 = getCategoryIndex(catFields[j].stringValue());
                if (catId1 >= 0) {
                    catChildren[catId2][--numCatChildren[catId2]] = catId1;
                    catParents[catId1][j] = catId2;
                } else {
                    catPages[catId2][--numCatPages[catId2]] = pageId;
                }
            }
        }
        for (int n : numCatChildren) { assert(n == 0); }
        for (int n : numCatPages) { assert(n == 0); }
        LOG.info("loaded " + totalEdges + " edges in category graph");
//        for (int i = 0; i < catChildren.length; i+= 10000) {
//            System.err.println("info for cat " + i + " " + cats[i]);
//            System.err.println("\tparents:" + Arrays.toString(catParents[i]));
//            System.err.println("\tchildren:" + Arrays.toString(catChildren[i]));
//            System.err.println("\tpages:" + Arrays.toString(catPages[i]));
//        }
    }

    public void computePageRanks() {
        LOG.info("computing category page ranks...");

        // initialize page rank
        long sumCredits = catPages.length;    // each category gets 1 credit to start
        for (int i = 0; i < catPages.length; i++) {
            sumCredits += catPages[i].length; // one more credit per page that references it.
        }
        for (int i = 0; i < catPages.length; i++) {
            catCosts[i] = (1.0 + catPages[i].length) / sumCredits;
        }

        for (int i = 0; i < 20; i++) {
            LOG.log(Level.INFO, "performing page ranks iteration {0}.", i);
            double error = onePageRankIteration();
            LOG.log(Level.INFO, "Error for iteration is {0}.", error);
            if (error == 0) {
                break;
            }
        }
        Integer sortedIndexes[] = new Integer[catCosts.length];
        for (int i = 0; i < catParents.length; i++) {
            catCosts[i] = 1.0/-Math.log(catCosts[i]);
            sortedIndexes[i] = i;
        }
        LOG.info("finished computing page ranks...");
        Arrays.sort(sortedIndexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer i1, Integer i2) {
                Double pr1 = catCosts[i1];
                Double pr2 = catCosts[i2];
                return -1 * pr1.compareTo(pr2);
            }
        });

        StringBuffer b = new StringBuffer();
        for (int i = 0; i < 20; i++) {
            int j = sortedIndexes[i];
            b.append("" + i + ". " + cats[j] + "=" + catCosts[j]);
            b.append(", ");
        }
        LOG.info("Min cat cost: " + minCost);
        LOG.info("Top cat costs: " + b.toString());
        minCost = catCosts[sortedIndexes[sortedIndexes.length - 1]];

//        double maxLog = Math.log(sortedIndexes.length+1);
//        for (int i = 0; i < sortedIndexes.length; i++) {
//            int j = sortedIndexes[i];
//            catCosts[j] = 1 - (1 + Math.log(i + 1)) / (1 + maxLog);
//            System.out.println("" + i + ". " + cats[j] + "=" + catCosts[j]);
//        }
    }

    private static final double DAMPING_FACTOR = 0.85;
    protected double onePageRankIteration() {
        double nextRanks [] = new double[catCosts.length];
        Arrays.fill(nextRanks, (1.0 - DAMPING_FACTOR) / catCosts.length);
        for (int i = 0; i < catParents.length; i++) {
            int d = catParents[i].length;   // degree
            double pr = catCosts[i];    // current page-rank
            for (int j : catParents[i]) {
                nextRanks[j] += DAMPING_FACTOR * pr / d;
            }
        }
        double diff = 0.0;
        for (int i = 0; i < catParents.length; i++) {
            diff += Math.abs(catCosts[i] - nextRanks[i]);
        }
        catCosts = nextRanks;
        return diff;
    }

    public int getCategoryIndex(Document d) {
        return getCategoryIndex(d.get("title"));
    }

    public int getCategoryIndex(String cat) {
        cat = cleanTitle(cat);
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
//                    topLevelCategories.add(ci);
                    numSecondLevel++;
                }
            }
        }
        LOG.log(Level.INFO, "marked {0} top-level and {1} second-level categories.",
                new Object[] {TOP_LEVEL_CATS.length, numSecondLevel} );
    }

    @Override
    protected void calculatePairwiseSims(int mod, int offset, int maxSimsPerDoc) throws IOException {
        for (int i = offset; i < reader.maxDoc(); i += mod) {
            Document d = reader.document(i);
            if (isCat(d)) {
                continue;
            }
            LinkedHashMap<Integer, Double> neighbors = getClosestDocs(d, maxSimsPerDoc);
            int simPageIds[] = new int[neighbors.size()];
            float simPageScores[] = new float[neighbors.size()];
            int j = 0;
            for (int id : neighbors.keySet()) {
                simPageIds[j] = id;
                double dist = neighbors.get(id).floatValue();
                simPageScores[j] = (float) (Math.log(dist) / Math.log(minCost));
                j++;
            }
            writeOutput(helper.luceneIdToWpId(i), simPageIds, simPageScores);
            if (counter.incrementAndGet() % 1000 == 0) {
                System.err.println("" + new Date() + ": finding matches for doc " + counter.get());
            }
        }
    }

    private LinkedHashMap<Integer, Double> getClosestDocs(Document doc, int maxSimsPerDoc) {
        int pageId = Integer.valueOf(doc.getField("id").stringValue());
        TIntDoubleHashMap catDistances = new TIntDoubleHashMap();
        LinkedHashMap<Integer, Double> pageDistances = new LinkedHashMap<Integer, Double>();
        pageDistances.put(pageId, 0.000000);

//        LOG.info("getting sims for " + doc.get("title"));
        TIntHashSet closedCats = new TIntHashSet();
        PriorityQueue<CategoryDistance> openCats = new PriorityQueue<CategoryDistance>();
        for (IndexableField f : doc.getFields("cats")) {
            int ci = getCategoryIndex(f.stringValue());
            openCats.add(new CategoryDistance(ci, cats[ci], catCosts[ci], (byte)+1));
//            LOG.info("adding category " + f.stringValue());
        }

        while (openCats.size() > 0 && pageDistances.size() < maxSimsPerDoc) {
            CategoryDistance cs = openCats.poll();
//            LOG.info("next cat is " + cs.toString());

            // already processed better match
            if (closedCats.contains(cs.getCatIndex())) {
//                LOG.info("cat is closed");
                continue;
            }
            closedCats.add(cs.getCatIndex());

            // add directly linked pages
//            LOG.info("considering pages...");
            for (int i : catPages[cs.getCatIndex()]) {
//                String title = helper.wpIdToTitle(i);
//                LOG.info("considering page " + i + ": " + title);
                if (!pageDistances.containsKey(i) || pageDistances.get(i) > cs.getDistance()) {
//                    LOG.info("adding page " + title + " with distance " + cs.getDistance());
                    pageDistances.put(i, cs.getDistance());
                }
                if (pageDistances.size() >= maxSimsPerDoc) {
                    break;  // may be an issue for huge categories
                }
            }

            // next steps downwards
//            LOG.info("considering downward cats...");
            for (int i : catChildren[cs.getCatIndex()]) {
//                LOG.info("considering cat " + cats[i]);
                if (!closedCats.contains(i) && !catDistances.containsKey(i)) {
                    double d = cs.getDistance() + catCosts[cs.getCatIndex()];
                    catDistances.put(i, d);
                    openCats.add(new CategoryDistance(i, cats[i], d, (byte)-1));
//                    LOG.info("stepping down to " + new CategoryDistance(i, cats[i], d, (byte)-1));
                }
            }

            // next steps upwards (if still possible)
//            LOG.info("considering downward cats...");
            if (cs.getDirection() == +1) {
                for (int i : catParents[cs.getCatIndex()]) {
//                    LOG.info("considering cat " + cats[i]);
                    double d = cs.getDistance() + catCosts[cs.getCatIndex()];
                    if (!closedCats.contains(i) && (!catDistances.containsKey(i) || catDistances.get(i) > d)) {
                        catDistances.put(i, d);
                        openCats.add(new CategoryDistance(i, cats[i], d, (byte)-1));
//                        LOG.info("stepping up to " + new CategoryDistance(i, cats[i], d, (byte)-1));
                    }
                }
            }

        }

        return pageDistances;
    }

    private boolean isCat(Document d) {
        return d.getField("ns").stringValue().equals("14");
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
        private String catString;
        private double distance;
        private byte direction; // +1 or -1

        public CategoryDistance(int catIndex, String catString, double distance, byte direction) {
            this.catIndex = catIndex;
            this.catString = catString;
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

        @Override
        public String toString() {
            return "CategoryDistance{" +
                    "catIndex=" + catIndex +
                    ", catString='" + catString + '\'' +
                    ", distance=" + distance +
                    ", direction=" + direction +
                    '}';
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
        cs.calculateTopLevelCategories();
        cs.computePageRanks();
        int cores = (args.length == 4)
                ? Integer.valueOf(args[3])
                : Runtime.getRuntime().availableProcessors();
        cs.openOutput(new File(args[1]));
        cs.calculatePairwiseSims(cores, Integer.valueOf(args[2]));
        cs.closeOutput();
    }
}
