package net.teamaof.skylorebosses.bosses.staticdeacon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every value marked TUNE ME in bosses/static_deacon/DESIGN.md §15: the [static_deacon] section of
 * skylore_bosses-server.toml. Ticks are game ticks (20 per second). Fractions of HP are of the Deacon's max HP.
 */
public final class DeaconConfig {
    // deacon
    public static ModConfigSpec.DoubleValue HP;
    public static ModConfigSpec.DoubleValue MP_HP_PER_PLAYER;
    public static ModConfigSpec.DoubleValue MP_HP_CAP;
    public static ModConfigSpec.DoubleValue ATTACK_SPEED;
    // communion (heal floor)
    public static ModConfigSpec.DoubleValue NAVE_REGEN;
    public static ModConfigSpec.DoubleValue NAVE_DR;
    public static ModConfigSpec.DoubleValue PLINTH_REGEN;
    public static ModConfigSpec.DoubleValue PLINTH_DR;
    public static ModConfigSpec.DoubleValue RITE_DR;
    public static ModConfigSpec.DoubleValue P2_THRESHOLD;
    public static ModConfigSpec.DoubleValue VULNERABLE_MULT;
    public static ModConfigSpec.DoubleValue DESECRATION_THREAT;
    public static ModConfigSpec.IntValue STUMBLE_TICKS;
    // vigil (plinth hold)
    public static ModConfigSpec.IntValue EXPOSE_TICKS;
    public static ModConfigSpec.DoubleValue VIGIL_BREAK_FRACTION;
    public static ModConfigSpec.IntValue VIGIL_BREAK_WINDOW;
    public static ModConfigSpec.IntValue KNOCKOFF_STAGGER_TICKS;
    public static ModConfigSpec.IntValue DESECRATED_TICKS;
    public static ModConfigSpec.IntValue PLINTH_LOCK_TICKS;
    // reseed rite
    public static ModConfigSpec.IntValue RESEED_BASE;
    public static ModConfigSpec.IntValue RESEED_PER_RITE;
    public static ModConfigSpec.IntValue RESEED_MAX;
    public static ModConfigSpec.DoubleValue RESEED_SLACK;
    public static ModConfigSpec.IntValue RESEED_STEP_TICKS;
    public static ModConfigSpec.IntValue RESEED_GROW_TICKS;
    public static ModConfigSpec.IntValue RITE_RECAST_TICKS;
    public static ModConfigSpec.DoubleValue RITE_BREAK_FRACTION;
    public static ModConfigSpec.IntValue RESEED_TIMEOUT_TICKS;
    public static ModConfigSpec.IntValue RESEED_ABORT_EXPOSE_TICKS;
    // attacks
    public static ModConfigSpec.DoubleValue LASH_DAMAGE;
    public static ModConfigSpec.DoubleValue BOLT_DAMAGE;
    public static ModConfigSpec.DoubleValue PULSE_DAMAGE;
    public static ModConfigSpec.DoubleValue PULSE_HEAL;
    public static ModConfigSpec.DoubleValue SHATTER_DAMAGE;
    public static ModConfigSpec.DoubleValue SHARD_DAMAGE;
    public static ModConfigSpec.DoubleValue BEAM_DAMAGE;
    public static ModConfigSpec.IntValue STATIC_TICKS;
    // crypt
    public static ModConfigSpec.IntValue DORMANT_TIMEOUT_TICKS;
    public static ModConfigSpec.IntValue COVER_REGEN_TICKS;
    public static ModConfigSpec.BooleanValue REMATCH;
    public static ModConfigSpec.IntValue REMATCH_DELAY_TICKS;
    public static ModConfigSpec.ConfigValue<String> VICTORY_FUNCTION;

    private DeaconConfig() {}

