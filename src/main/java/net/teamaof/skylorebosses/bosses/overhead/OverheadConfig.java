package net.teamaof.skylorebosses.bosses.overhead;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every value marked TUNE ME in bosses/overhead/DESIGN.md §15: the [overhead] section of skylore_bosses-server.toml.
 * Ticks are game ticks (20 per second).
 */
public final class OverheadConfig {
    // chassis
    public static ModConfigSpec.DoubleValue HP;
    public static ModConfigSpec.DoubleValue MP_HP_PER_PLAYER;
    public static ModConfigSpec.DoubleValue MP_HP_CAP;
    // damage gate
    public static ModConfigSpec.DoubleValue DR_4, DR_3, DR_2, DR_1;
    public static ModConfigSpec.DoubleValue WINDOW_DR;
    public static ModConfigSpec.DoubleValue REARM_DR;
    public static ModConfigSpec.DoubleValue VULNERABLE_MULT;
    public static ModConfigSpec.IntValue WINDOW_TICKS;
    public static ModConfigSpec.IntValue STAGGER_TICKS;
    public static ModConfigSpec.IntValue EXPOSE_TICKS;
    // pylons
    public static ModConfigSpec.DoubleValue PYLON_HP;
    public static ModConfigSpec.DoubleValue PYLON_MP_PER_PLAYER;
    public static ModConfigSpec.DoubleValue PYLON_MP_CAP;
    public static ModConfigSpec.DoubleValue PYLON_PROJECTILE_DAMAGE;
    public static ModConfigSpec.DoubleValue PYLON_EXPLOSION_DAMAGE;
    public static ModConfigSpec.DoubleValue PYLON_FRIENDLY_FIRE;
    public static ModConfigSpec.DoubleValue OVERCHARGE_REPAIR;
    // re-arm
    public static ModConfigSpec.IntValue REBUILD_TICKS;
    public static ModConfigSpec.IntValue REBUILD_STAGGER_TICKS;
    public static ModConfigSpec.IntValue REARM_BASE_PYLONS;
    public static ModConfigSpec.IntValue REARM_ABORT_TICKS;
    public static ModConfigSpec.DoubleValue REARM_ABORT_EXPOSE_FRACTION;
    // attacks
    public static ModConfigSpec.DoubleValue HOWITZER_DAMAGE;
    public static ModConfigSpec.DoubleValue MISSILE_DAMAGE;
    public static ModConfigSpec.DoubleValue LASER_DAMAGE;
    public static ModConfigSpec.DoubleValue BOLT_DAMAGE;
    public static ModConfigSpec.DoubleValue OVERCHARGE_DAMAGE;
    public static ModConfigSpec.DoubleValue CARPET_DAMAGE;
    public static ModConfigSpec.DoubleValue FLAK_DAMAGE;
    public static ModConfigSpec.DoubleValue SHIELD_PULSE_DAMAGE;
    public static ModConfigSpec.DoubleValue ATTACK_SPEED;
    // yard
    public static ModConfigSpec.IntValue DORMANT_TIMEOUT_TICKS;
    public static ModConfigSpec.IntValue COVER_REGEN_TICKS;
    public static ModConfigSpec.BooleanValue REMATCH;
    public static ModConfigSpec.IntValue REMATCH_DELAY_TICKS;
    public static ModConfigSpec.ConfigValue<String> VICTORY_FUNCTION;

    private OverheadConfig() {}

