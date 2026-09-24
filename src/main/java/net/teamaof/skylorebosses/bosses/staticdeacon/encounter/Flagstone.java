package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One 3x3 slot of the crypt floor. Only NAVE slots change state; the crypt owns this state and the floor blocks are
 * a view of it (DESIGN.md §6).
 */
public final class Flagstone {
    public enum State { LIVE, DESECRATED, RESEEDING }

    public final int index, cx, cz;
    public final CryptLayout.Kind kind;
    public final BlockPos center;
    public State state = State.LIVE;
    /** Game time a RESEEDING flagstone turns LIVE. */
    public long matureAt;
    /** Player who last desecrated it. */
    @Nullable public UUID lastBy;

    public Flagstone(BlockPos origin, int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
        this.index = CryptLayout.index(cx, cz);
        this.kind = CryptLayout.kind(cx, cz);
        this.center = CryptLayout.slotCenter(origin, cx, cz).immutable();
    }

    public boolean nave() { return kind == CryptLayout.Kind.NAVE; }

    public boolean live() { return nave() && state == State.LIVE; }

    /** Ring distance from the plinth, used for the reseed spread order. */
    public int ring() { return Math.max(Math.abs(cx), Math.abs(cz)); }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("I", index);
        t.putInt("S", state.ordinal());
        t.putLong("M", matureAt);
        if (lastBy != null) t.putUUID("By", lastBy);
        return t;
    }

    public void load(CompoundTag t) {
        int s = t.getInt("S");
        state = s >= 0 && s < State.values().length ? State.values()[s] : State.LIVE;
        matureAt = t.getLong("M");
        lastBy = t.hasUUID("By") ? t.getUUID("By") : null;
    }
}
