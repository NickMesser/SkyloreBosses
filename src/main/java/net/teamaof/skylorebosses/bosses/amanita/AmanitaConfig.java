package net.teamaof.skylorebosses.bosses.amanita;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every value marked TUNE ME in bosses/amanita/DESIGN.md §16: the [amanita] section of skylore_bosses-server.toml.
 * Ticks are game ticks (20 per second). Fractions of HP are of Amanita's max HP. "Light" is the 0..15 light level.
 */
public final class AmanitaConfig {
    // amanita
    public static ModConfigSpec.DoubleValue HP;
    public static ModConfigSpec.DoubleValue MP_HP_PER_PLAYER;
    public static ModConfigSpec.DoubleValue MP_HP_CAP;
    public static ModConfigSpec.DoubleValue ATTACK_SPEED;
    // light gate
    public static ModConfigSpec.IntValue LIGHT_THRESHOLD;
    public static ModConfigSpec.IntValue DEEP_THRESHOLD;
    public static ModConfigSpec.BooleanValue COUNT_SKY_LIGHT;
    public static ModConfigSpec.DoubleValue DARK_MULT;
    public static ModConfigSpec.DoubleValue BRIGHT_BONUS;
    public static ModConfigSpec.DoubleValue BARED_MULT;
    public static ModConfigSpec.IntValue AFTERGLOW_TICKS;
    public static ModConfigSpec.IntValue SOURCE_MIN_EMISSION;
    public static ModConfigSpec.DoubleValue BURST_FRACTION;
    public static ModConfigSpec.IntValue RECOIL_TICKS;
    // phases
    public static ModConfigSpec.IntValue P0_TICKS;
    public static ModConfigSpec.DoubleValue SNUFF_HP;
    public static ModConfigSpec.IntValue RELIGHT_SOURCES;
    public static ModConfigSpec.IntValue P2_MAX_TICKS;
    public static ModConfigSpec.IntValue STALL_P1_TICKS;
    public static ModConfigSpec.IntValue STALL_P3_TICKS;
    // deep bloom
    public static ModConfigSpec.IntValue DEEP_MAX_TICKS;
    public static ModConfigSpec.IntValue DEEP_BREAK_TICKS;
    public static ModConfigSpec.DoubleValue DEEP_REGEN;
    public static ModConfigSpec.DoubleValue DEEP_REGEN_CAP;
    public static ModConfigSpec.IntValue BARED_TICKS;
    // snuff
    public static ModConfigSpec.IntValue SNUFF_RADIUS;
    public static ModConfigSpec.IntValue DEEP_SNUFF_RADIUS;
    public static ModConfigSpec.IntValue SNUFF_DARKNESS_TICKS;
    public static ModConfigSpec.IntValue FULL_SNUFF_DARKNESS_TICKS;
    public static ModConfigSpec.BooleanValue SNUFF_DROPS;
    public static ModConfigSpec.IntValue HUSH_TICKS;
    // adds
    public static ModConfigSpec.IntValue EATER_P1_TICKS;
    public static ModConfigSpec.IntValue EATER_P3_TICKS;
    public static ModConfigSpec.IntValue EATER_MAX_P1;
    public static ModConfigSpec.IntValue EATER_MAX_P3;
    public static ModConfigSpec.IntValue EATER_BITES;
    public static ModConfigSpec.IntValue EATER_CHEW_TICKS;
    public static ModConfigSpec.DoubleValue EATER_HP;
    public static ModConfigSpec.IntValue SPAWN_P3_TICKS;
    public static ModConfigSpec.IntValue SPAWN_MAX;
    public static ModConfigSpec.DoubleValue SPAWN_HP;
    public static ModConfigSpec.DoubleValue SPAWN_DAMAGE;
    public static ModConfigSpec.DoubleValue SPAWN_LIGHT_BURN;
    // attacks
    public static ModConfigSpec.DoubleValue LASH_DAMAGE;
    public static ModConfigSpec.DoubleValue BOLT_DAMAGE;
    public static ModConfigSpec.DoubleValue VEIL_DAMAGE;
    public static ModConfigSpec.DoubleValue SNUFF_DAMAGE;
    public static ModConfigSpec.DoubleValue HOWL_DAMAGE;
    public static ModConfigSpec.DoubleValue SLAM_DAMAGE;
    public static ModConfigSpec.DoubleValue DASH_DAMAGE;
    // origins
    public static ModConfigSpec.ConfigValue<String> TENEBRIS_TAG;
    public static ModConfigSpec.ConfigValue<String> LAEVIS_TAG;
    public static ModConfigSpec.ConfigValue<List<? extends String>> TENEBRIS_ORIGINS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> LAEVIS_ORIGINS;
    public static ModConfigSpec.IntValue CALL_STACKS;
    public static ModConfigSpec.IntValue ROOTED_TICKS;
    public static ModConfigSpec.IntValue CHILL_TICKS;
    public static ModConfigSpec.BooleanValue LAEVIS_HAND_IGNITE;
    // hollow
    public static ModConfigSpec.IntValue DORMANT_TIMEOUT_TICKS;
    public static ModConfigSpec.IntValue COVER_REGEN_TICKS;
    public static ModConfigSpec.BooleanValue REMATCH;
    public static ModConfigSpec.IntValue REMATCH_DELAY_TICKS;
    public static ModConfigSpec.BooleanValue GRANT_TROPHY;
    public static ModConfigSpec.ConfigValue<String> ARTIFACT_LOOT_TABLE;
    public static ModConfigSpec.ConfigValue<String> VICTORY_FUNCTION;

