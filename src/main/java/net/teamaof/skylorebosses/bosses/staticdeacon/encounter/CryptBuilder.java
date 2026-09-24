package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.teamaof.skylorebosses.bosses.staticdeacon.block.ConsecratedEndstoneBlock;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconBlocks;

/**
 * The mod-placed test undercroft (DESIGN.md §3). The shipped crypt is expected to be a Skylore Islands structure under
 * a Church of Ender; that structure only has to contain the same blocks at the same offsets (see CryptLayout) plus a
 * sacristy_bell, and {@code /skyloredeacon register} adopts it without rebuilding. Placement is idempotent.
 */
public final class CryptBuilder {
    private CryptBuilder() {}

    /** Build the whole crypt: shell, floor, pillars, pews, plinth, lamps, door landing and bell. */
    public static void build(ServerLevel level, BlockPos o) {
        BlockState wall = DeaconBlocks.CRYPT_WALL.get().defaultBlockState();
        BlockState sub = DeaconBlocks.CRYPT_SUBFLOOR.get().defaultBlockState();
        int X = CryptLayout.WALL_X, Z = CryptLayout.WALL_Z, C = CryptLayout.CEILING;
        for (int x = -X; x <= X; x++) {
            for (int z = -Z; z <= Z; z++) {
                boolean edge = Math.abs(x) == X || Math.abs(z) == Z;
                set(level, o.offset(x, -1, z), sub);
                set(level, o.offset(x, C, z), wall);
                for (int y = 0; y < C; y++) {
                    if (edge) set(level, o.offset(x, y, z), wall);
                    else if (y > 0) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        // floor slots
        for (int[] s : CryptLayout.SLOTS) {
            CryptLayout.Kind k = CryptLayout.kind(s[0], s[1]);
            if (k == CryptLayout.Kind.NAVE) continue;   // restoreFloor lays the flagstones
            for (BlockPos p : CryptLayout.slotBlocks(o, s[0], s[1])) set(level, p, DeaconBlocks.CRYPT_TILE.get().defaultBlockState());
        }
        // pillars (floor to ceiling) with a beryl lamp in the ceiling above each
        for (int[] s : CryptLayout.SLOTS) {
            if (CryptLayout.kind(s[0], s[1]) != CryptLayout.Kind.PILLAR) continue;
            BlockPos c = CryptLayout.slotCenter(o, s[0], s[1]);
            for (int y = 1; y < C; y++) set(level, c.above(y), DeaconBlocks.CRYPT_PILLAR.get().defaultBlockState());
        }
        // lamps down the nave and over the aisles
        for (int cz = -CryptLayout.ROWS; cz <= CryptLayout.ROWS; cz += 2) {
            for (int cx : new int[]{-3, 0, 3}) set(level, CryptLayout.slotCenter(o, cx, cz).above(C), DeaconBlocks.BERYL_LAMP.get().defaultBlockState());
        }
        placePlinth(level, o);
        restoreFloor(level, o);
        restoreCover(level, o);
        // door landing outside the south wall + the bell
        for (int x = -2; x <= 2; x++)
            for (int z = Z + 1; z <= Z + 6; z++) {
                set(level, o.offset(x, 0, z), DeaconBlocks.CRYPT_TILE.get().defaultBlockState());
                set(level, o.offset(x, -1, z), sub);
                for (int y = 1; y <= 4; y++) set(level, o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        set(level, CryptLayout.bell(o), DeaconBlocks.SACRISTY_BELL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        openDoor(level, o);
    }

    public static void placePlinth(ServerLevel level, BlockPos o) {
        BlockState p = DeaconBlocks.ALTAR_PLINTH.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 0; y < CryptLayout.PLINTH_HEIGHT; y++) set(level, o.offset(dx, y, dz), p);
    }

    public static boolean plinthIntact(ServerLevel level, BlockPos o) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int y = 0; y < CryptLayout.PLINTH_HEIGHT; y++) if (!level.getBlockState(o.offset(dx, y, dz)).is(DeaconBlocks.ALTAR_PLINTH.get())) return false;
        return true;
    }

    /** Lay every nave flagstone as lit consecrated endstone and sweep anything placed on top of it. */
    public static void restoreFloor(ServerLevel level, BlockPos o) {
        for (int[] s : CryptLayout.SLOTS) {
            if (CryptLayout.kind(s[0], s[1]) == CryptLayout.Kind.NAVE) consecrate(level, o, s[0], s[1], true);
        }
    }

    /** (Re)lay one flagstone and clear the block layer above it (the rite sweeps the floor). */
    public static void consecrate(ServerLevel level, BlockPos o, int cx, int cz, boolean lit) {
        BlockState e = DeaconBlocks.CONSECRATED_ENDSTONE.get().defaultBlockState().setValue(ConsecratedEndstoneBlock.LIT, lit);
        for (BlockPos p : CryptLayout.slotBlocks(o, cx, cz)) {
            set(level, p.below(), DeaconBlocks.CRYPT_SUBFLOOR.get().defaultBlockState());
            set(level, p, e);
            if (!level.getBlockState(p.above()).isAir()) level.destroyBlock(p.above(), false);
        }
    }

    public static void restoreCover(ServerLevel level, BlockPos o) {
        for (BlockPos p : CryptLayout.pewBlocks(o)) set(level, p, pew(o, p));
    }

    /** Stalls face the nave: east aisle faces west and vice versa. */
    public static BlockState pew(BlockPos o, BlockPos p) {
        Direction f = p.getX() > o.getX() ? Direction.WEST : Direction.EAST;
        return DeaconBlocks.CRYPT_PEW.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f);
    }

    public static void closeDoor(ServerLevel level, BlockPos o) { door(level, o, DeaconBlocks.CRYPT_GRATE.get().defaultBlockState()); }

    public static void openDoor(ServerLevel level, BlockPos o) { door(level, o, Blocks.AIR.defaultBlockState()); }

    private static void door(ServerLevel level, BlockPos o, BlockState s) {
        for (int x = -CryptLayout.DOOR_HALF; x <= CryptLayout.DOOR_HALF; x++)
            for (int y = 1; y <= CryptLayout.DOOR_HEIGHT; y++) set(level, o.offset(x, y, CryptLayout.WALL_Z), s);
    }

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p) != s) level.setBlock(p, s, 2);
    }
}
