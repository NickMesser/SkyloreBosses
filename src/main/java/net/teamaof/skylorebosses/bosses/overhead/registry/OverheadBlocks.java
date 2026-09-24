package net.teamaof.skylorebosses.bosses.overhead.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.teamaof.skylorebosses.bosses.overhead.block.GeneratorPylonBlock;
import net.teamaof.skylorebosses.bosses.overhead.block.PylonCasingBlock;
import net.teamaof.skylorebosses.bosses.overhead.block.YardConsoleBlock;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

/**
 * Yard blocks. Structure blocks are unbreakable (the yard must not be dug out from under a fight); only the
 * yard crates are cover that Overhead's ordnance destroys and the yard reprints.
 */
public final class OverheadBlocks {

    private static BlockBehaviour.Properties steel(MapColor c) {
        return BlockBehaviour.Properties.of().mapColor(c).sound(SoundType.NETHERITE_BLOCK).strength(-1f, 3600000f)
                .noLootTable().pushReaction(PushReaction.BLOCK);
    }

    public static final RegistrySupplier<Block> YARD_PLATING = SBRegistries.BLOCKS.register("yard_plating",
            () -> new Block(steel(MapColor.METAL)));
    public static final RegistrySupplier<Block> HAZARD_PLATING = SBRegistries.BLOCKS.register("hazard_plating",
            () -> new Block(steel(MapColor.COLOR_YELLOW)));
    public static final RegistrySupplier<Block> YARD_WALL = SBRegistries.BLOCKS.register("yard_wall",
            () -> new Block(steel(MapColor.COLOR_CYAN)));
    public static final RegistrySupplier<Block> YARD_SHUTTER = SBRegistries.BLOCKS.register("yard_shutter",
            () -> new Block(steel(MapColor.COLOR_ORANGE).lightLevel(s -> 4)));
    /** Cover. Breakable by hand and by Overhead's blasts; reprinted by the yard, drops nothing. */
    public static final RegistrySupplier<Block> YARD_CRATE = SBRegistries.BLOCKS.register("yard_crate",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).sound(SoundType.WOOD)
                    .strength(2.0f, 1.0f).noLootTable()));
    public static final RegistrySupplier<Block> YARD_CONSOLE = SBRegistries.BLOCKS.register("yard_console",
            () -> new YardConsoleBlock(steel(MapColor.COLOR_CYAN).lightLevel(s -> 7).noOcclusion()));
    public static final RegistrySupplier<Block> GENERATOR_PYLON = SBRegistries.BLOCKS.register("generator_pylon",
            () -> new GeneratorPylonBlock(steel(MapColor.COLOR_CYAN).lightLevel(s -> 10).noOcclusion()));
    /** Invisible collision column above a pylon core; hits on it count as hits on the pylon. */
    public static final RegistrySupplier<Block> PYLON_CASING = SBRegistries.BLOCKS.register("pylon_casing",
            () -> new PylonCasingBlock(steel(MapColor.METAL).noOcclusion()));

    private OverheadBlocks() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
