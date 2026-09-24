package net.teamaof.skylorebosses.bosses.overhead.encounter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlocks;

/**
 * The mod-placed test yard (DESIGN.md §3). The shipped Skylore yard is expected to be a Skylore Islands structure;
 * that structure only has to contain the same blocks at the same offsets (see YardLayout) plus a yard_console, and
 * {@code /skyloreoverhead register} adopts it without rebuilding. Placement is idempotent.
 */
public final class YardBuilder {
    private YardBuilder() {}

    /** Build the whole yard, including a stone underside so it reads as a sky island. */
    public static void build(ServerLevel level, BlockPos o) {
        BlockState plating = OverheadBlocks.YARD_PLATING.get().defaultBlockState();
        BlockState hazard = OverheadBlocks.HAZARD_PLATING.get().defaultBlockState();
        BlockState wall = OverheadBlocks.YARD_WALL.get().defaultBlockState();
        int H = YardLayout.HALF;
        // floor + lane lines + border stripe, clear the air above
        for (int x = -H; x <= H; x++) {
            for (int z = -H; z <= H; z++) {
                boolean border = Math.abs(x) >= H - 1 || Math.abs(z) >= H - 1;
                boolean laneLine = (x == -15 || x == 0 || x == 15) && Math.floorMod(z, 4) < 2;
                set(level, o.offset(x, 0, z), border || laneLine ? hazard : plating);
                for (int y = 1; y <= 24; y++) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        // sky-island underside
        for (int d = 1; d <= 7; d++) {
            int r = H - d * 3;
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > (r + 6) * (r + 6)) continue;
                    set(level, o.offset(x, -d, z), (d == 1 ? Blocks.POLISHED_DEEPSLATE : d < 4 ? Blocks.DEEPSLATE : Blocks.STONE).defaultBlockState());
                }
        }
        // perimeter wall, open gates
        int W = YardLayout.WALL;
        for (int i = -W; i <= W; i++) {
            for (int y = 0; y <= YardLayout.WALL_HEIGHT; y++) {
                BlockState s = wall;
                boolean gateCol = Math.abs(i) <= YardLayout.GATE_HALF && y > 0;
                set(level, o.offset(i, y, -W), gateCol ? Blocks.AIR.defaultBlockState() : s);
                set(level, o.offset(i, y, W), gateCol ? Blocks.AIR.defaultBlockState() : s);
                set(level, o.offset(-W, y, i), s);
                set(level, o.offset(W, y, i), s);
            }
        }
        // south apron + console, north cradle
        for (int x = -3; x <= 3; x++)
            for (int z = W + 1; z <= W + 7; z++) {
                set(level, o.offset(x, 0, z), Math.abs(x) == 3 ? hazard : plating);
                set(level, o.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        set(level, YardLayout.console(o), OverheadBlocks.YARD_CONSOLE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        for (int x = -4; x <= 4; x++)
            for (int z = -27; z <= -21; z++)
                if (Math.abs(x) == 4 || z == -27 || z == -21) set(level, o.offset(x, 1, z), wall);
        // pylon pads
        for (int i = 0; i < 4; i++) {
            BlockPos c = YardLayout.pylon(o, i);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) set(level, c.offset(x, -1, z), hazard);
            placePylon(level, c);
        }
        restoreCover(level, o);
        openGates(level, o);
    }

    public static void placePylon(ServerLevel level, BlockPos core) {
        set(level, core, OverheadBlocks.GENERATOR_PYLON.get().defaultBlockState());
        for (int k = 1; k <= YardLayout.CASINGS; k++) set(level, core.above(k), OverheadBlocks.PYLON_CASING.get().defaultBlockState());
    }

    public static boolean pylonIntact(ServerLevel level, BlockPos core) {
        if (!level.getBlockState(core).is(OverheadBlocks.GENERATOR_PYLON.get())) return false;
        for (int k = 1; k <= YardLayout.CASINGS; k++) if (!level.getBlockState(core.above(k)).is(OverheadBlocks.PYLON_CASING.get())) return false;
        return true;
    }

    public static List<BlockPos> coverBlocks(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] c : YardLayout.COVER)
            for (int x = 0; x < c[2]; x++)
                for (int z = 0; z < c[3]; z++)
                    for (int y = 1; y <= c[4]; y++) out.add(o.offset(c[0] + x, y, c[1] + z));
        return out;
    }

    public static void restoreCover(ServerLevel level, BlockPos o) {
        BlockState crate = OverheadBlocks.YARD_CRATE.get().defaultBlockState();
        for (BlockPos p : coverBlocks(o)) set(level, p, crate);
    }

    public static void closeGates(ServerLevel level, BlockPos o) { gates(level, o, OverheadBlocks.YARD_SHUTTER.get().defaultBlockState()); }

    public static void openGates(ServerLevel level, BlockPos o) { gates(level, o, Blocks.AIR.defaultBlockState()); }

    private static void gates(ServerLevel level, BlockPos o, BlockState s) {
        int W = YardLayout.WALL;
        for (int x = -YardLayout.GATE_HALF; x <= YardLayout.GATE_HALF; x++)
            for (int y = 1; y <= YardLayout.WALL_HEIGHT; y++) {
                set(level, o.offset(x, y, -W), s);
                set(level, o.offset(x, y, W), s);
            }
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p) != s) level.setBlock(p, s, 2);
    }
}
