package com.serverops.common;

/**
 * The hierarchy / security rules, kept free of Minecraft so they are easy to reason about and test.
 *
 * <p>Rank: lower number = more senior. Masters (vanilla OP 4 / singleplayer host) are {@link #MASTER}.
 * Players without a role are {@link #UNRANKED} (the bottom).
 */
public final class AccessRules {
    private AccessRules() {}

    public static final int MASTER = -1;
    public static final int UNRANKED = 10_000;

    /**
     * May the actor act on the target? Masters may act on anyone. Everyone else may not touch a master
     * or a player whose rank is more senior (a lower number) than their own.
     */
    public static boolean canTouch(boolean actorMaster, int actorRank, boolean targetMaster, int targetRank) {
        if (actorMaster) return true;
        if (targetMaster) return false;
        return targetRank >= actorRank;
    }

    /** May the actor hand out / take away a role at {@code roleRank}? Must be strictly below the actor. */
    public static boolean canGrantRole(boolean actorMaster, int actorRank, int roleRank) {
        return actorMaster || roleRank > actorRank;
    }

    /** May the actor edit, delete or re-permission a role at {@code roleRank}? Same rule as granting. */
    public static boolean canEditRole(boolean actorMaster, int actorRank, int roleRank) {
        return canGrantRole(actorMaster, actorRank, roleRank);
    }

    /** A non-master may only give permissions they hold themselves. */
    public static long clampPerms(boolean actorMaster, long actorPerms, long requested, long existing) {
        if (actorMaster) return requested & Perm.all();
        return ((requested & actorPerms) | (existing & ~actorPerms)) & Perm.all();
    }
}
