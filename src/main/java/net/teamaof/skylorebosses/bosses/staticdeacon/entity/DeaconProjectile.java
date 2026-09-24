package net.teamaof.skylorebosses.bosses.staticdeacon.entity;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.staticdeacon.DeaconConfig;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconEntities;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Deacon's projectiles (DESIGN.md §9): the straight static bolt and the slow homing shard. Both inflict "static"
 * (Mining Fatigue I) on a hit, so a player stripping flagstones under fire is slowed. Shards can be struck out of the
 * air by any player hit and shatter on any block (pillars are the intended cover).
 */
public class DeaconProjectile extends Projectile implements GeoEntity {
    public enum Kind { BOLT, SHARD }

    public static final double BOLT_SPEED = 1.1, SHARD_SPEED = 0.35;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(DeaconProjectile.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life = 60;
    @Nullable private UUID target;
    private float turnDeg;

    public DeaconProjectile(EntityType<? extends DeaconProjectile> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    private static DeaconProjectile make(Level level, Entity owner, Kind k, Vec3 pos, Vec3 vel) {
        DeaconProjectile o = new DeaconProjectile(DeaconEntities.PROJECTILE.get(), level);
        o.setOwner(owner);
        o.entityData.set(KIND, k.ordinal());
        o.setPos(pos.x, pos.y, pos.z);
        o.setDeltaMovement(vel);
        o.alignToVelocity();
        return o;
    }

    public static DeaconProjectile bolt(Level level, Entity owner, Vec3 from, Vec3 vel) {
        DeaconProjectile o = make(level, owner, Kind.BOLT, from, vel);
        o.life = 60;
        return o;
    }

    public static DeaconProjectile shard(Level level, Entity owner, Vec3 from, Vec3 vel, @Nullable Entity target, float turnDeg) {
        DeaconProjectile o = make(level, owner, Kind.SHARD, from, vel);
        o.target = target == null ? null : target.getUUID();
        o.turnDeg = turnDeg;
        o.life = 200;
        return o;
    }

    public Kind kind() {
        int k = entityData.get(KIND);
        return k >= 0 && k < Kind.values().length ? Kind.values()[k] : Kind.BOLT;
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
            Entity t = target == null ? null : sl.getEntity(target);
            // shards drift up out of the halo for 8 ticks, then hunt
            if (kind() == Kind.SHARD && tickCount > 8 && t != null && t.isAlive()) v = steer(v, t.getBoundingBox().getCenter());
            setDeltaMovement(v);
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                if (hit instanceof EntityHitResult eh) strike(sl, eh.getEntity());
                shatter(sl, hit.getLocation());
                return;
            }
            if (tickCount > life) {
                shatter(sl, position());
                return;
            }
        } else {
            trail();
        }
        setPos(position().add(v));
        alignToVelocity();
    }

    private void strike(ServerLevel sl, Entity e) {
        float dmg = (kind() == Kind.BOLT ? DeaconConfig.BOLT_DAMAGE : DeaconConfig.SHARD_DAMAGE).get().floatValue();
        e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), dmg);
        if (e instanceof Player p && getOwner() instanceof StaticDeaconEntity d) d.staticDebuff(p);
    }

    private Vec3 steer(Vec3 v, Vec3 to) {
        Vec3 want = to.subtract(position()).normalize();
        Vec3 cur = v.lengthSqr() < 1e-6 ? want : v.normalize();
        double ang = Math.acos(Mth.clamp(cur.dot(want), -1, 1));
        double max = Math.toRadians(turnDeg);
        if (ang <= max || ang < 1e-4) return want.scale(SHARD_SPEED);
        double s = Math.sin(ang);
        Vec3 dir = cur.scale(Math.sin(ang - max) / s).add(want.scale(Math.sin(max) / s)).normalize();
        return dir.scale(SHARD_SPEED);
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
        level().addParticle(SBParticles.get(kind() == Kind.BOLT ? "beryl_glint" : "static_dust"), p.x, p.y, p.z, 0, 0, 0);
    }

    private void shatter(ServerLevel sl, Vec3 at) {
        AnimFx.serverBurst(sl, SBParticles.get("beryl_glint"), at, kind() == Kind.BOLT ? 8 : 14, 0.2, 0.2);
        AnimFx.play(sl, at, kind() == Kind.BOLT ? "static_deacon.bolt.impact" : "static_deacon.shard.break", 2.5f, 0.9f + sl.random.nextFloat() * 0.2f);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof StaticDeaconEntity) && !(e instanceof DeaconProjectile) && !e.isSpectator();
    }

    /** Shards are clearable: any hit from a player breaks one. Bolts are too fast to swat. */
    @Override
    public boolean isPickable() { return kind() == Kind.SHARD; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (kind() != Kind.SHARD || isRemoved()) return false;
        if (level() instanceof ServerLevel sl && source.getEntity() instanceof Player) shatter(sl, position());
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 96 * 96; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", entityData.get(KIND));
        tag.putInt("Life", life);
        tag.putFloat("Turn", turnDeg);
        if (target != null) tag.putUUID("Target", target);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(KIND, tag.getInt("Kind"));
        life = tag.getInt("Life");
        turnDeg = tag.getFloat("Turn");
        target = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    }

    public String model() { return kind() == Kind.BOLT ? "static_bolt" : "homing_shard"; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation." + model() + ".fly")))
                .setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, model(), 1f, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
