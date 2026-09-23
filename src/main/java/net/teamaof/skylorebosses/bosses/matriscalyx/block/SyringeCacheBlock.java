package net.teamaof.skylorebosses.bosses.matriscalyx.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.MatrisEncounter;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisItems;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/** Anchor refill: each player may take {@code syringesPerPlayer} purgatives once per fight. */
public class SyringeCacheBlock extends Block {
    public SyringeCacheBlock(Properties p) {
        super(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        MatrisEncounter enc = MatrisEncounter.get(sl);
        if (enc != null && !enc.claimSyringes(player.getUUID())) {
            player.displayClientMessage(Component.translatable("matris_calyx.cache.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        int n = enc == null ? 1 : MatrisConfig.SYRINGES_PER_PLAYER.get();
        ItemStack s = new ItemStack(MatrisItems.PURGATIVE_SYRINGE.get(), n);
        if (!player.addItem(s)) player.drop(s, false);
        AnimFx.play(sl, pos.getCenter(), "matris_calyx.syringe.inject", 1f, 0.7f);
        player.displayClientMessage(Component.translatable("matris_calyx.cache.took", n).withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.CONSUME;
    }
}
