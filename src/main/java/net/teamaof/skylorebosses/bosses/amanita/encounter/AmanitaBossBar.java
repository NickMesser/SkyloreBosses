package net.teamaof.skylorebosses.bosses.amanita.encounter;

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
 * <li>Amanita: HP, coloured by the gate (white P0, purple closed in the dark, yellow exposed, blue snuffed, red deep
 * bloom). The title carries the light at her against the threshold, so "why can't I hurt her" is always on screen.</li>
 * <li>Hollow light: the light sources burning in the hollow, ten notches. In P2 it counts the relight target; in P4 it
 * counts the force-open progress.</li>
 * </ul>
 */
public final class AmanitaBossBar {
    private final ServerBossEvent boss = new ServerBossEvent(Component.translatable("amanita.bossbar.name"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent light = new ServerBossEvent(Component.translatable("amanita.bossbar.light"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private Component lastStatus, lastLight;

    public AmanitaBossBar() {
        boss.setDarkenScreen(true);
        boss.setPlayBossMusic(true);
        boss.setCreateWorldFog(true);
    }

    public void update(float hp, float lit, BossEvent.BossBarColor color, BossEvent.BossBarColor lightColor, Component status, Component lightTitle) {
        boss.setProgress(clamp(hp));
        light.setProgress(clamp(lit));
        boss.setColor(color);
        light.setColor(lightColor);
        if (!Objects.equals(status, lastStatus)) {
            lastStatus = status;
            boss.setName(Component.translatable("amanita.bossbar.title", Component.translatable("amanita.bossbar.name"), status));
        }
        if (!Objects.equals(lightTitle, lastLight)) {
            lastLight = lightTitle;
            light.setName(lightTitle);
        }
    }

    public void syncPlayers(List<ServerPlayer> now) {
        Set<ServerPlayer> keep = new HashSet<>(now);
        for (ServerBossEvent e : new ServerBossEvent[]{boss, light}) {
            for (ServerPlayer p : List.copyOf(e.getPlayers())) if (!keep.contains(p)) e.removePlayer(p);
            for (ServerPlayer p : now) e.addPlayer(p);
        }
    }

    public void clear() {
        boss.removeAllPlayers();
        light.removeAllPlayers();
        lastStatus = null;
        lastLight = null;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
