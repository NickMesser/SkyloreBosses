package net.teamaof.skylorebosses.bosses.overhead;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.bosses.overhead.client.OverheadClient;
import net.teamaof.skylorebosses.bosses.overhead.command.OverheadCommands;
import net.teamaof.skylorebosses.bosses.overhead.encounter.OverheadCommonEvents;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlockEntities;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlocks;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadEntities;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadItems;
import net.teamaof.skylorebosses.core.BossModule;

/**
 * Act II: Overhead, Noven's Decommissioned Prototype (design: bosses/overhead/DESIGN.md).
 * A hovering artillery chassis shielded by four generator pylons in a Teknari yard: break the pylons under fire.
 */
public final class OverheadBoss implements BossModule {
    public static final String ID = "overhead";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        OverheadBlocks.init();
        OverheadEntities.init();
        OverheadBlockEntities.init();
        OverheadItems.init();
        OverheadLocators.register();
    }

    @Override
    public List<String> soundIds() {
        return List.of(OverheadSoundIds.ALL);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        OverheadConfig.define(builder);
    }

    @Override
    public void commonInit() {
        OverheadEntities.registerAttributes();
        OverheadCommonEvents.init();
    }

    @Override
    public void clientInit() {
        OverheadClient.init();
    }

    @Override
    public void buildCommands(LiteralArgumentBuilder<CommandSourceStack> node) {
        OverheadCommands.build(node);
    }

    @Override
    public List<String> commandAliases() {
        return List.of("skyloreoverhead");
    }
}
