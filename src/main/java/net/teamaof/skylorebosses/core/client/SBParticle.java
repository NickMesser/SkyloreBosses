package net.teamaof.skylorebosses.core.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** One particle class, styled per effect to match the Snowstorm files (colours, gravity, life, size, glow). */
public class SBParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final Style style;

    protected SBParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Style style) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.style = style;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.gravity = style.gravity;
        this.friction = style.friction;
        this.lifetime = (int) (style.life * (0.7 + random.nextFloat() * 0.6));
        this.quadSize = style.size * (0.7f + random.nextFloat() * 0.6f);
        this.hasPhysics = style.gravity > 0.3f;
        setColor(style.r0, style.g0, style.b0);
        setAlpha(style.a0);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float f = age / (float) lifetime;
        setColor(Mth.lerp(f, style.r0, style.r1), Mth.lerp(f, style.g0, style.g1), Mth.lerp(f, style.b0, style.b1));
        setAlpha(Mth.lerp(f, style.a0, style.a1));
        if (style.grow != 0) quadSize = Math.max(0.01f, quadSize + style.grow);
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return style.emissive ? 0xF000F0 : super.getLightColor(partialTick);
    }

    public record Style(float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1,
                        float gravity, float friction, int life, float size, float grow, boolean emissive) {
        static Style c(float[] from, float[] to, float gravity, int life, float size, float grow, boolean glow) {
            return new Style(from[0], from[1], from[2], 1f, to[0], to[1], to[2], glow ? 0f : 0.6f, gravity, 0.92f, life, size, grow, glow);
        }

        public static Style of(String name) {
            float[] bile = {0.6f, 0.85f, 0.15f}, bile2 = {0.35f, 0.55f, 0.05f};
            float[] spore = {0.75f, 0.66f, 0.45f}, spore2 = {0.45f, 0.4f, 0.3f};
            float[] nerve = {0.7f, 0.95f, 1f}, nerve2 = {0.2f, 0.5f, 1f};
            float[] blood = {0.55f, 0.05f, 0.12f}, blood2 = {0.25f, 0.02f, 0.05f};
            return switch (name) {
                case "bile_drip" -> c(bile, bile2, 0.5f, 24, 0.12f, 0, false);
                case "bile_splash" -> c(bile, bile2, 0.6f, 16, 0.18f, -0.005f, false);
                case "spore_puff" -> c(spore, spore2, -0.02f, 40, 0.35f, 0.01f, false);
                case "spore_haze" -> c(spore, spore2, -0.01f, 60, 0.6f, 0.012f, false);
                case "nerve_spark" -> c(nerve, nerve2, 0f, 8, 0.12f, -0.008f, true);
                case "nerve_pulse" -> c(nerve, nerve2, 0f, 12, 0.16f, -0.01f, true);
                case "core_glow" -> c(new float[]{1f, 0.55f, 0.75f}, new float[]{1f, 0.2f, 0.5f}, -0.03f, 18, 0.18f, -0.005f, true);
                case "blood_burst" -> c(blood, blood2, 0.8f, 20, 0.15f, 0, false);
                case "flesh_chunks" -> c(new float[]{0.6f, 0.25f, 0.28f}, new float[]{0.35f, 0.12f, 0.16f}, 0.9f, 36, 0.25f, 0, false);
                case "root_dust" -> c(new float[]{0.45f, 0.32f, 0.26f}, new float[]{0.3f, 0.22f, 0.2f}, -0.01f, 30, 0.4f, 0.015f, false);
                case "laser_charge" -> c(new float[]{1f, 0.8f, 0.3f}, new float[]{1f, 0.95f, 0.6f}, 0f, 12, 0.2f, 0, true);
                case "laser_beam" -> c(new float[]{1f, 0.95f, 0.6f}, new float[]{1f, 0.4f, 0.1f}, 0f, 5, 1.1f, -0.1f, true);
                case "eye_glint" -> c(new float[]{1f, 0.95f, 0.7f}, new float[]{1f, 0.7f, 0.3f}, 0f, 16, 0.4f, -0.02f, true);
                case "mucus_string" -> c(new float[]{0.85f, 0.75f, 0.7f}, new float[]{0.7f, 0.6f, 0.55f}, 0.2f, 30, 0.15f, 0, false);
                case "smoke" -> c(new float[]{0.32f, 0.32f, 0.34f}, new float[]{0.18f, 0.18f, 0.19f}, -0.03f, 36, 0.3f, 0.008f, false);
                case "spark" -> c(new float[]{1f, 0.85f, 0.4f}, new float[]{1f, 0.35f, 0.08f}, 0.7f, 10, 0.08f, -0.004f, true);
                case "muzzle_flash" -> c(new float[]{1f, 0.95f, 0.7f}, new float[]{1f, 0.5f, 0.1f}, 0f, 4, 0.7f, -0.12f, true);
                case "grid_arc" -> c(new float[]{0.6f, 0.95f, 1f}, new float[]{0.2f, 0.55f, 1f}, 0f, 6, 0.14f, -0.01f, true);
                case "ember" -> c(new float[]{1f, 0.6f, 0.2f}, new float[]{0.8f, 0.2f, 0.05f}, -0.02f, 24, 0.1f, -0.002f, true);
                case "shield" -> c(new float[]{0.45f, 0.8f, 1f}, new float[]{0.2f, 0.4f, 1f}, 0f, 16, 0.3f, -0.01f, true);
                case "target_mark" -> c(new float[]{1f, 0.15f, 0.1f}, new float[]{0.9f, 0.05f, 0.05f}, 0f, 24, 0.6f, 0, true);
                case "rail_mark" -> c(new float[]{1f, 0.7f, 0.15f}, new float[]{1f, 0.5f, 0.05f}, 0f, 24, 0.6f, 0, true);
                case "red_beam" -> c(new float[]{1f, 0.35f, 0.25f}, new float[]{1f, 0.1f, 0.05f}, 0f, 4, 0.5f, -0.06f, true);
                case "static_dust" -> c(new float[]{0.78f, 0.78f, 0.82f}, new float[]{0.35f, 0.35f, 0.4f}, 0.02f, 30, 0.12f, -0.002f, false);
                case "communion_mote" -> c(new float[]{0.85f, 0.6f, 1f}, new float[]{1f, 0.9f, 0.55f}, -0.02f, 30, 0.14f, -0.003f, true);
                case "beryl_glint" -> c(new float[]{0.7f, 1f, 0.9f}, new float[]{0.3f, 0.85f, 0.7f}, 0f, 12, 0.12f, -0.008f, true);
                case "litany_light" -> c(new float[]{1f, 0.95f, 0.85f}, new float[]{0.75f, 0.55f, 1f}, 0f, 10, 0.2f, -0.015f, true);
                case "packet_spark" -> c(new float[]{1f, 0.72f, 0.2f}, new float[]{1f, 0.4f, 0.05f}, 0.1f, 12, 0.11f, -0.006f, true);
                case "coolant_mist" -> c(new float[]{0.75f, 0.92f, 1f}, new float[]{0.45f, 0.7f, 1f}, -0.01f, 34, 0.26f, 0.006f, false);
                case "ack_glint" -> c(new float[]{0.55f, 1f, 0.6f}, new float[]{0.2f, 0.85f, 0.4f}, 0f, 14, 0.16f, -0.008f, true);
                case "null_beam" -> c(new float[]{0.85f, 1f, 1f}, new float[]{0.25f, 0.85f, 1f}, 0f, 5, 0.45f, -0.05f, true);
                case "hollow_spore" -> c(new float[]{0.72f, 0.62f, 0.82f}, new float[]{0.38f, 0.3f, 0.45f}, 0.01f, 50, 0.1f, 0f, false);
                case "gill_glow" -> c(new float[]{0.78f, 0.55f, 1f}, new float[]{0.45f, 0.2f, 0.85f}, -0.01f, 24, 0.14f, -0.004f, true);
                case "snuff_smoke" -> c(new float[]{0.16f, 0.13f, 0.18f}, new float[]{0.05f, 0.04f, 0.06f}, -0.03f, 34, 0.28f, 0.01f, false);
                case "veil_mist" -> c(new float[]{0.24f, 0.17f, 0.3f}, new float[]{0.1f, 0.06f, 0.14f}, -0.005f, 44, 0.5f, 0.012f, false);
                default -> c(spore, spore2, 0f, 20, 0.2f, 0, false);
            };
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SBParticle(level, x, y, z, vx, vy, vz, sprites, style);
        }
    }
}
