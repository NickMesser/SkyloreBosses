package net.teamaof.skylorebosses.bosses.amanita.entity;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaConfig;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollow;
import net.teamaof.skylorebosses.bosses.amanita.encounter.HollowLayout;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollows;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Lights;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Phase;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaBlocks;
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
 * Amanita, the Hollow Bloom (DESIGN.md §5, §9). A walking fungal warlord with one action slot:
 * IDLE -> TELEGRAPH -> ACTIVE -> RECOVERY -> IDLE. The hollow owns phase, the light gate and the snuff; this entity
 * owns movement (she keeps to dark tiles near her target and leaves light when she can), targeting and the attacks.
 * Spawned outside a hollow she runs "bench test" mode: the light gate applies, P1 attack table, no block snuffing.
 */
public class AmanitaEntity extends Monster implements GeoEntity {
    public static final String MODEL = "amanita";
    public static final float SCALE = 1.0f;
    public enum Stage { IDLE, TELEGRAPH, ACTIVE, RECOVERY }

    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(AmanitaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(AmanitaEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EXPOSED = SynchedEntityData.defineId(AmanitaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STAGGERED = SynchedEntityData.defineId(AmanitaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEEP = SynchedEntityData.defineId(AmanitaEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos hollowOrigin;
    @Nullable private Vec3 home;
    // action slot
    @Nullable private AmanitaAction action;
    private Stage stage = Stage.IDLE;
    private int stageTicks, gap = 40, staggerTicks;
    private final Map<AmanitaAction, Long> readyAt = new EnumMap<>(AmanitaAction.class);
    // per-action scratch
    @Nullable private UUID target;
    @Nullable private Vec3 aim;
    @Nullable private BlockPos dashLight;
    @Nullable private Vec3 dashTo;
    private boolean arrived;
    private int smother;
    private float lockedYaw;
    private final Set<UUID> hitThisCast = new HashSet<>();
    // movement
    @Nullable private Vec3 stance;
    private int stanceIn, stuckTicks;
    @Nullable private Vec3 lastProgress;
    // bookkeeping
    private final Map<UUID, Float> threat = new HashMap<>();
    private final Map<UUID, Long> rootedUntil = new HashMap<>();
    private final Map<UUID, Long> gateMsgAt = new HashMap<>();
    private final List<Veil> veils = new ArrayList<>();
    private float burst;
    private long burstStart, staggerLockUntil, hurtAnimAt;
    private boolean resumed, wasExposedLocal;

    /** A lingering spore veil (spore_veil): Darkness and chip damage inside; in P3/P4 it smothers the lights under it. */
    private static final class Veil {
        final Vec3 c;
        final double r;
        int left;
        final boolean snuffAtEnd;

        Veil(Vec3 c, double r, int left, boolean snuffAtEnd) {
            this.c = c;
            this.r = r;
            this.left = left;
            this.snuffAtEnd = snuffAtEnd;
        }
    }

    public AmanitaEntity(EntityType<? extends AmanitaEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 300;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600)
                .add(Attributes.ARMOR, 4)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.ATTACK_DAMAGE, 7);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ACTION, -1);
        b.define(STAGE, 0);
        b.define(EXPOSED, false);
        b.define(STAGGERED, false);
        b.define(DEEP, false);
    }

    @Override
    protected void registerGoals() {}

    // ================================================================== hollow link
    public void bindHollow(BlockPos origin) {
        hollowOrigin = origin.immutable();
    }

    @Nullable
    public BlockPos hollowOrigin() { return hollowOrigin; }

    @Nullable
    private Hollow hollow() {
        return hollowOrigin == null || !(level() instanceof ServerLevel sl) ? null : Hollows.get(sl).at(hollowOrigin);
    }

    public Phase phase() {
        Hollow h = hollow();
        return h != null ? h.phase() : Phase.P1_DARK_IMMUNITY;
    }

    @Nullable public AmanitaAction action() {
        int i = entityData.get(ACTION);
        return i < 0 ? null : AmanitaAction.values()[i];
    }

    public Stage stage() { return Stage.values()[entityData.get(STAGE)]; }
    public boolean exposedSynced() { return entityData.get(EXPOSED); }
    public boolean staggered() { return entityData.get(STAGGERED); }
    public boolean deepSynced() { return entityData.get(DEEP); }

    public void setExposedSynced(boolean e) { entityData.set(EXPOSED, e); }

    /** Closing the flower: sealed (immune) while a full snuff or deep bloom is being cast. */
    public boolean sealed() {
        return action != null && action.seals() && (stage == Stage.TELEGRAPH || stage == Stage.ACTIVE);
    }

    public Vec3 loc(String name) { return AnimFx.locator(this, MODEL, SCALE, name); }

    private BlockPos originOrHome() {
        return hollowOrigin != null ? hollowOrigin : BlockPos.containing(home == null ? position() : home).below();
    }

    private double floorY() { return originOrHome().getY() + 1; }

    private int threshold() {
        Hollow h = hollow();
        return h != null ? h.threshold() : AmanitaConfig.LIGHT_THRESHOLD.get();
    }

    public int lux() {
        if (!(level() instanceof ServerLevel sl)) return 0;
        Hollow h = hollow();
        return h != null ? h.lux(sl, this) : Lights.sample(sl, this);
    }

    public boolean exposed() {
        if (!(level() instanceof ServerLevel sl)) return exposedSynced();
        Hollow h = hollow();
        return h != null ? h.exposed(sl, this) : Lights.sample(sl, this) >= threshold();
    }

    private List<BlockPos> census(ServerLevel sl) {
        Hollow h = hollow();
        return h != null ? h.census(sl) : List.of();
    }

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
        Hollow hollow = hollow();
        if (hollowOrigin != null && hollow == null) { discard(); return; }
        if (home == null) home = position();
        if (!resumed) {
            resumed = true;
            if (hollow != null) hollow.resumeScripts(this);
        }
        Phase ph = phase();
        long now = sl.getGameTime();
        List<ServerPlayer> ps = players(sl);
        entityData.set(DEEP, ph == Phase.P4_DEEP_BLOOM);
        if (hollow == null) {
            boolean ex = exposed();
            if (ex && !wasExposedLocal) onExposed(true);
            wasExposedLocal = ex;
            setExposedSynced(ex);
        }
        if (now % 20 == 0) threat.replaceAll((k, v) -> v * 0.98f);
        if (staggerTicks > 0) staggerTicks--;
        entityData.set(STAGGERED, staggerTicks > 0);
        tickVeils(sl, hollow, ph);
        if (hollowOrigin != null && (!HollowLayout.leash(hollowOrigin).contains(position()) || getY() < hollowOrigin.getY() - 2)) {
            // anti-escape / anti-void: carried out of the hollow she sinks into the loam and rises on the bloom bed
            Vec3 bed = HollowLayout.bloomBed(hollowOrigin);
            AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), position().add(0, 1.5, 0), 40, 0.8, 0.1);
            teleportTo(bed.x, bed.y, bed.z);
            stance = null;
        }
        boolean hold = hollow != null && hollow.held();
        move(sl, hollow, ph, ps, now);
        face(sl, ps);
        runAction(sl, hollow, ph, ps, now, hold);
    }

    private List<ServerPlayer> players(ServerLevel sl) {
        Hollow h = hollow();
        if (h != null) return h.participants(sl);
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

    /** A Tenebris player the hollow has rooted: her preferred bolt target until {@code until}. */
    public void markRooted(UUID player, long until) {
        rootedUntil.put(player, until);
    }

    @Nullable
    private ServerPlayer nearest(List<ServerPlayer> ts) {
        return ts.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
    }

    @Nullable
    private ServerPlayer topThreat(List<ServerPlayer> ts) {
        return ts.stream().max(Comparator.comparingDouble(p -> threat.getOrDefault(p.getUUID(), 0f) - p.distanceTo(this) * 0.5)).orElse(null);
    }

    // ================================================================== movement (DESIGN.md §9 idle/reposition)
    private void move(ServerLevel sl, @Nullable Hollow hollow, Phase ph, List<ServerPlayer> ps, long now) {
        if (action == AmanitaAction.LIGHT_SEEKER_DASH && stage == Stage.ACTIVE) return;   // the dash moves itself
        if (action != null || staggerTicks > 0 || ph == Phase.P0_BLOOM_OPENS || !ph.fighting()) {
            getNavigation().stop();
            return;
        }
        ServerPlayer tgt = topThreat(targets(ps));
        Vec3 goal;
        if (hollow == null) {
            // bench test: shuffle toward the target but stay near home
            Vec3 t = tgt == null ? home : tgt.position();
            Vec3 d = t.subtract(home);
            goal = d.length() > 8 ? home.add(d.normalize().scale(8)) : t;
        } else {
            // keep to the darkest tile that still lets her fight: re-picked every 30 ticks, and at once when lit
            if (stance == null || --stanceIn <= 0 || (exposed() && stanceLit(sl))) {
                stance = chooseStance(sl, tgt);
                stanceIn = 30;
            }
            if (stance == null) { getNavigation().stop(); return; }
            goal = stance;
        }
        double speed = exposed() ? 0.85 : 1.0;
        if (ph == Phase.P3_LIT_DUEL || ph == Phase.P4_DEEP_BLOOM) speed *= 1.1;
        if (position().distanceTo(goal) < 1.2) {
            getNavigation().stop();
            stuckTicks = 0;
            return;
        }
        if (position().distanceTo(goal) < 3) {
            getNavigation().stop();
            getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
        } else if (getNavigation().isDone() || tickCount % 10 == 0) {
            getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
        }
        // stuck: no progress for 100 ticks while the goal is more than 2 blocks away -> sink into the loam and rise there
        if (lastProgress == null || position().distanceTo(lastProgress) > 0.6) {
            lastProgress = position();
            stuckTicks = 0;
        } else if (position().distanceTo(goal) > 2 && ++stuckTicks > 100) {
            stuckTicks = 0;
            AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), position().add(0, 1, 0), 40, 0.8, 0.08);
            teleportTo(goal.x, goal.y, goal.z);
            AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), goal.add(0, 1, 0), 40, 0.8, 0.08);
            AnimFx.play(sl, goal, "amanita.sink", 4f, 1f);
            lastProgress = null;
        }
    }

    private boolean stanceLit(ServerLevel sl) {
        return stance != null && Lights.at(sl, BlockPos.containing(stance.x, stance.y + 0.5, stance.z)) >= threshold();
    }

    /** Darkest tile close to (about 3 blocks from) the target; light is weighted far above distance. */
    @Nullable
    private Vec3 chooseStance(ServerLevel sl, @Nullable ServerPlayer tgt) {
        Vec3 me = position();
        Vec3 want = tgt == null ? me : tgt.position();
        int t = threshold();
        BlockPos best = null;
        double bs = Double.MAX_VALUE;
        for (BlockPos p : HollowLayout.stanceTiles(hollowOrigin)) {
            if (!sl.getBlockState(p).getCollisionShape(sl, p).isEmpty() || !sl.getBlockState(p.above()).getCollisionShape(sl, p.above()).isEmpty()
                    || !sl.getBlockState(p.above(2)).getCollisionShape(sl, p.above(2)).isEmpty()) continue;
            int l = Math.max(Lights.at(sl, p), Lights.at(sl, p.above()));
            Vec3 c = Vec3.atBottomCenterOf(p);
            double s = (l >= t ? 100 + l * 4 : l * 1.5) + Math.abs(c.distanceTo(want) - 3) + 0.3 * c.distanceTo(me);
            if (s < bs) { bs = s; best = p; }
        }
        return best == null ? null : Vec3.atBottomCenterOf(best);
    }

    private void face(ServerLevel sl, List<ServerPlayer> ps) {
        AmanitaAction a = action;
        float want;
        if (a == AmanitaAction.SHADOW_LASH && stage != Stage.RECOVERY) {
            want = lockedYaw;
        } else if (a == AmanitaAction.LIGHT_SEEKER_DASH && dashTo != null) {
            Vec3 d = dashTo.subtract(position());
            want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        } else if ((a == AmanitaAction.SPORE_VEIL) && aim != null) {
            Vec3 d = aim.subtract(position());
            want = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
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

    // ================================================================== action slot
    private void runAction(ServerLevel sl, @Nullable Hollow hollow, Phase ph, List<ServerPlayer> ps, long now, boolean hold) {
        AmanitaAction a = action;
        switch (stage) {
            case IDLE -> {
                if (staggerTicks > 0 || ph.combatIndex() < 1 || hold) return;
                if (--gap > 0) return;
                AmanitaAction pick = choose(sl, hollow, ph, ps, now);
                if (pick != null) begin(sl, hollow, ph, ps, pick);
                else gap = 10;
            }
            case TELEGRAPH -> {
                telegraph(sl, hollow, ph, a, stageTicks, ps);
                if (++stageTicks >= a.telegraph) {
                    setStage(Stage.ACTIVE);
                    beginActive(sl, ph, a);
                }
            }
            case ACTIVE -> {
                activeTick(sl, hollow, ph, a, stageTicks, ps);
                if (action != a) return;   // cancelled inside the tick
                if (++stageTicks >= a.activeTicks(ph)) {
                    setStage(Stage.RECOVERY);
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
        AmanitaAction a = action;
        if (a != null) readyAt.put(a, now + Math.round(a.cooldown(ph) / AmanitaConfig.ATTACK_SPEED.get()));
        action = null;
        entityData.set(ACTION, -1);
        setStage(Stage.IDLE);
        clearScratch();
        int base = switch (ph) {
            case P1_DARK_IMMUNITY -> 30;
            case P2_FULL_SNUFF -> 26;
            case P3_LIT_DUEL -> 18;
            case P4_DEEP_BLOOM -> 16;
            default -> 25;
        };
        gap = (int) Math.round(base / AmanitaConfig.ATTACK_SPEED.get());
    }

    private void clearScratch() {
        dashLight = null;
        dashTo = null;
        aim = null;
        arrived = false;
        smother = 0;
    }

    /** Cancel whatever is running (interrupts, commands, phase scripts). */
    public void cancel(String why) {
        AmanitaAction a = action;
        if (a == null) return;
        action = null;
        entityData.set(ACTION, -1);
        setStage(Stage.IDLE);
        clearScratch();
        gap = 20;
        readyAt.put(a, level().getGameTime() + a.cooldown / 2);
    }

    /** Designer command / hollow script: start {@code a} now (skips weights and cooldowns, keeps the telegraph). */
    public boolean force(AmanitaAction a) {
        if (!(level() instanceof ServerLevel sl)) return false;
        cancel("forced");
        staggerTicks = 0;
        begin(sl, hollow(), phase(), players(sl), a);
        return true;
    }

    /** Scripted beats (bloom_open in P0, full_snuff at P2 and P4 close, deep_bloom at P4 entry). */
    public void script(AmanitaAction a) {
        force(a);
    }

    @Nullable
    private AmanitaAction choose(ServerLevel sl, @Nullable Hollow hollow, Phase ph, List<ServerPlayer> ps, long now) {
        List<ServerPlayer> ts = targets(ps);
        if (ts.isEmpty()) return null;
        ServerPlayer near = nearest(ts);
        double nearH = near == null ? 999 : Math.sqrt(near.position().subtract(position()).horizontalDistanceSqr());
        boolean ex = exposed();
        List<BlockPos> lights = census(sl);
        double r = snuffRadius(ph);
        long inRange = lights.stream().filter(p -> p.getCenter().distanceTo(position()) <= r).count();
        List<AmanitaAction> bag = new ArrayList<>();
        for (AmanitaAction a : AmanitaAction.values()) {
            int w = a.weight(ph);
            if (w <= 0 || readyAt.getOrDefault(a, 0L) > now) continue;
            switch (a) {
                case SHADOW_LASH -> {
                    if (nearH > 4.5 || Math.abs(near.getY() - getY()) > 3) continue;
                    w *= 2;   // in reach, she would rather cut you
                }
                case SPORE_VEIL -> { if (nearH > 20) continue; }
                case SNUFF_PULSE -> {
                    if (hollow == null ? !ex : inRange == 0) continue;
                    if (ph == Phase.P1_DARK_IMMUNITY && inRange < 2 && !ex) continue;
                    if (ex) w *= 2;   // lit: she answers the light
                }
                case DARKNESS_HOWL -> { if (lights.isEmpty() && ph != Phase.P2_FULL_SNUFF) continue; }
                case BLOOM_SLAM -> { if (nearH > 10) continue; }
                case LIGHT_SEEKER_DASH -> {
                    if (hollow == null || dashTarget(sl, ts) == null) continue;
                    if (ex) w *= 2;
                }
                default -> {}
            }
            for (int i = 0; i < w; i++) bag.add(a);
        }
        return bag.isEmpty() ? null : bag.get(random.nextInt(bag.size()));
    }

    private double snuffRadius(Phase ph) {
        return ph == Phase.P4_DEEP_BLOOM ? AmanitaConfig.DEEP_SNUFF_RADIUS.get() : AmanitaConfig.SNUFF_RADIUS.get();
    }

    /**
     * light_seeker_dash target: a player carrying light (torch in hand) within 18 blocks and in sight first; otherwise
     * the brightest light source 4..20 blocks away, player-placed first.
     */
    @Nullable
    private Object dashTarget(ServerLevel sl, List<ServerPlayer> ts) {
        for (ServerPlayer p : ts) {
            if (Lights.carriesLight(p) && p.distanceTo(this) <= 18 && p.distanceTo(this) >= 3 && clear(sl, getEyePosition(), p.getEyePosition())) return p;
        }
        Hollow h = hollow();
        if (h == null) return null;
        BlockPos best = null;
        double bs = -1e9;
        for (BlockPos p : h.census(sl)) {
            double d = p.getCenter().distanceTo(position());
            if (d < 4 || d > 20) continue;
            double s = Lights.emission(sl, p, sl.getBlockState(p)) * 2 + (h.placedBy(p) != null ? 3 : 0) - d * 0.2;
            if (s > bs) { bs = s; best = p; }
        }
        return best;
    }

    @Nullable
    private ServerPlayer pickTarget(ServerLevel sl, List<ServerPlayer> ps, AmanitaAction a) {
        List<ServerPlayer> ts = targets(ps);
        if (ts.isEmpty()) return null;
        long now = sl.getGameTime();
        return switch (a) {
            case SHADOW_LASH, BLOOM_SLAM -> nearest(ts);
            case HOLLOW_BOLT -> {
                // a rooted Tenebris player cannot hide in the dark from her
                for (ServerPlayer p : ts) if (rootedUntil.getOrDefault(p.getUUID(), 0L) > now && p.distanceTo(this) < 24) yield p;
                List<ServerPlayer> los = ts.stream().filter(p -> clear(sl, loc("hand_r"), p.getEyePosition())).toList();
                List<ServerPlayer> pool = los.isEmpty() ? ts : los;
                yield random.nextFloat() < 0.3f ? pool.get(random.nextInt(pool.size())) : topThreat(pool);
            }
            default -> topThreat(ts);
        };
    }

    private void begin(ServerLevel sl, @Nullable Hollow hollow, Phase ph, List<ServerPlayer> ps, AmanitaAction a) {
        action = a;
        entityData.set(ACTION, a.ordinal());
        setStage(Stage.TELEGRAPH);
        hitThisCast.clear();
        clearScratch();
        getNavigation().stop();
        ServerPlayer t = pickTarget(sl, ps, a);
        target = t == null ? null : t.getUUID();
        switch (a) {
            case BLOOM_OPEN -> triggerAnim("main", "bloom_open");
            case SHADOW_LASH -> {
                triggerAnim("main", "lash_windup");
                Vec3 tp = t == null ? position().add(getLookAngle()) : t.position();
                lockedYaw = (float) (Mth.atan2(tp.z - getZ(), tp.x - getX()) * Mth.RAD_TO_DEG) - 90f;
            }
            case HOLLOW_BOLT -> triggerAnim("main", "bolt_cast");
            case SPORE_VEIL -> {
                triggerAnim("main", "veil_cast");
                aim = veilPoint(sl, hollow, ph, t);
            }
            case SNUFF_PULSE -> {
                triggerAnim("main", "snuff_windup");
                warnAll(ps, "amanita.warn.snuff", ChatFormatting.DARK_PURPLE);
            }
            case FULL_SNUFF -> {
                triggerAnim("main", "full_snuff_windup");
                warnAll(ps, ph == Phase.P4_DEEP_BLOOM ? "amanita.warn.closing" : "amanita.warn.full_snuff", ChatFormatting.RED);
            }
            case DARKNESS_HOWL -> {
                triggerAnim("main", "howl_windup");
                warnAll(ps, "amanita.warn.howl", ChatFormatting.DARK_PURPLE);
            }
            case BLOOM_SLAM -> {
                triggerAnim("main", "slam_windup");
                warnAll(ps, "amanita.warn.slam", ChatFormatting.GOLD);
            }
            case LIGHT_SEEKER_DASH -> {
                triggerAnim("main", "dash_windup");
                Object dt = dashTarget(sl, targets(ps));
                if (dt instanceof ServerPlayer p) {
                    target = p.getUUID();
                    p.displayClientMessage(Component.translatable("amanita.warn.dash_carrier").withStyle(ChatFormatting.RED), true);
                } else if (dt instanceof BlockPos b) {
                    dashLight = b;
                    target = null;
                }
            }
            case DEEP_BLOOM -> triggerAnim("main", "deep_bloom");
        }
    }

    /** P1/P2: on the target. P3/P4: on the brightest cluster of lights within 16 blocks, if there is one. */
    private Vec3 veilPoint(ServerLevel sl, @Nullable Hollow hollow, Phase ph, @Nullable ServerPlayer t) {
        Vec3 fallback = t == null ? position() : t.position();
        if (hollow == null || (ph != Phase.P3_LIT_DUEL && ph != Phase.P4_DEEP_BLOOM)) return fallback;
        List<BlockPos> ls = hollow.census(sl);
        BlockPos best = null;
        int bn = 0;
        for (BlockPos p : ls) {
            if (p.getCenter().distanceTo(position()) > 16) continue;
            int n = 0;
            for (BlockPos q : ls) if (q.distSqr(p) <= 3.5 * 3.5) n++;
            if (n > bn) { bn = n; best = p; }
        }
        return best == null ? fallback : new Vec3(best.getX() + 0.5, floorY(), best.getZ() + 0.5);
    }

    private void telegraph(ServerLevel sl, @Nullable Hollow hollow, Phase ph, AmanitaAction a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case BLOOM_OPEN -> {
                if (t % 4 == 0) Telegraph.ring(sl, SBParticles.get("gill_glow"), groundUnder(position()), 1 + t * 0.22, 28);
            }
            case SHADOW_LASH -> { if (t % 4 == 0) sector(sl, SBParticles.TARGET_MARK.get(), 4.5, 60); }
            case HOLLOW_BOLT -> { if (t % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("gill_glow"), loc("hand_r"), 5, 0.2, 0.04); }
            case SPORE_VEIL -> {
                if (t % 5 == 0 && aim != null) Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), aim, 3.5, 24);
                if (t % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), loc("cap"), 6, 0.8, 0.03);
            }
            case SNUFF_PULSE -> {
                double r = snuffRadius(ph);
                if (t % 5 == 0) Telegraph.ring(sl, SBParticles.get("gill_glow"), groundUnder(position()), r, Math.max(24, (int) (r * 6)));
                if (t % 8 == 0 && hollow != null)
                    for (BlockPos p : hollow.census(sl)) if (p.getCenter().distanceTo(position()) <= r) Telegraph.point(sl, SBParticles.get("snuff_smoke"), p.getCenter(), 3, 0.2);
            }
            case FULL_SNUFF -> {
                if (t % 6 == 0 && hollow != null) for (BlockPos p : hollow.census(sl)) Telegraph.point(sl, SBParticles.get("snuff_smoke"), p.getCenter(), 3, 0.2);
                if (t % 5 == 0) Telegraph.ring(sl, SBParticles.get("gill_glow"), groundUnder(position()), 2 + (60 - t) * 0.2, 36);
                if (t == 0) AnimFx.play(sl, position(), "amanita.snuff.windup", 12f, 0.7f);
            }
            case DARKNESS_HOWL -> { if (t % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), loc("head"), 6, 0.5, 0.02); }
            case BLOOM_SLAM -> {
                if (t % 5 != 0) return;
                Vec3 g = groundUnder(position());
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), g, 8.5, 56);
                Telegraph.ring(sl, SBParticles.TARGET_MARK.get(), g, 4, 28);
            }
            case LIGHT_SEEKER_DASH -> {
                if (t % 3 != 0) return;
                Vec3 to = dashLight != null ? dashLight.getCenter() : tgt != null ? tgt.position() : null;
                if (to != null) Telegraph.line(sl, SBParticles.TARGET_MARK.get(), groundUnder(position()).add(0, 0.2, 0), new Vec3(to.x, floorY() + 0.2, to.z), 0.8);
                if (dashLight != null) Telegraph.point(sl, SBParticles.get("snuff_smoke"), dashLight.getCenter(), 4, 0.3);
            }
            case DEEP_BLOOM -> { if (t % 4 == 0) Telegraph.ring(sl, SBParticles.get("hollow_spore"), groundUnder(position()), 1 + t * 0.3, 30); }
        }
    }

    private void beginActive(ServerLevel sl, Phase ph, AmanitaAction a) {
        switch (a) {
            case SHADOW_LASH -> triggerAnim("main", "lash");
            case SNUFF_PULSE -> triggerAnim("main", "snuff");
            case FULL_SNUFF -> triggerAnim("main", "full_snuff");
            case DARKNESS_HOWL -> triggerAnim("main", "howl");
            case BLOOM_SLAM -> {
                triggerAnim("main", "slam");
                for (ServerPlayer p : players(sl)) if (p.distanceTo(this) < 20) SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 12);
            }
            case LIGHT_SEEKER_DASH -> {
                Entity tgt = target == null ? null : sl.getEntity(target);
                Vec3 to = dashLight != null ? Vec3.atBottomCenterOf(dashLight) : tgt != null ? tgt.position() : null;
                if (to == null) { cancel("no dash target"); return; }
                BlockPos o = originOrHome();
                double lim = HollowLayout.HALF - 0.8;
                double x = hollowOrigin == null ? to.x : Mth.clamp(to.x, o.getX() + 0.5 - lim, o.getX() + 0.5 + lim);
                double z = hollowOrigin == null ? to.z : Mth.clamp(to.z, o.getZ() + 0.5 - lim, o.getZ() + 0.5 + lim);
                dashTo = new Vec3(x, floorY(), z);
                AnimFx.play(sl, position(), "amanita.dash.go", 5f, 1f);
            }
            default -> {}
        }
    }

    private void activeTick(ServerLevel sl, @Nullable Hollow hollow, Phase ph, AmanitaAction a, int t, List<ServerPlayer> ps) {
        Entity tgt = target == null ? null : sl.getEntity(target);
        switch (a) {
            case BLOOM_OPEN -> {
                if (t != 0) return;
                Vec3 c = loc("cap");
                AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), c, 80, 3, 0.3);
                AnimFx.play(sl, c, "amanita.bloom.open", 10f, 1f);
                for (ServerPlayer p : ps) {
                    Vec3 d = p.position().subtract(position());
                    if (new Vec3(d.x, 0, d.z).length() > 10) continue;
                    push(p, position(), 1.2, 0.4);
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, true));
                    p.displayClientMessage(Component.translatable("amanita.warn.bloom_open").withStyle(ChatFormatting.DARK_PURPLE), true);
                }
            }
            case SHADOW_LASH -> {
                boolean second = (ph == Phase.P3_LIT_DUEL || ph == Phase.P4_DEEP_BLOOM) && t == 11;
                if (t != 1 && !second) return;
                if (second) {
                    triggerAnim("main", "lash");
                    hitThisCast.clear();
                    lockedYaw += 25f;
                }
                AnimFx.play(sl, position(), "amanita.lash.swing", 3f, second ? 1.15f : 1f);
                sector(sl, SBParticles.get("veil_mist"), 4.5, 60);
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || hitThisCast.contains(p.getUUID()) || !inSector(p, 4.5, 60) || Math.abs(p.getY() - getY()) > 3) continue;
                    hitThisCast.add(p.getUUID());
                    p.hurt(damageSources().mobAttack(this), AmanitaConfig.LASH_DAMAGE.get().floatValue());
                    push(p, position(), 0.9, 0.3);
                }
            }
            case HOLLOW_BOLT -> {
                int[] sched = AmanitaAction.boltSchedule(ph);
                double lead = ph == Phase.P4_DEEP_BLOOM ? 0.5 : 0;
                for (int i = 0; i < sched.length; i++) {
                    if (t != sched[i] || tgt == null || !tgt.isAlive()) continue;
                    Vec3 from = loc("hand_r");
                    Vec3 at = tgt.getBoundingBox().getCenter();
                    double flight = from.distanceTo(at) / HollowBoltEntity.SPEED;
                    Vec3 v = tgt.getDeltaMovement();
                    at = at.add(new Vec3(v.x, 0, v.z).scale(flight * lead));
                    Vec3 dir = at.subtract(from).normalize();
                    double fan = sched.length == 3 ? new double[]{0, -10, 10}[i] : 0;
                    dir = dir.yRot((float) Math.toRadians(fan));
                    sl.addFreshEntity(HollowBoltEntity.shoot(sl, this, from, dir.scale(HollowBoltEntity.SPEED)));
                    AnimFx.play(sl, from, "amanita.bolt.cast", 3f, 0.9f + random.nextFloat() * 0.2f);
                }
            }
            case SPORE_VEIL -> {
                if (t != 0 || aim == null) return;
                boolean smotherLights = hollow != null && (ph == Phase.P3_LIT_DUEL || ph == Phase.P4_DEEP_BLOOM);
                veils.add(new Veil(aim, 3.5, 100, smotherLights));
                AnimFx.play(sl, aim, "amanita.veil.bloom", 5f, 1f);
            }
            case SNUFF_PULSE -> {
                if (t != 0) return;
                double r = snuffRadius(ph);
                AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), position().add(0, 1, 0), 60, r * 0.4, 0.05);
                AnimFx.play(sl, position(), "amanita.snuff.pulse", 10f, 1f);
                if (hollow != null) {
                    int n = hollow.snuff(sl, position(), r, "snuff_pulse");
                    hollow.applyDarkness(sl, position(), r, AmanitaConfig.SNUFF_DARKNESS_TICKS.get());
                    if (n > 0) warnAll(ps, "amanita.log.snuff_pulse", ChatFormatting.DARK_PURPLE, n);
                } else {
                    for (ServerPlayer p : ps) if (p.distanceTo(this) <= r) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, AmanitaConfig.SNUFF_DARKNESS_TICKS.get(), 0));
                }
                if (ph == Phase.P3_LIT_DUEL || ph == Phase.P4_DEEP_BLOOM) {
                    for (ServerPlayer p : ps) if (canTarget(p) && p.distanceTo(this) <= r)
                        p.hurt(damageSources().indirectMagic(this, this), AmanitaConfig.SNUFF_DAMAGE.get().floatValue());
                }
                stance = null;
            }
            case FULL_SNUFF -> {
                if (t != 0) return;
                AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), position().add(0, 2, 0), 200, 8, 0.1);
                AnimFx.play(sl, position(), "amanita.snuff.full", 16f, 1f);
                if (hollow != null) hollow.onFullSnuff(sl, this);
                stance = null;
            }
            case DARKNESS_HOWL -> {
                if (t != 0) return;
                AnimFx.play(sl, position(), "amanita.howl", 16f, 1f);
                AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), loc("head"), 60, 2, 0.2);
                if (hollow != null) {
                    hollow.applyDarkness(sl, position(), -1, 120);
                    hollow.hush(sl, AmanitaConfig.HUSH_TICKS.get());
                } else {
                    for (ServerPlayer p : ps) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                }
                warnAll(ps, "amanita.log.hushed_all", ChatFormatting.DARK_PURPLE);
                for (ServerPlayer p : ps) if (canTarget(p) && p.distanceTo(this) <= 6)
                    p.hurt(damageSources().indirectMagic(this, this), AmanitaConfig.HOWL_DAMAGE.get().floatValue());
            }
            case BLOOM_SLAM -> {
                double r = 1 + t * 0.5;
                Vec3 c = groundUnder(position());
                Telegraph.ring(sl, SBParticles.get("hollow_spore"), c, r, Math.max(12, (int) (r * 6)));
                if (t % 4 == 0) AnimFx.play(sl, c, "amanita.slam.wave", 4f, 0.8f + t * 0.02f);
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || hitThisCast.contains(p.getUUID())) continue;
                    double h = Math.sqrt(p.position().subtract(c).horizontalDistanceSqr());
                    if (h < r - 0.9 || h > r + 0.3 || !p.onGround() || Math.abs(p.getY() - c.y) > 1.6) continue;
                    hitThisCast.add(p.getUUID());
                    p.hurt(damageSources().mobAttack(this), AmanitaConfig.SLAM_DAMAGE.get().floatValue());
                    push(p, c, 0.8, 0.6);
                }
                slamBand(sl, hollow, c, r);
            }
            case LIGHT_SEEKER_DASH -> tickDash(sl, hollow, t, ps, tgt);
            case DEEP_BLOOM -> {
                if (t != 0) return;
                AnimFx.serverBurst(sl, SBParticles.get("gill_glow"), loc("cap"), 80, 2.5, 0.1);
                if (hollow != null) hollow.onDeepRooted(sl);
            }
        }
    }

    /** Travel toward dashTo (players in the path are struck once), then smother the light on arrival. */
    private void tickDash(ServerLevel sl, @Nullable Hollow hollow, int t, List<ServerPlayer> ps, @Nullable Entity tgt) {
        if (dashTo == null) { stageTicks = AmanitaAction.LIGHT_SEEKER_DASH.active; return; }
        if (!arrived) {
            Vec3 d = dashTo.subtract(position()).multiply(1, 0, 1);
            double len = d.length();
            if (len < 1.6 || t >= AmanitaAction.DASH_TRAVEL) {
                arrived = true;
                smother = AmanitaAction.DASH_SMOTHER;
                setDeltaMovement(Vec3.ZERO);
                triggerAnim("main", "smother");
                AnimFx.play(sl, position(), "amanita.dash.smother", 5f, 1f);
            } else {
                Vec3 step = d.normalize().scale(Math.min(0.8, len));
                Vec3 before = position();
                move(MoverType.SELF, new Vec3(step.x, -0.08, step.z));
                if (position().distanceTo(before) < 0.05 && t > 4) arrived = true;   // ran into a wall or a column
                if (t % 2 == 0) AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), position().add(0, 1, 0), 4, 0.4, 0.02);
                AABB box = getBoundingBox().inflate(0.4);
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || hitThisCast.contains(p.getUUID()) || !box.intersects(p.getBoundingBox())) continue;
                    hitThisCast.add(p.getUUID());
                    p.hurt(damageSources().mobAttack(this), AmanitaConfig.DASH_DAMAGE.get().floatValue());
                    push(p, position(), 1.0, 0.4);
                }
                return;
            }
        }
        if (--smother > 0) {
            if (smother % 3 == 0) AnimFx.serverBurst(sl, SBParticles.get("snuff_smoke"), loc("cap"), 6, 0.6, 0.02);
            return;
        }
        Vec3 at = dashLight != null ? dashLight.getCenter() : position();
        if (hollow != null) hollow.snuff(sl, at, 2.0, "light_seeker_dash");
        if (tgt instanceof ServerPlayer p && p.distanceTo(this) < 3) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
        stance = null;
        stageTicks = AmanitaAction.LIGHT_SEEKER_DASH.active;   // ends ACTIVE
    }

    // ================================================================== veils
    private void tickVeils(ServerLevel sl, @Nullable Hollow hollow, Phase ph) {
        if (veils.isEmpty()) return;
        List<ServerPlayer> ps = players(sl);
        veils.removeIf(v -> {
            v.left--;
            if (v.left % 4 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * v.r;
                    Telegraph.point(sl, SBParticles.get("veil_mist"), v.c.add(Math.cos(a) * rr, 0.3 + random.nextDouble() * 1.6, Math.sin(a) * rr), 1, 0.1);
                }
            }
            if (v.left % 20 == 0) {
                for (ServerPlayer p : ps) {
                    if (!canTarget(p) || Math.sqrt(p.position().subtract(v.c).horizontalDistanceSqr()) > v.r || Math.abs(p.getY() - v.c.y) > 3) continue;
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, true));
                    p.hurt(damageSources().indirectMagic(this, this), AmanitaConfig.VEIL_DAMAGE.get().floatValue());
                }
            }
            if (v.left > 0) return false;
            if (v.snuffAtEnd && hollow != null) hollow.snuff(sl, v.c.add(0, 1, 0), v.r, "spore_veil");
            return true;
        });
    }

    // ================================================================== attack geometry
    private void sector(ServerLevel sl, ParticleOptions type, double r, double halfDeg) {
        Vec3 c = groundUnder(position());
        for (double rr : new double[]{r * 0.5, r}) {
            for (double d = -halfDeg; d <= halfDeg; d += 12) {
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

    /** bloom_slam band: breaks gill shelves and knocks over floor-standing lights (wall and ceiling lights survive). */
    private void slamBand(ServerLevel sl, @Nullable Hollow hollow, Vec3 c, double r) {
        if (hollowOrigin == null) return;
        for (BlockPos p : HollowLayout.coverBlocks(hollowOrigin)) {
            double h = Math.sqrt(Vec3.atCenterOf(p).subtract(c).horizontalDistanceSqr());
            if (h < r - 0.9 || h > r + 0.3) continue;
            BlockState s = sl.getBlockState(p);
            if (!s.is(AmanitaBlocks.GILL_SHELF.get())) continue;
            sl.levelEvent(2001, p, Block.getId(s));
            sl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        if (hollow == null) return;
        for (BlockPos p : hollow.census(sl)) {
            double h = Math.sqrt(Vec3.atCenterOf(p).subtract(c).horizontalDistanceSqr());
            if (h < r - 0.9 || h > r + 0.3 || !Lights.floorLight(sl, p, hollowOrigin)) continue;
            hollow.snuff(sl, p.getCenter(), 0.5, "bloom_slam");
        }
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

    private Vec3 groundUnder(Vec3 p) {
        return new Vec3(p.x, Math.floor(p.y) + 0.05, p.z);
    }

    // ================================================================== hollow callbacks
    /**
     * She just became exposed. With {@code sudden} (the light at her jumped: someone placed a light at her), an
     * interruptible telegraph is cancelled and she recoils. Either way she looks for dark ground at once.
     */
    public void onExposed(boolean sudden) {
        stance = null;
        stanceIn = 0;
        if (!(level() instanceof ServerLevel sl)) return;
        AmanitaAction a = action;
        if (sudden && a != null && stage == Stage.TELEGRAPH && a.lightInterruptible()) {
            cancel("sudden light");
            staggerTicks = AmanitaConfig.RECOIL_TICKS.get();
            triggerAnim("main", "recoil");
            AnimFx.play(sl, position(), "amanita.recoil", 6f, 1f);
            warnAll(players(sl), "amanita.log.recoil", ChatFormatting.YELLOW);
        }
    }

    /** Deep bloom forced open: stagger, bared (extra damage) for a while. */
    public void bare() {
        cancel("bared");
        staggerTicks = 60;
        triggerAnim("main", "bared");
        if (level() instanceof ServerLevel sl) {
            AnimFx.play(sl, position(), "amanita.bared", 10f, 1f);
            AnimFx.serverBurst(sl, SBParticles.get("gill_glow"), loc("cap"), 60, 1.5, 0.2);
        }
    }

    public void resetForPhase() {
        cancel("phase");
        stance = null;
        getNavigation().stop();
    }

    // ================================================================== damage (the light gate)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        Entity attacker = source.getEntity();
        if (attacker == this || source.getDirectEntity() instanceof HollowBoltEntity || attacker instanceof LampEaterEntity || attacker instanceof HollowSpawnEntity)
            return false;
        ServerLevel sl = (ServerLevel) level();
        Hollow hollow = hollow();
        boolean ex = exposed();
        float mult = hollow == null ? (ex ? 1f : AmanitaConfig.DARK_MULT.get().floatValue()) : hollow.damageMultiplier(sl, this);
        long now = sl.getGameTime();
        if (attacker instanceof ServerPlayer p && gateMsgAt.getOrDefault(p.getUUID(), 0L) < now) {
            String key;
            Object[] args = {};
            Phase ph = phase();
            if (ph == Phase.P0_BLOOM_OPENS) key = "amanita.gate.p0";
            else if (sealed()) key = "amanita.gate.sealed";
            else if (!ex) { key = "amanita.gate.closed"; args = new Object[]{lux(), threshold()}; }
            else { key = hollow != null && hollow.bared(now) ? "amanita.gate.bared" : "amanita.gate.exposed"; args = new Object[]{lux(), Math.round(mult * 100)}; }
            gateMsgAt.put(p.getUUID(), now + (ex ? 200 : 80));
            p.displayClientMessage(Component.translatable(key, args).withStyle(ex ? ChatFormatting.YELLOW : ChatFormatting.DARK_PURPLE), true);
        }
        if (mult <= 0) {
            AnimFx.play(sl, position(), "amanita.absorb", 2f, 1.1f);
            AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), getBoundingBox().getCenter(), 6, 0.5, 0.05);
            return false;
        }
        float dealt = amount * mult;
        if (!ex) dealt = Math.min(dealt, getHealth() - 1);   // soft DR in the dark never kills: she dies only lit
        boolean half = false;
        if (hollow != null && hollow.phase() == Phase.P1_DARK_IMMUNITY && !hollow.snuffedOnce()) {
            float floor = (float) (getMaxHealth() * AmanitaConfig.SNUFF_HP.get());
            if (getHealth() - dealt <= floor) {
                dealt = Math.max(0, getHealth() - floor);
                half = true;
            }
        }
        boolean r = dealt > 0 && super.hurt(source, dealt);
        if (r) {
            if (attacker instanceof Player p) addThreat(p.getUUID(), dealt);
            if (now - hurtAnimAt > 10) { hurtAnimAt = now; triggerAnim("main", "hurt"); }
            if (ex && hollow != null) hollow.onFirstLitHit(sl);
            // burst: exposed damage within 40 ticks breaks an interruptible telegraph (a snuff can be stopped)
            if (now - burstStart > 40) { burstStart = now; burst = 0; }
            burst += dealt;
            AmanitaAction a = action;
            if (ex && burst >= getMaxHealth() * AmanitaConfig.BURST_FRACTION.get() && now > staggerLockUntil && a != null
                    && stage == Stage.TELEGRAPH && a.burstInterruptible()) {
                cancel("damage stagger");
                staggerTicks = 30;
                staggerLockUntil = now + 200;
                triggerAnim("main", "recoil");
                warnAll(players(sl), a == AmanitaAction.SNUFF_PULSE ? "amanita.log.snuff_broken" : "amanita.log.staggered", ChatFormatting.YELLOW);
            }
            if (hollow != null) hollow.onAmanitaDamaged(this);
        }
        if (half && hollow != null) {
            // armour ate part of the clamped hit: land her exactly on the threshold so P2 always starts at the same HP
            float floor = (float) (getMaxHealth() * AmanitaConfig.SNUFF_HP.get());
            if (getHealth() > floor) setHealth(floor);
            hollow.onHalf(sl, this);
        }
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            getNavigation().stop();
            veils.clear();
            triggerAnim("main", "death");
            AnimFx.play(sl, position(), "amanita.death", 10f, 1f);
            Hollow h = hollow();
            if (h != null) h.onAmanitaDied(sl, this, source);
        }
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (level() instanceof ServerLevel sl) {
            if (deathTime % 4 == 0) AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), position().add(0, 2.4 - deathTime * 0.012, 0), 14, 0.8, 0.05);
            if (deathTime % 10 == 0) AnimFx.serverBurst(sl, SBParticles.get("gill_glow"), loc("cap"), 6, 0.6, 0.02);
            if (deathTime >= 140) {
                AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), position().add(0, 1, 0), 140, 1.4, 0.12);
                AnimFx.serverBurst(sl, SBParticles.get("veil_mist"), position().add(0, 1, 0), 60, 1.0, 0.05);
                remove(RemovalReason.KILLED);
            }
        }
    }

    private void warnAll(List<ServerPlayer> ps, String key, ChatFormatting color, Object... args) {
        Component c = Component.translatable(key, args).withStyle(color);
        for (ServerPlayer p : ps) p.displayClientMessage(c, true);
    }

    // ================================================================== client
    private void clientFx() {
        if (isDeadOrDying()) return;
        if (tickCount % 4 == 0) {
            Vec3 h = loc("cap");
            level().addParticle(SBParticles.get("hollow_spore"), h.x + (random.nextDouble() - 0.5) * 2, h.y - random.nextDouble() * 0.6,
                    h.z + (random.nextDouble() - 0.5) * 2, 0, -0.01, 0);
        }
        if (!exposedSynced() && tickCount % 6 == 0) {
            Vec3 g = loc("gills");
            level().addParticle(SBParticles.get("gill_glow"), g.x + (random.nextDouble() - 0.5) * 1.6, g.y, g.z + (random.nextDouble() - 0.5) * 1.6, 0, -0.02, 0);
        }
        if (exposedSynced() && tickCount % 3 == 0) {
            Vec3 f = position().add((random.nextDouble() - 0.5) * 1.2, 1 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 1.2);
            level().addParticle(SBParticles.get("snuff_smoke"), f.x, f.y, f.z, 0, 0.03, 0);
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
        AmanitaAction a = action;
        return String.format(java.util.Locale.ROOT, "amanita hp=%.0f/%.0f action=%s stage=%s t=%d gap=%d stagger=%d veils=%d pos=%.1f,%.1f,%.1f stance=%s",
                getHealth(), getMaxHealth(), a == null ? "-" : a.id(), stage, stageTicks, gap, staggerTicks, veils.size(), getX(), getY(), getZ(),
                stance == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", stance.x, stance.y, stance.z));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hollowOrigin != null) tag.put("Hollow", NbtUtils.writeBlockPos(hollowOrigin));
        if (home != null) {
            tag.putDouble("HomeX", home.x);
            tag.putDouble("HomeY", home.y);
            tag.putDouble("HomeZ", home.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hollowOrigin = NbtUtils.readBlockPos(tag, "Hollow").orElse(null);
        if (tag.contains("HomeX")) home = new Vec3(tag.getDouble("HomeX"), tag.getDouble("HomeY"), tag.getDouble("HomeZ"));
        // a reloaded Amanita resumes idle; the hollow re-scripts a full snuff it still owes (resumeScripts)
        gap = 40;
        resumed = false;
    }

    private static String anim(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<AmanitaEntity> c = new AnimationController<>(this, "main", 4, s -> {
            AmanitaAction a = action();
            Stage st = stage();
            if (isDeadOrDying()) return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(anim("death")));
            if (a == AmanitaAction.LIGHT_SEEKER_DASH && st == Stage.ACTIVE) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("dash")));
            if (staggered()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("stagger")));
            if (s.isMoving()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("walk")));
            if (exposedSynced()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("wilt")));
            if (deepSynced()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("deep_idle")));
            return s.setAndContinue(RawAnimation.begin().thenLoop(anim("idle")));
        });
        for (String n : new String[]{"rise", "bloom_open", "lash_windup", "lash", "bolt_cast", "veil_cast", "snuff_windup", "snuff",
                "full_snuff_windup", "full_snuff", "howl_windup", "howl", "slam_windup", "slam", "dash_windup", "smother", "deep_bloom",
                "hurt", "recoil", "bared"}) {
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
