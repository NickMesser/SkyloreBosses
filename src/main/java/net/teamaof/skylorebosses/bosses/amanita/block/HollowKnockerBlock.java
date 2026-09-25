package net.teamaof.skylorebosses.bosses.amanita.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollows;

/**
 * A pale shelf mushroom outside the hollow mouth. Dormant hollow: rapping on it wakes the bloom (starts the fight).
 * During a fight: re-admits a participant through the membrane to the entry pad (the rejoin path after a death).
 */
public class HollowKnockerBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    public HollowKnockerBlock(Properties p) {
        super(p);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Hollows.get(sl).onKnocker(sl, pos, sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
