package net.teamaof.skylorebosses.bosses.amanita.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.teamaof.skylorebosses.bosses.amanita.block.HollowBrazierBlock;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaBlocks;

/**
 * The mod-placed test hollow (DESIGN.md §3). The shipped hollow is expected to be a Skylore Islands structure; that
 * structure only has to contain the same blocks at the same offsets (see HollowLayout) plus a hollow_knocker, and
 * {@code /skyloreamanita register} adopts it without rebuilding. Nothing placed here emits light. Placement is idempotent.
 */
public final class HollowBuilder {
    private HollowBuilder() {}

    /** Build the whole hollow: shell, loam, columns, gill shelves, unlit braziers, mouth landing and the knocker. */
    public static void build(ServerLevel level, BlockPos o) {
        BlockState wall = AmanitaBlocks.HOLLOW_WALL.get().defaultBlockState();
        BlockState loam = AmanitaBlocks.HOLLOW_LOAM.get().defaultBlockState();
        BlockState ceiling = AmanitaBlocks.HOLLOW_CEILING.get().defaultBlockState();
        int W = HollowLayout.WALL, C = HollowLayout.CEILING;
        for (int x = -W; x <= W; x++) {
            for (int z = -W; z <= W; z++) {
                boolean edge = Math.abs(x) == W || Math.abs(z) == W;
                set(level, o.offset(x, -1, z), wall);
                set(level, o.offset(x, 0, z), edge ? wall : loam);
                set(level, o.offset(x, C, z), ceiling);
                for (int y = 1; y < C; y++) {
                    if (edge) set(level, o.offset(x, y, z), wall);
                    else set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockState column = AmanitaBlocks.HOLLOW_COLUMN.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int[] p : HollowLayout.PILLARS)
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++)
                    for (int y = 1; y < C; y++) set(level, o.offset(p[0] + dx, y, p[1] + dz), column);
        restoreCover(level, o);
        restoreBraziers(level, o);
        // mouth landing outside the south wall + the knocker
        for (int x = -2; x <= 2; x++)
            for (int z = W + 1; z <= W + 6; z++) {
                set(level, o.offset(x, 0, z), loam);
                set(level, o.offset(x, -1, z), wall);
                for (int y = 1; y <= 4; y++) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        set(level, HollowLayout.knocker(o), AmanitaBlocks.HOLLOW_KNOCKER.get().defaultBlockState());
        openMouth(level, o);
    }

    public static void restoreCover(ServerLevel level, BlockPos o) {
        for (BlockPos p : HollowLayout.coverBlocks(o)) set(level, p, AmanitaBlocks.GILL_SHELF.get().defaultBlockState());
    }

    /** Every brazier back, unlit (the hollow always starts dark). */
    public static void restoreBraziers(ServerLevel level, BlockPos o) {
        for (BlockPos p : HollowLayout.braziers(o))
            set(level, p, AmanitaBlocks.HOLLOW_BRAZIER.get().defaultBlockState().setValue(HollowBrazierBlock.LIT, false));
    }

    public static void closeMouth(ServerLevel level, BlockPos o) { mouth(level, o, AmanitaBlocks.HOLLOW_MEMBRANE.get().defaultBlockState()); }

    public static void openMouth(ServerLevel level, BlockPos o) { mouth(level, o, Blocks.AIR.defaultBlockState()); }

    private static void mouth(ServerLevel level, BlockPos o, BlockState s) {
        for (int x = -HollowLayout.MOUTH_HALF; x <= HollowLayout.MOUTH_HALF; x++)
            for (int y = 1; y <= HollowLayout.MOUTH_HEIGHT; y++) set(level, o.offset(x, y, HollowLayout.WALL), s);
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p) != s) level.setBlock(p, s, 2);
    }
}
