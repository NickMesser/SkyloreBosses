package net.teamaof.skylorebosses.core.fx;

import java.util.HashMap;
import java.util.Map;

/**
 * Rest-pose locator positions (model pixels) for every boss model, registered by each boss from its
 * generated table (e.g. {@code bosses/matriscalyx/MatrisLocators}). Side-agnostic: server code uses these too.
 */
public final class ModelLocators {
    private static final Map<String, Map<String, float[]>> M = new HashMap<>();

    private ModelLocators() {}

    public static void register(String model, Map<String, float[]> locators) {
        M.put(model, locators);
    }

    /** @return locator position in model pixels, or null */
    public static float[] get(String model, String locator) {
        Map<String, float[]> m = M.get(model);
        return m == null ? null : m.get(locator);
    }
}
