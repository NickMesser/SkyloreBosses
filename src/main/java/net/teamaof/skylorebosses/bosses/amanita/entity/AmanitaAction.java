package net.teamaof.skylorebosses.bosses.amanita.entity;

import java.util.Locale;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Phase;

/**
 * The attack roster (DESIGN.md §9). Ticks and weights here are the TUNE ME baseline; per-phase deltas (bolt counts,
 * the double lash, snuff cooldowns) live in {@link #activeTicks(Phase)}, {@link #cooldown(Phase)} and AmanitaEntity.
 * Weight arrays are indexed P0..P4; a weight of 0 means the attack is not in that phase's bag (scripted beats are
 * forced by the hollow, not drawn).
 */
public enum AmanitaAction {
    //                    tele active rec   cd  interrupt                P0 P1 P2 P3 P4
    BLOOM_OPEN(            40,   1,  20,    0, Interrupt.NEVER,          0, 0, 0, 0, 0),
    SHADOW_LASH(           14,   4,  14,   40, Interrupt.LIGHT_OR_BURST, 0, 6, 5, 5, 4),
    HOLLOW_BOLT(           20,  11,  14,   50, Interrupt.LIGHT_OR_BURST, 0, 5, 4, 4, 4),
    SPORE_VEIL(            30,   1,  16,  220, Interrupt.LIGHT_OR_BURST, 0, 3, 2, 3, 2),
    SNUFF_PULSE(           40,   1,  24,  300, Interrupt.BURST,          0, 3, 0, 4, 5),
    DARKNESS_HOWL(         30,   1,  20,  400, Interrupt.LIGHT_OR_BURST, 0, 0, 3, 2, 3),
    BLOOM_SLAM(            30,  16,  30,  160, Interrupt.BURST,          0, 0, 0, 4, 3),
    LIGHT_SEEKER_DASH(     20,  42,  16,  180, Interrupt.BURST,          0, 3, 0, 4, 3),
    DEEP_BLOOM(            40,   1,  20,    0, Interrupt.NEVER,          0, 0, 0, 0, 0),
    FULL_SNUFF(            60,   1,  30,    0, Interrupt.NEVER,          0, 0, 0, 0, 0);

    /** What can cancel the telegraph: nothing, a burst of exposed damage, or also a sudden rise of light at her. */
    public enum Interrupt { NEVER, BURST, LIGHT_OR_BURST }

    /** Dash: ticks of travel at most, then the smother on arrival. */
    public static final int DASH_TRAVEL = 30, DASH_SMOTHER = 12;

    public final int telegraph, active, recovery, cooldown;
    public final Interrupt interrupt;
    private final int[] weights;

    AmanitaAction(int telegraph, int active, int recovery, int cooldown, Interrupt interrupt, int... weights) {
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
            case SHADOW_LASH -> p == Phase.P3_LIT_DUEL || p == Phase.P4_DEEP_BLOOM ? 14 : 4;
            case HOLLOW_BOLT -> switch (p) {
                case P1_DARK_IMMUNITY -> 1;
                case P2_FULL_SNUFF -> 9;
                default -> 11;
            };
            default -> active;
        };
    }

    public int cooldown(Phase p) {
        if (this == SNUFF_PULSE) return switch (p) {
            case P1_DARK_IMMUNITY -> 400;
            case P4_DEEP_BLOOM -> 200;
            default -> 300;
        };
        return cooldown;
    }

    /** Tick offsets of each hollow bolt within ACTIVE. */
    public static int[] boltSchedule(Phase p) {
        return switch (p) {
            case P1_DARK_IMMUNITY -> new int[]{0};
            case P2_FULL_SNUFF -> new int[]{0, 8};
            default -> new int[]{0, 5, 10};
        };
    }

    /** Scripted beats during which she is sealed (immune): closing the flower. */
    public boolean seals() {
        return this == FULL_SNUFF || this == DEEP_BLOOM;
    }

    public boolean lightInterruptible() { return interrupt == Interrupt.LIGHT_OR_BURST; }

    public boolean burstInterruptible() { return interrupt != Interrupt.NEVER; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static AmanitaAction byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