    /** Called by the core config builder inside the [static_deacon] section. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("The Static Deacon. All defaults are first-pass playtest values (TUNE ME).").push("deacon");
        HP = b.comment("Base max HP (solo)").defineInRange("hp", 800.0, 1, 100000);
        MP_HP_PER_PLAYER = b.comment("Extra HP multiplier per additional participant").defineInRange("mpHpPerPlayer", 0.5, 0, 4);
        MP_HP_CAP = b.defineInRange("mpHpCap", 2.5, 1, 10);
        ATTACK_SPEED = b.comment("Scales every attack gap and cooldown; 1.2 = 20% faster").defineInRange("attackSpeed", 1.0, 0.25, 4);
        b.pop();

        b.comment("Communion: the Deacon heals and hardens while standing on a live consecrated flagstone.",
                "Nave regen and DR scale with coverage C = live nave flagstones / total nave flagstones.").push("communion");
        NAVE_REGEN = b.comment("Fraction of max HP regenerated per second on a live flagstone at C = 1 (scales linearly with C)")
                .defineInRange("naveRegen", 0.04, 0, 1);
        NAVE_DR = b.comment("Damage reduction on a live flagstone at C = 1 (scales linearly with C)").defineInRange("naveDr", 0.5, 0, 1);
        PLINTH_REGEN = b.comment("Fraction of max HP regenerated per second while keeping vigil on the altar plinth").defineInRange("plinthRegen", 0.006, 0, 1);
        PLINTH_DR = b.comment("Damage reduction while keeping vigil on the altar plinth").defineInRange("plinthDr", 0.25, 0, 1);
        RITE_DR = b.comment("Damage reduction while channelling the reseed rite").defineInRange("riteDr", 0.6, 0, 1);
        P2_THRESHOLD = b.comment("P1 -> P2 once coverage drops below this").defineInRange("p2Threshold", 0.70, 0.05, 1);
        VULNERABLE_MULT = b.comment("Damage multiplier while desecrated (vigil broken) or in a vented recovery").defineInRange("vulnerableMult", 1.25, 1, 4);
        DESECRATION_THREAT = b.comment("Threat a player gains per flagstone they desecrate").defineInRange("desecrationThreat", 30.0, 0, 10000);
        STUMBLE_TICKS = b.comment("Stagger when the flagstone under the Deacon is desecrated").defineInRange("stumbleTicks", 20, 0, 400);
        b.pop();

        b.comment("P3 plinth hold (vigil)").push("vigil");
        EXPOSE_TICKS = b.comment("P3 length before the reseed rite starts").defineInRange("exposeTicks", 900, 100, 12000);
        VIGIL_BREAK_FRACTION = b.comment("Damage (after DR, as a fraction of max HP) within the window that knocks the Deacon off the plinth")
                .defineInRange("vigilBreakFraction", 0.08, 0.005, 1);
        VIGIL_BREAK_WINDOW = b.defineInRange("vigilBreakWindowTicks", 60, 5, 1200);
        KNOCKOFF_STAGGER_TICKS = b.defineInRange("knockoffStaggerTicks", 60, 0, 1200);
        DESECRATED_TICKS = b.comment("After a vigil or rite break the Deacon takes vulnerableMult damage for this long").defineInRange("desecratedTicks", 100, 0, 1200);
        PLINTH_LOCK_TICKS = b.comment("After a vigil or rite break the Deacon cannot re-mount the plinth for this long").defineInRange("plinthLockTicks", 100, 0, 2400);
        b.pop();

        b.comment("P4 reseed rite").push("reseed");
        RESEED_BASE = b.comment("Flagstones the first rite must restore to end P4").defineInRange("baseFlagstones", 12, 1, 200);
        RESEED_PER_RITE = b.comment("Extra flagstones per later rite").defineInRange("perRite", 6, 0, 200);
        RESEED_MAX = b.defineInRange("maxFlagstones", 36, 1, 200);
        RESEED_SLACK = b.comment("The rite schedules target * (1 + slack) flagstones so a few denials do not end it early").defineInRange("scheduleSlack", 0.5, 0, 4);
        RESEED_STEP_TICKS = b.comment("Ticks between flagstones while channelling").defineInRange("stepTicks", 10, 1, 200);
        RESEED_GROW_TICKS = b.comment("A reseeding flagstone turns live after this long unless it is broken or covered").defineInRange("growTicks", 40, 1, 400);
        RITE_RECAST_TICKS = b.comment("After the rite is broken, the Deacon waits this long before channelling again").defineInRange("recastTicks", 200, 20, 2400);
        RITE_BREAK_FRACTION = b.comment("Damage (after DR, fraction of max HP) within vigilBreakWindowTicks that breaks the rite").defineInRange("riteBreakFraction", 0.10, 0.005, 1);
        RESEED_TIMEOUT_TICKS = b.defineInRange("timeoutTicks", 1200, 100, 24000);
        RESEED_ABORT_EXPOSE_TICKS = b.comment("P3 clock after a rite that ends with no live nave flagstones").defineInRange("abortExposeTicks", 600, 20, 12000);
        b.pop();

        b.push("attacks");
        LASH_DAMAGE = b.defineInRange("lashDamage", 9.0, 0, 1000);
        BOLT_DAMAGE = b.defineInRange("boltDamage", 7.0, 0, 1000);
        PULSE_DAMAGE = b.defineInRange("pulseDamage", 3.0, 0, 1000);
        PULSE_HEAL = b.comment("Fraction of max HP the Deacon regains per player a communion pulse hits").defineInRange("pulseHeal", 0.02, 0, 1);
        SHATTER_DAMAGE = b.defineInRange("shatterDamage", 8.0, 0, 1000);
        SHARD_DAMAGE = b.defineInRange("shardDamage", 5.0, 0, 1000);
        BEAM_DAMAGE = b.comment("Per litany contact (every 5 ticks)").defineInRange("beamDamage", 4.0, 0, 1000);
        STATIC_TICKS = b.comment("Mining Fatigue I applied by static bolts, shards and the litany").defineInRange("staticTicks", 80, 0, 1200);
        b.pop();

        b.push("crypt");
        DORMANT_TIMEOUT_TICKS = b.comment("No participants in the crypt for this long: the fight resets").defineInRange("dormantTimeoutTicks", 1200, 100, 72000);
        COVER_REGEN_TICKS = b.comment("Broken pews are replaced after this long").defineInRange("coverRegenTicks", 1200, 20, 72000);
        REMATCH = b.comment("After a kill, the crypt resets for another fight").define("rematch", true);
        REMATCH_DELAY_TICKS = b.defineInRange("rematchDelayTicks", 6000, 200, 720000);
        VICTORY_FUNCTION = b.comment("Function run as each participant on victory (empty = none), e.g. skylore:story/act3/static_deacon_cleared")
                .define("victoryFunction", "");
        b.pop();
    }
}
