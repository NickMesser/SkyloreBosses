package net.teamaof.skylorebosses.bosses.nullrouter.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vaults;

/**
 * Service terminal outside the vault door. Dormant vault: using it opens a ticket (starts the fight). During a fight:
 * re-admits a player to the entry pad (the rejoin path after a death). Cleared: says the queue is empty.
 */
public class ServiceTerminalBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ServiceTerminalBlock> CODEC = simpleCodec(ServiceTerminalBlock::new);

    public ServiceTerminalBlock(Properties p) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Vaults.get(sl).onTerminal(sl, pos, sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
