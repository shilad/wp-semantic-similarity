package edu.macalester.wpsemsim.sim;

import gnu.trove.map.hash.TIntDoubleHashMap;

public class SimUtils {
    static double cosineSimilarity(TIntDoubleHashMap X, TIntDoubleHashMap Y) {
        double xDotX = 0.0;
        double yDotY = 0.0;
        double xDotY = 0.0;

        for (int id : X.keys()) {
            double x = X.get(id);
            xDotX += x * x;
            if (Y.containsKey(id)) {
                xDotY += x * Y.get(id);
            }
        }
        for (int id : Y.keys()) {
            double y = Y.get(id);
            yDotY += y * y;
        }

        if (yDotY == 0.0) {
            return Double.NaN;
        } else {
            return xDotY / Math.sqrt(xDotX * yDotY);
        }

    }
}
