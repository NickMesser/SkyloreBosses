package net.teamaof.skylorebosses.bosses.staticdeacon.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Crypts;

/**
 * Hand bell outside the crypt door. Dormant crypt: ringing it asks for the vesting (starts the fight). During a
 * fight: re-admits a player through the grate to the entry pad (the rejoin path after a death). Cleared: says so.
 */
public class SacristyBellBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<SacristyBellBlock> CODEC = simpleCodec(SacristyBellBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public SacristyBellBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
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
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Crypts.get(sl).onBell(sl, pos, sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
