package net.teamaof.skylorebosses.bosses.overhead.encounter;

import dev.architectury.event.EventResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.overhead.OverheadBoss;
import net.teamaof.skylorebosses.bosses.overhead.OverheadConfig;
import net.teamaof.skylorebosses.bosses.overhead.api.OverheadEvents;
import net.teamaof.skylorebosses.bosses.overhead.block.GeneratorPylonBlockEntity;
import net.teamaof.skylorebosses.bosses.overhead.entity.Action;
import net.teamaof.skylorebosses.bosses.overhead.entity.OverheadEntity;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlocks;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadEntities;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadItems;
import net.teamaof.skylorebosses.core.api.BossEvents;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;

/**
 * One Teknari yard and the fight in it (DESIGN.md §4, §6, §7). Persisted inside {@link OverheadYards}, so a fight
 * survives relogs, restarts and chunk unloads: the chassis and pylon blocks are views of this state and are
 * re-created from it if they go missing.
 */
public final class Yard {
    public final BlockPos origin;
    private Phase phase = Phase.DORMANT;
    private int phaseTicks;
    private long fightTicks;
    private final Pylon[] pylons = new Pylon[4];
    private int windowTicks, exposeTicks, rearmCount, rearmTarget, breakCounter, pylonsBroken, deaths;
    private boolean anyRearm, forced, firstPylonAwarded;
    @Nullable private UUID overheadId;
    private float overheadHp;
    private int overheadMissing, idleTicks;
    private long clearedAt;
    private final Set<UUID> participantsEver = new HashSet<>();
    private final Map<BlockPos, Long> coverRegen = new HashMap<>();
    private final transient OverheadBossBar bar = new OverheadBossBar();
    private transient Runnable dirty = () -> {};

    public Yard(BlockPos origin) {
        this.origin = origin.immutable();
        for (int i = 0; i < 4; i++) {
            pylons[i] = new Pylon(i, YardLayout.pylon(origin, i));
            pylons[i].restore((float) (double) OverheadConfig.PYLON_HP.get());
        }
    }

    void setDirtyHook(Runnable r) { dirty = r; }

    private void setDirty() { dirty.run(); }

    public Phase phase() { return phase; }
    public int phaseTicks() { return phaseTicks; }
    public int windowTicks() { return windowTicks; }
    public int exposeTicks() { return exposeTicks; }
    public int rearmCount() { return rearmCount; }
    public Pylon pylon(int i) { return pylons[i]; }

    public int online() {
        int n = 0;
        for (Pylon p : pylons) if (p.online()) n++;
        return n;
    }

    // ================================================================== damage gate (DESIGN.md §7)
    /** Fraction of incoming damage Overhead actually takes (1 - DR). */
    public float damageMultiplier() {
        return 1f - damageReduction();
    }

    public float damageReduction() {
        return switch (phase) {
            case P0_LOCKDOWN, DORMANT, CLEARED -> 1f;
            case P4_REARM -> OverheadConfig.REARM_DR.get().floatValue();
            case P3_EXPOSED, DEFEATED -> 0f;
            default -> {
                int n = online();
                if (n == 0) yield 0f;
                if (windowTicks > 0) yield OverheadConfig.WINDOW_DR.get().floatValue();
                double dr = switch (n) {
                    case 4 -> OverheadConfig.DR_4.get();
                    case 3 -> OverheadConfig.DR_3.get();
                    case 2 -> OverheadConfig.DR_2.get();
                    default -> OverheadConfig.DR_1.get();
                };
                yield (float) dr;
            }
        };
    }

