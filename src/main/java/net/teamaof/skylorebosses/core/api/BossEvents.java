package net.teamaof.skylorebosses.core.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Boss-agnostic hooks for the Skylore pack (KubeJS, quests, stages). Every boss fires these in addition to
 * its own detailed events (e.g. {@code MatrisEvents}). {@code bossId} is the module id, e.g. "matris_calyx".
 */
public final class BossEvents {
    private BossEvents() {}

    public static final Event<Started> STARTED = EventFactory.createLoop();
    public static final Event<Defeated> DEFEATED = EventFactory.createLoop();

    public interface Started { void started(ServerLevel level, String bossId, BlockPos origin, List<ServerPlayer> players); }

    public interface Defeated { void defeated(ServerLevel level, String bossId, BlockPos origin, List<ServerPlayer> participants); }
}
