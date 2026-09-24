package net.teamaof.skylorebosses.bosses.overhead.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.overhead.OverheadConfig;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadEntities;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Every round Overhead fires (DESIGN.md §9 projectile specs). Own integration with no drag, so the howitzer solve
 * "land here in T ticks" is exact: x(T) = x0 + v*T, y(T) = y0 + vy*T - g*T(T-1)/2.
 */
public class Ordnance extends Projectile implements GeoEntity {
    public enum Kind { SHELL, MISSILE, BOLT, FLARE, FLAK }

    public static final double SHELL_GRAVITY = 0.05;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Ordnance.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double gravity;
    private int life = 200;
    private int fuse = -1;
    @Nullable private UUID target;
    private float turnDeg;
    private double speed;
    @Nullable private Vec3 mark;

    public Ordnance(EntityType<? extends Ordnance> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    private static Ordnance make(Level level, Entity owner, Kind k, Vec3 pos, Vec3 vel) {
        Ordnance o = new Ordnance(OverheadEntities.ORDNANCE.get(), level);
        o.setOwner(owner);
        o.entityData.set(KIND, k.ordinal());
        o.setPos(pos.x, pos.y, pos.z);
        o.setDeltaMovement(vel);
        o.alignToVelocity();
        return o;
    }

    /** Ballistic howitzer shell landing on {@code at} after exactly {@code ticks}; draws its impact ring while in flight. */
    public static Ordnance shell(Level level, Entity owner, Vec3 from, Vec3 at, int ticks) {
        double g = SHELL_GRAVITY;
        Vec3 d = at.subtract(from);
        Vec3 v = new Vec3(d.x / ticks, (d.y + g * ticks * (ticks - 1) / 2.0) / ticks, d.z / ticks);
        Ordnance o = make(level, owner, Kind.SHELL, from, v);
        o.gravity = g;
        o.mark = at;
        o.life = ticks + 40;
        return o;
    }

    public static Ordnance missile(Level level, Entity owner, Vec3 from, Vec3 vel, @Nullable Entity target, float turnDeg) {
        Ordnance o = make(level, owner, Kind.MISSILE, from, vel);
        o.target = target == null ? null : target.getUUID();
        o.turnDeg = turnDeg;
        o.speed = 0.9;
        o.life = 100;
        return o;
    }

    public static Ordnance bolt(Level level, Entity owner, Vec3 from, Vec3 vel) {
        Ordnance o = make(level, owner, Kind.BOLT, from, vel);
        o.life = 40;
        return o;
    }

    public static Ordnance flare(Level level, Entity owner, Vec3 from, Vec3 vel, int fuse) {
        Ordnance o = make(level, owner, Kind.FLARE, from, vel);
        o.gravity = 0.03;
        o.fuse = fuse;
        o.life = fuse + 5;
        return o;
    }

    public static Ordnance flak(Level level, Entity owner, Vec3 from, Vec3 vel, @Nullable Entity target, int fuse) {
        Ordnance o = make(level, owner, Kind.FLAK, from, vel);
        o.target = target == null ? null : target.getUUID();
        o.fuse = fuse;
        o.life = fuse + 5;
        return o;
    }

    public Kind kind() {
        int k = entityData.get(KIND);
        return k >= 0 && k < Kind.values().length ? Kind.values()[k] : Kind.SHELL;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (level() instanceof ServerLevel sl) {
            if (mark != null && tickCount % 4 == 0) Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), mark, 4, 14);
            Entity t = target == null ? null : sl.getEntity(target);
            if (kind() == Kind.MISSILE && tickCount > 10 && t != null && t.isAlive()) v = steer(v, t.getBoundingBox().getCenter());
            if (kind() == Kind.FLAK && t != null && t.getBoundingBox().getCenter().distanceTo(position()) < 3) {
                explode(sl, position());
                return;
            }
            if (fuse >= 0 && tickCount >= fuse) {
                explode(sl, position());
                return;
            }
            setDeltaMovement(v);
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                if (hit instanceof EntityHitResult eh && kind() == Kind.BOLT) {
                    eh.getEntity().hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null),
                            OverheadConfig.BOLT_DAMAGE.get().floatValue());
                }
                explode(sl, hit.getLocation());
                return;
            }
            if (tickCount > life) {
                explode(sl, position());
                return;
            }
        } else {
            trail();
        }
        setPos(position().add(v));
        setDeltaMovement(v.add(0, -gravity, 0));
        alignToVelocity();
    }

    private Vec3 steer(Vec3 v, Vec3 to) {
        Vec3 want = to.subtract(position()).normalize();
        Vec3 cur = v.normalize();
        double ang = Math.acos(Mth.clamp(cur.dot(want), -1, 1));
        double max = Math.toRadians(turnDeg);
        if (ang <= max || ang < 1e-4) return want.scale(speed);
        // rotate cur toward want by max radians (slerp)
        double s = Math.sin(ang);
        Vec3 dir = cur.scale(Math.sin(ang - max) / s).add(want.scale(Math.sin(max) / s)).normalize();
        return dir.scale(speed);
    }

    private void alignToVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 1e-6) return;
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    private void trail() {
        Vec3 p = position();
        switch (kind()) {
            case SHELL, MISSILE -> level().addParticle(SBParticles.SMOKE.get(), p.x, p.y, p.z, 0, 0.01, 0);
            case FLARE -> {
                level().addParticle(SBParticles.EMBER.get(), p.x, p.y, p.z, 0, 0, 0);
                level().addParticle(SBParticles.TARGET_MARK.get(), p.x, p.y, p.z, 0, 0, 0);
            }
            case BOLT -> level().addParticle(SBParticles.EMBER.get(), p.x, p.y, p.z, 0, 0, 0);
            case FLAK -> level().addParticle(SBParticles.SPARK.get(), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    private void explode(ServerLevel sl, Vec3 at) {
        Entity owner = getOwner();
        switch (kind()) {
            case SHELL -> Blast.detonate(sl, owner, at, 4.0, OverheadConfig.HOWITZER_DAMAGE.get().floatValue(), 2.5, "overhead.shell.impact");
            case MISSILE -> Blast.detonate(sl, owner, at, 2.2, OverheadConfig.MISSILE_DAMAGE.get().floatValue(), 1.5, "overhead.missile.impact");
            case FLAK -> Blast.detonate(sl, owner, at, 3.0, OverheadConfig.FLAK_DAMAGE.get().floatValue(), 0, "overhead.flak.burst");
            case BOLT -> AnimFx.serverBurst(sl, SBParticles.SPARK.get(), at, 6, 0.1, 0.2);
            case FLARE -> {
                if (owner instanceof OverheadEntity oh) oh.flareBurst(sl, at);
            }
        }
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof OverheadEntity) && !(e instanceof Ordnance) && !e.isSpectator();
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", entityData.get(KIND));
        tag.putDouble("Grav", gravity);
        tag.putInt("Life", life);
        tag.putInt("Fuse", fuse);
        tag.putFloat("Turn", turnDeg);
        tag.putDouble("Speed", speed);
        if (target != null) tag.putUUID("Target", target);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("Kind"));
        gravity = tag.getDouble("Grav");
        life = tag.getInt("Life");
        fuse = tag.getInt("Fuse");
        turnDeg = tag.getFloat("Turn");
        speed = tag.getDouble("Speed");
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    }

    public String model() { return kind() == Kind.MISSILE ? "seeker_missile" : "howitzer_shell"; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation." + model() + ".fly")))
                .setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, model(), 1.3f, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
