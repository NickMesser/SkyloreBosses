package net.teamaof.skylorebosses.bosses.nullrouter.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.teamaof.skylorebosses.bosses.nullrouter.RouterConfig;
import net.teamaof.skylorebosses.bosses.nullrouter.block.Glyph;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Phase;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vault;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.VaultLayout;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vaults;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterEntities;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.fx.Telegraph;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Null Router's chassis (DESIGN.md §5, §9). A hovering router rack with one action slot
 * (IDLE -> TELEGRAPH -> ACTIVE -> RECOVERY -> IDLE) that only runs while it is a ghost. The vault owns the queue, the
 * request, the consoles and the ACK window and drives this entity's {@link Mode}: GHOST (hovering, immune, attacking),
 * DOCKING (settling onto the pad, solid but not yet damageable), SOLID (docked, damageable, silent), RELEASING
 * (lifting off, no longer damageable). Damage in SOLID does not reduce health: it is forwarded to the vault, which
 * turns it into cleared requests. Spawned outside a vault it runs "bench test" mode: a timed ghost/solid cycle and the
 * P2 attack table near where it was placed.
 */
public class NullRouterEntity extends Monster implements GeoEntity {
    public static final String MODEL = "null_router";
    public static final float SCALE = 1.0f;
    public enum Mode { GHOST, DOCKING, SOLID, RELEASING }
    public enum Stage { IDLE, TELEGRAPH, ACTIVE, RECOVERY }

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(NullRouterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(NullRouterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(NullRouterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WET = SynchedEntityData.defineId(NullRouterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WARN = SynchedEntityData.defineId(NullRouterEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos vaultOrigin;
    @Nullable private Vec3 home;
    private int modeTicks, benchTicks;
    private boolean shuttingDown;
    // action slot
    @Nullable private RouterAction action;
    private Stage stage = Stage.IDLE;
    private int stageTicks, timeline, gap = 40;
    private final Map<RouterAction, Long> readyAt = new EnumMap<>(RouterAction.class);
    // per-action scratch
    @Nullable private UUID target;
    private final List<Lance> lances = new ArrayList<>();
    private int fired, flickConsole = -1;
    private final Set<UUID> hitThisCast = new HashSet<>();
    // feedback
    private final Map<UUID, Long> gateMsgAt = new HashMap<>();
    private long hurtAnimAt;
    // bench-mode queue so the damage path can be exercised without a vault
    private double benchCredit;

    /** One ghost_lance line: aim tracks its target until lockAt, fires at fireAt (both on the action timeline). */
    private static final class Lance {
        @Nullable UUID target;
        Vec3 from = Vec3.ZERO, to = Vec3.ZERO;
        double yawOffset;
        int lockAt, fireAt;
        final Set<UUID> hit = new HashSet<>();
    }

    public NullRouterEntity(EntityType<? extends NullRouterEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setNoGravity(true);
        noPhysics = true;
        xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, 0);
        b.define(ACTION, -1);
        b.define(STAGE, 0);
        b.define(WET, false);
        b.define(WARN, false);
    }

    @Override
    protected void registerGoals() {}

    // ================================================================== vault link
    public void bindVault(BlockPos origin) {
        vaultOrigin = origin.immutable();
    }

    @Nullable
    public BlockPos vaultOrigin() { return vaultOrigin; }

    @Nullable
    private Vault vault() {
        return vaultOrigin == null || !(level() instanceof ServerLevel sl) ? null : Vaults.get(sl).at(vaultOrigin);
    }

    public Phase phase() {
        Vault v = vault();
        return v != null ? v.phase() : Phase.P2_DUAL;
    }

    public Mode mode() { return Mode.values()[entityData.get(MODE)]; }
    public boolean wetSynced() { return entityData.get(WET); }
    public boolean warnSynced() { return entityData.get(WARN); }

    @Nullable
    public RouterAction action() {
        int i = entityData.get(ACTION);
        return i < 0 ? null : RouterAction.values()[i];
    }

    public Stage stage() { return Stage.values()[entityData.get(STAGE)]; }

    /** 0 = full ghost, 1 = fully solid; the renderer's opacity and the model's glow read this. */
    public float solidity(float partial) {
        return switch (mode()) {
            case GHOST -> 0f;
            case SOLID -> 1f;
            case DOCKING -> Mth.clamp((modeTicks + partial) / Math.max(1, RouterConfig.SOLIDIFY_TICKS.getDefault()), 0, 1);
            case RELEASING -> 1f - Mth.clamp((modeTicks + partial) / Math.max(1, RouterConfig.RELEASE_TICKS.getDefault()), 0, 1);
        };
    }

    public Vec3 loc(String name) { return AnimFx.locator(this, MODEL, SCALE, name); }

    private Vec3 lens() { return loc("lens"); }

    // ================================================================== mode (driven by the vault)
    private void setMode(Mode m) {
        entityData.set(MODE, m.ordinal());
        modeTicks = 0;
    }

    public void dock() {
        cancel("ack");
        setMode(Mode.DOCKING);
        triggerAnim("main", "dock");
        if (level() instanceof ServerLevel sl) AnimFx.play(sl, position(), "null_router.dock", 8f, 1f);
    }

    public void open() {
        setMode(Mode.SOLID);
        entityData.set(WARN, false);
    }

    public void release() {
        setMode(Mode.RELEASING);
        entityData.set(WARN, false);
        triggerAnim("main", "release");
        if (level() instanceof ServerLevel sl) AnimFx.play(sl, position(), "null_router.release", 8f, 1f);
    }

    public void ghost() {
        setMode(Mode.GHOST);
        entityData.set(WET, false);
        entityData.set(WARN, false);
        gap = 30;
    }

    public void setWet(boolean w) { entityData.set(WET, w); }

    public void setWarn(boolean w) { entityData.set(WARN, w); }

    /** Correct a chassis whose mode disagrees with the vault (reloaded mid-window, respawned). */
    public void syncMode(Vault.Ack a) {
        Mode want = switch (a) {
            case NONE -> Mode.GHOST;
            case SOLIDIFY -> Mode.DOCKING;
            case OPEN -> Mode.SOLID;
            case RELEASE -> Mode.RELEASING;
        };
        if (mode() != want) {
            if (want != Mode.GHOST) cancel("ack");
            setMode(want);
            modeTicks = 100;
        }
    }

    // ================================================================== tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            modeTicks++;
            clientFx();
            return;
        }
        if (isDeadOrDying()) return;
        ServerLevel sl = (ServerLevel) level();
        Vault v = vault();
        if (vaultOrigin != null && v == null) { discard(); return; }
        if (home == null) home = position();
        modeTicks++;
        if (v != null) syncMode(v.ack());
        else benchCycle(sl);
        // position is owned by the fight: hovering over the pad (ghost) or docked on it (solid)
        Vec3 at = anchor(heightNow(0));
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        List<ServerPlayer> ps = players(sl);
        face(sl, ps);
        Phase ph = phase();
        if (mode() == Mode.GHOST && (v == null || ph.fighting())) runAction(sl, v, ph, ps);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (MODE.equals(key)) modeTicks = 0;
    }

    private Vec3 anchor(double height) {
        if (vaultOrigin != null) return VaultLayout.chassisAt(vaultOrigin, height);
        Vec3 h = home == null ? position() : home;
        return new Vec3(h.x, h.y + height, h.z);
    }

    private double heightNow(float partial) {
        double hover = VaultLayout.HOVER + 0.25 * Math.sin((tickCount + partial) * 0.08);
        return switch (mode()) {
            case GHOST -> hover;
            case SOLID -> VaultLayout.DOCK;
            case DOCKING -> Mth.lerp(solidity(partial), hover, VaultLayout.DOCK);
            case RELEASING -> Mth.lerp(1 - solidity(partial), VaultLayout.DOCK, hover);
        };
    }

    /** Bench test: ghost 200, dock 10, solid 110, release 10, repeat. */
    private void benchCycle(ServerLevel sl) {
        benchTicks++;
        switch (mode()) {
            case GHOST -> { if (benchTicks >= 200) { benchTicks = 0; dock(); } }
            case DOCKING -> { if (benchTicks >= 10) { benchTicks = 0; open(); } }
            case SOLID -> {
                if (benchTicks >= 90) setWarn(true);
                if (benchTicks >= 110) { benchTicks = 0; release(); }
            }
            case RELEASING -> { if (benchTicks >= 10) { benchTicks = 0; ghost(); } }
        }
        if ((mode() == Mode.DOCKING || mode() == Mode.SOLID) && isInWater()) setWet(true);
    }

    private List<ServerPlayer> players(ServerLevel sl) {
        Vault v = vault();
        if (v != null) return v.participants(sl);
        return sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(32), p -> !p.isSpectator());
    }

    private static boolean canTarget(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative();
    }

    private List<ServerPlayer> targets(List<ServerPlayer> ps) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : ps) if (canTarget(p)) out.add(p);
        return out;
    }

    @Nullable
    private ServerPlayer nearest(List<ServerPlayer> ts) {
        return ts.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
    }

    private void face(ServerLevel sl, List<ServerPlayer> ps) {
        Vec3 want = null;
        RouterAction a = action;
        if (a == RouterAction.CONSOLE_FLICK && flickConsole >= 0 && vaultOrigin != null) want = Vec3.atCenterOf(VaultLayout.console(vaultOrigin, flickConsole));
        else if (a == RouterAction.GHOST_LANCE && !lances.isEmpty()) want = lances.get(0).to;
        else {
            Entity t = target == null ? null : sl.getEntity(target);
            if (t == null) t = nearest(targets(ps));
            if (t != null) want = t.position();
        }
        if (want == null) return;
        Vec3 d = want.subtract(position());
        float yaw = Mth.approachDegrees(getYRot(), (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f, mode() == Mode.GHOST ? 8f : 2f);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ================================================================== action slot
    private void runAction(ServerLevel sl, @Nullable Vault v, Phase ph, List<ServerPlayer> ps) {
        RouterAction a = action;
        switch (stage) {
            case IDLE -> {
                if (v != null && ph.combatIndex() < 1) return;
                if (--gap > 0) return;
                RouterAction pick = choose(sl, v, ph, ps);
                if (pick != null) begin(sl, v, ph, ps, pick, null);
                else gap = 10;
            }
            case TELEGRAPH -> {
                telegraph(sl, v, ph, a, stageTicks, ps);
                timeline++;
                if (++stageTicks >= a.telegraphTicks(ph)) {
                    setStage(Stage.ACTIVE);
                    beginActive(sl, a);
                }
            }
            case ACTIVE -> {
                activeTick(sl, v, ph, a, stageTicks, ps);
                timeline++;
                if (action != a) return;
                if (++stageTicks >= a.activeTicks(ph)) setStage(Stage.RECOVERY);
            }
            case RECOVERY -> {
                if (a == RouterAction.GHOST_LANCE && stageTicks < 3) for (Lance l : lances) drawLance(sl, l, SBParticles.get("null_beam"), 0.6);
                if (++stageTicks >= a.recovery) finish(ph, sl.getGameTime());
            }
        }
    }

    private void setStage(Stage s) {
        stage = s;
        stageTicks = 0;
        entityData.set(STAGE, s.ordinal());
    }

    private void finish(Phase ph, long now) {
        RouterAction a = action;
        if (a != null && !a.scripted) readyAt.put(a, now + Math.round(a.cooldownTicks(ph) / RouterConfig.ATTACK_SPEED.get()));
        clearAction();
        int base = switch (ph) {
            case P1_SINGLE -> 30;
            case P2_DUAL, P3_COOLANT -> 24;
            case P4_STORM -> 16;
            default -> 30;
        };
        gap = (int) Math.round(base / RouterConfig.ATTACK_SPEED.get());
    }

    private void clearAction() {
        action = null;
        entityData.set(ACTION, -1);
        setStage(Stage.IDLE);
        lances.clear();
        flickConsole = -1;
        target = null;
    }

    /** Cancel whatever is running (an ACK window opens, commands, phase scripts). */
    public void cancel(String why) {
        RouterAction a = action;
        if (a == null) return;
        Vault v = vault();
        if (a == RouterAction.MISROUTE_PULSE && v != null && level() instanceof ServerLevel sl) {
            // a misroute is already decided: land it even if the window opens under it
            Entity t = target == null ? null : sl.getEntity(target);
            if (t instanceof ServerPlayer p) v.routeActor(sl, p);
        }
        clearAction();
        gap = 20;
        if (!a.scripted) readyAt.put(a, level().getGameTime() + a.cooldown / 2);
    }

    /** Designer command / vault script: start {@code a} now (skips weights and cooldowns, keeps the telegraph). */
    public boolean force(RouterAction a) {
        return script(a, null);
    }

    public boolean script(RouterAction a, @Nullable ServerPlayer on) {
        if (!(level() instanceof ServerLevel sl)) return false;
        if (mode() != Mode.GHOST && a != RouterAction.MISROUTE_PULSE) return false;
        cancel("forced");
        begin(sl, vault(), phase(), players(sl), a, on);
        return true;
    }

    @Nullable
    private RouterAction choose(ServerLevel sl, @Nullable Vault v, Phase ph, List<ServerPlayer> ps) {
        List<ServerPlayer> ts = targets(ps);
        if (ts.isEmpty() || (v != null && v.held())) return null;
        long now = sl.getGameTime();
        List<RouterAction> bag = new ArrayList<>();
        for (RouterAction a : RouterAction.values()) {
            int w = a.weight(ph);
            if (w <= 0 || a.scripted || readyAt.getOrDefault(a, 0L) > now) continue;
            switch (a) {
                case REQUEST_PING -> { if (v == null || !anyWrong(v)) continue; }
                case GHOST_LANCE -> { if (lanceTargets(sl, ts).isEmpty()) continue; }
                case PACKET_BURST -> { if (v != null && v.retries(sl).size() >= RouterConfig.retryCap(ph)) continue; }
                case CONSOLE_FLICK -> {
                    if (v == null || flickCandidate(sl, v) < 0) continue;
                    if (anyHeadMatch(sl, v)) w *= 2;
                }
                case TTL_EXPIRY -> {
                    BlockPos o = vaultOrigin != null ? vaultOrigin : BlockPos.containing(position()).below(5);
                    boolean near = ts.stream().anyMatch(p -> VaultLayout.padDistance(o, p.position()) <= 9);
                    if (!near) continue;
                    if (v != null && ts.stream().anyMatch(p -> v.campTicks(p.getUUID()) >= 40)) w *= 3;
                }
                default -> {}
            }
            for (int i = 0; i < w; i++) bag.add(a);
        }
        return bag.isEmpty() ? null : bag.get(random.nextInt(bag.size()));
    }

    private static boolean anyWrong(Vault v) {
        for (int i = 0; i < 3; i++) if (!v.consoleMatchesHead(i)) return true;
        return false;
    }

    private boolean anyHeadMatch(ServerLevel sl, Vault v) {
        for (int i = 0; i < 3; i++) if (v.consoleMatchesHead(i) && !v.attended(sl, i)) return true;
        return false;
    }

    /** console_flick prefers a console that already matches the head, never an attended one. */
    private int flickCandidate(ServerLevel sl, Vault v) {
        List<Integer> good = new ArrayList<>(), any = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (v.attended(sl, i)) continue;
            any.add(i);
            if (v.consoleMatchesHead(i)) good.add(i);
        }
        List<Integer> pool = good.isEmpty() ? any : good;
        return pool.isEmpty() ? -1 : pool.get(random.nextInt(pool.size()));
    }

    /** Players the lens can see; airborne players weigh 3, players working a console weigh 2. */
    private List<ServerPlayer> lanceTargets(ServerLevel sl, List<ServerPlayer> ts) {
        List<ServerPlayer> out = new ArrayList<>();
        Vec3 from = lens();
        for (ServerPlayer p : ts) {
            if (!clear(sl, from, p.getEyePosition())) continue;
            int w = 1;
            if (!p.onGround() && p.getY() > floorY() + 2) w = 3;
            else if (vaultOrigin != null) for (int i = 0; i < 3; i++) if (VaultLayout.consoleStand(vaultOrigin, i).distanceTo(p.position()) < 3) w = 2;
            for (int k = 0; k < w; k++) out.add(p);
        }
        return out;
    }

    private double floorY() {
        return vaultOrigin != null ? VaultLayout.floorY(vaultOrigin) : (home == null ? getY() : home.y);
    }

    private void begin(ServerLevel sl, @Nullable Vault v, Phase ph, List<ServerPlayer> ps, RouterAction a, @Nullable ServerPlayer forcedTarget) {
        action = a;
        entityData.set(ACTION, a.ordinal());
        setStage(Stage.TELEGRAPH);
        timeline = 0;
        fired = 0;
        hitThisCast.clear();
        lances.clear();
        flickConsole = -1;
        target = forcedTarget == null ? null : forcedTarget.getUUID();
        List<ServerPlayer> ts = targets(ps);
        int tele = a.telegraphTicks(ph);
        switch (a) {
            case BOOT_CHIME -> {
                triggerAnim("main", "chime");
                AnimFx.play(sl, position(), "null_router.chime.windup", 8f, 1f);
            }
            case REQUEST_PING -> {
                triggerAnim("main", "ping");
                AnimFx.play(sl, position(), "null_router.ping", 6f, 1f);
            }
            case GHOST_LANCE -> {
                triggerAnim("main", "lance_windup");
                AnimFx.play(sl, position(), "null_router.lance.charge", 6f, 1f);
                List<ServerPlayer> pool = lanceTargets(sl, ts);
                if (pool.isEmpty()) pool = ts;
                if (pool.isEmpty()) { clearAction(); return; }
                ServerPlayer t1 = pool.get(random.nextInt(pool.size()));
                target = t1.getUUID();
                int n = RouterAction.lances(ph);
                for (int k = 0; k < n; k++) {
                    Lance l = new Lance();
                    if (n == 3) {
                        l.target = t1.getUUID();
                        l.yawOffset = (k - 1) * 14;
                        l.lockAt = tele / 2;
                        l.fireAt = tele;
                    } else if (k == 0) {
                        l.target = t1.getUUID();
                        l.lockAt = ph == Phase.P1_SINGLE ? 0 : tele / 2;
                        l.fireAt = tele;
                    } else {
                        List<ServerPlayer> others = pool.stream().filter(p -> !p.getUUID().equals(t1.getUUID())).toList();
                        l.target = (others.isEmpty() ? t1 : others.get(random.nextInt(others.size()))).getUUID();
                        l.lockAt = tele + 2;
                        l.fireAt = tele + RouterAction.LANCE_STAGGER;
                    }
                    aim(sl, l);
                    lances.add(l);
                }
                if (ph == Phase.P1_SINGLE || v == null) warn(t1, "null_router.warn.lance");
            }
            case PACKET_BURST -> {
                triggerAnim("main", "burst_windup");
                AnimFx.play(sl, position(), "null_router.burst.windup", 6f, 1f);
                warnAll(ps, "null_router.warn.burst", ChatFormatting.GOLD);
            }
            case CONSOLE_FLICK -> {
                if (v == null) { clearAction(); return; }
                flickConsole = flickCandidate(sl, v);
                if (flickConsole < 0) { clearAction(); return; }
                triggerAnim("main", "flick");
                AnimFx.play(sl, Vec3.atCenterOf(VaultLayout.console(vaultOrigin, flickConsole)), "null_router.flick.windup", 4f, 1f);
                v.alert(flickConsole, tele + 4);
                warnAll(ps, "null_router.warn.flick", ChatFormatting.GOLD, VaultLayout.CHANNEL[flickConsole]);
            }
            case TTL_EXPIRY -> {
                triggerAnim("main", "ttl_windup");
                AnimFx.play(sl, position(), "null_router.ttl.windup", 8f, 1f);
                warnAll(ps, "null_router.warn.ttl", ChatFormatting.RED);
            }
            case MISROUTE_PULSE -> {
                triggerAnim("main", "misroute");
                if (forcedTarget == null) { clearAction(); return; }
                AnimFx.play(sl, forcedTarget.position(), "null_router.misroute.windup", 4f, 1f);
            }
            case RETRY_STORM -> {
                triggerAnim("main", "storm_windup");
                AnimFx.play(sl, position(), "null_router.storm.alarm", 14f, 1.1f);
                warnAll(ps, "null_router.warn.storm", ChatFormatting.RED);
                if (v != null) for (int i = 0; i < 3; i++) v.alert(i, tele + a.active);
            }
        }
    }

    /** Re-aim a lance at its target's eye; the line ends at the first block or 32 blocks out. */
    private void aim(ServerLevel sl, Lance l) {
        Entity t = l.target == null ? null : sl.getEntity(l.target);
        Vec3 from = lens();
        Vec3 dir = t == null ? getLookAngle() : t.getBoundingBox().getCenter().subtract(from).normalize();
        if (l.yawOffset != 0) dir = dir.yRot((float) Math.toRadians(l.yawOffset));
        Vec3 end = from.add(dir.scale(32));
        HitResult hr = sl.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        l.from = from;
        l.to = hr.getType() == HitResult.Type.MISS ? end : hr.getLocation();
    }

    private void telegraph(ServerLevel sl, @Nullable Vault v, Phase ph, RouterAction a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case BOOT_CHIME -> {
                if (t % 5 == 0) AnimFx.serverBurst(sl, SBParticles.get("null_beam"), lens(), 6, 0.4, 0.04);
            }
            case REQUEST_PING -> {
                if (t % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("ack_glint"), lens(), 4, 0.3, 0.05);
            }
            case GHOST_LANCE -> {
                for (Lance l : lances) if (timeline < l.lockAt) aim(sl, l);
                if (t % 2 == 0) for (Lance l : lances) drawLance(sl, l, SBParticles.TARGET_MARK.get(), 0.8);
            }
            case PACKET_BURST -> {
                if (t % 4 == 0) for (String port : new String[]{"port_l", "port_r", "port_b"})
                    AnimFx.serverBurst(sl, SBParticles.get("packet_spark"), loc(port), 3, 0.2, 0.05);
            }
            case CONSOLE_FLICK -> {
                if (t % 2 == 0 && flickConsole >= 0 && vaultOrigin != null)
                    Telegraph.line(sl, SBParticles.RAIL_MARK.get(), lens(), Vec3.atCenterOf(VaultLayout.console(vaultOrigin, flickConsole)).add(0, 0.5, 0), 0.8);
            }
            case TTL_EXPIRY -> {
                if (t % 4 != 0) return;
                Vec3 c = floorCenter();
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), c, 6.5, 48);
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), c, 3.5, 26);
            }
            case MISROUTE_PULSE -> {
                if (tgt != null) Telegraph.line(sl, SBParticles.RED_BEAM.get(), lens(), tgt.getBoundingBox().getCenter(), 0.5);
            }
            case RETRY_STORM -> {
                if (t % 5 == 0 && vaultOrigin != null)
                    for (int i = 0; i < VaultLayout.PORTS.length; i++)
                        Telegraph.point(sl, SBParticles.TARGET_MARK.get(), VaultLayout.port(vaultOrigin, i).add(0, 0.8, 0), 4, 0.4);
            }
        }
    }

    private Vec3 floorCenter() {
        if (vaultOrigin != null) return Vec3.atBottomCenterOf(vaultOrigin).add(0, 1.05, 0);
        return new Vec3(getX(), floorY() + 0.05, getZ());
    }

    private void beginActive(ServerLevel sl, RouterAction a) {
        switch (a) {
            case GHOST_LANCE -> triggerAnim("main", "lance");
            case PACKET_BURST -> triggerAnim("main", "burst");
            case TTL_EXPIRY -> {
                triggerAnim("main", "ttl");
                for (ServerPlayer p : players(sl)) if (p.distanceTo(this) < 16) SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 8);
            }
            case RETRY_STORM -> triggerAnim("main", "storm");
            default -> {}
        }
    }

    private void activeTick(ServerLevel sl, @Nullable Vault v, Phase ph, RouterAction a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case BOOT_CHIME -> {
                if (t != 0) return;
                AnimFx.play(sl, lens(), "null_router.chime", 12f, 1f);
                AnimFx.serverBurst(sl, SBParticles.get("ack_glint"), lens(), 60, 1.5, 0.3);
                if (vaultOrigin != null) {
                    for (int i = 0; i < 3; i++) {
                        Telegraph.line(sl, SBParticles.get("ack_glint"), lens(), Vec3.atCenterOf(VaultLayout.console(vaultOrigin, i)).add(0, 0.5, 0), 0.5);
                        Telegraph.line(sl, SBParticles.get("ack_glint"), lens(), Vec3.atCenterOf(VaultLayout.boardHead(vaultOrigin, i)), 0.8);
                    }
                }
                warnAll(ps, "null_router.warn.boot", ChatFormatting.AQUA);
            }
            case REQUEST_PING -> {
                if (v == null || vaultOrigin == null || t % 5 != 0) return;
                int[] h = v.head();
                for (int i = 0; i < 3; i++) {
                    Vec3 c = Vec3.atCenterOf(VaultLayout.console(vaultOrigin, i)).add(0, 0.6, 0);
                    if (v.consoleMatchesHead(i)) {
                        if (t == 0) AnimFx.serverBurst(sl, SBParticles.get("ack_glint"), c, 6, 0.3, 0.02);
                        continue;
                    }
                    Telegraph.line(sl, SBParticles.get("null_beam"), Vec3.atCenterOf(VaultLayout.headLamp(vaultOrigin, i)), c, 0.4);
                    Telegraph.point(sl, SBParticles.RAIL_MARK.get(), c, 4, 0.35);
                    if (t == 0) {
                        AnimFx.play(sl, c, "null_router.ping.console", 3f, 1f + i * 0.1f);
                        for (ServerPlayer p : ps)
                            if (p.position().distanceTo(VaultLayout.consoleStand(vaultOrigin, i)) < 5)
                                p.displayClientMessage(Component.translatable("null_router.warn.ping", VaultLayout.CHANNEL[i], Glyph.of(h[i]).chip()).withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
            case GHOST_LANCE -> {
                for (Lance l : lances) {
                    int since = timeline - l.fireAt;
                    if (since < 0) {
                        if (timeline < l.lockAt) aim(sl, l);
                        if (t % 2 == 0) drawLance(sl, l, SBParticles.TARGET_MARK.get(), 0.8);
                        continue;
                    }
                    if (since >= a.active) continue;
                    if (since == 0) {
                        AnimFx.play(sl, l.from, "null_router.lance.fire", 6f, 0.9f + random.nextFloat() * 0.2f);
                        AnimFx.serverBurst(sl, SBParticles.get("null_beam"), l.to, 12, 0.3, 0.1);
                    }
                    drawLance(sl, l, SBParticles.get("null_beam"), 0.35);
                    for (ServerPlayer p : ps) {
                        if (!canTarget(p) || l.hit.contains(p.getUUID())) continue;
                        if (segmentDistance(l.from, l.to, p.getBoundingBox().getCenter()) > 0.9 && segmentDistance(l.from, l.to, p.getEyePosition()) > 0.9) continue;
                        l.hit.add(p.getUUID());
                        p.hurt(nullRoute(sl), RouterConfig.LANCE_DAMAGE.get().floatValue());
                    }
                }
            }
            case PACKET_BURST -> {
                int n = RouterAction.burst(ph);
                if (t % 5 != 0 || fired >= n) return;
                String[] ports = {"port_l", "port_r", "port_b"};
                Vec3 from = loc(ports[fired % 3]);
                double ang = random.nextDouble() * Math.PI * 2;
                Vec3 vel = new Vec3(Math.cos(ang) * 0.35, 0.3, Math.sin(ang) * 0.35);
                boolean made;
                if (v != null) made = v.spawnRetry(sl, from, vel, false) != null;
                else {
                    RetryPacketEntity r = RouterEntities.RETRY_PACKET.get().create(sl);
                    made = r != null;
                    if (r != null) {
                        r.moveTo(from.x, from.y, from.z, 0, 0);
                        r.setDeltaMovement(vel);
                        sl.addFreshEntity(r);
                    }
                }
                AnimFx.play(sl, from, made ? "null_router.burst.eject" : "null_router.burst.fizzle", 4f, 0.9f + fired * 0.1f);
                fired++;
            }
            case CONSOLE_FLICK -> {
                if (t != 0 || v == null || flickConsole < 0) return;
                if (v.attended(sl, flickConsole)) {
                    AnimFx.play(sl, Vec3.atCenterOf(VaultLayout.console(vaultOrigin, flickConsole)), "null_router.flick.refused", 4f, 1f);
                    for (ServerPlayer p : ps)
                        if (p.position().distanceTo(VaultLayout.consoleStand(vaultOrigin, flickConsole)) < 4)
                            p.displayClientMessage(Component.translatable("null_router.warn.flick_refused", VaultLayout.CHANNEL[flickConsole]).withStyle(ChatFormatting.GREEN), true);
                    return;
                }
                Telegraph.line(sl, SBParticles.get("packet_spark"), lens(), Vec3.atCenterOf(VaultLayout.console(vaultOrigin, flickConsole)).add(0, 0.5, 0), 0.5);
                v.bossFlip(sl, flickConsole, false);
            }
            case TTL_EXPIRY -> {
                double r = 1 + t * 0.75;
                Vec3 c = floorCenter();
                Telegraph.ring(sl, SBParticles.get("grid_arc"), c, r, Math.max(16, (int) (r * 6)));
                if (t % 3 == 0) AnimFx.play(sl, c, "null_router.ttl.pulse", 6f, 0.8f + t * 0.03f);
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || hitThisCast.contains(p.getUUID())) continue;
                    double h = Math.sqrt(p.position().subtract(c).horizontalDistanceSqr());
                    if (h < r - 0.9 || h > r + 0.3 || !p.onGround() || Math.abs(p.getY() - c.y) > 1.6) continue;
                    hitThisCast.add(p.getUUID());
                    p.hurt(nullRoute(sl), RouterConfig.TTL_DAMAGE.get().floatValue());
                    push(p, c, 1.2, 0.5);
                }
            }
            case MISROUTE_PULSE -> {
                if (t != 0) return;
                if (tgt instanceof ServerPlayer p) {
                    Telegraph.line(sl, SBParticles.get("null_beam"), lens(), p.getBoundingBox().getCenter(), 0.3);
                    if (v != null) v.routeActor(sl, p);
                }
            }
            case RETRY_STORM -> {
                if (t % 10 != 0 || v == null || vaultOrigin == null) return;
                int k = t / 10;
                Vec3 at = VaultLayout.port(vaultOrigin, k % VaultLayout.PORTS.length);
                v.spawnRetry(sl, at, Vec3.ZERO, false);
                v.bossFlip(sl, random.nextInt(3), true);
                AnimFx.play(sl, at, "null_router.storm.port", 5f, 1f);
            }
        }
    }

    /** Draw a lance line, leaving a 2.5-block gap around every player's eyes so the telegraph never fills a screen. */
    private void drawLance(ServerLevel sl, Lance l, ParticleOptions type, double step) {
        List<Vec3> eyes = players(sl).stream().map(Entity::getEyePosition).toList();
        double len = l.from.distanceTo(l.to);
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 p = l.from.lerp(l.to, i / (double) n);
            if (eyes.stream().anyMatch(e -> e.distanceToSqr(p) < 6.25)) continue;
            Telegraph.point(sl, type, p, 1, 0);
        }
    }

    private static double segmentDistance(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1e-6) return p.distanceTo(a);
        double t = Mth.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
        return p.distanceTo(a.add(ab.scale(t)));
    }

    private DamageSource nullRoute(ServerLevel sl) {
        return new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Vault.NULL_ROUTE), this, this);
    }

    private void push(Entity p, Vec3 from, double h, double up) {
        Vec3 d = p.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : d.normalize();
        p.push(d.x * h, up, d.z * h);
        p.hurtMarked = true;
    }

    private boolean clear(ServerLevel sl, Vec3 from, Vec3 to) {
        return sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private void warn(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    private void warnAll(List<ServerPlayer> ps, String key, ChatFormatting color, Object... args) {
        Component c = Component.translatable(key, args).withStyle(color);
        for (ServerPlayer p : ps) p.displayClientMessage(c, true);
    }

    // ================================================================== damage (DESIGN.md §7)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return shuttingDown && super.hurt(source, amount);
        ServerLevel sl = (ServerLevel) level();
        Vault v = vault();
        // water: the vanilla drowning tick for water-sensitive mobs, and splash water bottles
        if (source.is(DamageTypeTags.IS_DROWNING) || source.getDirectEntity() instanceof ThrownPotion) {
            if (v != null) v.onChassisWater(sl, this);
            else if (mode() != Mode.GHOST) setWet(true);
            return false;
        }
        if (source.getEntity() == this || source.getEntity() instanceof RetryPacketEntity) return false;
        if (mode() != Mode.SOLID || shuttingDown) {
            nak(sl, source);
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Damage after vanilla i-frames. Health never changes: the vault converts it into cleared requests. */
    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (shuttingDown) {
            super.actuallyHurt(source, amount);
            return;
        }
        if (!(level() instanceof ServerLevel sl) || amount <= 0) return;
        long now = sl.getGameTime();
        if (now - hurtAnimAt > 8) {
            hurtAnimAt = now;
            triggerAnim("main", "hurt");
        }
        AnimFx.serverBurst(sl, SBParticles.get(wetSynced() ? "coolant_mist" : "spark"), getBoundingBox().getCenter(), 8, 0.6, 0.15);
        Vault v = vault();
        if (v != null) v.onAckDamage(sl, this, amount, source.getEntity());
        else {
            benchCredit += amount * (wetSynced() ? 2.5 : 1.0) / RouterConfig.DAMAGE_PER_REQUEST.get();
        }
    }

    /** "NAK": a hit on a ghost (or on the docking edges) does nothing and says why. */
    private void nak(ServerLevel sl, DamageSource source) {
        Entity attacker = source.getEntity();
        AnimFx.serverBurst(sl, SBParticles.get("grid_arc"), getBoundingBox().getCenter(), 6, 0.8, 0.05);
        long now = sl.getGameTime();
        if (!(attacker instanceof ServerPlayer p) || gateMsgAt.getOrDefault(p.getUUID(), 0L) > now) return;
        gateMsgAt.put(p.getUUID(), now + 60);
        AnimFx.play(sl, position(), "null_router.nak", 3f, 1f);
        String key = switch (mode()) {
            case DOCKING -> "null_router.gate.docking";
            case RELEASING -> "null_router.gate.releasing";
            default -> {
                Vault v = vault();
                yield v != null && v.phase() == Phase.P0_BOOT && !v.requestIssued() ? "null_router.gate.booting" : "null_router.gate.ghost";
            }
        };
        p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.AQUA), true);
    }

    /** Projectiles pass through the ghost; the docked chassis stops them. */
    @Override
    public boolean canBeHitByProjectile() {
        return mode() != Mode.GHOST && isAlive();
    }

    @Override
    public boolean isSensitiveToWater() { return true; }

    @Override
    public boolean isPushedByFluid() { return false; }

    /** Queue empty: power down on the pad. */
    public void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        clearAction();
        setMode(Mode.SOLID);
        hurt(damageSources().genericKill(), Float.MAX_VALUE);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            triggerAnim("main", "death");
            AnimFx.play(sl, position(), "null_router.death", 12f, 1f);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl) {
            Vec3 at = anchor(VaultLayout.DOCK);
            setPos(at.x, at.y, at.z);
            if (deathTime % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("spark"), getBoundingBox().getCenter(), 10, 0.9, 0.1);
            if (deathTime % 10 == 0) AnimFx.serverBurst(sl, SBParticles.get("smoke"), position().add(0, 2.2, 0), 8, 0.6, 0.03);
            if (deathTime >= 140) {
                AnimFx.serverBurst(sl, SBParticles.get("ack_glint"), getBoundingBox().getCenter(), 120, 1.4, 0.2);
                AnimFx.serverBurst(sl, SBParticles.get("smoke"), position().add(0, 1, 0), 60, 1.2, 0.08);
                remove(RemovalReason.KILLED);
            }
        }
    }

    // ================================================================== client
    private void clientFx() {
        if (isDeadOrDying()) return;
        Mode m = mode();
        if (m == Mode.GHOST && tickCount % 3 == 0) {
            Vec3 c = getBoundingBox().getCenter();
            level().addParticle(SBParticles.get("null_beam"), c.x + (random.nextDouble() - 0.5) * 2.4, c.y + (random.nextDouble() - 0.5) * 2.6,
                    c.z + (random.nextDouble() - 0.5) * 2.4, 0, 0.01, 0);
        }
        if (m != Mode.GHOST && tickCount % 4 == 0) {
            Vec3 f = position();
            level().addParticle(SBParticles.get("ack_glint"), f.x + (random.nextDouble() - 0.5) * 2.4, f.y + 0.1, f.z + (random.nextDouble() - 0.5) * 2.4, 0, 0.05, 0);
        }
        if (wetSynced() && tickCount % 2 == 0) {
            Vec3 c = getBoundingBox().getCenter();
            level().addParticle(SBParticles.get("coolant_mist"), c.x + (random.nextDouble() - 0.5) * 2, c.y + random.nextDouble(), c.z + (random.nextDouble() - 0.5) * 2, 0, 0.06, 0);
        }
        if (warnSynced() && tickCount % 6 == 0) {
            Vec3 l = loc("lens");
            level().addParticle(SBParticles.get("spark"), l.x, l.y, l.z, 0, 0.05, 0);
        }
    }

    // ================================================================== misc
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(2, 2, 2); }
    @Override public boolean isPickable() { return isAlive(); }

    public String debugState() {
        RouterAction a = action;
        return String.format(java.util.Locale.ROOT, "chassis mode=%s modeT=%d wet=%s warn=%s action=%s stage=%s t=%d gap=%d pos=%.1f,%.1f,%.1f hp=%.0f bench=%.2f",
                mode().name().toLowerCase(java.util.Locale.ROOT), modeTicks, wetSynced(), warnSynced(), a == null ? "-" : a.id(), stage, stageTicks, gap,
                getX(), getY(), getZ(), getHealth(), benchCredit);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (vaultOrigin != null) tag.put("Vault", NbtUtils.writeBlockPos(vaultOrigin));
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        vaultOrigin = NbtUtils.readBlockPos(tag, "Vault").orElse(null);
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        // a reloaded chassis resumes idle; the vault re-applies the ACK mode on its next tick
        gap = 40;
    }

    private static String anim(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<NullRouterEntity> c = new AnimationController<>(this, "main", 3, s -> {
            if (isDeadOrDying()) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(anim("death")));
            return switch (mode()) {
                case SOLID -> s.setAndContinue(RawAnimation.begin().thenLoop(anim(warnSynced() ? "solid_warn" : "solid")));
                case DOCKING, RELEASING -> s.setAndContinue(RawAnimation.begin().thenLoop(anim("solid")));
                case GHOST -> s.setAndContinue(RawAnimation.begin().thenLoop(anim("idle")));
            };
        });
        for (String n : new String[]{"boot", "chime", "ping", "lance_windup", "lance", "burst_windup", "burst", "flick", "ttl_windup", "ttl",
                "misroute", "storm_windup", "storm", "dock", "release", "hurt"}) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        }
        c.triggerableAnim("death", RawAnimation.begin().thenPlayAndHold(anim("death")));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, MODEL, SCALE, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        c.setSoundKeyframeHandler(e -> AnimFx.keyframeSound(this, e.getKeyframeData().getSound(), 3.0f));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
