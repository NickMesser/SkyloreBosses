package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/** Lifesteal pressure: a bite that feeds the most-damaged living arm (capped per window cycle). */
public class MouthArm extends AbstractRootedArm {
    private static final int TELEGRAPH = 20, BITE = 18, FEED = 32;
    private static final float HEAL = 15, HEAL_CAP = 45;
    private int phase, phaseTicks;
    private float healedThisCycle;
    private boolean wasOpen;

    public MouthArm(EntityType<? extends MouthArm> type, Level level) {
        super(type, level, ArmType.MOUTH);
    }

    @Override
    protected void tickBehaviour() {
        if (isOpen() && !wasOpen) healedThisCycle = 0;
        wasOpen = isOpen();
        LivingEntity t = getTarget();
        switch (phase) {
            case 0 -> {
                if (t == null || attackCooldown > 0) return;
                if (t.position().distanceTo(maw()) < 14) {
                    phase = 1;
                    phaseTicks = TELEGRAPH;
                    busyTicks = TELEGRAPH + BITE;
                    triggerAnim("main", "telegraph");
                }
            }
            case 1 -> {
                if (--phaseTicks <= 0) {
                    phase = 2;
                    phaseTicks = BITE;
                    triggerAnim("main", "bite");
                }
            }
            case 2 -> {
                if (phaseTicks == BITE - 4 && t != null && t.position().distanceTo(maw()) < 11) {
                    if (t.hurt(damageSources().mobAttack(this), dmg(10f))) {
                        if (t instanceof Player p) Infection.add(p, 3);
                        phase = 3;
                        phaseTicks = FEED;
                        triggerAnim("main", "feed");
                        feed();
                        return;
                    }
                }
                if (--phaseTicks <= 0) { phase = 0; attackCooldown = 50; }
            }
            case 3 -> { if (--phaseTicks <= 0) { phase = 0; attackCooldown = 60; } }
            default -> phase = 0;
        }
    }

    private Vec3 maw() {
        return AnimFx.locator(this, armType.model, armType.renderScale, "maw");
    }

    private void feed() {
        if (healedThisCycle >= HEAL_CAP) return;
        List<AbstractRootedArm> arms = level().getEntitiesOfClass(AbstractRootedArm.class, new AABB(blockPosition()).inflate(320),
                a -> a.isAlive() && a.getHealth() < a.getMaxHealth()
                        && (encounterOrigin() == null ? a.encounterOrigin() == null : encounterOrigin().equals(a.encounterOrigin())));
        arms.stream().min(Comparator.comparingDouble(a -> a.getHealth() / a.getMaxHealth())).ifPresent(a -> {
            float h = Math.min(HEAL, HEAL_CAP - healedThisCycle);
            a.heal(h);
            healedThisCycle += h;
        });
    }

    @Override
    protected String extraDebug() { return " phase=" + phase + "/" + phaseTicks; }

    @Override
    protected List<String> actionAnimations() {
        return List.of("telegraph", "bite", "feed");
    }
}
