package net.teamaof.skylorebosses.bosses.overhead.registry;

import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class OverheadItems {

    /** Trophy handed to every participant on the kill. */
    public static final RegistrySupplier<Item> TARGETING_CORE = SBRegistries.ITEMS.register("targeting_core",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    static {
        for (RegistrySupplier<Block> b : List.of(OverheadBlocks.YARD_PLATING, OverheadBlocks.HAZARD_PLATING, OverheadBlocks.YARD_WALL,
                OverheadBlocks.YARD_SHUTTER, OverheadBlocks.YARD_CRATE, OverheadBlocks.YARD_CONSOLE, OverheadBlocks.GENERATOR_PYLON)) {
            SBRegistries.ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
        // outside a yard the chassis runs "bench test" mode: no gate, P2 attack table (for AI bring-up)
        SBRegistries.ITEMS.register("overhead_spawn_egg",
                () -> new ArchitecturySpawnEggItem(OverheadEntities.OVERHEAD, 0x2e6e70, 0xe2b41e, new Item.Properties()));
    }

    private OverheadItems() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
