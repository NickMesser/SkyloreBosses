package net.teamaof.skylorebosses.bosses.amanita.encounter;

import dev.architectury.event.EventResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaBoss;
import net.teamaof.skylorebosses.bosses.amanita.AmanitaConfig;
import net.teamaof.skylorebosses.bosses.amanita.api.AmanitaEvents;
import net.teamaof.skylorebosses.bosses.amanita.api.AmanitaEvents.OriginKind;
import net.teamaof.skylorebosses.bosses.amanita.block.HollowBrazierBlock;
import net.teamaof.skylorebosses.bosses.amanita.entity.AmanitaAction;
import net.teamaof.skylorebosses.bosses.amanita.entity.AmanitaEntity;
import net.teamaof.skylorebosses.bosses.amanita.entity.HollowSpawnEntity;
import net.teamaof.skylorebosses.bosses.amanita.entity.LampEaterEntity;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaBlocks;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaEntities;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaItems;
import net.teamaof.skylorebosses.core.api.BossEvents;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;

/**
 * One shadow hollow and the fight in it (DESIGN.md §4, §6, §7). Persisted inside {@link Hollows}, so a fight survives
 * relogs, restarts and chunk unloads: Amanita and her servants are views of this state and are re-created from it, and
 * the light gate is re-read from the level's light engine every tick.
 */
public final class Hollow {
    public final BlockPos origin;
    private Phase phase = Phase.DORMANT;
    private int phaseTicks;
    private long fightTicks;
    // light gate
    private int darkTicks, deepExposed, deepCount;
    private float deepHealed;
    private boolean snuffedOnce, fullSnuffDone, closingPending, relitByPlayers, anyDeep, wasExposed, firstLightAwarded;
    private long exposedUntil, baredUntil, hushUntil, exposedTotal;
    // stats
    private int deaths, snuffs, lightsPlaced, lightsSnuffed, lightsEaten;
    // adds
    private int eaterClock, spawnClock;
    // amanita
    @Nullable private UUID amanitaId;
    private float amanitaHp;
    private int amanitaMissing, idleTicks;
    private long clearedAt;
    private boolean forced;
    /** Who placed which light (multiplayer ownership, lamp-eater priority, stats). */
    private final Map<BlockPos, UUID> ledger = new HashMap<>();
    private final Set<UUID> participantsEver = new HashSet<>();
    private final Map<BlockPos, Long> coverRegen = new HashMap<>();
    // transient
    private final transient AmanitaBossBar bar = new AmanitaBossBar();
    private transient Runnable dirty = () -> {};
    private final transient Map<UUID, Long> hintAt = new HashMap<>();
    private final transient Map<UUID, Integer> callStacks = new HashMap<>();
    private transient List<BlockPos> census = List.of();
    private transient long censusAt = -1000;
    private transient int lightOverride = -1, lastLux;
    private transient boolean hold;

    public Hollow(BlockPos origin) {
        this.origin = origin.immutable();
    }

    void setDirtyHook(Runnable r) { dirty = r; }

    private void setDirty() { dirty.run(); }

    public Phase phase() { return phase; }
    public int phaseTicks() { return phaseTicks; }
    public boolean held() { return hold; }
    public void setHold(boolean h) { hold = h; }
    public boolean snuffedOnce() { return snuffedOnce; }
    public int lastLux() { return lastLux; }

    // ================================================================== light gate (DESIGN.md §6, §7)
    public int threshold() {
        return phase == Phase.P4_DEEP_BLOOM ? AmanitaConfig.DEEP_THRESHOLD.get() : AmanitaConfig.LIGHT_THRESHOLD.get();
    }

    /** Light at Amanita (the designer override from /skyloreamanita setlight wins). */
    public int lux(ServerLevel level, Entity a) {
        return lightOverride >= 0 ? lightOverride : Lights.sample(level, a);
    }

    public void setLightOverride(int l) { lightOverride = l; }

    public int lightOverride() { return lightOverride; }

    /** Exposed = the light at her reaches the threshold (or the afterglow of it), outside P0 and sealed actions. */
    public boolean exposed(ServerLevel level, AmanitaEntity a) {
        if (!phase.fighting() || phase == Phase.P0_BLOOM_OPENS || a.sealed()) return false;
        return lux(level, a) >= threshold() || level.getGameTime() < exposedUntil;
    }

    public boolean bared(long now) { return now < baredUntil; }

    /** Damage multiplier (DESIGN.md §7). 0 = immune. */
    public float damageMultiplier(ServerLevel level, AmanitaEntity a) {
        return switch (phase) {
            case DORMANT, CLEARED, P0_BLOOM_OPENS -> 0f;
            case DEFEATED -> 1f;
            default -> {
                if (a.sealed()) yield 0f;
                if (!exposed(level, a)) yield AmanitaConfig.DARK_MULT.get().floatValue();
                int t = threshold();
                float over = Math.max(0, Math.min(1, (lux(level, a) - t) / (float) Math.max(1, 15 - t)));
                float m = 1f + AmanitaConfig.BRIGHT_BONUS.get().floatValue() * over;
                if (bared(level.getGameTime())) m *= AmanitaConfig.BARED_MULT.get().floatValue();
                yield m;
            }
        };
    }

