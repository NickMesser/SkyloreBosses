package net.teamaof.skylorebosses.bosses.amanita.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollows;

/**
 * A coal pile in an iron hook: the hollow's teaching prop (DESIGN.md §3, §6). Ships unlit. Right-click with anything in
 * {@code #skylore_bosses:amanita/igniters} (flint and steel, fire charge, torch...) to light it; a Laevis player can
 * light it bare-handed. It burns at light 15 until a snuff or a lamp-eater puts it out, and can always be relit, so a
 * player who brought no lights is never soft-locked. The hollow decides whether a light may be lit right now (hush).
 */
public class HollowBrazierBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final TagKey<Item> IGNITERS = TagKey.create(Registries.ITEM, SkyloreBosses.id("amanita/igniters"));
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 11, 14);

    public HollowBrazierBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(LIT) || !stack.is(IGNITERS)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Hollows.get(sl).onBrazierUse(sl, pos, sp, stack);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(LIT)) return InteractionResult.PASS;
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Hollows.get(sl).onBrazierUse(sl, pos, sp, ItemStack.EMPTY);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
