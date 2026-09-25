package net.teamaof.skylorebosses.bosses.amanita;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.bosses.amanita.client.AmanitaClient;
import net.teamaof.skylorebosses.bosses.amanita.command.AmanitaCommands;
import net.teamaof.skylorebosses.bosses.amanita.encounter.AmanitaCommonEvents;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaBlocks;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaEntities;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaItems;
import net.teamaof.skylorebosses.core.BossModule;

/**
 * Act I-II: Amanita, the Hollow Bloom, Tenebris nemesis of the shadow hollow (design: bosses/amanita/DESIGN.md).
 * She cannot be hurt in the dark. Bring light to her, keep it burning through her snuffs and her lamp-eaters, and kill
 * her while she is lit.
 */
public final class AmanitaBoss implements BossModule {
    public static final String ID = "amanita";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        AmanitaBlocks.init();
        AmanitaEntities.init();
        AmanitaItems.init();
        AmanitaLocators.register();
    }

    @Override
    public List<String> soundIds() {
        return List.of(AmanitaSoundIds.ALL);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        AmanitaConfig.define(builder);
    }

    @Override
    public void commonInit() {
        AmanitaEntities.registerAttributes();
        AmanitaCommonEvents.init();
    }

    @Override
    public void clientInit() {
        AmanitaClient.init();
    }

    @Override
    public void buildCommands(LiteralArgumentBuilder<CommandSourceStack> node) {
        AmanitaCommands.build(node);
    }

    @Override
    public List<String> commandAliases() {
        return List.of("skyloreamanita");
    }
}
