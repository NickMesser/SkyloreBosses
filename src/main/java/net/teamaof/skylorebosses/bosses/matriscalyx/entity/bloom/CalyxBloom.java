package net.teamaof.skylorebosses.bosses.matriscalyx.entity.bloom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.BodyAttacks;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.MatrisEncounter;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Phase 3. A stationary laser turret the size of a building: aim, charge, sweep, rest. The rest is the
 * DPS window (eye takes 1.5x). Her own flesh (tag matris_calyx:laser_proof) is cover; anything else
 * the beam touches stops it for one sweep and is then burned away.
 */
public class CalyxBloom extends Monster implements GeoEntity {
    public static final String MODEL = "calyx_bloom";
    public static final float SCALE = 4.0f;
    public static final int EMERGE = 0, AIM = 1, CHARGE = 2, FIRE = 3, REST = 4;
    public static final TagKey<Block> LASER_PROOF = TagKey.create(Registries.BLOCK, SkyloreBosses.id("matris_calyx/laser_proof"));
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(CalyxBloom.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BEAM_PITCH = SynchedEntityData.defineId(CalyxBloom.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BEAM_LEN = SynchedEntityData.defineId(CalyxBloom.class, EntityDataSerializers.FLOAT);
    private static final double RANGE = 120;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int phaseTicks = 160;
    private int cycle;
    private float aimYaw, aimPitch;
    private float nextStaggerAt = 0.85f;
    private final Set<BlockPos> scorched = new HashSet<>();

    public CalyxBloom(EntityType<? extends CalyxBloom> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        setPersistenceRequired();
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 128)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ARMOR, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(PHASE, EMERGE);
        b.define(BEAM_PITCH, 0f);
        b.define(BEAM_LEN, 0f);
    }

    public int phase() { return entityData.get(PHASE); }
    public float beamPitch() { return entityData.get(BEAM_PITCH); }
    public float beamLength() { return entityData.get(BEAM_LEN); }

    public void setMaxHp(float hp) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        setHealth(hp);
    }

    public Vec3 eye() {
        return AnimFx.locator(this, MODEL, SCALE, "laser");
    }

    public Vec3 beamDir() {
        return Vec3.directionFromRotation(aimPitch, aimYaw);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) {
            clientBeam();
            return;
        }
        if (isDeadOrDying()) return;
        if (tickCount == 1 && phase() == EMERGE) triggerAnim("main", "emerge");
        Player target = pickTarget();
        if (target != null && phase() != FIRE) turnToward(target.getEyePosition(), 2.0f);
        if (target != null && phase() == FIRE) turnToward(target.getEyePosition(), MatrisConfig.LASER_TURN_DEG_PER_TICK.get().floatValue());
        setYRot(aimYaw);
        yBodyRot = aimYaw;
        yHeadRot = aimYaw;
        entityData.set(BEAM_PITCH, aimPitch);

