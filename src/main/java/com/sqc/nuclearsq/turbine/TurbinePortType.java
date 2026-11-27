package com.sqc.nuclearsq.turbine;

public enum TurbinePortType {
    ENERGY("energy", 'E', 11, 1, 0, false),
    STEAM("steam", 'V', 9, 8, 16, true),
    WATER("water", 'W', 9, 0, 41, false);

    private final String id;
    public final char c;
    public final int x, y, z;
    public final boolean input;

    TurbinePortType(String id, char c, int x, int y, int z, boolean input) {
        this.id = id;
        this.c = c;
        this.x = x;
        this.y = y;
        this.z = z;
        this.input = input;
    }

    public String id() {
        return id;
    }
}