    private AmanitaConfig() {}

    /** Called by the core config builder inside the [amanita] section. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Amanita, the Hollow Bloom. All defaults are first-pass playtest values (TUNE ME).").push("amanita");
        HP = b.comment("Base max HP (solo)").defineInRange("hp", 600.0, 1, 100000);
        MP_HP_PER_PLAYER = b.comment("Extra HP multiplier per additional participant").defineInRange("mpHpPerPlayer", 0.5, 0, 4);
        MP_HP_CAP = b.defineInRange("mpHpCap", 2.5, 1, 10);
        ATTACK_SPEED = b.comment("Scales every attack gap and cooldown; 1.2 = 20% faster").defineInRange("attackSpeed", 1.0, 0.25, 4);
        b.pop();

        b.comment("The light gate: Amanita only takes damage while the light at her position reaches the threshold.",
                "Light at her = max(block light at her feet, block light at her chest), plus sky light if countSkyLight.").push("lightGate");
        LIGHT_THRESHOLD = b.comment("Light at Amanita needed to expose her (P0-P3)").defineInRange("threshold", 7, 1, 15);
        DEEP_THRESHOLD = b.comment("Light needed to expose her during P4 deep bloom").defineInRange("deepThreshold", 10, 1, 15);
        COUNT_SKY_LIGHT = b.comment("Count sky light too (only matters for a hollow built open to the sky)").define("countSkyLight", false);
        DARK_MULT = b.comment("Damage multiplier while closed in the dark. 0 = immune. Above 0 can never take her below 1 HP.")
                .defineInRange("darkMult", 0.0, 0, 1);
        BRIGHT_BONUS = b.comment("Extra damage at light 15, scaling linearly from 0 at the threshold").defineInRange("brightBonus", 0.25, 0, 4);
        BARED_MULT = b.comment("Damage multiplier while bared (after deep bloom is forced open)").defineInRange("baredMult", 1.25, 1, 4);
        AFTERGLOW_TICKS = b.comment("She stays exposed this long after the light at her drops (a stepped-off tile, not a snuff)")
                .defineInRange("afterglowTicks", 10, 0, 200);
        SOURCE_MIN_EMISSION = b.comment("A block emitting at least this much counts as a light source (census, relight, lamp-eater targets)")
                .defineInRange("sourceMinEmission", 6, 1, 15);
        BURST_FRACTION = b.comment("Exposed damage (fraction of max HP) within 40 ticks that breaks an interruptible telegraph")
                .defineInRange("burstFraction", 0.08, 0.005, 1);
        RECOIL_TICKS = b.comment("Stagger when sudden light cancels a telegraph").defineInRange("recoilTicks", 20, 0, 400);
        b.pop();

        b.comment("Phase timers and triggers").push("phases");
        P0_TICKS = b.comment("P0 bloom opens length").defineInRange("p0Ticks", 160, 20, 2400);
        SNUFF_HP = b.comment("P1 -> P2 full snuff at this HP fraction").defineInRange("snuffHp", 0.5, 0.05, 0.95);
        RELIGHT_SOURCES = b.comment("P2 -> P3 once this many light sources burn in the hollow again").defineInRange("relightSources", 3, 1, 64);
        P2_MAX_TICKS = b.comment("P2 -> P3 anyway after this long").defineInRange("p2MaxTicks", 600, 20, 12000);
        STALL_P1_TICKS = b.comment("P1 -> P4 deep bloom after she stays closed (dark) this long without a break").defineInRange("stallP1Ticks", 900, 100, 24000);
        STALL_P3_TICKS = b.comment("P3 -> P4 deep bloom after she stays closed this long").defineInRange("stallP3Ticks", 400, 100, 24000);
        b.pop();

        b.comment("P4 deep bloom").push("deepBloom");
        DEEP_MAX_TICKS = b.comment("Deep bloom ends with a closing snuff after this long").defineInRange("maxTicks", 900, 100, 24000);
        DEEP_BREAK_TICKS = b.comment("Exposed ticks (at deepThreshold, cumulative) that force the bloom open").defineInRange("breakTicks", 80, 5, 2400);
        DEEP_REGEN = b.comment("Fraction of max HP regenerated per second while closed in deep bloom").defineInRange("regen", 0.005, 0, 1);
        DEEP_REGEN_CAP = b.comment("Max fraction of max HP one deep bloom can regenerate").defineInRange("regenCap", 0.10, 0, 1);
        BARED_TICKS = b.comment("Bared after the bloom is forced open").defineInRange("baredTicks", 100, 0, 2400);
        b.pop();

        b.comment("Snuff: light sources in range are extinguished (LIT blocks go out) or broken").push("snuff");
        SNUFF_RADIUS = b.comment("snuff_pulse radius in P1-P3").defineInRange("radius", 8, 1, 64);
        DEEP_SNUFF_RADIUS = b.comment("snuff_pulse radius in P4").defineInRange("deepRadius", 12, 1, 64);
        SNUFF_DARKNESS_TICKS = b.defineInRange("darknessTicks", 160, 0, 2400);
        FULL_SNUFF_DARKNESS_TICKS = b.defineInRange("fullDarknessTicks", 200, 0, 2400);
        SNUFF_DROPS = b.comment("Broken light sources drop their item (torch economy)").define("drops", true);
        HUSH_TICKS = b.comment("darkness_howl: no lights can be placed or lit for this long").defineInRange("hushTicks", 60, 0, 1200);
        b.pop();

        b.comment("Adds: lamp-eaters (eat lights) and hollow-spawn (fight players)").push("adds");
        EATER_P1_TICKS = b.comment("P1 lamp-eater spawn interval (counts only while a light burns)").defineInRange("eaterP1Ticks", 500, 20, 24000);
        EATER_P3_TICKS = b.comment("P3/P4 lamp-eater spawn interval").defineInRange("eaterP3Ticks", 300, 20, 24000);
        EATER_MAX_P1 = b.defineInRange("eaterMaxP1", 2, 0, 16);
        EATER_MAX_P3 = b.defineInRange("eaterMaxP3", 3, 0, 16);
        EATER_BITES = b.comment("Lights a lamp-eater eats before it is sated and burrows away").defineInRange("eaterBites", 3, 1, 64);
        EATER_CHEW_TICKS = b.comment("Time a lamp-eater chews on a light before it goes out").defineInRange("eaterChewTicks", 30, 1, 400);
        EATER_HP = b.defineInRange("eaterHp", 12.0, 1, 1000);
        SPAWN_P3_TICKS = b.comment("P3 hollow-spawn spawn interval").defineInRange("spawnP3Ticks", 400, 20, 24000);
        SPAWN_MAX = b.defineInRange("spawnMax", 3, 0, 16);
        SPAWN_HP = b.defineInRange("spawnHp", 20.0, 1, 1000);
        SPAWN_DAMAGE = b.defineInRange("spawnDamage", 4.0, 0, 1000);
        SPAWN_LIGHT_BURN = b.comment("Damage per second to a hollow-spawn standing in light 12+").defineInRange("spawnLightBurn", 2.0, 0, 1000);
        b.pop();

        b.push("attacks");
        LASH_DAMAGE = b.defineInRange("lashDamage", 7.0, 0, 1000);
        BOLT_DAMAGE = b.defineInRange("boltDamage", 6.0, 0, 1000);
        VEIL_DAMAGE = b.comment("Per second inside a spore veil").defineInRange("veilDamage", 1.0, 0, 1000);
        SNUFF_DAMAGE = b.comment("snuff_pulse damage in P3/P4 (P1 deals none)").defineInRange("snuffDamage", 3.0, 0, 1000);
        HOWL_DAMAGE = b.comment("darkness_howl damage within 6 blocks").defineInRange("howlDamage", 2.0, 0, 1000);
        SLAM_DAMAGE = b.defineInRange("slamDamage", 9.0, 0, 1000);
        DASH_DAMAGE = b.defineInRange("dashDamage", 8.0, 0, 1000);
        b.pop();

        b.comment("Origin asymmetry. The mod never reads Origins directly: a player counts as Tenebris / Laevis if a registered",
                "resolver says so (AmanitaEvents.registerOriginResolver), or they carry the scoreboard tag below.").push("origins");
        TENEBRIS_TAG = b.define("tenebrisTag", "skylore_origin_tenebris");
        LAEVIS_TAG = b.define("laevisTag", "skylore_origin_laevis");
        TENEBRIS_ORIGINS = b.comment("Origin ids a resolver may report as Tenebris").defineListAllowEmpty("tenebrisOrigins",
                List.of("skylore:tenebris"), () -> "", o -> o instanceof String);
        LAEVIS_ORIGINS = b.defineListAllowEmpty("laevisOrigins", List.of("skylore:laevis"), () -> "", o -> o instanceof String);
        CALL_STACKS = b.comment("Hollow Call stacks (one per second in the dark near her) that root a Tenebris player").defineInRange("callStacks", 5, 1, 100);
        ROOTED_TICKS = b.defineInRange("rootedTicks", 40, 0, 1200);
        CHILL_TICKS = b.comment("Weakness I a snuff gives Tenebris players in range (Darkness barely bothers them)").defineInRange("chillTicks", 100, 0, 1200);
        LAEVIS_HAND_IGNITE = b.comment("Laevis players can light a brazier with an empty hand").define("laevisHandIgnite", true);
        b.pop();

        b.push("hollow");
        DORMANT_TIMEOUT_TICKS = b.comment("No participants in the hollow for this long: the fight resets").defineInRange("dormantTimeoutTicks", 1200, 100, 72000);
        COVER_REGEN_TICKS = b.comment("Broken gill shelves regrow after this long").defineInRange("coverRegenTicks", 1200, 20, 72000);
        REMATCH = b.comment("After a kill, the hollow resets for another fight").define("rematch", true);
        REMATCH_DELAY_TICKS = b.defineInRange("rematchDelayTicks", 6000, 200, 720000);
        GRANT_TROPHY = b.comment("Give each participant the Hollow Bloom Cap trophy").define("grantTrophy", true);
        ARTIFACT_LOOT_TABLE = b.comment("Loot table rolled once per participant on victory (the pack's shadow artifacts; empty = none)")
                .define("artifactLootTable", "skylore_bosses:gameplay/amanita/shadow_artifacts");
        VICTORY_FUNCTION = b.comment("Function run as each participant on victory (empty = none), e.g. skylore:story/act1/amanita_bested")
                .define("victoryFunction", "");
        b.pop();
    }
}
