package net.teamaof.skylorebosses.bosses.overhead.encounter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/** One generator pylon slot. The yard owns this state; the block entity at {@link #core} only mirrors it. */
public final class Pylon {
    public final int index;
    public final BlockPos core;
    public PylonState state = PylonState.ONLINE;
    public float integrity, max;
    /** Game time of the last integrity damage (drives the "under attack" read and Overhead's response). */
    public long lastHit = -1000;
    /** Game time this pylon starts regenerating during a re-arm (staggered). */
    public long rebuildAt;
    public int brokenOrder;
    public final Set<UUID> contributors = new HashSet<>();

    public Pylon(int index, BlockPos core) {
        this.index = index;
        this.core = core.immutable();
    }

    public boolean online() { return state == PylonState.ONLINE; }

    public float fraction() { return max <= 0 ? 0 : integrity / max; }

    public boolean underAttack(long now) { return now - lastHit < 60; }

    public void restore(float maxIntegrity) {
        state = PylonState.ONLINE;
        max = maxIntegrity;
        integrity = maxIntegrity;
        contributors.clear();
        brokenOrder = 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("State", state.ordinal());
        t.putFloat("Integrity", integrity);
        t.putFloat("Max", max);
        t.putLong("RebuildAt", rebuildAt);
        t.putInt("BrokenOrder", brokenOrder);
        ListTag l = new ListTag();
        for (UUID u : contributors) l.add(NbtUtils.createUUID(u));
        t.put("Contributors", l);
        return t;
    }

    public void load(CompoundTag t) {
        state = PylonState.byOrdinal(t.getInt("State"));
        integrity = t.getFloat("Integrity");
        max = t.getFloat("Max");
        rebuildAt = t.getLong("RebuildAt");
        brokenOrder = t.getInt("BrokenOrder");
        contributors.clear();
        for (Tag u : t.getList("Contributors", Tag.TAG_INT_ARRAY)) contributors.add(NbtUtils.loadUUID(u));
    }
}
