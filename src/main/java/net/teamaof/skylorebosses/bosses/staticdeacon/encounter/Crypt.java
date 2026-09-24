package net.teamaof.skylorebosses.bosses.staticdeacon.encounter;

import dev.architectury.event.EventResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.staticdeacon.DeaconConfig;
import net.teamaof.skylorebosses.bosses.staticdeacon.StaticDeaconBoss;
import net.teamaof.skylorebosses.bosses.staticdeacon.api.DeaconEvents;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.DeaconAction;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.StaticDeaconEntity;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconBlocks;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconEntities;
import net.teamaof.skylorebosses.bosses.staticdeacon.registry.DeaconItems;
import net.teamaof.skylorebosses.core.api.BossEvents;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.fx.Telegraph;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;

/**
 * One Church of Ender undercroft and the fight in it (DESIGN.md §4, §6, §7). Persisted inside {@link Crypts}, so a
 * fight survives relogs, restarts and chunk unloads: the Deacon and the floor blocks are views of this state and are
 * re-created or re-checked from it.
 */
public final class Crypt {
    /** Blocks that count as intact heal substrate in a live flagstone. */
    public static final TagKey<Block> SUBSTRATE = TagKey.create(Registries.BLOCK, SkyloreBosses.id("static_deacon/communion_substrate"));
    /** Blocks nave_shatter may break (the crypt replaces them). */
    public static final TagKey<Block> BREAKABLE_COVER = TagKey.create(Registries.BLOCK, SkyloreBosses.id("static_deacon/breakable_cover"));

    /** What the Deacon is standing on. */
    public enum Commune { NONE, NAVE, PLINTH }

    public final BlockPos origin;
    private Phase phase = Phase.DORMANT;
    private int phaseTicks;
    private long fightTicks;
    private final Flagstone[] slots = new Flagstone[CryptLayout.SLOTS.size()];
    private final int naveTotal;
    private int exposeTicks, reseedCount, reseedTarget, deaths, desecrations, vigilBreaks, denials, riteStepIn;
    private boolean anyReseed, forced, firstFlagstoneAwarded, firstVigilAwarded;
    private final List<Integer> schedule = new ArrayList<>();
    private int scheduleCursor;
    @Nullable private UUID deaconId;
    private float deaconHp;
    private int deaconMissing, idleTicks;
    private long clearedAt;
    private final Set<UUID> participantsEver = new HashSet<>();
    private final Map<BlockPos, Long> coverRegen = new HashMap<>();
    private final transient DeaconBossBar bar = new DeaconBossBar();
    private transient Runnable dirty = () -> {};
    private final transient Map<UUID, Long> hintAt = new HashMap<>();

    public Crypt(BlockPos origin) {
        this.origin = origin.immutable();
        int n = 0;
        for (int[] s : CryptLayout.SLOTS) {
            Flagstone f = new Flagstone(this.origin, s[0], s[1]);
            slots[f.index] = f;
            if (f.nave()) n++;
        }
        naveTotal = n;
    }

    void setDirtyHook(Runnable r) { dirty = r; }

    private void setDirty() { dirty.run(); }

    public Phase phase() { return phase; }
    public int phaseTicks() { return phaseTicks; }
    public int exposeTicks() { return exposeTicks; }
    public int reseedCount() { return reseedCount; }
    public int naveTotal() { return naveTotal; }
    public Flagstone slot(int cx, int cz) { return slots[CryptLayout.index(cx, cz)]; }
    public Flagstone slot(int index) { return slots[index]; }
    public int slotCount() { return slots.length; }

    public int liveNave() {
        int n = 0;
        for (Flagstone f : slots) if (f.live()) n++;
        return n;
    }

    private int reseeding() {
        int n = 0;
        for (Flagstone f : slots) if (f.nave() && f.state == Flagstone.State.RESEEDING) n++;
        return n;
    }

    /** Coverage C in [0, 1]: live nave flagstones / all nave flagstones. */
    public float coverage() { return naveTotal == 0 ? 0 : liveNave() / (float) naveTotal; }

    @Nullable
    public Flagstone flagstoneAt(BlockPos floorBlock) {
        if (floorBlock.getY() != origin.getY()) return null;
        int[] s = CryptLayout.slotAt(origin, floorBlock.getX(), floorBlock.getZ());
        return s == null ? null : slot(s[0], s[1]);
    }

    /** The live nave flagstones, for the Deacon's stance choice and telegraphs. */
    public List<Flagstone> liveFlagstones() {
        List<Flagstone> out = new ArrayList<>();
        for (Flagstone f : slots) if (f.live()) out.add(f);
        return out;
    }

    // ================================================================== communion (DESIGN.md §6, §7)
    public Commune commune(Entity e) {
        Vec3 p = e.position();
        if (CryptLayout.onPlinth(origin, p)) return Commune.PLINTH;
        BlockPos below = BlockPos.containing(p.x, p.y - 0.2, p.z);
        Flagstone f = flagstoneAt(below);
        return f != null && f.live() && p.y - (origin.getY() + 1) < 0.6 ? Commune.NAVE : Commune.NONE;
    }

    /** Fraction of incoming damage the Deacon ignores. */
    public float damageReduction(StaticDeaconEntity d) {
        return switch (phase) {
            case P0_VESTING, DORMANT, CLEARED -> 1f;
            case DEFEATED -> 0f;
            default -> {
                if (d.channeling()) yield DeaconConfig.RITE_DR.get().floatValue();
                yield switch (commune(d)) {
                    case NAVE -> (float) (DeaconConfig.NAVE_DR.get() * coverage());
                    case PLINTH -> DeaconConfig.PLINTH_DR.get().floatValue();
                    case NONE -> 0f;
                };
            }
        };
    }

