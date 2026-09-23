package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import net.minecraft.core.BlockPos;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;

/**
 * Arena geometry (DESIGN.md section 3), relative to the heart centre (the origin, top surface of the heart island).
 * Six satellites on a rough hexagon ~120 blocks out at varying heights. Arms stand on their satellite's centre.
 */
public final class ArenaLayout {
    public static final int HEART_RADIUS = 40;
    public static final int SATELLITE_DISTANCE = 120;
    /** Y offsets per slot: N nerve, NE grasping, SE spitting, S slam, SW charging, NW mouth. */
    private static final int[] SAT_Y = {18, 30, -10, -24, 0, 12};
    private static final int[] SAT_R = {22, 20, 22, 26, 30, 20};

    private ArenaLayout() {}

    public static BlockPos satellite(BlockPos origin, int slot) {
        double a = Math.toRadians(-90 + 60 * slot);
        return origin.offset((int) Math.round(Math.cos(a) * SATELLITE_DISTANCE), SAT_Y[slot], (int) Math.round(Math.sin(a) * SATELLITE_DISTANCE));
    }

    public static int satelliteRadius(int slot) {
        return SAT_R[slot];
    }

    /** Where an arm stands (on top of its island). */
    public static BlockPos armAnchor(BlockPos origin, ArmType t) {
        return satellite(origin, t.slot).above();
    }

    /** Respawn/return point on the heart island's south rim (with the syringe cache). */
    public static BlockPos anchor(BlockPos origin) {
        return origin.offset(0, 1, 32);
    }

    /** Where the Calyx Bloom rises (the split heart). */
    public static BlockPos heart(BlockPos origin) {
        return origin.above();
    }
}
