package com.serverops.common;

/** Steps of the ban duration slider. Step 0 = permanent ("Forever"). */
public final class BanDurations {
    private BanDurations() {}

    public static final int[] MINUTES = {0, 10, 30, 60, 180, 720, 1440, 4320, 10080, 20160, 43200, 129600, 525600};

    public static int steps() {
        return MINUTES.length;
    }

    public static int minutes(int step) {
        return MINUTES[Math.max(0, Math.min(MINUTES.length - 1, step))];
    }

    public static String label(int step) {
        int m = minutes(step);
        if (m == 0) return "Forever";
        if (m < 60) return m + " min";
        if (m < 1440) return (m / 60) + (m / 60 == 1 ? " hour" : " hours");
        if (m < 43200) return (m / 1440) + (m / 1440 == 1 ? " day" : " days");
        if (m < 525600) return (m / 43200) + (m / 43200 == 1 ? " month" : " months");
        return "1 year";
    }
}
