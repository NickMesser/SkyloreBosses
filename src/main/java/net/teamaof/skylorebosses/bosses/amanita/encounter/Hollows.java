package net.teamaof.skylorebosses.bosses.amanita.encounter;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.teamaof.skylorebosses.SkyloreBosses;

/**
 * Every shadow hollow in one level, persisted as SavedData (DESIGN.md §14). Each hollow is keyed by its origin. Hollows
 * whose origin chunk is not loaded (and that are not mid-fight, which force-loads them) are not ticked.
 */
public final class Hollows extends SavedData {
    private static final String ID = "amanita_hollows";
    private final Map<BlockPos, Hollow> hollows = new LinkedHashMap<>();

    private static Factory<Hollows> factory() {
        return new Factory<>(Hollows::new, Hollows::load, null);
    }

    public static Hollows get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), ID);
    }

    public static void tickLevel(ServerLevel level) {
        Hollows h = get(level);
        if (h.hollows.isEmpty()) return;
        for (Hollow hollow : List.copyOf(h.hollows.values())) {
            if (!level.isLoaded(hollow.origin) && !hollow.phase().fighting()) continue;
            hollow.tick(level);
        }
    }

    public Collection<Hollow> all() { return hollows.values(); }

    @Nullable
    public Hollow at(BlockPos origin) { return hollows.get(origin); }

    /** @return the hollow whose origin is closest to {@code pos} within {@code maxDist}, or null */
    @Nullable
    public Hollow nearest(BlockPos pos, double maxDist) {
        Hollow best = null;
        double bd = maxDist * maxDist;
        for (Hollow c : hollows.values()) {
            double d = c.origin.distSqr(pos);
            if (d <= bd) { bd = d; best = c; }
        }
        return best;
    }

    /** @return the hollow whose participant volume contains {@code pos}, or null */
    @Nullable
    public Hollow containing(BlockPos pos) {
        for (Hollow c : hollows.values()) if (HollowLayout.volume(c.origin).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return c;
        return null;
    }

    /** Register a hollow at {@code origin}; with {@code build} the test hollow is placed there too. */
    public Hollow create(ServerLevel level, BlockPos origin, boolean build) {
        Hollow old = hollows.get(origin);
        if (old != null) old.reset(level, "rebuild");
        if (build) HollowBuilder.build(level, origin);
        Hollow c = old != null ? old : new Hollow(origin);
        c.setDirtyHook(this::setDirty);
        hollows.put(c.origin, c);
        c.reset(level, "created");
        setDirty();
        SkyloreBosses.LOG.info("Amanita hollow registered at {}{}", origin.toShortString(), build ? " (built)" : "");
        return c;
    }

    public boolean remove(ServerLevel level, BlockPos origin) {
        Hollow c = hollows.remove(origin);
        if (c == null) return false;
        c.reset(level, "removed");
        setDirty();
        return true;
    }

    public void onKnocker(ServerLevel level, BlockPos knocker, ServerPlayer p) {
        Hollow c = nearest(knocker, 48);
        if (c == null) {
            p.displayClientMessage(Component.translatable("amanita.knocker.unregistered"), true);
            return;
        }
        c.onKnocker(level, p);
    }

    public void onBrazierUse(ServerLevel level, BlockPos pos, ServerPlayer p, ItemStack stack) {
        Hollow c = containing(pos);
        if (c == null) c = new Hollow(pos);   // a loose brazier outside any hollow still lights (not recorded)
        c.onBrazierUse(level, pos, p, stack);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        ListTag l = new ListTag();
        for (Hollow c : hollows.values()) l.add(c.save());
        tag.put("Hollows", l);
        return tag;
    }

    private static Hollows load(CompoundTag tag, HolderLookup.Provider regs) {
        Hollows d = new Hollows();
        for (Tag t : tag.getList("Hollows", Tag.TAG_COMPOUND)) {
            Hollow c = Hollow.load((CompoundTag) t);
            c.setDirtyHook(d::setDirty);
            d.hollows.put(c.origin, c);
        }
        return d;
    }

    @Override
    public boolean isDirty() {
        // hollows change every tick while fighting; always save them with the level
        return true;
    }
}