    // ================================================================== lifecycle
    /** Try to start from DORMANT. The pack may veto through {@link AmanitaEvents#START_CHECK}. */
    public boolean tryStart(ServerLevel level, @Nullable ServerPlayer trigger) {
        if (phase != Phase.DORMANT) return false;
        if (trigger != null) {
            EventResult r = AmanitaEvents.START_CHECK.invoker().check(level, origin, trigger);
            if (r.isFalse()) {
                trigger.displayClientMessage(Component.translatable("amanita.knocker.denied").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
        }
        List<ServerPlayer> ps = participants(level);
        fightTicks = 0;
        darkTicks = 0;
        deepExposed = 0;
        deepCount = 0;
        deepHealed = 0;
        snuffedOnce = fullSnuffDone = closingPending = relitByPlayers = anyDeep = wasExposed = firstLightAwarded = false;
        exposedUntil = baredUntil = hushUntil = exposedTotal = 0;
        deaths = snuffs = lightsPlaced = lightsSnuffed = lightsEaten = 0;
        eaterClock = spawnClock = 0;
        participantsEver.clear();
        ledger.clear();
        amanitaHp = 0;
        restoreAll(level);
        // the bloom opens: whatever light was carried in before the fight goes out
        snuffAll(level, "full_snuff", false);
        setPhase(level, Phase.P0_BLOOM_OPENS);
        forceChunks(level, true);
        HollowBuilder.closeMouth(level, origin);
        spawnAmanita(level, true);
        for (ServerPlayer p : ps) {
            title(p, "amanita.title.bloom", "amanita.title.bloom.sub");
            award(p, "enter_hollow");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 20);
        }
        AnimFx.play(level, Vec3.atCenterOf(origin), "amanita.lockdown", 12f, 1f);
        AmanitaEvents.ENCOUNTER_STARTED.invoker().started(level, origin, ps);
        BossEvents.STARTED.invoker().started(level, AmanitaBoss.ID, origin, ps);
        return true;
    }

    public void reset(ServerLevel level, String reason) {
        AmanitaEntity a = amanita(level);
        if (a != null) a.discard();
        amanitaId = null;
        dissolveAdds(level, true, true);
        bar.clear();
        restoreAll(level);
        HollowBuilder.openMouth(level, origin);
        forceChunks(level, false);
        ledger.clear();
        callStacks.clear();
        lightOverride = -1;
        setPhase(level, Phase.DORMANT);
        AmanitaEvents.RESET.invoker().reset(level, origin, reason);
    }

    /** Cover and braziers back to a fresh hollow. */
    private void restoreAll(ServerLevel level) {
        if (level.isLoaded(origin)) {
            HollowBuilder.restoreCover(level, origin);
            HollowBuilder.restoreBraziers(level, origin);
        }
        coverRegen.clear();
    }

    private void setPhase(ServerLevel level, Phase to) {
        Phase from = phase;
        phase = to;
        phaseTicks = 0;
        setDirty();
        if (from != to) {
            SkyloreBosses.LOG.info("Amanita hollow {}: {} -> {}", origin.toShortString(), from, to);
            AmanitaEvents.PHASE_CHANGED.invoker().changed(level, origin, from, to);
        }
    }

    // ================================================================== tick
    void tick(ServerLevel level) {
        phaseTicks++;
        long now = level.getGameTime();
        switch (phase) {
            case DORMANT -> {
                if (phaseTicks % 10 == 0) {
                    List<ServerPlayer> in = level.getEntitiesOfClass(ServerPlayer.class, HollowLayout.trigger(origin), p -> !p.isSpectator());
                    if (!in.isEmpty()) tryStart(level, in.get(0));
                }
                return;
            }
            case CLEARED -> {
                if (AmanitaConfig.REMATCH.get() && now - clearedAt > AmanitaConfig.REMATCH_DELAY_TICKS.get()) reset(level, "rematch");
                return;
            }
            case DEFEATED -> {
                if (phaseTicks == 160) {
                    HollowBuilder.openMouth(level, origin);
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
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, HollowLayout.fallZone(origin), p -> !p.isSpectator() && p.isAlive()))
            rescue(level, p);
        List<ServerPlayer> ps = participants(level);
        if (ps.isEmpty()) {
            if (++idleTicks > AmanitaConfig.DORMANT_TIMEOUT_TICKS.get()) {
                idleTicks = 0;
                SkyloreBosses.LOG.info("Amanita hollow {} reset: no participants", origin.toShortString());
                reset(level, "abandoned");
            }
            return;
        }
        idleTicks = 0;
        fightTicks++;
        for (ServerPlayer p : ps) participantsEver.add(p.getUUID());
        AmanitaEntity a = amanita(level);
        if (a == null) {
            if (++amanitaMissing >= 40) spawnAmanita(level, false);
        } else {
            amanitaMissing = 0;
            if (phaseTicks % 20 == 0) amanitaHp = a.getHealth();
        }
        if (now - censusAt >= 20) refreshCensus(level);
        if (a != null) tickGate(level, a, now);
        if (a != null && !hold) {
            switch (phase) {
                case P0_BLOOM_OPENS -> tickBloomOpens(level, a, ps);
                case P1_DARK_IMMUNITY -> {
                    if (a.getHealth() <= a.getMaxHealth() * AmanitaConfig.SNUFF_HP.get() && !snuffedOnce) onHalf(level, a);
                    else stall(level, a, AmanitaConfig.STALL_P1_TICKS.get());
                }
                case P2_FULL_SNUFF -> tickSnuffed(level, a, ps);
                case P3_LIT_DUEL -> stall(level, a, AmanitaConfig.STALL_P3_TICKS.get());
                case P4_DEEP_BLOOM -> tickDeep(level, a, ps, now);
                default -> {}
            }
            tickAdds(level, a);
        }
        if (phaseTicks % 20 == 0) tickHollowCall(level, a, ps);
        if (phaseTicks % 20 == 0) tickCover(level, now);
        if (phaseTicks % 40 == 0) keepBraziers(level);
        if (phaseTicks % 5 == 0) updateBar(level, a);
        if (phaseTicks % 20 == 0) bar.syncPlayers(ps);
    }

    private void tickGate(ServerLevel level, AmanitaEntity a, long now) {
        int lux = lux(level, a);
        boolean raw = phase.fighting() && phase != Phase.P0_BLOOM_OPENS && !a.sealed() && lux >= threshold();
        if (raw) exposedUntil = now + AmanitaConfig.AFTERGLOW_TICKS.get();
        boolean ex = exposed(level, a);
        if (ex && !wasExposed) {
            AmanitaEvents.EXPOSED.invoker().exposed(level, origin, lux);
            a.onExposed(lux > lastLux + 3);
        }
        if (ex) exposedTotal++;
        wasExposed = ex;
        lastLux = lux;
        a.setExposedSynced(ex);
    }

    private void tickBloomOpens(ServerLevel level, AmanitaEntity a, List<ServerPlayer> ps) {
        if (phaseTicks == 100) a.script(AmanitaAction.BLOOM_OPEN);
        if (phaseTicks >= AmanitaConfig.P0_TICKS.get()) {
            for (ServerPlayer p : ps) {
                p.displayClientMessage(Component.translatable("amanita.log.p1", HollowLayout.BRAZIERS.length).withStyle(ChatFormatting.DARK_PURPLE), false);
                title(p, "amanita.title.p1", "amanita.title.p1.sub");
            }
            darkTicks = 0;
            setPhase(level, Phase.P1_DARK_IMMUNITY);
        }
    }

    /** P1 -> P2: the first time her HP reaches the snuff threshold. Damage is clamped there so a burst cannot skip it. */
    public void onHalf(ServerLevel level, AmanitaEntity a) {
        if (phase != Phase.P1_DARK_IMMUNITY || snuffedOnce) return;
        snuffedOnce = true;
        fullSnuffDone = false;
        a.resetForPhase();
        setPhase(level, Phase.P2_FULL_SNUFF);
        a.script(AmanitaAction.FULL_SNUFF);
        for (ServerPlayer p : participants(level)) {
            title(p, "amanita.title.snuff", "amanita.title.snuff.sub");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 25);
        }
        AnimFx.play(level, a.position(), "amanita.snuff.full", 16f, 0.8f);
    }

    /** Called by FULL_SNUFF when it lands: the P2 opener, or the P4 closing snuff. */
    public void onFullSnuff(ServerLevel level, AmanitaEntity a) {
        boolean closing = phase == Phase.P4_DEEP_BLOOM;
        int n = snuffAll(level, closing ? "closing_snuff" : "full_snuff", true);
        applyDarkness(level, Vec3.atCenterOf(origin), -1, AmanitaConfig.FULL_SNUFF_DARKNESS_TICKS.get());
        for (ServerPlayer p : participants(level)) {
            SBNetwork.sendScreenFx(p, SBNetwork.FX_TAKEN, 30);
            p.displayClientMessage(Component.translatable(closing ? "amanita.log.closing" : "amanita.log.snuffed", n).withStyle(ChatFormatting.DARK_PURPLE), false);
        }
        if (closing) {
            closingPending = false;
            exitDeep(level, a, false);
            return;
        }
        if (phase == Phase.P2_FULL_SNUFF) {
            fullSnuffDone = true;
            // the relight happens under pressure: a wave that fights players and one lamp-eater for the first new light
            int n2 = Math.min(4, participants(level).size());
            for (int i = 0; i < n2 + 1; i++) spawnAdd(level, AmanitaEntities.HOLLOW_SPAWN.get(), i);
            spawnAdd(level, AmanitaEntities.LAMP_EATER.get(), 3);
        }
    }

    private void tickSnuffed(ServerLevel level, AmanitaEntity a, List<ServerPlayer> ps) {
        int n = census.size();
        if (fullSnuffDone && n >= AmanitaConfig.RELIGHT_SOURCES.get()) {
            relitByPlayers = true;
            for (ServerPlayer p : ps) {
                award(p, "relit");
                title(p, "amanita.title.lit_duel", "amanita.title.lit_duel.sub");
            }
            enterLitDuel(level, a);
        } else if (phaseTicks >= AmanitaConfig.P2_MAX_TICKS.get() && !a.sealed()) {
            for (ServerPlayer p : ps) title(p, "amanita.title.lit_duel", "amanita.title.lit_duel_dark.sub");
            enterLitDuel(level, a);
        }
    }

    private void enterLitDuel(ServerLevel level, AmanitaEntity a) {
        darkTicks = 0;
        eaterClock = 0;
        spawnClock = 0;
        setPhase(level, Phase.P3_LIT_DUEL);
    }

    /** P1/P3 -> P4 once she has stayed closed in the dark for {@code limit} ticks. */
    private void stall(ServerLevel level, AmanitaEntity a, int limit) {
        if (a.sealed()) return;
        if (exposed(level, a)) {
            darkTicks = 0;
            return;
        }
        if (++darkTicks >= limit) enterDeep(level, a);
    }

    private void enterDeep(ServerLevel level, AmanitaEntity a) {
        deepCount++;
        anyDeep = true;
        deepExposed = 0;
        deepHealed = 0;
        darkTicks = 0;
        closingPending = false;
        a.resetForPhase();
        setPhase(level, Phase.P4_DEEP_BLOOM);
        a.script(AmanitaAction.DEEP_BLOOM);
        for (ServerPlayer p : participants(level)) {
            title(p, "amanita.title.deep", "amanita.title.deep.sub");
            award(p, "deep_bloom");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 15);
        }
        AnimFx.play(level, a.position(), "amanita.deep.bloom", 16f, 0.9f);
        AmanitaEvents.DEEP_BLOOM.invoker().bloomed(level, origin, deepCount);
    }

