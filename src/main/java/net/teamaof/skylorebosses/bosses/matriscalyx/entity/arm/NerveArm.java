package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/**
 * No direct damage. Pulses a buff to the other five arms: shorter open windows and +20% damage.
 * Killing it first strips the buff and makes the others flinch (teaches kill order).
 */
public class NerveArm extends AbstractRootedArm {
    private int pulseTimer = 60;
    private int pendingPulse = -1;

    public NerveArm(EntityType<? extends NerveArm> type, Level level) {
        super(type, level, ArmType.NERVE);
    }

    @Override
    protected boolean tracksTarget() { return false; }

    @Override
    protected void tickBehaviour() {
        if (--pulseTimer <= 0) {
            pulseTimer = MatrisConfig.NERVE_PULSE_TICKS.get();
            triggerAnim("main", "pulse");
            pendingPulse = 13; // the ganglion flash lands 0.65 s into the animation
        }
        if (pendingPulse >= 0 && pendingPulse-- == 0 && level() instanceof ServerLevel sl) {
            Vec3 from = AnimFx.locator(this, armType.model, armType.renderScale, "ganglion");
            List<AbstractRootedArm> arms = sl.getEntitiesOfClass(AbstractRootedArm.class, new AABB(from, from).inflate(320),
                    a -> a != this && a.isAlive() && sameEncounter(a));
            for (AbstractRootedArm a : arms) {
                a.buff(MatrisConfig.NERVE_PULSE_TICKS.get() + 20);
                nerveLine(sl, from, a.coreWorld());
            }
        }
    }

    private boolean sameEncounter(AbstractRootedArm a) {
        return encounterOrigin() == null ? a.encounterOrigin() == null : encounterOrigin().equals(a.encounterOrigin());
    }

    /** A cyan spark line from the ganglion to each buffed arm (readable across the arena). */
    private static void nerveLine(ServerLevel sl, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(80, d.length() / 3);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) Math.max(1, n)));
            sl.sendParticles(SBParticles.NERVE_SPARK.get(), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("pulse");
    }
}
