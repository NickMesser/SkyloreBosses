package net.teamaof.skylorebosses.bosses.staticdeacon.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * One block of a consecrated flagstone (DESIGN.md §6). The crypt owns the flagstone's state and only flips
 * {@link #LIT} here (lit = in communion, unlit = reseeding), so the floor reads correctly even in LOD renderers.
 */
public class ConsecratedEndstoneBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ConsecratedEndstoneBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }
}
