package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * Pillar 5: the bar tracks Matris, not a single hitbox. Six notches, each arm worth 100 of 600,
 * contributing in proportion to its HP. Retitled for the Bloom in phase 3.
 */
public final class MatrisBossBar {
    private final ServerBossEvent event = new ServerBossEvent(Component.translatable("matris_calyx.bossbar.matris"),
            BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.NOTCHED_6);
    private boolean bloom;

    public MatrisBossBar() {
        event.setDarkenScreen(true);
        event.setPlayBossMusic(true);
    }

    public void setLimbs(float value) {
        if (bloom) {
            bloom = false;
            event.setName(Component.translatable("matris_calyx.bossbar.matris"));
            event.setColor(BossEvent.BossBarColor.PINK);
            event.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
        }
        event.setProgress(Math.max(0, Math.min(1, value)));
    }

    public void setBloom(float value) {
        if (!bloom) {
            bloom = true;
            event.setName(Component.translatable("matris_calyx.bossbar.bloom"));
            event.setColor(BossEvent.BossBarColor.RED);
            event.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        }
        event.setProgress(Math.max(0, Math.min(1, value)));
    }

    public void syncPlayers(List<ServerPlayer> now) {
        Set<ServerPlayer> keep = new HashSet<>(now);
        for (ServerPlayer p : List.copyOf(event.getPlayers())) if (!keep.contains(p)) event.removePlayer(p);
        for (ServerPlayer p : now) event.addPlayer(p);
    }

    public void setVisible(boolean v) {
        event.setVisible(v);
    }

    public void clear() {
        event.removeAllPlayers();
    }
}
