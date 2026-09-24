package net.teamaof.skylorebosses.bosses.staticdeacon.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Phase;

/**
 * Stable public hooks for the Skylore pack (KubeJS / other mods). Fired on the logical server. The mod never
 * hard-codes pack ids: the {@code church_of_ender} gate, the {@code static_deacon_cleared} stage and the
 * {@code forbidden_knowledge} quest hang off these. {@code origin} is the crypt origin (centre of the floor under the
 * plinth), which identifies the crypt.
 */
public final class DeaconEvents {
    private DeaconEvents() {}

    /** Asked before a crypt starts. Return {@link EventResult#interruptFalse()} to refuse (e.g. stage gate). */
    public static final Event<StartCheck> START_CHECK = EventFactory.createEventResult();
    public static final Event<EncounterStarted> ENCOUNTER_STARTED = EventFactory.createLoop();
    public static final Event<PhaseChanged> PHASE_CHANGED = EventFactory.createLoop();
    public static final Event<FlagstoneDesecrated> FLAGSTONE_DESECRATED = EventFactory.createLoop();
    public static final Event<VigilBroken> VIGIL_BROKEN = EventFactory.createLoop();
    public static final Event<ReseedStarted> RESEED_STARTED = EventFactory.createLoop();
    public static final Event<Victory> VICTORY = EventFactory.createLoop();
    public static final Event<Reset> RESET = EventFactory.createLoop();

    public interface StartCheck { EventResult check(ServerLevel level, BlockPos origin, ServerPlayer trigger); }

    public interface EncounterStarted { void started(ServerLevel level, BlockPos origin, List<ServerPlayer> players); }

    public interface PhaseChanged { void changed(ServerLevel level, BlockPos origin, Phase from, Phase to); }

    /**
     * @param slot      flagstone index in the crypt layout (see CryptLayout; stable per crypt)
     * @param player    player responsible (mined, covered or blew it up), if known
     * @param cause     "mined", "covered", "explosion", "scan" (anything else that removed it) or "command"
     * @param remaining live nave flagstones after this one
     */
    public interface FlagstoneDesecrated { void desecrated(ServerLevel level, BlockPos origin, int slot, @Nullable ServerPlayer player, String cause, int remaining); }

    /** @param rite true if the reseed rite was broken, false for a plain vigil break */
    public interface VigilBroken { void broken(ServerLevel level, BlockPos origin, @Nullable ServerPlayer breaker, boolean rite); }

    /** @param count 1 for the first rite of this fight; @param target live flagstones that end the rite */
    public interface ReseedStarted { void started(ServerLevel level, BlockPos origin, int count, int target); }

    public interface Victory { void victory(ServerLevel level, BlockPos origin, List<ServerPlayer> participants, EncounterStats stats); }

    public interface Reset { void reset(ServerLevel level, BlockPos origin, String reason); }

    /** Read-only fight summary passed with {@link #VICTORY}. */
    public record EncounterStats(long ticks, int reseeds, int deaths, int flagstonesDesecrated, int vigilBreaks, boolean anyReseed) {}
}
