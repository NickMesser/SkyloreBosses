package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import net.teamaof.skylorebosses.core.net.SBNetwork;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.SporeVentBlockEntity;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.projectile.BileGlob;
import net.teamaof.skylorebosses.bosses.matriscalyx.net.MatrisNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/**
 * Pillar 3: attacks are encounter-wide events run by the controller, not melee reach from a hitbox.
 * Each has a warning (subtitle + stinger + visual), an effect, and a cooldown (DESIGN.md section 7).
 */
public final class BodyAttacks {
    public enum Kind {
        PERISTALSIS(3, 700, 60), BILE_RAIN(3, 600, 40), RETINA_FLASH(2, 900, 24), ROOT_GRIP(2, 500, 30),
        SWALLOW(1, 1200, 80), SPORE_EXHALE(2, 800, 40);
        final int weight, cooldown, warn;

        Kind(int weight, int cooldown, int warn) {
            this.weight = weight;
            this.cooldown = cooldown;
            this.warn = warn;
        }

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    private final Map<Kind, Long> readyAt = new EnumMap<>(Kind.class);
    private final List<Thorn> thorns = new ArrayList<>();
    private Kind pending, active, last;
    private int timer = 400, warnLeft, activeLeft;
    private boolean escalated;
    private int[] lanes = new int[0];

    private record Thorn(BlockPos pos, int[] ticks) {}

    public void escalate() { escalated = true; }
    public void reset() { pending = null; active = null; thorns.clear(); timer = 400; escalated = false; readyAt.clear(); }

    /** Debug/command: fire a specific attack now (skips cooldown, keeps the warning). */
    public void force(Kind k, RandomSource random) {
        if (k == Kind.BILE_RAIN) pickLanes(random);
        pending = k;
        warnLeft = k.warn;
    }

    private void pickLanes(RandomSource random) {
        java.util.List<Integer> all = new java.util.ArrayList<>(java.util.List.of(0, 1, 2, 3, 4, 5));
        java.util.Collections.shuffle(all, new java.util.Random(random.nextLong()));
        lanes = all.subList(0, 3).stream().mapToInt(Integer::intValue).toArray();
    }

    public void tick(ServerLevel level, MatrisEncounter enc, List<ServerPlayer> players) {
        tickThorns(level);
        long now = level.getGameTime();
        if (active != null) {
            activeTick(level, enc, players);
            if (--activeLeft <= 0) active = null;
            return;
        }
        if (pending != null) {
            if (--warnLeft <= 0) {
                execute(level, enc, players, pending);
                readyAt.put(pending, now + pending.cooldown);
                last = pending;
                pending = null;
            }
            return;
        }
        if (players.isEmpty() || --timer > 0) return;
        int min = MatrisConfig.BODY_ATTACK_MIN_TICKS.get(), max = Math.max(min, MatrisConfig.BODY_ATTACK_MAX_TICKS.get());
        timer = min + level.random.nextInt(max - min + 1);
        if (escalated) timer = Math.round(timer / 1.3f);
        Kind k = pick(level.random, enc, players, now);
        if (k != null) begin(level, enc, players, k);
    }

    private Kind pick(RandomSource r, MatrisEncounter enc, List<ServerPlayer> players, long now) {
        List<Kind> bag = new ArrayList<>();
        for (Kind k : Kind.values()) {
            if (k == last || readyAt.getOrDefault(k, 0L) > now) continue;
            if (k == Kind.SWALLOW && !escalated) continue;
            if (k == Kind.SPORE_EXHALE && enc.liveVents((ServerLevel) players.get(0).level()) < 3) continue;
            if (k == Kind.ROOT_GRIP && players.stream().noneMatch(Entity::onGround)) continue;
            for (int i = 0; i < k.weight; i++) bag.add(k);
        }
        return bag.isEmpty() ? null : bag.get(r.nextInt(bag.size()));
    }

