package net.teamaof.skylorebosses.bosses.overhead.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.teamaof.skylorebosses.bosses.overhead.encounter.YardLayout;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlocks;

/** Invisible collision above a pylon core so the whole rendered tower can be hit; forwards to the core below. */
public class PylonCasingBlock extends Block {
    public PylonCasingBlock(Properties p) {
        super(p);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        BlockPos core = coreBelow(level, pos);
        if (core != null) GeneratorPylonBlock.strike(level, core, player);
    }

    /** @return the pylon core under this casing column, or null */
    public static BlockPos coreBelow(Level level, BlockPos pos) {
        BlockPos p = pos;
        for (int i = 0; i <= YardLayout.CASINGS; i++) {
            p = p.below();
            if (level.getBlockState(p).is(OverheadBlocks.GENERATOR_PYLON.get())) return p;
            if (!level.getBlockState(p).is(OverheadBlocks.PYLON_CASING.get())) return null;
        }
        return null;
    }
}
