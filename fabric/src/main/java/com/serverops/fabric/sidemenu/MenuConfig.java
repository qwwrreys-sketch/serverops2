package com.serverops.fabric.sidemenu;

/** Small switches for the side menu. Change a value and rebuild. */
public final class MenuConfig {
    private MenuConfig() {}

    /** Open the Character tab by itself when a player who has no character yet joins. */
    public static final boolean AUTO_OPEN_CHARACTER_FOR_NEW_PLAYERS = true;

    /** Draw the little "<" tab on the left edge of the screen while playing (the menu key opens it). */
    public static final boolean SHOW_HUD_TAB = true;

    /**
     * false = every player may use Freecam.
     * true  = only players the existing admin panel lets in (OP / panel role) may use it.
     */
    public static final boolean FREECAM_STAFF_ONLY = false;

    /** Freecam cannot fly further than this many blocks away from the player's body. */
    public static final double FREECAM_MAX_DISTANCE = 64.0;

    /** Blocks per tick (20 ticks = 1 second). Sprint multiplies it. */
    public static final double FREECAM_SPEED = 0.45;
    public static final double FREECAM_SPRINT_MULTIPLIER = 3.0;
}
