package net.teamaof.skylorebosses.bosses.amanita.encounter;

import java.util.Locale;

/** Encounter phases (DESIGN.md §4). DORMANT and CLEARED are hollow states around the fight. */
public enum Phase {
    DORMANT, P0_BLOOM_OPENS, P1_DARK_IMMUNITY, P2_FULL_SNUFF, P3_LIT_DUEL, P4_DEEP_BLOOM, DEFEATED, CLEARED;

    public boolean fighting() {
        return this == P0_BLOOM_OPENS || this == P1_DARK_IMMUNITY || this == P2_FULL_SNUFF || this == P3_LIT_DUEL || this == P4_DEEP_BLOOM;
    }

    /** Index into per-phase attack weight tables: 0..4 for P0..P4, -1 outside the fight. */
    public int combatIndex() {
        return switch (this) {
            case P0_BLOOM_OPENS -> 0;
            case P1_DARK_IMMUNITY -> 1;
            case P2_FULL_SNUFF -> 2;
            case P3_LIT_DUEL -> 3;
            case P4_DEEP_BLOOM -> 4;
            default -> -1;
        };
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Phase byName(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return DORMANT;
        }
    }
}
