package net.teamaof.skylorebosses.bosses.overhead.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.overhead.OverheadConfig;
import net.teamaof.skylorebosses.bosses.overhead.encounter.OverheadYards;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Phase;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Pylon;
import net.teamaof.skylorebosses.bosses.overhead.encounter.PylonState;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Yard;
import net.teamaof.skylorebosses.bosses.overhead.encounter.YardLayout;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.registry.SBSounds;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Overhead, Noven's Decommissioned Prototype (DESIGN.md §5, §9). A hovering artillery chassis with one action
 * slot: IDLE -> TELEGRAPH -> ACTIVE -> RECOVERY -> IDLE. The yard owns phase, pylons and the damage gate;
 * this entity owns movement, targeting and the attacks. Spawned outside a yard it runs "bench test" mode:
 * no gate, P2 attack table, hovering around where it was placed.
 */
public class OverheadEntity extends Monster implements GeoEntity {
    public static final String MODEL = "overhead";
    public static final float SCALE = 1.5f;
    public enum Stage { IDLE, TELEGRAPH, ACTIVE, RECOVERY }

    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(OverheadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(OverheadEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BEAM_ON = SynchedEntityData.defineId(OverheadEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> BEAM = SynchedEntityData.defineId(OverheadEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Boolean> BROWNOUT = SynchedEntityData.defineId(OverheadEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos yardOrigin;
    @Nullable private Vec3 home;
    // action slot
    @Nullable private Action action;
    private Stage stage = Stage.IDLE;
    private int stageTicks, gap = 60, staggerTicks, shieldTicks;
    private boolean vulnerable;
    private final Map<Action, Long> readyAt = new EnumMap<>(Action.class);
    // per-action scratch
    @Nullable private UUID target;
    private Vec3 aimA = Vec3.ZERO, aimB = Vec3.ZERO;
    private final boolean[] lanes = new boolean[YardLayout.LANES];
    private int pylonIdx = -1, fired;
    // movement
    @Nullable private Vec3 hoverGoal;
    private int repositionIn;
    // targeting memory
    private final Map<UUID, Float> threat = new HashMap<>();
    private final Map<UUID, Long> paintedUntil = new HashMap<>();
    private final Map<UUID, Integer> airborne = new HashMap<>();
    private final Map<UUID, Long> gateMsgAt = new HashMap<>();
    private float burst;
    private long burstStart, staggerLockUntil;
    private final List<Impact> impacts = new ArrayList<>();

    private record Impact(Vec3 pos, long at) {}

    public OverheadEntity(EntityType<? extends OverheadEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setPersistenceRequired();
        xpReward = 400;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 96)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ACTION, -1);
        b.define(STAGE, 0);
        b.define(BEAM_ON, false);
        b.define(BEAM, new Vector3f());
        b.define(BROWNOUT, false);
    }

    @Override
    protected void registerGoals() {}

    // ================================================================== yard link
    public void bindYard(BlockPos origin) {
        yardOrigin = origin.immutable();
    }

    @Nullable
    public BlockPos yardOrigin() { return yardOrigin; }

    @Nullable
    private Yard yard() {
        return yardOrigin == null || !(level() instanceof ServerLevel sl) ? null : OverheadYards.get(sl).at(yardOrigin);
    }

    public Phase phase() {
        Yard y = yard();
        return y != null ? y.phase() : Phase.P2_DEGRADED;
    }

    @Nullable public Action action() {
        int i = entityData.get(ACTION);
        return i < 0 ? null : Action.values()[i];
    }

    public Stage stage() { return Stage.values()[entityData.get(STAGE)]; }
    public boolean beamOn() { return entityData.get(BEAM_ON); }
    public Vec3 beamEnd() { return new Vec3(entityData.get(BEAM)); }
    public boolean brownout() { return entityData.get(BROWNOUT); }
    public boolean vulnerable() { return vulnerable; }
    public boolean shielded() { return shieldTicks > 0; }

    public Vec3 loc(String name) { return AnimFx.locator(this, MODEL, SCALE, name); }

    // ================================================================== tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (isDeadOrDying()) return;
        ServerLevel sl = (ServerLevel) level();
        Yard yard = yard();
        if (yardOrigin != null && yard == null) { discard(); return; }
        if (home == null) home = position();
        Phase ph = phase();
        long now = sl.getGameTime();
        List<ServerPlayer> ps = players(sl);
        trackTargets(ps, now);
        tickImpacts(sl, now);
        if (shieldTicks > 0) shieldTicks--;
        if (staggerTicks > 0) staggerTicks--;
        entityData.set(BROWNOUT, yard != null && yard.windowTicks() > 0);
        hover(sl, yard, ph, ps);
        face(sl, ps);
        runAction(sl, yard, ph, ps, now);
    }

    private List<ServerPlayer> players(ServerLevel sl) {
        Yard y = yard();
        if (y != null) return y.participants(sl);
        return sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48), p -> !p.isSpectator());
    }

