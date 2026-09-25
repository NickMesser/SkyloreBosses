package net.teamaof.skylorebosses.bosses.nullrouter.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.teamaof.skylorebosses.bosses.nullrouter.block.ChannelConsoleBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.block.RequestLampBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.block.RoutingGateBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.block.ServiceTerminalBlock;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

/**
 * Automaton vault blocks. Everything the fight depends on is unbreakable and explosion-proof (hardness -1, blast
 * resistance 3.6M, pistons refused): the shell so the vault cannot be dug out from under a fight, and the consoles
 * because the verb is to set them, never to break them. Nothing here drops anything.
 */
public final class RouterBlocks {

    private static BlockBehaviour.Properties shell(MapColor c, SoundType s) {
        return BlockBehaviour.Properties.of().mapColor(c).sound(s).strength(-1f, 3600000f).noLootTable().pushReaction(PushReaction.BLOCK);
    }

    /** Clean-room raised floor. */
    public static final RegistrySupplier<Block> VAULT_FLOOR = SBRegistries.BLOCKS.register("vault_floor",
            () -> new Block(shell(MapColor.QUARTZ, SoundType.NETHERITE_BLOCK)));
    public static final RegistrySupplier<Block> VAULT_WALL = SBRegistries.BLOCKS.register("vault_wall",
            () -> new Block(shell(MapColor.COLOR_BLACK, SoundType.DEEPSLATE_TILES)));
    public static final RegistrySupplier<Block> VAULT_CEILING = SBRegistries.BLOCKS.register("vault_ceiling",
            () -> new Block(shell(MapColor.COLOR_GRAY, SoundType.NETHERITE_BLOCK)));
    public static final RegistrySupplier<Block> VAULT_LIGHT = SBRegistries.BLOCKS.register("vault_light",
            () -> new Block(shell(MapColor.SNOW, SoundType.GLASS).lightLevel(s -> 15)));
    /** Server-rack columns: hard cover (blocks ghost_lance). */
    public static final RegistrySupplier<Block> VAULT_PILLAR = SBRegistries.BLOCKS.register("vault_pillar",
            () -> new Block(shell(MapColor.COLOR_GRAY, SoundType.NETHERITE_BLOCK).lightLevel(s -> 3)));
    /** The chassis docks here during an ACK window; coolant poured on it wets the chassis. */
    public static final RegistrySupplier<Block> CHASSIS_PAD = SBRegistries.BLOCKS.register("chassis_pad",
            () -> new Block(shell(MapColor.COLOR_YELLOW, SoundType.NETHERITE_BLOCK).lightLevel(s -> 4)));
    /** Bottom of a coolant basin; lit while the vent is open (P3+ ACK windows). */
    public static final RegistrySupplier<Block> COOLANT_VENT = SBRegistries.BLOCKS.register("coolant_vent",
            () -> new CoolantVentBlock(shell(MapColor.COLOR_LIGHT_BLUE, SoundType.NETHERITE_BLOCK)
                    .lightLevel(s -> s.getValue(BlockStateProperties.LIT) ? 9 : 0)));
    public static final RegistrySupplier<Block> CHANNEL_CONSOLE = SBRegistries.BLOCKS.register("channel_console",
            () -> new ChannelConsoleBlock(shell(MapColor.COLOR_CYAN, SoundType.NETHERITE_BLOCK).lightLevel(ChannelConsoleBlock::light)));
    public static final RegistrySupplier<Block> REQUEST_LAMP = SBRegistries.BLOCKS.register("request_lamp",
            () -> new RequestLampBlock(shell(MapColor.COLOR_BLACK, SoundType.GLASS).lightLevel(RequestLampBlock::light)));
    public static final RegistrySupplier<Block> ROUTING_GATE = SBRegistries.BLOCKS.register("routing_gate",
            () -> new RoutingGateBlock(shell(MapColor.COLOR_RED, SoundType.GLASS).noOcclusion().lightLevel(s -> s.getValue(RoutingGateBlock.OPEN) ? 0 : 7)));
    /** Lockdown grate across the south door. */
    public static final RegistrySupplier<Block> VAULT_GRATE = SBRegistries.BLOCKS.register("vault_grate",
            () -> new Block(shell(MapColor.METAL, SoundType.CHAIN).noOcclusion()));
    public static final RegistrySupplier<Block> SERVICE_TERMINAL = SBRegistries.BLOCKS.register("service_terminal",
            () -> new ServiceTerminalBlock(shell(MapColor.COLOR_GRAY, SoundType.NETHERITE_BLOCK).lightLevel(s -> 6)));

    private RouterBlocks() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}

    /** Coolant vent: carries LIT only. */
    public static class CoolantVentBlock extends Block {
        public CoolantVentBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> b) {
            b.add(BlockStateProperties.LIT);
        }
    }
}
