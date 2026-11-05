package com.serverops.common.menu;

/**
 * Every tunable number and validation rule of the Character system, in one place.
 * Plain Java (no Minecraft) so client and server share exactly the same rules.
 */
public final class CharacterRules {
    private CharacterRules() {}

    // ---------------------------------------------------------------- lore / birth year
    /** The year the server's roleplay lore is "currently" in. Age = LORE_YEAR - birth year. */
    public static final int LORE_YEAR = 2010;
    public static final int BIRTH_YEAR_MIN = 1980;
    public static final int BIRTH_YEAR_MAX = 1990;
    public static final int BIRTH_YEAR_DEFAULT = 1985;

    // ---------------------------------------------------------------- ReRoll prices (set to 0 for "free")
    /** Changing the first name AFTER the free first-time choice. Can be done as often as the player can pay. */
    public static final int COST_NAME_CHANGE = 1;
    // NOTE: the birth year has NO price because it can never be changed after the first choice.
    /** Rolling a new random surname within the current race. */
    public static final int COST_SURNAME_REROLL = 1;
    /** Switching race (the surname is re-rolled from the new race's list automatically). */
    public static final int COST_RACE_CHANGE = 1;

    // ---------------------------------------------------------------- names
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 16;

    /**
     * Validates and tidies a first name: letters plus inner ' or - only, 2-16 chars, first letter capitalised.
     *
     * @return the cleaned name, or null when it is not acceptable
     */
    public static String cleanName(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.length() < NAME_MIN || s.length() > NAME_MAX) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetter(c) || c == '\'' || c == '-')) return null;
        }
        if (!Character.isLetter(s.charAt(0)) || !Character.isLetter(s.charAt(s.length() - 1))) return null;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static boolean validBirthYear(int year) {
        return year >= BIRTH_YEAR_MIN && year <= BIRTH_YEAR_MAX;
    }

    public static int age(int birthYear) {
        return LORE_YEAR - birthYear;
    }
}