    /** Fraction of max HP regenerated per second right now. */
    public double regenPerSecond(StaticDeaconEntity d) {
        if (!phase.fighting() || phase == Phase.P0_VESTING) return 0;
        return switch (commune(d)) {
            case NAVE -> DeaconConfig.NAVE_REGEN.get() * coverage();
            case PLINTH -> DeaconConfig.PLINTH_REGEN.get();
            case NONE -> 0;
        };
    }

    // ================================================================== lifecycle
    /** Try to start from DORMANT. The pack may veto through {@link DeaconEvents#START_CHECK}. */
    public boolean tryStart(ServerLevel level, @Nullable ServerPlayer trigger) {
        if (phase != Phase.DORMANT) return false;
        if (trigger != null) {
            EventResult r = DeaconEvents.START_CHECK.invoker().check(level, origin, trigger);
            if (r.isFalse()) {
                trigger.displayClientMessage(Component.translatable("static_deacon.bell.denied").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
        }
        List<ServerPlayer> ps = participants(level);
        fightTicks = 0;
        reseedCount = 0;
        reseedTarget = 0;
        anyReseed = false;
        deaths = 0;
        desecrations = 0;
        vigilBreaks = 0;
        denials = 0;
        exposeTicks = 0;
        schedule.clear();
        scheduleCursor = 0;
        firstFlagstoneAwarded = false;
        firstVigilAwarded = false;
        participantsEver.clear();
        deaconHp = 0;
        restoreAll(level);
        setPhase(level, Phase.P0_VESTING);
        forceChunks(level, true);
        CryptBuilder.closeDoor(level, origin);
        spawnDeacon(level, true);
        for (ServerPlayer p : ps) {
            title(p, "static_deacon.title.vesting", "static_deacon.title.vesting.sub");
            award(p, "enter_crypt");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 20);
        }
        AnimFx.play(level, Vec3.atCenterOf(origin), "static_deacon.lockdown", 12f, 1f);
        DeaconEvents.ENCOUNTER_STARTED.invoker().started(level, origin, ps);
        BossEvents.STARTED.invoker().started(level, StaticDeaconBoss.ID, origin, ps);
        return true;
    }

    public void reset(ServerLevel level, String reason) {
        StaticDeaconEntity d = deacon(level);
        if (d != null) d.discard();
        deaconId = null;
        exposeTicks = 0;
        schedule.clear();
        bar.clear();
        restoreAll(level);
        CryptBuilder.openDoor(level, origin);
        forceChunks(level, false);
        setPhase(level, Phase.DORMANT);
        DeaconEvents.RESET.invoker().reset(level, origin, reason);
    }

    /** Floor, pews and plinth back to a fresh crypt. */
    private void restoreAll(ServerLevel level) {
        for (Flagstone f : slots) {
            f.state = Flagstone.State.LIVE;
            f.lastBy = null;
        }
        if (level.isLoaded(origin)) {
            CryptBuilder.restoreFloor(level, origin);
            CryptBuilder.restoreCover(level, origin);
            if (!CryptBuilder.plinthIntact(level, origin)) CryptBuilder.placePlinth(level, origin);
        }
        coverRegen.clear();
    }

    private void setPhase(ServerLevel level, Phase to) {
        Phase from = phase;
        phase = to;
        phaseTicks = 0;
        setDirty();
        if (from != to) {
            SkyloreBosses.LOG.info("Static Deacon crypt {}: {} -> {}", origin.toShortString(), from, to);
            DeaconEvents.PHASE_CHANGED.invoker().changed(level, origin, from, to);
        }
    }

    // ================================================================== tick
    void tick(ServerLevel level) {
        phaseTicks++;
        long now = level.getGameTime();
        switch (phase) {
            case DORMANT -> {
                if (phaseTicks % 10 == 0) {
                    List<ServerPlayer> in = level.getEntitiesOfClass(ServerPlayer.class, CryptLayout.trigger(origin), p -> !p.isSpectator());
                    if (!in.isEmpty()) tryStart(level, in.get(0));
                }
                return;
            }
            case CLEARED -> {
                if (DeaconConfig.REMATCH.get() && now - clearedAt > DeaconConfig.REMATCH_DELAY_TICKS.get()) reset(level, "rematch");
                return;
            }
            case DEFEATED -> {
                if (phaseTicks == 160) {
                    CryptBuilder.openDoor(level, origin);
                    bar.clear();
                    forceChunks(level, false);
                    clearedAt = now;
                    setPhase(level, Phase.CLEARED);
                }
                return;
            }
            default -> {}
        }
        // ---- fighting
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, CryptLayout.fallZone(origin), p -> !p.isSpectator() && p.isAlive()))
            rescue(level, p);
        List<ServerPlayer> ps = participants(level);
        if (ps.isEmpty()) {
            if (++idleTicks > DeaconConfig.DORMANT_TIMEOUT_TICKS.get()) {
                idleTicks = 0;
                SkyloreBosses.LOG.info("Static Deacon crypt {} reset: no participants", origin.toShortString());
                reset(level, "abandoned");
            }
            return;
        }
        idleTicks = 0;
        fightTicks++;
        for (ServerPlayer p : ps) participantsEver.add(p.getUUID());
        StaticDeaconEntity d = deacon(level);
        if (d == null) {
            if (++deaconMissing >= 40) spawnDeacon(level, false);
        } else {
            deaconMissing = 0;
            if (phaseTicks % 20 == 0) deaconHp = d.getHealth();
        }
        if (phaseTicks % 10 == 0) scanFloor(level);
        tickReseeding(level, now);
        switch (phase) {
            case P0_VESTING -> tickVesting(level, d, ps);
            case P3_VIGIL -> {
                if (--exposeTicks <= 0) enterReseed(level, d);
            }
            case P4_RESEED -> tickReseedPhase(level);
            default -> {}
        }
        if (phaseTicks % 10 == 0 && d != null) tickRegen(level, d);
        if (phaseTicks % 10 == 0) keepPlinthClear(level);
        if (phaseTicks % 40 == 0 && !CryptBuilder.plinthIntact(level, origin)) CryptBuilder.placePlinth(level, origin);
        if (phaseTicks % 20 == 0) tickCover(level, now);
        if (phaseTicks % 5 == 0) updateBar(d);
        if (phaseTicks % 20 == 0) bar.syncPlayers(ps);
    }

    private void tickVesting(ServerLevel level, @Nullable StaticDeaconEntity d, List<ServerPlayer> ps) {
        // communion comes online ring by ring out from the plinth
        if (phaseTicks >= 20 && phaseTicks <= 100 && phaseTicks % 16 == 4) {
            int ring = 1 + (phaseTicks - 20) / 16;
            for (Flagstone f : slots) {
                if (!f.live() || f.ring() != ring) continue;
                Vec3 c = Vec3.atCenterOf(f.center).add(0, 0.6, 0);
                AnimFx.serverBurst(level, SBParticles.get("communion_mote"), c, 8, 0.9, 0.02);
            }
            AnimFx.play(level, Vec3.atCenterOf(origin), "static_deacon.flagstone.live", 6f, 0.8f + ring * 0.1f);
        }
        if (phaseTicks == 120 && d != null) d.script(DeaconAction.VESTING_CHIME);
        if (phaseTicks >= 200) {
            for (ServerPlayer p : ps) p.displayClientMessage(Component.translatable("static_deacon.log.p1", liveNave(), naveTotal).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            setPhase(level, Phase.P1_COMMUNION);
            recomputePhase(level);
        }
    }

    /** Re-evaluate P1/P2/P3 after the floor changes. */
    private void recomputePhase(ServerLevel level) {
        if (phase != Phase.P1_COMMUNION && phase != Phase.P2_PATCHWORK && phase != Phase.P3_VIGIL) return;
        int live = liveNave();
        float c = coverage();
        StaticDeaconEntity d = deacon(level);
        if (phase == Phase.P3_VIGIL) {
            // a reseed that matured after the rite ended brings patchwork back
            if (live > 0) {
                if (d != null) d.resetForPhase();
                setPhase(level, Phase.P2_PATCHWORK);
            }
            return;
        }
        if (live == 0) {
            exposeTicks = DeaconConfig.EXPOSE_TICKS.get();
            if (d != null) d.resetForPhase();
            setPhase(level, Phase.P3_VIGIL);
            for (ServerPlayer p : participants(level)) {
                title(p, "static_deacon.title.vigil", "static_deacon.title.vigil.sub");
                if (!anyReseed) award(p, "stranded");
            }
            AnimFx.play(level, Vec3.atCenterOf(origin), "static_deacon.vigil.begin", 10f, 1f);
        } else if (phase == Phase.P1_COMMUNION && c < DeaconConfig.P2_THRESHOLD.get()) {
            setPhase(level, Phase.P2_PATCHWORK);
            for (ServerPlayer p : participants(level))
                p.displayClientMessage(Component.translatable("static_deacon.log.p2", live, naveTotal).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else if (phase == Phase.P2_PATCHWORK && c >= DeaconConfig.P2_THRESHOLD.get()) {
            setPhase(level, Phase.P1_COMMUNION);
        }
    }

    // ================================================================== reseed (P4)
    private void enterReseed(ServerLevel level, @Nullable StaticDeaconEntity d) {
        reseedCount++;
        anyReseed = true;
        reseedTarget = Math.min(Math.min(naveTotal, DeaconConfig.RESEED_MAX.get()),
                DeaconConfig.RESEED_BASE.get() + DeaconConfig.RESEED_PER_RITE.get() * (reseedCount - 1));
        // spread out from the plinth: nearest ring first, shuffled within a ring
        List<Flagstone> dead = new ArrayList<>();
        for (Flagstone f : slots) if (f.nave() && f.state == Flagstone.State.DESECRATED) dead.add(f);
        java.util.Collections.shuffle(dead, new Random(level.getGameTime()));
        dead.sort(Comparator.comparingInt(Flagstone::ring));
        int n = (int) Math.ceil(reseedTarget * (1 + DeaconConfig.RESEED_SLACK.get()));
        schedule.clear();
        for (int i = 0; i < Math.min(n, dead.size()); i++) schedule.add(dead.get(i).index);
        scheduleCursor = 0;
        riteStepIn = 0;
        setPhase(level, Phase.P4_RESEED);
        if (d != null) {
            d.resetForPhase();
            d.script(DeaconAction.RESEED_RITE);
        }
        for (ServerPlayer p : participants(level)) {
            title(p, "static_deacon.title.reseed", "static_deacon.title.reseed.sub");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 15);
        }
        AnimFx.play(level, Vec3.atCenterOf(origin), "static_deacon.reseed.toll", 16f, 1f);
        DeaconEvents.RESEED_STARTED.invoker().started(level, origin, reseedCount, reseedTarget);
    }

    private void tickReseedPhase(ServerLevel level) {
        int live = liveNave();
        boolean exhausted = scheduleCursor >= schedule.size() && reseeding() == 0;
        if (live >= reseedTarget || exhausted || phaseTicks >= DeaconConfig.RESEED_TIMEOUT_TICKS.get()) finishReseed(level);
    }

    private void finishReseed(ServerLevel level) {
        StaticDeaconEntity d = deacon(level);
        if (d != null) d.resetForPhase();
        int live = liveNave();
        if (live == 0) {
            exposeTicks = DeaconConfig.RESEED_ABORT_EXPOSE_TICKS.get();
            setPhase(level, Phase.P3_VIGIL);
            for (ServerPlayer p : participants(level)) title(p, "static_deacon.title.rite_denied", "static_deacon.title.rite_denied.sub");
        } else {
            setPhase(level, coverage() >= DeaconConfig.P2_THRESHOLD.get() ? Phase.P1_COMMUNION : Phase.P2_PATCHWORK);
            for (ServerPlayer p : participants(level))
                p.displayClientMessage(Component.translatable("static_deacon.log.reseed_done", live, naveTotal).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    /**
     * Called every tick while the Deacon channels the rite. Lays the next scheduled flagstone every stepTicks.
     * @return false once the rite has nothing left to do (schedule exhausted or target reached)
     */
    public boolean riteStep(ServerLevel level) {
        if (phase != Phase.P4_RESEED || liveNave() >= reseedTarget) return false;
        if (--riteStepIn > 0) return true;
        riteStepIn = DeaconConfig.RESEED_STEP_TICKS.get();
        while (scheduleCursor < schedule.size()) {
            Flagstone f = slots[schedule.get(scheduleCursor++)];
            if (f.state != Flagstone.State.DESECRATED) continue;
            beginReseed(level, f);
            return true;
        }
        return false;
    }

    public boolean riteHasWork() {
        return phase == Phase.P4_RESEED && scheduleCursor < schedule.size() && liveNave() < reseedTarget;
    }

    private void beginReseed(ServerLevel level, Flagstone f) {
        f.state = Flagstone.State.RESEEDING;
        f.matureAt = level.getGameTime() + DeaconConfig.RESEED_GROW_TICKS.get();
        CryptBuilder.consecrate(level, origin, f.cx, f.cz, false);
        Vec3 c = Vec3.atCenterOf(f.center).add(0, 0.6, 0);
        AnimFx.serverBurst(level, SBParticles.get("communion_mote"), c, 24, 1.0, 0.05);
        AnimFx.play(level, c, "static_deacon.flagstone.reseed", 4f, 0.9f + level.random.nextFloat() * 0.2f);
        setDirty();
    }

    private void tickReseeding(ServerLevel level, long now) {
        boolean changed = false;
        for (Flagstone f : slots) {
            if (!f.nave() || f.state != Flagstone.State.RESEEDING) continue;
            if (now % 4 == 0) Telegraph.ring(level, SBParticles.get("communion_mote"), Vec3.atBottomCenterOf(f.center).add(0, 1, 0), 1.4, 8);
            if (now < f.matureAt) continue;
            if (intact(level, f)) {
                f.state = Flagstone.State.LIVE;
                light(level, f, true);
                AnimFx.play(level, Vec3.atCenterOf(f.center), "static_deacon.flagstone.live", 4f, 1.1f);
            } else {
                desecrate(level, f, null, "scan");
            }
            changed = true;
        }
        if (changed) {
            setDirty();
            recomputePhase(level);
        }
    }

    // ================================================================== floor (DESIGN.md §6)
    /** A flagstone is intact while all nine blocks are substrate with nothing solid on top. */
    public boolean intact(ServerLevel level, Flagstone f) {
        for (BlockPos p : CryptLayout.slotBlocks(origin, f.cx, f.cz)) {
            if (!level.getBlockState(p).is(SUBSTRATE)) return false;
            BlockPos a = p.above();
            if (!level.getBlockState(a).getCollisionShape(level, a).isEmpty()) return false;
        }
        return true;
    }

    /** Catch-all integrity check (pistons, fluids-turned-solid, Create drills, /setblock...). */
    private void scanFloor(ServerLevel level) {
        for (Flagstone f : slots) {
            if (!f.nave() || f.state == Flagstone.State.DESECRATED) continue;
            if (!level.isLoaded(f.center)) continue;
            if (!intact(level, f)) desecrate(level, f, null, "scan");
        }
    }

    private void light(ServerLevel level, Flagstone f, boolean lit) {
        for (BlockPos p : CryptLayout.slotBlocks(origin, f.cx, f.cz)) {
            BlockState s = level.getBlockState(p);
            if (s.is(DeaconBlocks.CONSECRATED_ENDSTONE.get()) && s.getValue(net.teamaof.skylorebosses.bosses.staticdeacon.block.ConsecratedEndstoneBlock.LIT) != lit)
                level.setBlock(p, s.setValue(net.teamaof.skylorebosses.bosses.staticdeacon.block.ConsecratedEndstoneBlock.LIT, lit), 3);
        }
    }

    /** Kill a flagstone: its remaining endstone turns to desecrated rubble. */
    public void desecrate(ServerLevel level, Flagstone f, @Nullable Player by, String cause) {
        if (!f.nave() || f.state == Flagstone.State.DESECRATED) return;
        boolean wasReseeding = f.state == Flagstone.State.RESEEDING;
        f.state = Flagstone.State.DESECRATED;
        f.lastBy = by == null ? null : by.getUUID();
        BlockState rubble = DeaconBlocks.DESECRATED_ENDSTONE.get().defaultBlockState();
        for (BlockPos p : CryptLayout.slotBlocks(origin, f.cx, f.cz))
            if (level.getBlockState(p).is(DeaconBlocks.CONSECRATED_ENDSTONE.get())) level.setBlock(p, rubble, 3);
        Vec3 c = Vec3.atCenterOf(f.center).add(0, 0.6, 0);
        AnimFx.serverBurst(level, SBParticles.get("static_dust"), c, 40, 1.0, 0.08);
        AnimFx.play(level, c, "static_deacon.flagstone.desecrate", 6f, 0.9f + level.random.nextFloat() * 0.2f);
        setDirty();
        if (!phase.fighting()) return;
        desecrations++;
        if (wasReseeding) denials++;
        int live = liveNave();
        StaticDeaconEntity d = deacon(level);
        if (by instanceof ServerPlayer sp) {
            if (d != null) d.addThreat(sp.getUUID(), DeaconConfig.DESECRATION_THREAT.get().floatValue());
            sp.displayClientMessage(Component.translatable(wasReseeding ? "static_deacon.log.denied" : "static_deacon.log.desecrated", live, naveTotal)
                    .withStyle(ChatFormatting.GOLD), true);
        }
        if (!firstFlagstoneAwarded) {
            firstFlagstoneAwarded = true;
            for (ServerPlayer p : participants(level)) award(p, "first_flagstone");
        }
        DeaconEvents.FLAGSTONE_DESECRATED.invoker().desecrated(level, origin, f.index, by instanceof ServerPlayer sp ? sp : null, cause, live);
        if (d != null) d.onFloorLost(f);
        recomputePhase(level);
    }

    /** BlockEvent.BreakEvent: a player broke a floor block. */
    public void onBlockBroken(ServerLevel level, BlockPos pos, Player player) {
        Flagstone f = flagstoneAt(pos);
        if (f != null && f.nave() && f.state != Flagstone.State.DESECRATED) desecrate(level, f, player, "mined");
    }

    /** BlockEvent.EntityPlaceEvent. @return true to cancel the placement */
    public boolean onBlockPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Player player) {
        if (phase.fighting() && CryptLayout.plinthColumn(origin).contains(Vec3.atCenterOf(pos))) {
            if (player instanceof ServerPlayer sp)
                sp.displayClientMessage(Component.translatable("static_deacon.log.plinth_refused").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        if (pos.getY() == origin.getY() + 1) {
            Flagstone f = flagstoneAt(pos.below());
            if (f != null && f.nave() && f.state != Flagstone.State.DESECRATED && !placed.getCollisionShape(level, pos).isEmpty())
                desecrate(level, f, player, "covered");
        } else if (pos.getY() == origin.getY() && player instanceof ServerPlayer sp && phase.fighting()) {
            Flagstone f = flagstoneAt(pos);
            if (f != null && f.nave() && f.state == Flagstone.State.DESECRATED && hint(sp, 200))
                sp.displayClientMessage(Component.translatable("static_deacon.log.unconsecrated").withStyle(ChatFormatting.GRAY), true);
        }
        return false;
    }

    /** ExplosionEvent.Detonate: every live floor block in the blast desecrates its flagstone. */
    public void onExplosion(ServerLevel level, List<BlockPos> affected, @Nullable Player by) {
        for (BlockPos p : affected) {
            Flagstone f = flagstoneAt(p);
            if (f != null && f.nave() && f.state != Flagstone.State.DESECRATED) desecrate(level, f, by, "explosion");
        }
    }

    private void keepPlinthClear(ServerLevel level) {
        AABB col = CryptLayout.plinthColumn(origin);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(col.minX, col.minY, col.minZ), BlockPos.containing(col.maxX - 1, col.maxY - 1, col.maxZ - 1))) {
            if (!level.getBlockState(p).isAir()) level.destroyBlock(p, true);
        }
    }

    // ================================================================== regen
    private void tickRegen(ServerLevel level, StaticDeaconEntity d) {
        if (d.isDeadOrDying()) return;
        double rate = regenPerSecond(d);
        if (rate <= 0 || d.getHealth() >= d.getMaxHealth()) return;
        d.heal((float) (d.getMaxHealth() * rate / 2));   // every 10 ticks
        Vec3 at = d.position().add(0, 0.2, 0);
        AnimFx.serverBurst(level, SBParticles.get("communion_mote"), at, 3, 0.5, 0.03);
        deaconHp = d.getHealth();
    }

    // ================================================================== vigil
    /** The entity reports a vigil (or rite) break; the crypt records it and fires the event. */
    public void onVigilBroken(ServerLevel level, @Nullable ServerPlayer breaker, boolean rite) {
        vigilBreaks++;
        for (ServerPlayer p : participants(level)) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(3, 30, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(rite ? "static_deacon.log.rite_broken" : "static_deacon.log.vigil_broken")
                    .withStyle(ChatFormatting.GOLD)));
            if (!firstVigilAwarded) award(p, "vigil_broken");
        }
        firstVigilAwarded = true;
        DeaconEvents.VIGIL_BROKEN.invoker().broken(level, origin, breaker, rite);
        setDirty();
    }

    /** First-contact teaching lines, at most one per player per {@code cooldown} ticks. */
    public boolean hint(ServerPlayer p, int cooldown) {
        long now = p.level().getGameTime();
        if (hintAt.getOrDefault(p.getUUID(), 0L) > now) return false;
        hintAt.put(p.getUUID(), now + cooldown);
        return true;
    }

    // ================================================================== cover
    private void tickCover(ServerLevel level, long now) {
        for (BlockPos p : CryptLayout.pewBlocks(origin)) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).isAir()) coverRegen.putIfAbsent(p.immutable(), now + DeaconConfig.COVER_REGEN_TICKS.get());
        }
        coverRegen.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            BlockPos p = e.getKey();
            if (!level.getBlockState(p).isAir()) return true;
            if (!level.getEntitiesOfClass(Entity.class, new AABB(p), x -> x instanceof Player || x instanceof StaticDeaconEntity).isEmpty()) return false;
            level.setBlock(p, CryptBuilder.pew(origin, p), 3);
            AnimFx.serverBurst(level, SBParticles.get("static_dust"), Vec3.atCenterOf(p), 8, 0.4, 0.02);
            return true;
        });
    }

    // ================================================================== deacon
    @Nullable
    public StaticDeaconEntity deacon(ServerLevel level) {
        if (deaconId == null) return null;
        Entity e = level.getEntity(deaconId);
        return e instanceof StaticDeaconEntity d && d.isAlive() ? d : null;
    }

    private float maxHp(ServerLevel level) {
        int n = Math.max(1, participants(level).size());
        return (float) (DeaconConfig.HP.get() * Math.min(DeaconConfig.MP_HP_CAP.get(), 1 + DeaconConfig.MP_HP_PER_PLAYER.get() * (n - 1)));
    }

    private void spawnDeacon(ServerLevel level, boolean fresh) {
        Entity old = deaconId == null ? null : level.getEntity(deaconId);
        if (old != null) old.discard();
        StaticDeaconEntity d = DeaconEntities.STATIC_DEACON.get().create(level);
        if (d == null) return;
        Vec3 at = CryptLayout.plinthStand(origin);
        d.moveTo(at.x, at.y, at.z, 0, 0);
        d.bindCrypt(origin);
        float max = maxHp(level);
        d.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        d.setHealth(!fresh && deaconHp > 0 ? Math.min(max, deaconHp) : max);
        d.finalizeSpawn(level, level.getCurrentDifficultyAt(origin), MobSpawnType.EVENT, null);
        level.addFreshEntity(d);
        if (fresh) d.triggerAnim("main", "vesting");
        else AnimFx.serverBurst(level, SBParticles.get("static_dust"), at.add(0, 1.5, 0), 60, 0.8, 0.1);
        deaconId = d.getUUID();
        deaconHp = d.getHealth();
        deaconMissing = 0;
        setDirty();
    }

    public void onDeaconDamaged(StaticDeaconEntity d) {
        deaconHp = d.getHealth();
    }

    public void onDeaconDied(ServerLevel level, StaticDeaconEntity d, DamageSource source) {
        if (!d.getUUID().equals(deaconId) || !phase.fighting()) return;
        deaconHp = 0;
        List<ServerPlayer> ps = participants(level);
        setPhase(level, Phase.DEFEATED);
        bar.update(0, coverage(), BossEvent.BossBarColor.WHITE, Component.translatable("static_deacon.status.down"),
                Component.translatable("static_deacon.bossbar.floor_count", liveNave(), naveTotal));
        String fn = DeaconConfig.VICTORY_FUNCTION.get();
        for (ServerPlayer p : ps) {
            award(p, "deacon_silenced");
            if (!anyReseed) award(p, "no_reseed");
            p.getInventory().placeItemBackInInventory(new ItemStack(DeaconItems.STATIC_THURIBLE.get()));
            title(p, "static_deacon.title.victory", "static_deacon.title.victory.sub");
            AnimFx.play(level, p.position(), "static_deacon.victory", 1f, 1f);
            if (!fn.isEmpty()) {
                var server = level.getServer();
                server.getFunctions().get(ResourceLocation.parse(fn)).ifPresent(f ->
                        server.getFunctions().execute(f, p.createCommandSourceStack().withSuppressedOutput().withPermission(2)));
            }
        }
        DeaconEvents.VICTORY.invoker().victory(level, origin, ps,
                new DeaconEvents.EncounterStats(fightTicks, reseedCount, deaths, desecrations, vigilBreaks, anyReseed));
        BossEvents.DEFEATED.invoker().defeated(level, StaticDeaconBoss.ID, origin, ps);
    }

    public void onPlayerDeath(ServerPlayer p) {
        if (phase.fighting() && CryptLayout.volume(origin).contains(p.position())) deaths++;
    }

    // ================================================================== players
    public List<ServerPlayer> participants(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, CryptLayout.volume(origin), p -> !p.isSpectator() && p.isAlive());
    }

    private void rescue(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(CryptLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.fallDistance = 0;
        p.hurt(level.damageSources().fellOutOfWorld(), 4f);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
        p.displayClientMessage(Component.translatable("static_deacon.log.rescue").withStyle(ChatFormatting.GRAY), true);
    }

    /** Bell right-click. */
    public void onBell(ServerLevel level, ServerPlayer p) {
        AnimFx.play(level, p.position(), "static_deacon.bell", 3f, 1f);
        switch (phase) {
            case DORMANT -> {
                if (tryStart(level, p)) readmit(level, p);
            }
            case CLEARED, DEFEATED -> p.displayClientMessage(Component.translatable("static_deacon.bell.cleared").withStyle(ChatFormatting.GRAY), true);
            default -> readmit(level, p);
        }
    }

    private void readmit(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(CryptLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.displayClientMessage(Component.translatable("static_deacon.bell.readmit").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ================================================================== bar
    private void updateBar(@Nullable StaticDeaconEntity d) {
        float hp = d == null ? (deaconHp > 0 ? 1 : 0) : d.getHealth() / d.getMaxHealth();
        int live = liveNave();
        BossEvent.BossBarColor color;
        Component status;
        Commune c = d == null ? Commune.NONE : commune(d);
        switch (phase) {
            case P0_VESTING -> { color = BossEvent.BossBarColor.WHITE; status = Component.translatable("static_deacon.status.p0"); }
            case P4_RESEED -> { color = BossEvent.BossBarColor.YELLOW; status = Component.translatable("static_deacon.status.p4", Math.min(live, reseedTarget), reseedTarget); }
            case P3_VIGIL -> {
                color = BossEvent.BossBarColor.RED;
                status = Component.translatable(c == Commune.PLINTH ? "static_deacon.status.p3_vigil" : "static_deacon.status.p3_stranded", Math.max(0, exposeTicks / 20));
            }
            default -> {
                if (c == Commune.NAVE && d != null) {
                    color = phase == Phase.P1_COMMUNION ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.PINK;
                    status = Component.translatable("static_deacon.status.communion", Math.round(damageReduction(d) * 100),
                            String.format(java.util.Locale.ROOT, "%.1f", regenPerSecond(d) * d.getMaxHealth()));
                } else if (c == Commune.PLINTH) {
                    color = BossEvent.BossBarColor.RED;
                    status = Component.translatable("static_deacon.status.plinth", Math.round(DeaconConfig.PLINTH_DR.get() * 100));
                } else {
                    color = BossEvent.BossBarColor.RED;
                    status = Component.translatable("static_deacon.status.stranded");
                }
            }
        }
        bar.update(hp, coverage(), color, status, Component.translatable("static_deacon.bossbar.floor_count", live, naveTotal));
    }

    // ================================================================== commands
    /** Designer: advance to the next beat. */
    public void skipPhase(ServerLevel level) {
        switch (phase) {
            case DORMANT, CLEARED -> {
                if (phase == Phase.CLEARED) reset(level, "skip");
                tryStart(level, null);
            }
            case P0_VESTING -> phaseTicks = 199;
            case P1_COMMUNION -> setFloor(level, (int) Math.floor(naveTotal * DeaconConfig.P2_THRESHOLD.get()) - 1);
            case P2_PATCHWORK -> setFloor(level, 0);
            case P3_VIGIL -> exposeTicks = 1;
            case P4_RESEED -> {
                while (liveNave() < reseedTarget) {
                    Flagstone next = null;
                    for (Flagstone f : slots) if (f.nave() && f.state != Flagstone.State.LIVE && (next == null || f.ring() < next.ring())) next = f;
                    if (next == null) break;
                    CryptBuilder.consecrate(level, origin, next.cx, next.cz, true);
                    next.state = Flagstone.State.LIVE;
                }
                tickReseedPhase(level);
            }
            default -> {}
        }
    }

    /** Designer: force the live nave count (desecrate the farthest or restore the nearest first) and re-evaluate. */
    public void setFloor(ServerLevel level, int n) {
        n = Math.max(0, Math.min(naveTotal, n));
        List<Flagstone> nave = new ArrayList<>();
        for (Flagstone f : slots) if (f.nave()) nave.add(f);
        nave.sort(Comparator.comparingInt(Flagstone::ring).thenComparingInt(f -> f.index));
        for (int i = 0; i < nave.size(); i++) {
            Flagstone f = nave.get(i);
            if (i < n && f.state != Flagstone.State.LIVE) {
                CryptBuilder.consecrate(level, origin, f.cx, f.cz, true);
                f.state = Flagstone.State.LIVE;
            }
        }
        for (int i = nave.size() - 1; i >= n; i--) desecrate(level, nave.get(i), null, "command");
        if (phase == Phase.P3_VIGIL || phase == Phase.P4_RESEED) {
            if (n > 0) {
                StaticDeaconEntity d = deacon(level);
                if (d != null) d.resetForPhase();
                setPhase(level, coverage() >= DeaconConfig.P2_THRESHOLD.get() ? Phase.P1_COMMUNION : Phase.P2_PATCHWORK);
            }
        }
        recomputePhase(level);
        setDirty();
    }

    /** Designer: desecrate the flagstone nearest to {@code at}. @return its index, or -1 */
    public int strip(ServerLevel level, Vec3 at, @Nullable Player by) {
        Flagstone best = null;
        for (Flagstone f : slots) {
            if (!f.nave() || f.state == Flagstone.State.DESECRATED) continue;
            if (best == null || Vec3.atCenterOf(f.center).distanceToSqr(at) < Vec3.atCenterOf(best.center).distanceToSqr(at)) best = f;
        }
        if (best == null) return -1;
        desecrate(level, best, by, "command");
        return best.index;
    }

    public boolean forceReseed(ServerLevel level) {
        if (!phase.fighting() || phase == Phase.P0_VESTING || phase == Phase.P4_RESEED) return false;
        enterReseed(level, deacon(level));
        return true;
    }

    public String describe(ServerLevel level) {
        StaticDeaconEntity d = deacon(level);
        StringBuilder sb = new StringBuilder();
        sb.append("crypt ").append(origin.toShortString()).append(" phase=").append(phase.id()).append(" t=").append(phaseTicks)
                .append(" floor=").append(liveNave()).append('/').append(naveTotal)
                .append(" reseeding=").append(reseeding())
                .append(" expose=").append(exposeTicks).append(" reseeds=").append(reseedCount).append(" target=").append(reseedTarget)
                .append(" sched=").append(scheduleCursor).append('/').append(schedule.size())
                .append(" desecrated=").append(desecrations).append(" denied=").append(denials).append(" vigilBreaks=").append(vigilBreaks);
        if (d != null) {
            sb.append(" commune=").append(commune(d).name().toLowerCase(java.util.Locale.ROOT))
                    .append(" dr=").append(Math.round(damageReduction(d) * 100)).append('%')
                    .append(String.format(java.util.Locale.ROOT, " regen=%.2f/s", regenPerSecond(d) * d.getMaxHealth()));
            sb.append(System.lineSeparator()).append("  ").append(d.debugState());
        }
        return sb.toString();
    }

    // ================================================================== helpers
    private void forceChunks(ServerLevel level, boolean on) {
        if (forced == on) return;
        forced = on;
        ChunkPos a = new ChunkPos(origin.offset(-CryptLayout.WALL_X - 8, 0, -CryptLayout.WALL_Z - 8));
        ChunkPos b = new ChunkPos(origin.offset(CryptLayout.WALL_X + 8, 0, CryptLayout.WALL_Z + 12));
        for (int x = a.x; x <= b.x; x++) for (int z = a.z; z <= b.z; z++) level.setChunkForced(x, z, on);
        setDirty();
    }

    static void award(ServerPlayer p, String path) {
        var h = p.server.getAdvancements().get(SkyloreBosses.id("static_deacon/" + path));
        if (h != null) p.getAdvancements().award(h, "code");
    }

    private static void title(ServerPlayer p, String title, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    // ================================================================== persistence
    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Origin", NbtUtils.writeBlockPos(origin));
        t.putString("Phase", phase.name());
        t.putInt("PhaseTicks", phaseTicks);
        t.putLong("FightTicks", fightTicks);
        t.putInt("Expose", exposeTicks);
        t.putInt("Reseeds", reseedCount);
        t.putInt("ReseedTarget", reseedTarget);
        t.putInt("Deaths", deaths);
        t.putInt("Desecrations", desecrations);
        t.putInt("VigilBreaks", vigilBreaks);
        t.putInt("Denials", denials);
        t.putBoolean("AnyReseed", anyReseed);
        t.putBoolean("Forced", forced);
        t.putBoolean("FirstFlagstone", firstFlagstoneAwarded);
        t.putBoolean("FirstVigil", firstVigilAwarded);
        t.putLong("ClearedAt", clearedAt);
        t.put("Schedule", new IntArrayTag(schedule.stream().mapToInt(Integer::intValue).toArray()));
        t.putInt("Cursor", scheduleCursor);
        if (deaconId != null) t.putUUID("Deacon", deaconId);
        t.putFloat("DeaconHp", deaconHp);
        ListTag sl = new ListTag();
        for (Flagstone f : slots) if (f.nave()) sl.add(f.save());
        t.put("Slots", sl);
        ListTag ev = new ListTag();
        for (UUID u : participantsEver) ev.add(NbtUtils.createUUID(u));
        t.put("Participants", ev);
        return t;
    }

    static Crypt load(CompoundTag t) {
        Crypt c = new Crypt(NbtUtils.readBlockPos(t, "Origin").orElse(BlockPos.ZERO));
        c.phase = Phase.byName(t.getString("Phase"));
        c.phaseTicks = t.getInt("PhaseTicks");
        c.fightTicks = t.getLong("FightTicks");
        c.exposeTicks = t.getInt("Expose");
        c.reseedCount = t.getInt("Reseeds");
        c.reseedTarget = t.getInt("ReseedTarget");
        c.deaths = t.getInt("Deaths");
        c.desecrations = t.getInt("Desecrations");
        c.vigilBreaks = t.getInt("VigilBreaks");
        c.denials = t.getInt("Denials");
        c.anyReseed = t.getBoolean("AnyReseed");
        c.forced = t.getBoolean("Forced");
        c.firstFlagstoneAwarded = t.getBoolean("FirstFlagstone");
        c.firstVigilAwarded = t.getBoolean("FirstVigil");
        c.clearedAt = t.getLong("ClearedAt");
        for (int i : t.getIntArray("Schedule")) c.schedule.add(i);
        c.scheduleCursor = t.getInt("Cursor");
        c.deaconId = t.hasUUID("Deacon") ? t.getUUID("Deacon") : null;
        c.deaconHp = t.getFloat("DeaconHp");
        for (Tag x : t.getList("Slots", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) x;
            int i = s.getInt("I");
            if (i >= 0 && i < c.slots.length) c.slots[i].load(s);
        }
        for (Tag u : t.getList("Participants", Tag.TAG_INT_ARRAY)) c.participantsEver.add(NbtUtils.loadUUID(u));
        return c;
    }
}