        if (phase() == FIRE) fireTick();
        if (--phaseTicks > 0) return;
        boolean enraged = getHealth() < getMaxHealth() * 0.2f;
        switch (phase()) {
            case EMERGE, REST -> {
                cycle++;
                if (getHealth() < getMaxHealth() * 0.5f && cycle % 3 == 0 && level() instanceof ServerLevel sl) {
                    triggerAnim("main", "retina_flash");
                    BodyAttacks.retinaFlashFrom(sl, eye(), participants(sl));
                }
                setPhase(AIM, 30);
            }
            case AIM -> {
                setPhase(CHARGE, 40);
                triggerAnim("main", "laser_charge");
            }
            case CHARGE -> {
                scorched.clear();
                setPhase(FIRE, enraged ? 120 : 60);
            }
            case FIRE -> {
                entityData.set(BEAM_LEN, 0f);
                for (BlockPos p : scorched) level().destroyBlock(p, false);
                scorched.clear();
                setPhase(REST, enraged ? 60 : 80);
            }
            default -> setPhase(AIM, 30);
        }
    }

    private void setPhase(int p, int ticks) {
        entityData.set(PHASE, p);
        phaseTicks = ticks;
    }

    private void turnToward(Vec3 t, float maxDeg) {
        Vec3 d = t.subtract(eye());
        float wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float wantPitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        aimYaw = Mth.approachDegrees(aimYaw, wantYaw, maxDeg);
        aimPitch = Mth.approach(aimPitch, Mth.clamp(wantPitch, -70, 70), maxDeg);
    }

    private List<ServerPlayer> participants(ServerLevel sl) {
        return sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(RANGE + 40), p -> p.isAlive() && !p.isSpectator());
    }

    private Player pickTarget() {
        if (!(level() instanceof ServerLevel sl)) return null;
        Vec3 e = eye();
        List<ServerPlayer> ps = new ArrayList<>(participants(sl));
        ps.removeIf(p -> p.isCreative());
        return ps.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(e) * (hasLineOfSight(p) ? 1 : 4))).orElse(null);
    }

    private void fireTick() {
        Vec3 from = eye();
        Vec3 to = from.add(beamDir().scale(RANGE));
        BlockHitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
        entityData.set(BEAM_LEN, (float) from.distanceTo(end));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockState s = level().getBlockState(hit.getBlockPos());
            if (!s.is(LASER_PROOF) && s.getDestroySpeed(level(), hit.getBlockPos()) >= 0) scorched.add(hit.getBlockPos());
        }
        ServerLevel sl = (ServerLevel) level();
        if (tickCount % 2 == 0) {
            sl.sendParticles(SBParticles.LASER_BEAM.get(), end.x, end.y, end.z, 6, 0.4, 0.4, 0.4, 0.05);
            sl.sendParticles(SBParticles.ROOT_DUST.get(), end.x, end.y, end.z, 2, 0.6, 0.2, 0.6, 0.02);
        }
        if (tickCount % 5 != 0) return;
        AABB sweep = new AABB(from, end).inflate(1.5);
        for (Player p : level().getEntitiesOfClass(Player.class, sweep, p -> p.isAlive() && !p.isSpectator())) {
            Optional<Vec3> clip = p.getBoundingBox().inflate(1.2).clip(from, end);
            if (clip.isPresent() || p.getBoundingBox().inflate(1.2).contains(from)) {
                p.hurt(damageSources().indirectMagic(this, this), MatrisConfig.LASER_DAMAGE.get().floatValue());
                Infection.add(p, 1);
            }
        }
    }

    /** Client: draw the beam as a dense particle line along the synced aim. */
    private void clientBeam() {
        float len = beamLength();
        if (len <= 0.5f || phase() != FIRE) return;
        Vec3 from = eye();
        Vec3 dir = Vec3.directionFromRotation(beamPitch(), yBodyRot);
        for (double d = 0; d < len; d += 1.2) {
            Vec3 p = from.add(dir.scale(d));
            level().addParticle(SBParticles.LASER_BEAM.get(), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (phase() == EMERGE) return false;
        Vec3 eyeCenter = eye().subtract(beamDir().scale(2));
        Entity direct = source.getDirectEntity();
        boolean eyeHit = direct != null && (direct.position().distanceTo(eyeCenter) < 10
                || direct instanceof Player pl && pl.getEyePosition().distanceTo(eyeCenter) < 12);
        float mult = (eyeHit ? 1f : 0.25f) * (phase() == REST ? 1.5f : 1f);
        boolean r = super.hurt(source, amount * mult);
        if (r && !level().isClientSide && getHealth() > 0 && getHealth() / getMaxHealth() < nextStaggerAt) {
            nextStaggerAt -= 0.15f;
            triggerAnim("main", "stagger");
            if (phase() == CHARGE) setPhase(REST, 60);
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            entityData.set(BEAM_LEN, 0f);
            triggerAnim("main", "death");
            MatrisEncounter.onBloomDied(sl, this, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= 160 && !level().isClientSide) {
            if (level() instanceof ServerLevel sl) AnimFx.serverBurst(sl, SBParticles.FLESH_CHUNKS.get(), eye(), 80, 6, 0.4);
            remove(RemovalReason.KILLED);
        }
    }

    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 400 * 400; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(30, 20, 30); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase());
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("Cycle", cycle);
        tag.putFloat("Stagger", nextStaggerAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // a mid-laser Bloom resumes at REST after a reload
        int p = tag.getInt("Phase");
        setPhase(p == EMERGE ? EMERGE : REST, p == EMERGE ? tag.getInt("PhaseTicks") : 60);
        cycle = tag.getInt("Cycle");
        if (tag.contains("Stagger")) nextStaggerAt = tag.getFloat("Stagger");
    }

    private static String a(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CalyxBloom> c = new AnimationController<>(this, "main", 5, s -> {
            if (phase() == FIRE) return s.setAndContinue(RawAnimation.begin().thenLoop(a("laser_fire")));
            return s.setAndContinue(RawAnimation.begin().thenLoop(a("idle")));
        });
        c.triggerableAnim("emerge", RawAnimation.begin().thenPlay(a("emerge")));
        c.triggerableAnim("laser_charge", RawAnimation.begin().thenPlay(a("laser_charge")));
        c.triggerableAnim("retina_flash", RawAnimation.begin().thenPlay(a("retina_flash")));
        c.triggerableAnim("stagger", RawAnimation.begin().thenPlay(a("stagger")));
        c.triggerableAnim("death", RawAnimation.begin().thenPlayAndHold(a("death")));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, MODEL, SCALE, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        c.setSoundKeyframeHandler(e -> AnimFx.keyframeSound(this, e.getKeyframeData().getSound(), 8.0f));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
