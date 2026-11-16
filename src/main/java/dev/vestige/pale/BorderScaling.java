package dev.vestige.pale;

/** Pure border math, free of game classes so it can be unit-tested on a plain JVM. */
public final class BorderScaling {
    private BorderScaling() {}

    /**
     * Size of a dependent dimension's border.
     *
     * @param overworldSize Overworld border diameter
     * @param divisor       dimension divisor (must be ≥ 1)
     * @param minimumSize   lower clamp for the result
     */
    public static double scaledSize(double overworldSize, double divisor, double minimumSize) {
        if (!(divisor >= 1.0)) {
            throw new IllegalArgumentException("divisor must be >= 1, was " + divisor);
        }
        if (overworldSize < 0.0) {
            throw new IllegalArgumentException("overworldSize must be >= 0, was " + overworldSize);
        }
        return Math.max(minimumSize, overworldSize / divisor);
    }
}
