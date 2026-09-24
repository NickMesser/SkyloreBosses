package net.teamaof.skylorebosses.bosses.staticdeacon.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Choir stall: a seat plus a tall back on the side opposite {@link #FACING} (the way a sitter faces). Soft cover. */
public class CryptPewBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CryptPewBlock> CODEC = simpleCodec(CryptPewBlock::new);
    private static final VoxelShape SEAT = Block.box(0, 0, 0, 16, 8, 16);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Shapes.or(SEAT, Block.box(0, 8, 12, 16, 16, 16)));
        SHAPES.put(Direction.SOUTH, Shapes.or(SEAT, Block.box(0, 8, 0, 16, 16, 4)));
        SHAPES.put(Direction.WEST, Shapes.or(SEAT, Block.box(12, 8, 0, 16, 16, 16)));
        SHAPES.put(Direction.EAST, Shapes.or(SEAT, Block.box(0, 8, 0, 4, 16, 16)));
    }

    public CryptPewBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(FACING));
    }
}
