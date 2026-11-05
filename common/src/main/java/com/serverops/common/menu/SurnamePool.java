package com.serverops.common.menu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The surname lists. Each race has its OWN file, so a Japanese player can only ever roll a Japanese
 * surname and a European player only a European one:
 * <pre>
 *   common/src/main/resources/serverops/surnames/japanese.txt   (one surname per line)
 *   common/src/main/resources/serverops/surnames/european.txt
 * </pre>
 * To add or remove surnames just edit those text files (blank lines and lines starting with # are ignored).
 */
public final class SurnamePool {
    private SurnamePool() {}

    private static final Map<Race, List<String>> POOLS = new EnumMap<>(Race.class);

    static {
        for (Race r : Race.values()) POOLS.put(r, load("/serverops/surnames/" + r.name().toLowerCase() + ".txt"));
    }

    private static List<String> load(String resource) {
        List<String> out = new ArrayList<>();
        try (InputStream in = SurnamePool.class.getResourceAsStream(resource)) {
            if (in == null) {
                System.err.println("[ServerOps] Missing surname list: " + resource);
                return Collections.emptyList();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) out.add(s);
            }
        } catch (IOException e) {
            System.err.println("[ServerOps] Could not read " + resource + ": " + e);
        }
        return Collections.unmodifiableList(out);
    }

    public static int size(Race race) {
        return POOLS.get(race).size();
    }

    /** Is this surname part of the given race's list? (used to double-check stored data) */
    public static boolean contains(Race race, String surname) {
        return POOLS.get(race).contains(surname);
    }

    /**
     * Random surname from the list of {@code race} only.
     *
     * @param avoid a surname to avoid repeating (may be null / empty)
     * @return the surname, or "" when the list is empty / missing
     */
    public static String roll(Race race, Random rng, String avoid) {
        List<String> pool = POOLS.get(race);
        if (pool.isEmpty()) return "";
        String pick = pool.get(rng.nextInt(pool.size()));
        for (int i = 0; i < 8 && pool.size() > 1 && pick.equals(avoid); i++) {
            pick = pool.get(rng.nextInt(pool.size()));
        }
        return pick;
    }
}
