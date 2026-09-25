package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

import java.util.Locale;

/** Encounter phases (DESIGN.md §4). DORMANT and CLEARED are vault states around the fight. */
public enum Phase {
    DORMANT, P0_BOOT, P1_SINGLE, P2_DUAL, P3_COOLANT, P4_STORM, DEFEATED, CLEARED;

    public boolean fighting() {
        return this == P0_BOOT || this == P1_SINGLE || this == P2_DUAL || this == P3_COOLANT || this == P4_STORM;
    }

    /** Index into per-phase attack weight tables: 0..4 for P0..P4, -1 outside the fight. */
    public int combatIndex() {
        return switch (this) {
            case P0_BOOT -> 0;
            case P1_SINGLE -> 1;
            case P2_DUAL -> 2;
            case P3_COOLANT -> 3;
            case P4_STORM -> 4;
            default -> -1;
        };
    }

    /** P2+ show two requests (head and a decoy); matching the decoy misroutes. */
    public boolean dualQueue() { return this == P2_DUAL || this == P3_COOLANT || this == P4_STORM; }

    /** P3+ use the late (coolant) throughput table: dry acks barely move the queue. */
    public boolean coolant() { return this == P3_COOLANT || this == P4_STORM; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Phase byName(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return DORMANT;
        }
    }
}
