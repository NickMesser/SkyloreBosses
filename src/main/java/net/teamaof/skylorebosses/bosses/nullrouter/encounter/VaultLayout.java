package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Automaton vault geometry (DESIGN.md §3). Everything is relative to the vault origin: the centre block of the floor
 * layer, directly under the chassis pad. Players stand at origin.y + 1. North is -Z; the door and the service
 * terminal are south (+Z). Channels A, B, C are the west, north and east consoles, read left to right from the door.
 */
public final class VaultLayout {
    /** Interior floor spans |x|, |z| <= HALF; walls at +-WALL. */
    public static final int HALF = 15, WALL = 16;
    /** Ceiling block layer at origin.y + CEILING; air from +1 to CEILING - 1. */
    public static final int CEILING = 13;
    /** The chassis pad: (2 * PAD + 1)^2 blocks of chassis_pad in the floor layer. */
    public static final int PAD = 2;
    /** Chassis feet height above the floor surface while a ghost (hovering) and while docked (solid). */
    public static final double HOVER = 4.0, DOCK = 0.0;
    /** South door: |x| <= DOOR_HALF, y 1..DOOR_HEIGHT. */
    public static final int DOOR_HALF = 1, DOOR_HEIGHT = 3;
    /** Service alcove gates are this tall. */
    public static final int GATE_HEIGHT = 4;

    /** Console positions (block at floor + 1) and the direction each one faces (toward the pad). */
    public static final int[][] CONSOLES = {{-10, 0}, {0, -10}, {10, 0}};
    public static final Direction[] CONSOLE_FACING = {Direction.EAST, Direction.SOUTH, Direction.WEST};
    public static final String[] CHANNEL = {"A", "B", "C"};
    /** Coolant basins: 1x1 recesses in the floor layer with a vent underneath (P3+ leak pads). */
    public static final int[][] BASINS = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
    /** 2x2 server-rack pillars, floor to ceiling: hard cover for ghost_lance. Lower corner of each. */
    public static final int[][] PILLARS = {{-8, -8}, {6, -8}, {-8, 6}, {6, 6}};
    /** Service alcoves (misroute destinations) in the south corners, out to the walls: interior x range, z range. */
    public static final int[][] ALCOVES = {{-15, -12, 12, 15}, {12, 15, 12, 15}};
    /** Wall ports that retry_storm spawns packets from. */
    public static final int[][] PORTS = {{-13, -6}, {13, -6}, {-13, 5}, {13, 5}};

    private VaultLayout() {}

    public static double floorY(BlockPos o) { return o.getY() + 1; }

    public static BlockPos console(BlockPos o, int i) { return o.offset(CONSOLES[i][0], 1, CONSOLES[i][1]); }

    /** Where a player (or a retry) stands to work console i. */
    public static Vec3 consoleStand(BlockPos o, int i) {
        Direction f = CONSOLE_FACING[i];
        return Vec3.atBottomCenterOf(console(o, i).relative(f));
    }

    /** Head-request lamp over console i; the decoy lamp is one above it. */
    public static BlockPos headLamp(BlockPos o, int i) { return console(o, i).above(2); }

    public static BlockPos nextLamp(BlockPos o, int i) { return console(o, i).above(3); }

    /** Support column behind each console (carries the lamps visually; unbreakable). */
    public static BlockPos consoleBack(BlockPos o, int i) { return console(o, i).relative(CONSOLE_FACING[i].getOpposite()); }

    /** The request board on the north wall's inner face: channel i at x = i - 1, head row at y + 8, decoy row at y + 9. */
    public static BlockPos boardHead(BlockPos o, int i) { return o.offset(i - 1, 8, -HALF); }

    public static BlockPos boardNext(BlockPos o, int i) { return o.offset(i - 1, 9, -HALF); }

