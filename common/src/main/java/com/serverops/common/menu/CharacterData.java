package com.serverops.common.menu;

import java.util.HashMap;
import java.util.Map;

/** Everything the side menu persists (world/serverops/characters.json). */
public class CharacterData {
    /** player uuid -> profile */
    public Map<String, CharacterProfile> players = new HashMap<>();
    /** normalised promo code -> how many times it has been redeemed in total (for max-use limits) */
    public Map<String, Integer> codeUses = new HashMap<>();

    public CharacterData sanitize() {
        if (players == null) players = new HashMap<>();
        if (codeUses == null) codeUses = new HashMap<>();
        players.values().removeIf(p -> p == null);
        for (CharacterProfile p : players.values()) p.sanitize();
        return this;
    }

    /** Gets (or creates) the profile of a player. */
    public CharacterProfile profile(String uuid) {
        return players.computeIfAbsent(uuid, k -> new CharacterProfile());
    }
}