    // ================================================================== lifecycle
    /** Try to start from DORMANT. The pack may veto through {@link OverheadEvents#START_CHECK}. */
    public boolean tryStart(ServerLevel level, @Nullable ServerPlayer trigger) {
        if (phase != Phase.DORMANT) return false;
        if (trigger != null) {
            EventResult r = OverheadEvents.START_CHECK.invoker().check(level, origin, trigger);
            if (r.isFalse()) {
                trigger.displayClientMessage(Component.translatable("overhead.console.denied").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
        }
        List<ServerPlayer> ps = participants(level);
        int n = Math.max(1, ps.size());
        float maxI = (float) (OverheadConfig.PYLON_HP.get() * Math.min(OverheadConfig.PYLON_MP_CAP.get(), 1 + OverheadConfig.PYLON_MP_PER_PLAYER.get() * (n - 1)));
        for (Pylon p : pylons) {
            p.restore(maxI);
            p.state = PylonState.REBUILDING;   // "spinning up" in P0
            p.integrity = 0;
            p.rebuildAt = level.getGameTime() + 20 + p.index * 30L;
        }
        fightTicks = 0;
        rearmCount = 0;
        anyRearm = false;
        breakCounter = 0;
        pylonsBroken = 0;
        deaths = 0;
        windowTicks = 0;
        firstPylonAwarded = false;
        participantsEver.clear();
        overheadHp = 0;
        YardBuilder.restoreCover(level, origin);
        coverRegen.clear();
        setPhase(level, Phase.P0_LOCKDOWN);
        forceChunks(level, true);
        YardBuilder.closeGates(level, origin);
        spawnOverhead(level, true);
        for (ServerPlayer p : ps) {
            title(p, "overhead.title.lockdown", "overhead.title.lockdown.sub");
            award(p, "enter_yard");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 30);
        }
        AnimFx.play(level, Vec3.atCenterOf(origin), "overhead.lockdown.gate", 16f, 1f);
        OverheadEvents.ENCOUNTER_STARTED.invoker().started(level, origin, ps);
        BossEvents.STARTED.invoker().started(level, OverheadBoss.ID, origin, ps);
        return true;
    }

    public void reset(ServerLevel level, String reason) {
        OverheadEntity oh = overhead(level);
        if (oh != null) oh.discard();
        overheadId = null;
        for (Pylon p : pylons) {
            p.restore((float) (double) OverheadConfig.PYLON_HP.get());
            ensurePylon(level, p);
        }
        windowTicks = 0;
        exposeTicks = 0;
        bar.clear();
        YardBuilder.openGates(level, origin);
        YardBuilder.restoreCover(level, origin);
        coverRegen.clear();
        forceChunks(level, false);
        setPhase(level, Phase.DORMANT);
        OverheadEvents.RESET.invoker().reset(level, origin, reason);
    }

    private void setPhase(ServerLevel level, Phase to) {
        Phase from = phase;
        phase = to;
        phaseTicks = 0;
        setDirty();
        if (from != to) {
            SkyloreBosses.LOG.info("Overhead yard {}: {} -> {}", origin.toShortString(), from, to);
            OverheadEvents.PHASE_CHANGED.invoker().changed(level, origin, from, to);
        }
    }

    // ================================================================== tick
    void tick(ServerLevel level) {
        phaseTicks++;
        long now = level.getGameTime();
        switch (phase) {
            case DORMANT -> {
                if (phaseTicks % 10 == 0) {
                    List<ServerPlayer> in = level.getEntitiesOfClass(ServerPlayer.class, YardLayout.trigger(origin), p -> !p.isSpectator());
                    if (!in.isEmpty()) tryStart(level, in.get(0));
                }
                if (phaseTicks % 40 == 0) for (Pylon p : pylons) ensurePylon(level, p);
                return;
            }
            case CLEARED -> {
                if (OverheadConfig.REMATCH.get() && now - clearedAt > OverheadConfig.REMATCH_DELAY_TICKS.get()) reset(level, "rematch");
                return;
            }
            case DEFEATED -> {
                if (phaseTicks == 140) {
                    YardBuilder.openGates(level, origin);
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
        List<ServerPlayer> ps = participants(level);
        if (ps.isEmpty()) {
            if (++idleTicks > OverheadConfig.DORMANT_TIMEOUT_TICKS.get()) {
                idleTicks = 0;
                SkyloreBosses.LOG.info("Overhead yard {} reset: no participants", origin.toShortString());
                reset(level, "abandoned");
            }
            return;
        }
        idleTicks = 0;
        fightTicks++;
        for (ServerPlayer p : ps) {
            participantsEver.add(p.getUUID());
            if (YardLayout.fellOut(origin, p)) rescue(level, p);
        }
        if (windowTicks > 0 && --windowTicks == 0) {
            for (ServerPlayer p : ps) p.displayClientMessage(Component.translatable("overhead.log.window_closed").withStyle(ChatFormatting.AQUA), true);
        }
        OverheadEntity oh = overhead(level);
        if (oh == null) {
            if (++overheadMissing >= 40) spawnOverhead(level, false);
        } else {
            overheadMissing = 0;
            if (phaseTicks % 20 == 0) overheadHp = oh.getHealth();
        }
        switch (phase) {
            case P0_LOCKDOWN -> tickLockdown(level, oh, now);
            case P3_EXPOSED -> {
                if (--exposeTicks <= 0) enterRearm(level, oh);
            }
            case P4_REARM -> tickRearm(level, oh, now);
            default -> {}
        }
        if (phaseTicks % 40 == 0) for (Pylon p : pylons) ensurePylon(level, p);
        if (phaseTicks % 20 == 0) tickCover(level, now);
        if (phaseTicks % 5 == 0) {
            mirrorPylons(level);
            updateBar(oh);
        }
        if (phaseTicks % 20 == 0) bar.syncPlayers(ps);
    }

    private void tickLockdown(ServerLevel level, @Nullable OverheadEntity oh, long now) {
        // pylons spin up one by one (integrity fills over 30 ticks each)
        for (Pylon p : pylons) {
            if (p.state != PylonState.REBUILDING || now < p.rebuildAt) continue;
            p.integrity = Math.min(p.max, p.integrity + p.max / 30f);
            if (p.integrity >= p.max) {
                p.state = PylonState.ONLINE;
                AnimFx.play(level, Vec3.atCenterOf(p.core), "overhead.pylon.online", 6f, 1f);
            }
        }
        if (phaseTicks == 100 && oh != null) oh.script(Action.POWER_ON_PULSE);
        if (phaseTicks >= 160) {
            for (Pylon p : pylons) {
                p.state = PylonState.ONLINE;
                p.integrity = p.max;
            }
            for (ServerPlayer p : participants(level))
                p.displayClientMessage(Component.translatable("overhead.log.p1").withStyle(ChatFormatting.AQUA), true);
            setPhase(level, Phase.P1_SHIELDED);
        }
    }

    private void tickRearm(ServerLevel level, @Nullable OverheadEntity oh, long now) {
        float rate = 1f / Math.max(1, OverheadConfig.REBUILD_TICKS.get());
        for (Pylon p : pylons) {
            if (p.state != PylonState.REBUILDING || now < p.rebuildAt) continue;
            p.integrity = Math.min(p.max, p.integrity + p.max * rate);
            if (p.integrity >= p.max) {
                p.state = PylonState.ONLINE;
                p.contributors.clear();
                AnimFx.play(level, Vec3.atCenterOf(p.core), "overhead.pylon.online", 8f, 1f);
                for (ServerPlayer pl : participants(level))
                    pl.displayClientMessage(Component.translatable("overhead.log.pylon_online", Component.translatable("overhead.pylon." + p.index)).withStyle(ChatFormatting.AQUA), true);
            }
        }
        int on = online();
        if (on >= rearmTarget) {
            for (Pylon p : pylons) if (p.state == PylonState.REBUILDING) { p.state = PylonState.OFFLINE; p.integrity = 0; }
            if (oh != null) oh.resetForPhase();
            setPhase(level, on >= 4 ? Phase.P1_SHIELDED : Phase.P2_DEGRADED);
            for (ServerPlayer pl : participants(level))
                pl.displayClientMessage(Component.translatable("overhead.log.rearm_done", on).withStyle(ChatFormatting.DARK_AQUA), true);
        } else if (phaseTicks >= OverheadConfig.REARM_ABORT_TICKS.get()) {
            for (Pylon p : pylons) if (p.state == PylonState.REBUILDING) { p.state = PylonState.OFFLINE; p.integrity = 0; }
            if (oh != null) oh.resetForPhase();
            exposeTicks = (int) (OverheadConfig.EXPOSE_TICKS.get() * OverheadConfig.REARM_ABORT_EXPOSE_FRACTION.get());
            setPhase(level, Phase.P3_EXPOSED);
            for (ServerPlayer pl : participants(level)) title(pl, "overhead.title.rearm_aborted", "overhead.title.rearm_aborted.sub");
        }
    }

    private void enterRearm(ServerLevel level, @Nullable OverheadEntity oh) {
        rearmCount++;
        anyRearm = true;
        rearmTarget = Math.min(4, OverheadConfig.REARM_BASE_PYLONS.get() + rearmCount - 1);
        long now = level.getGameTime();
        List<Pylon> order = new ArrayList<>();
        for (Pylon p : pylons) if (p.state == PylonState.OFFLINE) order.add(p);
        order.sort(Comparator.comparingInt(p -> p.brokenOrder));   // first broken, first rebuilt
        int k = 0;
        for (Pylon p : order) {
            if (k >= rearmTarget - online()) break;
            p.state = PylonState.REBUILDING;
            p.integrity = 0;
            p.contributors.clear();
            p.rebuildAt = now + 80 + (long) k * OverheadConfig.REBUILD_STAGGER_TICKS.get();
            k++;
        }
        windowTicks = 0;
        setPhase(level, Phase.P4_REARM);
        if (oh != null) {
            oh.resetForPhase();
            oh.script(Action.REARM_SHIELD_PULSE);
        }
        for (ServerPlayer p : participants(level)) {
            title(p, "overhead.title.rearm", "overhead.title.rearm.sub");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 20);
        }
        OverheadEvents.REARM_STARTED.invoker().started(level, origin, rearmCount, rearmTarget);
    }

    /** Re-evaluate P1/P2/P3 after pylon count changes. */
    private void recomputePhase(ServerLevel level) {
        if (phase != Phase.P1_SHIELDED && phase != Phase.P2_DEGRADED) return;
        int on = online();
        if (on == 0) {
            exposeTicks = OverheadConfig.EXPOSE_TICKS.get();
            windowTicks = 0;
            OverheadEntity oh = overhead(level);
            if (oh != null) oh.resetForPhase();
            setPhase(level, Phase.P3_EXPOSED);
            for (ServerPlayer p : participants(level)) {
                title(p, "overhead.title.exposed", "overhead.title.exposed.sub");
                if (!anyRearm) award(p, "demolition_crew");
            }
        } else if (on < 4 && phase == Phase.P1_SHIELDED) {
            setPhase(level, Phase.P2_DEGRADED);
        }
    }

    // ================================================================== pylons
    /** Melee strike on a pylon block. @return true if this yard owns the pylon */
    boolean meleeStrike(ServerLevel level, int idx, Player player) {
        float dmg = (float) Math.max(1, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float charge = player.getAttackStrengthScale(0.5f);
        dmg *= 0.2f + 0.8f * charge * charge;
        if (player.fallDistance > 0 && !player.onGround() && charge > 0.9f) dmg *= 1.5f;
        player.resetAttackStrengthTicker();
        damagePylon(level, idx, dmg, player);
        return true;
    }

    /** Player projectile hit a pylon block. */
    public void projectileHit(ServerLevel level, int idx, Player player) {
        damagePylon(level, idx, OverheadConfig.PYLON_PROJECTILE_DAMAGE.get().floatValue(), player);
    }

    /** Explosion damage to every pylon within {@code radius} of {@code at} (falls off to 30%). */
    public void blastPylons(ServerLevel level, Vec3 at, double radius, float damage, @Nullable Player by) {
        for (Pylon p : pylons) {
            double d = Vec3.atCenterOf(p.core).add(0, 2, 0).distanceTo(at);
            if (d > radius + 1.5) continue;
            float f = (float) (1 - 0.7 * Math.min(1, d / Math.max(0.1, radius)));
            damagePylon(level, p.index, damage * f, by);
        }
    }

    public void damagePylon(ServerLevel level, int idx, float amount, @Nullable Player by) {
        Pylon p = pylons[idx];
        if (!phase.fighting() || phase == Phase.P0_LOCKDOWN) {
            if (by instanceof ServerPlayer sp && phase == Phase.P0_LOCKDOWN)
                sp.displayClientMessage(Component.translatable("overhead.gate.pylon_spinning").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.state == PylonState.OFFLINE || amount <= 0) return;
        long now = level.getGameTime();
        p.integrity = Math.max(0, p.integrity - amount);
        p.lastHit = now;
        if (by != null) {
            p.contributors.add(by.getUUID());
            OverheadEntity oh = overhead(level);
            if (oh != null) oh.addThreat(by.getUUID(), amount * 1.5f);
        }
        pylonAnim(level, idx, "hurt");
        if (p.state == PylonState.ONLINE && p.integrity <= 0) breakPylon(level, idx, by instanceof ServerPlayer sp ? sp : null);
        mirrorPylons(level);
        setDirty();
    }

    public void breakPylon(ServerLevel level, int idx, @Nullable ServerPlayer breaker) {
        Pylon p = pylons[idx];
        if (p.state != PylonState.ONLINE) return;
        p.state = PylonState.OFFLINE;
        p.integrity = 0;
        p.brokenOrder = ++breakCounter;
        pylonsBroken++;
        windowTicks = OverheadConfig.WINDOW_TICKS.get();
        Vec3 c = Vec3.atCenterOf(p.core).add(0, 3, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        AnimFx.serverBurst(level, SBParticles.GRID_ARC.get(), c, 80, 2, 0.5);
        AnimFx.play(level, c, "overhead.pylon.break", 10f, 1f);
        pylonAnim(level, idx, "break");
        OverheadEntity oh = overhead(level);
        if (oh != null) oh.onPylonBroken();
        int remaining = online();
        List<ServerPlayer> ps = participants(level);
        Component name = Component.translatable("overhead.pylon." + idx);
        for (ServerPlayer pl : ps) {
            pl.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
            pl.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            pl.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("overhead.log.pylon_down", name, remaining)
                    .withStyle(ChatFormatting.GOLD)));
            AnimFx.play(level, pl.position(), "overhead.brownout", 1f, 1f);
            if (!firstPylonAwarded) award(pl, "first_pylon");
        }
        firstPylonAwarded = true;
        OverheadEvents.PYLON_BROKEN.invoker().broken(level, origin, idx, breaker, Set.copyOf(p.contributors), remaining);
        mirrorPylons(level);
        recomputePhase(level);
        setDirty();
    }

    public void overchargeRepair(ServerLevel level, int idx, float amount) {
        Pylon p = pylons[idx];
        if (p.state == PylonState.OFFLINE) return;
        p.integrity = Math.min(p.max, p.integrity + amount);
        mirrorPylons(level);
    }

    public void pylonAnim(ServerLevel level, int idx, String anim) {
        if (level.isLoaded(pylons[idx].core) && level.getBlockEntity(pylons[idx].core) instanceof GeneratorPylonBlockEntity be) be.triggerAnim("main", anim);
    }

    /** Re-place a missing pylon column (creative break, /setblock, structure mishap) and re-mirror its state. */
    private void ensurePylon(ServerLevel level, Pylon p) {
        if (!level.isLoaded(p.core)) return;
        if (!YardBuilder.pylonIntact(level, p.core)) YardBuilder.placePylon(level, p.core);
        if (level.getBlockEntity(p.core) instanceof GeneratorPylonBlockEntity be) be.mirror(p.index, p.state, p.fraction());
    }

    private void mirrorPylons(ServerLevel level) {
        for (Pylon p : pylons)
            if (level.isLoaded(p.core) && level.getBlockEntity(p.core) instanceof GeneratorPylonBlockEntity be) be.mirror(p.index, p.state, p.fraction());
    }

    public int pylonAt(BlockPos core) {
        for (Pylon p : pylons) if (p.core.equals(core)) return p.index;
        return -1;
    }

    // ================================================================== cover
    private void tickCover(ServerLevel level, long now) {
        BlockState crate = OverheadBlocks.YARD_CRATE.get().defaultBlockState();
        for (BlockPos p : YardBuilder.coverBlocks(origin)) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).isAir()) coverRegen.putIfAbsent(p, now + OverheadConfig.COVER_REGEN_TICKS.get());
        }
        coverRegen.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            BlockPos p = e.getKey();
            if (!level.getBlockState(p).isAir()) return true;
            if (!level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(p)).isEmpty()) return false;
            level.setBlock(p, crate, 3);
            AnimFx.serverBurst(level, SBParticles.SPARK.get(), Vec3.atCenterOf(p), 6, 0.4, 0.1);
            return true;
        });
    }

    // ================================================================== chassis
    @Nullable
    public OverheadEntity overhead(ServerLevel level) {
        if (overheadId == null) return null;
        Entity e = level.getEntity(overheadId);
        return e instanceof OverheadEntity oh && oh.isAlive() ? oh : null;
    }

    private float maxHp(ServerLevel level) {
        int n = Math.max(1, participants(level).size());
        return (float) (OverheadConfig.HP.get() * Math.min(OverheadConfig.MP_HP_CAP.get(), 1 + OverheadConfig.MP_HP_PER_PLAYER.get() * (n - 1)));
    }

    private void spawnOverhead(ServerLevel level, boolean fresh) {
        Entity old = overheadId == null ? null : level.getEntity(overheadId);
        if (old != null) old.discard();
        OverheadEntity oh = OverheadEntities.OVERHEAD.get().create(level);
        if (oh == null) return;
        Vec3 at = fresh || phase == Phase.P0_LOCKDOWN ? YardLayout.cradle(origin) : Vec3.atBottomCenterOf(origin).add(0, 15, 0);
        oh.moveTo(at.x, at.y, at.z, 180, 0);
        oh.bindYard(origin);
        float max = maxHp(level);
        oh.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        oh.setHealth(!fresh && overheadHp > 0 ? Math.min(max, overheadHp) : max);
        oh.finalizeSpawn(level, level.getCurrentDifficultyAt(origin), MobSpawnType.EVENT, null);
        level.addFreshEntity(oh);
        if (fresh) oh.triggerAnim("main", "power_on");
        overheadId = oh.getUUID();
        overheadHp = oh.getHealth();
        overheadMissing = 0;
        setDirty();
    }

    public void onOverheadDamaged(OverheadEntity oh) {
        overheadHp = oh.getHealth();
    }

    public void onOverheadDied(ServerLevel level, OverheadEntity oh, DamageSource source) {
        if (!oh.getUUID().equals(overheadId) || !phase.fighting()) return;
        overheadHp = 0;
        List<ServerPlayer> ps = participants(level);
        setPhase(level, Phase.DEFEATED);
        bar.update(0, 0, BossEvent.BossBarColor.RED, Component.translatable("overhead.status.down"));
        String fn = OverheadConfig.VICTORY_FUNCTION.get();
        for (ServerPlayer p : ps) {
            award(p, "prototype_down");
            if (!anyRearm) award(p, "no_rearm");
            p.getInventory().placeItemBackInInventory(new ItemStack(OverheadItems.TARGETING_CORE.get()));
            title(p, "overhead.title.victory", "overhead.title.victory.sub");
            AnimFx.play(level, p.position(), "overhead.victory", 1f, 1f);
            if (!fn.isEmpty()) {
                var server = level.getServer();
                server.getFunctions().get(ResourceLocation.parse(fn)).ifPresent(f ->
                        server.getFunctions().execute(f, p.createCommandSourceStack().withSuppressedOutput().withPermission(2)));
            }
        }
        OverheadEvents.VICTORY.invoker().victory(level, origin, ps,
                new OverheadEvents.EncounterStats(fightTicks, rearmCount, deaths, pylonsBroken, anyRearm));
        BossEvents.DEFEATED.invoker().defeated(level, OverheadBoss.ID, origin, ps);
    }

    public void onPlayerDeath(ServerPlayer p) {
        if (phase.fighting() && YardLayout.volume(origin).contains(p.position())) deaths++;
    }

    // ================================================================== players
    public List<ServerPlayer> participants(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, YardLayout.volume(origin), p -> !p.isSpectator() && p.isAlive());
    }

    private void rescue(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(YardLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.fallDistance = 0;
        p.hurt(level.damageSources().fellOutOfWorld(), 4f);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
        p.displayClientMessage(Component.translatable("overhead.log.rescue").withStyle(ChatFormatting.GRAY), true);
    }

    /** Console right-click. */
    public void onConsole(ServerLevel level, ServerPlayer p) {
        switch (phase) {
            case DORMANT -> {
                if (tryStart(level, p)) readmit(level, p);
            }
            case CLEARED, DEFEATED -> p.displayClientMessage(Component.translatable("overhead.console.cleared").withStyle(ChatFormatting.GRAY), true);
            default -> readmit(level, p);
        }
    }

    private void readmit(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(YardLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.displayClientMessage(Component.translatable("overhead.console.readmit").withStyle(ChatFormatting.AQUA), true);
    }

    // ================================================================== bar
    private void updateBar(@Nullable OverheadEntity oh) {
        float hp = oh == null ? (overheadHp > 0 ? 1 : 0) : oh.getHealth() / oh.getMaxHealth();
        float grid = 0;
        for (Pylon p : pylons) grid += p.state == PylonState.OFFLINE ? 0 : p.fraction();
        grid /= 4f;
        BossEvent.BossBarColor color;
        Component status;
        int on = online();
        switch (phase) {
            case P0_LOCKDOWN -> { color = BossEvent.BossBarColor.WHITE; status = Component.translatable("overhead.status.p0"); }
            case P3_EXPOSED -> { color = BossEvent.BossBarColor.RED; status = Component.translatable("overhead.status.p3", Math.max(0, exposeTicks / 20)); }
            case P4_REARM -> { color = BossEvent.BossBarColor.PURPLE; status = Component.translatable("overhead.status.p4", on, rearmTarget); }
            default -> {
                if (windowTicks > 0) { color = BossEvent.BossBarColor.YELLOW; status = Component.translatable("overhead.status.window", windowTicks / 20 + 1); }
                else { color = BossEvent.BossBarColor.BLUE; status = Component.translatable("overhead.status.shielded", on, Math.round(damageReduction() * 100)); }
            }
        }
        bar.update(hp, grid, color, status);
    }

    // ================================================================== commands
    /** Designer: advance to the next beat. */
    public void skipPhase(ServerLevel level) {
        switch (phase) {
            case DORMANT, CLEARED -> {
                if (phase == Phase.CLEARED) reset(level, "skip");
                tryStart(level, null);
            }
            case P0_LOCKDOWN -> phaseTicks = 159;
            case P1_SHIELDED, P2_DEGRADED -> {
                for (Pylon p : pylons) if (p.online()) { breakPylon(level, p.index, null); break; }
            }
            case P3_EXPOSED -> exposeTicks = 1;
            case P4_REARM -> { for (Pylon p : pylons) if (p.state == PylonState.REBUILDING) p.rebuildAt = 0; for (Pylon p : pylons) if (p.state == PylonState.REBUILDING) p.integrity = p.max - 0.01f; }
            default -> {}
        }
    }

    /** Designer: force the live pylon count (breaks without windows, or restores) and re-evaluate the phase. */
    public void setPylons(ServerLevel level, int n) {
        if (!phase.fighting() || phase == Phase.P0_LOCKDOWN) return;
        for (int i = 0; i < 4; i++) {
            Pylon p = pylons[i];
            if (i < n) {
                p.state = PylonState.ONLINE;
                p.integrity = p.max;
            } else if (p.state != PylonState.OFFLINE) {
                p.state = PylonState.OFFLINE;
                p.integrity = 0;
                p.brokenOrder = ++breakCounter;
            }
        }
        mirrorPylons(level);
        if (phase == Phase.P3_EXPOSED || phase == Phase.P4_REARM) {
            if (n > 0) setPhase(level, n >= 4 ? Phase.P1_SHIELDED : Phase.P2_DEGRADED);
        } else if (n == 4) {
            setPhase(level, Phase.P1_SHIELDED);
        }
        recomputePhase(level);
        OverheadEntity oh = overhead(level);
        if (oh != null) oh.resetForPhase();
    }

    public String describe(ServerLevel level) {
        StringBuilder sb = new StringBuilder();
        sb.append("yard ").append(origin.toShortString()).append(" phase=").append(phase.id()).append(" t=").append(phaseTicks)
                .append(" dr=").append(Math.round(damageReduction() * 100)).append('%')
                .append(" window=").append(windowTicks).append(" expose=").append(exposeTicks)
                .append(" rearms=").append(rearmCount).append(" pylons=[");
        for (Pylon p : pylons) sb.append(switch (p.state) { case ONLINE -> "on:"; case OFFLINE -> "off:"; case REBUILDING -> "rb:"; })
                .append(Math.round(p.integrity)).append(p.index < 3 ? " " : "");
        sb.append("]");
        OverheadEntity oh = overhead(level);
        if (oh != null) sb.append(System.lineSeparator()).append("  ").append(oh.debugState());
        return sb.toString();
    }

    // ================================================================== helpers
    private void forceChunks(ServerLevel level, boolean on) {
        if (forced == on) return;
        forced = on;
        ChunkPos a = new ChunkPos(origin.offset(-40, 0, -40)), b = new ChunkPos(origin.offset(40, 0, 40));
        for (int x = a.x; x <= b.x; x++) for (int z = a.z; z <= b.z; z++) level.setChunkForced(x, z, on);
        setDirty();
    }

    static void award(ServerPlayer p, String path) {
        var h = p.server.getAdvancements().get(SkyloreBosses.id("overhead/" + path));
        if (h != null) p.getAdvancements().award(h, "code");
    }

    private static void title(ServerPlayer p, String title, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.DARK_AQUA)));
    }

    // ================================================================== persistence
    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Origin", NbtUtils.writeBlockPos(origin));
        t.putString("Phase", phase.name());
        t.putInt("PhaseTicks", phaseTicks);
        t.putLong("FightTicks", fightTicks);
        t.putInt("Window", windowTicks);
        t.putInt("Expose", exposeTicks);
        t.putInt("Rearms", rearmCount);
        t.putInt("RearmTarget", rearmTarget);
        t.putInt("BreakCounter", breakCounter);
        t.putInt("PylonsBroken", pylonsBroken);
        t.putInt("Deaths", deaths);
        t.putBoolean("AnyRearm", anyRearm);
        t.putBoolean("Forced", forced);
        t.putBoolean("FirstPylon", firstPylonAwarded);
        t.putLong("ClearedAt", clearedAt);
        if (overheadId != null) t.putUUID("Overhead", overheadId);
        t.putFloat("OverheadHp", overheadHp);
        ListTag pl = new ListTag();
        for (Pylon p : pylons) pl.add(p.save());
        t.put("Pylons", pl);
        ListTag ev = new ListTag();
        for (UUID u : participantsEver) ev.add(NbtUtils.createUUID(u));
        t.put("Participants", ev);
        return t;
    }

    static Yard load(CompoundTag t) {
        Yard y = new Yard(NbtUtils.readBlockPos(t, "Origin").orElse(BlockPos.ZERO));
        y.phase = Phase.byName(t.getString("Phase"));
        y.phaseTicks = t.getInt("PhaseTicks");
        y.fightTicks = t.getLong("FightTicks");
        y.windowTicks = t.getInt("Window");
        y.exposeTicks = t.getInt("Expose");
        y.rearmCount = t.getInt("Rearms");
        y.rearmTarget = t.getInt("RearmTarget");
        y.breakCounter = t.getInt("BreakCounter");
        y.pylonsBroken = t.getInt("PylonsBroken");
        y.deaths = t.getInt("Deaths");
        y.anyRearm = t.getBoolean("AnyRearm");
        y.forced = t.getBoolean("Forced");
        y.firstPylonAwarded = t.getBoolean("FirstPylon");
        y.clearedAt = t.getLong("ClearedAt");
        y.overheadId = t.hasUUID("Overhead") ? t.getUUID("Overhead") : null;
        y.overheadHp = t.getFloat("OverheadHp");
        ListTag pl = t.getList("Pylons", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(4, pl.size()); i++) y.pylons[i].load(pl.getCompound(i));
        for (Tag u : t.getList("Participants", Tag.TAG_INT_ARRAY)) y.participantsEver.add(NbtUtils.loadUUID(u));
        return y;
    }
}
