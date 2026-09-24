package net.teamaof.skylorebosses.bosses.staticdeacon.entity;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.teamaof.skylorebosses.bosses.staticdeacon.DeaconConfig;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Crypt;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.CryptLayout;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Crypts;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Flagstone;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Phase;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.fx.Telegraph;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.registry.SBSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Static Deacon (DESIGN.md §5, §9). A walking crystalline caretaker with one action slot:
 * IDLE -> TELEGRAPH -> ACTIVE -> RECOVERY -> IDLE. The crypt owns phase, floor and the heal/damage gate; this entity
 * owns movement (it walks to live flagstones, or to the plinth), targeting and the attacks. Spawned outside a crypt it
 * runs "bench test" mode: no floor rules, P2 attack table, staying near where it was placed.
 */
public class StaticDeaconEntity extends Monster implements GeoEntity {
    public static final String MODEL = "static_deacon";
    public static final float SCALE = 1.0f;
    public enum Stage { IDLE, TELEGRAPH, ACTIVE, RECOVERY }

    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(StaticDeaconEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(StaticDeaconEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COMMUNE = SynchedEntityData.defineId(StaticDeaconEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STAGGERED = SynchedEntityData.defineId(StaticDeaconEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STEPPING = SynchedEntityData.defineId(StaticDeaconEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TETHER = SynchedEntityData.defineId(StaticDeaconEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos cryptOrigin;
    @Nullable private Vec3 home;
    // action slot
    @Nullable private DeaconAction action;
    private Stage stage = Stage.IDLE;
    private int stageTicks, gap = 40, staggerTicks, vulnTicks;
    private boolean vulnerable, comboLash, pendingRite;
    private long plinthLockUntil, riteRecastAt;
    private final Map<DeaconAction, Long> readyAt = new EnumMap<>(DeaconAction.class);
    // per-action scratch
    @Nullable private UUID target;
    private float lockedYaw;
    private int laneCx, laneCz, fired;
    private boolean cross;
    private final Set<UUID> hitThisCast = new HashSet<>();
    // movement
    @Nullable private Vec3 stance;
    private int stanceIn, stuckTicks, plinthWait;
    @Nullable private Vec3 lastProgress;
    @Nullable private Vec3 stepTo;
    private int stepTicks;
    // targeting memory and damage bookkeeping
    private final Map<UUID, Float> threat = new HashMap<>();
    private final Map<UUID, Long> gateMsgAt = new HashMap<>();
    private float burst, vigilDmg;
    private long burstStart, staggerLockUntil, vigilStart, hurtAnimAt;

    public StaticDeaconEntity(EntityType<? extends StaticDeaconEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 800)
                .add(Attributes.ARMOR, 6)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.ATTACK_DAMAGE, 9);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ACTION, -1);
        b.define(STAGE, 0);
        b.define(COMMUNE, 0);
        b.define(STAGGERED, false);
        b.define(STEPPING, false);
        b.define(TETHER, -1);
    }

    @Override
    protected void registerGoals() {}

    // ================================================================== crypt link
    public void bindCrypt(BlockPos origin) {
        cryptOrigin = origin.immutable();
    }

    @Nullable
    public BlockPos cryptOrigin() { return cryptOrigin; }

    @Nullable
    private Crypt crypt() {
        return cryptOrigin == null || !(level() instanceof ServerLevel sl) ? null : Crypts.get(sl).at(cryptOrigin);
    }

    public Phase phase() {
        Crypt c = crypt();
        return c != null ? c.phase() : Phase.P2_PATCHWORK;
    }

    @Nullable public DeaconAction action() {
        int i = entityData.get(ACTION);
        return i < 0 ? null : DeaconAction.values()[i];
    }

    public Stage stage() { return Stage.values()[entityData.get(STAGE)]; }
    public Crypt.Commune communeSynced() { return Crypt.Commune.values()[entityData.get(COMMUNE)]; }
    public boolean staggered() { return entityData.get(STAGGERED); }
    public boolean stepping() { return entityData.get(STEPPING); }
    public int tether() { return entityData.get(TETHER); }
    /** Channelling the reseed rite (rite DR applies). */
    public boolean channeling() { return action == DeaconAction.RESEED_RITE && stage == Stage.ACTIVE; }

    public Vec3 loc(String name) { return AnimFx.locator(this, MODEL, SCALE, name); }

    /** Crypt origin, or a stand-in under the spawn point in bench-test mode. */
    private BlockPos originOrHome() {
        return cryptOrigin != null ? cryptOrigin : BlockPos.containing(home == null ? position() : home).below();
    }

    private double floorY() { return originOrHome().getY() + 1; }

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
        Crypt crypt = crypt();
        if (cryptOrigin != null && crypt == null) { discard(); return; }
        if (home == null) home = position();
        Phase ph = phase();
        long now = sl.getGameTime();
        List<ServerPlayer> ps = players(sl);
        if (now % 20 == 0) threat.replaceAll((k, v) -> v * 0.98f);
        if (staggerTicks > 0) staggerTicks--;
        if (vulnTicks > 0) vulnTicks--;
        Crypt.Commune commune = crypt == null ? Crypt.Commune.NONE : crypt.commune(this);
        entityData.set(COMMUNE, commune.ordinal());
        entityData.set(STAGGERED, staggerTicks > 0);
        if (crypt != null && (!CryptLayout.leash(cryptOrigin).contains(position()) || getY() < cryptOrigin.getY() - 2)) {
            // anti-escape / anti-void: anything that carries it out of the nave loses it back to the altar at once
            teleportTo(CryptLayout.plinthStand(cryptOrigin).x, CryptLayout.plinthStand(cryptOrigin).y, CryptLayout.plinthStand(cryptOrigin).z);
            AnimFx.serverBurst(sl, SBParticles.get("static_dust"), position().add(0, 1.5, 0), 40, 0.8, 0.1);
        }
        if (stepTo != null) {
            tickStep(sl);
            return;
        }
        move(sl, crypt, ph, commune, ps, now);
        face(sl, ps);
        runAction(sl, crypt, ph, commune, ps, now);
    }

    private List<ServerPlayer> players(ServerLevel sl) {
        Crypt c = crypt();
        if (c != null) return c.participants(sl);
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

    public void addThreat(UUID player, float amount) {
        threat.merge(player, amount, Float::sum);
    }

    // ================================================================== movement (DESIGN.md §9 idle/reposition)
    private void move(ServerLevel sl, @Nullable Crypt crypt, Phase ph, Crypt.Commune commune, List<ServerPlayer> ps, long now) {
        if (action != null || staggerTicks > 0 || ph == Phase.P0_VESTING || !ph.fighting()) {
            getNavigation().stop();
            return;
        }
        boolean locked = now < plinthLockUntil;
        boolean wantPlinth = crypt != null && !locked && (ph == Phase.P3_VIGIL || ph == Phase.P4_RESEED);
        Vec3 goal;
        ServerPlayer near = nearest(targets(ps));
        if (wantPlinth) {
            goal = CryptLayout.plinthStand(cryptOrigin);
            if (commune == Crypt.Commune.PLINTH) { getNavigation().stop(); plinthWait = 0; return; }
            // P4 must reach the altar to channel: it gives up walking after 60 ticks and steps through the static
            if (ph == Phase.P4_RESEED && ++plinthWait > 60) { beginStep(goal); plinthWait = 0; return; }
        } else if (crypt == null) {
            // bench test: shuffle toward the target but stay near home
            Vec3 t = near == null ? home : near.position();
            Vec3 d = t.subtract(home);
            goal = d.length() > 8 ? home.add(d.normalize().scale(8)) : t;
        } else if (locked) {
            // knocked off the altar: hold ground a few blocks off the dais, toward whoever broke the vigil
            Vec3 c = CryptLayout.plinthStand(cryptOrigin);
            Vec3 dir = near == null ? new Vec3(0, 0, 1) : near.position().subtract(c).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : dir.normalize();
            goal = new Vec3(c.x + dir.x * 4, floorY(), c.z + dir.z * 4);
        } else {
            // P1/P2: stand on the live flagstone that best covers the target; re-evaluated every 40 ticks or when it dies
            if (stance == null || --stanceIn <= 0 || !stanceLive(crypt)) {
                stance = chooseStance(crypt, near);
                stanceIn = 40;
            }
            if (stance == null) {
                goal = CryptLayout.plinthStand(cryptOrigin);
            } else {
                goal = stance;
                Flagstone here = crypt.flagstoneAt(BlockPos.containing(getX(), getY() - 0.2, getZ()));
                Flagstone want = crypt.flagstoneAt(BlockPos.containing(stance.x, stance.y - 0.5, stance.z));
                if (commune == Crypt.Commune.NAVE && here == want) { getNavigation().stop(); stuckTicks = 0; return; }
            }
        }
        double speed = switch (ph) {
            case P1_COMMUNION -> 1.0;
            case P2_PATCHWORK -> 1.1;
            default -> 1.2;
        };
        if (position().distanceTo(goal) < 3) {
            // final approach: vanilla paths finish within a block of the target (and offset large mobs by half a
            // block), which can leave the Deacon standing on the dead flagstone next to its stance
            getNavigation().stop();
            getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
        } else if (getNavigation().isDone() || tickCount % 10 == 0) {
            getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
        }
        // stuck: no progress for 80 ticks while the goal is more than 2 blocks away -> static step
        if (lastProgress == null || position().distanceTo(lastProgress) > 0.6) {
            lastProgress = position();
            stuckTicks = 0;
        } else if (position().distanceTo(goal) > 2 && ++stuckTicks > 80) {
            stuckTicks = 0;
            beginStep(goal);
        }
    }

    private boolean stanceLive(Crypt crypt) {
        if (stance == null) return false;
        Flagstone f = crypt.flagstoneAt(BlockPos.containing(stance.x, stance.y - 0.5, stance.z));
        return f != null && f.live();
    }

    @Nullable
    private Vec3 chooseStance(Crypt crypt, @Nullable ServerPlayer near) {
        List<Flagstone> live = crypt.liveFlagstones();
        if (live.isEmpty()) return null;
        Vec3 me = position();
        Vec3 want = near == null ? me : near.position();
        Flagstone best = live.stream().min(Comparator.comparingDouble(f -> {
            Vec3 c = Vec3.atBottomCenterOf(f.center);
            return c.distanceTo(want) + 0.5 * c.distanceTo(me);
        })).orElse(null);
        return best == null ? null : Vec3.atBottomCenterOf(best.center).add(0, 1, 0);
    }

    @Nullable
    private ServerPlayer nearest(List<ServerPlayer> ts) {
        return ts.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
    }

    private void face(ServerLevel sl, List<ServerPlayer> ps) {
        DeaconAction a = action;
        float want;
        if (a == DeaconAction.LATTICE_LASH && stage != Stage.RECOVERY) {
            want = lockedYaw;
        } else {
            Entity t = target == null ? null : sl.getEntity(target);
            if (t == null) t = nearest(targets(ps));
            if (t == null) return;
            if (a == null && !getNavigation().isDone()) return;   // walking: the body follows the path
            Vec3 d = t.position().subtract(position());
            want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        }
        float yaw = Mth.approachDegrees(getYRot(), want, 12f);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // ================================================================== static step (teleport with a telegraph)
    private void beginStep(Vec3 to) {
        if (stepTo != null) return;
        stepTo = to;
        stepTicks = 20;
        entityData.set(STEPPING, true);
        getNavigation().stop();
        triggerAnim("main", "step_out");
        if (level() instanceof ServerLevel sl) AnimFx.play(sl, position(), "static_deacon.step", 3f, 1f);
    }

    private void tickStep(ServerLevel sl) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (stepTicks % 2 == 0) {
            AnimFx.serverBurst(sl, SBParticles.get("static_dust"), position().add(0, 1.6, 0), 8, 0.5, 0.05);
            Telegraph.point(sl, SBParticles.get("static_dust"), stepTo.add(0, 1.2, 0), 6, 0.4);
        }
        if (--stepTicks > 0) return;
        teleportTo(stepTo.x, stepTo.y, stepTo.z);
        stepTo = null;
        entityData.set(STEPPING, false);
        triggerAnim("main", "step_in");
        AnimFx.serverBurst(sl, SBParticles.get("static_dust"), position().add(0, 1.6, 0), 50, 0.7, 0.1);
        AnimFx.play(sl, position(), "static_deacon.step", 3f, 0.8f);
        lastProgress = null;
        if (pendingRite) {
            pendingRite = false;
            force(DeaconAction.RESEED_RITE);
        }
    }

    // ================================================================== action slot
    private void runAction(ServerLevel sl, @Nullable Crypt crypt, Phase ph, Crypt.Commune commune, List<ServerPlayer> ps, long now) {
        DeaconAction a = action;
        switch (stage) {
            case IDLE -> {
                if (staggerTicks > 0 || ph.combatIndex() < 1) return;
                // P4: the rite comes first whenever it is off cooldown and there is work left
                if (ph == Phase.P4_RESEED && crypt != null && crypt.riteHasWork() && now >= riteRecastAt && now >= plinthLockUntil) {
                    force(DeaconAction.RESEED_RITE);
                    return;
                }
                if (--gap > 0) return;
                DeaconAction pick = choose(sl, crypt, ph, commune, ps, now);
                if (pick != null) begin(sl, crypt, ph, ps, pick);
                else gap = 10;
            }
            case TELEGRAPH -> {
                telegraph(sl, crypt, ph, a, stageTicks, ps);
                if (++stageTicks >= a.telegraph) {
                    setStage(Stage.ACTIVE);
                    beginActive(sl, a);
                }
            }
            case ACTIVE -> {
                activeTick(sl, crypt, ph, a, stageTicks, ps);
                if (action != a) return;   // cancelled inside the tick
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
        DeaconAction a = action;
        if (a != null) readyAt.put(a, now + Math.round(a.cooldown / DeaconConfig.ATTACK_SPEED.get()));
        action = null;
        entityData.set(ACTION, -1);
        entityData.set(TETHER, -1);
        vulnerable = false;
        setStage(Stage.IDLE);
        int base = switch (ph) {
            case P1_COMMUNION -> 30;
            case P2_PATCHWORK -> 20;
            case P3_VIGIL -> 15;
            default -> 25;
        };
        gap = (int) Math.round(base / DeaconConfig.ATTACK_SPEED.get());
        if (comboLash && level() instanceof ServerLevel sl) {
            // plinth_pull follow-up: a lash with a shortened (10 tick) telegraph
            comboLash = false;
            ServerPlayer t = nearest(targets(players(sl)));
            if (t != null && t.distanceTo(this) < 5) {
                begin(sl, crypt(), ph, players(sl), DeaconAction.LATTICE_LASH);
                stageTicks = DeaconAction.LATTICE_LASH.telegraph - 10;
            }
        }
    }

    /** Cancel whatever is running (interrupts, commands, phase scripts). */
    public void cancel(String why) {
        DeaconAction a = action;
        if (a == null) return;
        if (level() instanceof ServerLevel sl) endActive(sl, a);
        action = null;
        entityData.set(ACTION, -1);
        entityData.set(TETHER, -1);
        vulnerable = false;
        comboLash = false;
        setStage(Stage.IDLE);
        gap = 20;
        readyAt.put(a, level().getGameTime() + a.cooldown / 2);
    }

    /** Designer command / crypt script: start {@code a} now (skips weights and cooldowns, keeps the telegraph). */
    public boolean force(DeaconAction a) {
        if (!(level() instanceof ServerLevel sl)) return false;
        cancel("forced");
        begin(sl, crypt(), phase(), players(sl), a);
        return true;
    }

    /** Scripted beats (vesting chime in P0, reseed rite at P4 entry). */
    public void script(DeaconAction a) {
        force(a);
    }

    @Nullable
    private DeaconAction choose(ServerLevel sl, @Nullable Crypt crypt, Phase ph, Crypt.Commune commune, List<ServerPlayer> ps, long now) {
        List<ServerPlayer> ts = targets(ps);
        if (ts.isEmpty()) return null;
        ServerPlayer near = nearest(ts);
        double nearH = near == null ? 999 : Math.sqrt(near.position().subtract(position()).horizontalDistanceSqr());
        // stranded and on its way back to communion (a live flagstone, or the altar in P3/P4): walking comes first,
        // it only lashes at whoever stands in its way
        boolean returning = crypt != null && now >= plinthLockUntil && switch (ph) {
            case P1_COMMUNION, P2_PATCHWORK -> commune == Crypt.Commune.NONE && crypt.liveNave() > 0;
            case P3_VIGIL, P4_RESEED -> commune != Crypt.Commune.PLINTH;
            default -> false;
        };
        List<DeaconAction> bag = new ArrayList<>();
        for (DeaconAction a : DeaconAction.values()) {
            int w = a.weight(ph);
            if (w <= 0 || readyAt.getOrDefault(a, 0L) > now) continue;
            if (returning && a != DeaconAction.LATTICE_LASH) continue;
            switch (a) {
                case COMMUNION_PULSE -> { if (crypt == null || crypt.liveNave() == 0 || commune == Crypt.Commune.NONE) continue; }
                case LATTICE_LASH -> {
                    if (nearH > 4.5 || Math.abs(near.getY() - getY()) > 3) continue;
                    w *= 2;   // in reach, it would rather hit you
                }
                case NAVE_SHATTER -> { if (nearH > 11) continue; }
                case PLINTH_PULL -> {
                    if (commune != Crypt.Commune.PLINTH || pullTarget(sl, ts) == null) continue;
                    if (nearH > 4.5) w *= 2;
                }
                case LITANY_BEAM -> { if (ts.stream().noneMatch(this::onFloor)) continue; }
                default -> {}
            }
            for (int i = 0; i < w; i++) bag.add(a);
        }
        return bag.isEmpty() ? null : bag.get(random.nextInt(bag.size()));
    }

    private boolean onFloor(Player p) {
        BlockPos o = originOrHome();
        return Math.abs(p.getX() - o.getX() - 0.5) <= CryptLayout.HALF_X + 0.5 && Math.abs(p.getZ() - o.getZ() - 0.5) <= CryptLayout.HALF_Z + 0.5;
    }

    @Nullable
    private ServerPlayer pullTarget(ServerLevel sl, List<ServerPlayer> ts) {
        Vec3 from = loc("censer");
        return ts.stream().filter(p -> {
            double h = Math.sqrt(p.position().subtract(position()).horizontalDistanceSqr());
            return h > 3.5 && h < 16 && clear(sl, from, p.getEyePosition());
        }).min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
    }

    @Nullable
    private ServerPlayer pickTarget(ServerLevel sl, List<ServerPlayer> ps, DeaconAction a) {
        List<ServerPlayer> ts = targets(ps);
        if (ts.isEmpty()) return null;
        Comparator<ServerPlayer> byThreat = Comparator.comparingDouble(p -> -threat.getOrDefault(p.getUUID(), 0f));
        return switch (a) {
            case LATTICE_LASH, NAVE_SHATTER -> nearest(ts);
            case PLINTH_PULL -> pullTarget(sl, ts);
            case HOMING_SHARD -> ts.stream().min(byThreat).orElse(null);
            case LITANY_BEAM -> {
                List<ServerPlayer> pool = ts.stream().filter(this::onFloor).toList();
                yield pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
            }
            case STATIC_BOLT -> {
                List<ServerPlayer> los = ts.stream().filter(p -> clear(sl, loc("hand_l"), p.getEyePosition())).toList();
                List<ServerPlayer> pool = los.isEmpty() ? ts : los;
                yield random.nextFloat() < 0.3f ? pool.get(random.nextInt(pool.size())) : pool.stream().min(byThreat).orElse(null);
            }
            default -> ts.stream().min(byThreat).orElse(null);
        };
    }

    private void begin(ServerLevel sl, @Nullable Crypt crypt, Phase ph, List<ServerPlayer> ps, DeaconAction a) {
        if (a == DeaconAction.RESEED_RITE && crypt != null && crypt.commune(this) != Crypt.Commune.PLINTH) {
            // the rite is said from the altar: get there first (static step), then begin
            pendingRite = true;
            beginStep(CryptLayout.plinthStand(cryptOrigin));
            return;
        }
        action = a;
        entityData.set(ACTION, a.ordinal());
        setStage(Stage.TELEGRAPH);
        fired = 0;
        hitThisCast.clear();
        getNavigation().stop();
        ServerPlayer t = pickTarget(sl, ps, a);
        target = t == null ? null : t.getUUID();
        switch (a) {
            case VESTING_CHIME -> triggerAnim("main", "chime");
            case COMMUNION_PULSE -> {
                triggerAnim("main", "pulse_windup");
                warnAll(ps, "static_deacon.warn.pulse", ChatFormatting.LIGHT_PURPLE);
            }
            case LATTICE_LASH -> {
                triggerAnim("main", "lash_windup");
                Vec3 tp = t == null ? position().add(getLookAngle()) : t.position();
                lockedYaw = (float) (Mth.atan2(tp.z - getZ(), tp.x - getX()) * Mth.RAD_TO_DEG) - 90f;
            }
            case STATIC_BOLT -> triggerAnim("main", "bolt_cast");
            case NAVE_SHATTER -> {
                triggerAnim("main", "shatter_windup");
                warnAll(ps, "static_deacon.warn.shatter", ChatFormatting.GOLD);
            }
            case HOMING_SHARD -> triggerAnim("main", "shard_cast");
            case PLINTH_PULL -> {
                triggerAnim("main", "pull_windup");
                if (t != null) {
                    entityData.set(TETHER, t.getId());
                    notify(t, "static_deacon.pull.windup", 1.1f);
                    t.displayClientMessage(Component.translatable("static_deacon.warn.pull").withStyle(ChatFormatting.RED), true);
                }
            }
            case LITANY_BEAM -> {
                triggerAnim("main", "beam_windup");
                BlockPos o = originOrHome();
                Vec3 tp = t == null ? position() : t.position();
                int[] s = CryptLayout.slotAt(o, BlockPos.containing(tp).getX(), BlockPos.containing(tp).getZ());
                laneCx = s == null ? 0 : Mth.clamp(s[0], -CryptLayout.COLS, CryptLayout.COLS);
                laneCz = s == null ? 0 : Mth.clamp(s[1], -CryptLayout.ROWS, CryptLayout.ROWS);
                cross = ph == Phase.P3_VIGIL;
                warnAll(ps, cross ? "static_deacon.warn.litany_cross" : "static_deacon.warn.litany", ChatFormatting.GOLD);
            }
            case RESEED_RITE -> {
                triggerAnim("main", "reseed_begin");
                warnAll(ps, "static_deacon.warn.reseed", ChatFormatting.YELLOW);
            }
        }
    }

    private void telegraph(ServerLevel sl, @Nullable Crypt crypt, Phase ph, DeaconAction a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case VESTING_CHIME -> {
                if (t % 5 == 0) Telegraph.ring(sl, SBParticles.get("communion_mote"), groundUnder(position()), 2 + t * 0.25, 24);
                if (t % 10 == 0 && crypt != null) {
                    // mark the flagstone under every player: "you are standing on communion"
                    for (ServerPlayer p : ps) {
                        Flagstone f = crypt.flagstoneAt(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ()));
                        if (f != null && f.live()) outline(sl, f, SBParticles.get("litany_light"));
                    }
                }
            }
            case COMMUNION_PULSE -> {
                if (t % 8 != 0 || crypt == null) return;
                for (Flagstone f : crypt.liveFlagstones()) {
                    Vec3 c = Vec3.atBottomCenterOf(f.center).add(0, 1.05, 0);
                    for (int k = 0; k < 4; k++) {
                        double ang = Math.PI / 2 * k + t * 0.1;
                        Telegraph.point(sl, SBParticles.get("communion_mote"), c.add(Math.cos(ang) * 1.1, 0, Math.sin(ang) * 1.1), 1, 0);
                    }
                }
            }
            case LATTICE_LASH -> {
                if (t % 4 != 0) return;
                sector(sl, SBParticles.TARGET_MARK.get(), 4.5, 55);
            }
            case STATIC_BOLT -> {
                if (t % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("beryl_glint"), loc("hand_l"), 4, 0.2, 0.05);
            }
            case NAVE_SHATTER -> {
                if (t % 5 != 0) return;
                Vec3 g = groundUnder(position());
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), g, 12, 60);
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), g, 6, 30);
            }
            case HOMING_SHARD -> {
                if (t % 5 == 0) AnimFx.serverBurst(sl, SBParticles.get("beryl_glint"), loc("halo"), 10, 0.6, 0.05);
            }
            case PLINTH_PULL -> {
                if (t % 2 == 0 && tgt != null) Telegraph.line(sl, SBParticles.get("litany_light"), loc("censer"), tgt.getBoundingBox().getCenter(), 0.8);
            }
            case LITANY_BEAM -> {
                if (t % 4 == 0) lanes(sl, SBParticles.TARGET_MARK.get(), 0.1, 1.0);
            }
            case RESEED_RITE -> {
                if (t % 5 == 0) Telegraph.ring(sl, SBParticles.get("communion_mote"), groundUnder(position()), 3.5, 28);
            }
        }
    }

    private void beginActive(ServerLevel sl, DeaconAction a) {
        switch (a) {
            case LATTICE_LASH -> triggerAnim("main", "lash");
            case NAVE_SHATTER -> {
                triggerAnim("main", "shatter");
                for (ServerPlayer p : players(sl)) if (p.distanceTo(this) < 20) net.teamaof.skylorebosses.core.net.SBNetwork.sendScreenFx(p, net.teamaof.skylorebosses.core.net.SBNetwork.FX_SHAKE, 12);
            }
            case PLINTH_PULL -> triggerAnim("main", "pull");
            case COMMUNION_PULSE -> triggerAnim("main", "pulse");
            default -> {}
        }
    }

    private void activeTick(ServerLevel sl, @Nullable Crypt crypt, Phase ph, DeaconAction a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case VESTING_CHIME -> {
                if (t != 0) return;
                Vec3 c = loc("core");
                AnimFx.serverBurst(sl, SBParticles.get("communion_mote"), c, 80, 3, 0.4);
                AnimFx.play(sl, c, "static_deacon.chime", 10f, 1f);
                for (ServerPlayer p : ps) {
                    Vec3 d = p.position().subtract(c);
                    if (new Vec3(d.x, 0, d.z).length() > 12 || !clear(sl, c, p.getEyePosition())) continue;
                    push(p, c, 1.0, 0.4);
                    if (crypt != null) {
                        Flagstone f = crypt.flagstoneAt(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ()));
                        if (f != null && f.live()) p.displayClientMessage(Component.translatable("static_deacon.warn.on_communion").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    }
                }
            }
            case COMMUNION_PULSE -> {
                if (t != 0 || crypt == null) return;
                AnimFx.play(sl, position(), "static_deacon.pulse.release", 8f, 1f);
                for (Flagstone f : crypt.liveFlagstones())
                    AnimFx.serverBurst(sl, SBParticles.get("communion_mote"), Vec3.atBottomCenterOf(f.center).add(0, 1.1, 0), 6, 0.9, 0.08);
                int hits = 0;
                for (ServerPlayer p : ps) {
                    if (!canTarget(p)) continue;
                    BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
                    Flagstone f = crypt.flagstoneAt(under);
                    boolean on = (f != null && f.nave() && f.state != Flagstone.State.DESECRATED) || CryptLayout.onPlinth(cryptOrigin, p.position());
                    if (!on || p.getY() - (under.getY() + 1) > 0.6) continue;
                    p.hurt(damageSources().indirectMagic(this, this), DeaconConfig.PULSE_DAMAGE.get().floatValue());
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    hits++;
                }
                if (hits > 0) heal((float) (getMaxHealth() * DeaconConfig.PULSE_HEAL.get() * hits));
            }
            case LATTICE_LASH -> {
                boolean second = ph == Phase.P3_VIGIL && t == 11;
                if (t != 1 && !second) return;
                if (second) {
                    triggerAnim("main", "lash");
                    hitThisCast.clear();
                }
                AnimFx.play(sl, position(), "static_deacon.lash.swing", 3f, second ? 1.15f : 1f);
                sector(sl, SBParticles.get("beryl_glint"), 4.5, 55);
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || hitThisCast.contains(p.getUUID()) || !inSector(p, 4.5, 55) || Math.abs(p.getY() - getY()) > 3) continue;
                    hitThisCast.add(p.getUUID());
                    p.hurt(damageSources().mobAttack(this), DeaconConfig.LASH_DAMAGE.get().floatValue());
                    push(p, position(), 1.0, 0.35);
                }
            }
            case STATIC_BOLT -> {
                int[] sched = DeaconAction.boltSchedule(ph);
                double lead = switch (ph) {
                    case P1_COMMUNION -> 0;
                    case P2_PATCHWORK -> 0.5;
                    default -> 0.75;
                };
                for (int s : sched) {
                    if (t != s || tgt == null || !tgt.isAlive()) continue;
                    Vec3 from = loc("hand_l");
                    Vec3 aim = tgt.getBoundingBox().getCenter();
                    double flight = from.distanceTo(aim) / DeaconProjectile.BOLT_SPEED;
                    Vec3 v = tgt.getDeltaMovement();
                    aim = aim.add(new Vec3(v.x, 0, v.z).scale(flight * lead));
                    sl.addFreshEntity(DeaconProjectile.bolt(sl, this, from, aim.subtract(from).normalize().scale(DeaconProjectile.BOLT_SPEED)));
                    AnimFx.play(sl, from, "static_deacon.bolt.cast", 3f, 0.9f + random.nextFloat() * 0.2f);
                }
            }
            case NAVE_SHATTER -> {
                double r = 1 + t * 0.55;
                Vec3 c = groundUnder(position());
                Telegraph.ring(sl, SBParticles.get("static_dust"), c, r, Math.max(12, (int) (r * 6)));
                if (t % 4 == 0) AnimFx.play(sl, c, "static_deacon.shatter.crack", 4f, 0.8f + t * 0.02f);
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || hitThisCast.contains(p.getUUID())) continue;
                    double h = Math.sqrt(p.position().subtract(c).horizontalDistanceSqr());
                    if (h < r - 0.9 || h > r + 0.3 || !p.onGround() || Math.abs(p.getY() - c.y) > 1.6) continue;
                    hitThisCast.add(p.getUUID());
                    p.hurt(damageSources().mobAttack(this), DeaconConfig.SHATTER_DAMAGE.get().floatValue());
                    push(p, c, 0.8, 0.6);
                }
                breakCoverInBand(sl, c, r);
            }
            case HOMING_SHARD -> {
                int n = DeaconAction.shards(ph, getHealth() < getMaxHealth() * 0.5f);
                if (t % 6 != 0 || fired >= n || tgt == null) return;
                double ang = (fired - (n - 1) / 2.0) * 0.8 + Math.toRadians(getYRot() + 90);
                Vec3 from = loc("halo").add(Math.cos(ang) * 0.8, 0.2, Math.sin(ang) * 0.8);
                Vec3 vel = new Vec3(Math.cos(ang) * 0.25, 0.25, Math.sin(ang) * 0.25);
                sl.addFreshEntity(DeaconProjectile.shard(sl, this, from, vel, tgt, ph == Phase.P3_VIGIL ? 4f : 3f));
                AnimFx.play(sl, from, "static_deacon.shard.cast", 3f, 0.9f + fired * 0.08f);
                fired++;
            }
            case PLINTH_PULL -> {
                if (t != 0) return;
                entityData.set(TETHER, -1);
                if (!(tgt instanceof ServerPlayer p) || !p.isAlive() || cryptOrigin == null) return;
                if (!clear(sl, loc("censer"), p.getEyePosition())) {
                    AnimFx.play(sl, p.position(), "static_deacon.pull.snap", 3f, 1f);
                    p.displayClientMessage(Component.translatable("static_deacon.warn.pull_snapped").withStyle(ChatFormatting.GRAY), true);
                    return;
                }
                Vec3 c = Vec3.atBottomCenterOf(cryptOrigin);
                Vec3 dir = p.position().subtract(c).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : dir.normalize();
                Vec3 dest = new Vec3(c.x + dir.x * 2.4, floorY(), c.z + dir.z * 2.4);
                // players lose most of an impulse to drag within a few ticks, so the yank over-drives toward the dais
                Vec3 v = dest.subtract(p.position()).scale(0.32).add(0, 0.45, 0);
                if (v.length() > 2.6) v = v.normalize().scale(2.6);
                p.setDeltaMovement(v);
                p.hurtMarked = true;
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                Telegraph.line(sl, SBParticles.get("litany_light"), loc("censer"), p.getBoundingBox().getCenter(), 0.4);
                AnimFx.play(sl, p.position(), "static_deacon.pull.yank", 4f, 1f);
                comboLash = true;
            }
            case LITANY_BEAM -> {
                if (t % 2 == 0) lanes(sl, SBParticles.get("litany_light"), 0.4, 1.0);
                if (t % 2 == 1) lanes(sl, SBParticles.get("litany_light"), 1.6, 1.4);
                if (t % 10 == 0) AnimFx.play(sl, position(), "static_deacon.beam.loop", 5f, 1f);
                if (t % 5 != 0) return;
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || !inLane(p)) continue;
                    p.hurt(damageSources().indirectMagic(this, this), DeaconConfig.BEAM_DAMAGE.get().floatValue());
                    staticDebuff(p);
                }
            }
            case RESEED_RITE -> {
                if (crypt == null || !crypt.riteStep(sl)) {
                    stageTicks = a.activeTicks(ph);   // nothing left to lay: end the channel
                    return;
                }
                if (t % 3 == 0) AnimFx.serverBurst(sl, SBParticles.get("communion_mote"), loc("core"), 4, 1.2, 0.04);
                if (t % 20 == 0) AnimFx.play(sl, position(), "static_deacon.reseed.chant", 6f, 1f);
            }
        }
    }

    private void endActive(ServerLevel sl, DeaconAction a) {
        entityData.set(TETHER, -1);
    }

    // ================================================================== attack geometry
    private void sector(ServerLevel sl, net.minecraft.core.particles.ParticleOptions type, double r, double halfDeg) {
        Vec3 c = groundUnder(position());
        for (double rr : new double[]{r * 0.5, r}) {
            for (double d = -halfDeg; d <= halfDeg; d += 11) {
                double ang = Math.toRadians(lockedYaw + 90 + d);
                Telegraph.point(sl, type, c.add(Math.cos(ang) * rr, 0.1, Math.sin(ang) * rr), 1, 0);
            }
        }
    }

    private boolean inSector(Entity e, double r, double halfDeg) {
        Vec3 d = e.position().subtract(position());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        if (h > r + e.getBbWidth() * 0.5) return false;
        if (h < 1.2) return true;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        return Math.abs(Mth.wrapDegrees(yaw - lockedYaw)) <= halfDeg;
    }

    /** Lane outlines for litany_beam: the target's slot column along Z, plus its row along X in P3. */
    private void lanes(ServerLevel sl, net.minecraft.core.particles.ParticleOptions type, double y, double step) {
        BlockPos o = originOrHome();
        double fy = floorY() + y;
        double x = CryptLayout.laneX(o, laneCx), z0 = o.getZ() - CryptLayout.HALF_Z, z1 = o.getZ() + CryptLayout.HALF_Z + 1;
        for (double dx : new double[]{-1.5, 1.5}) Telegraph.line(sl, type, new Vec3(x + dx, fy, z0), new Vec3(x + dx, fy, z1), step);
        if (cross) {
            double z = CryptLayout.rowZ(o, laneCz), x0 = o.getX() - CryptLayout.HALF_X, x1 = o.getX() + CryptLayout.HALF_X + 1;
            for (double dz : new double[]{-1.5, 1.5}) Telegraph.line(sl, type, new Vec3(x0, fy, z + dz), new Vec3(x1, fy, z + dz), step);
        }
    }

    private boolean inLane(Entity e) {
        BlockPos o = originOrHome();
        double dy = e.getY() - floorY();
        if (dy < -0.8 || dy > 2.8 || !(e instanceof Player p && onFloor(p))) return false;
        if (Math.abs(e.getX() - CryptLayout.laneX(o, laneCx)) <= 1.5 + e.getBbWidth() * 0.5) return true;
        return cross && Math.abs(e.getZ() - CryptLayout.rowZ(o, laneCz)) <= 1.5 + e.getBbWidth() * 0.5;
    }

    private void breakCoverInBand(ServerLevel sl, Vec3 c, double r) {
        BlockPos o = originOrHome();
        List<BlockPos> pews = cryptOrigin != null ? CryptLayout.pewBlocks(o) : List.of();
        for (BlockPos p : pews) {
            double h = Math.sqrt(Vec3.atCenterOf(p).subtract(c).horizontalDistanceSqr());
            if (h < r - 0.9 || h > r + 0.3) continue;
            BlockState s = sl.getBlockState(p);
            if (!s.is(Crypt.BREAKABLE_COVER)) continue;
            sl.levelEvent(2001, p, Block.getId(s));
            sl.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void push(Entity p, Vec3 from, double h, double up) {
        Vec3 d = p.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : d.normalize();
        p.push(d.x * h, up, d.z * h);
        p.hurtMarked = true;
    }

    public void staticDebuff(Player p) {
        int ticks = DeaconConfig.STATIC_TICKS.get();
        if (ticks > 0) p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 0));
    }

    private void outline(ServerLevel sl, Flagstone f, net.minecraft.core.particles.ParticleOptions type) {
        Vec3 c = Vec3.atBottomCenterOf(f.center).add(0, 1.05, 0);
        Vec3 a = c.add(-1.5, 0, -1.5), b = c.add(1.5, 0, -1.5), d = c.add(1.5, 0, 1.5), e = c.add(-1.5, 0, 1.5);
        Telegraph.line(sl, type, a, b, 0.5);
        Telegraph.line(sl, type, b, d, 0.5);
        Telegraph.line(sl, type, d, e, 0.5);
        Telegraph.line(sl, type, e, a, 0.5);
    }

    private boolean clear(ServerLevel sl, Vec3 from, Vec3 to) {
        return sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private Vec3 groundUnder(Vec3 p) {
        return new Vec3(p.x, Math.floor(p.y) + 0.05, p.z);
    }

    // ================================================================== crypt callbacks
    /** A flagstone died. If it was the one underfoot: stumble, drop interruptible telegraphs, find new ground. */
    public void onFloorLost(Flagstone f) {
        Crypt c = crypt();
        if (c == null) return;
        Flagstone under = c.flagstoneAt(BlockPos.containing(getX(), getY() - 0.2, getZ()));
        if (under != f) return;
        DeaconAction a = action;
        if (a != null && a != DeaconAction.RESEED_RITE && a.interruptible(stage == Stage.TELEGRAPH)) cancel("stranded");
        if (action == null) {
            staggerTicks = Math.max(staggerTicks, DeaconConfig.STUMBLE_TICKS.get());
            triggerAnim("main", "hurt");
        }
        stance = null;
    }

    /** Knocked off the altar: the vigil (or the rite) is broken (DESIGN.md §7). */
    public void breakVigil(@Nullable Player breaker, boolean rite) {
        if (!(level() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        cancel(rite ? "rite broken" : "vigil broken");
        pendingRite = false;
        Vec3 c = cryptOrigin != null ? CryptLayout.plinthStand(cryptOrigin) : position();
        Vec3 dir = breaker != null ? breaker.position().subtract(c).multiply(1, 0, 1) : new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        dir = dir.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : dir.normalize();
        setDeltaMovement(dir.x * 1.1, 0.5, dir.z * 1.1);
        hurtMarked = true;
        hasImpulse = true;
        staggerTicks = DeaconConfig.KNOCKOFF_STAGGER_TICKS.get();
        vulnTicks = DeaconConfig.DESECRATED_TICKS.get();
        plinthLockUntil = now + DeaconConfig.PLINTH_LOCK_TICKS.get();
        if (rite) riteRecastAt = now + DeaconConfig.RITE_RECAST_TICKS.get();
        vigilDmg = 0;
        triggerAnim("main", "knockoff");
        AnimFx.play(sl, position(), "static_deacon.vigil.break", 8f, 1f);
        AnimFx.serverBurst(sl, SBParticles.get("static_dust"), position().add(0, 1.6, 0), 60, 1.0, 0.2);
        Crypt cr = crypt();
        if (cr != null) cr.onVigilBroken(sl, breaker instanceof ServerPlayer sp ? sp : null, rite);
    }

    public void resetForPhase() {
        cancel("phase");
        stance = null;
        pendingRite = false;
        plinthWait = 0;
        entityData.set(TETHER, -1);
        getNavigation().stop();
    }

    // ================================================================== damage
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (source.getEntity() == this || source.getDirectEntity() instanceof DeaconProjectile) return false;
        ServerLevel sl = (ServerLevel) level();
        Crypt crypt = crypt();
        float dr = crypt == null ? 0f : crypt.damageReduction(this);
        float mult = 1f - dr;
        boolean vuln = vulnTicks > 0 || vulnerable;
        if (vuln) mult *= DeaconConfig.VULNERABLE_MULT.get().floatValue();
        Entity attacker = source.getEntity();
        long now = sl.getGameTime();
        if (attacker instanceof ServerPlayer p && crypt != null && gateMsgAt.getOrDefault(p.getUUID(), 0L) < now) {
            Crypt.Commune c = crypt.commune(this);
            String key = null;
            Object[] args = {};
            if (mult <= 0) key = "static_deacon.gate.immune";
            else if (channeling()) { key = "static_deacon.gate.rite"; args = new Object[]{Math.round(dr * 100)}; }
            else if (c == Crypt.Commune.NAVE && dr >= 0.1f) { key = "static_deacon.gate.communion"; args = new Object[]{Math.round(dr * 100)}; }
            else if (c == Crypt.Commune.PLINTH) key = "static_deacon.gate.vigil";
            else if (c == Crypt.Commune.NONE && crypt.phase().fighting()) key = vuln ? "static_deacon.gate.desecrated" : "static_deacon.gate.stranded";
            if (key != null) {
                gateMsgAt.put(p.getUUID(), now + 100);
                p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        if (mult <= 0) {
            AnimFx.play(sl, position(), "static_deacon.absorb", 2f, 1.2f);
            return false;
        }
        if (dr >= 0.2f) AnimFx.serverBurst(sl, SBParticles.get("communion_mote"), getBoundingBox().getCenter(), 6, 0.5, 0.1);
        boolean r = super.hurt(source, amount * mult);
        if (r) {
            float dealt = amount * mult;
            if (attacker instanceof Player p) addThreat(p.getUUID(), dealt);
            if (now - hurtAnimAt > 10) { hurtAnimAt = now; triggerAnim("main", "hurt"); }
            // burst stagger: 10% of max HP inside 40 ticks cancels an interruptible telegraph
            if (now - burstStart > 40) { burstStart = now; burst = 0; }
            burst += dealt;
            if (burst >= getMaxHealth() * 0.10f && now > staggerLockUntil && action != null && action != DeaconAction.RESEED_RITE
                    && stage == Stage.TELEGRAPH && action.interruptible(true)) {
                cancel("damage stagger");
                staggerTicks = 30;
                staggerLockUntil = now + 200;
            }
            // vigil / rite break: damage while on the altar knocks it off
            if (crypt != null && (crypt.commune(this) == Crypt.Commune.PLINTH || channeling()) && crypt.phase().fighting()
                    && crypt.phase() != Phase.P0_VESTING) {
                if (now - vigilStart > DeaconConfig.VIGIL_BREAK_WINDOW.get()) { vigilStart = now; vigilDmg = 0; }
                vigilDmg += dealt;
                boolean rite = channeling();
                double need = getMaxHealth() * (rite ? DeaconConfig.RITE_BREAK_FRACTION.get() : DeaconConfig.VIGIL_BREAK_FRACTION.get());
                if (vigilDmg >= need && isAlive()) breakVigil(attacker instanceof Player p ? p : null, rite);
            }
            if (crypt != null) crypt.onDeaconDamaged(this);
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            entityData.set(TETHER, -1);
            getNavigation().stop();
            triggerAnim("main", "death");
            AnimFx.play(sl, position(), "static_deacon.death", 10f, 1f);
            Crypt c = crypt();
            if (c != null) c.onDeaconDied(sl, this, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl) {
            if (deathTime % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("static_dust"), position().add(0, 1.6 - deathTime * 0.01, 0), 14, 0.7, 0.06);
            if (deathTime % 20 == 0) AnimFx.play(sl, position(), "static_deacon.shard.break", 3f, 0.6f + deathTime * 0.004f);
            if (deathTime >= 140) {
                AnimFx.serverBurst(sl, SBParticles.get("static_dust"), position().add(0, 1, 0), 120, 1.4, 0.15);
                AnimFx.serverBurst(sl, SBParticles.get("beryl_glint"), position().add(0, 1, 0), 60, 1.0, 0.3);
                remove(RemovalReason.KILLED);
            }
        }
    }

    private void notify(ServerPlayer p, String sound, float pitch) {
        SoundEvent s = SBSounds.get(sound);
        if (s != null) p.playNotifySound(s, SoundSource.HOSTILE, 1f, pitch);
    }

    private void warnAll(List<ServerPlayer> ps, String key, ChatFormatting color) {
        Component c = Component.translatable(key).withStyle(color);
        for (ServerPlayer p : ps) p.displayClientMessage(c, true);
    }

    // ================================================================== client
    private void clientFx() {
        if (isDeadOrDying()) return;
        if (tickCount % 5 == 0) {
            Vec3 h = loc("halo");
            level().addParticle(SBParticles.get("beryl_glint"), h.x + (random.nextDouble() - 0.5), h.y + random.nextDouble() * 0.3, h.z + (random.nextDouble() - 0.5), 0, 0.01, 0);
        }
        if (tickCount % 3 == 0) {
            Vec3 f = position().add((random.nextDouble() - 0.5) * 0.9, 0.3 + random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.9);
            level().addParticle(SBParticles.get("static_dust"), f.x, f.y, f.z, 0, -0.01, 0);
        }
        if (communeSynced() != Crypt.Commune.NONE && tickCount % 4 == 0) {
            Vec3 f = position().add((random.nextDouble() - 0.5) * 1.6, 0.05, (random.nextDouble() - 0.5) * 1.6);
            level().addParticle(SBParticles.get("communion_mote"), f.x, f.y, f.z, 0, 0.06, 0);
        }
        int tid = tether();
        if (tid >= 0 && level().getEntity(tid) instanceof Entity t) {
            Vec3 a = loc("censer"), b = t.getBoundingBox().getCenter();
            double len = a.distanceTo(b);
            for (double d = 0; d < len; d += 0.6) {
                Vec3 p = a.lerp(b, d / len);
                level().addParticle(SBParticles.get("litany_light"), p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }

    // ================================================================== misc
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity e) {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128 * 128; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(2, 1, 2); }

    public String debugState() {
        DeaconAction a = action;
        var path = getNavigation().getPath();
        return String.format(java.util.Locale.ROOT, "deacon hp=%.0f/%.0f action=%s stage=%s t=%d gap=%d stagger=%d vuln=%d plinthLock=%d step=%s pos=%.1f,%.1f,%.1f stance=%s path=%s",
                getHealth(), getMaxHealth(), a == null ? "-" : a.id(), stage, stageTicks, gap, staggerTicks, vulnTicks,
                Math.max(0, plinthLockUntil - level().getGameTime()), stepTo != null, getX(), getY(), getZ(),
                stance == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", stance.x, stance.y, stance.z),
                path == null ? "none" : (path.isDone() ? "done" : path.getNextNodeIndex() + "/" + path.getNodeCount()) + (path.canReach() ? "" : "!unreachable"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (cryptOrigin != null) tag.put("Crypt", NbtUtils.writeBlockPos(cryptOrigin));
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        cryptOrigin = NbtUtils.readBlockPos(tag, "Crypt").orElse(null);
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        // a reloaded Deacon resumes idle; whatever it was doing is dropped (the crypt re-scripts the rite if needed)
        gap = 40;
    }

    private static String anim(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<StaticDeaconEntity> c = new AnimationController<>(this, "main", 4, s -> {
            DeaconAction a = action();
            Stage st = stage();
            if (isDeadOrDying()) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(anim("death")));
            if (stepping()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("static_step")));
            if (a == DeaconAction.LITANY_BEAM && st == Stage.ACTIVE) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("beam")));
            if (a == DeaconAction.RESEED_RITE && st == Stage.ACTIVE) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("reseed")));
            if (staggered()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("stagger")));
            if (s.isMoving()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("walk")));
            return switch (communeSynced()) {
                case PLINTH -> s.setAndContinue(RawAnimation.begin().thenLoop(anim("vigil")));
                case NAVE -> s.setAndContinue(RawAnimation.begin().thenLoop(anim("commune")));
                default -> s.setAndContinue(RawAnimation.begin().thenLoop(anim("idle")));
            };
        });
        for (String n : new String[]{"vesting", "chime", "pulse_windup", "pulse", "lash_windup", "lash", "bolt_cast", "shatter_windup",
                "shatter", "shard_cast", "pull_windup", "pull", "beam_windup", "reseed_begin", "hurt", "knockoff", "step_out", "step_in"}) {
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
