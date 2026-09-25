package net.teamaof.skylorebosses.bosses.amanita.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.teamaof.skylorebosses.bosses.amanita.block.HollowBrazierBlock;
import net.teamaof.skylorebosses.bosses.amanita.block.HollowKnockerBlock;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

/**
 * Hollow blocks. The shell (wall, loam floor, ceiling, columns, braziers, mouth membrane, knocker) is unbreakable and
 * emits no light: the hollow ships dark on purpose. Gill shelves are the soft cover: breakable, non-emitting, regrown by
 * the hollow. Braziers are the only light the hollow provides, and they ship unlit.
 */
public final class AmanitaBlocks {

    private static BlockBehaviour.Properties shell(MapColor c, SoundType s) {
        return BlockBehaviour.Properties.of().mapColor(c).sound(s).strength(-1f, 3600000f).noLootTable().pushReaction(PushReaction.BLOCK);
    }

    public static final RegistrySupplier<Block> HOLLOW_WALL = SBRegistries.BLOCKS.register("hollow_wall",
            () -> new Block(shell(MapColor.COLOR_BLACK, SoundType.NETHER_WART)));
    public static final RegistrySupplier<Block> HOLLOW_LOAM = SBRegistries.BLOCKS.register("hollow_loam",
            () -> new Block(shell(MapColor.COLOR_PURPLE, SoundType.ROOTED_DIRT)));
    public static final RegistrySupplier<Block> HOLLOW_CEILING = SBRegistries.BLOCKS.register("hollow_ceiling",
            () -> new Block(shell(MapColor.COLOR_BLACK, SoundType.SHROOMLIGHT)));
    public static final RegistrySupplier<Block> HOLLOW_COLUMN = SBRegistries.BLOCKS.register("hollow_column",
            () -> new RotatedPillarBlock(shell(MapColor.TERRACOTTA_WHITE, SoundType.STEM)));
    /** Soft cover. Breakable by hand and by bloom_slam; regrown by the hollow, drops nothing. */
    public static final RegistrySupplier<Block> GILL_SHELF = SBRegistries.BLOCKS.register("gill_shelf",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PURPLE).sound(SoundType.FUNGUS)
                    .strength(1.5f, 2.0f).noLootTable()));
    public static final RegistrySupplier<Block> HOLLOW_BRAZIER = SBRegistries.BLOCKS.register("hollow_brazier",
            () -> new HollowBrazierBlock(shell(MapColor.METAL, SoundType.LANTERN).noOcclusion()
                    .lightLevel(s -> s.getValue(HollowBrazierBlock.LIT) ? 15 : 0)));
    /** Lockdown seal across the south mouth. */
    public static final RegistrySupplier<Block> HOLLOW_MEMBRANE = SBRegistries.BLOCKS.register("hollow_membrane",
            () -> new Block(shell(MapColor.COLOR_PURPLE, SoundType.SLIME_BLOCK).noOcclusion()));
    public static final RegistrySupplier<Block> HOLLOW_KNOCKER = SBRegistries.BLOCKS.register("hollow_knocker",
            () -> new HollowKnockerBlock(shell(MapColor.TERRACOTTA_WHITE, SoundType.FUNGUS).noOcclusion()));

    private AmanitaBlocks() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