    private List<ServerPlayer> targets(List<ServerPlayer> ps, boolean allowAboveCeiling) {
        double ceiling = floorY() + YardLayout.CEILING;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : ps) if (Blast.canTarget(p) && (allowAboveCeiling || p.getY() < ceiling)) out.add(p);
        return out;
    }

    private double floorY() {
        return yardOrigin != null ? YardLayout.floorY(yardOrigin) : (home != null ? home.y - 14 : getY() - 14);
    }

    private void trackTargets(List<ServerPlayer> ps, long now) {
        if (now % 20 == 0) threat.replaceAll((k, v) -> v * 0.98f);
        paintedUntil.values().removeIf(t -> t < now);
        for (ServerPlayer p : ps) {
            boolean up = !p.onGround() && p.getY() > floorY() + 10;
            airborne.merge(p.getUUID(), up ? 1 : 0, (a, b) -> b == 0 ? 0 : a + 1);
        }
    }

    public void addThreat(UUID player, float amount) {
        threat.merge(player, amount, Float::sum);
    }

    private boolean painted(Entity p) {
        return paintedUntil.containsKey(p.getUUID());
    }

    private boolean isAirborne(ServerPlayer p) {
        Entity v = p.getVehicle();
        return airborne.getOrDefault(p.getUUID(), 0) >= 20 || p.isFallFlying() || (v != null && v.getType().is(Blast.FLIGHT_VEHICLES));
    }

    // ================================================================== movement
    private void hover(ServerLevel sl, @Nullable Yard yard, Phase ph, List<ServerPlayer> ps) {
        Vec3 center = yardOrigin != null ? Vec3.atBottomCenterOf(yardOrigin).add(0, 1, 0) : home.add(0, -14, 0);
        double floor = center.y;
        double speed;
        double minH, maxH;
        switch (ph) {
            case P0_LOCKDOWN -> { minH = maxH = Math.min(14, 2 + (yard == null ? 999 : yard.phaseTicks()) * 0.1); speed = 0.15; }
            case P1_SHIELDED -> { minH = 14; maxH = 18; speed = 0.25; }
            case P3_EXPOSED -> { minH = 4; maxH = 8; speed = 0.4; }
            case P4_REARM -> { minH = 16; maxH = 20; speed = 0.3; }
            case DEFEATED, CLEARED, DORMANT -> { minH = maxH = 3; speed = 0.1; }
            default -> { minH = 11; maxH = 16; speed = 0.3; }
        }
        if (brownout()) { minH = maxH = YardLayout.LEASH_MIN_Y; speed = 0.35; }
        Action a = action();
        boolean anchored = a == Action.LASER_SWEEP && (stage == Stage.TELEGRAPH || stage == Stage.ACTIVE);
        Vec3 goal;
        if (a == Action.STRAFE_BARRAGE && stage == Stage.ACTIVE) {
            goal = aimA.lerp(aimB, Math.min(1, stageTicks / (double) Action.STRAFE_BARRAGE.activeTicks(ph)));
            speed = 0.9;
        } else if (ph == Phase.P0_LOCKDOWN && yardOrigin != null) {
            goal = YardLayout.cradle(yardOrigin).add(0, 0, Math.min(14, (yard == null ? 0 : yard.phaseTicks()) * 0.1)).with(net.minecraft.core.Direction.Axis.Y, floor + minH);
        } else {
            if (hoverGoal == null || --repositionIn <= 0 || hoverGoal.y < floor + minH - 0.5 || hoverGoal.y > floor + maxH + 0.5) {
                double ang = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 12;
                hoverGoal = center.add(Math.cos(ang) * r, minH + random.nextDouble() * (maxH - minH), Math.sin(ang) * r);
                repositionIn = 60 + random.nextInt(41);
            }
            goal = hoverGoal;
        }
        if (anchored) goal = position();
        if (yardOrigin != null) goal = YardLayout.clampLeash(yardOrigin, goal);
        Vec3 d = goal.subtract(position());
        if (d.length() > speed) d = d.normalize().scale(speed);
        setDeltaMovement(d);
        Vec3 next = position().add(d);
        if (yardOrigin != null) {
            // anti-void / anti-escape: never leave the leash box; snap back if something carried it far away
            if (next.distanceTo(Vec3.atCenterOf(yardOrigin)) > 60) next = Vec3.atBottomCenterOf(yardOrigin).add(0, 14, 0);
            next = YardLayout.clampLeash(yardOrigin, next);
        }
        setPos(next.x, next.y, next.z);
    }

    private void face(ServerLevel sl, List<ServerPlayer> ps) {
        Vec3 look = null;
        Entity t = target == null ? null : sl.getEntity(target);
        if (action() == Action.LASER_SWEEP && stage == Stage.ACTIVE) look = aimA.lerp(aimB, stageTicks / 60.0);
        else if (t != null) look = t.position();
        else if (!ps.isEmpty()) look = ps.get(0).position();
        if (look == null) return;
        Vec3 d = look.subtract(position());
        float want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), want, 6f);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ================================================================== action slot
    private void runAction(ServerLevel sl, @Nullable Yard yard, Phase ph, List<ServerPlayer> ps, long now) {
        Action a = action;
        switch (stage) {
            case IDLE -> {
                if (staggerTicks > 0 || ph.combatIndex() < 1) return;
                if (--gap > 0) return;
                Action pick = choose(sl, yard, ph, ps, now);
                if (pick != null) begin(sl, yard, ph, ps, pick);
                else gap = 10;
            }
            case TELEGRAPH -> {
                telegraph(sl, yard, ph, a, stageTicks);
                if (++stageTicks >= a.telegraph) setStage(Stage.ACTIVE);
            }
            case ACTIVE -> {
                activeTick(sl, yard, ph, a, stageTicks, ps);
                if (++stageTicks >= a.activeTicks(ph)) {
                    endActive(sl, a);
                    setStage(Stage.RECOVERY);
                    vulnerable = a.ventsOnRecovery();
                }
            }
            case RECOVERY -> {
                if (++stageTicks >= a.recovery) finish(ph, now);
            }
        }
    }

    private void setStage(Stage s) {
        stage = s;
        stageTicks = 0;
        entityData.set(STAGE, s.ordinal());
    }

    private void finish(Phase ph, long now) {
        Action a = action;
        if (a != null) readyAt.put(a, now + Math.round(a.cooldown / OverheadConfig.ATTACK_SPEED.get()));
        action = null;
        entityData.set(ACTION, -1);
        vulnerable = false;
        setStage(Stage.IDLE);
        int base = switch (ph) {
            case P1_SHIELDED -> 40;
            case P2_DEGRADED -> 25;
            case P3_EXPOSED -> 15;
            default -> 30;
        };
        if (brownout()) base = base * 3 / 2;
        gap = (int) Math.round(base / OverheadConfig.ATTACK_SPEED.get());
    }

    /** Cancel whatever is running (interrupts, commands, phase scripts). */
    public void cancel(String why) {
        Action a = action;
        if (a == null) return;
        if (level() instanceof ServerLevel sl) endActive(sl, a);
        if (a == Action.MISSILE_SALVO) triggerAnim("main", "missile_close");
        action = null;
        entityData.set(ACTION, -1);
        vulnerable = false;
        setStage(Stage.IDLE);
        gap = 20;
        readyAt.put(a, level().getGameTime() + a.cooldown / 2);
    }

    /** Designer command / yard script: start {@code a} now (skips weights and cooldowns, keeps the telegraph). */
    public boolean force(Action a) {
        if (!(level() instanceof ServerLevel sl)) return false;
        cancel("forced");
        Yard y = yard();
        begin(sl, y, phase(), players(sl), a);
        return true;
    }

    @Nullable
    private Action choose(ServerLevel sl, @Nullable Yard yard, Phase ph, List<ServerPlayer> ps, long now) {
        List<ServerPlayer> ts = targets(ps, false);
        boolean anyAir = targets(ps, true).stream().anyMatch(this::isAirborne);
        List<Action> bag = new ArrayList<>();
        for (Action a : Action.values()) {
            int w = a.weight(ph);
            if (w <= 0 || readyAt.getOrDefault(a, 0L) > now) continue;
            switch (a) {
                case FLAK_BURST -> { if (!anyAir) continue; }
                case PYLON_OVERCHARGE -> {
                    int i = overchargePylon(sl, yard, ps, now);
                    if (i < 0) continue;
                    if (yard.pylon(i).underAttack(now)) w *= 2;
                }
                case LASER_SWEEP, DESPERATION_CARPET -> {
                    if (brownout() || ts.isEmpty()) continue;
                    if (a == Action.DESPERATION_CARPET && ph == Phase.P3_EXPOSED && yard != null && yard.phaseTicks() < 200
                            && getHealth() > getMaxHealth() * 0.5f) continue;
                }
                case SUPPRESSION_FLARE -> { if (ts.stream().allMatch(this::painted)) continue; }
                default -> { if (ts.isEmpty()) continue; }
            }
            for (int i = 0; i < w; i++) bag.add(a);
        }
        return bag.isEmpty() ? null : bag.get(random.nextInt(bag.size()));
    }

    /** @return pylon slot to overcharge: ONLINE or REBUILDING with a player on its pad, preferring one under attack */
    private int overchargePylon(ServerLevel sl, @Nullable Yard yard, List<ServerPlayer> ps, long now) {
        if (yard == null) return -1;
        int best = -1;
        for (int i = 0; i < 4; i++) {
            Pylon p = yard.pylon(i);
            if (p.state == PylonState.OFFLINE) continue;
            if (onPad(ps, p).isEmpty()) continue;
            if (best < 0 || p.underAttack(now)) best = i;
        }
        return best;
    }

    private static List<ServerPlayer> onPad(List<ServerPlayer> ps, Pylon p) {
        Vec3 c = Vec3.atBottomCenterOf(p.core);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer pl : ps) {
            double dx = pl.getX() - c.x, dz = pl.getZ() - c.z, dy = pl.getY() - c.y;
            if (dx * dx + dz * dz <= 4.5 * 4.5 && dy > -1.5 && dy < 4 && !pl.isSpectator()) out.add(pl);
        }
        return out;
    }

    @Nullable
    private ServerPlayer pickTarget(ServerLevel sl, List<ServerPlayer> ps, Action a) {
        List<ServerPlayer> ts = targets(ps, a == Action.FLAK_BURST);
        if (ts.isEmpty()) return null;
        Comparator<ServerPlayer> byThreat = Comparator.comparingDouble(p -> -threat.getOrDefault(p.getUUID(), 0f) - (painted(p) ? 40 : 0));
        return switch (a) {
            case FLAK_BURST -> ts.stream().filter(this::isAirborne).findFirst().orElse(null);
            case MISSILE_SALVO -> ts.stream().filter(this::painted).findFirst().orElseGet(() -> ts.stream().min(byThreat).orElse(null));
            case LASER_SWEEP -> {
                List<ServerPlayer> los = ts.stream().filter(this::hasLineOfSight).toList();
                List<ServerPlayer> pool = los.isEmpty() ? ts : los;
                yield pool.get(random.nextInt(pool.size()));
            }
            case STRAFE_BARRAGE -> ts.stream().min(Comparator.comparingDouble(p -> p.position().subtract(position()).horizontalDistanceSqr())).orElse(null);
            case SUPPRESSION_FLARE -> ts.stream().filter(p -> !painted(p)).min(byThreat).orElse(null);
            default -> random.nextFloat() < 0.3f ? ts.get(random.nextInt(ts.size())) : ts.stream().min(byThreat).orElse(null);
        };
    }

    private void begin(ServerLevel sl, @Nullable Yard yard, Phase ph, List<ServerPlayer> ps, Action a) {
        action = a;
        entityData.set(ACTION, a.ordinal());
        setStage(Stage.TELEGRAPH);
        fired = 0;
        ServerPlayer t = pickTarget(sl, ps, a);
        target = t == null ? null : t.getUUID();
        switch (a) {
            case HOWITZER_LOB -> triggerAnim("main", "howitzer_load");
            case MISSILE_SALVO -> {
                triggerAnim("main", "missile_open");
                if (t != null) {
                    notify(t, "overhead.missile.lock", 1.2f);
                    t.displayClientMessage(Component.translatable("overhead.warn.missile_lock").withStyle(ChatFormatting.RED), true);
                }
            }
            case LASER_SWEEP -> {
                triggerAnim("main", "laser_charge");
                Vec3 tp = t == null ? position() : t.position();
                Vec3 across = new Vec3(tp.x - getX(), 0, tp.z - getZ());
                across = across.lengthSqr() < 1 ? new Vec3(1, 0, 0) : across.normalize();
                Vec3 perp = new Vec3(-across.z, 0, across.x);
                double fy = floorY() + 0.1;
                aimA = clampFloor(new Vec3(tp.x, fy, tp.z).add(perp.scale(-18)));
                aimB = clampFloor(new Vec3(tp.x, fy, tp.z).add(perp.scale(18)));
                warnAll(ps, "overhead.warn.laser");
            }
            case STRAFE_BARRAGE -> {
                triggerAnim("main", "strafe_spinup");
                Vec3 tp = t == null ? position() : t.position();
                double h = Math.max(floorY() + 6, getY() - 3);
                boolean alongZ = random.nextBoolean();
                double L = YardLayout.LEASH;
                Vec3 c = yardOrigin != null ? Vec3.atBottomCenterOf(yardOrigin) : home.add(0, -14, 0);
                Vec3 p1 = alongZ ? new Vec3(tp.x, h, c.z - L) : new Vec3(c.x - L, h, tp.z);
                Vec3 p2 = alongZ ? new Vec3(tp.x, h, c.z + L) : new Vec3(c.x + L, h, tp.z);
                boolean flip = p1.distanceToSqr(position()) > p2.distanceToSqr(position());
                aimA = flip ? p2 : p1;
                aimB = flip ? p1 : p2;
            }
            case SUPPRESSION_FLARE -> AnimFx.play(sl, position(), "overhead.flare.launch", 3f, 0.8f);
            case PYLON_OVERCHARGE -> {
                pylonIdx = overchargePylon(sl, yard, ps, sl.getGameTime());
                if (pylonIdx < 0 || yard == null) { cancel("no pylon"); return; }
                triggerAnim("main", "overcharge");
                yard.pylonAnim(sl, pylonIdx, "overcharge");
                for (ServerPlayer p : onPad(ps, yard.pylon(pylonIdx)))
                    p.displayClientMessage(Component.translatable("overhead.warn.overcharge").withStyle(ChatFormatting.AQUA), true);
            }
            case DESPERATION_CARPET -> {
                triggerAnim("main", "carpet");
                pickLanes(ps, ph);
                warnAll(ps, "overhead.warn.carpet");
            }
            case REARM_SHIELD_PULSE -> AnimFx.play(sl, position(), "overhead.rearm.siren", 12f, 1f);
            case POWER_ON_PULSE -> AnimFx.play(sl, position(), "overhead.power.charge", 8f, 1.2f);
            case FLAK_BURST -> { if (t != null) notify(t, "overhead.missile.lock", 1.6f); }
        }
    }

    private Vec3 clampFloor(Vec3 p) {
        if (yardOrigin == null) return p;
        int H = YardLayout.HALF - 1;
        return new Vec3(Mth.clamp(p.x, yardOrigin.getX() - H, yardOrigin.getX() + H + 1), p.y,
                Mth.clamp(p.z, yardOrigin.getZ() - H, yardOrigin.getZ() + H + 1));
    }

    private void pickLanes(List<ServerPlayer> ps, Phase ph) {
        java.util.Arrays.fill(lanes, false);
        int count = ph == Phase.P3_EXPOSED && getHealth() < getMaxHealth() * 0.25f ? 3 : 2;
        int[] occ = new int[YardLayout.LANES];
        for (ServerPlayer p : ps) {
            int k = yardOrigin == null ? -1 : YardLayout.lane(yardOrigin, p.getX());
            if (k >= 0) occ[k]++;
        }
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        java.util.Collections.shuffle(order, new java.util.Random(random.nextLong()));
        order.sort(Comparator.comparingInt(k -> -occ[k]));
        for (int i = 0; i < count; i++) lanes[order.get(i)] = true;   // count <= 3: one lane is always safe
    }

    private void telegraph(ServerLevel sl, @Nullable Yard yard, Phase ph, Action a, int t) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case HOWITZER_LOB -> {
                if (t % 4 != 0 || tgt == null) return;
                Vec3 g = groundAt(sl, tgt.position());
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), g, 4, 18);
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), g, 2, 8);
            }
            case LASER_SWEEP -> {
                if (t % 4 == 0) Telegraph.line(sl, SBParticles.TARGET_MARK.get(), aimA, aimB, 1.0);
                if (t % 4 == 2) Telegraph.line(sl, SBParticles.RED_BEAM.get(), loc("lens"), aimA, 2.0);
            }
            case STRAFE_BARRAGE -> {
                if (t % 3 == 0) Telegraph.line(sl, SBParticles.RAIL_MARK.get(),
                        new Vec3(aimA.x, floorY() + 0.1, aimA.z), new Vec3(aimB.x, floorY() + 0.1, aimB.z), 1.0);
            }
            case PYLON_OVERCHARGE -> {
                if (yard == null || pylonIdx < 0) return;
                Vec3 top = Vec3.atBottomCenterOf(yard.pylon(pylonIdx).core).add(0, 5, 0);
                if (t % 2 == 0) Telegraph.line(sl, SBParticles.GRID_ARC.get(), loc("lens"), top, 1.2);
                if (t % 4 == 0) Telegraph.ring(sl, SBParticles.GRID_ARC.get(), Vec3.atBottomCenterOf(yard.pylon(pylonIdx).core).add(0, -0.9, 0), 4.5, 20);
                entityData.set(BEAM, top.toVector3f());
            }
            case DESPERATION_CARPET -> {
                if (t % 5 != 0 || yardOrigin == null) return;
                for (int k = 0; k < YardLayout.LANES; k++) {
                    if (!lanes[k]) continue;
                    double x0 = YardLayout.laneMinX(yardOrigin, k);
                    for (double x = x0 + 1.5; x < x0 + YardLayout.LANE_WIDTH; x += 4) {
                        Telegraph.line(sl, SBParticles.TARGET_MARK.get(), new Vec3(x, floorY() + 0.1, yardOrigin.getZ() - 28),
                                new Vec3(x, floorY() + 0.1, yardOrigin.getZ() + 29), 2);
                    }
                }
            }
            case REARM_SHIELD_PULSE -> { if (t % 5 == 0) Telegraph.ring(sl, SBParticles.GRID_ARC.get(), new Vec3(getX(), floorY() + 0.1, getZ()), 12, 36); }
            case POWER_ON_PULSE -> { if (t % 5 == 0) Telegraph.ring(sl, SBParticles.GRID_ARC.get(), new Vec3(getX(), floorY() + 0.1, getZ()), 4 + t * 0.55, 40); }
            default -> {}
        }
    }

    private void activeTick(ServerLevel sl, @Nullable Yard yard, Phase ph, Action a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case HOWITZER_LOB -> {
                int[] schedule = ph == Phase.P1_SHIELDED ? new int[]{0} : ph == Phase.P2_DEGRADED ? new int[]{0, 10} : new int[]{0, 8, 16};
                double lead = ph == Phase.P1_SHIELDED ? 0 : ph == Phase.P2_DEGRADED || ph == Phase.P4_REARM ? 0.5 : 1.0;
                for (int k = 0; k < schedule.length; k++) {
                    if (t != schedule[k] || tgt == null) continue;
                    Vec3 v = tgt.getDeltaMovement();
                    Vec3 lead3 = new Vec3(v.x, 0, v.z).scale(40 * lead);
                    if (lead3.length() > 6) lead3 = lead3.normalize().scale(6);
                    Vec3 walk = new Vec3(tgt.getX() - getX(), 0, tgt.getZ() - getZ());
                    walk = walk.lengthSqr() < 1 ? Vec3.ZERO : walk.normalize().scale((k - (schedule.length - 1) / 2.0) * 3);
                    Vec3 at = clampFloor(groundAt(sl, tgt.position().add(lead3).add(walk)));
                    sl.addFreshEntity(Ordnance.shell(sl, this, loc("muzzle"), at, 40));
                    triggerAnim("main", "howitzer_fire");
                }
            }
            case MISSILE_SALVO -> {
                if (t % 4 != 0 || fired >= Action.missiles(ph)) return;
                boolean left = fired % 2 == 0;
                Vec3 from = loc(left ? "bay_l" : "bay_r");
                Vec3 side = from.subtract(position()).multiply(1, 0, 1).normalize().scale(0.3);
                Vec3 vel = new Vec3(side.x + (random.nextDouble() - 0.5) * 0.2, 0.6, side.z + (random.nextDouble() - 0.5) * 0.2);
                float turn = switch (ph) {
                    case P2_DEGRADED -> 5f;
                    case P3_EXPOSED -> 6f;
                    default -> 4f;
                } + (tgt != null && painted(tgt) ? 2f : 0f);
                sl.addFreshEntity(Ordnance.missile(sl, this, from, vel, tgt, turn));
                AnimFx.play(sl, from, "overhead.missile.launch", 3f, 0.9f + random.nextFloat() * 0.2f);
                fired++;
            }
            case LASER_SWEEP -> laserTick(sl, ph, t);
            case STRAFE_BARRAGE -> {
                if (t % 3 != 0 || tgt == null) return;
                Vec3 from = loc("gatling");
                Vec3 dir = tgt.getBoundingBox().getCenter().subtract(from).normalize();
                dir = dir.xRot((float) Math.toRadians((random.nextDouble() - 0.5) * 6)).yRot((float) Math.toRadians((random.nextDouble() - 0.5) * 6));
                sl.addFreshEntity(Ordnance.bolt(sl, this, from, dir.scale(2.2)));
                AnimFx.play(sl, from, "overhead.strafe.fire", 2f, 0.9f + random.nextFloat() * 0.2f);
            }
            case SUPPRESSION_FLARE -> {
                if (t != 0 || tgt == null) return;
                triggerAnim("main", "flare_fire");
                Vec3 from = loc("flare");
                Vec3 at = tgt.position().add(0, 8, 0);
                int T = 30;
                double g = 0.03;
                Vec3 d = at.subtract(from);
                sl.addFreshEntity(Ordnance.flare(sl, this, from, new Vec3(d.x / T, (d.y + g * T * (T - 1) / 2.0) / T, d.z / T), T));
            }
            case PYLON_OVERCHARGE -> {
                if (t != 0 || yard == null || pylonIdx < 0) return;
                Pylon p = yard.pylon(pylonIdx);
                Vec3 pad = Vec3.atBottomCenterOf(p.core);
                for (ServerPlayer pl : onPad(ps, p)) {
                    pl.hurt(damageSources().indirectMagic(this, this), OverheadConfig.OVERCHARGE_DAMAGE.get().floatValue());
                    Vec3 away = pl.position().subtract(pad).multiply(1, 0, 1);
                    away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
                    pl.push(away.x * 1.5, 0.5, away.z * 1.5);
                    pl.hurtMarked = true;
                    pl.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
                yard.overchargeRepair(sl, pylonIdx, OverheadConfig.OVERCHARGE_REPAIR.get().floatValue());
                Telegraph.line(sl, SBParticles.RED_BEAM.get(), loc("lens"), pad.add(0, 5, 0), 0.8);
                AnimFx.serverBurst(sl, SBParticles.GRID_ARC.get(), pad.add(0, 0.5, 0), 80, 3, 0.4);
                AnimFx.play(sl, pad, "overhead.overcharge.discharge", 6f, 1f);
            }
            case DESPERATION_CARPET -> {
                if (t != 0 || yardOrigin == null) return;
                long now = sl.getGameTime();
                for (int k = 0; k < YardLayout.LANES; k++) {
                    if (!lanes[k]) continue;
                    double x0 = YardLayout.laneMinX(yardOrigin, k);
                    for (int i = 0; i < 12; i++) {
                        Vec3 p = new Vec3(x0 + 1 + random.nextDouble() * (YardLayout.LANE_WIDTH - 2), floorY(),
                                yardOrigin.getZ() - 28 + random.nextDouble() * 57);
                        impacts.add(new Impact(p, now + 10 + random.nextInt(31)));
                    }
                }
            }
            case REARM_SHIELD_PULSE -> {
                if (t != 0) return;
                shieldTicks = a.active;
                triggerAnim("main", "shield_pulse");
                pulse(sl, ps, 12, OverheadConfig.SHIELD_PULSE_DAMAGE.get().floatValue(), 1.2);
            }
            case POWER_ON_PULSE -> {
                if (t != 0) return;
                triggerAnim("main", "power_pulse");
                pulse(sl, ps, 20, 0, 1.2);
            }
            case FLAK_BURST -> {
                if (t % 6 != 0 || tgt == null) return;
                Vec3 from = loc("gatling");
                double dist = from.distanceTo(tgt.position());
                int flight = (int) Math.max(4, dist / 1.6);
                Vec3 at = tgt.getBoundingBox().getCenter().add(tgt.getDeltaMovement().scale(flight));
                sl.addFreshEntity(Ordnance.flak(sl, this, from, at.subtract(from).normalize().scale(1.6), tgt, flight + 4));
                AnimFx.play(sl, from, "overhead.flak.fire", 3f, 1f);
            }
        }
    }

    private void laserTick(ServerLevel sl, Phase ph, int t) {
        int n = Action.LASER_SWEEP.activeTicks(ph);
        Vec3 from = loc("lens");
        Vec3 ground = aimA.lerp(aimB, t / (double) Math.max(1, n - 1));
        Vec3 dir = ground.subtract(from).normalize();
        Vec3 far = ground.add(dir.scale(4));
        BlockHitResult hit = sl.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        entityData.set(BEAM, end.toVector3f());
        entityData.set(BEAM_ON, true);
        if (t % 2 == 0) AnimFx.serverBurst(sl, SBParticles.EMBER.get(), end, 4, 0.3, 0.1);
        if (t % 20 == 0) AnimFx.play(sl, from, "overhead.laser.loop", 4f, 1f);
        if (t % 5 != 0) return;
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(from, end).inflate(1.5), p -> p.isAlive() && !p.isSpectator())) {
            if (p.getBoundingBox().inflate(0.6).clip(from, end).isPresent()) {
                p.hurt(damageSources().indirectMagic(this, this), OverheadConfig.LASER_DAMAGE.get().floatValue());
                p.igniteForSeconds(3f);
            }
        }
    }

    private void endActive(ServerLevel sl, Action a) {
        switch (a) {
            case LASER_SWEEP -> {
                entityData.set(BEAM_ON, false);
                if (stage == Stage.ACTIVE) triggerAnim("main", "laser_vent");
            }
            case MISSILE_SALVO -> triggerAnim("main", "missile_close");
            default -> {}
        }
    }

    /** Radial pulse from the chassis: blocked by cover (line of sight from the core). */
    private void pulse(ServerLevel sl, List<ServerPlayer> ps, double radius, float dmg, double knock) {
        Vec3 c = loc("core");
        AnimFx.serverBurst(sl, SBParticles.SHIELD.get(), c, 120, 2.5, 0.6);
        for (ServerPlayer p : ps) {
            Vec3 d = p.position().subtract(c);
            if (new Vec3(d.x, 0, d.z).length() > radius || p.isSpectator()) continue;
            if (Blast.covered(sl, c, p.getEyePosition())) continue;
            if (dmg > 0) p.hurt(damageSources().indirectMagic(this, this), dmg);
            Vec3 h = new Vec3(d.x, 0, d.z);
            h = h.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : h.normalize();
            p.push(h.x * knock, 0.4, h.z * knock);
            p.hurtMarked = true;
        }
    }

    private void tickImpacts(ServerLevel sl, long now) {
        for (Iterator<Impact> it = impacts.iterator(); it.hasNext(); ) {
            Impact im = it.next();
            long dt = im.at - now;
            if (dt > 0 && dt <= 10) {
                Vec3 p = im.pos.add(0, dt * 1.6, 0);
                Telegraph.point(sl, SBParticles.EMBER.get(), p, 2, 0.1);
                Telegraph.point(sl, SBParticles.SMOKE.get(), p.add(0, 0.8, 0), 1, 0.1);
            }
            if (dt <= 0) {
                Blast.detonate(sl, this, im.pos.add(0, 0.1, 0), 2.5, OverheadConfig.CARPET_DAMAGE.get().floatValue(), 1.5, "overhead.carpet.impact");
                it.remove();
            }
        }
    }

    /** Suppression flare burst (called by the flare round): paints players who can see it. */
    public void flareBurst(ServerLevel sl, Vec3 at) {
        AnimFx.serverBurst(sl, SBParticles.TARGET_MARK.get(), at, 40, 2, 0.3);
        AnimFx.serverBurst(sl, SBParticles.EMBER.get(), at, 30, 2, 0.3);
        AnimFx.play(sl, at, "overhead.flare.burst", 6f, 1f);
        long until = sl.getGameTime() + 160;
        for (ServerPlayer p : players(sl)) {
            if (p.distanceToSqr(at) > 12 * 12 || p.isSpectator() || Blast.covered(sl, at, p.getEyePosition())) continue;
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
            paintedUntil.put(p.getUUID(), until);
            addThreat(p.getUUID(), 40);
            Vec3 toBurst = at.subtract(p.getEyePosition()).normalize();
            if (p.getLookAngle().dot(toBurst) > 0.6) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
            p.displayClientMessage(Component.translatable("overhead.warn.painted").withStyle(ChatFormatting.RED), true);
        }
    }

    // ================================================================== yard callbacks
    /** A pylon died: interrupt what can be interrupted, then stagger (DESIGN.md §6). */
    public void onPylonBroken() {
        Action a = action;
        if (a != null && a.interruptible(stage == Stage.TELEGRAPH)) cancel("pylon break");
        staggerTicks = OverheadConfig.STAGGER_TICKS.get();
        triggerAnim("main", "hurt");
        hoverGoal = null;
    }

    /** Scripted beats (power-on pulse in P0, shield pulse at P4 entry). */
    public void script(Action a) {
        force(a);
    }

    public void resetForPhase() {
        cancel("phase");
        impacts.clear();
        hoverGoal = null;
        entityData.set(BEAM_ON, false);
    }

    // ================================================================== damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() instanceof OverheadEntity || source.getDirectEntity() instanceof Ordnance) return false;
        Yard yard = yard();
        float mult = yard == null ? 1f : yard.damageMultiplier();
        if (shieldTicks > 0) mult = 0;
        if (vulnerable) mult *= OverheadConfig.VULNERABLE_MULT.get().floatValue();
        Entity attacker = source.getEntity();
        ServerLevel sl = (ServerLevel) level();
        if (attacker instanceof ServerPlayer p && mult < 0.5f) {
            long now = sl.getGameTime();
            if (gateMsgAt.getOrDefault(p.getUUID(), 0L) < now) {
                gateMsgAt.put(p.getUUID(), now + 100);
                int pct = Math.round((1 - mult) * 100);
                p.displayClientMessage(Component.translatable(mult <= 0 ? "overhead.gate.immune" : "overhead.gate.absorbed", pct)
                        .withStyle(ChatFormatting.AQUA), true);
            }
            AnimFx.serverBurst(sl, SBParticles.SHIELD.get(), source.getSourcePosition() != null ? source.getSourcePosition() : getBoundingBox().getCenter(), 10, 0.4, 0.2);
        }
        if (mult <= 0) {
            AnimFx.play(sl, position(), "overhead.shield.deflect", 2f, 1.2f);
            return false;
        }
        boolean r = super.hurt(source, amount * mult);
        if (r) {
            if (attacker instanceof Player p) addThreat(p.getUUID(), amount * mult);
            triggerAnim("main", "hurt");
            long now = sl.getGameTime();
            if (now - burstStart > 40) { burstStart = now; burst = 0; }
            burst += amount * mult;
            if (burst >= getMaxHealth() * 0.10f && now > staggerLockUntil && action != null && stage == Stage.TELEGRAPH
                    && action.interruptible(true)) {
                cancel("damage stagger");
                staggerTicks = 30;
                staggerLockUntil = now + 200;
            }
            if (yard != null) yard.onOverheadDamaged(this);
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            entityData.set(BEAM_ON, false);
            impacts.clear();
            triggerAnim("main", "death");
            Yard y = yard();
            if (y != null) y.onOverheadDied(sl, this, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl) {
            double floor = floorY() + 0.2;
            if (getY() > floor) setPos(getX(), Math.max(floor, getY() - 0.12), getZ());
            if (deathTime % 8 == 0) {
                Vec3 c = getBoundingBox().getCenter().add((random.nextDouble() - 0.5) * 4, random.nextDouble(), (random.nextDouble() - 0.5) * 4);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                AnimFx.play(sl, c, "overhead.missile.impact", 4f, 0.7f);
            }
            if (deathTime >= 120) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 1, 1, 1, 0);
                AnimFx.serverBurst(sl, SBParticles.SMOKE.get(), position().add(0, 1, 0), 80, 3, 0.1);
                remove(RemovalReason.KILLED);
            }
        }
    }

    private void notify(ServerPlayer p, String sound, float pitch) {
        SoundEvent s = SBSounds.get(sound);
        if (s != null) p.playNotifySound(s, SoundSource.HOSTILE, 1f, pitch);
    }

    private void warnAll(List<ServerPlayer> ps, String key) {
        Component c = Component.translatable(key).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : ps) p.displayClientMessage(c, true);
    }

    /** Surface under a point: first solid block within 30 below, else the yard floor. */
    private Vec3 groundAt(ServerLevel sl, Vec3 p) {
        BlockHitResult hit = sl.clip(new ClipContext(p.add(0, 1, 0), p.add(0, -30, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : new Vec3(p.x, floorY(), p.z);
    }

    // ================================================================== client
    private void clientFx() {
        if (beamOn() && action() == Action.LASER_SWEEP) {
            Vec3 from = loc("lens");
            Vec3 to = beamEnd();
            double len = from.distanceTo(to);
            Vec3 dir = to.subtract(from).normalize();
            for (double d = 0; d < len; d += 0.7) {
                Vec3 p = from.add(dir.scale(d));
                level().addParticle(SBParticles.RED_BEAM.get(), p.x, p.y, p.z, 0, 0, 0);
            }
        }
        if (tickCount % 6 == 0 && !isDeadOrDying()) {
            for (int i = (tickCount / 6) % 2; i < 4; i += 2) {
                Vec3 t = loc("thruster_" + i);
                level().addParticle(brownout() ? SBParticles.SPARK.get() : SBParticles.SMOKE.get(), t.x, t.y, t.z, 0, -0.05, 0);
            }
        }
    }

    // ================================================================== misc
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(4, 3, 4); }

    public String debugState() {
        Action a = action;
        return String.format("overhead hp=%.0f/%.0f action=%s stage=%s t=%d gap=%d stagger=%d shield=%d vuln=%s pos=%s",
                getHealth(), getMaxHealth(), a == null ? "-" : a.id(), stage, stageTicks, gap, staggerTicks, shieldTicks, vulnerable,
                blockPosition().toShortString());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (yardOrigin != null) tag.put("Yard", NbtUtils.writeBlockPos(yardOrigin));
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        yardOrigin = NbtUtils.readBlockPos(tag, "Yard").orElse(null);
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        // a reloaded chassis resumes idle; whatever it was doing is dropped
        gap = 40;
    }

    private static String anim(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<OverheadEntity> c = new AnimationController<>(this, "main", 4, s -> {
            Action a = action();
            if (isDeadOrDying()) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(anim("death")));
            if (a == Action.LASER_SWEEP && stage() == Stage.ACTIVE) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("laser_fire")));
            if (a == Action.STRAFE_BARRAGE && stage() == Stage.ACTIVE) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("strafe")));
            if (brownout()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("brownout")));
            return s.setAndContinue(RawAnimation.begin().thenLoop(anim("idle")));
        });
        for (String n : new String[]{"power_on", "power_pulse", "howitzer_load", "howitzer_fire", "missile_close", "laser_charge",
                "laser_vent", "strafe_spinup", "flare_fire", "overcharge", "carpet", "shield_pulse", "hurt"}) {
            c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        }
        c.triggerableAnim("missile_open", RawAnimation.begin().thenPlayAndHold(anim("missile_open")));
        c.triggerableAnim("death", RawAnimation.begin().thenPlayAndHold(anim("death")));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, MODEL, SCALE, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        c.setSoundKeyframeHandler(e -> AnimFx.keyframeSound(this, e.getKeyframeData().getSound(), 4.0f));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
