package net.teamaof.skylorebosses.bosses.nullrouter.entity;

import java.util.Locale;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Phase;

/**
 * The chassis's attack roster (DESIGN.md §9). Ticks and weights here are the TUNE ME baseline; per-phase deltas
 * (lance count and telegraph, burst size, cooldowns) are in the methods below. Weight arrays are indexed P0..P4; a
 * weight of 0 means the action is not drawn in that phase (scripted beats are forced, not drawn). Every action runs
 * only while the chassis is a ghost: an ACK window cancels whatever is running.
 */
public enum RouterAction {
    //                  tele active rec  cd   scripted   P0 P1 P2 P3 P4
    BOOT_CHIME(          40,   1,  20,   0,  true,       0, 0, 0, 0, 0),
    REQUEST_PING(        20,  30,  10, 300,  false,      0, 3, 3, 2, 2),
    GHOST_LANCE(         30,   6,  16,  60,  false,      0, 6, 5, 4, 4),
    PACKET_BURST(        30,  20,  20, 400,  false,      0, 3, 4, 3, 4),
    CONSOLE_FLICK(       30,   1,  10, 300,  false,      0, 0, 4, 3, 5),
    TTL_EXPIRY(          30,  12,  20, 240,  false,      0, 0, 3, 3, 3),
    MISROUTE_PULSE(      10,   1,  10,   0,  true,       0, 0, 0, 0, 0),
    RETRY_STORM(         40,  40,  20,   0,  true,       0, 0, 0, 0, 0);

    public final int telegraph, active, recovery, cooldown;
    public final boolean scripted;
    private final int[] weights;

    RouterAction(int telegraph, int active, int recovery, int cooldown, boolean scripted, int... weights) {
        this.telegraph = telegraph;
        this.active = active;
        this.recovery = recovery;
        this.cooldown = cooldown;
        this.scripted = scripted;
        this.weights = weights;
    }

    public int weight(Phase p) {
        int i = p.combatIndex();
        return i < 0 ? 0 : weights[i];
    }

    /** Telegraph length including per-phase deltas. */
    public int telegraphTicks(Phase p) {
        if (this == GHOST_LANCE) {
            return switch (p) {
                case P1_SINGLE -> 30;
                case P4_STORM -> 20;
                default -> 24;
            };
        }
        return telegraph;
    }

    /** Active length including per-phase deltas. */
    public int activeTicks(Phase p) {
        if (this == GHOST_LANCE) return lances(p) == 2 ? active + LANCE_STAGGER : active;
        if (this == PACKET_BURST) return 5 * (burst(p) - 1) + 1;
        return active;
    }

    /** Own cooldown including per-phase deltas. */
    public int cooldownTicks(Phase p) {
        return switch (this) {
            case PACKET_BURST -> switch (p) {
                case P2_DUAL -> 320;
                case P3_COOLANT -> 360;
                case P4_STORM -> 200;
                default -> 400;
            };
            case CONSOLE_FLICK -> switch (p) {
                case P3_COOLANT -> 260;
                case P4_STORM -> 140;
                default -> 300;
            };
            default -> cooldown;
        };
    }

    /** Second lance of a P2/P3 salvo fires this many ticks after the first. */
    public static final int LANCE_STAGGER = 12;

    /** Lances per ghost_lance: 1 in P1, 2 staggered in P2/P3, 3 in a fan in P4. */
    public static int lances(Phase p) {
        return switch (p) {
            case P2_DUAL, P3_COOLANT -> 2;
            case P4_STORM -> 3;
            default -> 1;
        };
    }

    /** Retries per packet_burst. */
    public static int burst(Phase p) {
        return switch (p) {
            case P2_DUAL, P3_COOLANT -> 2;
            case P4_STORM -> 3;
            default -> 1;
        };
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static RouterAction byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
