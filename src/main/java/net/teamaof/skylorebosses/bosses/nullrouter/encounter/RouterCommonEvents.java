package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.RetryPacketEntity;

/**
 * Hooks: vault ticking, death counting, retry kill counting, and the placement rule that keeps the docking bay and
 * the console stands clear (DESIGN.md §17 cheese list). Consoles and the shell are unbreakable, so no break hook.
 */
public final class RouterCommonEvents {
    private RouterCommonEvents() {}

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(Vaults::tickLevel);
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity.level() instanceof ServerLevel sl) {
                if (entity instanceof ServerPlayer p) for (Vault v : Vaults.get(sl).all()) v.onPlayerDeath(p);
                if (entity instanceof RetryPacketEntity r && r.vaultOrigin() != null) {
                    Vault v = Vaults.get(sl).at(r.vaultOrigin());
                    if (v != null) v.onRetryKilled(sl, source.getEntity() instanceof Player p ? p : null);
                }
            }
            return EventResult.pass();
        });
        NeoForge.EVENT_BUS.addListener(RouterCommonEvents::onPlace);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Vault v = Vaults.get(sl).containing(e.getPos());
        if (v != null && v.refusePlacement(sl, e.getPos(), e.getEntity() instanceof Player p ? p : null)) e.setCanceled(true);
    }
}
