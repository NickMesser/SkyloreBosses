package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import java.util.Locale;

/** Encounter phases (DESIGN.md §4). DORMANT and CLEARED are crypt states around the fight. */
public enum Phase {
    DORMANT, P0_VESTING, P1_COMMUNION, P2_PATCHWORK, P3_VIGIL, P4_RESEED, DEFEATED, CLEARED;

    public boolean fighting() {
        return this == P0_VESTING || this == P1_COMMUNION || this == P2_PATCHWORK || this == P3_VIGIL || this == P4_RESEED;
    }

    /** Index into per-phase attack weight tables: 0..4 for P0..P4, -1 outside the fight. */
    public int combatIndex() {
        return switch (this) {
            case P0_VESTING -> 0;
            case P1_COMMUNION -> 1;
            case P2_PATCHWORK -> 2;
            case P3_VIGIL -> 3;
            case P4_RESEED -> 4;
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
