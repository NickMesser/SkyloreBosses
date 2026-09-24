package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Undercroft geometry (DESIGN.md §3). Everything is relative to the crypt origin: the centre block of the floor layer,
 * directly under the altar plinth. The floor is a grid of 3x3 flagstone slots, columns cx -3..3 (x = 3cx-1..3cx+1)
 * and rows cz -5..5 (z = 3cz-1..3cz+1). Players stand at origin.y + 1. North is -Z; the door and bell are south (+Z).
 */
public final class CryptLayout {
    public enum Kind { NAVE, PLINTH, PILLAR, AISLE }

    /** Slot grid half extents (columns -COLS..COLS, rows -ROWS..ROWS). */
    public static final int COLS = 3, ROWS = 5;
    /** Floor spans |x| <= HALF_X, |z| <= HALF_Z; walls one further out. */
    public static final int HALF_X = 3 * COLS + 1, HALF_Z = 3 * ROWS + 1;
    public static final int WALL_X = HALF_X + 1, WALL_Z = HALF_Z + 1;
    /** Ceiling block layer is at origin.y + CEILING; air from +1 to CEILING-1. */
    public static final int CEILING = 9;
    /** South door: |x| <= DOOR_HALF, y 1..DOOR_HEIGHT. */
    public static final int DOOR_HALF = 1, DOOR_HEIGHT = 3;
    /** The altar dais is 3x3 and PLINTH_HEIGHT layers tall (top surface at origin.y + PLINTH_HEIGHT). */
    public static final int PLINTH_HEIGHT = 2;

    /** Every slot in index order (row-major from north-west). Index is stable and used in events and saves. */
    public static final List<int[]> SLOTS;

    static {
        List<int[]> s = new ArrayList<>();
        for (int cz = -ROWS; cz <= ROWS; cz++)
            for (int cx = -COLS; cx <= COLS; cx++) s.add(new int[]{cx, cz});
        SLOTS = Collections.unmodifiableList(s);
    }

    private CryptLayout() {}

    public static Kind kind(int cx, int cz) {
        if (cx == 0 && cz == 0) return Kind.PLINTH;
        if (Math.abs(cx) == 2 && (Math.abs(cz) == 2 || Math.abs(cz) == 4)) return Kind.PILLAR;
        if (Math.abs(cx) == 3 && cz != 0) return Kind.AISLE;
        return Kind.NAVE;
    }

    public static int index(int cx, int cz) {
        return (cz + ROWS) * (2 * COLS + 1) + (cx + COLS);
    }

    /** Centre floor block of slot (cx, cz). */
    public static BlockPos slotCenter(BlockPos o, int cx, int cz) {
        return o.offset(3 * cx, 0, 3 * cz);
    }

    /** The nine floor blocks of a slot. */
    public static List<BlockPos> slotBlocks(BlockPos o, int cx, int cz) {
        List<BlockPos> out = new ArrayList<>(9);
        BlockPos c = slotCenter(o, cx, cz);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) out.add(c.offset(dx, 0, dz));
        return out;
    }

    /** @return {cx, cz} of the slot containing world x/z, or null outside the floor */
    @Nullable
    public static int[] slotAt(BlockPos o, int x, int z) {
        int dx = x - o.getX(), dz = z - o.getZ();
        if (Math.abs(dx) > HALF_X || Math.abs(dz) > HALF_Z) return null;
        return new int[]{Math.floorDiv(dx + 1, 3), Math.floorDiv(dz + 1, 3)};
    }

    public static double floorY(BlockPos o) { return o.getY() + 1; }

    public static double plinthTopY(BlockPos o) { return o.getY() + PLINTH_HEIGHT; }

    /** Where the Deacon stands on the plinth. */
    public static Vec3 plinthStand(BlockPos o) { return Vec3.atBottomCenterOf(o).add(0, PLINTH_HEIGHT, 0); }

    public static boolean onPlinth(BlockPos o, Vec3 p) {
        return Math.abs(p.x - (o.getX() + 0.5)) <= 1.6 && Math.abs(p.z - (o.getZ() + 0.5)) <= 1.6 && p.y >= plinthTopY(o) - 0.2 && p.y <= plinthTopY(o) + 1.2;
    }

    /** Column of air above the plinth that nothing may be placed in during a fight. */
    public static AABB plinthColumn(BlockPos o) {
        return new AABB(o.getX() - 1, plinthTopY(o), o.getZ() - 1, o.getX() + 2, plinthTopY(o) + 4, o.getZ() + 2);
    }

    public static BlockPos entryPad(BlockPos o) { return o.offset(0, 1, 3 * ROWS - 1); }

    public static BlockPos bell(BlockPos o) { return o.offset(0, 1, WALL_Z + 4); }

    /** Pillar column blocks stand at these slots' centres. */
    public static boolean isPillarColumn(BlockPos o, BlockPos p) {
        int[] s = slotAt(o, p.getX(), p.getZ());
        return s != null && kind(s[0], s[1]) == Kind.PILLAR && p.getX() == o.getX() + 3 * s[0] && p.getZ() == o.getZ() + 3 * s[1];
    }

    /** Pews: one stall of three blocks along X in the middle row of each aisle slot, facing the nave. */
    public static List<BlockPos> pewBlocks(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] s : SLOTS) {
            if (kind(s[0], s[1]) != Kind.AISLE) continue;
            BlockPos c = slotCenter(o, s[0], s[1]).above();
            for (int dx = -1; dx <= 1; dx++) out.add(c.offset(dx, 0, 0));
        }
        return out;
    }

    /** Players inside this box are participants. */
    public static AABB volume(BlockPos o) {
        return new AABB(o.getX() - WALL_X, o.getY() - 4, o.getZ() - WALL_Z, o.getX() + WALL_X + 1, o.getY() + CEILING + 3, o.getZ() + WALL_Z + 7);
    }

    /** Interior air of the nave: walking in here wakes a dormant crypt (the last row by the door does not). */
    public static AABB trigger(BlockPos o) {
        return new AABB(o.getX() - HALF_X, o.getY() + 1, o.getZ() - HALF_Z, o.getX() + HALF_X + 1, o.getY() + CEILING, o.getZ() + HALF_Z);
    }

    /** The Deacon must stay inside this box; outside it, it static-steps back to the plinth. */
    public static AABB leash(BlockPos o) {
        return new AABB(o.getX() - HALF_X, o.getY() - 1, o.getZ() - HALF_Z, o.getX() + HALF_X + 1, o.getY() + CEILING, o.getZ() + HALF_Z + 1);
    }

    /** Below the crypt, outside the participant volume: anyone here during a fight fell out and is put back. */
    public static AABB fallZone(BlockPos o) {
        return new AABB(o.getX() - WALL_X - 6, o.getY() - 128, o.getZ() - WALL_Z - 6, o.getX() + WALL_X + 7, o.getY() - 3, o.getZ() + WALL_Z + 9);
    }

    public static boolean fellOut(BlockPos o, Entity e) {
        return fallZone(o).contains(e.position());
    }

    /** Lane centre x for litany_beam along a slot column. */
    public static double laneX(BlockPos o, int cx) { return o.getX() + 0.5 + 3 * cx; }

    public static double rowZ(BlockPos o, int cz) { return o.getZ() + 0.5 + 3 * cz; }
}
