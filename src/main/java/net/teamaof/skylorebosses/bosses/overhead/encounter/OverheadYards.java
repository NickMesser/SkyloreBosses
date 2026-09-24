package net.teamaof.skylorebosses.bosses.overhead.encounter;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.teamaof.skylorebosses.SkyloreBosses;

/**
 * Every Teknari yard in one level, persisted as SavedData (DESIGN.md §13). A level can hold several yards; each is
 * keyed by its origin. Yards whose origin chunk is not loaded (and that are not mid-fight, which force-loads them)
 * are not ticked.
 */
public final class OverheadYards extends SavedData {
    private static final String ID = "overhead_yards";
    private final Map<BlockPos, Yard> yards = new LinkedHashMap<>();

    private static Factory<OverheadYards> factory() {
        return new Factory<>(OverheadYards::new, OverheadYards::load, null);
    }

    public static OverheadYards get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), ID);
    }

    public static void tickLevel(ServerLevel level) {
        OverheadYards y = get(level);
        if (y.yards.isEmpty()) return;
        for (Yard yard : List.copyOf(y.yards.values())) {
            if (!level.isLoaded(yard.origin) && !yard.phase().fighting()) continue;
            yard.tick(level);
        }
    }

    public Collection<Yard> all() { return yards.values(); }

    @Nullable
    public Yard at(BlockPos origin) { return yards.get(origin); }

    /** @return the yard whose origin is closest to {@code pos} within {@code maxDist}, or null */
    @Nullable
    public Yard nearest(BlockPos pos, double maxDist) {
        Yard best = null;
        double bd = maxDist * maxDist;
        for (Yard y : yards.values()) {
            double d = y.origin.distSqr(pos);
            if (d <= bd) { bd = d; best = y; }
        }
        return best;
    }

    /** Register a yard at {@code origin}; with {@code build} the test yard is placed there too. */
    public Yard create(ServerLevel level, BlockPos origin, boolean build) {
        Yard old = yards.get(origin);
        if (old != null) old.reset(level, "rebuild");
        if (build) YardBuilder.build(level, origin);
        Yard y = old != null ? old : new Yard(origin);
        y.setDirtyHook(this::setDirty);
        yards.put(y.origin, y);
        y.reset(level, "created");
        setDirty();
        SkyloreBosses.LOG.info("Overhead yard registered at {}{}", origin.toShortString(), build ? " (built)" : "");
        return y;
    }

    public boolean remove(ServerLevel level, BlockPos origin) {
        Yard y = yards.remove(origin);
        if (y == null) return false;
        y.reset(level, "removed");
        setDirty();
        return true;
    }

    /** Melee hit on a pylon core. @return true if a yard owns it */
    public boolean meleePylon(ServerLevel level, BlockPos core, Player player) {
        for (Yard y : yards.values()) {
            int i = y.pylonAt(core);
            if (i >= 0) return y.meleeStrike(level, i, player);
        }
        return false;
    }

    /** Projectile hit on a pylon core (from OverheadCommonEvents). */
    public boolean projectilePylon(ServerLevel level, BlockPos core, Player player) {
        for (Yard y : yards.values()) {
            int i = y.pylonAt(core);
            if (i >= 0) {
                y.projectileHit(level, i, player);
                return true;
            }
        }
        return false;
    }

    public void onConsole(ServerLevel level, BlockPos console, ServerPlayer p) {
        Yard y = nearest(console, 64);
        if (y == null) {
            p.displayClientMessage(Component.translatable("overhead.console.unregistered"), true);
            return;
        }
        y.onConsole(level, p);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        ListTag l = new ListTag();
        for (Yard y : yards.values()) l.add(y.save());
        tag.put("Yards", l);
        return tag;
    }

    private static OverheadYards load(CompoundTag tag, HolderLookup.Provider regs) {
        OverheadYards d = new OverheadYards();
        for (Tag t : tag.getList("Yards", Tag.TAG_COMPOUND)) {
            Yard y = Yard.load((CompoundTag) t);
            y.setDirtyHook(d::setDirty);
            d.yards.put(y.origin, y);
        }
        return d;
    }

    @Override
    public boolean isDirty() {
        // yards change every tick while fighting; always save them with the level
        return true;
    }
}
