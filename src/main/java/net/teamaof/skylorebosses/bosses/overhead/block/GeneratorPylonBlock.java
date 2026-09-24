package net.teamaof.skylorebosses.bosses.overhead.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.teamaof.skylorebosses.bosses.overhead.encounter.OverheadYards;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlockEntities;

/**
 * Generator pylon core (DESIGN.md §6). Unbreakable as a block: it is destroyed by draining its integrity, which
 * the yard tracks. Melee hits land through {@link #attack}; projectile and explosion hits come in through
 * OverheadCommonEvents. The GeckoLib model draws the whole 3x3x5 tower from this one block.
 */
public class GeneratorPylonBlock extends BaseEntityBlock {
    public static final MapCodec<GeneratorPylonBlock> CODEC = simpleCodec(GeneratorPylonBlock::new);

    public GeneratorPylonBlock(Properties p) {
        super(p);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.block();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorPylonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, OverheadBlockEntities.GENERATOR_PYLON.get(), GeneratorPylonBlockEntity::clientTick) : null;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        strike(level, pos, player);
    }

    /** Shared by the core and its casings. */
    static void strike(Level level, BlockPos core, Player player) {
        if (!(level instanceof ServerLevel sl) || !(level.getBlockEntity(core) instanceof GeneratorPylonBlockEntity be)) return;
        if (!OverheadYards.get(sl).meleePylon(sl, core, player)) be.triggerAnim("main", "hurt");
    }
}
