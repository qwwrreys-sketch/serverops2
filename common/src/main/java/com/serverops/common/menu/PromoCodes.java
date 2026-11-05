package com.serverops.common.menu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The promo-code table. THIS is the place where you add new codes.
 *
 * <p>Two ways to add codes (both work at the same time):
 * <ol>
 *   <li><b>In code</b>: add a line to {@link #BUILT_IN} below and rebuild the mod.</li>
 *   <li><b>Without rebuilding (recommended for real codes)</b>: create the text file
 *       {@code <server>/config/serverops-codes.txt} with one code per line:
 *       {@code CODE,rerolls}  or  {@code CODE,rerolls,maxUses}  (lines starting with # are comments).
 *       It is read when the server starts. A code in the file overrides the same code in {@link #BUILT_IN}.</li>
 * </ol>
 * Codes are not case sensitive. {@code maxUses} = how many players in TOTAL may redeem it (-1 = unlimited).
 * Every player can use each code only once.
 *
 * <p>NOTE: the mod jar is also installed on every player's PC, so anything written in {@link #BUILT_IN}
 * can be read by someone who decompiles the jar. Keep your real/secret codes in the server-side file.
 */
public final class PromoCodes {
    private PromoCodes() {}

    /** One redeemable code. */
    public record Code(String code, int rerolls, int maxUses) {
        public Code(String code, int rerolls) {
            this(code, rerolls, -1);
        }
    }

    // =====================================================================================
    //  ADD YOUR CODES HERE  ->  new Code("YOURCODE", rerolls)   or   new Code("YOURCODE", rerolls, maxUses)
    //  (these two are only examples - replace or delete them)
    // =====================================================================================
    private static final List<Code> BUILT_IN = List.of(
            new Code("WELCOME", 3),
            new Code("LAUNCH2010", 5, 100)
    );

    private static volatile Map<String, Code> table = build(null);

    /** Upper-case + trim, so "  welcome " matches WELCOME. */
    public static String normalise(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    /** @return the code, or null if it does not exist */
    public static Code find(String raw) {
        String key = normalise(raw);
        return key.isEmpty() ? null : table.get(key);
    }

    /** (Re)loads BUILT_IN plus the optional text file. Safe to call when the file does not exist. */
    public static void reload(Path extraFile) {
        table = build(extraFile);
        System.out.println("[ServerOps] " + table.size() + " promo code(s) loaded.");
    }

    private static Map<String, Code> build(Path extraFile) {
        Map<String, Code> map = new HashMap<>();
        for (Code c : BUILT_IN) map.put(normalise(c.code()), new Code(normalise(c.code()), c.rerolls(), c.maxUses()));
        if (extraFile != null && Files.isRegularFile(extraFile)) {
            try {
                for (String line : Files.readAllLines(extraFile, StandardCharsets.UTF_8)) {
                    String s = line.trim();
                    if (s.isEmpty() || s.startsWith("#")) continue;
                    String[] p = s.split(",");
                    try {
                        String code = normalise(p[0]);
                        int rerolls = Integer.parseInt(p[1].trim());
                        int max = p.length > 2 ? Integer.parseInt(p[2].trim()) : -1;
                        if (!code.isEmpty() && rerolls > 0) map.put(code, new Code(code, rerolls, max));
                    } catch (RuntimeException bad) {
                        System.err.println("[ServerOps] Ignoring bad promo line: " + s);
                    }
                }
            } catch (IOException e) {
                System.err.println("[ServerOps] Could not read " + extraFile + ": " + e);
            }
        }
        return map;
    }
}
