package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Hooks (DESIGN.md §6): crypt ticking, death counting, and the three player verbs that desecrate the floor with
 * attribution: mining a flagstone block, covering one with a solid block, and blowing one up. The crypt's periodic
 * floor scan catches every other way a block can change (pistons, machines, commands).
 */
public final class DeaconCommonEvents {
    private DeaconCommonEvents() {}

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(Crypts::tickLevel);
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && p.level() instanceof ServerLevel sl) {
                for (Crypt c : Crypts.get(sl).all()) c.onPlayerDeath(p);
            }
            return EventResult.pass();
        });
        NeoForge.EVENT_BUS.addListener(DeaconCommonEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(DeaconCommonEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(DeaconCommonEvents::onExplosion);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Crypt c = Crypts.get(sl).containing(e.getPos());
        if (c != null) c.onBlockBroken(sl, e.getPos(), e.getPlayer());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Crypt c = Crypts.get(sl).containing(e.getPos());
        if (c != null && c.onBlockPlaced(sl, e.getPos(), e.getPlacedBlock(), e.getEntity() instanceof Player p ? p : null)) e.setCanceled(true);
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Entity cause = e.getExplosion().getIndirectSourceEntity();
        BlockPos at = BlockPos.containing(e.getExplosion().center());
        Crypt c = Crypts.get(sl).containing(at);
        if (c != null) c.onExplosion(sl, e.getAffectedBlocks(), cause instanceof Player p ? p : null);
    }
}
