package net.teamaof.skylorebosses.bosses.matriscalyx.entity.add;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/** Fast swarmer: when close it swells for a second, then bursts into spore haze (+5 infection nearby). */
public class SporeMite extends AbstractAdd {
    private int swell = -1;

    public SporeMite(EntityType<? extends SporeMite> type, Level level) {
        super(type, level, "spore_mite", 1.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, 1).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        LivingEntity t = getTarget();
        if (swell < 0 && t != null && distanceTo(t) < 2.5) {
            swell = 20;
            triggerAnim("main", "swell");
            getNavigation().stop();
        }
        if (swell >= 0) {
            setDeltaMovement(0, getDeltaMovement().y, 0);
            if (--swell == 0) burst();
        }
    }

    private void burst() {
        if (!(level() instanceof ServerLevel sl)) return;
        AnimFx.serverBurst(sl, SBParticles.SPORE_PUFF.get(), position().add(0, 0.4, 0), 30, 1.2, 0.08);
        AnimFx.play(sl, position(), "matris_calyx.mite.pop", 1f, 1f);
        for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(blockPosition()).inflate(3))) {
            p.hurt(damageSources().mobAttack(this), 3f);
            Infection.add(p, 5);
        }
        hurt(damageSources().genericKill(), Float.MAX_VALUE);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
    }

    @Override
    protected java.util.List<String> triggerables() {
        return java.util.List.of("swell");
    }
}
