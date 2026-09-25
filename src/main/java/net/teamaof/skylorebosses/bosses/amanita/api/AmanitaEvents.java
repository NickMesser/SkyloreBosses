package net.teamaof.skylorebosses.bosses.amanita.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import dev.architectury.event.EventResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Phase;

/**
 * Stable public hooks for the Skylore pack (KubeJS / other mods). Fired on the logical server. The mod never hard-codes
 * pack ids: the {@code met_amanita} gate, the {@code amanita_bested} stage and the {@code named_contacts} / {@code defense}
 * quests hang off these. {@code origin} is the hollow origin (centre of the floor), which identifies the hollow.
 */
public final class AmanitaEvents {
    private AmanitaEvents() {}

    /** Asked before a hollow starts. Return {@link EventResult#interruptFalse()} to refuse (e.g. the met_amanita gate). */
    public static final Event<StartCheck> START_CHECK = EventFactory.createEventResult();
    public static final Event<EncounterStarted> ENCOUNTER_STARTED = EventFactory.createLoop();
    public static final Event<PhaseChanged> PHASE_CHANGED = EventFactory.createLoop();
    public static final Event<LightPlaced> LIGHT_PLACED = EventFactory.createLoop();
    public static final Event<LightsSnuffed> LIGHTS_SNUFFED = EventFactory.createLoop();
    public static final Event<Exposed> EXPOSED = EventFactory.createLoop();
    public static final Event<DeepBloom> DEEP_BLOOM = EventFactory.createLoop();
    public static final Event<Victory> VICTORY = EventFactory.createLoop();
    /** Fired per participant after the artifact loot table rolled; the pack may add to or replace {@code drops}. */
    public static final Event<ArtifactsRolled> ARTIFACTS_ROLLED = EventFactory.createLoop();
    public static final Event<Reset> RESET = EventFactory.createLoop();

    public interface StartCheck { EventResult check(ServerLevel level, BlockPos origin, ServerPlayer trigger); }

    public interface EncounterStarted { void started(ServerLevel level, BlockPos origin, List<ServerPlayer> players); }

    public interface PhaseChanged { void changed(ServerLevel level, BlockPos origin, Phase from, Phase to); }

    /** A light-emitting block was placed (or a brazier lit) inside a fighting hollow. */
    public interface LightPlaced { void placed(ServerLevel level, BlockPos origin, BlockPos pos, @Nullable ServerPlayer player, int emission); }

    /**
     * @param cause "snuff_pulse", "full_snuff", "closing_snuff", "lamp_eater", "hollow_bolt", "spore_veil", "bloom_slam",
     *              "light_seeker_dash" or "command"
     */
    public interface LightsSnuffed { void snuffed(ServerLevel level, BlockPos origin, String cause, int count); }

    /** Amanita went from closed (dark) to exposed. {@code light} is the light at her. */
    public interface Exposed { void exposed(ServerLevel level, BlockPos origin, int light); }

    /** @param count 1 for the first deep bloom of this fight */
    public interface DeepBloom { void bloomed(ServerLevel level, BlockPos origin, int count); }

    public interface Victory { void victory(ServerLevel level, BlockPos origin, List<ServerPlayer> participants, EncounterStats stats); }

    public interface ArtifactsRolled { void rolled(ServerLevel level, BlockPos origin, ServerPlayer player, List<ItemStack> drops); }

    public interface Reset { void reset(ServerLevel level, BlockPos origin, String reason); }

    /** Read-only fight summary passed with {@link #VICTORY}. */
    public record EncounterStats(long ticks, int deaths, int snuffs, int lightsPlaced, int lightsSnuffed, int lightsEaten,
                                 int deepBlooms, boolean relitByPlayers, long exposedTicks) {}

    // ------------------------------------------------------------ origins (soft: no Origins dependency)
    /** Origin kinds the fight distinguishes (DESIGN.md §13). */
    public enum OriginKind { TENEBRIS, LAEVIS, OTHER }

    private static final List<Function<ServerPlayer, String>> ORIGIN_RESOLVERS = new CopyOnWriteArrayList<>();

    /**
     * Register a resolver that returns a player's origin id (e.g. "skylore:tenebris") or null. The pack registers one
     * that reads Origins; the id is matched against the tenebrisOrigins / laevisOrigins config lists.
     */
    public static void registerOriginResolver(Function<ServerPlayer, String> resolver) {
        ORIGIN_RESOLVERS.add(resolver);
    }

    public static List<Function<ServerPlayer, String>> originResolvers() {
        return ORIGIN_RESOLVERS;
    }
}
