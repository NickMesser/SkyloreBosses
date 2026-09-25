package net.teamaof.skylorebosses.bosses.nullrouter;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Phase;

/**
 * Every value marked TUNE ME in bosses/null_router/DESIGN.md §15: the [null_router] section of
 * skylore_bosses-server.toml. Ticks are game ticks (20 per second).
 */
public final class RouterConfig {
    // queue
    public static ModConfigSpec.IntValue QUEUE_BASE;
    public static ModConfigSpec.IntValue QUEUE_PER_PLAYER;
    public static ModConfigSpec.IntValue QUEUE_CAP;
    public static ModConfigSpec.DoubleValue DAMAGE_PER_REQUEST;
    public static ModConfigSpec.DoubleValue DRY_MULT_EARLY;
    public static ModConfigSpec.DoubleValue WET_MULT_EARLY;
    public static ModConfigSpec.DoubleValue DRY_MULT_LATE;
    public static ModConfigSpec.DoubleValue WET_MULT_LATE;
    public static ModConfigSpec.IntValue ACK_CAP_P0;
    public static ModConfigSpec.IntValue ACK_CAP_EARLY;
    public static ModConfigSpec.IntValue ACK_CAP_WET_LATE;
    public static ModConfigSpec.IntValue ACK_CAP_DRY_LATE;
    public static ModConfigSpec.IntValue WET_MINIMUM;
    public static ModConfigSpec.DoubleValue ATTACK_SPEED;
    // ack window
    public static ModConfigSpec.IntValue ARM_TICKS;
    public static ModConfigSpec.IntValue SOLIDIFY_TICKS;
    public static ModConfigSpec.IntValue OPEN_TICKS;
    public static ModConfigSpec.IntValue WARN_TICKS;
    public static ModConfigSpec.IntValue RELEASE_TICKS;
    public static ModConfigSpec.IntValue WET_TICKS;
    public static ModConfigSpec.IntValue CONSOLE_COOLDOWN_TICKS;
    // misroute
    public static ModConfigSpec.IntValue MISROUTE_QUEUE_PENALTY;
    public static ModConfigSpec.IntValue MISROUTE_HOLD_TICKS;
    public static ModConfigSpec.IntValue MISROUTE_COOLDOWN_TICKS;
    // phases
    public static ModConfigSpec.DoubleValue P2_QUEUE_FRACTION;
    public static ModConfigSpec.IntValue P2_AFTER_ACKS;
    public static ModConfigSpec.DoubleValue P3_QUEUE_FRACTION;
    public static ModConfigSpec.IntValue STALL_P1;
    public static ModConfigSpec.IntValue STALL_P2;
    public static ModConfigSpec.IntValue STALL_P3;
    public static ModConfigSpec.IntValue STALL_P4;
    public static ModConfigSpec.IntValue P4_NO_WET_ACK_TICKS;
    public static ModConfigSpec.IntValue P4_QUEUE_RISE;
    public static ModConfigSpec.IntValue ALTERNATE_P2;
    public static ModConfigSpec.IntValue ALTERNATE_P4;
    public static ModConfigSpec.IntValue ALTERNATE_WARN;
    public static ModConfigSpec.IntValue STORM_RECAST_TICKS;
    public static ModConfigSpec.IntValue STORM_FLICK_TICKS;
    // retries
    public static ModConfigSpec.DoubleValue RETRY_HP;
    public static ModConfigSpec.DoubleValue RETRY_DAMAGE;
    public static ModConfigSpec.IntValue RETRY_FLIP_TICKS;
    public static ModConfigSpec.IntValue RETRY_FLIP_TICKS_STORM;
    public static ModConfigSpec.IntValue RETRY_LIFE_TICKS;
    public static ModConfigSpec.IntValue RETRY_CAP_P1;
    public static ModConfigSpec.IntValue RETRY_CAP_P2;
    public static ModConfigSpec.IntValue RETRY_CAP_P3;
    public static ModConfigSpec.IntValue RETRY_CAP_P4;
    // hazards
    public static ModConfigSpec.DoubleValue SHORT_DAMAGE;
    public static ModConfigSpec.IntValue SHORT_SLOW_TICKS;
    public static ModConfigSpec.IntValue DRAIN_TICKS;
    public static ModConfigSpec.IntValue FLIGHT_CEILING;
    // attacks
    public static ModConfigSpec.DoubleValue LANCE_DAMAGE;
    public static ModConfigSpec.DoubleValue TTL_DAMAGE;
    public static ModConfigSpec.DoubleValue MISROUTE_DAMAGE;
    // vault
    public static ModConfigSpec.IntValue DORMANT_TIMEOUT_TICKS;
    public static ModConfigSpec.BooleanValue REMATCH;
    public static ModConfigSpec.IntValue REMATCH_DELAY_TICKS;
    public static ModConfigSpec.ConfigValue<String> VICTORY_FUNCTION;

