package edu.macalester.wpsemsim.utils;

import org.apache.commons.collections.iterators.ArrayIterator;

import java.util.Arrays;
import java.util.Iterator;

public class DocScoreList implements Iterable<DocScore> {
    private DocScore[] results;
    private int numDocs;

    public DocScoreList(int maxNumDocs) {
        this.results = new DocScore[maxNumDocs];
        for (int i = 0; i < this.results.length; i++) {
            results[i] = new DocScore();
        }
        numDocs = maxNumDocs;
    }

    public int numDocs() {
        return numDocs;
    }

    public int getId(int i) {
        assert(i < numDocs);
        return results[i].id;
    }

    public double getScore(int i) {
        assert(i < numDocs);
        return results[i].score;
    }

    public void truncate(int numDocs) {
        assert(numDocs <= results.length);
        this.numDocs = numDocs;
    }

    public void set(int i, int id, double score) {
        assert(i < numDocs);
        results[i].id = id;
        results[i].score = score;
    }

    public int[] getIds() {
        int ids[] = new int[numDocs];
        for (int i = 0; i < numDocs; i++) {
            ids[i] = results[i].id;
        }
        return ids;
    }

    public double[] getScores() {
        double result[] = new double[numDocs];
        for (int i = 0; i < numDocs; i++) {
            result[i] = results[i].score;
        }
        return result;
    }

    public float[] getScoresAsFloat() {
        float result[] = new float[numDocs];
        for (int i = 0; i < numDocs; i++) {
            result[i] = (float) results[i].score;
        }
        return result;
    }

    public void sort() {
        Arrays.sort(results, 0, numDocs);
    }

    @Override
    public Iterator<DocScore> iterator() {
        return new ArrayIterator(results, 0, numDocs);
    }

}
