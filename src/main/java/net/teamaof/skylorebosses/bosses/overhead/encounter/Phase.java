package net.teamaof.skylorebosses.bosses.overhead.encounter;

import java.util.Locale;

/** Encounter phases (DESIGN.md §4). DORMANT and CLEARED are yard states around the fight. */
public enum Phase {
    DORMANT, P0_LOCKDOWN, P1_SHIELDED, P2_DEGRADED, P3_EXPOSED, P4_REARM, DEFEATED, CLEARED;

    public boolean fighting() {
        return this == P0_LOCKDOWN || this == P1_SHIELDED || this == P2_DEGRADED || this == P3_EXPOSED || this == P4_REARM;
    }

    /** Index into per-phase attack weight tables: 0..4 for P0..P4, -1 outside the fight. */
    public int combatIndex() {
        return switch (this) {
            case P0_LOCKDOWN -> 0;
            case P1_SHIELDED -> 1;
            case P2_DEGRADED -> 2;
            case P3_EXPOSED -> 3;
            case P4_REARM -> 4;
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
