package net.teamaof.skylorebosses.bosses.amanita.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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
 * Hollow-spawn (DESIGN.md §10): a spore-husk thrall that climbs out of the loam and fights players while the
 * lamp-eaters work. Photophobic like its mistress: it takes +50% damage standing in light 7+, and burns in light 12+.
 * Lighting the hollow is also how you thin them.
 */
public class HollowSpawnEntity extends Monster implements GeoEntity {
    public static final String MODEL = "hollow_spawn";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private BlockPos hollowOrigin;
    private int rise = 20, dissolving;

    public HollowSpawnEntity(EntityType<? extends HollowSpawnEntity> type, Level level) {
        super(type, level);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ATTACK_DAMAGE, 4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance diff, MobSpawnType type, @Nullable SpawnGroupData data) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(AmanitaConfig.SPAWN_HP.get());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(AmanitaConfig.SPAWN_DAMAGE.get());
        setHealth(getMaxHealth());
        return super.finalizeSpawn(level, diff, type, data);
    }

    public void bindHollow(BlockPos origin) { hollowOrigin = origin.immutable(); }

    @Nullable public BlockPos hollowOrigin() { return hollowOrigin; }

    @Nullable
    private Hollow hollow() {
        return hollowOrigin == null || !(level() instanceof ServerLevel sl) ? null : Hollows.get(sl).at(hollowOrigin);
    }

    public void dissolve() {
        if (dissolving > 0 || !(level() instanceof ServerLevel sl)) return;
        dissolving = 20;
        getNavigation().stop();
        triggerAnim("main", "dissolve");
        AnimFx.play(sl, position(), "amanita.spawn.dissolve", 3f, 1f);
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || rise > 0 || dissolving > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || isDeadOrDying()) return;
        if (dissolving > 0) {
            AnimFx.serverBurst(sl, SBParticles.get("hollow_spore"), position().add(0, 0.8, 0), 4, 0.4, 0.02);
            if (--dissolving == 0) discard();
            return;
        }
        if (rise > 0 && rise-- == 19) triggerAnim("main", "rise");
        Hollow h = hollow();
        if (hollowOrigin != null && (h == null || !h.phase().fighting())) { dissolve(); return; }
        if (tickCount % 20 == 0 && Lights.sample(sl, this) >= 12 && AmanitaConfig.SPAWN_LIGHT_BURN.get() > 0) {
            hurt(damageSources().magic(), AmanitaConfig.SPAWN_LIGHT_BURN.get().floatValue());
            AnimFx.serverBurst(sl, SBParticles.get("snuff_smoke"), position().add(0, 1.4, 0), 6, 0.3, 0.03);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof AmanitaEntity || source.getDirectEntity() instanceof HollowBoltEntity) return false;
        if (level() instanceof ServerLevel sl && Lights.sample(sl, this) >= AmanitaConfig.LIGHT_THRESHOLD.get()) amount *= 1.5f;
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity e) {
        triggerAnim("main", "attack");
        return super.doHurtTarget(e);
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hollowOrigin != null) tag.put("Hollow", NbtUtils.writeBlockPos(hollowOrigin));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hollowOrigin = NbtUtils.readBlockPos(tag, "Hollow").orElse(null);
        rise = 0;
    }

    private static String anim(String n) { return "animation." + MODEL + "." + n; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<HollowSpawnEntity> c = new AnimationController<>(this, "main", 3, s -> {
            if (s.isMoving()) return s.setAndContinue(RawAnimation.begin().thenLoop(anim("walk")));
            return s.setAndContinue(RawAnimation.begin().thenLoop(anim("idle")));
        });
        for (String n : new String[]{"rise", "attack", "dissolve"}) c.triggerableAnim(n, RawAnimation.begin().thenPlay(anim(n)));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, MODEL, 1f, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
