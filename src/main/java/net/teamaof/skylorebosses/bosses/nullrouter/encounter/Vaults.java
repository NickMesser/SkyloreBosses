package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

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
 * Every Automaton vault in one level, persisted as SavedData (DESIGN.md §13). Each vault is keyed by its origin.
 * Vaults whose origin chunk is not loaded (and that are not mid-fight, which force-loads them) are not ticked.
 */
public final class Vaults extends SavedData {
    private static final String ID = "null_router_vaults";
    private final Map<BlockPos, Vault> vaults = new LinkedHashMap<>();

    private static Factory<Vaults> factory() {
        return new Factory<>(Vaults::new, Vaults::load, null);
    }

    public static Vaults get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), ID);
    }

    public static void tickLevel(ServerLevel level) {
        Vaults v = get(level);
        if (v.vaults.isEmpty()) return;
        for (Vault vault : List.copyOf(v.vaults.values())) {
            if (!level.isLoaded(vault.origin) && !vault.phase().fighting()) continue;
            vault.tick(level);
        }
    }

    public Collection<Vault> all() { return vaults.values(); }

    @Nullable
    public Vault at(BlockPos origin) { return vaults.get(origin); }

    /** @return the vault whose origin is closest to {@code pos} within {@code maxDist}, or null */
    @Nullable
    public Vault nearest(BlockPos pos, double maxDist) {
        Vault best = null;
        double bd = maxDist * maxDist;
        for (Vault v : vaults.values()) {
            double d = v.origin.distSqr(pos);
            if (d <= bd) { bd = d; best = v; }
        }
        return best;
    }

    /** @return the vault whose participant volume contains {@code pos}, or null */
    @Nullable
    public Vault containing(BlockPos pos) {
        for (Vault v : vaults.values()) if (VaultLayout.volume(v.origin).contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) return v;
        return null;
    }

    /** Register a vault at {@code origin}; with {@code build} the test vault is placed there too. */
    public Vault create(ServerLevel level, BlockPos origin, boolean build) {
        Vault old = vaults.get(origin);
        if (old != null) old.reset(level, "rebuild");
        if (build) VaultBuilder.build(level, origin);
        Vault v = old != null ? old : new Vault(origin);
        v.setDirtyHook(this::setDirty);
        vaults.put(v.origin, v);
        v.reset(level, "created");
        setDirty();
        SkyloreBosses.LOG.info("Null Router vault registered at {}{}", origin.toShortString(), build ? " (built)" : "");
        return v;
    }

    public boolean remove(ServerLevel level, BlockPos origin) {
        Vault v = vaults.remove(origin);
        if (v == null) return false;
        v.reset(level, "removed");
        setDirty();
        return true;
    }

    public void onConsoleUse(ServerLevel level, BlockPos console, ServerPlayer p) {
        for (Vault v : vaults.values()) if (v.onConsoleUse(level, console, p)) return;
        p.displayClientMessage(Component.translatable("null_router.console.unregistered"), true);
    }

    public void onTerminal(ServerLevel level, BlockPos terminal, ServerPlayer p) {
        Vault v = nearest(terminal, 48);
        if (v == null) {
            p.displayClientMessage(Component.translatable("null_router.terminal.unregistered"), true);
            return;
        }
        v.onTerminal(level, p);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        ListTag l = new ListTag();
        for (Vault v : vaults.values()) l.add(v.save());
        tag.put("Vaults", l);
        return tag;
    }

    private static Vaults load(CompoundTag tag, HolderLookup.Provider regs) {
        Vaults d = new Vaults();
        for (Tag t : tag.getList("Vaults", Tag.TAG_COMPOUND)) {
            Vault v = Vault.load((CompoundTag) t);
            v.setDirtyHook(d::setDirty);
            d.vaults.put(v.origin, v);
        }
        return d;
    }

    @Override
    public boolean isDirty() {
        // vaults change every tick while fighting; always save them with the level
        return true;
    }
}
