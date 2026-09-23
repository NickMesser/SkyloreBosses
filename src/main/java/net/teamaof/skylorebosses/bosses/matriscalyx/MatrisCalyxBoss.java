package net.teamaof.skylorebosses.bosses.matriscalyx;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.bosses.matriscalyx.client.MatrisClient;
import net.teamaof.skylorebosses.bosses.matriscalyx.command.MatrisCommands;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.MatrisCommonEvents;
import net.teamaof.skylorebosses.bosses.matriscalyx.net.MatrisNetwork;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisAttachments;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisBlockEntities;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisBlocks;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisEntities;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisItems;
import net.teamaof.skylorebosses.core.BossModule;

/**
 * Act V finale: Matris Calyx, the Parasite Mother (design: bosses/matris_calyx/DESIGN.md).
 * Six rooted arms on satellite islands, body attacks, infection clock, then the Calyx Bloom.
 */
public final class MatrisCalyxBoss implements BossModule {
    public static final String ID = "matris_calyx";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        MatrisBlocks.init();
        MatrisEntities.init();
        MatrisBlockEntities.init();
        MatrisItems.init();
        MatrisAttachments.init();
        MatrisLocators.register();
    }

    @Override
    public List<String> soundIds() {
        return List.of(MatrisSoundIds.ALL);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        MatrisConfig.define(builder);
    }

    @Override
    public void commonInit() {
        MatrisEntities.registerAttributes();
        MatrisNetwork.init();
        MatrisCommonEvents.init();
    }

    @Override
    public void clientInit() {
        MatrisClient.init();
    }

    @Override
    public void buildCommands(LiteralArgumentBuilder<CommandSourceStack> node) {
        MatrisCommands.build(node);
    }

    @Override
    public List<String> commandAliases() {
        return List.of("skylorecalyx");
    }
}
