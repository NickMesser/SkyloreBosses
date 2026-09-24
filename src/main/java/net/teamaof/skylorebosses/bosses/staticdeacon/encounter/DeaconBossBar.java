package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

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
 * <li>Deacon: HP, coloured by what protects it (white vesting, purple communion, pink patchwork, red stranded or
 * vigil, yellow reseed). The title carries a status line with the live numbers.</li>
 * <li>Communion: live nave flagstones / total, twelve notches (four flagstones each).</li>
 * </ul>
 */
public final class DeaconBossBar {
    private final ServerBossEvent deacon = new ServerBossEvent(Component.translatable("static_deacon.bossbar.name"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final ServerBossEvent floor = new ServerBossEvent(Component.translatable("static_deacon.bossbar.floor"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_12);
    private Component lastStatus, lastFloor;

    public DeaconBossBar() {
        deacon.setDarkenScreen(true);
        deacon.setPlayBossMusic(true);
    }

    public void update(float hp, float coverage, BossEvent.BossBarColor color, Component status, Component floorTitle) {
        deacon.setProgress(clamp(hp));
        floor.setProgress(clamp(coverage));
        deacon.setColor(color);
        if (!Objects.equals(status, lastStatus)) {
            lastStatus = status;
            deacon.setName(Component.translatable("static_deacon.bossbar.title", Component.translatable("static_deacon.bossbar.name"), status));
        }
        if (!Objects.equals(floorTitle, lastFloor)) {
            lastFloor = floorTitle;
            floor.setName(floorTitle);
        }
    }

    public void syncPlayers(List<ServerPlayer> now) {
        Set<ServerPlayer> keep = new HashSet<>(now);
        for (ServerBossEvent e : new ServerBossEvent[]{deacon, floor}) {
            for (ServerPlayer p : List.copyOf(e.getPlayers())) if (!keep.contains(p)) e.removePlayer(p);
            for (ServerPlayer p : now) e.addPlayer(p);
        }
    }

    public void clear() {
        deacon.removeAllPlayers();
        floor.removeAllPlayers();
        lastStatus = null;
        lastFloor = null;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }
}
