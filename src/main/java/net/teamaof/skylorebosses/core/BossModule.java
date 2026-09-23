package net.teamaof.skylorebosses.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * One boss encounter. Implement this, add it to {@link BossModules#all()}, and put the boss's classes under
 * {@code bosses/<name>/} (Java), its art and tools under the repo folder {@code bosses/<id>/}, and its assets under {@code assets/skylore_bosses/.../<id>/}.
 *
 * <p>Lifecycle (all called from the mod constructor, in this order):
 * {@link #registerContent()} → {@link #soundIds()} → {@link #defineConfig} → {@link #commonInit()} →
 * {@link #clientInit()} (client only). Commands are added under {@code /skylorebosses <id>}.
 */
public interface BossModule {
    /** Short stable id, also the asset subfolder and sound prefix, e.g. "matris_calyx". */
    String id();

    /**
     * Touch the boss's registry holder classes so their entries are added to the shared
     * {@link net.teamaof.skylorebosses.core.registry.SBRegistries} before they are registered.
     */
    void registerContent();

    /** Sound ids without prefix (e.g. "arm.open"); registered as {@code skylore_bosses:<id>.<sound>}. */
    default List<String> soundIds() { return List.of(); }

    /** Server config values; called inside a {@code [<id>]} section of skylore_bosses-server.toml. */
    default void defineConfig(ModConfigSpec.Builder builder) {}

    /** Events, attributes, networking. */
    default void commonInit() {}

    /** Renderers, HUD, client packet handlers. Only called on the physical client. */
    default void clientInit() {}

    /** Subcommands, attached as {@code /skylorebosses <id> ...}. {@code node} is already the {@code <id>} literal. */
    default void buildCommands(LiteralArgumentBuilder<CommandSourceStack> node) {}

    /** Extra top-level command names that alias {@code /skylorebosses <id>} (e.g. "skylorecalyx"). */
    default List<String> commandAliases() { return List.of(); }
}