    public static List<BlockPos> padBlocks(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = -PAD; x <= PAD; x++) for (int z = -PAD; z <= PAD; z++) out.add(o.offset(x, 0, z));
        return out;
    }

    public static BlockPos basin(BlockPos o, int i) { return o.offset(BASINS[i][0], 0, BASINS[i][1]); }

    /** Chassis position (feet, centre of the pad) at a height above the floor surface. */
    public static Vec3 chassisAt(BlockPos o, double height) { return Vec3.atBottomCenterOf(o).add(0, 1 + height, 0); }

    public static List<BlockPos> pillarColumns(BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] p : PILLARS)
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) out.add(o.offset(p[0] + dx, 0, p[1] + dz));
        return out;
    }

    /** Standing spot inside alcove a. */
    public static Vec3 alcoveSpot(BlockPos o, int a) {
        int[] r = ALCOVES[a];
        return new Vec3(o.getX() + (r[0] + r[1]) / 2.0 + 0.5, floorY(o), o.getZ() + (r[2] + r[3]) / 2.0 + 0.5);
    }

    /** Gate blocks of alcove a: an L across its two open sides, GATE_HEIGHT tall. */
    public static List<BlockPos> alcoveGate(BlockPos o, int a) {
        int[] r = ALCOVES[a];
        List<BlockPos> out = new ArrayList<>();
        int gx = r[0] < 0 ? r[1] + 1 : r[0] - 1;   // the side facing the interior
        int gz = r[2] - 1;
        for (int y = 1; y <= GATE_HEIGHT; y++) {
            for (int z = r[2]; z <= r[3]; z++) out.add(o.offset(gx, y, z));
            for (int x = Math.min(r[0], gx); x <= Math.max(r[1], gx); x++) out.add(o.offset(x, y, gz));
        }
        return out;
    }

    public static AABB alcoveBox(BlockPos o, int a) {
        int[] r = ALCOVES[a];
        return new AABB(o.getX() + r[0], o.getY() + 1, o.getZ() + r[2], o.getX() + r[1] + 1, o.getY() + 1 + GATE_HEIGHT, o.getZ() + r[3] + 1);
    }

    public static Vec3 port(BlockPos o, int i) { return Vec3.atBottomCenterOf(o.offset(PORTS[i][0], 1, PORTS[i][1])); }

    public static BlockPos entryPad(BlockPos o) { return o.offset(0, 1, 12); }

    public static BlockPos terminal(BlockPos o) { return o.offset(0, 1, WALL + 4); }

    /** Players inside this box are participants. */
    public static AABB volume(BlockPos o) {
        return new AABB(o.getX() - WALL, o.getY() - 4, o.getZ() - WALL, o.getX() + WALL + 1, o.getY() + CEILING + 3, o.getZ() + WALL + 7);
    }

    /** Interior air: the fight's own space (retries, chassis, water scan). */
    public static AABB interior(BlockPos o) {
        return new AABB(o.getX() - HALF, o.getY(), o.getZ() - HALF, o.getX() + HALF + 1, o.getY() + CEILING, o.getZ() + HALF + 1);
    }

    /** Walking in here wakes a dormant vault (the rows by the door do not). */
    public static AABB trigger(BlockPos o) {
        return new AABB(o.getX() - HALF, o.getY() + 1, o.getZ() - HALF, o.getX() + HALF + 1, o.getY() + CEILING, o.getZ() + 10);
    }

    /** Below the vault, outside the participant volume: anyone here during a fight fell out and is put back. */
    public static AABB fallZone(BlockPos o) {
        return new AABB(o.getX() - WALL - 6, o.getY() - 128, o.getZ() - WALL - 6, o.getX() + WALL + 7, o.getY() - 3, o.getZ() + WALL + 9);
    }

    /** Horizontal distance from the pad centre. */
    public static double padDistance(BlockPos o, Vec3 p) {
        double dx = p.x - (o.getX() + 0.5), dz = p.z - (o.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }
}
