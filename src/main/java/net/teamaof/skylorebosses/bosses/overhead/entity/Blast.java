package net.teamaof.skylorebosses.bosses.overhead.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.overhead.OverheadConfig;
import net.teamaof.skylorebosses.bosses.overhead.encounter.OverheadYards;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Yard;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;

/**
 * Overhead's own explosions. Never a vanilla explosion: terrain is untouched except yard cover
 * ({@link #BREAKABLE_COVER}), cover between the blast and a target cuts damage to 25%, and pylons in range take
 * friendly fire (DESIGN.md §7).
 */
public final class Blast {
    public static final TagKey<Block> BREAKABLE_COVER = TagKey.create(Registries.BLOCK, SkyloreBosses.id("overhead/breakable_cover"));
    public static final TagKey<EntityType<?>> FLIGHT_VEHICLES = TagKey.create(Registries.ENTITY_TYPE, SkyloreBosses.id("overhead/flight_vehicles"));

    private Blast() {}

    /**
     * @param radius damage falls off linearly to 30% at the edge
     * @param coverBreak blocks of cover within this radius are destroyed (0 = none)
     */
    public static void detonate(ServerLevel level, @Nullable Entity owner, Vec3 at, double radius, float damage, double coverBreak, String sound) {
        level.sendParticles(radius >= 3 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        AnimFx.serverBurst(level, SBParticles.SMOKE.get(), at, 12, radius * 0.4, 0.05);
        AnimFx.serverBurst(level, SBParticles.SPARK.get(), at, 16, 0.4, 0.4);
        AnimFx.play(level, at, sound, (float) Math.max(2, radius), 0.9f + level.random.nextFloat() * 0.2f);
        DamageSource src = level.damageSources().explosion(owner, owner);
        for (Entity e : level.getEntities(owner, new AABB(at, at).inflate(radius + 1))) {
            if (e instanceof OverheadEntity || e instanceof Ordnance || e.isSpectator()) continue;
            boolean vehicle = e.getType().is(FLIGHT_VEHICLES);
            if (!(e instanceof LivingEntity) && !vehicle) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(at);
            if (d > radius + e.getBbWidth() * 0.5) continue;
            float f = (float) (1 - 0.7 * Math.min(1, d / radius));
            if (covered(level, at.add(0, 0.6, 0), c)) f *= 0.25f;
            float dmg = damage * f * (vehicle ? 2f : 1f);
            if (dmg <= 0.1f) continue;
            e.hurt(src, dmg);
            Vec3 push = c.subtract(at).normalize().scale(0.6 * f);
            e.push(push.x, 0.25 * f, push.z);
            e.hurtMarked = true;
        }
        if (coverBreak > 0) breakCover(level, at, coverBreak);
        Yard yard = OverheadYards.get(level).nearest(BlockPos.containing(at), 48);
        if (yard != null) yard.blastPylons(level, at, radius + 1, damage * OverheadConfig.PYLON_FRIENDLY_FIRE.get().floatValue(), null);
    }

    public static boolean covered(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.BLOCK;
    }

    /** Cover-only block damage. The yard reprints destroyed crates (Yard#tickCover). */
    public static void breakCover(ServerLevel level, Vec3 at, double r) {
        BlockPos c = BlockPos.containing(at);
        int ri = (int) Math.ceil(r);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -ri, -ri), c.offset(ri, ri, ri))) {
            if (p.distToCenterSqr(at) > r * r) continue;
            if (level.getBlockState(p).is(BREAKABLE_COVER)) {
                level.levelEvent(2001, p, Block.getId(level.getBlockState(p)));
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /** Anything that can be a target in a line-of-sight test: players and vehicles. */
    public static boolean canTarget(Entity e) {
        return e instanceof Player p ? p.isAlive() && !p.isSpectator() && !p.isCreative() : e.isAlive();
    }
}
