package net.teamaof.skylorebosses.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.teamaof.skylorebosses.core.BossModule;
import net.teamaof.skylorebosses.core.BossModules;

/** skylore_bosses-server.toml: one section per boss module. */
public final class SBConfig {
    public static ModConfigSpec.ConfigValue<String> DOCTOR_PLAYER_TAG;

    private SBConfig() {}

    public static ModConfigSpec build() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Settings shared by all Skylore bosses").push("general");
        DOCTOR_PLAYER_TAG = b.comment("Scoreboard tag (/tag) that marks a doctor; holding #skylore_bosses:doctor_tools also counts")
                .define("doctorPlayerTag", "skylore_doctor");
        b.pop();
        for (BossModule m : BossModules.all()) {
            b.push(m.id());
            m.defineConfig(b);
            b.pop();
        }
        return b.build();
    }
}
