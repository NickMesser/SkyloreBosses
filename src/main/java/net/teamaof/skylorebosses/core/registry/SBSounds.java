package net.teamaof.skylorebosses.core.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.core.BossModule;

/**
 * Sound events for every boss, registered as {@code skylore_bosses:<boss>.<sound>} (e.g.
 * {@code skylore_bosses:matris_calyx.arm.open}); sounds.json uses the same keys.
 */
public final class SBSounds {
    private static final Map<String, RegistrySupplier<SoundEvent>> BY_ID = new HashMap<>();

    private SBSounds() {}

    static void registerModule(BossModule m) {
        for (String s : m.soundIds()) register(m.id() + "." + s);
    }

    public static void register(String fullId) {
        if (BY_ID.containsKey(fullId)) return;
        BY_ID.put(fullId, SBRegistries.SOUND_EVENTS.register(fullId, () -> SoundEvent.createVariableRangeEvent(SkyloreBosses.id(fullId))));
    }

    /** @param fullId e.g. "matris_calyx.arm.open"; null if unknown */
    public static SoundEvent get(String fullId) {
        RegistrySupplier<SoundEvent> s = BY_ID.get(fullId);
        return s == null ? null : s.get();
    }
}
