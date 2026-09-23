package net.teamaof.skylorebosses;

import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.teamaof.skylorebosses.core.BossModule;
import net.teamaof.skylorebosses.core.BossModules;
import net.teamaof.skylorebosses.core.command.SBCommands;
import net.teamaof.skylorebosses.core.config.SBConfig;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skylore Bosses: the boss encounters of the Skylore modpack. The core (registries, animation effects,
 * networking, config, commands) is shared; each boss is a {@link BossModule} listed in {@link BossModules}.
 * Registration, networking and events go through Architectury API.
 */
@Mod(SkyloreBosses.MOD_ID)
public final class SkyloreBosses {
    public static final String MOD_ID = "skylore_bosses";
    public static final Logger LOG = LoggerFactory.getLogger("SkyloreBosses");

    public SkyloreBosses(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SBConfig.build());
        SBRegistries.init(modBus);
        SBNetwork.init();
        SBCommands.init();
        for (BossModule m : BossModules.all()) {
            m.commonInit();
            LOG.info("Loaded boss module {}", m.id());
        }
        if (Platform.getEnvironment() == Env.CLIENT) {
            net.teamaof.skylorebosses.core.client.SBClient.init();
            for (BossModule m : BossModules.all()) m.clientInit();
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