    /** Called by the core config builder inside the [overhead] section. */
    public static void define(ModConfigSpec.Builder b) {
        b.comment("Overhead chassis. All defaults are first-pass playtest values (TUNE ME).").push("chassis");
        HP = b.comment("Base max HP (solo)").defineInRange("hp", 600.0, 1, 100000);
        MP_HP_PER_PLAYER = b.comment("Extra HP multiplier per additional participant").defineInRange("mpHpPerPlayer", 0.5, 0, 4);
        MP_HP_CAP = b.defineInRange("mpHpCap", 2.5, 1, 10);
        ATTACK_SPEED = b.comment("Scales every attack gap and cooldown; 1.2 = 20% faster").defineInRange("attackSpeed", 1.0, 0.25, 4);
        b.pop();

        b.comment("Damage gate: fraction of damage Overhead ignores. Keyed by live pylon count.").push("gate");
        DR_4 = b.defineInRange("drFourPylons", 0.90, 0, 1);
        DR_3 = b.defineInRange("drThreePylons", 0.85, 0, 1);
        DR_2 = b.defineInRange("drTwoPylons", 0.80, 0, 1);
        DR_1 = b.defineInRange("drOnePylon", 0.75, 0, 1);
        WINDOW_DR = b.comment("DR during the window after a pylon breaks").defineInRange("windowDr", 0.0, 0, 1);
        REARM_DR = b.comment("DR during emergency re-arm, after the shield pulse").defineInRange("rearmDr", 0.5, 0, 1);
        VULNERABLE_MULT = b.comment("Damage multiplier during vented recoveries (laser_sweep, desperation_carpet)").defineInRange("vulnerableMult", 1.25, 1, 4);
        WINDOW_TICKS = b.defineInRange("windowTicks", 200, 20, 2400);
        STAGGER_TICKS = b.comment("Brownout stagger when a pylon breaks (no attacks)").defineInRange("staggerTicks", 40, 0, 400);
        EXPOSE_TICKS = b.comment("P3: ticks with zero pylons before emergency re-arm").defineInRange("exposeTicks", 900, 100, 12000);
        b.pop();

        b.push("pylons");
        PYLON_HP = b.comment("Integrity per pylon (solo)").defineInRange("integrity", 150.0, 1, 100000);
        PYLON_MP_PER_PLAYER = b.defineInRange("mpIntegrityPerPlayer", 0.35, 0, 4);
        PYLON_MP_CAP = b.defineInRange("mpIntegrityCap", 2.0, 1, 10);
        PYLON_PROJECTILE_DAMAGE = b.comment("Integrity lost per player projectile that hits a pylon block").defineInRange("projectileDamage", 6.0, 0, 1000);
        PYLON_EXPLOSION_DAMAGE = b.comment("Integrity lost at the centre of a player explosion (falls off over 5 blocks)").defineInRange("explosionDamage", 30.0, 0, 1000);
        PYLON_FRIENDLY_FIRE = b.comment("Fraction of Overhead's own blast damage its pylons take").defineInRange("friendlyFire", 0.5, 0, 1);
        OVERCHARGE_REPAIR = b.comment("Integrity a pylon regains when pylon_overcharge discharges").defineInRange("overchargeRepair", 20.0, 0, 1000);
        b.pop();

        b.push("rearm");
        REBUILD_TICKS = b.comment("Ticks for one pylon to rebuild from 0 to full").defineInRange("rebuildTicks", 300, 20, 12000);
        REBUILD_STAGGER_TICKS = b.defineInRange("rebuildStaggerTicks", 100, 0, 2400);
        REARM_BASE_PYLONS = b.comment("Pylons rebuilt by the first re-arm; +1 per later re-arm, max 4").defineInRange("basePylons", 2, 1, 4);
        REARM_ABORT_TICKS = b.comment("If the target count is not online after this long, the re-arm aborts back to P3").defineInRange("abortTicks", 1200, 100, 24000);
        REARM_ABORT_EXPOSE_FRACTION = b.defineInRange("abortExposeFraction", 0.66, 0.1, 1);
        b.pop();

        b.push("attacks");
        HOWITZER_DAMAGE = b.defineInRange("howitzerDamage", 14.0, 0, 1000);
        MISSILE_DAMAGE = b.defineInRange("missileDamage", 6.0, 0, 1000);
        LASER_DAMAGE = b.comment("Per laser tick (every 5 ticks while touching the beam)").defineInRange("laserDamage", 4.0, 0, 1000);
        BOLT_DAMAGE = b.defineInRange("boltDamage", 3.0, 0, 1000);
        OVERCHARGE_DAMAGE = b.defineInRange("overchargeDamage", 10.0, 0, 1000);
        CARPET_DAMAGE = b.defineInRange("carpetDamage", 8.0, 0, 1000);
        FLAK_DAMAGE = b.defineInRange("flakDamage", 6.0, 0, 1000);
        SHIELD_PULSE_DAMAGE = b.defineInRange("shieldPulseDamage", 4.0, 0, 1000);
        b.pop();

        b.push("yard");
        DORMANT_TIMEOUT_TICKS = b.comment("No participants in the yard for this long: the fight resets").defineInRange("dormantTimeoutTicks", 1200, 100, 72000);
        COVER_REGEN_TICKS = b.comment("Destroyed cover crates are reprinted after this long").defineInRange("coverRegenTicks", 1200, 20, 72000);
        REMATCH = b.comment("After a kill, the yard re-arms for another fight").define("rematch", true);
        REMATCH_DELAY_TICKS = b.defineInRange("rematchDelayTicks", 6000, 200, 720000);
        VICTORY_FUNCTION = b.comment("Function run as each participant on victory (empty = none), e.g. skylore:story/act2/prototype_down")
                .define("victoryFunction", "");
        b.pop();
    }
}
