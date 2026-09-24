package net.teamaof.skylorebosses.bosses.overhead.entity;

import java.util.Locale;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Phase;

/**
 * The attack roster (DESIGN.md §9). Ticks and weights here are the TUNE ME baseline; per-phase deltas (shell
 * counts, missile counts, laser length) live in {@link #activeTicks(Phase)} and in OverheadEntity.
 * Weight arrays are indexed P0..P4; a weight of 0 means the attack is illegal in that phase.
 */
public enum Action {
    //                  tele active rec  cd   interrupt              P0 P1 P2 P3 P4
    POWER_ON_PULSE(      30,  1,   30,   0,  Interrupt.NEVER,       0, 0, 0, 0, 0),
    HOWITZER_LOB(        20, 20,   20,  80,  Interrupt.TELEGRAPH,   0, 5, 4, 3, 3),
    MISSILE_SALVO(       30, 32,   20, 160,  Interrupt.TELEGRAPH,   0, 3, 3, 3, 2),
    LASER_SWEEP(         40, 60,   30, 220,  Interrupt.ANY,         0, 0, 3, 3, 0),
    STRAFE_BARRAGE(      30, 60,   20, 200,  Interrupt.ANY,         0, 0, 3, 2, 0),
    SUPPRESSION_FLARE(   20, 30,   10, 400,  Interrupt.TELEGRAPH,   0, 2, 2, 0, 2),
    PYLON_OVERCHARGE(    40,  1,   20, 240,  Interrupt.ANY,         0, 6, 6, 0, 4),
    DESPERATION_CARPET(  60, 40,   60, 400,  Interrupt.NEVER,       0, 0, 0, 4, 3),
    REARM_SHIELD_PULSE(  20, 60,    0,   0,  Interrupt.NEVER,       0, 0, 0, 0, 0),
    FLAK_BURST(          20, 13,   10, 100,  Interrupt.NEVER,       0, 8, 8, 8, 8);

    public enum Interrupt { NEVER, TELEGRAPH, ANY }

    public final int telegraph, active, recovery, cooldown;
    public final Interrupt interrupt;
    private final int[] weights;

    Action(int telegraph, int active, int recovery, int cooldown, Interrupt interrupt, int... weights) {
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
            case HOWITZER_LOB -> p == Phase.P1_SHIELDED ? 10 : 20;
            case MISSILE_SALVO -> missiles(p) * 4;
            case LASER_SWEEP -> p == Phase.P2_DEGRADED ? 50 : 60;
            default -> active;
        };
    }

    public static int missiles(Phase p) {
        return switch (p) {
            case P1_SHIELDED -> 4;
            case P2_DEGRADED -> 6;
            case P3_EXPOSED -> 8;
            default -> 4;
        };
    }

    /** Recovery during which Overhead takes the vulnerable multiplier. */
    public boolean ventsOnRecovery() {
        return this == LASER_SWEEP || this == DESPERATION_CARPET;
    }

    public boolean interruptible(boolean inTelegraph) {
        return interrupt == Interrupt.ANY || (interrupt == Interrupt.TELEGRAPH && inTelegraph);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Action byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
