package net.teamaof.skylorebosses.bosses.staticdeacon.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.teamaof.skylorebosses.bosses.staticdeacon.block.ConsecratedEndstoneBlock;
import net.teamaof.skylorebosses.bosses.staticdeacon.block.CryptPewBlock;
import net.teamaof.skylorebosses.bosses.staticdeacon.block.SacristyBellBlock;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

/**
 * Undercroft blocks. The shell (walls, subfloor, aisle tiles, pillars, plinth) is unbreakable so the crypt cannot be
 * dug out from under a fight. The consecrated flagstones are the fight: breakable with a pickaxe, dropping nothing.
 * Pews are cover the Deacon's shatter breaks and the crypt replaces.
 */
public final class DeaconBlocks {

    private static BlockBehaviour.Properties shell(MapColor c, SoundType s) {
        return BlockBehaviour.Properties.of().mapColor(c).sound(s).strength(-1f, 3600000f).noLootTable().pushReaction(PushReaction.BLOCK);
    }

    /** Heal substrate. LIT while its flagstone is in communion. Hardness matches vanilla end stone. */
    public static final RegistrySupplier<Block> CONSECRATED_ENDSTONE = SBRegistries.BLOCKS.register("consecrated_endstone",
            () -> new ConsecratedEndstoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).sound(SoundType.STONE)
                    .strength(3.0f, 9.0f).requiresCorrectToolForDrops().noLootTable().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(ConsecratedEndstoneBlock.LIT) ? 6 : 0)));
    /** What is left of a flagstone after it is desecrated. No heal; breakable; drops nothing. */
    public static final RegistrySupplier<Block> DESECRATED_ENDSTONE = SBRegistries.BLOCKS.register("desecrated_endstone",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).sound(SoundType.TUFF)
                    .strength(1.5f, 6.0f).noLootTable()));
    /** The unbreakable altar dais (3x3x2). Always counts as communion ground (the vigil). */
    public static final RegistrySupplier<Block> ALTAR_PLINTH = SBRegistries.BLOCKS.register("altar_plinth",
            () -> new Block(shell(MapColor.SAND, SoundType.DEEPSLATE_TILES).lightLevel(s -> 5)));
    public static final RegistrySupplier<Block> CRYPT_TILE = SBRegistries.BLOCKS.register("crypt_tile",
            () -> new Block(shell(MapColor.DEEPSLATE, SoundType.DEEPSLATE_TILES)));
    public static final RegistrySupplier<Block> CRYPT_SUBFLOOR = SBRegistries.BLOCKS.register("crypt_subfloor",
            () -> new Block(shell(MapColor.DEEPSLATE, SoundType.DEEPSLATE)));
    public static final RegistrySupplier<Block> CRYPT_WALL = SBRegistries.BLOCKS.register("crypt_wall",
            () -> new Block(shell(MapColor.COLOR_PURPLE, SoundType.DEEPSLATE_BRICKS)));
    public static final RegistrySupplier<Block> CRYPT_PILLAR = SBRegistries.BLOCKS.register("crypt_pillar",
            () -> new Block(shell(MapColor.COLOR_PURPLE, SoundType.DEEPSLATE_BRICKS)));
    public static final RegistrySupplier<Block> BERYL_LAMP = SBRegistries.BLOCKS.register("beryl_lamp",
            () -> new Block(shell(MapColor.COLOR_LIGHT_GREEN, SoundType.AMETHYST).lightLevel(s -> 13)));
    /** Lockdown portcullis across the south door. */
    public static final RegistrySupplier<Block> CRYPT_GRATE = SBRegistries.BLOCKS.register("crypt_grate",
            () -> new Block(shell(MapColor.METAL, SoundType.CHAIN).noOcclusion()));
    /** Cover. Breakable by hand and by nave_shatter; replaced by the crypt, drops nothing. */
    public static final RegistrySupplier<Block> CRYPT_PEW = SBRegistries.BLOCKS.register("crypt_pew",
            () -> new CryptPewBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).sound(SoundType.WOOD)
                    .strength(2.0f, 1.0f).noLootTable().noOcclusion()));
    public static final RegistrySupplier<Block> SACRISTY_BELL = SBRegistries.BLOCKS.register("sacristy_bell",
            () -> new SacristyBellBlock(shell(MapColor.GOLD, SoundType.ANVIL).lightLevel(s -> 7).noOcclusion()));

    private DeaconBlocks() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
