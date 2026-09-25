package net.teamaof.skylorebosses.bosses.amanita.encounter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hollow geometry (DESIGN.md §3). Everything is relative to the hollow origin: the centre block of the floor layer.
 * The floor is |x|, |z| <= HALF (27 x 27); walls one further out; the ceiling block layer is at origin.y + CEILING and
 * the air runs from +1 to CEILING-1. Players stand at origin.y + 1. North is -Z; the mouth and the knocker are south (+Z).
 */
public final class HollowLayout {
    public static final int HALF = 13, WALL = HALF + 1, CEILING = 11;
    /** South mouth: |x| <= MOUTH_HALF, y 1..MOUTH_HEIGHT. */
    public static final int MOUTH_HALF = 1, MOUTH_HEIGHT = 3;
    /** Stance candidates: floor tiles every STANCE_STEP blocks. */
    public static final int STANCE_STEP = 2;

    /** Pre-placed, unlit braziers: the teaching props (x, z offsets). */
    public static final int[][] BRAZIERS = {{-12, -6}, {-12, 6}, {12, -6}, {12, 6}, {-6, -12}, {6, -12}};
    /** 2x2 stone columns, floor to ceiling (lower-north-west corner offsets). Non-emitting hard cover. */
    public static final int[][] PILLARS = {{-9, -9}, {8, -9}, {-9, 8}, {8, 8}};
    /** Where adds climb out of the loam. */
    public static final int[][] VENTS = {{-11, -11}, {11, -11}, {-11, 11}, {11, 11}, {0, -12}};

    private HollowLayout() {}

    public static double floorY(BlockPos o) { return o.getY() + 1; }

    /** Where Amanita rises in P0. */
    public static Vec3 bloomBed(BlockPos o) { return Vec3.atBottomCenterOf(o).add(0, 1, -2); }

    public static BlockPos entryPad(BlockPos o) { return o.offset(0, 1, HALF - 1); }

    public static BlockPos knocker(BlockPos o) { return o.offset(0, 1, WALL + 4); }

    public static List<BlockPos> braziers(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] b : BRAZIERS) out.add(o.offset(b[0], 1, b[1]));
        return out;
    }

    public static boolean isPillar(BlockPos o, int dx, int dz) {
        for (int[] p : PILLARS) if (dx >= p[0] && dx <= p[0] + 1 && dz >= p[1] && dz <= p[1] + 1) return true;
        return false;
    }

    /** Gill shelves: six breakable, non-emitting cover walls, two blocks tall. */
    public static List<BlockPos> coverBlocks(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int s : new int[]{-1, 1}) {
            for (int zs : new int[]{-1, 1})
                for (int x = 3; x <= 5; x++)
                    for (int y = 1; y <= 2; y++) out.add(o.offset(s * x, y, zs * 5));
            for (int z = -1; z <= 1; z++)
                for (int y = 1; y <= 2; y++) out.add(o.offset(s * 9, y, z));
        }
        return out;
    }

    public static List<BlockPos> vents(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] v : VENTS) out.add(o.offset(v[0], 1, v[1]));
        return out;
    }

    /** Floor tiles (standing block positions, y = origin + 1) Amanita may pick as a stance. */
    public static List<BlockPos> stanceTiles(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -HALF + 1; x <= HALF - 1; x += STANCE_STEP)
            for (int z = -HALF + 1; z <= HALF - 1; z += STANCE_STEP)
                if (!isPillar(o, x, z)) out.add(o.offset(x, 1, z));
        return out;
    }

    /** Interior air (inclusive block bounds): the census and full-snuff volume. */
    public static BlockPos interiorMin(BlockPos o) { return o.offset(-HALF, 1, -HALF); }

    public static BlockPos interiorMax(BlockPos o) { return o.offset(HALF, CEILING - 1, HALF); }

    public static boolean inInterior(BlockPos o, BlockPos p) {
        int dx = p.getX() - o.getX(), dy = p.getY() - o.getY(), dz = p.getZ() - o.getZ();
        return Math.abs(dx) <= HALF && Math.abs(dz) <= HALF && dy >= 1 && dy <= CEILING - 1;
    }

    /** Players inside this box are participants (includes the mouth landing). */
    public static AABB volume(BlockPos o) {
        return new AABB(o.getX() - WALL, o.getY() - 4, o.getZ() - WALL, o.getX() + WALL + 1, o.getY() + CEILING + 3, o.getZ() + WALL + 7);
    }

    /** Walking in here wakes a dormant hollow (the two rows by the mouth do not). */
    public static AABB trigger(BlockPos o) {
        return new AABB(o.getX() - HALF, o.getY() + 1, o.getZ() - HALF, o.getX() + HALF + 1, o.getY() + CEILING, o.getZ() + HALF - 1);
    }

    /** Amanita must stay inside this box; outside it she sinks into the loam and rises on the bloom bed. */
    public static AABB leash(BlockPos o) {
        return new AABB(o.getX() - HALF, o.getY() - 1, o.getZ() - HALF, o.getX() + HALF + 1, o.getY() + CEILING, o.getZ() + HALF + 1);
    }

    /** Below the hollow, outside the participant volume: anyone here during a fight fell out and is put back. */
    public static AABB fallZone(BlockPos o) {
        return new AABB(o.getX() - WALL - 6, o.getY() - 128, o.getZ() - WALL - 6, o.getX() + WALL + 7, o.getY() - 3, o.getZ() + WALL + 9);
    }
}
