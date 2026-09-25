package net.teamaof.skylorebosses.bosses.nullrouter.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Phase;

/**
 * Stable public hooks for the Skylore pack (KubeJS / other mods). Fired on the logical server. The mod never
 * hard-codes pack ids: the {@code ae2_cpu} gate, the existing {@code automaton_network} stage (which opens Act V) and
 * the {@code modular_expression} chapter hang off these. {@code origin} is the vault origin (centre of the floor under
 * the chassis pad), which identifies the vault. Patterns are three glyph ordinals (0 circle, 1 triangle, 2 square) for
 * channels A, B, C.
 */
public final class RouterEvents {
    private RouterEvents() {}

    /** Asked before a vault starts. Return {@link EventResult#interruptFalse()} to refuse (e.g. the ae2_cpu stage gate). */
    public static final Event<StartCheck> START_CHECK = EventFactory.createEventResult();
    public static final Event<EncounterStarted> ENCOUNTER_STARTED = EventFactory.createLoop();
    public static final Event<PhaseChanged> PHASE_CHANGED = EventFactory.createLoop();
    public static final Event<RequestIssued> REQUEST_ISSUED = EventFactory.createLoop();
    public static final Event<AckOpened> ACK_OPENED = EventFactory.createLoop();
    public static final Event<AckClosed> ACK_CLOSED = EventFactory.createLoop();
    public static final Event<Misrouted> MISROUTED = EventFactory.createLoop();
    public static final Event<QueueChanged> QUEUE_CHANGED = EventFactory.createLoop();
    public static final Event<Victory> VICTORY = EventFactory.createLoop();
    public static final Event<Reset> RESET = EventFactory.createLoop();

    public interface StartCheck { EventResult check(ServerLevel level, BlockPos origin, ServerPlayer trigger); }

    public interface EncounterStarted { void started(ServerLevel level, BlockPos origin, List<ServerPlayer> players, int queue); }

    public interface PhaseChanged { void changed(ServerLevel level, BlockPos origin, Phase from, Phase to); }

    /** @param decoy the second request of the dual queue (P2+), or null */
    public interface RequestIssued { void issued(ServerLevel level, BlockPos origin, int serial, int[] head, @Nullable int[] decoy); }

    /** @param actor the player whose flip completed the match, if any */
    public interface AckOpened { void opened(ServerLevel level, BlockPos origin, @Nullable ServerPlayer actor, int queue); }

    /** @param cleared requests this window removed; @param wet whether coolant touched the chassis during it */
    public interface AckClosed { void closed(ServerLevel level, BlockPos origin, int cleared, boolean wet, int queue); }

    /** @param how "alcove", "swap" (traded places with a retry), "shock" (no alcove or retry available), "cooldown" */
    public interface Misrouted { void misrouted(ServerLevel level, BlockPos origin, ServerPlayer actor, String how, int queue); }

    /** @param cause "ack", "misroute", "stall", "command" */
    public interface QueueChanged { void changed(ServerLevel level, BlockPos origin, int from, int to, String cause); }

    public interface Victory { void victory(ServerLevel level, BlockPos origin, List<ServerPlayer> participants, EncounterStats stats); }

    public interface Reset { void reset(ServerLevel level, BlockPos origin, String reason); }

    /** Read-only fight summary passed with {@link #VICTORY}. */
    public record EncounterStats(long ticks, int acks, int wetAcks, int misroutes, int stalls, int storms, int retriesKilled, int deaths) {}
}
