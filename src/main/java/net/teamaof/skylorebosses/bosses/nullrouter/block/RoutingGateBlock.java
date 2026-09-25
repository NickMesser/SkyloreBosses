package net.teamaof.skylorebosses.bosses.nullrouter.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Service alcove gate (DESIGN.md §6 misroute). Closed: a red holographic lattice that holds a misrouted player for
 * holdTicks. Open: no shape at all (walk and shoot through it), drawn as a faint floor strip. Unbreakable.
 */
public class RoutingGateBlock extends Block {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape STRIP = Block.box(0, 0, 0, 16, 0.5, 16);

    public RoutingGateBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(OPEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(OPEN);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(OPEN) ? STRIP : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState s, BlockGetter level, BlockPos pos) {
        return true;
    }
}