    /** DEEP_BLOOM landing: the hollow-spawn sink back into the loam, two lamp-eaters climb out. */
    public void onDeepRooted(ServerLevel level) {
        dissolveAdds(level, false, true);
        spawnAdd(level, AmanitaEntities.LAMP_EATER.get(), 0);
        spawnAdd(level, AmanitaEntities.LAMP_EATER.get(), 1);
    }

    private void tickDeep(ServerLevel level, AmanitaEntity a, List<ServerPlayer> ps, long now) {
        if (a.sealed()) return;
        if (exposed(level, a)) {
            if (++deepExposed >= AmanitaConfig.DEEP_BREAK_TICKS.get()) {
                for (ServerPlayer p : ps) {
                    title(p, "amanita.title.forced", "amanita.title.forced.sub");
                    award(p, "forced_open");
                }
                exitDeep(level, a, true);
                return;
            }
        } else if (phaseTicks % 10 == 0 && !a.isDeadOrDying()) {
            // closed in deep bloom: she regenerates, up to a cap per bloom and never back above the snuff threshold
            float max = a.getMaxHealth();
            float cap = (float) (max * AmanitaConfig.DEEP_REGEN_CAP.get()) - deepHealed;
            float ceiling = snuffedOnce ? (float) (max * AmanitaConfig.SNUFF_HP.get()) : max;
            float amt = Math.min((float) (max * AmanitaConfig.DEEP_REGEN.get() / 2), Math.min(cap, ceiling - a.getHealth()));
            if (amt > 0) {
                a.heal(amt);
                deepHealed += amt;
                amanitaHp = a.getHealth();
                AnimFx.serverBurst(level, SBParticles.get("gill_glow"), a.position().add(0, 1, 0), 4, 0.6, 0.03);
            }
        }
        if (phaseTicks >= AmanitaConfig.DEEP_MAX_TICKS.get() && !closingPending) {
            closingPending = true;
            a.script(AmanitaAction.FULL_SNUFF);
            for (ServerPlayer p : ps) p.displayClientMessage(Component.translatable("amanita.warn.closing").withStyle(ChatFormatting.RED), true);
        }
    }

