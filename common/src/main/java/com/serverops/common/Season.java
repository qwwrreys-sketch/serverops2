package com.serverops.common;

/** Server-wide season. Only changes grass / foliage tint on clients (no gameplay effect). */
public enum Season {
    SPRING("Spring", 0xA6E85A, 25, 0x8FE07A, 25),
    SUMMER("Summer", 0x000000, 0, 0x000000, 0),
    AUTUMN("Autumn", 0xC9A441, 55, 0xD8662A, 75),
    WINTER("Winter", 0xE6EEF3, 65, 0xC5D5DD, 60);

    public final String label;
    public final int grassColor;
    public final int grassPercent;
    public final int leafColor;
    public final int leafPercent;

    Season(String label, int grassColor, int grassPercent, int leafColor, int leafPercent) {
        this.label = label;
        this.grassColor = grassColor;
        this.grassPercent = grassPercent;
        this.leafColor = leafColor;
        this.leafPercent = leafPercent;
    }

    public static Season byOrdinal(int i) {
        Season[] v = values();
        return i >= 0 && i < v.length ? v[i] : SUMMER;
    }

    public static Season byName(String n) {
        for (Season s : values()) if (s.name().equalsIgnoreCase(n)) return s;
        return SUMMER;
    }

    /** Blend {@code base} towards the season colour (both 0xRRGGBB). */
    public static int blend(int base, int target, int percent) {
        if (percent <= 0) return base;
        int p = Math.min(100, percent);
        int r = ((base >> 16 & 255) * (100 - p) + (target >> 16 & 255) * p) / 100;
        int g = ((base >> 8 & 255) * (100 - p) + (target >> 8 & 255) * p) / 100;
        int b = ((base & 255) * (100 - p) + (target & 255) * p) / 100;
        return r << 16 | g << 8 | b;
    }

    public int tintGrass(int base) {
        return blend(base, grassColor, grassPercent);
    }

    public int tintLeaves(int base) {
        return blend(base, leafColor, leafPercent);
    }
}
