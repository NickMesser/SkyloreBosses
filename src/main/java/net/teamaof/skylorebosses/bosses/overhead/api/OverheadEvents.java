package net.teamaof.skylorebosses.bosses.overhead.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Phase;

/**
 * Stable public hooks for the Skylore pack (KubeJS / other mods). Fired on the logical server. The mod never
 * hard-codes pack ids: stages ({@code teknari_prototype_down}), quests and chapter text hang off these.
 * {@code origin} is the yard origin (floor centre), which identifies the yard.
 */
public final class OverheadEvents {
    private OverheadEvents() {}

    /** Asked before a yard starts. Return {@link EventResult#interruptFalse()} to refuse (e.g. stage gate). */
    public static final Event<StartCheck> START_CHECK = EventFactory.createEventResult();
    public static final Event<EncounterStarted> ENCOUNTER_STARTED = EventFactory.createLoop();
    public static final Event<PhaseChanged> PHASE_CHANGED = EventFactory.createLoop();
    public static final Event<PylonBroken> PYLON_BROKEN = EventFactory.createLoop();
    public static final Event<RearmStarted> REARM_STARTED = EventFactory.createLoop();
    public static final Event<Victory> VICTORY = EventFactory.createLoop();
    public static final Event<Reset> RESET = EventFactory.createLoop();

    public interface StartCheck { EventResult check(ServerLevel level, BlockPos origin, ServerPlayer trigger); }

    public interface EncounterStarted { void started(ServerLevel level, BlockPos origin, List<ServerPlayer> players); }

    public interface PhaseChanged { void changed(ServerLevel level, BlockPos origin, Phase from, Phase to); }

    /**
     * @param index       pylon slot 0..3 (0 = north-west, 1 = north-east, 2 = south-west, 3 = south-east)
     * @param breaker     player who dealt the final integrity damage, if any
     * @param contributors every player who damaged this pylon since it was last online
     * @param remaining   pylons still online after this break
     */
    public interface PylonBroken { void broken(ServerLevel level, BlockPos origin, int index, @Nullable ServerPlayer breaker, Set<UUID> contributors, int remaining); }

    /** @param count 1 for the first re-arm of this fight; @param target pylons it will bring back online */
    public interface RearmStarted { void started(ServerLevel level, BlockPos origin, int count, int target); }

    public interface Victory { void victory(ServerLevel level, BlockPos origin, List<ServerPlayer> participants, EncounterStats stats); }

    public interface Reset { void reset(ServerLevel level, BlockPos origin, String reason); }

    /** Read-only fight summary passed with {@link #VICTORY}. */
    public record EncounterStats(long ticks, int rearms, int deaths, int pylonsBroken, boolean anyRearm) {}
}
