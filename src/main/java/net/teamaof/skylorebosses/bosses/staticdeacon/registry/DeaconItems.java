package net.teamaof.skylorebosses.bosses.staticdeacon.registry;

import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class DeaconItems {

    /** Trophy handed to every participant on the kill. */
    public static final RegistrySupplier<Item> STATIC_THURIBLE = SBRegistries.ITEMS.register("static_thurible",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.translatable("item.skylore_bosses.static_thurible.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    static {
        for (RegistrySupplier<Block> b : List.of(DeaconBlocks.CONSECRATED_ENDSTONE, DeaconBlocks.DESECRATED_ENDSTONE, DeaconBlocks.ALTAR_PLINTH,
                DeaconBlocks.CRYPT_TILE, DeaconBlocks.CRYPT_SUBFLOOR, DeaconBlocks.CRYPT_WALL, DeaconBlocks.CRYPT_PILLAR, DeaconBlocks.BERYL_LAMP,
                DeaconBlocks.CRYPT_GRATE, DeaconBlocks.CRYPT_PEW, DeaconBlocks.SACRISTY_BELL)) {
            SBRegistries.ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
        // outside a crypt the Deacon runs "bench test" mode: no floor rules, P2 attack table (for AI bring-up)
        SBRegistries.ITEMS.register("static_deacon_spawn_egg",
                () -> new ArchitecturySpawnEggItem(DeaconEntities.STATIC_DEACON, 0x9fd8c4, 0x5b2a86, new Item.Properties()));
    }

    private DeaconItems() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