    /** P4 exit: forced open (bared, stagger) or after the closing snuff; back to P1 before the first snuff, P3 after. */
    private void exitDeep(ServerLevel level, AmanitaEntity a, boolean forcedOpen) {
        long now = level.getGameTime();
        if (forcedOpen) {
            baredUntil = now + AmanitaConfig.BARED_TICKS.get();
            a.bare();
        } else {
            a.resetForPhase();
        }
        darkTicks = 0;
        deepExposed = 0;
        closingPending = false;
        eaterClock = 0;
        spawnClock = 0;
        setPhase(level, snuffedOnce ? Phase.P3_LIT_DUEL : Phase.P1_DARK_IMMUNITY);
    }

    // ================================================================== adds (DESIGN.md §10)
    private void tickAdds(ServerLevel level, AmanitaEntity a) {
        boolean lit = !census.isEmpty();
        switch (phase) {
            case P1_DARK_IMMUNITY -> {
                if (lit && ++eaterClock >= AmanitaConfig.EATER_P1_TICKS.get()) {
                    eaterClock = 0;
                    if (eaters(level).size() < AmanitaConfig.EATER_MAX_P1.get()) spawnAdd(level, AmanitaEntities.LAMP_EATER.get(), level.random.nextInt(8));
                }
            }
            case P3_LIT_DUEL, P4_DEEP_BLOOM -> {
                if (lit && ++eaterClock >= AmanitaConfig.EATER_P3_TICKS.get()) {
                    eaterClock = 0;
                    if (eaters(level).size() < AmanitaConfig.EATER_MAX_P3.get()) spawnAdd(level, AmanitaEntities.LAMP_EATER.get(), level.random.nextInt(8));
                }
                if (phase == Phase.P3_LIT_DUEL && ++spawnClock >= AmanitaConfig.SPAWN_P3_TICKS.get()) {
                    spawnClock = 0;
                    if (spawns(level).size() < AmanitaConfig.SPAWN_MAX.get()) spawnAdd(level, AmanitaEntities.HOLLOW_SPAWN.get(), level.random.nextInt(8));
                }
            }
            default -> {}
        }
    }

    /** Spawn one add at vent {@code i % vents}, with a rising animation. */
    public Mob spawnAdd(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, int i) {
        List<BlockPos> vents = HollowLayout.vents(origin);
        BlockPos v = vents.get(Math.floorMod(i, vents.size()));
        Mob m = type.create(level);
        if (m == null) return null;
        m.moveTo(v.getX() + 0.5, v.getY(), v.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        if (m instanceof LampEaterEntity e) e.bindHollow(origin);
        if (m instanceof HollowSpawnEntity s) s.bindHollow(origin);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(v), MobSpawnType.EVENT, null);
        level.addFreshEntity(m);
        AnimFx.serverBurst(level, SBParticles.get("hollow_spore"), Vec3.atBottomCenterOf(v).add(0, 0.4, 0), 30, 0.6, 0.05);
        AnimFx.play(level, Vec3.atCenterOf(v), m instanceof LampEaterEntity ? "amanita.eater.emerge" : "amanita.spawn.emerge", 4f, 1f);
        return m;
    }

    public List<LampEaterEntity> eaters(ServerLevel level) {
        return level.getEntitiesOfClass(LampEaterEntity.class, HollowLayout.volume(origin), e -> e.isAlive() && origin.equals(e.hollowOrigin()));
    }

    public List<HollowSpawnEntity> spawns(ServerLevel level) {
        return level.getEntitiesOfClass(HollowSpawnEntity.class, HollowLayout.volume(origin), e -> e.isAlive() && origin.equals(e.hollowOrigin()));
    }

    /** Sink adds back into the loam (phase change, victory, reset). */
    public void dissolveAdds(ServerLevel level, boolean eatersToo, boolean spawnToo) {
        if (eatersToo) for (LampEaterEntity e : eaters(level)) e.burrow();
        if (spawnToo) for (HollowSpawnEntity s : spawns(level)) s.dissolve();
    }

    // ================================================================== lights: census, placement, snuff
    private void refreshCensus(ServerLevel level) {
        census = Lights.census(level, origin);
        censusAt = level.getGameTime();
        ledger.keySet().removeIf(p -> level.isLoaded(p) && Lights.emission(level, p, level.getBlockState(p)) <= 0);
    }

    /** Light sources burning in the interior (refreshed every second, and right after any snuff or placement). */
    public List<BlockPos> census(ServerLevel level) {
        if (level.getGameTime() - censusAt >= 20) refreshCensus(level);
        return census;
    }

    @Nullable
    public UUID placedBy(BlockPos p) { return ledger.get(p); }

    public boolean hushed(long now) { return now < hushUntil; }

    public void hush(ServerLevel level, int ticks) {
        hushUntil = level.getGameTime() + ticks;
    }

    /**
     * Snuff every light in range. @param radius < 0 for the whole interior. @return lights put out.
     * Wall-mounted and ceiling lights are not special: a snuff reaches everything in range.
     */
    public int snuff(ServerLevel level, Vec3 c, double radius, String cause) {
        int n = 0;
        boolean drops = AmanitaConfig.SNUFF_DROPS.get();
        for (BlockPos p : Lights.inRange(level, origin, c, radius)) {
            if (Lights.snuffOne(level, p, drops)) {
                n++;
                ledger.remove(p);
                AnimFx.serverBurst(level, SBParticles.get("snuff_smoke"), Vec3.atCenterOf(p), 8, 0.2, 0.02);
            }
        }
        if (n > 0) {
            snuffs++;
            lightsSnuffed += n;
            AnimFx.play(level, c, "amanita.snuff.hiss", 6f, 0.9f + level.random.nextFloat() * 0.2f);
            AmanitaEvents.LIGHTS_SNUFFED.invoker().snuffed(level, origin, cause, n);
        }
        censusAt = -1000;
        setDirty();
        return n;
    }

    private int snuffAll(ServerLevel level, String cause, boolean count) {
        if (!level.isLoaded(origin)) return 0;
        int before = snuffs;
        int n = snuff(level, Vec3.atCenterOf(origin), -1, cause);
        if (!count) {
            snuffs = before;
            lightsSnuffed -= n;
        }
        return n;
    }

