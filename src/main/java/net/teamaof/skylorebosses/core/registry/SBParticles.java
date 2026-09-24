package net.teamaof.skylorebosses.core.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.particles.SimpleParticleType;
import net.teamaof.skylorebosses.SkyloreBosses;

/**
 * Shared particle library, named like the Snowstorm effects keyframed in the Blockbench animations
 * (any boss can use them; keyframes may carry any namespace, e.g. "matris_calyx:bile_drip").
 */
public final class SBParticles {
    public static final Map<String, RegistrySupplier<SimpleParticleType>> BY_NAME = new LinkedHashMap<>();
    public static final String[] NAMES = {"bile_drip", "bile_splash", "spore_puff", "spore_haze", "nerve_spark", "nerve_pulse",
            "core_glow", "blood_burst", "flesh_chunks", "root_dust", "laser_charge", "laser_beam", "eye_glint", "mucus_string",
            // industrial set (Overhead)
            "smoke", "spark", "muzzle_flash", "grid_arc", "ember", "shield", "target_mark", "rail_mark", "red_beam"};

    static {
        for (String n : NAMES) {
            boolean alwaysShow = n.equals("laser_beam") || n.equals("laser_charge") || n.endsWith("_mark") || n.equals("red_beam")
                    || n.equals("grid_arc");
            BY_NAME.put(n, SBRegistries.PARTICLE_TYPES.register(n, () -> new SimpleParticleType(alwaysShow)));
        }
    }

    public static final RegistrySupplier<SimpleParticleType> BILE_DRIP = BY_NAME.get("bile_drip");
    public static final RegistrySupplier<SimpleParticleType> BILE_SPLASH = BY_NAME.get("bile_splash");
    public static final RegistrySupplier<SimpleParticleType> SPORE_PUFF = BY_NAME.get("spore_puff");
    public static final RegistrySupplier<SimpleParticleType> SPORE_HAZE = BY_NAME.get("spore_haze");
    public static final RegistrySupplier<SimpleParticleType> NERVE_SPARK = BY_NAME.get("nerve_spark");
    public static final RegistrySupplier<SimpleParticleType> NERVE_PULSE = BY_NAME.get("nerve_pulse");
    public static final RegistrySupplier<SimpleParticleType> CORE_GLOW = BY_NAME.get("core_glow");
    public static final RegistrySupplier<SimpleParticleType> BLOOD_BURST = BY_NAME.get("blood_burst");
    public static final RegistrySupplier<SimpleParticleType> FLESH_CHUNKS = BY_NAME.get("flesh_chunks");
    public static final RegistrySupplier<SimpleParticleType> ROOT_DUST = BY_NAME.get("root_dust");
    public static final RegistrySupplier<SimpleParticleType> LASER_CHARGE = BY_NAME.get("laser_charge");
    public static final RegistrySupplier<SimpleParticleType> LASER_BEAM = BY_NAME.get("laser_beam");
    public static final RegistrySupplier<SimpleParticleType> EYE_GLINT = BY_NAME.get("eye_glint");
    public static final RegistrySupplier<SimpleParticleType> MUCUS_STRING = BY_NAME.get("mucus_string");
    public static final RegistrySupplier<SimpleParticleType> SMOKE = BY_NAME.get("smoke");
    public static final RegistrySupplier<SimpleParticleType> SPARK = BY_NAME.get("spark");
    public static final RegistrySupplier<SimpleParticleType> MUZZLE_FLASH = BY_NAME.get("muzzle_flash");
    public static final RegistrySupplier<SimpleParticleType> GRID_ARC = BY_NAME.get("grid_arc");
    public static final RegistrySupplier<SimpleParticleType> EMBER = BY_NAME.get("ember");
    public static final RegistrySupplier<SimpleParticleType> SHIELD = BY_NAME.get("shield");
    /** Ground telegraphs: red = something lands here, amber = something passes over this line. */
    public static final RegistrySupplier<SimpleParticleType> TARGET_MARK = BY_NAME.get("target_mark");
    public static final RegistrySupplier<SimpleParticleType> RAIL_MARK = BY_NAME.get("rail_mark");
    public static final RegistrySupplier<SimpleParticleType> RED_BEAM = BY_NAME.get("red_beam");

    private SBParticles() {}

    /** Forces class init so the entries exist before registration. */
    static void init() {}

    public static SimpleParticleType get(String name) {
        RegistrySupplier<SimpleParticleType> s = BY_NAME.get(name);
        return s == null ? null : s.get();
    }
}
