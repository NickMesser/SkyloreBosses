package net.teamaof.skylorebosses.bosses.amanita.encounter;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Hooks (DESIGN.md §6): hollow ticking, death counting, light placement (recorded with its owner, or refused while
 * hushed) and broken lights. Everything else about light is read straight from the light engine, so pistons, machines,
 * spells and commands that add or remove light are seen by the next census without a hook.
 */
public final class AmanitaCommonEvents {
    private AmanitaCommonEvents() {}

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(Hollows::tickLevel);
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && p.level() instanceof ServerLevel sl) {
                for (Hollow c : Hollows.get(sl).all()) c.onPlayerDeath(p);
            }
            return EventResult.pass();
        });
        NeoForge.EVENT_BUS.addListener(AmanitaCommonEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(AmanitaCommonEvents::onBreak);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Hollow c = Hollows.get(sl).containing(e.getPos());
        if (c != null && c.onBlockPlaced(sl, e.getPos(), e.getPlacedBlock(), e.getEntity() instanceof Player p ? p : null)) e.setCanceled(true);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Hollow c = Hollows.get(sl).containing(e.getPos());
        if (c != null) c.onBlockBroken(sl, e.getPos());
    }
}
