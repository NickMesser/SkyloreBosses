package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.net.MatrisNetwork;

/** Loader-agnostic hooks through Architectury events (plus one NeoForge event Architectury does not expose). */
public final class MatrisCommonEvents {
    public static final ResourceKey<Level> PROTO_WORLD = ResourceKey.create(Registries.DIMENSION, SkyloreBosses.id("proto_world"));
    /** Default arena origin inside the Proto-World dimension. */
    public static final BlockPos PROTO_ORIGIN = new BlockPos(0, 120, 0);

    private MatrisCommonEvents() {}

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(MatrisEncounter::tickLevel);
        PlayerEvent.PLAYER_JOIN.register(p -> MatrisNetwork.sendInfection(p, Infection.get(p)));
        PlayerEvent.PLAYER_RESPAWN.register((p, conqueredEnd, reason) -> MatrisNetwork.sendInfection(p, Infection.get(p)));
        PlayerEvent.CHANGE_DIMENSION.register(MatrisCommonEvents::onChangeDimension);
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer p && p.level() instanceof ServerLevel sl) {
                MatrisEncounter e = MatrisEncounter.get(sl);
                if (e != null) e.onPlayerDeath(p);
            }
            return EventResult.pass();
        });
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish ev) -> {
            if (ev.getEntity() instanceof ServerPlayer p) Infection.onItemConsumed(p, ev.getItem());
        });
    }

    private static void onChangeDimension(ServerPlayer p, ResourceKey<Level> from, ResourceKey<Level> to) {
        MatrisNetwork.sendInfection(p, Infection.get(p));
        if (!to.equals(PROTO_WORLD)) return;
        MatrisEncounter.award(p, "enter_proto_world");
        ServerLevel level = p.server.getLevel(PROTO_WORLD);
        if (level == null) return;
        MatrisEncounter e = MatrisEncounter.get(level);
        if (e == null) e = MatrisEncounter.start(level, PROTO_ORIGIN, false, true);
        e.finishBuild(level);
    }
}
