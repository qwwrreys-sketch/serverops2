package com.serverops.common;

/** Every permission a role can carry. Bit position = ordinal(), so never reorder existing entries. */
public enum Perm {
    PANEL_ACCESS("Panel access", "Master key: open and use this panel."),
    SERVER_WORLD("World control", "Change weather, time of day and season."),
    HEAL("Heal members", "Heal players."),
    TELEPORT("Teleport members", "Bring a player to you, or go to them."),
    RESPAWN("Respawn members", "Force a player to respawn."),
    KICK("Kick members", "Remove someone from the server. They can rejoin."),
    BAN("Ban members", "Ban players. Also allows unbanning."),
    FREEZE("Freeze members", "Stop someone from moving."),
    GODMODE("Godmode", "Toggle godmode for players."),
    INVISIBLE("Go invisible", "Hide players from everyone else."),
    FLY("Manage flight", "Let players fly."),
    SIGHT("Sight tools", "ESP and health bars for players."),
    ASSIGN_ROLES("Assign roles", "Give and take roles below their own."),
    MANAGE_ROLES("Manage roles", "Create, edit and reorder roles below their own.");

    public final String label;
    public final String description;

    Perm(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public long bit() {
        return 1L << ordinal();
    }

    public static long all() {
        long m = 0;
        for (Perm p : values()) m |= p.bit();
        return m;
    }

    public static boolean has(long mask, Perm p) {
        return (mask & p.bit()) != 0;
    }
}
