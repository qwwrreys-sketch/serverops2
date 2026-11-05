package com.serverops.common.menu;

/** The two playable ethnicities. The ordinal is sent over the network, so never reorder these. */
public enum Race {
    EUROPEAN("European"),
    JAPANESE("Japanese");

    public final String label;

    Race(String label) {
        this.label = label;
    }

    /** @return the race, or null when the number is not valid (for example a tampered packet). */
    public static Race byOrdinal(int ordinal) {
        Race[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }

    /** Forgiving lookup for data read from JSON; falls back to EUROPEAN. */
    public static Race byName(String name) {
        for (Race r : values()) if (r.name().equalsIgnoreCase(name)) return r;
        return EUROPEAN;
    }
}
