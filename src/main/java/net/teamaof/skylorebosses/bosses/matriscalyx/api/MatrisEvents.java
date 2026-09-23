package net.teamaof.skylorebosses.bosses.matriscalyx.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;

/**
 * Stable public hooks for the Skylore pack (KubeJS / other mods). Fired on the logical server.
 * The mod never hard-codes pack ids: grant stages, run quests and show ending text from these.
 */
public final class MatrisEvents {
    private MatrisEvents() {}

    public static final Event<EncounterStarted> ENCOUNTER_STARTED = EventFactory.createLoop();
    public static final Event<ArmKilled> ARM_KILLED = EventFactory.createLoop();
    public static final Event<BloomEmerged> BLOOM_EMERGED = EventFactory.createLoop();
    public static final Event<Victory> VICTORY = EventFactory.createLoop();
    public static final Event<Reset> RESET = EventFactory.createLoop();

    public interface EncounterStarted { void started(ServerLevel level, BlockPos origin, List<ServerPlayer> players); }

    /** @param order 1 for the first arm killed ... 6 for the last */
    public interface ArmKilled { void killed(ServerLevel level, BlockPos origin, ArmType arm, @Nullable Entity killer, int order); }

    public interface BloomEmerged { void emerged(ServerLevel level, BlockPos origin, Entity bloom); }

    public interface Victory { void victory(ServerLevel level, BlockPos origin, List<ServerPlayer> participants, EncounterStats stats); }

    public interface Reset { void reset(ServerLevel level, BlockPos origin, String reason); }

    /** Read-only fight summary passed with {@link #VICTORY}. */
    public record EncounterStats(long ticks, boolean nerveFirst, int deaths) {}
}
