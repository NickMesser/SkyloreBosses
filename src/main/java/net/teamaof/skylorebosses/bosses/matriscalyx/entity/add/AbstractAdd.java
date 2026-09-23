package net.teamaof.skylorebosses.bosses.matriscalyx.entity.add;

import java.util.List;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Infected adds birthed by spore vents. Shared GeckoLib wiring. */
public abstract class AbstractAdd extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final String model;
    protected final float scale;

    protected AbstractAdd(EntityType<? extends AbstractAdd> type, Level level, String model, float scale) {
        super(type, level);
        this.model = model;
        this.scale = scale;
        this.xpReward = 3;
    }

    public String model() { return model; }
    public float scale() { return scale; }

    protected String a(String n) { return "animation." + model + "." + n; }

    /** Extra one-shot animations (besides hurt/death). */
    protected List<String> triggerables() { return List.of(); }

    protected String loop() {
        return walkAnimation.isMoving() ? "walk" : "idle";
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && isAlive()) triggerAnim("main", "hurt");
        return r;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) triggerAnim("main", "death");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<AbstractAdd> c = new AnimationController<>(this, "main", 3,
                s -> s.setAndContinue(RawAnimation.begin().thenLoop(a(loop()))));
        c.triggerableAnim("hurt", RawAnimation.begin().thenPlay(a("hurt")));
        c.triggerableAnim("death", RawAnimation.begin().thenPlayAndHold(a("death")));
        for (String t : triggerables()) c.triggerableAnim(t, RawAnimation.begin().thenPlay(a(t)));
        c.setParticleKeyframeHandler(e -> AnimFx.keyframeParticle(this, model, scale, e.getKeyframeData().getEffect(), e.getKeyframeData().getLocator()));
        c.setSoundKeyframeHandler(e -> AnimFx.keyframeSound(this, e.getKeyframeData().getSound(), 1.0f));
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
