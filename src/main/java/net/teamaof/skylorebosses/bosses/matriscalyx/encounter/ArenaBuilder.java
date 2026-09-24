package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisBlocks;

/**
 * Authored flesh terrain (not wild noise): seven islands, rib arches for cover, glow-vein landmarks that run
 * back to the heart, lobe pillars (laser cover), and optionally the rib dome + membrane floor that make the
 * whole arena read as her body. Placement is queued and spread over ticks.
 */
public final class ArenaBuilder {
    private static final byte AIR = 0, FLESH = 1, HARD = 2, MEMBRANE = 3, VEIN = 4, CORE = 5, RIB = 6, CACHE = 7, VENT = 8;
    /** Remove a previously placed rib in the arm's volume. Not a palette entry. */
    private static final byte CARVE_RIB = 9;
    private final BlockPos anchorPos;
    private final LongArrayList positions = new LongArrayList();
    private final ByteArrayList kinds = new ByteArrayList();
    private final List<BlockPos> vents = new ArrayList<>();
    private int cursor;
    private BlockState[] palette;

    public ArenaBuilder(BlockPos origin, boolean dome) {
        anchorPos = ArenaLayout.anchor(origin);
        // heart island
        island(origin, ArenaLayout.HEART_RADIUS, 22, 1.0, 7L);
        heartMass(origin);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + 60 * i);
            pillar(origin.offset((int) (Math.cos(a) * 22), 1, (int) (Math.sin(a) * 22)), 3, 18);
            BlockPos v = origin.offset((int) Math.round(Math.cos(Math.toRadians(60 * i)) * 30), 1, (int) Math.round(Math.sin(Math.toRadians(60 * i)) * 30));
            vent(v);
        }
        arch(origin, 36, 16, 0.0);
        arch(origin, 36, 16, Math.PI / 2);
        anchorPlatform(anchorPos);

        for (ArmType t : ArmType.values()) {
            BlockPos c = ArenaLayout.satellite(origin, t.slot);
            int r = ArenaLayout.satelliteRadius(t.slot);
            double squash = t == ArmType.CHARGING ? 0.45 : 1.0;
            island(c, r, 12 + r / 3, squash, 31L * (t.slot + 1));
            double facing = Math.atan2(origin.getZ() - c.getZ(), origin.getX() - c.getX());
            int clearR = clearRadius(t);
            int clearH = clearHeight(t);
            if (t != ArmType.CHARGING) {
                // Crown sits just above the mesh. Bone still inside the body cylinder is omitted.
                int peak = clearH + 3;
                arch(c, (int) (r * 0.7), peak, facing + Math.PI / 2, c, clearR, clearH);
                arch(c, (int) (r * 0.55), Math.max(peak - 4, clearH), facing, c, clearR, clearH);
            } else {
                // Lane runs along X. A center arch would cross the crawler, so the ribs stand on the flanks.
                int flank = 8;
                arch(c.offset(0, 0, flank), 12, 8, 0.0, c, clearR, clearH);
                arch(c.offset(0, 0, -flank), 12, 8, 0.0, c, clearR, clearH);
            }
            carveRibs(c, clearR, clearH);
            vent(c.offset(t == ArmType.CHARGING ? 22 : 8, 1, t == ArmType.CHARGING ? 0 : 8));
            // landmark vein from the satellite's underside back to the heart
            line(c.below(8), origin.below(18), VEIN, 1);
        }
        if (dome) dome(origin);
    }

    public List<BlockPos> vents() { return vents; }
    public boolean done() { return cursor >= positions.size(); }
    public int total() { return positions.size(); }
    public int placed() { return cursor; }

    /** Floor under the return point, placed immediately so an arrival does not fall while the dome is still queued. */
    public void placeAnchor(ServerLevel level) {
        ensurePalette();
        BlockPos a = anchorPos;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            byte k = Math.abs(dx) == 3 || Math.abs(dz) == 3 ? RIB : FLESH;
            level.setBlock(a.offset(dx, -1, dz), palette[k], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (int dy = 0; dy < 6; dy++) level.setBlock(a.offset(0, dy, 0), palette[AIR], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(a.offset(2, 0, 2), palette[CACHE], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private void ensurePalette() {
        if (palette != null) return;
        palette = new BlockState[]{Blocks.AIR.defaultBlockState(), MatrisBlocks.FLESH.get().defaultBlockState(),
                MatrisBlocks.HARDENED_FLESH.get().defaultBlockState(), MatrisBlocks.FLESH_MEMBRANE.get().defaultBlockState(),
                MatrisBlocks.GLOW_VEIN.get().defaultBlockState(), MatrisBlocks.HEART_CORE.get().defaultBlockState(),
                MatrisBlocks.RIB_BONE.get().defaultBlockState(), MatrisBlocks.SYRINGE_CACHE.get().defaultBlockState(),
                MatrisBlocks.SPORE_VENT.get().defaultBlockState()};
    }

    /** Place up to {@code budget} blocks. */
    public void step(ServerLevel level, int budget) {
        ensurePalette();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int end = Math.min(positions.size(), cursor + budget);
        for (; cursor < end; cursor++) {
            p.set(positions.getLong(cursor));
            if (!level.isInWorldBounds(p)) continue;
            byte kind = kinds.getByte(cursor);
            if (kind == CARVE_RIB) {
                if (level.getBlockState(p).is(MatrisBlocks.RIB_BONE.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            } else {
                level.setBlock(p, palette[kind], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    private void put(BlockPos p, byte kind) {
        positions.add(p.asLong());
        kinds.add(kind);
    }

    private static double noise(long seed, double x, double z) {
        return Math.sin(x * 0.21 + seed) * 0.5 + Math.sin(z * 0.17 + seed * 1.7) * 0.5 + Math.sin((x + z) * 0.09 + seed * 0.3) * 0.4;
    }

    /** Flesh island: bumpy membrane top, unbreakable body, stalactite underside laced with glow veins. */
    private void island(BlockPos c, int r, int depth, double zSquash, long seed) {
        int rz = (int) Math.ceil(r * zSquash);
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -rz - 2; dz <= rz + 2; dz++) {
                double nx = dx / (double) r, nz = dz / (double) (r * zSquash);
                double ang = Math.atan2(nz, nx);
                double edge = 0.88 + 0.12 * Math.sin(ang * 5 + seed) * Math.cos(ang * 3 - seed);
                double d = Math.sqrt(nx * nx + nz * nz) / edge;
                if (d > 1) continue;
                int top = (int) Math.round(noise(seed, dx, dz) * 1.2 * (1 - d));
                int thick = 1 + (int) Math.round(Math.pow(1 - d * d, 0.7) * depth * (0.8 + 0.2 * noise(seed + 5, dz, dx)));
                for (int y = top - thick; y <= top; y++) {
                    byte k;
                    if (y == top) k = d > 0.82 ? MEMBRANE : FLESH;
                    else if (y == top - thick) k = ((dx * 31 + dz * 17) & 7) == 0 ? VEIN : FLESH;
                    else k = HARD;
                    put(c.offset(dx, y, dz), k);
                }
            }
        }
    }

    private void heartMass(BlockPos o) {
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = 0; dy <= 9; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.sqrt(dx * dx + (dy - 4.5) * (dy - 4.5) * 1.3 + dz * dz);
                    if (d <= 6) put(o.offset(dx, dy + 1, dz), d > 5 ? FLESH : CORE);
                }
    }

    private void pillar(BlockPos base, int r, int h) {
        for (int y = 0; y < h; y++) {
            double rr = r * (1 - y / (double) (h * 1.6));
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (dx * dx + dz * dz <= rr * rr) put(base.offset(dx, y, dz), (y % 6 == 5) ? RIB : HARD);
        }
    }

    /** Rib arch: a semicircle of bone 2x2 thick, spanning 2*half along the given yaw. */
    private void arch(BlockPos c, int half, int height, double yaw) {
        arch(c, half, height, yaw, c, 0, 0);
    }

    /**
     * @param avoid island center the appendage occupies
     * @param clearRadius horizontal radius of the model cylinder; 0 disables the cut
     * @param clearHeight blocks below this, inside the radius, are not bone
     */
    private void arch(BlockPos c, int half, int height, double yaw, BlockPos avoid, int clearRadius, int clearHeight) {
        double cx = Math.cos(yaw), cz = Math.sin(yaw);
        int r2 = clearRadius * clearRadius;
        for (double t = 0; t <= Math.PI; t += 0.6 / Math.max(half, height)) {
            double along = Math.cos(t) * half;
            int y = (int) Math.round(Math.sin(t) * height);
            int x = (int) Math.round(cx * along), z = (int) Math.round(cz * along);
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
                BlockPos at = c.offset(x + a, y + 1, z + b);
                if (clearRadius > 0 && at.getY() - avoid.getY() < clearHeight) {
                    int dx = at.getX() - avoid.getX(), dz = at.getZ() - avoid.getZ();
                    if (dx * dx + dz * dz < r2) continue;
                }
                put(at, RIB);
            }
        }
    }

    /** Drop rib bone already built through an appendage. Flesh on the island surface is left in place. */
    private void carveRibs(BlockPos center, int radius, int height) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz >= r2) continue;
                for (int y = 1; y < height; y++) put(center.offset(dx, y, dz), (byte) CARVE_RIB);
            }
        }
    }

    /** Unscaled Blockbench visible bounds, times {@link ArmType#renderScale}, plus a one-block margin. */
    private static int clearRadius(ArmType t) {
        float width = switch (t) {
            case NERVE -> 7f;
            case GRASPING -> 6f;
            case SPITTING, SLAM -> 7f;
            case MOUTH -> 7f;
            case CHARGING -> 10f;
        };
        return Math.round(width * t.renderScale / 2f) + 1;
    }

    private static int clearHeight(ArmType t) {
        float height = switch (t) {
            case NERVE -> 10f;
            case GRASPING -> 12f;
            case SPITTING, SLAM -> 9f;
            case MOUTH -> 10f;
            case CHARGING -> 3.5f;
        };
        return Math.round(height * t.renderScale) + 1;
    }

    private void anchorPlatform(BlockPos a) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) put(a.offset(dx, -1, dz), Math.abs(dx) == 3 || Math.abs(dz) == 3 ? RIB : FLESH);
        for (int dy = 0; dy < 6; dy++) put(a.offset(0, dy, 0), AIR);
        put(a.offset(2, 0, 2), CACHE);
    }

    private void vent(BlockPos p) {
        put(p.below(), HARD);
        put(p, VENT);
        for (int dy = 1; dy <= 2; dy++) put(p.above(dy), AIR);
        vents.add(p.immutable());
    }

    private void line(BlockPos a, BlockPos b, byte kind, int thickness) {
        double len = Math.sqrt(a.distSqr(b));
        for (double t = 0; t <= len; t += 0.7) {
            double f = t / len;
            BlockPos p = BlockPos.containing(Mth.lerp(f, a.getX(), b.getX()), Mth.lerp(f, a.getY(), b.getY()) - Math.sin(f * Math.PI) * 10,
                    Mth.lerp(f, a.getZ(), b.getZ()));
            for (int d = 0; d < thickness; d++) put(p.offset(0, -d, 0), kind);
        }
    }

    /** Her body: 6 rib hoops over the arena, an equator spine, and a lattice of digestive membrane below. */
    private void dome(BlockPos o) {
        int R = 200;
        for (int k = 0; k < 6; k++) {
            double yaw = Math.PI * k / 6;
            double cx = Math.cos(yaw), cz = Math.sin(yaw);
            for (double phi = -0.35; phi <= Math.PI + 0.35; phi += 0.5 / R) {
                double h = Math.cos(phi) * R;
                int y = (int) Math.round(Math.sin(phi) * R * 0.55) - 20;
                BlockPos p = o.offset((int) Math.round(cx * h), y, (int) Math.round(cz * h));
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) put(p.offset(a, b, 0), RIB);
            }
        }
        for (double t = 0; t < Math.PI * 2; t += 0.5 / R) {
            BlockPos p = o.offset((int) Math.round(Math.cos(t) * R), -20, (int) Math.round(Math.sin(t) * R));
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) put(p.offset(a, b, 0), FLESH);
        }
        int floor = -90;
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (x * x + z * z > R * R) continue;
                if (Math.floorMod(x, 9) == 0 || Math.floorMod(z, 9) == 0) put(o.offset(x, floor + (int) Math.round(Math.sin(x * 0.05) * Math.cos(z * 0.05) * 3), z),
                        ((x ^ z) & 15) == 0 ? VEIN : MEMBRANE);
            }
        }
    }
}
