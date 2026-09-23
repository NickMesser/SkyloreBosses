package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.MatrisEncounter;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A rooted appendage of Matris. Pillar 4: it ignores damage except during a short telegraphed open window.
 * Pillar 2: it never leaves its island (anchor leash). Attacks are subclass behaviours driven from
 * {@link #tickBehaviour()}; animations are GeckoLib triggerables named like the Blockbench animations.
 */
public abstract class AbstractRootedArm extends Monster implements GeoEntity {
    public static final int CLOSED = 0, WARN = 1, OPEN = 2;
    private static final EntityDataAccessor<Integer> WINDOW = SynchedEntityData.defineId(AbstractRootedArm.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(AbstractRootedArm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BUFFED = SynchedEntityData.defineId(AbstractRootedArm.class, EntityDataSerializers.BOOLEAN);

    public final ArmType armType;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable protected BlockPos anchor;
    @Nullable private BlockPos encounterOrigin;
    private int windowTimer = 120;
    /** Multiplies the closed period (0.8 after the Nerve Arm dies). */
    private float cadence = 1.0f;
    protected int busyTicks;
    private long buffUntil;
    protected int attackCooldown = 60;

    protected AbstractRootedArm(EntityType<? extends AbstractRootedArm> type, Level level, ArmType armType) {
        super(type, level);
        this.armType = armType;
        this.xpReward = 60;
        this.setPersistenceRequired();
        this.windowTimer = 100 + this.random.nextInt(160);
    }

    public static AttributeSupplier.Builder createAttributes(ArmType t) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, t.maxHp)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 72.0)
                .add(Attributes.MOVEMENT_SPEED, t == ArmType.CHARGING ? 0.3 : 0.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(WINDOW, CLOSED);
        b.define(ACTION, 0);
        b.define(BUFFED, false);
    }

    @Override
    protected void registerGoals() {
        // targeting is done in retarget(): she senses through her own body, so no line-of-sight requirement
    }

    /** Nearest valid player within follow range (keeps the current target while it stays valid). */
    protected void retarget() {
        double range = getAttributeValue(Attributes.FOLLOW_RANGE);
        LivingEntity cur = getTarget();
        if (cur != null && cur.isAlive() && !(cur instanceof Player p && (p.isCreative() || p.isSpectator()))
                && cur.distanceToSqr(this) < range * range) return;
        Player best = null;
        double bd = range * range;
        for (Player p : level().players()) {
            if (!p.isAlive() || p.isCreative() || p.isSpectator()) continue;
            double d = p.distanceToSqr(this);
            if (d < bd) { bd = d; best = p; }
        }
        setTarget(best);
    }

    // ------------------------------------------------------------------ state
    public int window() { return this.entityData.get(WINDOW); }
    public boolean isOpen() { return window() == OPEN; }
    public boolean isBuffed() { return this.entityData.get(BUFFED); }
    public void setBuffed(boolean b) { this.entityData.set(BUFFED, b); }

    /** Nerve Arm buff: lasts until refreshed by the next pulse. */
    public void buff(int ticks) {
        setBuffed(true);
        buffUntil = level().getGameTime() + ticks;
    }
    public void setCadence(float c) { this.cadence = c; }
    public void setAnchor(BlockPos p) { this.anchor = p; }
    public void setEncounterOrigin(@Nullable BlockPos p) { this.encounterOrigin = p; }
    @Nullable public BlockPos encounterOrigin() { return encounterOrigin; }
    public String anim(String name) { return "animation." + armType.model + "." + name; }

    /** Damage multiplier from the Nerve Arm's buff. */
    protected float dmg(float base) {
        return isBuffed() ? base * (1f + MatrisConfig.NERVE_DAMAGE_BUFF.get().floatValue()) : base;
    }

    /** Force an early window (punish after a missed/whiffed attack). */
    public void openWindowSoon(int delayTicks) {
        if (window() == CLOSED && !isDeadOrDying()) windowTimer = Math.min(windowTimer, delayTicks);
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isDeadOrDying()) return;
        if (anchor == null) anchor = blockPosition();
        if (isBuffed() && level().getGameTime() > buffUntil) setBuffed(false);
        leash();
        if (tickCount % 10 == 0) retarget();
        tickWindow();
        LivingEntity t = getTarget();
        if (t != null && busyTicks <= 0 && tracksTarget()) faceSmoothly(t.position(), 3f);
        if (busyTicks > 0) busyTicks--;
        if (attackCooldown > 0) attackCooldown--;
        tickBehaviour();
        if (isOpen() && tickCount % 10 == 0 && level() instanceof ServerLevel sl) {
            AnimFx.serverBurst(sl, SBParticles.CORE_GLOW.get(), coreWorld(), 3, 0.3, 0.02);
        }
    }

    protected boolean tracksTarget() { return true; }

    /** Rooting rule: parts that leave their anchor get snapped back; they never chase into the void. */
    protected void leash() {
        if (anchor == null) return;
        Vec3 a = Vec3.atBottomCenterOf(anchor);
        if (position().distanceToSqr(a) > 9) {
            teleportTo(a.x, a.y, a.z);
        }
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
    }

    private void tickWindow() {
        int w = window();
        if (--windowTimer > 0) return;
        switch (w) {
            case CLOSED -> {
                entityData.set(WINDOW, WARN);
                windowTimer = MatrisConfig.WINDOW_WARN_TICKS.get();
                triggerAnim("main", "window_warn");
            }
            case WARN -> {
                entityData.set(WINDOW, OPEN);
                windowTimer = Math.max(20, MatrisConfig.WINDOW_OPEN_TICKS.get() - (isBuffed() ? 10 : 0));
                triggerAnim("main", "open_window");
            }
            default -> {
                entityData.set(WINDOW, CLOSED);
                int jitter = MatrisConfig.WINDOW_JITTER_TICKS.get();
                windowTimer = Math.round(MatrisConfig.WINDOW_PERIOD_TICKS.get() * cadence) + random.nextInt(jitter * 2 + 1) - jitter;
            }
        }
    }

    protected abstract void tickBehaviour();

    /** Designer readout for /skylorecalyx status. */
    public String debugState() {
        LivingEntity t = getTarget();
        return armType.name() + " hp=" + Math.round(getHealth()) + " win=" + window() + " cd=" + attackCooldown
                + " buff=" + isBuffed() + " target=" + (t == null ? "-" : t.getName().getString() + "@" + Math.round(t.distanceTo(this)))
                + extraDebug();
    }

    protected String extraDebug() { return ""; }

    protected void faceSmoothly(Vec3 target, float maxDeg) {
        double dx = target.x - getX(), dz = target.z - getZ();
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), want, maxDeg);
        setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    public Vec3 coreWorld() {
        return AnimFx.locator(this, armType.model, armType.renderScale, "core");
    }

    protected List<Player> playersNear(Vec3 c, double r) {
        return level().getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    // ------------------------------------------------------------------ damage gating
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!isOpen()) {
            if (!level().isClientSide && source.getEntity() instanceof Player) {
                AnimFx.play(level(), position().add(0, 2, 0), "matris_calyx.arm.thunk", 1.2f, 0.8f + random.nextFloat() * 0.3f);
            }
            return false;
        }
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && !isDeadOrDying()) triggerAnim("main", "hurt");
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            entityData.set(WINDOW, OPEN);
            triggerAnim("main", "death");
            MatrisEncounter.onArmDied(sl, this, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.deathTime >= 70 && !level().isClientSide()) {
            if (level() instanceof ServerLevel sl) {
                AnimFx.serverBurst(sl, SBParticles.FLESH_CHUNKS.get(), coreWorld(), 30, 1.5, 0.3);
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ rooted physics & rendering
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 320 * 320; }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(armType.height * 0.8, armType.height * 0.5, armType.height * 0.8);
    }

    // ------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (anchor != null) tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
        if (encounterOrigin != null) tag.put("Encounter", NbtUtils.writeBlockPos(encounterOrigin));
        tag.putInt("Window", window());
        tag.putInt("WindowTimer", windowTimer);
        tag.putFloat("Cadence", cadence);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "Anchor").ifPresent(p -> anchor = p);
        NbtUtils.readBlockPos(tag, "Encounter").ifPresent(p -> encounterOrigin = p);
        entityData.set(WINDOW, tag.getInt("Window"));
        windowTimer = tag.getInt("WindowTimer");
        if (tag.contains("Cadence")) cadence = tag.getFloat("Cadence");
    }

    // ------------------------------------------------------------------ GeckoLib
    /** Loop animation for the current {@link #ACTION}. */
    protected String loopAnimation() { return "idle"; }

    /** One-shot animations this arm can trigger (besides the shared ones). */
    protected abstract List<String> actionAnimations();

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<AbstractRootedArm> c = new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop(anim(loopAnimation()))));
        for (String n : List.of("open_window", "window_warn", "hurt", "emerge")) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        }
        c.triggerableAnim("death", RawAnimation.begin().thenPlayAndHold(anim("death")));
        for (String n : actionAnimations()) c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, armType.model, armType.renderScale,
                e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        c.setSoundKeyframeHandler(e -> AnimFx.keyframeSound(this, e.getKeyframeData().getSound(), 4.0f));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
