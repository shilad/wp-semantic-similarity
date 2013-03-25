package edu.macalester.wpsemsim.utils;

public class MathUtils {

    /**
     * Returns a value that approaches yInf from y0 as xDelta increases.
     * The values for the function range from (0, y0) to (inf, yInf).
     * For each xHalfLife, the function moves 50% closer to yInf.
     *
     * @param x
     * @param xHalfLife
     * @param y0
     * @param yInf
     * @return
     */
    public static double toAsymptote(double x, double xHalfLife, double y0, double yInf) {
        assert(x > 0);
        double hl = x / xHalfLife;
        return y0 + (1.0 - Math.exp(-hl)) * (yInf - y0);
    }
}
