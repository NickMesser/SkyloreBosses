package net.teamaof.skylorebosses.bosses.matriscalyx.registry;

import net.teamaof.skylorebosses.core.registry.SBRegistries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.FleshMembraneBlock;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.SporeVentBlock;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.SyringeCacheBlock;

public final class MatrisBlocks {

    private static BlockBehaviour.Properties flesh() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).sound(SoundType.MUD).strength(1.5f, 6f);
    }

    private static BlockBehaviour.Properties unbreakable(BlockBehaviour.Properties p) {
        return p.strength(-1f, 3600000f).noLootTable().pushReaction(PushReaction.BLOCK);
    }

    public static final RegistrySupplier<Block> FLESH = SBRegistries.BLOCKS.register("flesh", () -> new Block(flesh()));
    /** Arena cover. Unbreakable so the Bloom's laser cover can't be dug out. */
    public static final RegistrySupplier<Block> HARDENED_FLESH = SBRegistries.BLOCKS.register("hardened_flesh",
            () -> new Block(unbreakable(flesh().mapColor(MapColor.TERRACOTTA_RED))));
    public static final RegistrySupplier<Block> FLESH_MEMBRANE = SBRegistries.BLOCKS.register("flesh_membrane",
            () -> new FleshMembraneBlock(flesh().mapColor(MapColor.TERRACOTTA_PINK).strength(0.8f).sound(SoundType.SLIME_BLOCK).speedFactor(0.7f)));
    public static final RegistrySupplier<Block> GLOW_VEIN = SBRegistries.BLOCKS.register("glow_vein",
            () -> new Block(flesh().mapColor(MapColor.COLOR_ORANGE).lightLevel(s -> 11)));
    public static final RegistrySupplier<Block> HEART_CORE = SBRegistries.BLOCKS.register("heart_core",
            () -> new Block(unbreakable(flesh().mapColor(MapColor.COLOR_PINK).lightLevel(s -> 15))));
    public static final RegistrySupplier<Block> RIB_BONE = SBRegistries.BLOCKS.register("rib_bone",
            () -> new Block(unbreakable(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).sound(SoundType.BONE_BLOCK))));
    public static final RegistrySupplier<Block> RUPTURED_VENT = SBRegistries.BLOCKS.register("ruptured_vent", () -> new Block(flesh()));
    public static final RegistrySupplier<Block> SPORE_VENT = SBRegistries.BLOCKS.register("spore_vent",
            () -> new SporeVentBlock(unbreakable(flesh()).noOcclusion()));
    public static final RegistrySupplier<Block> SYRINGE_CACHE = SBRegistries.BLOCKS.register("syringe_cache",
            () -> new SyringeCacheBlock(unbreakable(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.BONE_BLOCK).lightLevel(s -> 6))));

    private MatrisBlocks() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
