package net.teamaof.skylorebosses.bosses.amanita.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaConfig;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollow;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollows;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Lights;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Lamp-eater (DESIGN.md §10): a pale moth-grub that crawls to the nearest light it can reach and eats it. It prefers
 * lights near Amanita (it keeps her dark), then the nearest. It reaches lights up to three blocks above the floor:
 * lights hung higher are safe from it (but not from a snuff). Chewing takes a telegraphed second and a half; kill it
 * first. Sated after a few lights it burrows away. With nothing to eat it nips at players.
 */
public class LampEaterEntity extends Monster implements GeoEntity {
    public static final String MODEL = "lamp_eater";
    private static final EntityDataAccessor<Boolean> CHEWING = SynchedEntityData.defineId(LampEaterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BELLY = SynchedEntityData.defineId(LampEaterEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private BlockPos hollowOrigin;
    @Nullable private BlockPos meal;
    private int chew, bites, retargetIn, stuck, attackCd, burrowing, rise = 20;
    @Nullable private Vec3 lastProgress;
    private final Map<BlockPos, Long> blacklist = new HashMap<>();

    public LampEaterEntity(EntityType<? extends LampEaterEntity> type, Level level) {
        super(type, level);
        xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ATTACK_DAMAGE, 2);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(CHEWING, false);
        b.define(BELLY, 0);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance diff, MobSpawnType type, @Nullable SpawnGroupData data) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(AmanitaConfig.EATER_HP.get());
        setHealth(getMaxHealth());
        return super.finalizeSpawn(level, diff, type, data);
    }

    public void bindHollow(BlockPos origin) { hollowOrigin = origin.immutable(); }

    @Nullable public BlockPos hollowOrigin() { return hollowOrigin; }

    @Nullable public BlockPos meal() { return meal; }

    public boolean chewing() { return entityData.get(CHEWING); }

    public int belly() { return entityData.get(BELLY); }

    public int bites() { return bites; }

    @Nullable
    private Hollow hollow() {
        return hollowOrigin == null || !(level() instanceof ServerLevel sl) ? null : Hollows.get(sl).at(hollowOrigin);
    }

