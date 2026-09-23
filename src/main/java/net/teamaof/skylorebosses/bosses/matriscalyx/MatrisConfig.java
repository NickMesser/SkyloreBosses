package net.teamaof.skylorebosses.bosses.matriscalyx;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Every value marked TUNE in DESIGN.md: the [matris_calyx] section of skylore_bosses-server.toml. */
public final class MatrisConfig {
    public static ModConfigSpec.DoubleValue MP_HP_PER_PLAYER;
    public static ModConfigSpec.DoubleValue MP_HP_CAP;
    public static ModConfigSpec.IntValue WINDOW_PERIOD_TICKS;
    public static ModConfigSpec.IntValue WINDOW_JITTER_TICKS;
    public static ModConfigSpec.IntValue WINDOW_OPEN_TICKS;
    public static ModConfigSpec.IntValue WINDOW_WARN_TICKS;
    public static ModConfigSpec.IntValue NERVE_PULSE_TICKS;
    public static ModConfigSpec.DoubleValue NERVE_DAMAGE_BUFF;
    public static ModConfigSpec.IntValue BODY_ATTACK_MIN_TICKS;
    public static ModConfigSpec.IntValue BODY_ATTACK_MAX_TICKS;
    public static ModConfigSpec.BooleanValue INFECTION_ENABLED;
    public static ModConfigSpec.IntValue INFECTION_PASSIVE_SECONDS;
    public static ModConfigSpec.IntValue SYRINGES_PER_PLAYER;
    public static ModConfigSpec.DoubleValue DOCTOR_CURE_MULTIPLIER;
    public static ModConfigSpec.ConfigValue<List<? extends String>> CURE_VALUES;
    public static ModConfigSpec.IntValue VENT_HP;
    public static ModConfigSpec.IntValue VENT_SPAWN_TICKS;
    public static ModConfigSpec.IntValue ADD_CAP_BASE;
    public static ModConfigSpec.IntValue ADD_CAP_PER_PLAYER;
    public static ModConfigSpec.DoubleValue BLOOM_HP;
    public static ModConfigSpec.DoubleValue LASER_DAMAGE;
    public static ModConfigSpec.DoubleValue LASER_TURN_DEG_PER_TICK;
    public static ModConfigSpec.IntValue ARENA_RADIUS;
    public static ModConfigSpec.BooleanValue BUILD_DOME;
    public static ModConfigSpec.ConfigValue<String> VICTORY_FUNCTION;
    public static ModConfigSpec.IntValue DORMANT_TIMEOUT_TICKS;

    /** Called by the core config builder inside the [matris_calyx] section. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Matris Calyx encounter tuning. All defaults are first-pass playtest values.").push("encounter");
        MP_HP_PER_PLAYER = b.comment("Extra HP multiplier per additional player").defineInRange("mpHpPerPlayer", 0.5, 0, 4);
        MP_HP_CAP = b.defineInRange("mpHpCap", 3.0, 1, 10);
        WINDOW_PERIOD_TICKS = b.comment("Ticks an arm stays armoured between open windows").defineInRange("windowPeriodTicks", 300, 40, 2400);
        WINDOW_JITTER_TICKS = b.defineInRange("windowJitterTicks", 40, 0, 400);
        WINDOW_OPEN_TICKS = b.defineInRange("windowOpenTicks", 60, 10, 400);
        WINDOW_WARN_TICKS = b.defineInRange("windowWarnTicks", 16, 0, 100);
        NERVE_PULSE_TICKS = b.defineInRange("nervePulseTicks", 100, 20, 1200);
        NERVE_DAMAGE_BUFF = b.defineInRange("nerveDamageBuff", 0.2, 0, 2);
        BODY_ATTACK_MIN_TICKS = b.defineInRange("bodyAttackMinTicks", 500, 100, 6000);
        BODY_ATTACK_MAX_TICKS = b.defineInRange("bodyAttackMaxTicks", 800, 100, 6000);
        ARENA_RADIUS = b.comment("Players within this many blocks of the heart are participants").defineInRange("arenaRadius", 260, 64, 1024);
        BUILD_DOME = b.comment("Build the rib dome and membrane floor (large; disable for quick tests)").define("buildDome", true);
        DORMANT_TIMEOUT_TICKS = b.defineInRange("dormantTimeoutTicks", 6000, 200, 72000);
        VICTORY_FUNCTION = b.comment("Function run as each participant on victory (empty = none), e.g. skylore:story/ending/savior")
                .define("victoryFunction", "");
        b.pop();

        b.push("infection");
        INFECTION_ENABLED = b.define("enabled", true);
        INFECTION_PASSIVE_SECONDS = b.comment("Seconds per +1 ambient infection inside the arena").defineInRange("passiveSeconds", 6, 1, 600);
        SYRINGES_PER_PLAYER = b.defineInRange("syringesPerPlayer", 4, 0, 64);
        DOCTOR_CURE_MULTIPLIER = b.defineInRange("doctorCureMultiplier", 1.5, 1, 5);
        CURE_VALUES = b.comment("item_id=amount pairs that reduce infection when eaten/drunk")
                .defineListAllowEmpty("cureValues", List.of("minecraft:honey_bottle=10", "minecraft:golden_apple=25",
                        "minecraft:enchanted_golden_apple=60", "minecraft:milk_bucket=15"), o -> o instanceof String s && s.contains("="));
        b.pop();

        b.push("vents");
        VENT_HP = b.defineInRange("ventHp", 60, 1, 10000);
        VENT_SPAWN_TICKS = b.defineInRange("ventSpawnTicks", 600, 40, 12000);
        ADD_CAP_BASE = b.defineInRange("addCapBase", 6, 0, 100);
        ADD_CAP_PER_PLAYER = b.defineInRange("addCapPerPlayer", 3, 0, 100);
        b.pop();

        b.push("bloom");
        BLOOM_HP = b.defineInRange("bloomHp", 400.0, 1, 100000);
        LASER_DAMAGE = b.defineInRange("laserDamage", 8.0, 0, 1000);
        LASER_TURN_DEG_PER_TICK = b.defineInRange("laserTurnDegPerTick", 0.9, 0.05, 20);
        b.pop();
    }

    private MatrisConfig() {}
}
