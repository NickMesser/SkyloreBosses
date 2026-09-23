package net.teamaof.skylorebosses.bosses.matriscalyx.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Soft digestive membrane: halves fall damage (she cushions what she means to digest). */
public class FleshMembraneBlock extends Block {
    public FleshMembraneBlock(Properties p) {
        super(p);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float distance) {
        entity.causeFallDamage(distance, 0.5f, level.damageSources().fall());
    }
}
