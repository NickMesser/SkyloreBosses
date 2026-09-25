package net.teamaof.skylorebosses.bosses.amanita.registry;

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

public final class AmanitaItems {

    /** Trophy handed to every participant on the kill. The pack attaches the shadow artifacts through loot (DESIGN.md §11). */
    public static final RegistrySupplier<Item> HOLLOW_BLOOM_CAP = SBRegistries.ITEMS.register("hollow_bloom_cap",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
                    lines.add(Component.translatable("item.skylore_bosses.hollow_bloom_cap.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            });

    static {
        for (RegistrySupplier<Block> b : List.of(AmanitaBlocks.HOLLOW_WALL, AmanitaBlocks.HOLLOW_LOAM, AmanitaBlocks.HOLLOW_CEILING,
                AmanitaBlocks.HOLLOW_COLUMN, AmanitaBlocks.GILL_SHELF, AmanitaBlocks.HOLLOW_BRAZIER, AmanitaBlocks.HOLLOW_MEMBRANE,
                AmanitaBlocks.HOLLOW_KNOCKER)) {
            SBRegistries.ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
        // outside a hollow Amanita runs "bench test" mode: the light gate applies, no phases (P1 table), stays near her spawn
        SBRegistries.ITEMS.register("amanita_spawn_egg",
                () -> new ArchitecturySpawnEggItem(AmanitaEntities.AMANITA, 0x2b1a33, 0xc9b8d8, new Item.Properties()));
        SBRegistries.ITEMS.register("lamp_eater_spawn_egg",
                () -> new ArchitecturySpawnEggItem(AmanitaEntities.LAMP_EATER, 0xd8cfc0, 0xffb040, new Item.Properties()));
        SBRegistries.ITEMS.register("hollow_spawn_spawn_egg",
                () -> new ArchitecturySpawnEggItem(AmanitaEntities.HOLLOW_SPAWN, 0x5a4a44, 0x9a7ab8, new Item.Properties()));
    }

    private AmanitaItems() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
