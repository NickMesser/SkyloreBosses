package net.teamaof.skylorebosses.bosses.nullrouter.registry;

import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class RouterItems {

    /** Trophy handed to every participant when the queue reaches zero. */
    public static final RegistrySupplier<Item> CLOSED_TICKET = SBRegistries.ITEMS.register("closed_ticket",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.translatable("item.skylore_bosses.closed_ticket.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    static {
        for (RegistrySupplier<Block> b : List.of(RouterBlocks.VAULT_FLOOR, RouterBlocks.VAULT_WALL, RouterBlocks.VAULT_CEILING, RouterBlocks.VAULT_LIGHT,
                RouterBlocks.VAULT_PILLAR, RouterBlocks.CHASSIS_PAD, RouterBlocks.COOLANT_VENT, RouterBlocks.CHANNEL_CONSOLE, RouterBlocks.REQUEST_LAMP,
                RouterBlocks.ROUTING_GATE, RouterBlocks.VAULT_GRATE, RouterBlocks.SERVICE_TERMINAL)) {
            SBRegistries.ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
        // outside a vault the chassis runs "bench test" mode: ghost/solid cycle on a timer, P2 attack table (AI bring-up)
        SBRegistries.ITEMS.register("null_router_spawn_egg",
                () -> new ArchitecturySpawnEggItem(RouterEntities.NULL_ROUTER, 0x2c2f36, 0x40e0ff, new Item.Properties()));
        SBRegistries.ITEMS.register("retry_packet_spawn_egg",
                () -> new ArchitecturySpawnEggItem(RouterEntities.RETRY_PACKET, 0x2c2f36, 0xffa030, new Item.Properties()));
    }

    private RouterItems() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
