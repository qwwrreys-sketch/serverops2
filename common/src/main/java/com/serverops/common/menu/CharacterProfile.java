package com.serverops.common.menu;

import java.util.HashSet;
import java.util.Set;

/** One player's roleplay identity + ReRoll balance. Plain fields so Gson can (de)serialise it. */
public class CharacterProfile {
    /** false until the player confirmed the creation form once. */
    public boolean created;
    public String firstName = "";
    public String surname = "";
    /** {@link Race} name. */
    public String race = Race.EUROPEAN.name();
    public int birthYear = CharacterRules.BIRTH_YEAR_DEFAULT;
    public int rerolls;
    /** Total time spent on the server, in seconds (finished sessions only). */
    public long playSeconds;
    /** Last Minecraft username seen for this UUID (informational). */
    public String lastKnownName = "";
    /** Normalised promo codes this player has already used. */
    public Set<String> redeemedCodes = new HashSet<>();

    public Race raceEnum() {
        return Race.byName(race);
    }

    public String fullName() {
        return created ? firstName + " " + surname : "";
    }

    /** Repair anything missing after loading an older / hand-edited file. */
    public CharacterProfile sanitize() {
        if (firstName == null) firstName = "";
        if (surname == null) surname = "";
        if (race == null) race = Race.EUROPEAN.name();
        if (lastKnownName == null) lastKnownName = "";
        if (redeemedCodes == null) redeemedCodes = new HashSet<>();
        if (rerolls < 0) rerolls = 0;
        if (playSeconds < 0) playSeconds = 0;
        return this;
    }
}
