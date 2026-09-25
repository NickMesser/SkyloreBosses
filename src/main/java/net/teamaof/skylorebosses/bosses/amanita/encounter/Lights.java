package net.teamaof.skylorebosses.bosses.amanita.encounter;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaConfig;
import net.teamaof.skylorebosses.bosses.amanita.block.HollowBrazierBlock;

/**
 * The light-gate and snuff system card (DESIGN.md §6) in code. Everything reads the server's light engine, so client
 * dynamic-light mods, held torches, Glowing, spectral arrows and glow item frames never count: only real block light.
 */
public final class Lights {
    /** Light-emitting blocks a snuff never touches (the pack can add its own). */
    public static final TagKey<Block> SNUFF_IMMUNE = TagKey.create(Registries.BLOCK, SkyloreBosses.id("amanita/snuff_immune"));

    private Lights() {}

    /** Block light at a position (plus raw sky light if the hollow is configured to count it). */
    public static int at(ServerLevel level, BlockPos p) {
        int l = level.getBrightness(LightLayer.BLOCK, p);
        if (AmanitaConfig.COUNT_SKY_LIGHT.get()) l = Math.max(l, level.getBrightness(LightLayer.SKY, p) - level.getSkyDarken());
        return Math.max(0, Math.min(15, l));
    }

    /** Light at an entity: the brighter of its feet block and its chest block. */
    public static int sample(ServerLevel level, Entity e) {
        BlockPos feet = BlockPos.containing(e.getX(), e.getY() + 0.1, e.getZ());
        BlockPos chest = BlockPos.containing(e.getX(), e.getY() + Math.min(1.6, e.getBbHeight() * 0.5), e.getZ());
        return Math.max(at(level, feet), at(level, chest));
    }

    public static int emission(ServerLevel level, BlockPos p, BlockState s) {
        return s.isAir() ? 0 : s.getLightEmission(level, p);
    }

    /** A block the snuff acts on: emits light, not immune, and either has LIT or can be broken. */
    public static boolean snuffable(ServerLevel level, BlockPos p, BlockState s) {
        if (emission(level, p, s) <= 0 || s.is(SNUFF_IMMUNE)) return false;
        if (s.hasProperty(BlockStateProperties.LIT)) return s.getValue(BlockStateProperties.LIT);
        if (s.getBlock() instanceof LiquidBlock) return true;
        return s.getDestroySpeed(level, p) >= 0;
    }

    /**
     * Put out one light: LIT blocks (braziers, campfires, candles) go dark, fluids (lava) are drunk, anything else breaks
     * (with its drop if {@code drops}). @return true if the block changed
     */
    public static boolean snuffOne(ServerLevel level, BlockPos p, boolean drops) {
        BlockState s = level.getBlockState(p);
        if (!snuffable(level, p, s)) return false;
        if (s.hasProperty(BlockStateProperties.LIT)) {
            level.setBlock(p, s.setValue(BlockStateProperties.LIT, false), 3);
        } else if (s.getBlock() instanceof LiquidBlock) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        } else {
            level.destroyBlock(p, drops);
        }
        return true;
    }

    /** Every light source (emission >= sourceMinEmission) inside the hollow interior. */
    public static List<BlockPos> census(ServerLevel level, BlockPos o) {
        List<BlockPos> out = new ArrayList<>();
        int min = AmanitaConfig.SOURCE_MIN_EMISSION.get();
        for (BlockPos p : BlockPos.betweenClosed(HollowLayout.interiorMin(o), HollowLayout.interiorMax(o))) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;
            if (emission(level, p, s) >= min && !s.is(SNUFF_IMMUNE)) out.add(p.immutable());
        }
        return out;
    }

    /** Every snuffable block inside the interior within {@code radius} of {@code c} (radius < 0: the whole interior). */
    public static List<BlockPos> inRange(ServerLevel level, BlockPos o, net.minecraft.world.phys.Vec3 c, double radius) {
        List<BlockPos> out = new ArrayList<>();
        double r2 = radius * radius;
        for (BlockPos p : BlockPos.betweenClosed(HollowLayout.interiorMin(o), HollowLayout.interiorMax(o))) {
            if (radius >= 0 && p.getCenter().distanceToSqr(c) > r2) continue;
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && snuffable(level, p, s)) out.add(p.immutable());
        }
        return out;
    }

    /** A held item that would emit light as a block (torch, lantern, glowstone...) or an igniter: the player "carries light". */
    public static boolean isLightItem(ItemStack st) {
        if (st.isEmpty()) return false;
        if (st.is(HollowBrazierBlock.IGNITERS)) return true;
        return st.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission() >= AmanitaConfig.SOURCE_MIN_EMISSION.get();
    }

    public static boolean carriesLight(Player p) {
        return isLightItem(p.getMainHandItem()) || isLightItem(p.getOffhandItem());
    }

    /**
     * Is this a floor-standing light (standing on the loam, not hung from a wall or a ceiling), which bloom_slam knocks
     * over? Wall torches and hanging lanterns ride the shockwave out.
     */
    public static boolean floorLight(ServerLevel level, BlockPos p, BlockPos o) {
        if (p.getY() != o.getY() + 1) return false;
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock
                || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("wall")) return false;
        return !(s.hasProperty(BlockStateProperties.HANGING) && s.getValue(BlockStateProperties.HANGING));
    }
}
