package net.teamaof.skylorebosses.bosses.overhead.encounter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * DESIGN.md §8. Two bars shown to every participant:
 * <ul>
 * <li>Chassis: Overhead's HP, coloured by gate state (white powering on, blue shielded, yellow window, red exposed,
 * purple re-arm). The title carries a status line ("Grid 3/4 - shielded 85%", "GRID FAULT - window 7s", ...).</li>
 * <li>Yard grid: sum of pylon integrity / 4, twenty notches (five per pylon).</li>
 * </ul>
 */
public final class OverheadBossBar {
    private final ServerBossEvent chassis = new ServerBossEvent(Component.translatable("overhead.bossbar.name"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent grid = new ServerBossEvent(Component.translatable("overhead.bossbar.grid"),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_20);
    private Component lastStatus;

    public OverheadBossBar() {
        chassis.setDarkenScreen(false);
        chassis.setPlayBossMusic(true);
    }

    public void update(float hp, float gridFraction, BossEvent.BossBarColor color, Component status) {
        chassis.setProgress(clamp(hp));
        grid.setProgress(clamp(gridFraction));
        chassis.setColor(color);
        if (!Objects.equals(status, lastStatus)) {
            lastStatus = status;
            chassis.setName(Component.translatable("overhead.bossbar.title", Component.translatable("overhead.bossbar.name"), status));
        }
    }

    public void syncPlayers(List<ServerPlayer> now) {
        Set<ServerPlayer> keep = new HashSet<>(now);
        for (ServerBossEvent e : new ServerBossEvent[]{chassis, grid}) {
            for (ServerPlayer p : List.copyOf(e.getPlayers())) if (!keep.contains(p)) e.removePlayer(p);
            for (ServerPlayer p : now) e.addPlayer(p);
        }
    }

    public void clear() {
        chassis.removeAllPlayers();
        grid.removeAllPlayers();
        lastStatus = null;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
