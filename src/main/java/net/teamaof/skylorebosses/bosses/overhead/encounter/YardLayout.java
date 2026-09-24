package net.teamaof.skylorebosses.bosses.overhead.encounter;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Teknari yard geometry (DESIGN.md §3). Everything is relative to the yard origin: the centre block of the floor.
 * Players stand at origin.y + 1. North is -Z; the entry gate and console are south (+Z).
 */
public final class YardLayout {
    /** Floor spans -HALF..HALF on X and Z (61 x 61). */
    public static final int HALF = 30;
    /** Perimeter wall ring and its height. */
    public static final int WALL = 31, WALL_HEIGHT = 4;
    /** Gates: |x| <= GATE_HALF in the north and south walls. */
    public static final int GATE_HALF = 2;
    /** Pylon cores sit this far out on both axes (corner pads). */
    public static final int PYLON_OFFSET = 22;
    /** Pylon column height above the core block (casings). */
    public static final int CASINGS = 4;
    /** Chassis leash: |x|,|z| <= LEASH, y in floor + [LEASH_MIN_Y, LEASH_MAX_Y]. */
    public static final int LEASH = 24;
    public static final double LEASH_MIN_Y = 2.5, LEASH_MAX_Y = 30;
    /** Above floor + CEILING a target is in violation of yard airspace (flak priority). */
    public static final int CEILING = 32;
    /** Carpet lanes run along Z; lane k covers x in [-30 + 15k, -16 + 15k]. */
    public static final int LANES = 4, LANE_WIDTH = 15;

    /** min corner (x, z), size (w along x, d along z, h) of each cover cluster of yard crates */
    public static final List<int[]> COVER = List.of(
            // broken ring round the centre
            new int[]{-9, -3, 2, 6, 2}, new int[]{7, -3, 2, 6, 2}, new int[]{-3, -9, 6, 2, 2}, new int[]{-3, 7, 6, 2, 2},
            // tall stacks on the cardinal lanes
            new int[]{-1, -18, 3, 2, 3}, new int[]{-1, 16, 3, 2, 3}, new int[]{-18, -1, 2, 3, 3}, new int[]{16, -1, 2, 3, 3},
            // pylon approach cover
            new int[]{-15, -15, 2, 2, 2}, new int[]{13, -15, 2, 2, 2}, new int[]{-15, 13, 2, 2, 2}, new int[]{13, 13, 2, 2, 2});

    private YardLayout() {}

    /** Pylon core block: slot 0 NW, 1 NE, 2 SW, 3 SE. */
    public static BlockPos pylon(BlockPos o, int i) {
        int sx = (i & 1) == 0 ? -1 : 1, sz = i < 2 ? -1 : 1;
        return o.offset(sx * PYLON_OFFSET, 1, sz * PYLON_OFFSET);
    }

    public static BlockPos entryPad(BlockPos o) { return o.offset(0, 1, 26); }

    public static BlockPos console(BlockPos o) { return o.offset(0, 1, 37); }

    /** Where the chassis rests before P0 (north cradle). */
    public static Vec3 cradle(BlockPos o) { return Vec3.atBottomCenterOf(o).add(0, 3, -24); }

    public static double floorY(BlockPos o) { return o.getY() + 1; }

    /** Players inside this box are participants. */
    public static AABB volume(BlockPos o) {
        return new AABB(o.getX() - 36, o.getY() - 24, o.getZ() - 36, o.getX() + 37, o.getY() + 48, o.getZ() + 37);
    }

    /** Walking into this box wakes a dormant yard. */
    public static AABB trigger(BlockPos o) {
        return new AABB(o.getX() - 28, o.getY() + 1, o.getZ() - 28, o.getX() + 29, o.getY() + 20, o.getZ() + 29);
    }

    public static boolean fellOut(BlockPos o, Entity e) {
        return e.getY() < o.getY() - 12 && Math.abs(e.getX() - o.getX()) < 44 && Math.abs(e.getZ() - o.getZ()) < 44;
    }

    public static Vec3 clampLeash(BlockPos o, Vec3 p) {
        double x = Math.max(o.getX() - LEASH, Math.min(o.getX() + LEASH + 1, p.x));
        double z = Math.max(o.getZ() - LEASH, Math.min(o.getZ() + LEASH + 1, p.z));
        double y = Math.max(floorY(o) + LEASH_MIN_Y, Math.min(floorY(o) + LEASH_MAX_Y, p.y));
        return new Vec3(x, y, z);
    }

    /** @return lane 0..3 for a world x, or -1 outside the floor */
    public static int lane(BlockPos o, double x) {
        double dx = x - o.getX();
        if (dx < -HALF || dx >= HALF + 1) return -1;
        return Math.min(LANES - 1, (int) Math.floor((dx + HALF) / LANE_WIDTH));
    }

    public static double laneMinX(BlockPos o, int k) { return o.getX() - HALF + k * LANE_WIDTH; }
}
