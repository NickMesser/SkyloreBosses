package net.teamaof.skylorebosses.core.fx;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.registry.SBSounds;

/**
 * Turns Blockbench particle/sound keyframes into in-game effects, and gives server code the same
 * locator positions (so e.g. the Bloom's laser starts at the pupil). Side-agnostic.
 */
public final class AnimFx {
    private AnimFx() {}

    /** World position of a model locator (rest pose) on an entity rendered at {@code scale}. */
    public static Vec3 locator(Entity e, String model, float scale, String locator) {
        float[] p = ModelLocators.get(model, locator);
        if (p == null) return e.position().add(0, e.getBbHeight() * 0.5, 0);
        float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
        Vec3 local = new Vec3(-p[0], p[1], p[2]).scale(scale / 16.0);
        return e.position().add(local.yRot((float) Math.toRadians(180 - yaw)));
    }

    /** Called from GeckoLib particle keyframes (client). */
    public static void keyframeParticle(Entity e, String model, float scale, String effect, String locator) {
        Level level = e.level();
        if (!level.isClientSide) return;
        String name = effect.contains(":") ? effect.substring(effect.indexOf(':') + 1) : effect;
        SimpleParticleType type = SBParticles.get(name);
        if (type == null) return;
        Vec3 at = locator(e, model, scale, locator);
        burst(level, type, name, at, scale);
    }

    /** Client-side burst shaped per effect (count, spread, velocity), mirroring the Snowstorm files. */
    public static void burst(Level level, SimpleParticleType type, String name, Vec3 at, float scale) {
        RandomSource r = level.random;
        Profile pr = Profile.of(name);
        int n = Math.max(1, Math.round(pr.count * Math.min(2.5f, 0.6f + scale * 0.3f)));
        for (int i = 0; i < n; i++) {
            double sx = (r.nextDouble() - 0.5) * pr.spread * scale;
            double sy = (r.nextDouble() - 0.5) * pr.spread * scale;
            double sz = (r.nextDouble() - 0.5) * pr.spread * scale;
            double vx = (r.nextDouble() - 0.5) * pr.speed;
            double vy = pr.up + (r.nextDouble() - 0.5) * pr.speed;
            double vz = (r.nextDouble() - 0.5) * pr.speed;
            level.addParticle(type, at.x + sx, at.y + sy, at.z + sz, vx, vy, vz);
        }
    }

    /** Server-side equivalent (sends to tracking clients). */
    public static void serverBurst(ServerLevel level, SimpleParticleType type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /**
     * Maps a Blockbench sound keyframe id to a registered sound: "matris_calyx:arm.open" becomes
     * "matris_calyx.arm.open" (the keyframe namespace is the boss id); "skylore_bosses:x.y" stays "x.y".
     */
    public static SoundEvent keyframeSoundEvent(String soundId) {
        if (soundId == null || soundId.isEmpty()) return null;
        int c = soundId.indexOf(':');
        if (c < 0) return SBSounds.get(soundId);
        String ns = soundId.substring(0, c), path = soundId.substring(c + 1);
        return SBSounds.get(ns.equals(SkyloreBosses.MOD_ID) ? path : ns + "." + path);
    }

    /** Called from GeckoLib sound keyframes (client). */
    public static void keyframeSound(Entity e, String soundId, float volume) {
        if (!e.level().isClientSide) return;
        SoundEvent s = keyframeSoundEvent(soundId);
        if (s == null) return;
        e.level().playLocalSound(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), s, SoundSource.HOSTILE, volume,
                0.92f + e.level().random.nextFloat() * 0.16f, false);
    }

    /** @param soundId full id without namespace, e.g. "matris_calyx.arm.thunk" */
    public static void play(Level level, Vec3 at, String soundId, float volume, float pitch) {
        SoundEvent s = SBSounds.get(soundId);
        if (s != null) level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, volume, pitch);
    }

    record Profile(int count, double spread, double speed, double up) {
        static Profile of(String name) {
            return switch (name) {
                case "blood_burst" -> new Profile(24, 0.6, 0.5, 0.25);
                case "flesh_chunks" -> new Profile(16, 1.0, 0.6, 0.35);
                case "bile_splash" -> new Profile(22, 0.5, 0.45, 0.2);
                case "bile_drip" -> new Profile(3, 0.3, 0.02, -0.05);
                case "spore_puff" -> new Profile(18, 0.8, 0.15, 0.12);
                case "spore_haze" -> new Profile(10, 2.0, 0.04, 0.03);
                case "root_dust" -> new Profile(24, 2.0, 0.25, 0.05);
                case "nerve_spark" -> new Profile(8, 0.4, 0.4, 0.0);
                case "nerve_pulse" -> new Profile(40, 0.4, 0.9, 0.0);
                case "core_glow" -> new Profile(8, 0.5, 0.05, 0.06);
                case "laser_charge" -> new Profile(30, 2.5, 0.1, 0.0);
                case "laser_beam" -> new Profile(20, 0.4, 0.3, 0.0);
                case "eye_glint" -> new Profile(4, 0.3, 0.01, 0.0);
                case "mucus_string" -> new Profile(6, 0.6, 0.02, -0.03);
                default -> new Profile(6, 0.5, 0.1, 0.0);
            };
        }
    }
}