    /** Sated, or the phase moved on: sink back into the loam. */
    public void burrow() {
        if (burrowing > 0 || !(level() instanceof ServerLevel sl)) return;
        burrowing = 20;
        getNavigation().stop();
        triggerAnim("main", "burrow");
        AnimFx.play(sl, position(), "amanita.eater.burrow", 3f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (chewing() && tickCount % 2 == 0) {
                Vec3 m = position().add(getLookAngle().scale(0.6)).add(0, 0.4, 0);
                level().addParticle(SBParticles.get("ember"), m.x, m.y, m.z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
            }
            return;
        }
        if (isDeadOrDying()) return;
        ServerLevel sl = (ServerLevel) level();
        if (burrowing > 0) {
            AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), position().add(0, 0.2, 0), 3, 0.3, 0.02);
            if (--burrowing == 0) discard();
            return;
        }
        if (rise > 0) {
            if (rise-- == 20) triggerAnim("main", "rise");
            return;
        }
        Hollow h = hollow();
        if (hollowOrigin != null && (h == null || !h.phase().fighting())) { burrow(); return; }
        long now = sl.getGameTime();
        if (attackCd > 0) attackCd--;
        blacklist.values().removeIf(t -> t < now);
        if (meal != null && Lights.emission(sl, meal, sl.getBlockState(meal)) < AmanitaConfig.SOURCE_MIN_EMISSION.get()) resetMeal();
        if (meal == null || --retargetIn <= 0) {
            BlockPos next = pickMeal(sl, h);
            if (next != null && !next.equals(meal)) resetMeal();
            meal = next;
            retargetIn = 20;
        }
        if (meal != null) eatTowards(sl, h, now);
        else hunt(sl, h);
    }

    private void resetMeal() {
        meal = null;
        chew = 0;
        entityData.set(CHEWING, false);
    }

    /** Reachable light nearest Amanita (within 10 of her), else the nearest reachable light. */
    @Nullable
    private BlockPos pickMeal(ServerLevel sl, @Nullable Hollow h) {
        List<BlockPos> lights = h != null ? h.census(sl) : benchLights(sl);
        Entity boss = h == null ? null : h.amanita(sl);
        double floor = h != null ? h.origin.getY() + 1 : Math.floor(getY());
        BlockPos best = null;
        double bs = Double.MAX_VALUE;
        for (BlockPos p : lights) {
            if (blacklist.containsKey(p) || p.getY() - floor > 2) continue;
            double d = p.getCenter().distanceTo(position());
            double s = d + (boss != null && p.getCenter().distanceTo(boss.position()) <= 10 ? -20 : 0);
            if (s < bs) { bs = s; best = p; }
        }
        return best;
    }

    private List<BlockPos> benchLights(ServerLevel sl) {
        List<BlockPos> out = new java.util.ArrayList<>();
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-10, -2, -10), c.offset(10, 3, 10))) {
            if (Lights.emission(sl, p, sl.getBlockState(p)) >= AmanitaConfig.SOURCE_MIN_EMISSION.get() && Lights.snuffable(sl, p, sl.getBlockState(p)))
                out.add(p.immutable());
        }
        return out;
    }

    private void eatTowards(ServerLevel sl, @Nullable Hollow h, long now) {
        Vec3 c = meal.getCenter();
        double d = c.distanceTo(position().add(0, 0.35, 0));
        if (d <= 2.6) {
            getNavigation().stop();
            getLookControl().setLookAt(c.x, c.y, c.z);
            if (chew == 0) {
                triggerAnim("main", "chew");
                AnimFx.play(sl, position(), "amanita.eater.chew", 3f, 1f);
            }
            entityData.set(CHEWING, true);
            if (++chew % 10 == 0) AnimFx.serverBurst(sl, SBParticles.get("ember"), c, 4, 0.2, 0.03);
            if (chew >= AmanitaConfig.EATER_CHEW_TICKS.get()) {
                boolean ate = h != null ? h.eat(sl, meal) : Lights.snuffOne(sl, meal, false);
                if (ate) {
                    bites++;
                    entityData.set(BELLY, bites);
                    AnimFx.play(sl, c, "amanita.eater.gulp", 3f, 1f);
                    AnimFx.serverBurst(sl, SBParticles.get("snuff_smoke"), c, 10, 0.2, 0.02);
                    heal(2);
                }
                resetMeal();
                if (bites >= AmanitaConfig.EATER_BITES.get()) burrow();
            }
            return;
        }
        chew = 0;
        entityData.set(CHEWING, false);
        if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(c.x, meal.getY(), c.z, 1.0);
        if (lastProgress == null || position().distanceTo(lastProgress) > 0.5) {
            lastProgress = position();
            stuck = 0;
        } else if (++stuck > 100) {
            blacklist.put(meal, now + 200);
            stuck = 0;
            resetMeal();
        }
    }

    private void hunt(ServerLevel sl, @Nullable Hollow h) {
        List<ServerPlayer> ps = h != null ? h.participants(sl) : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16));
        ServerPlayer t = ps.stream().filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
                .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
        if (t == null) { getNavigation().stop(); return; }
        if (distanceTo(t) < 1.6) {
            getNavigation().stop();
            if (attackCd == 0) {
                doHurtTarget(t);
                attackCd = 30;
            }
        } else if (getNavigation().isDone() || tickCount % 10 == 0) {
            getNavigation().moveTo(t, 1.0);
        }
    }

    @Override
    public boolean doHurtTarget(Entity e) {
        triggerAnim("main", "nip");
        return super.doHurtTarget(e);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) {
            AnimFx.play(sl, position(), "amanita.eater.death", 3f, 1f);
            // what it ate spills out as a brief burst of ember light (visual only)
            if (bites > 0) AnimFx.serverBurst(sl, SBParticles.get("ember"), position().add(0, 0.4, 0), 12 * bites, 0.5, 0.1);
        }
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hollowOrigin != null) tag.put("Hollow", NbtUtils.writeBlockPos(hollowOrigin));
        tag.putInt("Bites", bites);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hollowOrigin = NbtUtils.readBlockPos(tag, "Hollow").orElse(null);
        bites = tag.getInt("Bites");
        entityData.set(BELLY, bites);
        rise = 0;
    }

    private static String anim(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<LampEaterEntity> c = new AnimationController<>(this, "main", 3, s -> {
            if (chewing()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("chew_loop")));
            if (s.isMoving()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("walk")));
            return s.setAndContinue(RawAnimation.begin().thenLoop(anim("idle")));
        });
        for (String n : new String[]{"chew", "nip", "burrow", "rise"}) c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, MODEL, 1f, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
