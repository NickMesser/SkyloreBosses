package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.teamaof.skylorebosses.bosses.nullrouter.block.ChannelConsoleBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.block.Glyph;
import net.teamaof.skylorebosses.bosses.nullrouter.block.RoutingGateBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterBlocks;

/**
 * The mod-placed test vault (DESIGN.md §3). The shipped vault is expected to be a Skylore Islands sky-island structure;
 * that structure only has to contain the same encounter blocks at the same offsets (see VaultLayout) plus a
 * service_terminal, and {@code /skylorenullrouter register} adopts it without rebuilding. Placement is idempotent.
 */
public final class VaultBuilder {
    private VaultBuilder() {}

    /** Build the whole vault: shell, clean-room floor, pad, basins, consoles, lamps, board, pillars, alcoves, door, terminal. */
    public static void build(ServerLevel level, BlockPos o) {
        BlockState wall = RouterBlocks.VAULT_WALL.get().defaultBlockState();
        BlockState floor = RouterBlocks.VAULT_FLOOR.get().defaultBlockState();
        BlockState ceiling = RouterBlocks.VAULT_CEILING.get().defaultBlockState();
        int W = VaultLayout.WALL, C = VaultLayout.CEILING;
        for (int x = -W; x <= W; x++) {
            for (int z = -W; z <= W; z++) {
                boolean edge = Math.abs(x) == W || Math.abs(z) == W;
                set(level, o.offset(x, -1, z), wall);
                set(level, o.offset(x, 0, z), edge ? wall : floor);
                set(level, o.offset(x, C, z), ceiling);
                for (int y = 1; y < C; y++) set(level, o.offset(x, y, z), edge ? wall : Blocks.AIR.defaultBlockState());
            }
        }
        // ceiling lights on a 6-block grid
        for (int x = -12; x <= 12; x += 6) for (int z = -12; z <= 12; z += 6) set(level, o.offset(x, C, z), RouterBlocks.VAULT_LIGHT.get().defaultBlockState());
        // pad and coolant basins (1-deep recesses with a vent underneath)
        for (BlockPos p : VaultLayout.padBlocks(o)) set(level, p, RouterBlocks.CHASSIS_PAD.get().defaultBlockState());
        for (int i = 0; i < VaultLayout.BASINS.length; i++) {
            BlockPos b = VaultLayout.basin(o, i);
            set(level, b, Blocks.AIR.defaultBlockState());
            set(level, b.below(), RouterBlocks.COOLANT_VENT.get().defaultBlockState());
        }
        // server-rack pillars, floor to ceiling
        for (BlockPos p : VaultLayout.pillarColumns(o))
            for (int y = 1; y < C; y++) set(level, p.above(y), RouterBlocks.VAULT_PILLAR.get().defaultBlockState());
        // consoles with a backing column carrying the lamp stack
        placeConsoles(level, o);
        for (int i = 0; i < 3; i++) {
            BlockPos back = VaultLayout.consoleBack(o, i);
            for (int y = 0; y <= 4; y++) set(level, back.above(y), RouterBlocks.VAULT_PILLAR.get().defaultBlockState());
            set(level, VaultLayout.headLamp(o, i), RouterBlocks.REQUEST_LAMP.get().defaultBlockState());
            set(level, VaultLayout.nextLamp(o, i), RouterBlocks.REQUEST_LAMP.get().defaultBlockState());
            set(level, VaultLayout.boardHead(o, i), RouterBlocks.REQUEST_LAMP.get().defaultBlockState());
            set(level, VaultLayout.boardNext(o, i), RouterBlocks.REQUEST_LAMP.get().defaultBlockState());
        }
        // board frame on the north wall
        for (int x = -2; x <= 2; x++) {
            set(level, o.offset(x, 7, -VaultLayout.HALF), RouterBlocks.VAULT_PILLAR.get().defaultBlockState());
            set(level, o.offset(x, 10, -VaultLayout.HALF), RouterBlocks.VAULT_PILLAR.get().defaultBlockState());
        }
        for (int y = 8; y <= 9; y++) {
            set(level, o.offset(-2, y, -VaultLayout.HALF), RouterBlocks.VAULT_PILLAR.get().defaultBlockState());
            set(level, o.offset(2, y, -VaultLayout.HALF), RouterBlocks.VAULT_PILLAR.get().defaultBlockState());
        }
        // service alcoves: gates open
        for (int a = 0; a < VaultLayout.ALCOVES.length; a++) gate(level, o, a, true);
        // door landing outside the south wall + the service terminal
        for (int x = -2; x <= 2; x++)
            for (int z = W + 1; z <= W + 6; z++) {
                set(level, o.offset(x, 0, z), floor);
                set(level, o.offset(x, -1, z), wall);
                for (int y = 1; y <= 4; y++) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        set(level, VaultLayout.terminal(o), RouterBlocks.SERVICE_TERMINAL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        openDoor(level, o);
    }

    /** Consoles in their fresh state (A circle, B triangle, C square), facing the pad. */
    public static void placeConsoles(ServerLevel level, BlockPos o) {
        for (int i = 0; i < 3; i++) {
            BlockState s = RouterBlocks.CHANNEL_CONSOLE.get().defaultBlockState().setValue(ChannelConsoleBlock.FACING, VaultLayout.CONSOLE_FACING[i])
                    .setValue(ChannelConsoleBlock.GLYPH, Glyph.of(i));
            set(level, VaultLayout.console(o, i), s);
        }
    }

    /** Open (walk-through) or close (hold) service alcove a's gate. */
    public static void gate(ServerLevel level, BlockPos o, int a, boolean open) {
        BlockState s = RouterBlocks.ROUTING_GATE.get().defaultBlockState().setValue(RoutingGateBlock.OPEN, open);
        for (BlockPos p : VaultLayout.alcoveGate(o, a)) {
            BlockState cur = level.getBlockState(p);
            if (cur.isAir() || cur.is(RouterBlocks.ROUTING_GATE.get()) || cur.canBeReplaced()) set(level, p, s);
        }
    }

    /** Anything the fight needs that went missing (commands, other mods): consoles, pad, vents. Called every 20 ticks. */
    static void verify(ServerLevel level, BlockPos o) {
        for (int i = 0; i < 3; i++) {
            BlockPos c = VaultLayout.console(o, i);
            if (level.isLoaded(c) && !level.getBlockState(c).is(RouterBlocks.CHANNEL_CONSOLE.get()))
                set(level, c, RouterBlocks.CHANNEL_CONSOLE.get().defaultBlockState().setValue(ChannelConsoleBlock.FACING, VaultLayout.CONSOLE_FACING[i]));
        }
        for (BlockPos p : VaultLayout.padBlocks(o))
            if (level.isLoaded(p) && !level.getBlockState(p).is(RouterBlocks.CHASSIS_PAD.get())) set(level, p, RouterBlocks.CHASSIS_PAD.get().defaultBlockState());
        for (int i = 0; i < VaultLayout.BASINS.length; i++) {
            BlockPos v = VaultLayout.basin(o, i).below();
            if (level.isLoaded(v) && !level.getBlockState(v).is(RouterBlocks.COOLANT_VENT.get()))
                set(level, v, RouterBlocks.COOLANT_VENT.get().defaultBlockState().setValue(BlockStateProperties.LIT, false));
        }
    }

    public static void closeDoor(ServerLevel level, BlockPos o) { door(level, o, RouterBlocks.VAULT_GRATE.get().defaultBlockState()); }

    public static void openDoor(ServerLevel level, BlockPos o) { door(level, o, Blocks.AIR.defaultBlockState()); }

    private static void door(ServerLevel level, BlockPos o, BlockState s) {
        for (int x = -VaultLayout.DOOR_HALF; x <= VaultLayout.DOOR_HALF; x++)
            for (int y = 1; y <= VaultLayout.DOOR_HEIGHT; y++) set(level, o.offset(x, y, VaultLayout.WALL), s);
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p) != s) level.setBlock(p, s, 2);
    }
}
