package net.teamaof.skylorebosses.bosses.staticdeacon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.bosses.staticdeacon.client.DeaconClient;
import net.teamaof.skylorebosses.bosses.staticdeacon.command.DeaconCommands;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.DeaconCommonEvents;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconBlocks;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconEntities;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconItems;
import net.teamaof.skylorebosses.core.BossModule;

/**
 * Act III: The Static Deacon, caretaker of the Church of Ender undercroft (design: bosses/static_deacon/DESIGN.md).
 * A crystalline Beryl caretaker that heals from the consecrated endstone floor: strip the flagstones, strand it on the
 * altar plinth, and kill it before the reseed rite brings the floor back.
 */
public final class StaticDeaconBoss implements BossModule {
    public static final String ID = "static_deacon";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void registerContent() {
        DeaconBlocks.init();
        DeaconEntities.init();
        DeaconItems.init();
        DeaconLocators.register();
    }

    @Override
    public List<String> soundIds() {
        return List.of(DeaconSoundIds.ALL);
    }

    @Override
    public void defineConfig(ModConfigSpec.Builder builder) {
        DeaconConfig.define(builder);
    }

    @Override
    public void commonInit() {
        DeaconEntities.registerAttributes();
        DeaconCommonEvents.init();
    }

    @Override
    public void clientInit() {
        DeaconClient.init();
    }

    @Override
    public void buildCommands(LiteralArgumentBuilder<CommandSourceStack> node) {
        DeaconCommands.build(node);
    }

    @Override
    public List<String> commandAliases() {
        return List.of("skyloredeacon");
    }
}
