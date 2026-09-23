package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.projectile.BileGlob;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/** Ranged lane denial: gurgling telegraph, then three bile globs with lead prediction. */
public class SpittingArm extends AbstractRootedArm {
    private static final int TELEGRAPH = 24, VOLLEY = 30;
    private static final int[] SHOTS = {2, 11, 20};
    private static final double SPEED = 1.6, ACCURACY = 0.6;
    private int phase, phaseTicks;

    public SpittingArm(EntityType<? extends SpittingArm> type, Level level) {
        super(type, level, ArmType.SPITTING);
    }

    @Override
    protected void tickBehaviour() {
        LivingEntity t = getTarget();
        switch (phase) {
            case 0 -> {
                if (t == null || attackCooldown > 0) return;
                if (t.distanceTo(this) < 64 && hasLineOfSight(t)) {
                    phase = 1;
                    phaseTicks = TELEGRAPH;
                    triggerAnim("main", "telegraph");
                }
            }
            case 1 -> {
                if (--phaseTicks <= 0) {
                    phase = 2;
                    phaseTicks = VOLLEY;
                    triggerAnim("main", "volley");
                }
            }
            case 2 -> {
                int elapsed = VOLLEY - phaseTicks;
                for (int s : SHOTS) if (elapsed == s && t != null) spit(t);
                if (--phaseTicks <= 0) {
                    phase = 0;
                    attackCooldown = 70 + random.nextInt(40);
                }
            }
            default -> phase = 0;
        }
    }

    private void spit(LivingEntity t) {
        Vec3 muzzle = AnimFx.locator(this, armType.model, armType.renderScale, "muzzle");
        Vec3 aim = t.getEyePosition().subtract(0, 0.5, 0);
        double flight = aim.distanceTo(muzzle) / SPEED;
        Vec3 lead = t.getDeltaMovement().scale(flight * ACCURACY);
        Vec3 dir = aim.add(lead).subtract(muzzle).normalize();
        BileGlob g = new BileGlob(level(), this, muzzle, dir.scale(SPEED), dmg(6f));
        level().addFreshEntity(g);
    }

    @Override
    protected String extraDebug() { return " phase=" + phase + "/" + phaseTicks; }

    @Override
    protected List<String> actionAnimations() {
        return List.of("telegraph", "volley");
    }
}