    /** One light eaten by a lamp-eater: no drop. */
    public boolean eat(ServerLevel level, BlockPos p) {
        if (!Lights.snuffOne(level, p, false)) return false;
        ledger.remove(p);
        lightsEaten++;
        censusAt = -1000;
        AmanitaEvents.LIGHTS_SNUFFED.invoker().snuffed(level, origin, "lamp_eater", 1);
        setDirty();
        return true;
    }

    /** Darkness to every participant within {@code radius} of {@code c} (radius < 0: all); Tenebris also get chilled. */
    public void applyDarkness(ServerLevel level, Vec3 c, double radius, int ticks) {
        for (ServerPlayer p : participants(level)) {
            if (radius >= 0 && p.position().distanceTo(c) > radius) continue;
            if (ticks > 0) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, ticks, 0, false, false, true));
            if (origin(p) == OriginKind.TENEBRIS && AmanitaConfig.CHILL_TICKS.get() > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AmanitaConfig.CHILL_TICKS.get(), 0));
                if (hint(p, 200)) p.displayClientMessage(Component.translatable("amanita.origin.tenebris_chill").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
    }

    /** BlockEvent.EntityPlaceEvent inside the hollow. @return true to cancel the placement */
    public boolean onBlockPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Player player) {
        if (!phase.fighting() || !HollowLayout.inInterior(origin, pos)) return false;
        int e = Lights.emission(level, pos, placed);
        if (e <= 0) return false;
        if (hushed(level.getGameTime())) {
            if (player instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("amanita.log.hushed").withStyle(ChatFormatting.GRAY), true);
            return true;
        }
        recordLight(level, pos, player instanceof ServerPlayer sp ? sp : null, e);
        return false;
    }

    private void recordLight(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by, int emission) {
        if (by != null) ledger.put(pos.immutable(), by.getUUID());
        lightsPlaced++;
        censusAt = -1000;
        if (phase == Phase.P1_DARK_IMMUNITY && lightsPlaced == 1) eaterClock = Math.max(eaterClock, AmanitaConfig.EATER_P1_TICKS.get() - 100);
        if (by != null && emission >= AmanitaConfig.SOURCE_MIN_EMISSION.get() && hint(by, 600) && phase != Phase.P0_BLOOM_OPENS)
            by.displayClientMessage(Component.translatable("amanita.log.light_placed").withStyle(ChatFormatting.YELLOW), true);
        AmanitaEvents.LIGHT_PLACED.invoker().placed(level, origin, pos, by, emission);
        setDirty();
    }

    public void onBlockBroken(ServerLevel level, BlockPos pos) {
        if (ledger.remove(pos) != null) censusAt = -1000;
    }

    /** A player uses a brazier (with an igniter in hand, or bare-handed). */
    public void onBrazierUse(ServerLevel level, BlockPos pos, ServerPlayer p, ItemStack stack) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(AmanitaBlocks.HOLLOW_BRAZIER.get()) || s.getValue(HollowBrazierBlock.LIT)) return;
        if (phase.fighting() && hushed(level.getGameTime())) {
            p.displayClientMessage(Component.translatable("amanita.log.hushed").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (stack.isEmpty()) {
            if (!(AmanitaConfig.LAEVIS_HAND_IGNITE.get() && origin(p) == OriginKind.LAEVIS)) {
                p.displayClientMessage(Component.translatable("amanita.brazier.needs_flame").withStyle(ChatFormatting.GRAY), true);
                return;
            }
            if (hint(p, 400)) p.displayClientMessage(Component.translatable("amanita.origin.laevis_ignite").withStyle(ChatFormatting.GOLD), true);
        } else if (!p.getAbilities().instabuild) {
            if (stack.isDamageableItem()) stack.hurtAndBreak(1, p, EquipmentSlot.MAINHAND);
            else if (stack.is(Items.FIRE_CHARGE)) stack.shrink(1);
        }
        level.setBlock(pos, s.setValue(HollowBrazierBlock.LIT, true), 3);
        AnimFx.play(level, Vec3.atCenterOf(pos), "amanita.brazier.light", 3f, 1f);
        AnimFx.serverBurst(level, SBParticles.get("ember"), Vec3.atCenterOf(pos).add(0, 0.4, 0), 12, 0.3, 0.05);
        if (phase.fighting()) recordLight(level, pos, p, 15);
    }

    private void keepBraziers(ServerLevel level) {
        for (BlockPos p : HollowLayout.braziers(origin)) {
            if (level.isLoaded(p) && !level.getBlockState(p).is(AmanitaBlocks.HOLLOW_BRAZIER.get()))
                level.setBlock(p, AmanitaBlocks.HOLLOW_BRAZIER.get().defaultBlockState(), 3);
        }
    }

    // ================================================================== origins (DESIGN.md §13)
    public static OriginKind origin(ServerPlayer p) {
        for (var r : AmanitaEvents.originResolvers()) {
            String id = r.apply(p);
            if (id == null) continue;
            if (AmanitaConfig.TENEBRIS_ORIGINS.get().contains(id)) return OriginKind.TENEBRIS;
            if (AmanitaConfig.LAEVIS_ORIGINS.get().contains(id)) return OriginKind.LAEVIS;
        }
        Set<String> tags = p.getTags();
        if (tags.contains(AmanitaConfig.TENEBRIS_TAG.get())) return OriginKind.TENEBRIS;
        if (tags.contains(AmanitaConfig.LAEVIS_TAG.get())) return OriginKind.LAEVIS;
        return OriginKind.OTHER;
    }

    /**
     * Tenebris pressure: the dark is home, so the hollow calls to them. One stack per second in the dark within 16
     * blocks of her; light clears it; a full call roots them (Slowness III) and makes them her preferred target.
     */
    private void tickHollowCall(ServerLevel level, @Nullable AmanitaEntity a, List<ServerPlayer> ps) {
        if (a == null || phase == Phase.P0_BLOOM_OPENS) return;
        int max = AmanitaConfig.CALL_STACKS.get();
        for (ServerPlayer p : ps) {
            if (origin(p) != OriginKind.TENEBRIS || p.isCreative()) continue;
            int lit = Lights.at(level, BlockPos.containing(p.getEyePosition()));
            int n = callStacks.getOrDefault(p.getUUID(), 0);
            if (lit >= AmanitaConfig.LIGHT_THRESHOLD.get() || p.distanceTo(a) > 16) {
                if (n > 0) callStacks.put(p.getUUID(), 0);
                continue;
            }
            n++;
            if (n >= max) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AmanitaConfig.ROOTED_TICKS.get(), 2));
                a.addThreat(p.getUUID(), 60f);
                a.markRooted(p.getUUID(), level.getGameTime() + AmanitaConfig.ROOTED_TICKS.get() + 40);
                p.displayClientMessage(Component.translatable("amanita.origin.rooted").withStyle(ChatFormatting.DARK_PURPLE), true);
                AnimFx.play(level, p.position(), "amanita.call.root", 3f, 1f);
                n = max / 2;
            } else {
                p.displayClientMessage(Component.translatable("amanita.origin.call", n, max).withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            callStacks.put(p.getUUID(), n);
        }
    }

    public int callStacks(UUID p) { return callStacks.getOrDefault(p, 0); }

    // ================================================================== cover
    private void tickCover(ServerLevel level, long now) {
        for (BlockPos p : HollowLayout.coverBlocks(origin)) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).isAir()) coverRegen.putIfAbsent(p.immutable(), now + AmanitaConfig.COVER_REGEN_TICKS.get());
        }
        coverRegen.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            BlockPos p = e.getKey();
            if (!level.getBlockState(p).isAir()) return true;
            if (!level.getEntitiesOfClass(Entity.class, new AABB(p), x -> x instanceof Player || x instanceof AmanitaEntity).isEmpty()) return false;
            level.setBlock(p, AmanitaBlocks.GILL_SHELF.get().defaultBlockState(), 3);
            AnimFx.serverBurst(level, SBParticles.get("hollow_spore"), Vec3.atCenterOf(p), 8, 0.4, 0.02);
            return true;
        });
    }

    // ================================================================== amanita
    @Nullable
    public AmanitaEntity amanita(ServerLevel level) {
        if (amanitaId == null) return null;
        Entity e = level.getEntity(amanitaId);
        return e instanceof AmanitaEntity a && a.isAlive() ? a : null;
    }

    private float maxHp(ServerLevel level) {
        int n = Math.max(1, participants(level).size());
        return (float) (AmanitaConfig.HP.get() * Math.min(AmanitaConfig.MP_HP_CAP.get(), 1 + AmanitaConfig.MP_HP_PER_PLAYER.get() * (n - 1)));
    }

    private void spawnAmanita(ServerLevel level, boolean fresh) {
        Entity old = amanitaId == null ? null : level.getEntity(amanitaId);
        if (old != null) old.discard();
        AmanitaEntity a = AmanitaEntities.AMANITA.get().create(level);
        if (a == null) return;
        Vec3 at = HollowLayout.bloomBed(origin);
        a.moveTo(at.x, at.y, at.z, 180, 0);
        a.bindHollow(origin);
        float max = maxHp(level);
        a.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        a.setHealth(!fresh && amanitaHp > 0 ? Math.min(max, amanitaHp) : max);
        a.finalizeSpawn(level, level.getCurrentDifficultyAt(origin), MobSpawnType.EVENT, null);
        level.addFreshEntity(a);
        if (fresh) a.triggerAnim("main", "rise");
        else AnimFx.serverBurst(level, SBParticles.get("hollow_spore"), at.add(0, 1.5, 0), 60, 0.8, 0.1);
        amanitaId = a.getUUID();
        amanitaHp = a.getHealth();
        amanitaMissing = 0;
        setDirty();
    }

    public void onAmanitaDamaged(AmanitaEntity a) {
        amanitaHp = a.getHealth();
    }

    /** The first hit that lands in a fight (the lesson learned). */
    public void onFirstLitHit(ServerLevel level) {
        if (firstLightAwarded) return;
        firstLightAwarded = true;
        for (ServerPlayer p : participants(level)) award(p, "first_light");
    }

    public void onAmanitaDied(ServerLevel level, AmanitaEntity a, DamageSource source) {
        if (!a.getUUID().equals(amanitaId) || !phase.fighting()) return;
        amanitaHp = 0;
        List<ServerPlayer> ps = participants(level);
        setPhase(level, Phase.DEFEATED);
        dissolveAdds(level, true, true);
        bar.update(0, 0, BossEvent.BossBarColor.WHITE, BossEvent.BossBarColor.WHITE, Component.translatable("amanita.status.down"),
                Component.translatable("amanita.bossbar.light_count", census.size()));
        String fn = AmanitaConfig.VICTORY_FUNCTION.get();
        for (ServerPlayer p : ps) {
            award(p, "bloom_wilted");
            if (!anyDeep) award(p, "no_deep_bloom");
            if (AmanitaConfig.GRANT_TROPHY.get()) p.getInventory().placeItemBackInInventory(new ItemStack(AmanitaItems.HOLLOW_BLOOM_CAP.get()));
            rollArtifacts(level, p);
            title(p, "amanita.title.victory", "amanita.title.victory.sub");
            AnimFx.play(level, p.position(), "amanita.victory", 1f, 1f);
            if (!fn.isEmpty()) {
                var server = level.getServer();
                server.getFunctions().get(ResourceLocation.parse(fn)).ifPresent(f ->
                        server.getFunctions().execute(f, p.createCommandSourceStack().withSuppressedOutput().withPermission(2)));
            }
        }
        AmanitaEvents.VICTORY.invoker().victory(level, origin, ps, new AmanitaEvents.EncounterStats(fightTicks, deaths, snuffs, lightsPlaced,
                lightsSnuffed, lightsEaten, deepCount, relitByPlayers, exposedTotal));
        BossEvents.DEFEATED.invoker().defeated(level, AmanitaBoss.ID, origin, ps);
    }

    /** The pack's shadow artifacts: one roll of the configured loot table per participant, then the pack event. */
    private void rollArtifacts(ServerLevel level, ServerPlayer p) {
        List<ItemStack> drops = new ArrayList<>();
        String id = AmanitaConfig.ARTIFACT_LOOT_TABLE.get();
        if (!id.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                LootTable t = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, rl));
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, p.position())
                        .withParameter(LootContextParams.THIS_ENTITY, p).create(LootContextParamSets.GIFT);
                drops.addAll(t.getRandomItems(params));
            }
        }
        AmanitaEvents.ARTIFACTS_ROLLED.invoker().rolled(level, origin, p, drops);
        for (ItemStack s : drops) if (!s.isEmpty()) p.getInventory().placeItemBackInInventory(s);
    }

    public void onPlayerDeath(ServerPlayer p) {
        if (phase.fighting() && HollowLayout.volume(origin).contains(p.position())) deaths++;
    }

    // ================================================================== players
    public List<ServerPlayer> participants(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, HollowLayout.volume(origin), p -> !p.isSpectator() && p.isAlive());
    }

    private void rescue(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(HollowLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.fallDistance = 0;
        p.hurt(level.damageSources().fellOutOfWorld(), 4f);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
        p.displayClientMessage(Component.translatable("amanita.log.rescue").withStyle(ChatFormatting.GRAY), true);
    }

    /** Knocker right-click. */
    public void onKnocker(ServerLevel level, ServerPlayer p) {
        AnimFx.play(level, p.position(), "amanita.knocker", 3f, 1f);
        switch (phase) {
            case DORMANT -> {
                readmit(level, p);
                tryStart(level, p);
            }
            case CLEARED, DEFEATED -> p.displayClientMessage(Component.translatable("amanita.knocker.cleared").withStyle(ChatFormatting.GRAY), true);
            default -> readmit(level, p);
        }
    }

    private void readmit(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(HollowLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.displayClientMessage(Component.translatable("amanita.knocker.readmit").withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // ================================================================== bar
    private void updateBar(ServerLevel level, @Nullable AmanitaEntity a) {
        float hp = a == null ? (amanitaHp > 0 ? 1 : 0) : a.getHealth() / a.getMaxHealth();
        int lux = a == null ? 0 : lux(level, a);
        int t = threshold();
        int n = census.size();
        boolean ex = a != null && exposed(level, a);
        BossEvent.BossBarColor color, lightColor = BossEvent.BossBarColor.WHITE;
        Component status, lightTitle;
        float lit = Math.min(1f, n / 10f);
        lightTitle = Component.translatable("amanita.bossbar.light_count", n);
        switch (phase) {
            case P0_BLOOM_OPENS -> { color = BossEvent.BossBarColor.WHITE; status = Component.translatable("amanita.status.p0"); }
            case P2_FULL_SNUFF -> {
                color = ex ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.BLUE;
                status = ex ? exposedStatus(level, a, lux, t) : Component.translatable("amanita.status.p2", lux, t);
                int need = AmanitaConfig.RELIGHT_SOURCES.get();
                lit = Math.min(1f, n / (float) need);
                lightColor = BossEvent.BossBarColor.BLUE;
                lightTitle = Component.translatable("amanita.bossbar.relight", Math.min(n, need), need);
            }
            case P4_DEEP_BLOOM -> {
                color = ex ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED;
                status = ex ? exposedStatus(level, a, lux, t) : Component.translatable("amanita.status.p4", lux, t);
                int need = AmanitaConfig.DEEP_BREAK_TICKS.get();
                lit = Math.min(1f, deepExposed / (float) need);
                lightColor = BossEvent.BossBarColor.RED;
                lightTitle = Component.translatable("amanita.bossbar.deep", Math.round(100f * deepExposed / need), t);
            }
            default -> {
                if (ex) { color = BossEvent.BossBarColor.YELLOW; status = exposedStatus(level, a, lux, t); }
                else { color = BossEvent.BossBarColor.PURPLE; status = Component.translatable("amanita.status.closed", lux, t); }
                if (n > 0) lightColor = BossEvent.BossBarColor.YELLOW;
            }
        }
        bar.update(hp, lit, color, lightColor, status, lightTitle);
    }

    private Component exposedStatus(ServerLevel level, AmanitaEntity a, int lux, int t) {
        int pct = Math.round(damageMultiplier(level, a) * 100);
        return Component.translatable(bared(level.getGameTime()) ? "amanita.status.bared" : "amanita.status.exposed", lux, t, pct);
    }

    // ================================================================== commands
    /** Designer: advance to the next beat. */
    public void skipPhase(ServerLevel level) {
        AmanitaEntity a = amanita(level);
        switch (phase) {
            case DORMANT, CLEARED -> {
                if (phase == Phase.CLEARED) reset(level, "skip");
                tryStart(level, null);
            }
            case P0_BLOOM_OPENS -> phaseTicks = AmanitaConfig.P0_TICKS.get() - 1;
            case P1_DARK_IMMUNITY -> {
                if (a != null) {
                    a.setHealth((float) (a.getMaxHealth() * AmanitaConfig.SNUFF_HP.get()));
                    onHalf(level, a);
                }
            }
            case P2_FULL_SNUFF -> {
                if (a != null) {
                    fullSnuffDone = true;
                    enterLitDuel(level, a);
                }
            }
            case P3_LIT_DUEL -> { if (a != null) enterDeep(level, a); }
            case P4_DEEP_BLOOM -> {
                if (a != null) {
                    a.resetForPhase();
                    exitDeep(level, a, true);
                }
            }
            default -> {}
        }
    }

    /** Designer: /skyloreamanita snuff [radius]. radius < 0 = whole hollow. */
    public int forceSnuff(ServerLevel level, double radius) {
        AmanitaEntity a = amanita(level);
        Vec3 c = a != null && radius >= 0 ? a.position() : Vec3.atCenterOf(origin);
        int n = snuff(level, c, radius, "command");
        applyDarkness(level, c, radius, AmanitaConfig.SNUFF_DARKNESS_TICKS.get());
        return n;
    }

    public String describe(ServerLevel level) {
        AmanitaEntity a = amanita(level);
        StringBuilder sb = new StringBuilder();
        sb.append("hollow ").append(origin.toShortString()).append(" phase=").append(phase.id()).append(" t=").append(phaseTicks)
                .append(" lights=").append(census(level).size()).append(" threshold=").append(threshold())
                .append(" dark=").append(darkTicks).append(" deepExposed=").append(deepExposed).append(" deeps=").append(deepCount)
                .append(" snuffed=").append(snuffedOnce).append(" relit=").append(relitByPlayers)
                .append(" hush=").append(Math.max(0, hushUntil - level.getGameTime()))
                .append(" eaters=").append(eaters(level).size()).append(" spawn=").append(spawns(level).size())
                .append(" placed=").append(lightsPlaced).append(" snuffedLights=").append(lightsSnuffed).append(" eaten=").append(lightsEaten)
                .append(" snuffs=").append(snuffs).append(" hold=").append(hold);
        if (lightOverride >= 0) sb.append(" lightOverride=").append(lightOverride);
        if (a != null) {
            sb.append(" lux=").append(lux(level, a)).append(" exposed=").append(exposed(level, a))
                    .append(" mult=").append(String.format(Locale.ROOT, "%.2f", damageMultiplier(level, a)));
            sb.append(System.lineSeparator()).append("  ").append(a.debugState());
        }
        return sb.toString();
    }

    public String describeLights(ServerLevel level) {
        StringBuilder sb = new StringBuilder("lights " + census(level).size() + ":");
        for (BlockPos p : census) {
            UUID u = ledger.get(p);
            sb.append(' ').append(p.toShortString()).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath());
            if (u != null) sb.append("@").append(u.toString(), 0, 8);
        }
        return sb.toString();
    }

    // ================================================================== helpers
    /** First-contact teaching lines, at most one per player per {@code cooldown} ticks. */
    public boolean hint(ServerPlayer p, int cooldown) {
        long now = p.level().getGameTime();
        if (hintAt.getOrDefault(p.getUUID(), 0L) > now) return false;
        hintAt.put(p.getUUID(), now + cooldown);
        return true;
    }

    private void forceChunks(ServerLevel level, boolean on) {
        if (forced == on) return;
        forced = on;
        ChunkPos a = new ChunkPos(origin.offset(-HollowLayout.WALL - 8, 0, -HollowLayout.WALL - 8));
        ChunkPos b = new ChunkPos(origin.offset(HollowLayout.WALL + 8, 0, HollowLayout.WALL + 12));
        for (int x = a.x; x <= b.x; x++) for (int z = a.z; z <= b.z; z++) level.setChunkForced(x, z, on);
        setDirty();
    }

    static void award(ServerPlayer p, String path) {
        var h = p.server.getAdvancements().get(SkyloreBosses.id("amanita/" + path));
        if (h != null) p.getAdvancements().award(h, "code");
    }

    private static void title(ServerPlayer p, String title, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.DARK_PURPLE)));
    }

    // ================================================================== persistence
    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Origin", NbtUtils.writeBlockPos(origin));
        t.putString("Phase", phase.name());
        t.putInt("PhaseTicks", phaseTicks);
        t.putLong("FightTicks", fightTicks);
        t.putInt("Dark", darkTicks);
        t.putInt("DeepExposed", deepExposed);
        t.putInt("Deeps", deepCount);
        t.putFloat("DeepHealed", deepHealed);
        t.putBoolean("Snuffed", snuffedOnce);
        t.putBoolean("FullSnuffDone", fullSnuffDone);
        t.putBoolean("Closing", closingPending);
        t.putBoolean("Relit", relitByPlayers);
        t.putBoolean("AnyDeep", anyDeep);
        t.putBoolean("FirstLight", firstLightAwarded);
        t.putLong("Bared", baredUntil);
        t.putLong("Hush", hushUntil);
        t.putLong("ExposedTotal", exposedTotal);
        t.putInt("Deaths", deaths);
        t.putInt("Snuffs", snuffs);
        t.putInt("Placed", lightsPlaced);
        t.putInt("LightsSnuffed", lightsSnuffed);
        t.putInt("Eaten", lightsEaten);
        t.putInt("EaterClock", eaterClock);
        t.putInt("SpawnClock", spawnClock);
        t.putBoolean("Forced", forced);
        t.putLong("ClearedAt", clearedAt);
        if (amanitaId != null) t.putUUID("Amanita", amanitaId);
        t.putFloat("AmanitaHp", amanitaHp);
        ListTag led = new ListTag();
        for (var e : ledger.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.put("P", NbtUtils.writeBlockPos(e.getKey()));
            c.putUUID("U", e.getValue());
            led.add(c);
        }
        t.put("Ledger", led);
        ListTag ev = new ListTag();
        for (UUID u : participantsEver) ev.add(NbtUtils.createUUID(u));
        t.put("Participants", ev);
        return t;
    }

    static Hollow load(CompoundTag t) {
        Hollow h = new Hollow(NbtUtils.readBlockPos(t, "Origin").orElse(BlockPos.ZERO));
        h.phase = Phase.byName(t.getString("Phase"));
        h.phaseTicks = t.getInt("PhaseTicks");
        h.fightTicks = t.getLong("FightTicks");
        h.darkTicks = t.getInt("Dark");
        h.deepExposed = t.getInt("DeepExposed");
        h.deepCount = t.getInt("Deeps");
        h.deepHealed = t.getFloat("DeepHealed");
        h.snuffedOnce = t.getBoolean("Snuffed");
        h.fullSnuffDone = t.getBoolean("FullSnuffDone");
        h.closingPending = t.getBoolean("Closing");
        h.relitByPlayers = t.getBoolean("Relit");
        h.anyDeep = t.getBoolean("AnyDeep");
        h.firstLightAwarded = t.getBoolean("FirstLight");
        h.baredUntil = t.getLong("Bared");
        h.hushUntil = t.getLong("Hush");
        h.exposedTotal = t.getLong("ExposedTotal");
        h.deaths = t.getInt("Deaths");
        h.snuffs = t.getInt("Snuffs");
        h.lightsPlaced = t.getInt("Placed");
        h.lightsSnuffed = t.getInt("LightsSnuffed");
        h.lightsEaten = t.getInt("Eaten");
        h.eaterClock = t.getInt("EaterClock");
        h.spawnClock = t.getInt("SpawnClock");
        h.forced = t.getBoolean("Forced");
        h.clearedAt = t.getLong("ClearedAt");
        h.amanitaId = t.hasUUID("Amanita") ? t.getUUID("Amanita") : null;
        h.amanitaHp = t.getFloat("AmanitaHp");
        for (Tag x : t.getList("Ledger", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            NbtUtils.readBlockPos(c, "P").ifPresent(p -> h.ledger.put(p, c.getUUID("U")));
        }
        for (Tag u : t.getList("Participants", Tag.TAG_INT_ARRAY)) h.participantsEver.add(NbtUtils.loadUUID(u));
        // a closing snuff or a full snuff interrupted by the reload is re-scripted by the entity's first tick
        return h;
    }

    /** After a reload the entity lost its action; re-script whatever the phase still owes (called by the entity). */
    public void resumeScripts(AmanitaEntity a) {
        if (phase == Phase.P2_FULL_SNUFF && !fullSnuffDone) a.script(AmanitaAction.FULL_SNUFF);
        else if (phase == Phase.P4_DEEP_BLOOM && closingPending) a.script(AmanitaAction.FULL_SNUFF);
    }
}
