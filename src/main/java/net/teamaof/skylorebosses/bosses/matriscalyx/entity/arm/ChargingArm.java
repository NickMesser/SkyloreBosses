package net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/**
 * Lane runner. Leashed to a lane on its ridge island by the umbilical cord: patrols, paws the ground,
 * charges straight to the lane end, then is stunned against the edge with its dorsal core exposed.
 */
public class ChargingArm extends AbstractRootedArm {
    public static final int IDLE = 0, WALK = 1, CHARGE = 2;
    private static final int TELEGRAPH = 30, IMPACT = 36;
    private static final double HALF_LANE = 14, HALF_WIDTH = 4;
    private Direction.Axis laneAxis = Direction.Axis.X;
    private int phase, phaseTicks, patrolSign = 1;
    private int chargeSign;

    public ChargingArm(EntityType<? extends ChargingArm> type, Level level) {
        super(type, level, ArmType.CHARGING);
    }

    public void setLaneAxis(Direction.Axis a) { laneAxis = a; }

    @Override
    protected boolean tracksTarget() { return phase == 0; }

    private double along(Vec3 p) {
        Vec3 a = Vec3.atBottomCenterOf(anchor);
        return laneAxis == Direction.Axis.X ? p.x - a.x : p.z - a.z;
    }

    private Vec3 laneDir(int sign) {
        return laneAxis == Direction.Axis.X ? new Vec3(sign, 0, 0) : new Vec3(0, 0, sign);
    }

    /** Lane leash instead of point leash: clamp to the lane rectangle, never into the void. */
    @Override
    protected void leash() {
        if (anchor == null) return;
        Vec3 a = Vec3.atBottomCenterOf(anchor);
        double s = along(position());
        double perp = laneAxis == Direction.Axis.X ? getZ() - a.z : getX() - a.x;
        boolean out = Math.abs(s) > HALF_LANE + 1 || Math.abs(perp) > HALF_WIDTH + 1 || getY() < a.y - 4;
        if (out) {
            double cs = Math.max(-HALF_LANE, Math.min(HALF_LANE, s));
            Vec3 back = a.add(laneDir(1).scale(cs));
            teleportTo(back.x, a.y + 0.5, back.z);
        }
    }

    @Override
    protected void tickBehaviour() {
        LivingEntity t = getTarget();
        switch (phase) {
            case 0 -> {
                if (anchor == null) return;
                if (t != null && attackCooldown <= 0 && t.distanceTo(this) < 22 && Math.abs(t.getY() - anchor.getY()) < 8) {
                    double ts = along(t.position()), me = along(position());
                    chargeSign = ts >= me ? 1 : -1;
                    faceDir(laneDir(chargeSign));
                    phase = 1;
                    phaseTicks = TELEGRAPH;
                    setAction(IDLE);
                    triggerAnim("main", "telegraph");
                    return;
                }
                // patrol
                double s = along(position());
                if (Math.abs(s) > HALF_LANE - 2) patrolSign = s > 0 ? -1 : 1;
                faceDir(laneDir(patrolSign));
                move(MoverType.SELF, laneDir(patrolSign).scale(0.08).add(0, -0.08, 0));
                setAction(WALK);
            }
            case 1 -> {
                if (--phaseTicks <= 0) {
                    phase = 2;
                    setAction(CHARGE);
                }
            }
            case 2 -> {
                move(MoverType.SELF, laneDir(chargeSign).scale(0.9).add(0, -0.08, 0));
                for (Player p : playersNear(position().add(0, 2, 0), 3.5)) {
                    p.hurt(damageSources().mobAttack(this), dmg(12f));
                    Vec3 k = laneDir(chargeSign).scale(1.6).add(0, 0.6, 0);
                    p.push(k.x, k.y, k.z);
                    p.hurtMarked = true;
                }
                if (tickCount % 3 == 0 && level() instanceof ServerLevel sl)
                    AnimFx.serverBurst(sl, SBParticles.ROOT_DUST.get(), position(), 4, 1.0, 0.05);
                if (chargeSign * along(position()) >= HALF_LANE - 0.5 || horizontalCollision) {
                    phase = 3;
                    phaseTicks = IMPACT;
                    setAction(IDLE);
                    triggerAnim("main", "impact");
                    openWindowSoon(4); // stunned against the edge: dorsal core exposed
                }
            }
            case 3 -> {
                if (--phaseTicks <= 0) {
                    phase = 0;
                    attackCooldown = 60;
                }
            }
            default -> phase = 0;
        }
    }

    private void faceDir(Vec3 d) {
        faceSmoothly(position().add(d), 12f);
    }

    private void setAction(int a) {
        if (entityData.get(ACTION) != a) entityData.set(ACTION, a);
    }

    @Override
    protected String loopAnimation() {
        return switch (entityData.get(ACTION)) {
            case WALK -> "walk";
            case CHARGE -> "charge";
            default -> "idle";
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("LaneAxis", laneAxis.getName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Direction.Axis a = Direction.Axis.byName(tag.getString("LaneAxis"));
        if (a != null) laneAxis = a;
    }

    @Override
    protected String extraDebug() { return " phase=" + phase + "/" + phaseTicks; }

    @Override
    protected List<String> actionAnimations() {
        return List.of("telegraph", "impact");
    }
}
