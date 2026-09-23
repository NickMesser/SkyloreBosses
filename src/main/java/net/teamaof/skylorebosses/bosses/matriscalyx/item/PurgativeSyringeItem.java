package net.teamaof.skylorebosses.bosses.matriscalyx.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;

/** Act I medicine: -40 infection. Use on yourself, or on another player (anyone can; doctors cure better). */
public class PurgativeSyringeItem extends Item {
    public static final int AMOUNT = 40;

    public PurgativeSyringeItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Infection.cure(sp, AMOUNT, sp);
            stack.consume(1, player);
            player.getCooldowns().addCooldown(this, 20);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target instanceof ServerPlayer other) {
            Infection.cure(other, AMOUNT, player);
            stack.consume(1, player);
            return InteractionResult.CONSUME;
        }
        return target instanceof Player ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.skylore_bosses.purgative_syringe.desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.skylore_bosses.purgative_syringe.canon").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
    }
}
