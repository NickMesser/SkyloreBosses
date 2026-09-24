package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.teamaof.skylorebosses.SkyloreBosses;

/**
 * Every Church of Ender undercroft in one level, persisted as SavedData (DESIGN.md §13). Each crypt is keyed by its
 * origin. Crypts whose origin chunk is not loaded (and that are not mid-fight, which force-loads them) are not ticked.
 */
public final class Crypts extends SavedData {
    private static final String ID = "static_deacon_crypts";
    private final Map<BlockPos, Crypt> crypts = new LinkedHashMap<>();

    private static Factory<Crypts> factory() {
        return new Factory<>(Crypts::new, Crypts::load, null);
    }

    public static Crypts get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), ID);
    }

    public static void tickLevel(ServerLevel level) {
        Crypts c = get(level);
        if (c.crypts.isEmpty()) return;
        for (Crypt crypt : List.copyOf(c.crypts.values())) {
            if (!level.isLoaded(crypt.origin) && !crypt.phase().fighting()) continue;
            crypt.tick(level);
        }
    }

    public Collection<Crypt> all() { return crypts.values(); }

    @Nullable
    public Crypt at(BlockPos origin) { return crypts.get(origin); }

    /** @return the crypt whose origin is closest to {@code pos} within {@code maxDist}, or null */
    @Nullable
    public Crypt nearest(BlockPos pos, double maxDist) {
        Crypt best = null;
        double bd = maxDist * maxDist;
        for (Crypt c : crypts.values()) {
            double d = c.origin.distSqr(pos);
            if (d <= bd) { bd = d; best = c; }
        }
        return best;
    }

    /** @return the crypt whose floor contains {@code pos} (x/z within the walls, y near the floor), or null */
    @Nullable
    public Crypt containing(BlockPos pos) {
        for (Crypt c : crypts.values()) if (CryptLayout.volume(c.origin).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return c;
        return null;
    }

    /** Register a crypt at {@code origin}; with {@code build} the test undercroft is placed there too. */
    public Crypt create(ServerLevel level, BlockPos origin, boolean build) {
        Crypt old = crypts.get(origin);
        if (old != null) old.reset(level, "rebuild");
        if (build) CryptBuilder.build(level, origin);
        Crypt c = old != null ? old : new Crypt(origin);
        c.setDirtyHook(this::setDirty);
        crypts.put(c.origin, c);
        c.reset(level, "created");
        setDirty();
        SkyloreBosses.LOG.info("Static Deacon crypt registered at {}{}", origin.toShortString(), build ? " (built)" : "");
        return c;
    }

    public boolean remove(ServerLevel level, BlockPos origin) {
        Crypt c = crypts.remove(origin);
        if (c == null) return false;
        c.reset(level, "removed");
        setDirty();
        return true;
    }

    public void onBell(ServerLevel level, BlockPos bell, ServerPlayer p) {
        Crypt c = nearest(bell, 48);
        if (c == null) {
            p.displayClientMessage(Component.translatable("static_deacon.bell.unregistered"), true);
            return;
        }
        c.onBell(level, p);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        ListTag l = new ListTag();
        for (Crypt c : crypts.values()) l.add(c.save());
        tag.put("Crypts", l);
        return tag;
    }

    private static Crypts load(CompoundTag tag, HolderLookup.Provider regs) {
        Crypts d = new Crypts();
        for (Tag t : tag.getList("Crypts", Tag.TAG_COMPOUND)) {
            Crypt c = Crypt.load((CompoundTag) t);
            c.setDirtyHook(d::setDirty);
            d.crypts.put(c.origin, c);
        }
        return d;
    }

    @Override
    public boolean isDirty() {
        // crypts change every tick while fighting; always save them with the level
        return true;
    }
}
