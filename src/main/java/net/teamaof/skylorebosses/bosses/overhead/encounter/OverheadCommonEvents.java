package net.teamaof.skylorebosses.bosses.overhead.encounter;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.teamaof.skylorebosses.bosses.overhead.OverheadConfig;
import net.teamaof.skylorebosses.bosses.overhead.block.PylonCasingBlock;
import net.teamaof.skylorebosses.bosses.overhead.entity.Ordnance;
import net.teamaof.skylorebosses.bosses.overhead.entity.OverheadEntity;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlocks;

/**
 * Hooks: yard ticking, death counting, and the two ways a pylon is hurt at range (DESIGN.md §6):
 * a player's projectile striking a pylon block, and a player-caused explosion near a pylon.
 */
public final class OverheadCommonEvents {
    private OverheadCommonEvents() {}

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(OverheadYards::tickLevel);
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && p.level() instanceof ServerLevel sl) {
                for (Yard y : OverheadYards.get(sl).all()) y.onPlayerDeath(p);
            }
            return EventResult.pass();
        });
        NeoForge.EVENT_BUS.addListener(OverheadCommonEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(OverheadCommonEvents::onExplosion);
    }

    private static void onProjectileImpact(ProjectileImpactEvent e) {
        if (!(e.getProjectile().level() instanceof ServerLevel sl) || e.getProjectile() instanceof Ordnance) return;
        if (e.getRayTraceResult().getType() != HitResult.Type.BLOCK || !(e.getProjectile().getOwner() instanceof Player p)) return;
        BlockPos hit = ((BlockHitResult) e.getRayTraceResult()).getBlockPos();
        BlockPos core = sl.getBlockState(hit).is(OverheadBlocks.GENERATOR_PYLON.get()) ? hit
                : sl.getBlockState(hit).is(OverheadBlocks.PYLON_CASING.get()) ? PylonCasingBlock.coreBelow(sl, hit) : null;
        if (core != null) OverheadYards.get(sl).projectilePylon(sl, core, p);
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Entity cause = e.getExplosion().getIndirectSourceEntity();
        if (cause instanceof OverheadEntity) return;
        Vec3 at = e.getExplosion().center();
        Yard y = OverheadYards.get(sl).nearest(BlockPos.containing(at), 48);
        if (y == null) return;
        y.blastPylons(sl, at, 5, OverheadConfig.PYLON_EXPLOSION_DAMAGE.get().floatValue(), cause instanceof Player p ? p : null);
    }
}