    private RouterConfig() {}

    /** Called by the core config builder inside the [null_router] section. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Null Router, the Unacked. All defaults are first-pass playtest values (TUNE ME).",
                "The boss bar is the unacked request queue: damage only counts inside an ACK window, and each request",
                "in the queue costs damagePerRequest points of damage (times the dry/wet multiplier) to clear.").push("queue");
        QUEUE_BASE = b.comment("Queue depth at the start of a solo fight").defineInRange("queueBase", 24, 1, 500);
        QUEUE_PER_PLAYER = b.comment("Extra requests per additional participant at the start").defineInRange("queuePerPlayer", 4, 0, 100);
        QUEUE_CAP = b.comment("Most requests a queue can hold (start value and stall growth are capped here)").defineInRange("queueCap", 40, 1, 1000);
        DAMAGE_PER_REQUEST = b.comment("Raw damage (after the multiplier) that clears one request").defineInRange("damagePerRequest", 20.0, 0.5, 10000);
        DRY_MULT_EARLY = b.comment("P0-P2 multiplier for damage to a dry chassis").defineInRange("dryMultEarly", 1.0, 0, 20);
        WET_MULT_EARLY = b.comment("P0-P2 multiplier for damage to a wet chassis").defineInRange("wetMultEarly", 1.5, 0, 20);
        DRY_MULT_LATE = b.comment("P3-P4 multiplier for damage to a dry chassis (a scratch)").defineInRange("dryMultLate", 0.25, 0, 20);
        WET_MULT_LATE = b.comment("P3-P4 multiplier for damage to a wet chassis (the real DPS)").defineInRange("wetMultLate", 2.5, 0, 20);
        ACK_CAP_P0 = b.comment("Most requests one ACK window can clear in P0").defineInRange("ackCapP0", 2, 1, 100);
        ACK_CAP_EARLY = b.comment("Most requests one ACK window can clear in P1-P2").defineInRange("ackCapEarly", 3, 1, 100);
        ACK_CAP_WET_LATE = b.comment("Most requests one wet ACK window can clear in P3-P4").defineInRange("ackCapWetLate", 4, 1, 100);
        ACK_CAP_DRY_LATE = b.comment("Most requests one dry ACK window can clear in P3-P4").defineInRange("ackCapDryLate", 1, 0, 100);
        WET_MINIMUM = b.comment("A window in which the chassis was wet and took any damage clears at least this many").defineInRange("wetMinimum", 1, 0, 100);
        ATTACK_SPEED = b.comment("Scales every attack gap and cooldown; 1.2 = 20% faster").defineInRange("attackSpeed", 1.0, 0.25, 4);
        b.pop();

        b.comment("The ACK window: consoles match the head request -> the chassis solidifies and can be damaged").push("ack");
        ARM_TICKS = b.comment("A console pattern that matches a request must hold this long before it commits (misread grace)").defineInRange("armTicks", 20, 1, 200);
        SOLIDIFY_TICKS = b.comment("Docking: solid but not yet damageable (leading edge i-frames)").defineInRange("solidifyTicks", 10, 0, 200);
        OPEN_TICKS = b.comment("Damageable window").defineInRange("openTicks", 110, 10, 2400);
        WARN_TICKS = b.comment("Last part of the open window that flashes a closing warning (still damageable)").defineInRange("warnTicks", 30, 0, 2400);
        RELEASE_TICKS = b.comment("Undocking: solid but no longer damageable (trailing edge i-frames)").defineInRange("releaseTicks", 10, 0, 200);
        WET_TICKS = b.comment("The chassis counts as wet this long after its last water contact (inside a window only)").defineInRange("wetTicks", 40, 1, 1200);
        CONSOLE_COOLDOWN_TICKS = b.comment("Per-console cooldown between flips").defineInRange("consoleCooldownTicks", 4, 0, 100);
        b.pop();

        b.comment("Misroute: consoles held on the decoy request").push("misroute");
        MISROUTE_QUEUE_PENALTY = b.defineInRange("queuePenalty", 1, 0, 50);
        MISROUTE_HOLD_TICKS = b.comment("The service alcove gate stays shut this long").defineInRange("holdTicks", 60, 0, 1200);
        MISROUTE_COOLDOWN_TICKS = b.comment("A player misrouted again within this long is slowed instead of teleported").defineInRange("cooldownTicks", 300, 0, 6000);
        b.pop();

        b.comment("Phase flow").push("phases");
        P2_QUEUE_FRACTION = b.comment("P1 -> P2 when the queue is at or below this fraction of its start").defineInRange("p2QueueFraction", 0.75, 0, 1);
        P2_AFTER_ACKS = b.comment("... or after this many acks in P1").defineInRange("p2AfterAcks", 3, 1, 100);
        P3_QUEUE_FRACTION = b.comment("P2 -> P3 when the queue is at or below this fraction of its start").defineInRange("p3QueueFraction", 0.42, 0, 1);
        STALL_P1 = b.comment("Ticks without an ack before a request is retransmitted (queue +1), per phase").defineInRange("stallP1", 600, 20, 72000);
        STALL_P2 = b.defineInRange("stallP2", 500, 20, 72000);
        STALL_P3 = b.defineInRange("stallP3", 400, 20, 72000);
        STALL_P4 = b.defineInRange("stallP4", 300, 20, 72000);
        P4_NO_WET_ACK_TICKS = b.comment("P3 -> P4 after this long without a wet ack").defineInRange("p4NoWetAckTicks", 1200, 100, 72000);
        P4_QUEUE_RISE = b.comment("P1-P3 -> P4 when the queue climbs this far above its lowest point in the phase").defineInRange("p4QueueRise", 3, 1, 100);
        ALTERNATE_P2 = b.comment("P2 dual queue: the head swaps between the two requests this often").defineInRange("alternateP2", 240, 40, 72000);
        ALTERNATE_P4 = b.defineInRange("alternateP4", 160, 40, 72000);
        ALTERNATE_WARN = b.comment("Warning before the head swaps").defineInRange("alternateWarn", 40, 0, 400);
        STORM_RECAST_TICKS = b.comment("P4 recasts retry_storm this often").defineInRange("stormRecastTicks", 600, 100, 72000);
        STORM_FLICK_TICKS = b.comment("P4 randomises one console this often on top of attacks").defineInRange("stormFlickTicks", 200, 20, 72000);
        b.pop();

        b.comment("Retry packets (adds)").push("retries");
        RETRY_HP = b.defineInRange("hp", 6.0, 1, 1000);
        RETRY_DAMAGE = b.comment("Contact zap when a retry aggroes a player").defineInRange("damage", 3.0, 0, 1000);
        RETRY_FLIP_TICKS = b.comment("Uplink time at a console before it flips").defineInRange("flipTicks", 30, 1, 400);
        RETRY_FLIP_TICKS_STORM = b.defineInRange("flipTicksStorm", 20, 1, 400);
        RETRY_LIFE_TICKS = b.comment("A retry times out after this long").defineInRange("lifeTicks", 1200, 100, 72000);
        RETRY_CAP_P1 = b.comment("Most retries alive at once, per phase").defineInRange("capP1", 2, 0, 50);
        RETRY_CAP_P2 = b.defineInRange("capP2", 3, 0, 50);
        RETRY_CAP_P3 = b.defineInRange("capP3", 3, 0, 50);
        RETRY_CAP_P4 = b.defineInRange("capP4", 6, 0, 50);
        b.pop();

        b.comment("Hazards").push("hazards");
        SHORT_DAMAGE = b.comment("floor_short: damage every 20 ticks (one per player i-frame cycle) to a player standing in water outside an ACK window")
                .defineInRange("shortDamage", 3.0, 0, 1000);
        SHORT_SLOW_TICKS = b.defineInRange("shortSlowTicks", 40, 0, 1200);
        DRAIN_TICKS = b.comment("Water in the vault outside an ACK window is drained after this long").defineInRange("drainTicks", 100, 0, 72000);
        FLIGHT_CEILING = b.comment("Players more than this many blocks above the floor are pushed down (clean-room airflow)").defineInRange("flightCeiling", 7, 2, 12);
        b.pop();

        b.push("attacks");
        LANCE_DAMAGE = b.defineInRange("lanceDamage", 8.0, 0, 1000);
        TTL_DAMAGE = b.defineInRange("ttlDamage", 6.0, 0, 1000);
        MISROUTE_DAMAGE = b.comment("misroute_pulse damage when no alcove or retry is available").defineInRange("misrouteDamage", 4.0, 0, 1000);
        b.pop();

        b.push("vault");
        DORMANT_TIMEOUT_TICKS = b.comment("No participants in the vault for this long: the fight resets").defineInRange("dormantTimeoutTicks", 1200, 100, 72000);
        REMATCH = b.comment("After a clear, the vault resets for another fight").define("rematch", true);
        REMATCH_DELAY_TICKS = b.defineInRange("rematchDelayTicks", 6000, 200, 720000);
        VICTORY_FUNCTION = b.comment("Function run as each participant on victory (empty = none), e.g. skylore:story/act4/null_router_cleared")
                .define("victoryFunction", "");
        b.pop();
    }

    public static int queueStart(int players) {
        int n = Math.max(1, players);
        return Mth.clamp(QUEUE_BASE.get() + QUEUE_PER_PLAYER.get() * (n - 1), 1, QUEUE_CAP.get());
    }

    public static int stallTicks(Phase p) {
        return switch (p) {
            case P1_SINGLE -> STALL_P1.get();
            case P2_DUAL -> STALL_P2.get();
            case P3_COOLANT -> STALL_P3.get();
            case P4_STORM -> STALL_P4.get();
            default -> Integer.MAX_VALUE;
        };
    }

    public static int retryCap(Phase p) {
        return switch (p) {
            case P1_SINGLE -> RETRY_CAP_P1.get();
            case P2_DUAL -> RETRY_CAP_P2.get();
            case P3_COOLANT -> RETRY_CAP_P3.get();
            case P4_STORM -> RETRY_CAP_P4.get();
            default -> 0;
        };
    }

    /** Throughput multiplier for damage inside an ACK window. */
    public static double mult(Phase p, boolean wet) {
        if (p.coolant()) return (wet ? WET_MULT_LATE : DRY_MULT_LATE).get();
        return (wet ? WET_MULT_EARLY : DRY_MULT_EARLY).get();
    }

    /** Most requests one window may clear. */
    public static int ackCap(Phase p, boolean wet) {
        if (p == Phase.P0_BOOT) return ACK_CAP_P0.get();
        if (p.coolant()) return (wet ? ACK_CAP_WET_LATE : ACK_CAP_DRY_LATE).get();
        return ACK_CAP_EARLY.get();
    }
}
