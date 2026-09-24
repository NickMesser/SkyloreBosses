package net.teamaof.skylorebosses.bosses.staticdeacon.entity;

import java.util.Locale;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Phase;

/**
 * The attack roster (DESIGN.md §9). Ticks and weights here are the TUNE ME baseline; per-phase deltas (bolt and
 * shard counts, double lash) live in {@link #activeTicks(Phase)} and in StaticDeaconEntity. Weight arrays are indexed
 * P0..P4; a weight of 0 means the attack is not in that phase's bag (scripted beats are forced, not drawn).
 */
public enum DeaconAction {
    //                  tele active rec  cd   interrupt              P0 P1 P2 P3 P4
    VESTING_CHIME(       40,   1,  20,   0, Interrupt.NEVER,        0, 0, 0, 0, 0),
    COMMUNION_PULSE(     40,   1,  20, 200, Interrupt.TELEGRAPH,    0, 3, 4, 0, 3),
    LATTICE_LASH(        16,   4,  16,  40, Interrupt.TELEGRAPH,    0, 6, 5, 5, 3),
    STATIC_BOLT(         20,  12,  14,  50, Interrupt.TELEGRAPH,    0, 5, 4, 3, 3),
    NAVE_SHATTER(        30,  22,  30, 160, Interrupt.TELEGRAPH,    0, 0, 3, 4, 2),
    HOMING_SHARD(        30,  24,  16, 180, Interrupt.TELEGRAPH,    0, 0, 3, 3, 3),
    PLINTH_PULL(         25,  10,  10, 140, Interrupt.ANY,          0, 0, 0, 6, 3),
    LITANY_BEAM(         40,  30,  30, 220, Interrupt.ANY,          0, 0, 3, 3, 0),
    RESEED_RITE(         40, 600,  30,   0, Interrupt.NEVER,        0, 0, 0, 0, 0);

    public enum Interrupt { NEVER, TELEGRAPH, ANY }

    public final int telegraph, active, recovery, cooldown;
    public final Interrupt interrupt;
    private final int[] weights;

    DeaconAction(int telegraph, int active, int recovery, int cooldown, Interrupt interrupt, int... weights) {
        this.telegraph = telegraph;
        this.active = active;
        this.recovery = recovery;
        this.cooldown = cooldown;
        this.interrupt = interrupt;
        this.weights = weights;
    }

    public int weight(Phase p) {
        int i = p.combatIndex();
        return i < 0 ? 0 : weights[i];
    }

    /** Active length, including per-phase deltas. */
    public int activeTicks(Phase p) {
        return switch (this) {
            case LATTICE_LASH -> p == Phase.P3_VIGIL ? 14 : 4;
            case STATIC_BOLT -> switch (p) {
                case P1_COMMUNION -> 4;
                case P2_PATCHWORK -> 10;
                default -> 12;
            };
            case HOMING_SHARD -> shards(p, false) * 6;
            default -> active;
        };
    }

    /** Tick offsets of each static bolt within ACTIVE. */
    public static int[] boltSchedule(Phase p) {
        return switch (p) {
            case P1_COMMUNION -> new int[]{0};
            case P2_PATCHWORK -> new int[]{0, 8};
            default -> new int[]{0, 5, 10};
        };
    }

    /** Shards per cast; one more below half HP. */
    public static int shards(Phase p, boolean belowHalf) {
        int n = switch (p) {
            case P3_VIGIL -> 3;
            default -> 2;
        };
        return n + (belowHalf ? 1 : 0);
    }

    /** Recoveries during which the Deacon takes the vulnerable multiplier. */
    public boolean ventsOnRecovery() {
        return this == NAVE_SHATTER || this == LITANY_BEAM;
    }

    public boolean interruptible(boolean inTelegraph) {
        return interrupt == Interrupt.ANY || (interrupt == Interrupt.TELEGRAPH && inTelegraph);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static DeaconAction byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
