package net.teamaof.skylorebosses.core;

import java.util.List;
import java.util.Optional;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaBoss;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisCalyxBoss;
import net.teamaof.skylorebosses.bosses.nullrouter.NullRouterBoss;
import net.teamaof.skylorebosses.bosses.overhead.OverheadBoss;
import net.teamaof.skylorebosses.bosses.staticdeacon.StaticDeaconBoss;

/** The list of bosses in the mod. Adding a boss = one line here. */
public final class BossModules {
    private static final List<BossModule> ALL = List.of(
            new MatrisCalyxBoss(),
            new AmanitaBoss(),
            new OverheadBoss(),
            new StaticDeaconBoss(),
            new NullRouterBoss()
    );

    private BossModules() {}

    public static List<BossModule> all() {
        return ALL;
    }

    public static Optional<BossModule> byId(String id) {
        return ALL.stream().filter(m -> m.id().equals(id)).findFirst();
    }
}
