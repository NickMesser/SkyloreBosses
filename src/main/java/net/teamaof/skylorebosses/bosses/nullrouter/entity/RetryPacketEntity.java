package net.teamaof.skylorebosses.bosses.nullrouter.entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.nullrouter.RouterConfig;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Phase;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vault;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.VaultLayout;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vaults;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.fx.Telegraph;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A retry packet (DESIGN.md §10): a small, fast, fragile add whose job is to undo the puzzle. It walks to a console
 * that currently shows the head request's glyph and uplinks to it (a visible amber beam, the console flashes ALERT)
 * for flipTicks, then flips it. With nothing matched to undo, or while an ACK window is open, it aggroes the nearest
 * player instead. Any hit during an uplink cancels it; standing water shorts it (it is an Automaton process too);
 * it times out after lifeTicks. Killing retries is tempo, never the win condition.
 */
public class RetryPacketEntity extends Monster implements GeoEntity {
    public static final String MODEL = "retry_packet";
    private static final EntityDataAccessor<Integer> UPLINK = SynchedEntityData.defineId(RetryPacketEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AGGRO = SynchedEntityData.defineId(RetryPacketEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos vaultOrigin;
    private int life, console = -1, uplink, rest, rethink, stuck, zapCd;
    @Nullable private UUID prey;
    @Nullable private Vec3 lastPos;

    public RetryPacketEntity(EntityType<? extends RetryPacketEntity> type, Level level) {
        super(type, level);
        xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 6)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ATTACK_DAMAGE, 3)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(UPLINK, -1);
        b.define(AGGRO, false);
    }

    @Override
    protected void registerGoals() {}

    public void bindVault(BlockPos origin) {
        vaultOrigin = origin.immutable();
    }

    @Nullable
    public BlockPos vaultOrigin() { return vaultOrigin; }

    @Nullable
    private Vault vault() {
        return vaultOrigin == null || !(level() instanceof ServerLevel sl) ? null : Vaults.get(sl).at(vaultOrigin);
    }

    /** Console index this retry is uplinking to (synced; the client draws the beam), or -1. */
    public int uplinkSynced() { return entityData.get(UPLINK); }

    public boolean aggroSynced() { return entityData.get(AGGRO); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (!isAlive()) return;
        ServerLevel sl = (ServerLevel) level();
        if (life == 0) {
            life = RouterConfig.RETRY_LIFE_TICKS.get();
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(RouterConfig.RETRY_HP.get());
            setHealth(getMaxHealth());
        }
        if (--life <= 0) {
            timeout();
            return;
        }
        Vault v = vault();
        if (vaultOrigin != null && (v == null || !v.phase().fighting())) {
            timeout();
            return;
        }
        if (zapCd > 0) zapCd--;
        if (rest > 0) {
            rest--;
            getNavigation().stop();
            return;
        }
        if (--rethink <= 0 || (console >= 0 && v != null && (v.inAck() || !v.consoleMatchesHead(console)))) {
            rethink = 10;
            think(sl, v);
        }
        if (console >= 0 && v != null) tickUplink(sl, v);
        else tickAggro(sl);
    }

