package com.sqc.nuclearsq.machine.shapes;

public final class KilnShapes {
    private KilnShapes() {}

    public static final int COUNT = 3;

    public static final double[][][] BOXES = {
            {{0, 0, 0, 16, 16, 16}},
            {{0, 0, 8, 16, 16, 16}},
            {{0, 0, 8, 16, 8, 16}, {0, 4, 0, 16, 8, 8}},
    };

    public static final boolean[] LADDER = {false, false, false};

    public static final int[][] CELLS = {
            {1, 0, 1, 0}, {1, 0, 2, 0}, {1, 1, 1, 1}, {1, 1, 2, 0}, {1, 2, 1, 2},
    };

    public static final double[] MONITOR = {0.4375, 0.375, -0.125, 0.8125, 0.75, -0.0625};
}
