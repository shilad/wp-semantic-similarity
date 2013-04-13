package edu.macalester.wpsemsim.utils;

public final class DocScore implements Comparable<DocScore> {
    protected int id;
    protected double score;

    public final int getId() { return id; }
    public final double getScore() { return score; }
    public final void setScore(double score) { this.score = score; }

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
