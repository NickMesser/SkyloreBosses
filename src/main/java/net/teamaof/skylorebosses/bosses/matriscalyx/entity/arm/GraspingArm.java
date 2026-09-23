package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import net.teamaof.skylorebosses.core.fx.AnimFx;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Punishes hovering: a flyer that sits still near its island for 1.5 s gets grabbed off its vehicle,
 * held at the claw, then yanked down onto the island. A missed grab opens a punish window.
 * Players can escape the hold by dismounting (sneak).
 */
public class GraspingArm extends AbstractRootedArm {
    private static final int TELEGRAPH = 24, GRAB = 20, HOLD = 28, YANK = 28;
    private int phase, phaseTicks, hoverTicks;
    private LivingEntity victim;

    public GraspingArm(EntityType<? extends GraspingArm> type, Level level) {
        super(type, level, ArmType.GRASPING);
    }

    @Override
    protected void tickBehaviour() {
        LivingEntity t = getTarget();
        switch (phase) {
            case 0 -> {
                if (t == null || attackCooldown > 0) return;
                double d = t.distanceTo(this);
                boolean hovering = !t.onGround() && t.getDeltaMovement().horizontalDistance() < 0.35 && d < 30;
                hoverTicks = hovering ? hoverTicks + 1 : Math.max(0, hoverTicks - 2);
                if (hoverTicks >= 30 || d < 10) start(1, TELEGRAPH, "telegraph");
            }
            case 1 -> { if (--phaseTicks <= 0) start(2, GRAB, "grab"); }
            case 2 -> {
                if (phaseTicks == GRAB - 6 && t != null) tryCatch(t);
                if (--phaseTicks <= 0) {
                    if (victim != null) start(3, HOLD, null);
                    else { reset(); openWindowSoon(8); }
                }
            }
            case 3 -> {
                if (victim == null || victim.getVehicle() != this) reset(); // escaped by dismounting
                else if (--phaseTicks <= 0) start(4, YANK, "yank");
            }
            case 4 -> {
                if (--phaseTicks <= 0) {
                    if (victim != null && victim.getVehicle() == this) {
                        victim.stopRiding();
                        Vec3 land = Vec3.atBottomCenterOf(anchor != null ? anchor : blockPosition())
                                .add(Vec3.directionFromRotation(0, getYRot()).scale(6)).add(0, 1.5, 0);
                        victim.teleportTo(land.x, land.y, land.z);
                        victim.hurt(damageSources().mobAttack(this), dmg(4f));
                    }
                    reset();
                }
            }
            default -> reset();
        }
    }

    private void start(int p, int ticks, String anim) {
        phase = p;
        phaseTicks = ticks;
        busyTicks = ticks;
        if (anim != null) triggerAnim("main", anim);
    }

    private void reset() {
        phase = 0;
        hoverTicks = 0;
        victim = null;
        attackCooldown = 80;
    }

    private void tryCatch(LivingEntity t) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        Vec3 to = t.position().subtract(position());
        boolean inFront = to.horizontalDistance() < 1 || look.dot(to.multiply(1, 0, 1).normalize()) > 0.35;
        if (t.distanceTo(this) > 32 || !inFront) return;
        if (t.isPassenger()) t.stopRiding(); // ejects the pilot, the aircraft keeps floating
        t.hurt(damageSources().mobAttack(this), dmg(6f));
        if (t instanceof Player || t.getBbHeight() < 3) {
            if (t.startRiding(this, true)) victim = t;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && phase >= 2;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        Vec3 grip = net.teamaof.skylorebosses.core.fx.AnimFx.locator(this, armType.model, armType.renderScale, "grip");
        double lift = phase == 4 ? -8 * (1 - phaseTicks / (double) YANK) : 0;
        move.accept(passenger, grip.x, grip.y - passenger.getBbHeight() * 0.5 + lift, grip.z);
    }

    @Override
    protected String extraDebug() { return " phase=" + phase + "/" + phaseTicks; }

    @Override
    protected List<String> actionAnimations() {
        return List.of("telegraph", "grab", "yank");
    }
}
