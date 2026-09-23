package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/**
 * Area denial on its island: a 2.2 s wind-up with a marked impact ring, then a slam. The fist stays stuck,
 * which is a guaranteed open window.
 */
public class SlamArm extends AbstractRootedArm {
    private static final int TELEGRAPH = 44, SLAM = 32, RADIUS = 7;
    private int phase, phaseTicks;
    private Vec3 impact;

    public SlamArm(EntityType<? extends SlamArm> type, Level level) {
        super(type, level, ArmType.SLAM);
    }

    @Override
    protected void tickBehaviour() {
        LivingEntity t = getTarget();
        switch (phase) {
            case 0 -> {
                if (t == null || attackCooldown > 0 || anchor == null) return;
                double horiz = t.position().subtract(Vec3.atBottomCenterOf(anchor)).horizontalDistance();
                if (horiz < 18 && Math.abs(t.getY() - anchor.getY()) < 10) {
                    Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
                    impact = Vec3.atBottomCenterOf(anchor).add(dir.scale(8));
                    phase = 1;
                    phaseTicks = TELEGRAPH;
                    busyTicks = TELEGRAPH + SLAM;
                    triggerAnim("main", "telegraph");
                }
            }
            case 1 -> {
                if (phaseTicks % 4 == 0) ring();
                if (--phaseTicks <= 0) {
                    phase = 2;
                    phaseTicks = SLAM;
                    triggerAnim("main", "slam");
                }
            }
            case 2 -> {
                if (phaseTicks == SLAM - 4) strike();
                if (--phaseTicks <= 0) {
                    phase = 0;
                    attackCooldown = 70;
                }
            }
            default -> phase = 0;
        }
    }

    private void ring() {
        if (!(level() instanceof ServerLevel sl) || impact == null) return;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            sl.sendParticles(SBParticles.ROOT_DUST.get(), impact.x + Math.cos(a) * RADIUS, impact.y + 0.3, impact.z + Math.sin(a) * RADIUS,
                    1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    private void strike() {
        if (!(level() instanceof ServerLevel sl) || impact == null) return;
        for (Player p : playersNear(impact, RADIUS + 1)) {
            if (p.position().distanceTo(impact) > RADIUS + 0.5) continue;
            p.hurt(damageSources().mobAttack(this), dmg(14f));
            p.push(0, 1.1, 0);
            p.hurtMarked = true;
        }
        AnimFx.serverBurst(sl, SBParticles.ROOT_DUST.get(), impact.add(0, 0.5, 0), 60, 3.0, 0.15);
        AnimFx.serverBurst(sl, SBParticles.FLESH_CHUNKS.get(), impact.add(0, 1, 0), 20, 1.5, 0.3);
        openWindowSoon(6); // the fist is stuck in the ground
    }

    @Override
    protected String extraDebug() { return " phase=" + phase + "/" + phaseTicks; }

    @Override
    protected List<String> actionAnimations() {
        return List.of("telegraph", "slam");
    }
}
