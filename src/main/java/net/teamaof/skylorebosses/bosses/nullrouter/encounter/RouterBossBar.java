package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

/**
 * DESIGN.md §8. Two bars shown to every participant (one shared queue, never per-player):
 * <ul>
 * <li>Queue: unacked requests / the starting queue, twelve notches. Coloured by state (white boot, blue ghost,
 * yellow arming, green ACK, purple wet ACK, red retry storm); the title carries the count and a status line.</li>
 * <li>Request: between windows, the head request's glyphs and the retransmit clock (progress drains toward the next
 * stall); during a window, the window itself (progress drains to close) with requests cleared / quota.</li>
 * </ul>
 */
public final class RouterBossBar {
    public enum Color {
        WHITE(BossEvent.BossBarColor.WHITE), BLUE(BossEvent.BossBarColor.BLUE), YELLOW(BossEvent.BossBarColor.YELLOW),
        GREEN(BossEvent.BossBarColor.GREEN), PURPLE(BossEvent.BossBarColor.PURPLE), RED(BossEvent.BossBarColor.RED);

        final BossEvent.BossBarColor vanilla;

        Color(BossEvent.BossBarColor v) { vanilla = v; }
    }

    private final ServerBossEvent queueBar = new ServerBossEvent(Component.translatable("null_router.bossbar.name"),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_12);
    private final ServerBossEvent requestBar = new ServerBossEvent(Component.translatable("null_router.bossbar.booting"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
    private Component lastStatus, lastSecond;
    private int lastQueue = -1;

    public RouterBossBar() {
        queueBar.setPlayBossMusic(true);
    }

    public void update(float queue, Color color, Component status, float second, Component secondTitle, boolean wet) {
        queueBar.setProgress(clamp(queue));
        queueBar.setColor(color.vanilla);
        requestBar.setProgress(clamp(second));
        requestBar.setColor(wet ? BossEvent.BossBarColor.PURPLE : color == Color.GREEN ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.WHITE);
        if (!Objects.equals(status, lastStatus)) {
            lastStatus = status;
            refreshTitle();
        }
        if (!Objects.equals(secondTitle, lastSecond)) {
            lastSecond = secondTitle;
            requestBar.setName(secondTitle);
        }
    }

    public void setQueue(int q) {
        if (q == lastQueue) return;
        lastQueue = q;
        refreshTitle();
    }

    private void refreshTitle() {
        queueBar.setName(Component.translatable("null_router.bossbar.title", Component.translatable("null_router.bossbar.name"),
                Math.max(0, lastQueue), lastStatus == null ? Component.empty() : lastStatus));
    }

    public void syncPlayers(List<ServerPlayer> now) {
        Set<ServerPlayer> keep = new HashSet<>(now);
        for (ServerBossEvent e : new ServerBossEvent[]{queueBar, requestBar}) {
            for (ServerPlayer p : List.copyOf(e.getPlayers())) if (!keep.contains(p)) e.removePlayer(p);
            for (ServerPlayer p : now) e.addPlayer(p);
        }
    }

    public void clear() {
        queueBar.removeAllPlayers();
        requestBar.removeAllPlayers();
        lastStatus = null;
        lastSecond = null;
        lastQueue = -1;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
