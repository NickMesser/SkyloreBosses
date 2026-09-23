package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import dev.architectury.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.teamaof.skylorebosses.SkyloreBosses;

/**
 * Soft integration point with Immortuos Calyx. The mod never links against it: when it is installed,
 * its registered status effects are looked up by name so an existing Act I infection makes the Proto-World
 * spread faster and an active immunity/cure effect slows it. Without it, the clock runs on its own.
 */
public interface InfectionBridge {
    String IMMORTUOS = "immortuos_calyx";

    /** Multiplier for incoming infection. */
    float incomingMultiplier(ServerPlayer p);

    default void onChanged(ServerPlayer p, int oldValue, int newValue) {}

    static InfectionBridge create() {
        if (Platform.isModLoaded(IMMORTUOS)) {
            SkyloreBosses.LOG.info("Immortuos Calyx present: infection clock reads its status effects");
            return new Immortuos();
        }
        return p -> 1.0f;
    }

    final class Immortuos implements InfectionBridge {
        private final List<Holder<MobEffect>> infected = new ArrayList<>();
        private final List<Holder<MobEffect>> protectedBy = new ArrayList<>();

        Immortuos() {
            BuiltInRegistries.MOB_EFFECT.holders().forEach(h -> {
                var key = h.key().location();
                if (!key.getNamespace().equals(IMMORTUOS)) return;
                String path = key.getPath();
                if (path.contains("infect") || path.contains("calyx") || path.contains("parasite")) infected.add(h);
                if (path.contains("immun") || path.contains("cure") || path.contains("resist") || path.contains("vaccin")) protectedBy.add(h);
            });
            SkyloreBosses.LOG.info("Immortuos bridge: {} infection effects, {} protective effects", infected.size(), protectedBy.size());
        }

        @Override
        public float incomingMultiplier(ServerPlayer p) {
            float m = 1.0f;
            for (Holder<MobEffect> h : infected) if (p.hasEffect(h)) m *= 1.25f;
            for (Holder<MobEffect> h : protectedBy) if (p.hasEffect(h)) m *= 0.5f;
            return m;
        }
    }
}