    private void begin(ServerLevel level, MatrisEncounter enc, List<ServerPlayer> players, Kind k) {
        pending = k;
        warnLeft = k.warn;
        Component msg = Component.translatable("matris_calyx.attack." + k.id() + ".warn").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC);
        for (ServerPlayer p : players) {
            p.displayClientMessage(msg, true);
            AnimFx.play(level, p.position(), "matris_calyx.attack." + k.id() + ".warn", 1.0f, 1f);
        }
        BlockPos o = enc.origin();
        switch (k) {
            case PERISTALSIS -> players.forEach(p -> SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, k.warn));
            case BILE_RAIN -> {
                pickLanes(level.random);
                for (int lane : lanes) laneParticles(level, o, lane, SBParticles.BILE_DRIP.get(), 40);
            }
            case RETINA_FLASH -> AnimFx.serverBurst(level, SBParticles.EYE_GLINT.get(), Vec3.atCenterOf(o.above(40)), 60, 12, 0.05);
            case ROOT_GRIP -> players.stream().filter(Entity::onGround)
                    .forEach(p -> AnimFx.serverBurst(level, SBParticles.ROOT_DUST.get(), p.position(), 30, 1.2, 0.05));
            case SWALLOW -> players.forEach(p -> SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, k.warn));
            case SPORE_EXHALE -> enc.forEachVent(level, v -> v.puff());
        }
    }

    private void execute(ServerLevel level, MatrisEncounter enc, List<ServerPlayer> players, Kind k) {
        switch (k) {
            case PERISTALSIS -> {
                for (ServerPlayer p : players) {
                    if (p.onGround()) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0));
                    Entity v = p.getVehicle();
                    if (v != null) {
                        v.setDeltaMovement(v.getDeltaMovement().add(0, 1.2, 0));
                        v.hurtMarked = true;
                    }
                }
            }
            case BILE_RAIN -> {
                if (lanes.length == 0) pickLanes(level.random);
                active = k;
                activeLeft = 80;
            }
            case RETINA_FLASH -> retinaFlashFrom(level, Vec3.atCenterOf(enc.origin().above(40)), players);
            case ROOT_GRIP -> {
                for (ServerPlayer p : players) {
                    if (!p.onGround()) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    thorns.add(new Thorn(p.blockPosition(), new int[]{100}));
                }
            }
            case SWALLOW -> { active = k; activeLeft = 100; }
            case SPORE_EXHALE -> {
                java.util.Set<ServerPlayer> hit = new java.util.HashSet<>();
                enc.forEachVent(level, v -> {
                    if (v.isBroken()) return;
                    v.exhale(level);
                    for (ServerPlayer p : players) if (p.blockPosition().closerThan(v.getBlockPos(), 16)) hit.add(p);
                });
                hit.forEach(p -> Infection.add(p, 8));
            }
        }
    }

    private void activeTick(ServerLevel level, MatrisEncounter enc, List<ServerPlayer> players) {
        if (active == Kind.BILE_RAIN && activeLeft % 2 == 0) {
            if (lanes.length == 0) return;
            BlockPos o = enc.origin();
            int lane = lanes[level.random.nextInt(lanes.length)];
            Vec3 a = Vec3.atCenterOf(o), b = Vec3.atCenterOf(ArenaLayout.satellite(o, lane));
            Vec3 p = a.lerp(b, 0.15 + level.random.nextDouble() * 0.75).add(level.random.nextGaussian() * 6, 45, level.random.nextGaussian() * 6);
            level.addFreshEntity(new BileGlob(level, null, p, new Vec3(0, -0.4, 0), 5f).rain());
        } else if (active == Kind.SWALLOW) {
            for (ServerPlayer p : players) {
                Entity m = p.getVehicle() != null ? p.getVehicle() : p;
                m.setDeltaMovement(m.getDeltaMovement().add(0, -0.08, 0));
                m.hurtMarked = true;
                if (activeLeft % 10 == 0) AnimFx.serverBurst(level, SBParticles.SPORE_HAZE.get(), p.position().add(0, 3, 0), 6, 2, 0.1);
            }
        }
    }

    private void tickThorns(ServerLevel level) {
        for (Iterator<Thorn> it = thorns.iterator(); it.hasNext(); ) {
            Thorn t = it.next();
            if (--t.ticks[0] <= 0) { it.remove(); continue; }
            if (t.ticks[0] % 5 == 0) AnimFx.serverBurst(level, SBParticles.ROOT_DUST.get(), Vec3.atBottomCenterOf(t.pos()), 3, 0.8, 0.01);
            if (t.ticks[0] % 10 == 0) {
                for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(t.pos()).inflate(1.5, 1, 1.5))) {
                    p.hurt(level.damageSources().cactus(), 2f);
                }
            }
        }
    }

    private static void laneParticles(ServerLevel level, BlockPos o, int lane, net.minecraft.core.particles.SimpleParticleType type, int n) {
        Vec3 a = Vec3.atCenterOf(o), b = Vec3.atCenterOf(ArenaLayout.satellite(o, lane));
        for (int i = 0; i < n; i++) {
            Vec3 p = a.lerp(b, i / (double) n).add(0, 40, 0);
            level.sendParticles(type, p.x, p.y, p.z, 2, 1.5, 0.5, 1.5, 0.0);
        }
    }

    /**
     * Retina Flash: players looking toward the eye with line of sight are blinded; everyone else only gets
     * a white vignette. Shared by the body attack and the Bloom.
     */
    public static void retinaFlashFrom(ServerLevel level, Vec3 eye, List<ServerPlayer> players) {
        AnimFx.play(level, eye, "matris_calyx.bloom.retina_flash", 6f, 1f);
        for (ServerPlayer p : players) {
            Vec3 to = eye.subtract(p.getEyePosition()).normalize();
            boolean looking = p.getLookAngle().dot(to) > 0.6;
            boolean los = level.clip(new ClipContext(p.getEyePosition(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                    .getType() == HitResult.Type.MISS || p.getEyePosition().distanceTo(eye) < 12;
            if (looking && los) {
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                SBNetwork.sendScreenFx(p, SBNetwork.FX_WHITEOUT, 30);
            } else {
                SBNetwork.sendScreenFx(p, SBNetwork.FX_WHITEOUT, 12);
            }
        }
    }
}
