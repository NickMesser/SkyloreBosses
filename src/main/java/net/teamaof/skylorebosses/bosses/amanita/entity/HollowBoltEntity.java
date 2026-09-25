package net.teamaof.skylorebosses.bosses.amanita.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaConfig;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollow;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollows;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Lights;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaEntities;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * hollow_bolt (DESIGN.md §9): a straight shadow bolt. On a player: damage plus a short Darkness. It collides with block
 * outlines, so it strikes torches and lanterns too, and a light it strikes is snuffed. Cover (the gill shelves, the
 * columns) stops it.
 */
public class HollowBoltEntity extends Projectile implements GeoEntity {
    public static final double SPEED = 1.0;
    public static final String MODEL = "hollow_bolt";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int life = 60;

    public HollowBoltEntity(EntityType<? extends HollowBoltEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static HollowBoltEntity shoot(Level level, Entity owner, Vec3 from, Vec3 vel) {
        HollowBoltEntity o = new HollowBoltEntity(AmanitaEntities.HOLLOW_BOLT.get(), level);
        o.setOwner(owner);
        o.setPos(from.x, from.y, from.z);
        o.setDeltaMovement(vel);
        o.alignToVelocity();
        return o;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (level() instanceof ServerLevel sl) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity, ClipContext.Block.OUTLINE);
            if (hit.getType() != HitResult.Type.MISS) {
                if (hit instanceof EntityHitResult eh) strike(sl, eh.getEntity());
                else if (hit instanceof BlockHitResult bh) strikeBlock(sl, bh.getBlockPos());
                burst(sl, hit.getLocation());
                return;
            }
            if (tickCount > life) {
                burst(sl, position());
                return;
            }
        } else {
            Vec3 p = position();
            level().addParticle(SBParticles.get("veil_mist"), p.x, p.y, p.z, 0, 0, 0);
            if (tickCount % 2 == 0) level().addParticle(SBParticles.get("gill_glow"), p.x, p.y, p.z, 0, 0, 0);
        }
        setPos(position().add(v));
        alignToVelocity();
    }

    private void strike(ServerLevel sl, Entity e) {
        e.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity le ? le : null), AmanitaConfig.BOLT_DAMAGE.get().floatValue());
        if (e instanceof Player p) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
    }

    /** A bolt that strikes a light snuffs it (through the owner's hollow, so it is counted and never leaves the hollow). */
    private void strikeBlock(ServerLevel sl, BlockPos p) {
        if (!(getOwner() instanceof AmanitaEntity a) || a.hollowOrigin() == null) return;
        if (!Lights.snuffable(sl, p, sl.getBlockState(p))) return;
        Hollow h = Hollows.get(sl).at(a.hollowOrigin());
        if (h != null) h.snuff(sl, p.getCenter(), 0.5, "hollow_bolt");
    }

    private void alignToVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 1e-6) return;
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    private void burst(ServerLevel sl, Vec3 at) {
        AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), at, 10, 0.2, 0.05);
        AnimFx.play(sl, at, "amanita.bolt.impact", 2.5f, 0.9f + sl.random.nextFloat() * 0.2f);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && !(e instanceof AmanitaEntity) && !(e instanceof HollowBoltEntity) && !(e instanceof LampEaterEntity)
                && !(e instanceof HollowSpawnEntity) && !e.isSpectator();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 96 * 96; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getInt("Life");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation." + MODEL + ".fly"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
