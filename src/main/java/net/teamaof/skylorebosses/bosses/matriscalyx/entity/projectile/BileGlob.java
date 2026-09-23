package net.teamaof.skylorebosses.bosses.matriscalyx.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.AbstractRootedArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisEntities;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Spitting Arm volley and Bile Rain projectile: damage + infection, leaves a slowing bile puddle. */
public class BileGlob extends ThrowableProjectile implements GeoEntity {
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.bile_glob.fly");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float damage = 6f;
    private double gravity = 0.02;

    public BileGlob(EntityType<? extends BileGlob> type, Level level) {
        super(type, level);
    }

    public BileGlob(Level level, @Nullable LivingEntity owner, Vec3 pos, Vec3 velocity, float damage) {
        super(MatrisEntities.BILE_GLOB.get(), level);
        setOwner(owner);
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(velocity);
        this.damage = damage;
    }

    /** Bile Rain globs fall faster and hit a little softer. */
    public BileGlob rain() {
        gravity = 0.06;
        damage = 5f;
        return this;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    protected double getDefaultGravity() {
        return gravity;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0) {
            level().addParticle(SBParticles.BILE_DRIP.get(), getX(), getY() + 0.3, getZ(), 0, -0.02, 0);
        }
        if (!level().isClientSide && tickCount > 200) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof AbstractRootedArm) && !(e instanceof BileGlob);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity e = hit.getEntity();
        e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), damage);
        if (e instanceof Player p) Infection.add(p, 4);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel sl) {
            AnimFx.serverBurst(sl, SBParticles.BILE_SPLASH.get(), position(), 20, 0.4, 0.25);
            AnimFx.play(sl, position(), "matris_calyx.bile.splat", 1.2f, 0.9f + random.nextFloat() * 0.2f);
            if (result.getType() == HitResult.Type.BLOCK) {
                AreaEffectCloud cloud = new AreaEffectCloud(sl, getX(), getY(), getZ());
                cloud.setRadius(2.2f);
                cloud.setDuration(60);
                cloud.setRadiusPerTick(-0.01f);
                cloud.setParticle(SBParticles.BILE_DRIP.get());
                cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                if (getOwner() instanceof LivingEntity le) cloud.setOwner(le);
                sl.addFreshEntity(cloud);
            }
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putDouble("Grav", gravity);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        gravity = tag.getDouble("Grav");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(FLY))
                .setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, "bile_glob", 2.0f,
                        e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
