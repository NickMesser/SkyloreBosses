package net.teamaof.skylorebosses.bosses.matriscalyx.entity.add;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Flying bladder. Hunts flyers; within 4 blocks it tethers on and drags them (and their aircraft) at
 * 70% speed until it is shot. Tethers break on any damage.
 */
public class InfectedDrifter extends AbstractAdd {
    private LivingEntity tethered;

    public InfectedDrifter(EntityType<? extends InfectedDrifter> type, Level level) {
        super(type, level, "infected_drifter", 1.5f);
        moveControl = new FlyingMoveControl(this, 10, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.FLYING_SPEED, 0.5)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation n = new FlyingPathNavigation(this, level);
        n.setCanOpenDoors(false);
        n.setCanFloat(true);
        return n;
    }

    @Override
    protected void registerGoals() {
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        LivingEntity t = getTarget();
        if (tethered != null) {
            if (!tethered.isAlive() || distanceTo(tethered) > 10) { tethered = null; return; }
            Entity mover = tethered.getVehicle() != null ? tethered.getVehicle() : tethered;
            mover.setDeltaMovement(mover.getDeltaMovement().scale(0.7));
            mover.hurtMarked = true;
            Vec3 hang = tethered.position().add(0, 4, 0);
            setPos(getX() + (hang.x - getX()) * 0.3, getY() + (hang.y - getY()) * 0.3, getZ() + (hang.z - getZ()) * 0.3);
            return;
        }
        if (t != null) {
            Vec3 goal = t.position().add(0, 3, 0);
            Vec3 d = goal.subtract(position());
            if (d.length() > 1) setDeltaMovement(getDeltaMovement().scale(0.8).add(d.normalize().scale(0.08)));
            lookAt(t, 20, 20);
            if (distanceTo(t) < 4.5) {
                tethered = t;
                triggerAnim("main", "tether");
            }
        } else {
            setDeltaMovement(getDeltaMovement().scale(0.9).add(0, Math.sin(tickCount * 0.05) * 0.005, 0));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        tethered = null;
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }

    @Override
    protected String loop() { return "idle"; }

    @Override
    protected List<String> triggerables() {
        return List.of("tether");
    }
}
