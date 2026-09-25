package net.teamaof.skylorebosses.bosses.nullrouter;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.bosses.nullrouter.client.RouterClient;
import net.teamaof.skylorebosses.bosses.nullrouter.command.RouterCommands;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.RouterCommonEvents;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterBlocks;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterEntities;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterItems;
import net.teamaof.skylorebosses.core.BossModule;

/**
 * Act IV: Null Router, the Unacked (design: bosses/null_router/DESIGN.md). An orphaned Automaton routing process still
 * answering tickets for a guild that left. The boss bar is its unacked request queue: set the three channel consoles to
 * match the displayed request, and the chassis solidifies for a short ACK window in which damage clears requests. Wet
 * the chassis during the window in P3+, keep the floor dry between windows, and kill the retry packets that undo the
 * consoles. The queue at zero is the win.
 */
public final class NullRouterBoss implements BossModule {
    public static final String ID = "null_router";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        RouterBlocks.init();
        RouterEntities.init();
        RouterItems.init();
        RouterLocators.register();
    }

    @Override
    public List<String> soundIds() {
        return List.of(RouterSoundIds.ALL);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        RouterConfig.define(builder);
    }

    @Override
    public void commonInit() {
        RouterEntities.registerAttributes();
        RouterCommonEvents.init();
    }

    @Override
    public void clientInit() {
        RouterClient.init();
    }

    @Override
    public void buildCommands(LiteralArgumentBuilder<CommandSourceStack> node) {
        RouterCommands.build(node);
    }

    @Override
    public List<String> commandAliases() {
        return List.of("skylorenullrouter");
    }
}
