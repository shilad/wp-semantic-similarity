package edu.macalester.wpsemsim.utils;

import java.util.Comparator;

public final class DocScore implements Comparable<DocScore> {
    protected int id;
    protected double score;

    public final int getId() { return id; }
    public final double getScore() { return score; }

    @Override
    public int compareTo(DocScore ds) {
        if (this.score > ds.score) {
            return -1;
        } else if (this.score < ds.score) {
            return +1;
        } else {
            return this.id - ds.id;
        }
    }
}