    /** Pick a console that shows the head glyph (fewest other retries on it, then nearest); none -> hunt a player. */
    private void think(ServerLevel sl, @Nullable Vault v) {
        int was = console;
        console = -1;
        if (v != null && !v.inAck() && v.requestIssued()) {
            List<RetryPacketEntity> others = v.retries(sl);
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                if (!v.consoleMatchesHead(i)) continue;
                final int ci = i;
                long crowd = others.stream().filter(r -> r != this && r.console == ci).count();
                double d = VaultLayout.consoleStand(vaultOrigin, i).distanceTo(position()) + crowd * 12;
                if (d < bd) { bd = d; best = i; }
            }
            console = best;
        }
        if (console != was) {
            uplink = 0;
            entityData.set(UPLINK, -1);
        }
        entityData.set(AGGRO, console < 0);
        if (console < 0) {
            List<ServerPlayer> ps = v != null ? v.participants(sl) : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24), p -> !p.isSpectator());
            prey = ps.stream().filter(p -> !p.isCreative() && p.isAlive()).min(Comparator.comparingDouble(p -> p.distanceToSqr(this)))
                    .map(Entity::getUUID).orElse(null);
        }
    }

    private void tickUplink(ServerLevel sl, Vault v) {
        Vec3 stand = VaultLayout.consoleStand(vaultOrigin, console);
        double d = position().distanceTo(stand);
        if (d > 1.6) {
            entityData.set(UPLINK, -1);
            uplink = 0;
            if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(stand.x, stand.y, stand.z, 1.0);
            // walled off from its console: reroute (a short hop next to it) instead of standing there forever
            if (lastPos == null || position().distanceTo(lastPos) > 0.5) {
                lastPos = position();
                stuck = 0;
            } else if (++stuck > 80) {
                stuck = 0;
                AnimFx.serverBurst(sl, SBParticles.get("packet_spark"), position().add(0, 0.4, 0), 12, 0.3, 0.1);
                teleportTo(stand.x, stand.y, stand.z);
                AnimFx.play(sl, stand, "null_router.retry.reroute", 2f, 1.2f);
            }
            return;
        }
        getNavigation().stop();
        Vec3 c = Vec3.atCenterOf(VaultLayout.console(vaultOrigin, console)).add(0, 0.4, 0);
        getLookControl().setLookAt(c.x, c.y, c.z);
        entityData.set(UPLINK, console);
        int need = v.phase() == Phase.P4_STORM ? RouterConfig.RETRY_FLIP_TICKS_STORM.get() : RouterConfig.RETRY_FLIP_TICKS.get();
        v.alert(console, 3);
        if (uplink % 4 == 0) Telegraph.line(sl, SBParticles.get("packet_spark"), position().add(0, 0.4, 0), c, 0.4);
        if (uplink % 10 == 0) AnimFx.play(sl, position(), "null_router.retry.uplink", 1.5f, 0.9f + uplink / (float) need * 0.4f);
        if (++uplink >= need) {
            v.retryFlip(sl, console);
            uplink = 0;
            console = -1;
            entityData.set(UPLINK, -1);
            rest = 30;
            rethink = 0;
        }
    }

    private void tickAggro(ServerLevel sl) {
        Entity t = prey == null ? null : sl.getEntity(prey);
        if (!(t instanceof ServerPlayer p) || !p.isAlive() || p.isCreative()) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(p);
        if (distanceTo(p) > 1.3) {
            if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(p, 1.15);
            return;
        }
        if (zapCd > 0) return;
        zapCd = 20;
        p.hurt(damageSources().mobAttack(this), RouterConfig.RETRY_DAMAGE.get().floatValue());
        AnimFx.serverBurst(sl, SBParticles.get("packet_spark"), p.getBoundingBox().getCenter(), 8, 0.3, 0.1);
        AnimFx.play(sl, position(), "null_router.retry.zap", 1.5f, 1f);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof NullRouterEntity || source.getEntity() instanceof RetryPacketEntity) return false;
        boolean r = super.hurt(source, amount);
        if (r && uplink > 0) {
            // any hit drops the uplink: the console keeps its glyph
            uplink = 0;
            entityData.set(UPLINK, -1);
            rest = 10;
            if (level() instanceof ServerLevel sl) AnimFx.play(sl, position(), "null_router.retry.drop", 1.5f, 1f);
        }
        return r;
    }

    /** Despawn with a "timed out" puff (TTL, vault reset, victory). Not a kill. */
    public void timeout() {
        if (level() instanceof ServerLevel sl) {
            AnimFx.serverBurst(sl, SBParticles.get("packet_spark"), position().add(0, 0.4, 0), 14, 0.3, 0.05);
            AnimFx.play(sl, position(), "null_router.retry.timeout", 1.5f, 1f);
        }
        discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            AnimFx.serverBurst(sl, SBParticles.get("packet_spark"), position().add(0, 0.4, 0), 24, 0.35, 0.15);
            AnimFx.play(sl, position(), "null_router.retry.death", 2f, 1f);
        }
    }

    @Override
    protected void tickDeath() {
        if (++deathTime >= 6 && !level().isClientSide) remove(RemovalReason.KILLED);
    }

    private void clientFx() {
        int u = uplinkSynced();
        if (u >= 0 && tickCount % 2 == 0) {
            Vec3 p = position().add(0, 0.4, 0);
            level().addParticle(SBParticles.get("packet_spark"), p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    @Override public boolean isSensitiveToWater() { return true; }
    @Override public boolean removeWhenFarAway(double d) { return vaultOrigin == null && d > 64 * 64; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }

    public String debugState() {
        return String.format(java.util.Locale.ROOT, "retry console=%d uplink=%d aggro=%s life=%d hp=%.1f", console, uplink, aggroSynced(), life, getHealth());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (vaultOrigin != null) tag.put("Vault", NbtUtils.writeBlockPos(vaultOrigin));
        tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        vaultOrigin = NbtUtils.readBlockPos(tag, "Vault").orElse(null);
        life = tag.getInt("Life");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, s -> {
            String n = uplinkSynced() >= 0 ? "uplink" : s.isMoving() ? "move" : "idle";
            return s.setAndContinue(RawAnimation.begin().thenLoop("animation." + MODEL + "." + n));
        }).setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, MODEL, 1f, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
