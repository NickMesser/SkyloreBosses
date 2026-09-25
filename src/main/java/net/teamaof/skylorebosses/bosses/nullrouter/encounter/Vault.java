package net.teamaof.skylorebosses.bosses.nullrouter.encounter;

import dev.architectury.event.EventResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.nullrouter.NullRouterBoss;
import net.teamaof.skylorebosses.bosses.nullrouter.RouterConfig;
import net.teamaof.skylorebosses.bosses.nullrouter.api.RouterEvents;
import net.teamaof.skylorebosses.bosses.nullrouter.block.ChannelConsoleBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.block.Glyph;
import net.teamaof.skylorebosses.bosses.nullrouter.block.RequestLampBlock;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.NullRouterEntity;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.RetryPacketEntity;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.RouterAction;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterBlocks;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterEntities;
import net.teamaof.skylorebosses.bosses.nullrouter.registry.RouterItems;
import net.teamaof.skylorebosses.core.api.BossEvents;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.fx.Telegraph;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;

/**
 * One Automaton vault and the fight in it (DESIGN.md §4, §6, §7). Persisted inside {@link Vaults}, so a fight
 * survives relogs, restarts and chunk unloads: the chassis, the consoles and the lamps are views of this state and are
 * re-created or re-set from it. The boss bar is the unacked request queue; the chassis has no HP pool that matters.
 */
public final class Vault {
    public static final ResourceKey<DamageType> SHORT_CIRCUIT = ResourceKey.create(Registries.DAMAGE_TYPE, SkyloreBosses.id("short_circuit"));
    public static final ResourceKey<DamageType> NULL_ROUTE = ResourceKey.create(Registries.DAMAGE_TYPE, SkyloreBosses.id("null_route"));

    /** The ACK window's sub-state. */
    public enum Ack { NONE, SOLIDIFY, OPEN, RELEASE }

    public final BlockPos origin;
    private Phase phase = Phase.DORMANT;
    private int phaseTicks;
    private long fightTicks;
    // queue
    private int queue, queueStart, queueLow, queueRef;
    // request and consoles (glyph ordinals, channels A B C)
    private final int[] consoles = {0, 1, 2};
    private int[] reqA = {0, 0, 0};
    @Nullable private int[] reqB;
    private boolean headB;
    private int serial;
    // arming (misread grace) and the ack window
    private int armTicks, armTarget = -1;
    @Nullable private UUID armActor, lastFlipper;
    private Ack ack = Ack.NONE;
    private int ackTicks, ackCleared, wetTicks;
    private double ackCredit;
    private boolean windowWet, dryHinted, wetHinted, capHinted;
    // timers
    private int stallTicks, sinceWetAck, altTicks = -1, stormTicks, stormFlickTicks, idleTicks, nudgeTicks;
    private final long[] consoleReadyAt = new long[3];
    private final int[] alertTicks = new int[3];
    private final long[] gateOpenAt = new long[VaultLayout.ALCOVES.length];
    // stats
    private int acks, acksInPhase, wetAcks, misroutes, stalls, storms, retriesKilled, deaths;
    private boolean anyMisroute, stormSurvived;
    /** Designer hold: the chassis draws no attacks and the pressure clocks (stall, rotation, storm, P4 triggers) pause. */
    private boolean held;
    // entities and chunks
    @Nullable private UUID chassisId;
    private int chassisMissing;
    private long clearedAt;
    private boolean forced;
    private final Set<UUID> participantsEver = new HashSet<>();
    private final Map<UUID, Long> misrouteUntil = new HashMap<>();
    // transient
    private final transient RouterBossBar bar = new RouterBossBar();
    private transient Runnable dirty = () -> {};
    private final transient Map<BlockPos, Long> waterSeen = new HashMap<>();
    private final transient Map<UUID, Integer> campTicks = new HashMap<>();
    private final transient Map<UUID, Long> hintAt = new HashMap<>();
    private transient boolean displayDirty = true;

    public Vault(BlockPos origin) {
        this.origin = origin.immutable();
    }

    void setDirtyHook(Runnable r) { dirty = r; }

    private void setDirty() { dirty.run(); }

    // ================================================================== read-only view
    public Phase phase() { return phase; }
    public int phaseTicks() { return phaseTicks; }
    public int queue() { return queue; }
    public int queueStart() { return queueStart; }
    public Ack ack() { return ack; }
    public boolean ackOpen() { return ack == Ack.OPEN; }
    public boolean inAck() { return ack != Ack.NONE; }
    public int[] head() { return headB && reqB != null ? reqB : reqA; }
    @Nullable public int[] decoy() { return !phase.dualQueue() || reqB == null ? null : headB ? reqA : reqB; }
    public int console(int i) { return consoles[i]; }
    public int[] consoles() { return consoles.clone(); }
    public boolean wet() { return wetTicks > 0; }
    public int serial() { return serial; }
    public boolean held() { return held; }

    public void setHeld(boolean h) {
        held = h;
        setDirty();
    }

    /** Console i shows the head request's glyph (retries and console_flick target these). */
    public boolean consoleMatchesHead(int i) { return consoles[i] == head()[i]; }

    public boolean requestIssued() { return serial > 0; }

    // ================================================================== lifecycle
    /** Try to start from DORMANT. The pack may veto through {@link RouterEvents#START_CHECK}. */
    public boolean tryStart(ServerLevel level, @Nullable ServerPlayer trigger) {
        if (phase != Phase.DORMANT) return false;
        if (trigger != null) {
            EventResult r = RouterEvents.START_CHECK.invoker().check(level, origin, trigger);
            if (r.isFalse()) {
                trigger.displayClientMessage(Component.translatable("null_router.terminal.denied").withStyle(ChatFormatting.GRAY), true);
                return false;
            }
        }
        List<ServerPlayer> ps = participants(level);
        queueStart = RouterConfig.queueStart(ps.size());
        queue = queueStart;
        queueLow = queue;
        queueRef = queue;
        fightTicks = 0;
        serial = 0;
        acks = acksInPhase = wetAcks = misroutes = stalls = storms = retriesKilled = deaths = 0;
        anyMisroute = false;
        stormSurvived = false;
        participantsEver.clear();
        misrouteUntil.clear();
        reqB = null;
        headB = false;
        clearAck();
        disarm();
        restoreAll(level);
        setPhase(level, Phase.P0_BOOT);
        forceChunks(level, true);
        VaultBuilder.closeDoor(level, origin);
        spawnChassis(level, true);
        for (ServerPlayer p : ps) {
            title(p, "null_router.title.boot", "null_router.title.boot.sub", ChatFormatting.AQUA);
            award(p, "enter_vault");
            SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 15);
        }
        AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.lockdown", 12f, 1f);
        RouterEvents.ENCOUNTER_STARTED.invoker().started(level, origin, ps, queue);
        BossEvents.STARTED.invoker().started(level, NullRouterBoss.ID, origin, ps);
        return true;
    }

    public void reset(ServerLevel level, String reason) {
        NullRouterEntity c = chassis(level);
        if (c != null) c.discard();
        chassisId = null;
        for (RetryPacketEntity r : retries(level)) r.discard();
        clearAck();
        disarm();
        bar.clear();
        serial = 0;
        reqB = null;
        headB = false;
        restoreAll(level);
        VaultBuilder.openDoor(level, origin);
        forceChunks(level, false);
        setPhase(level, Phase.DORMANT);
        RouterEvents.RESET.invoker().reset(level, origin, reason);
    }

    /** Consoles, lamps, gates, vents and water back to a fresh vault. */
    private void restoreAll(ServerLevel level) {
        consoles[0] = 0;
        consoles[1] = 1;
        consoles[2] = 2;
        Arrays.fill(alertTicks, 0);
        Arrays.fill(gateOpenAt, 0);
        if (!level.isLoaded(origin)) return;
        VaultBuilder.placeConsoles(level, origin);
        for (int a = 0; a < VaultLayout.ALCOVES.length; a++) VaultBuilder.gate(level, origin, a, true);
        drainAll(level);
        displayDirty = true;
        updateDisplay(level);
        applyConsoles(level);
    }

    private void setPhase(ServerLevel level, Phase to) {
        Phase from = phase;
        phase = to;
        phaseTicks = 0;
        acksInPhase = 0;
        queueLow = queue;
        stallTicks = 0;
        altTicks = switch (to) {
            case P2_DUAL -> RouterConfig.ALTERNATE_P2.get();
            case P4_STORM -> RouterConfig.ALTERNATE_P4.get();
            default -> -1;
        };
        if (to == Phase.P3_COOLANT) {
            sinceWetAck = 0;
            queueRef = queue;
        }
        if (to.fighting() && to != Phase.P0_BOOT) {
            if (to.dualQueue() && reqB == null && requestIssued()) reqB = decoyFor(level.random, reqA);
            if (!to.dualQueue() && reqB != null) {
                reqA = head().clone();
                reqB = null;
                headB = false;
            }
        }
        displayDirty = true;
        setDirty();
        if (from != to) {
            SkyloreBosses.LOG.info("Null Router vault {}: {} -> {} (queue {})", origin.toShortString(), from, to, queue);
            RouterEvents.PHASE_CHANGED.invoker().changed(level, origin, from, to);
        }
        if (!to.fighting() || from == to) return;
        List<ServerPlayer> ps = participants(level);
        switch (to) {
            case P1_SINGLE -> log(ps, "null_router.log.p1", ChatFormatting.AQUA, queue);
            case P2_DUAL -> {
                for (ServerPlayer p : ps) title(p, "null_router.title.p2", "null_router.title.p2.sub", ChatFormatting.AQUA);
                log(ps, "null_router.log.p2", ChatFormatting.AQUA, queue);
            }
            case P3_COOLANT -> {
                for (ServerPlayer p : ps) title(p, "null_router.title.p3", "null_router.title.p3.sub", ChatFormatting.AQUA);
                log(ps, "null_router.log.p3", ChatFormatting.AQUA, queue);
            }
            case P4_STORM -> {
                stormTicks = 0;
                stormFlickTicks = 0;
                for (ServerPlayer p : ps) {
                    title(p, "null_router.title.p4", "null_router.title.p4.sub", ChatFormatting.RED);
                    SBNetwork.sendScreenFx(p, SBNetwork.FX_SHAKE, 20);
                }
                AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.storm.alarm", 14f, 1f);
                NullRouterEntity c = chassis(level);
                if (c != null && ack == Ack.NONE) c.script(RouterAction.RETRY_STORM, null);
                storms++;
            }
            default -> {}
        }
    }

    // ================================================================== tick
    void tick(ServerLevel level) {
        phaseTicks++;
        long now = level.getGameTime();
        switch (phase) {
            case DORMANT -> {
                if (phaseTicks % 10 == 0) {
                    List<ServerPlayer> in = level.getEntitiesOfClass(ServerPlayer.class, VaultLayout.trigger(origin), p -> !p.isSpectator());
                    if (!in.isEmpty()) tryStart(level, in.get(0));
                }
                return;
            }
            case CLEARED -> {
                if (RouterConfig.REMATCH.get() && now - clearedAt > RouterConfig.REMATCH_DELAY_TICKS.get()) reset(level, "rematch");
                return;
            }
            case DEFEATED -> {
                if (phaseTicks == 160) {
                    VaultBuilder.openDoor(level, origin);
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
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, VaultLayout.fallZone(origin), p -> !p.isSpectator() && p.isAlive()))
            rescue(level, p);
        List<ServerPlayer> ps = participants(level);
        if (ps.isEmpty()) {
            if (++idleTicks > RouterConfig.DORMANT_TIMEOUT_TICKS.get()) {
                idleTicks = 0;
                SkyloreBosses.LOG.info("Null Router vault {} reset: no participants", origin.toShortString());
                reset(level, "abandoned");
            }
            return;
        }
        idleTicks = 0;
        fightTicks++;
        for (ServerPlayer p : ps) participantsEver.add(p.getUUID());
        NullRouterEntity c = chassis(level);
        if (c == null) {
            if (++chassisMissing >= 40) spawnChassis(level, false);
        } else {
            chassisMissing = 0;
        }
        for (int i = 0; i < 3; i++) if (alertTicks[i] > 0) alertTicks[i]--;
        if (phase == Phase.P0_BOOT) tickBoot(level, c, ps);
        if (armTicks > 0 && --armTicks == 0) commit(level);
        if (ack != Ack.NONE) tickAck(level, c, ps);
        else if (phase != Phase.P0_BOOT && !held) tickPressure(level, c, ps);
        if (phaseTicks % 20 == 0) tickShort(level, ps);
        if (phaseTicks % 5 == 0) tickFlight(level, ps);
        if (phaseTicks % 20 == 0) scanWater(level, now, false);
        if (phaseTicks % 10 == 0) keepDockClear(level);
        tickGates(level, now);
        tickCamp(ps);
        applyConsoles(level);
        if (displayDirty || phaseTicks % 40 == 0) updateDisplay(level);
        if (phaseTicks % 20 == 0) VaultBuilder.verify(level, origin);
        if (phaseTicks % 5 == 0) updateBar(c);
        if (phaseTicks % 20 == 0) bar.syncPlayers(ps);
    }

    /** P0: boot (immune ghost), the chime at 60, the tutorial request at 110; afterwards gentle nudges until the first ack. */
    private void tickBoot(ServerLevel level, @Nullable NullRouterEntity c, List<ServerPlayer> ps) {
        if (phaseTicks == 60 && c != null) c.script(RouterAction.BOOT_CHIME, null);
        if (phaseTicks == 110) issueRequest(level);
        if (requestIssued() && ack == Ack.NONE && ++nudgeTicks >= 400) {
            nudgeTicks = 0;
            if (c != null) c.script(RouterAction.REQUEST_PING, null);
            log(ps, "null_router.log.p0_nudge", ChatFormatting.GRAY);
        }
    }

    /** Between acks, P1-P4: stall growth, P4 triggers, dual-queue alternation, the storm clock. */
    private void tickPressure(ServerLevel level, @Nullable NullRouterEntity c, List<ServerPlayer> ps) {
        if (++stallTicks >= RouterConfig.stallTicks(phase)) {
            stallTicks = 0;
            if (queue < queueStart) {
                stalls++;
                changeQueue(level, queue + 1, "stall");
                log(ps, "null_router.log.stall", ChatFormatting.YELLOW, queue);
                AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.stall", 8f, 1f);
            }
        }
        if (phase == Phase.P3_COOLANT) sinceWetAck++;
        if (phase == Phase.P1_SINGLE || phase == Phase.P2_DUAL || phase == Phase.P3_COOLANT) {
            boolean climbed = queue >= queueLow + RouterConfig.P4_QUEUE_RISE.get();
            boolean dry = phase == Phase.P3_COOLANT && sinceWetAck >= RouterConfig.P4_NO_WET_ACK_TICKS.get();
            if (climbed || dry) {
                setPhase(level, Phase.P4_STORM);
                return;
            }
        }
        if (altTicks > 0) {
            if (altTicks == RouterConfig.ALTERNATE_WARN.get()) {
                log(ps, "null_router.log.rotate_warn", ChatFormatting.YELLOW);
                AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.rotate.warn", 8f, 1f);
                for (int i = 0; i < 3; i++) {
                    Telegraph.point(level, SBParticles.RAIL_MARK.get(), Vec3.atCenterOf(VaultLayout.headLamp(origin, i)), 6, 0.4);
                    Telegraph.point(level, SBParticles.RAIL_MARK.get(), Vec3.atCenterOf(VaultLayout.nextLamp(origin, i)), 6, 0.4);
                }
            }
            // never rotate under a settling pattern: a misroute must be a misread, not bad timing
            boolean hold = armTicks > 0 && altTicks == 1;
            if (!hold && --altTicks == 0) rotateHead(level, ps);
        }
        if (phase == Phase.P4_STORM) {
            if (++stormTicks >= RouterConfig.STORM_RECAST_TICKS.get() && c != null) {
                stormTicks = 0;
                storms++;
                c.script(RouterAction.RETRY_STORM, null);
            }
            if (++stormFlickTicks >= RouterConfig.STORM_FLICK_TICKS.get()) {
                stormFlickTicks = 0;
                int i = level.random.nextInt(3);
                bossFlip(level, i, true);
            }
        }
    }

    private void rotateHead(ServerLevel level, List<ServerPlayer> ps) {
        if (reqB == null) return;
        headB = !headB;
        altTicks = phase == Phase.P4_STORM ? RouterConfig.ALTERNATE_P4.get() : RouterConfig.ALTERNATE_P2.get();
        displayDirty = true;
        updateDisplay(level);
        AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.rotate", 8f, 1f);
        for (ServerPlayer p : ps)
            p.displayClientMessage(Component.translatable("null_router.log.rotated", headB ? "B" : "A", Glyph.pattern(head())).withStyle(ChatFormatting.AQUA), true);
        // the new head may already be on the consoles: that counts as the last flipper's match
        if (Arrays.equals(consoles, head()) && lastFlipper != null) arm(level, 0, lastFlipper);
        setDirty();
    }

    // ================================================================== requests (DESIGN.md §6)
    /** Issue the next request (and in P2+ its decoy). Called at the end of P0's boot and after every ACK window. */
    private void issueRequest(ServerLevel level) {
        RandomSource r = level.random;
        serial++;
        headB = false;
        if (phase == Phase.P0_BOOT) {
            // tutorial: one glyph on every channel; two consoles start wrong
            int g = r.nextInt(3);
            reqA = new int[]{g, g, g};
            int keep = r.nextInt(3);
            for (int i = 0; i < 3; i++) consoles[i] = i == keep ? g : (g + 1 + r.nextInt(2)) % 3;
            reqB = null;
        } else {
            reqA = patternAway(r, consoles, 2);
            reqB = phase.dualQueue() ? decoyFor(r, reqA) : null;
        }
        disarm();
        displayDirty = true;
        updateDisplay(level);
        applyConsoles(level);
        List<ServerPlayer> ps = participants(level);
        MutableComponent line = Component.translatable("null_router.log.request", hex(serial), Glyph.pattern(reqA));
        for (ServerPlayer p : ps) p.displayClientMessage(line.copy().withStyle(ChatFormatting.AQUA), true);
        AnimFx.play(level, Vec3.atCenterOf(VaultLayout.boardHead(origin, 1)), "null_router.request", 8f, 1f);
        NullRouterEntity c = chassis(level);
        if (c != null && (phase == Phase.P0_BOOT || phase == Phase.P1_SINGLE)) c.script(RouterAction.REQUEST_PING, null);
        RouterEvents.REQUEST_ISSUED.invoker().issued(level, origin, serial, reqA.clone(), reqB == null ? null : reqB.clone());
        setDirty();
    }

    /** A random pattern at Hamming distance >= d from {@code from}. */
    private static int[] patternAway(RandomSource r, int[] from, int d) {
        for (int n = 0; n < 64; n++) {
            int[] p = {r.nextInt(3), r.nextInt(3), r.nextInt(3)};
            if (hamming(p, from) >= d) return p;
        }
        return new int[]{(from[0] + 1) % 3, (from[1] + 1) % 3, (from[2] + 1) % 3};
    }

    /** The decoy: at least two channels different from the head (so one mis-set console can never complete it). */
    private int[] decoyFor(RandomSource r, int[] head) {
        for (int n = 0; n < 64; n++) {
            int[] p = patternAway(r, head, 2);
            if (!Arrays.equals(p, consoles)) return p;
        }
        return new int[]{(head[0] + 1) % 3, (head[1] + 2) % 3, head[2]};
    }

    static int hamming(int[] a, int[] b) {
        int n = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) n++;
        return n;
    }

    private static String hex(int serial) {
        return String.format("0x%03X", 0x100 + serial * 7 % 0xEFF);
    }

    // ================================================================== consoles (DESIGN.md §6)
    /** Right-click on a console. Only participants may set consoles; the vault is authoritative. */
    public void onConsoleUse(ServerLevel level, int i, ServerPlayer p) {
        long now = level.getGameTime();
        if (!phase.fighting()) {
            p.displayClientMessage(Component.translatable(phase == Phase.DEFEATED || phase == Phase.CLEARED
                    ? "null_router.console.cleared" : "null_router.console.idle").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!VaultLayout.volume(origin).contains(p.position())) return;
        if (!requestIssued()) {
            p.displayClientMessage(Component.translatable("null_router.console.booting").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (ack != Ack.NONE) {
            if (hint(p, 40)) p.displayClientMessage(Component.translatable("null_router.console.locked").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (now < consoleReadyAt[i]) return;
        consoleReadyAt[i] = now + RouterConfig.CONSOLE_COOLDOWN_TICKS.get();
        consoles[i] = (consoles[i] + 1) % 3;
        lastFlipper = p.getUUID();
        nudgeTicks = 0;
        Glyph g = Glyph.of(consoles[i]);
        BlockPos at = VaultLayout.console(origin, i);
        AnimFx.play(level, Vec3.atCenterOf(at), "null_router.console.set", 1.5f, 0.8f + consoles[i] * 0.2f);
        AnimFx.serverBurst(level, SBParticles.get("ack_glint"), Vec3.atCenterOf(at).add(0, 0.6, 0), 4, 0.25, 0.02);
        p.displayClientMessage(Component.translatable("null_router.console.set", VaultLayout.CHANNEL[i], g.chip(), Glyph.pattern(consoles),
                Glyph.pattern(head())).withStyle(ChatFormatting.GRAY), true);
        applyConsoles(level);
        evaluate(level, p.getUUID());
        setDirty();
    }

    /** Resolve a console block position to its channel and forward. */
    public boolean onConsoleUse(ServerLevel level, BlockPos pos, ServerPlayer p) {
        for (int i = 0; i < 3; i++) {
            if (VaultLayout.console(origin, i).equals(pos)) {
                onConsoleUse(level, i, p);
                return true;
            }
        }
        return false;
    }

    /** After any player flip: does the console pattern now match a displayed request? */
    private void evaluate(ServerLevel level, @Nullable UUID actor) {
        if (ack != Ack.NONE || !phase.fighting()) return;
        int[] d = decoy();
        if (Arrays.equals(consoles, head())) arm(level, 0, actor);
        else if (d != null && Arrays.equals(consoles, d)) arm(level, 1, actor);
        else disarm();
    }

    private void arm(ServerLevel level, int target, @Nullable UUID actor) {
        armTarget = target;
        armActor = actor;
        armTicks = RouterConfig.ARM_TICKS.get();
        AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.arm", 6f, 1f);
        applyConsoles(level);
    }

    private void disarm() {
        armTicks = 0;
        armTarget = -1;
        armActor = null;
    }

    /** The settled pattern commits: the head opens an ACK window, the decoy misroutes. */
    private void commit(ServerLevel level) {
        UUID actor = armActor;
        disarm();
        if (ack != Ack.NONE) return;
        ServerPlayer p = actor == null ? null : level.getServer().getPlayerList().getPlayer(actor);
        int[] d = decoy();
        if (Arrays.equals(consoles, head())) openAck(level, p);
        else if (d != null && Arrays.equals(consoles, d) && p != null) misroute(level, p);
        applyConsoles(level);
    }

    /** A non-player flip (retry, console_flick, storm). Never completes a request: it only ever undoes. */
    public boolean bossFlip(ServerLevel level, int i, boolean storm) {
        if (ack != Ack.NONE || !requestIssued() || !phase.fighting()) return false;
        int[] h = head(), d = decoy();
        List<Integer> options = new ArrayList<>();
        for (int g = 0; g < 3; g++) {
            if (g == consoles[i] || (!storm && g == h[i])) continue;
            int[] next = consoles.clone();
            next[i] = g;
            if (Arrays.equals(next, h) || (d != null && Arrays.equals(next, d))) continue;
            options.add(g);
        }
        if (options.isEmpty()) return false;
        consoles[i] = options.get(level.random.nextInt(options.size()));
        alertTicks[i] = 0;
        BlockPos at = VaultLayout.console(origin, i);
        AnimFx.play(level, Vec3.atCenterOf(at), "null_router.console.flipped", 3f, 1f);
        AnimFx.serverBurst(level, SBParticles.get("packet_spark"), Vec3.atCenterOf(at).add(0, 0.6, 0), 14, 0.35, 0.08);
        for (ServerPlayer p : participants(level))
            if (p.distanceToSqr(Vec3.atCenterOf(at)) < 24 * 24 && hint(p, 30))
                p.displayClientMessage(Component.translatable("null_router.console.flipped", VaultLayout.CHANNEL[i], Glyph.of(consoles[i]).chip())
                        .withStyle(ChatFormatting.GOLD), true);
        // a flip under a settling match cancels it
        if (armTicks > 0) {
            int[] want = armTarget == 0 ? head() : decoy();
            if (want == null || !Arrays.equals(consoles, want)) disarm();
        }
        applyConsoles(level);
        setDirty();
        return true;
    }

    /** Show ALERT on console i (a retry is uplinking, or console_flick is incoming). */
    public void alert(int i, int ticks) {
        alertTicks[i] = Math.max(alertTicks[i], ticks);
    }

    /** A player is standing at console i: console_flick is refused. */
    public boolean attended(ServerLevel level, int i) {
        Vec3 s = VaultLayout.consoleStand(origin, i);
        for (ServerPlayer p : participants(level)) if (!p.isSpectator() && p.position().distanceTo(s) <= 2.5) return true;
        return false;
    }

    private void applyConsoles(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            BlockPos at = VaultLayout.console(origin, i);
            if (!level.isLoaded(at)) continue;
            BlockState s = level.getBlockState(at);
            if (!s.is(RouterBlocks.CHANNEL_CONSOLE.get())) continue;
            ChannelConsoleBlock.Status st = ack != Ack.NONE ? ChannelConsoleBlock.Status.LOCKED
                    : alertTicks[i] > 0 ? ChannelConsoleBlock.Status.ALERT
                    : armTicks > 0 ? ChannelConsoleBlock.Status.ARMING : ChannelConsoleBlock.Status.IDLE;
            BlockState want = s.setValue(ChannelConsoleBlock.GLYPH, Glyph.of(consoles[i])).setValue(ChannelConsoleBlock.STATUS, st)
                    .setValue(ChannelConsoleBlock.FACING, VaultLayout.CONSOLE_FACING[i]);
            if (want != s) level.setBlock(at, want, 3);
        }
    }

    /** Lamp stacks over the consoles and the north-wall board: row A below, row B above; the head row is bright. */
    private void updateDisplay(ServerLevel level) {
        displayDirty = false;
        boolean live = phase.fighting() && requestIssued();
        for (int i = 0; i < 3; i++) {
            RequestLampBlock.Mode a = !live ? RequestLampBlock.Mode.OFF : headB && reqB != null ? RequestLampBlock.Mode.NEXT : RequestLampBlock.Mode.HEAD;
            RequestLampBlock.Mode b = !live || reqB == null || !phase.dualQueue() ? RequestLampBlock.Mode.OFF
                    : headB ? RequestLampBlock.Mode.HEAD : RequestLampBlock.Mode.NEXT;
            lamp(level, VaultLayout.headLamp(origin, i), reqA[i], a);
            lamp(level, VaultLayout.boardHead(origin, i), reqA[i], a);
            int gb = reqB == null ? 0 : reqB[i];
            lamp(level, VaultLayout.nextLamp(origin, i), gb, b);
            lamp(level, VaultLayout.boardNext(origin, i), gb, b);
        }
    }

    private static void lamp(ServerLevel level, BlockPos p, int glyph, RequestLampBlock.Mode mode) {
        if (!level.isLoaded(p)) return;
        BlockState want = RouterBlocks.REQUEST_LAMP.get().defaultBlockState().setValue(RequestLampBlock.GLYPH, Glyph.of(glyph)).setValue(RequestLampBlock.MODE, mode);
        if (level.getBlockState(p) != want) level.setBlock(p, want, 3);
    }

    // ================================================================== ACK window (DESIGN.md §6, §7)
    private void openAck(ServerLevel level, @Nullable ServerPlayer actor) {
        ack = Ack.SOLIDIFY;
        ackTicks = 0;
        ackCredit = 0;
        ackCleared = 0;
        windowWet = false;
        dryHinted = wetHinted = capHinted = false;
        wetTicks = 0;
        stallTicks = 0;
        NullRouterEntity c = chassis(level);
        if (c != null) c.dock();
        if (phase.coolant()) coolant(level, true);
        List<ServerPlayer> ps = participants(level);
        for (ServerPlayer p : ps) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(2, 20, 6));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(phase.coolant() ? "null_router.log.ack_coolant" : "null_router.log.ack")
                    .withStyle(ChatFormatting.GREEN)));
        }
        AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.ack", 12f, 1f);
        for (int i = 0; i < 3; i++)
            Telegraph.line(level, SBParticles.get("ack_glint"), Vec3.atCenterOf(VaultLayout.console(origin, i)), VaultLayout.chassisAt(origin, 1.5), 0.6);
        applyConsoles(level);
        if (actor != null && acks == 0) award(actor, "first_ack");
        RouterEvents.ACK_OPENED.invoker().opened(level, origin, actor, queue);
        setDirty();
    }

    private void tickAck(ServerLevel level, @Nullable NullRouterEntity c, List<ServerPlayer> ps) {
        ackTicks++;
        if (wetTicks > 0) wetTicks--;
        if (c != null && (ack == Ack.SOLIDIFY || ack == Ack.OPEN) && touchingWater(level, c.getBoundingBox())) onChassisWater(level, c);
        if (c != null) c.setWet(wetTicks > 0);
        switch (ack) {
            case SOLIDIFY -> {
                if (ackTicks >= RouterConfig.SOLIDIFY_TICKS.get()) {
                    ack = Ack.OPEN;
                    ackTicks = 0;
                    if (c != null) c.open();
                }
            }
            case OPEN -> {
                int left = RouterConfig.OPEN_TICKS.get() - ackTicks;
                if (c != null) c.setWarn(left <= RouterConfig.WARN_TICKS.get());
                if (left == RouterConfig.WARN_TICKS.get()) AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.ack.warn", 8f, 1f);
                if (phase.coolant() && ackTicks % 4 == 0)
                    for (int i = 0; i < VaultLayout.BASINS.length; i++)
                        AnimFx.serverBurst(level, SBParticles.get("coolant_mist"), Vec3.atBottomCenterOf(VaultLayout.basin(origin, i)).add(0, 1.1, 0), 2, 0.3, 0.02);
                if (left <= 0) {
                    ack = Ack.RELEASE;
                    ackTicks = 0;
                    if (c != null) c.release();
                }
            }
            case RELEASE -> {
                if (ackTicks >= RouterConfig.RELEASE_TICKS.get()) closeAck(level, c);
            }
            default -> {}
        }
    }

    /** Water on the chassis. Only counts inside an ACK window (solidify or open). */
    public void onChassisWater(ServerLevel level, NullRouterEntity c) {
        if (ack != Ack.SOLIDIFY && ack != Ack.OPEN) return;
        boolean first = wetTicks <= 0;
        wetTicks = RouterConfig.WET_TICKS.get();
        windowWet = true;
        c.setWet(true);
        if (first) {
            AnimFx.play(level, c.position(), "null_router.coolant.hiss", 6f, 1f);
            AnimFx.serverBurst(level, SBParticles.get("coolant_mist"), c.getBoundingBox().getCenter(), 30, 1.0, 0.06);
        }
        if (!wetHinted) {
            wetHinted = true;
            double m = RouterConfig.mult(phase, true);
            for (ServerPlayer p : participants(level))
                p.displayClientMessage(Component.translatable("null_router.log.wet", Math.round(m * 100)).withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Damage the chassis took inside an open window, after vanilla i-frames. Converts to cleared requests. */
    public void onAckDamage(ServerLevel level, NullRouterEntity c, float amount, @Nullable Entity attacker) {
        if (ack != Ack.OPEN || amount <= 0 || !phase.fighting()) return;
        boolean wet = wetTicks > 0;
        double mult = RouterConfig.mult(phase, wet);
        int cap = RouterConfig.ackCap(phase, windowWet);
        ackCredit += amount * mult / RouterConfig.DAMAGE_PER_REQUEST.get();
        int due = (int) Math.floor(ackCredit + 1e-6);
        if (wet && due < RouterConfig.WET_MINIMUM.get()) {
            due = RouterConfig.WET_MINIMUM.get();
            ackCredit = Math.max(ackCredit, due);
        }
        int pop = Math.min(Math.min(due, cap) - ackCleared, queue);
        List<ServerPlayer> ps = participants(level);
        if (!wet && phase.coolant() && !dryHinted) {
            dryHinted = true;
            for (ServerPlayer p : ps)
                p.displayClientMessage(Component.translatable("null_router.log.dry", Math.round(mult * 100)).withStyle(ChatFormatting.GOLD), true);
        }
        if (pop > 0) {
            ackCleared += pop;
            changeQueue(level, queue - pop, "ack");
            Vec3 at = c.getBoundingBox().getCenter();
            AnimFx.serverBurst(level, SBParticles.get("ack_glint"), at, 16 * pop, 1.0, 0.2);
            AnimFx.play(level, at, "null_router.request.cleared", 6f, 0.9f + ackCleared * 0.08f);
            for (ServerPlayer p : ps)
                p.displayClientMessage(Component.translatable(wet ? "null_router.log.cleared_wet" : "null_router.log.cleared", ackCleared, cap, queue)
                        .withStyle(wet ? ChatFormatting.AQUA : ChatFormatting.GREEN), true);
            if (queue <= 0) {
                victory(level, c);
                return;
            }
        } else if (ackCleared >= cap && !capHinted) {
            capHinted = true;
            for (ServerPlayer p : ps)
                p.displayClientMessage(Component.translatable("null_router.log.quota", cap).withStyle(ChatFormatting.GRAY), true);
        }
        setDirty();
    }

    private void closeAck(ServerLevel level, @Nullable NullRouterEntity c) {
        int cleared = ackCleared;
        boolean wet = windowWet && cleared > 0;
        clearAck();
        if (c != null) c.ghost();
        coolant(level, false);
        drainAll(level);
        acks++;
        acksInPhase++;
        if (wet) {
            wetAcks++;
            sinceWetAck = 0;
            queueRef = queue;
            for (ServerPlayer p : participants(level)) award(p, "wet_ack");
        }
        RouterEvents.ACK_CLOSED.invoker().closed(level, origin, cleared, wet, queue);
        AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.ack.close", 8f, 1f);
        // phase flow happens at window close so a window is never split across phases
        Phase next = phase;
        int p2 = (int) Math.ceil(queueStart * RouterConfig.P2_QUEUE_FRACTION.get());
        int p3 = (int) Math.ceil(queueStart * RouterConfig.P3_QUEUE_FRACTION.get());
        switch (phase) {
            case P0_BOOT -> next = Phase.P1_SINGLE;
            case P1_SINGLE -> {
                if (queue <= p3) next = Phase.P3_COOLANT;
                else if (queue <= p2 || acksInPhase >= RouterConfig.P2_AFTER_ACKS.get()) next = Phase.P2_DUAL;
            }
            case P2_DUAL -> { if (queue <= p3) next = Phase.P3_COOLANT; }
            case P4_STORM -> {
                if (wet) {
                    next = phaseForQueue();
                    stormSurvived = true;
                    for (ServerPlayer p : participants(level)) award(p, "storm_weathered");
                }
            }
            default -> {}
        }
        if (next != phase) setPhase(level, next);
        issueRequest(level);
        applyConsoles(level);
        setDirty();
    }

    /** Where the queue says the fight belongs (P4's exit). */
    private Phase phaseForQueue() {
        int p2 = (int) Math.ceil(queueStart * RouterConfig.P2_QUEUE_FRACTION.get());
        int p3 = (int) Math.ceil(queueStart * RouterConfig.P3_QUEUE_FRACTION.get());
        return queue <= p3 ? Phase.P3_COOLANT : queue <= p2 ? Phase.P2_DUAL : Phase.P1_SINGLE;
    }

    private void clearAck() {
        ack = Ack.NONE;
        ackTicks = 0;
        ackCredit = 0;
        ackCleared = 0;
        windowWet = false;
        wetTicks = 0;
    }

    private void changeQueue(ServerLevel level, int to, String cause) {
        to = Math.max(0, Math.min(RouterConfig.QUEUE_CAP.get(), to));
        int from = queue;
        if (from == to) return;
        queue = to;
        if (to < queueLow) queueLow = to;
        RouterEvents.QUEUE_CHANGED.invoker().changed(level, origin, from, to, cause);
        setDirty();
    }

    // ================================================================== coolant and water (DESIGN.md §7 wet card)
    /** P3+: open (fill the four basins, light the vents) or close the coolant vents. */
    public void coolant(ServerLevel level, boolean on) {
        for (int i = 0; i < VaultLayout.BASINS.length; i++) {
            BlockPos b = VaultLayout.basin(origin, i);
            if (!level.isLoaded(b)) continue;
            BlockState vent = level.getBlockState(b.below());
            if (vent.is(RouterBlocks.COOLANT_VENT.get()) && vent.getValue(BlockStateProperties.LIT) != on)
                level.setBlock(b.below(), vent.setValue(BlockStateProperties.LIT, on), 3);
            if (on) {
                level.setBlock(b, Blocks.WATER.defaultBlockState(), 3);
                AnimFx.serverBurst(level, SBParticles.get("coolant_mist"), Vec3.atCenterOf(b).add(0, 0.8, 0), 20, 0.4, 0.08);
            } else if (level.getFluidState(b).is(FluidTags.WATER)) {
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (on) AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.coolant.vent", 10f, 1f);
    }

    static boolean touchingWater(ServerLevel level, AABB box) {
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Every 20 ticks: water that has sat in the vault for drainTicks outside an ACK window is drained. */
    private void scanWater(ServerLevel level, long now, boolean all) {
        Map<BlockPos, Long> seen = new HashMap<>();
        int y0 = origin.getY();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -VaultLayout.HALF; x <= VaultLayout.HALF; x++)
            for (int z = -VaultLayout.HALF; z <= VaultLayout.HALF; z++)
                for (int y = 0; y <= 3; y++) {
                    m.set(origin.getX() + x, y0 + y, origin.getZ() + z);
                    if (!level.getFluidState(m).is(FluidTags.WATER)) continue;
                    BlockPos p = m.immutable();
                    long first = waterSeen.getOrDefault(p, now);
                    if (all || (ack == Ack.NONE && now - first >= RouterConfig.DRAIN_TICKS.get())) {
                        drain(level, p);
                    } else {
                        seen.put(p, first);
                    }
                }
        waterSeen.clear();
        waterSeen.putAll(seen);
    }

    private void drainAll(ServerLevel level) {
        scanWater(level, level.getGameTime(), true);
        AnimFx.play(level, Vec3.atCenterOf(origin), "null_router.drain", 6f, 1f);
    }

    private static void drain(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) {
            level.setBlock(p, s.setValue(BlockStateProperties.WATERLOGGED, false), 3);
        } else if (s.getBlock() instanceof LiquidBlock || s.canBeReplaced()) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        if (level.random.nextInt(4) == 0) AnimFx.serverBurst(level, SBParticles.get("coolant_mist"), Vec3.atCenterOf(p), 3, 0.3, 0.02);
    }

    /** floor_short (hazard): standing in water outside an ACK window shorts the player's rig. */
    private void tickShort(ServerLevel level, List<ServerPlayer> ps) {
        if (ack != Ack.NONE) return;
        for (ServerPlayer p : ps) {
            if (!p.isInWater() || p.isCreative()) continue;
            DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SHORT_CIRCUIT));
            p.hurt(src, RouterConfig.SHORT_DAMAGE.get().floatValue());
            int slow = RouterConfig.SHORT_SLOW_TICKS.get();
            if (slow > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1));
            AnimFx.serverBurst(level, SBParticles.get("grid_arc"), p.position().add(0, 0.4, 0), 10, 0.5, 0.2);
            AnimFx.play(level, p.position(), "null_router.short", 2f, 1f);
            if (hint(p, 60)) p.displayClientMessage(Component.translatable("null_router.log.short").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Clean-room airflow: no flying above the chassis. */
    private void tickFlight(ServerLevel level, List<ServerPlayer> ps) {
        double ceiling = VaultLayout.floorY(origin) + RouterConfig.FLIGHT_CEILING.get();
        for (ServerPlayer p : ps) {
            if (p.getY() <= ceiling || p.onGround() || p.isCreative() || p.isSpectator()) continue;
            if (!VaultLayout.interior(origin).contains(p.position())) continue;
            if (p.isFallFlying()) p.stopFallFlying();
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.5, Math.min(v.y, -0.9), v.z * 0.5);
            p.hurtMarked = true;
            AnimFx.serverBurst(level, SBParticles.get("coolant_mist"), p.position().add(0, 2.2, 0), 6, 0.5, 0.05);
            if (hint(p, 100)) p.displayClientMessage(Component.translatable("null_router.log.airflow").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Nothing solid may sit on the pad or in the chassis's column (it has to dock). */
    private void keepDockClear(ServerLevel level) {
        for (BlockPos p : VaultLayout.padBlocks(origin)) {
            for (int y = 1; y <= 6; y++) {
                BlockPos a = p.above(y);
                BlockState s = level.getBlockState(a);
                if (s.isAir() || s.getBlock() instanceof LiquidBlock) continue;
                level.destroyBlock(a, true);
            }
        }
    }

    /** Placement rule for BlockEvent.EntityPlaceEvent. @return true to cancel */
    public boolean refusePlacement(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!phase.fighting()) return false;
        int dx = pos.getX() - origin.getX(), dz = pos.getZ() - origin.getZ(), dy = pos.getY() - origin.getY();
        boolean dock = Math.abs(dx) <= VaultLayout.PAD && Math.abs(dz) <= VaultLayout.PAD && dy >= 1 && dy <= 6;
        boolean console = false;
        for (int i = 0; i < 3; i++) if (VaultLayout.consoleStand(origin, i).distanceTo(Vec3.atBottomCenterOf(pos)) < 1.2) console = true;
        if (!dock && !console) return false;
        if (player instanceof ServerPlayer sp && hint(sp, 40))
            sp.displayClientMessage(Component.translatable(dock ? "null_router.log.dock_clear" : "null_router.log.console_clear").withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    private void tickCamp(List<ServerPlayer> ps) {
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : ps) {
            if (VaultLayout.padDistance(origin, p.position()) <= 6.5 && ack == Ack.NONE) {
                campTicks.merge(p.getUUID(), 1, Integer::sum);
                here.add(p.getUUID());
            }
        }
        campTicks.keySet().retainAll(here);
    }

    /** Ticks this player has stood near the pad while the chassis was a ghost (ttl_expiry prefers campers). */
    public int campTicks(UUID p) { return campTicks.getOrDefault(p, 0); }

    // ================================================================== retries
    public List<RetryPacketEntity> retries(ServerLevel level) {
        return level.getEntitiesOfClass(RetryPacketEntity.class, VaultLayout.interior(origin).inflate(4), r -> origin.equals(r.vaultOrigin()) && r.isAlive());
    }

    /** Spawn one retry if the phase cap allows. */
    @Nullable
    public RetryPacketEntity spawnRetry(ServerLevel level, Vec3 at, Vec3 vel, boolean ignoreCap) {
        if (!phase.fighting() || phase == Phase.P0_BOOT) return null;
        if (!ignoreCap && retries(level).size() >= RouterConfig.retryCap(phase)) return null;
        RetryPacketEntity r = RouterEntities.RETRY_PACKET.get().create(level);
        if (r == null) return null;
        r.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        r.bindVault(origin);
        r.setDeltaMovement(vel);
        r.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        level.addFreshEntity(r);
        AnimFx.serverBurst(level, SBParticles.get("packet_spark"), at, 16, 0.4, 0.1);
        return r;
    }

    /** A retry finished its uplink at console i. */
    public boolean retryFlip(ServerLevel level, int i) {
        if (!consoleMatchesHead(i)) return false;
        return bossFlip(level, i, false);
    }

    public void onRetryKilled(ServerLevel level, @Nullable Player by) {
        retriesKilled++;
        setDirty();
    }

    // ================================================================== misroute (DESIGN.md §6)
    private void misroute(ServerLevel level, ServerPlayer actor) {
        misroutes++;
        anyMisroute = true;
        int pen = RouterConfig.MISROUTE_QUEUE_PENALTY.get();
        changeQueue(level, Math.min(queueStart, queue + pen), "misroute");
        if (phase.dualQueue()) {
            // the head stays where it is; the decoy row gets a fresh pattern (never the one now on the consoles)
            int[] fresh = decoyFor(level.random, head());
            if (headB) reqA = fresh;
            else reqB = fresh;
        }
        displayDirty = true;
        updateDisplay(level);
        for (ServerPlayer p : participants(level)) award(p, "misrouted");
        long now = level.getGameTime();
        if (misrouteUntil.getOrDefault(actor.getUUID(), 0L) > now) {
            actor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            actor.displayClientMessage(Component.translatable("null_router.log.misroute_cooldown", queue).withStyle(ChatFormatting.YELLOW), true);
            RouterEvents.MISROUTED.invoker().misrouted(level, origin, actor, "cooldown", queue);
            AnimFx.play(level, actor.position(), "null_router.misroute", 4f, 1.2f);
            return;
        }
        misrouteUntil.put(actor.getUUID(), now + RouterConfig.MISROUTE_COOLDOWN_TICKS.get());
        for (ServerPlayer p : participants(level))
            p.displayClientMessage(Component.translatable("null_router.log.misroute", actor.getDisplayName(), queue).withStyle(ChatFormatting.YELLOW), true);
        NullRouterEntity c = chassis(level);
        if (c != null) c.script(RouterAction.MISROUTE_PULSE, actor);
        else routeActor(level, actor);
        setDirty();
    }

    /**
     * misroute_pulse lands. Primary: into the service alcove farthest from the actor (the gate closes for holdTicks).
     * Fallback 1: swap places with a retry packet. Fallback 2: a shock and a shove away from the consoles.
     */
    public void routeActor(ServerLevel level, ServerPlayer actor) {
        if (!actor.isAlive() || !VaultLayout.volume(origin).contains(actor.position())) return;
        long now = level.getGameTime();
        int best = -1;
        double bd = -1;
        for (int a = 0; a < VaultLayout.ALCOVES.length; a++) {
            if (gateOpenAt[a] > now || !alcoveUsable(level, a)) continue;
            double d = VaultLayout.alcoveSpot(origin, a).distanceTo(actor.position());
            if (d > bd) { bd = d; best = a; }
        }
        String how;
        if (best >= 0) {
            Vec3 s = VaultLayout.alcoveSpot(origin, best);
            AnimFx.serverBurst(level, SBParticles.get("packet_spark"), actor.position().add(0, 1, 0), 30, 0.5, 0.15);
            actor.teleportTo(level, s.x, s.y, s.z, 0, 0);
            actor.fallDistance = 0;
            VaultBuilder.gate(level, origin, best, false);
            gateOpenAt[best] = now + RouterConfig.MISROUTE_HOLD_TICKS.get();
            how = "alcove";
        } else {
            List<RetryPacketEntity> rs = retries(level);
            if (!rs.isEmpty()) {
                RetryPacketEntity r = rs.get(level.random.nextInt(rs.size()));
                Vec3 a = actor.position(), b = r.position();
                actor.teleportTo(level, b.x, b.y, b.z, actor.getYRot(), actor.getXRot());
                r.teleportTo(a.x, a.y, a.z);
                how = "swap";
            } else {
                DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NULL_ROUTE));
                actor.hurt(src, RouterConfig.MISROUTE_DAMAGE.get().floatValue());
                Vec3 d = actor.position().subtract(Vec3.atCenterOf(origin)).multiply(1, 0, 1);
                d = d.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : d.normalize();
                actor.push(d.x * 1.2, 0.4, d.z * 1.2);
                actor.hurtMarked = true;
                how = "shock";
            }
        }
        actor.connection.send(new ClientboundSetTitlesAnimationPacket(3, 40, 10));
        actor.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("null_router.title.misroute.sub." + how).withStyle(ChatFormatting.GRAY)));
        actor.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("null_router.title.misroute").withStyle(ChatFormatting.YELLOW)));
        AnimFx.play(level, actor.position(), "null_router.misroute", 4f, 1f);
        RouterEvents.MISROUTED.invoker().misrouted(level, origin, actor, how, queue);
    }

    /** An alcove is usable when its spot has two blocks of air over something solid and nobody is already held in it. */
    private boolean alcoveUsable(ServerLevel level, int a) {
        Vec3 s = VaultLayout.alcoveSpot(origin, a);
        BlockPos feet = BlockPos.containing(s);
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return false;
        return level.getEntitiesOfClass(Player.class, VaultLayout.alcoveBox(origin, a)).isEmpty();
    }

    private void tickGates(ServerLevel level, long now) {
        for (int a = 0; a < gateOpenAt.length; a++) {
            if (gateOpenAt[a] != 0 && now >= gateOpenAt[a]) {
                gateOpenAt[a] = 0;
                VaultBuilder.gate(level, origin, a, true);
                AnimFx.play(level, VaultLayout.alcoveSpot(origin, a), "null_router.gate.open", 3f, 1f);
            }
        }
    }

    // ================================================================== chassis
    @Nullable
    public NullRouterEntity chassis(ServerLevel level) {
        if (chassisId == null) return null;
        Entity e = level.getEntity(chassisId);
        return e instanceof NullRouterEntity c && c.isAlive() ? c : null;
    }

    private void spawnChassis(ServerLevel level, boolean fresh) {
        Entity old = chassisId == null ? null : level.getEntity(chassisId);
        if (old != null) old.discard();
        NullRouterEntity c = RouterEntities.NULL_ROUTER.get().create(level);
        if (c == null) return;
        Vec3 at = VaultLayout.chassisAt(origin, VaultLayout.HOVER);
        c.moveTo(at.x, at.y, at.z, 180, 0);
        c.bindVault(origin);
        c.finalizeSpawn(level, level.getCurrentDifficultyAt(origin), MobSpawnType.EVENT, null);
        level.addFreshEntity(c);
        switch (ack) {
            case SOLIDIFY -> c.dock();
            case OPEN -> { c.dock(); c.open(); }
            case RELEASE -> c.release();
            default -> {}
        }
        if (fresh) c.triggerAnim("main", "boot");
        else AnimFx.serverBurst(level, SBParticles.get("grid_arc"), at.add(0, 1.4, 0), 60, 1.0, 0.1);
        chassisId = c.getUUID();
        chassisMissing = 0;
        setDirty();
    }

    // ================================================================== victory
    private void victory(ServerLevel level, NullRouterEntity c) {
        List<ServerPlayer> ps = participants(level);
        clearAck();
        coolant(level, false);
        drainAll(level);
        for (RetryPacketEntity r : retries(level)) r.timeout();
        setPhase(level, Phase.DEFEATED);
        c.shutdown();
        applyConsoles(level);
        displayDirty = true;
        updateDisplay(level);
        bar.update(0, RouterBossBar.Color.WHITE, Component.translatable("null_router.status.down"), 0, Component.translatable("null_router.bossbar.cleared"), false);
        String fn = RouterConfig.VICTORY_FUNCTION.get();
        for (ServerPlayer p : ps) {
            award(p, "queue_zero");
            if (!anyMisroute) award(p, "no_misroute");
            p.getInventory().placeItemBackInInventory(new ItemStack(RouterItems.CLOSED_TICKET.get()));
            title(p, "null_router.title.victory", "null_router.title.victory.sub", ChatFormatting.GREEN);
            AnimFx.play(level, p.position(), "null_router.victory", 1f, 1f);
            if (!fn.isEmpty()) {
                var server = level.getServer();
                server.getFunctions().get(ResourceLocation.parse(fn)).ifPresent(f ->
                        server.getFunctions().execute(f, p.createCommandSourceStack().withSuppressedOutput().withPermission(2)));
            }
        }
        RouterEvents.VICTORY.invoker().victory(level, origin, ps,
                new RouterEvents.EncounterStats(fightTicks, acks + 1, wetAcks, misroutes, stalls, storms, retriesKilled, deaths));
        BossEvents.DEFEATED.invoker().defeated(level, NullRouterBoss.ID, origin, ps);
        setDirty();
    }

    public void onPlayerDeath(ServerPlayer p) {
        if (phase.fighting() && VaultLayout.volume(origin).contains(p.position())) deaths++;
    }

    // ================================================================== players
    public List<ServerPlayer> participants(ServerLevel level) {
        return level.getEntitiesOfClass(ServerPlayer.class, VaultLayout.volume(origin), p -> !p.isSpectator() && p.isAlive());
    }

    private void rescue(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(VaultLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.fallDistance = 0;
        p.hurt(level.damageSources().fellOutOfWorld(), 4f);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
        p.displayClientMessage(Component.translatable("null_router.log.rescue").withStyle(ChatFormatting.GRAY), true);
    }

    /** Service terminal right-click. */
    public void onTerminal(ServerLevel level, ServerPlayer p) {
        AnimFx.play(level, p.position(), "null_router.terminal", 2f, 1f);
        switch (phase) {
            case DORMANT -> {
                if (tryStart(level, p)) readmit(level, p);
            }
            case CLEARED, DEFEATED -> p.displayClientMessage(Component.translatable("null_router.terminal.cleared").withStyle(ChatFormatting.GRAY), true);
            default -> readmit(level, p);
        }
    }

    private void readmit(ServerLevel level, ServerPlayer p) {
        Vec3 a = Vec3.atBottomCenterOf(VaultLayout.entryPad(origin));
        p.teleportTo(level, a.x, a.y, a.z, 180, 0);
        p.displayClientMessage(Component.translatable("null_router.terminal.readmit", queue).withStyle(ChatFormatting.AQUA), true);
    }

    // ================================================================== bar
    private void updateBar(@Nullable NullRouterEntity c) {
        RouterBossBar.Color color;
        Component status;
        switch (phase) {
            case P0_BOOT -> { color = RouterBossBar.Color.WHITE; status = Component.translatable(requestIssued() ? "null_router.status.p0" : "null_router.status.boot"); }
            case P4_STORM -> { color = RouterBossBar.Color.RED; status = Component.translatable("null_router.status.p4"); }
            default -> {
                color = RouterBossBar.Color.BLUE;
                status = Component.translatable("null_router.status." + phase.id().substring(0, 2));
            }
        }
        if (ack != Ack.NONE) {
            color = wetTicks > 0 ? RouterBossBar.Color.PURPLE : RouterBossBar.Color.GREEN;
            status = Component.translatable(wetTicks > 0 ? "null_router.status.ack_wet" : "null_router.status.ack", Math.round(RouterConfig.mult(phase, wetTicks > 0) * 100));
        } else if (armTicks > 0) {
            color = RouterBossBar.Color.YELLOW;
            status = Component.translatable("null_router.status.arming");
        }
        float queueFrac = queueStart <= 0 ? 0 : queue / (float) queueStart;
        // second bar: the ACK window while one is open, otherwise the head request and the retransmit clock
        float second;
        Component secondTitle;
        boolean ackBar = ack != Ack.NONE;
        if (ackBar) {
            int total = RouterConfig.SOLIDIFY_TICKS.get() + RouterConfig.OPEN_TICKS.get() + RouterConfig.RELEASE_TICKS.get();
            int used = switch (ack) {
                case SOLIDIFY -> ackTicks;
                case OPEN -> RouterConfig.SOLIDIFY_TICKS.get() + ackTicks;
                default -> RouterConfig.SOLIDIFY_TICKS.get() + RouterConfig.OPEN_TICKS.get() + ackTicks;
            };
            second = 1f - used / (float) total;
            int cap = RouterConfig.ackCap(phase, windowWet);
            secondTitle = Component.translatable(wetTicks > 0 ? "null_router.bossbar.ack_wet" : "null_router.bossbar.ack", ackCleared, cap);
        } else if (requestIssued()) {
            int stall = RouterConfig.stallTicks(phase);
            second = phase == Phase.P0_BOOT || stall == Integer.MAX_VALUE ? 1f : 1f - stallTicks / (float) stall;
            secondTitle = phase.dualQueue() && reqB != null
                    ? Component.translatable("null_router.bossbar.request_dual", hex(serial), Glyph.pattern(head()), headB ? "B" : "A")
                    : Component.translatable("null_router.bossbar.request", hex(serial), Glyph.pattern(head()));
        } else {
            second = 1f;
            secondTitle = Component.translatable("null_router.bossbar.booting");
        }
        bar.update(queueFrac, color, status, second, secondTitle, ackBar && wetTicks > 0);
        bar.setQueue(queue);
    }

    // ================================================================== commands
    public void skipPhase(ServerLevel level) {
        switch (phase) {
            case DORMANT, CLEARED -> {
                if (phase == Phase.CLEARED) reset(level, "skip");
                tryStart(level, null);
            }
            case P0_BOOT -> {
                if (!requestIssued()) issueRequest(level);
                clearAck();
                setPhase(level, Phase.P1_SINGLE);
                issueRequest(level);
            }
            case P1_SINGLE -> {
                changeQueue(level, Math.min(queue, (int) Math.ceil(queueStart * RouterConfig.P2_QUEUE_FRACTION.get())), "command");
                setPhase(level, Phase.P2_DUAL);
            }
            case P2_DUAL -> {
                changeQueue(level, Math.min(queue, (int) Math.ceil(queueStart * RouterConfig.P3_QUEUE_FRACTION.get())), "command");
                setPhase(level, Phase.P3_COOLANT);
            }
            case P3_COOLANT -> setPhase(level, Phase.P4_STORM);
            case P4_STORM -> setPhase(level, phaseForQueue());
            default -> {}
        }
        displayDirty = true;
        setDirty();
    }

    public void setQueue(ServerLevel level, int n) {
        changeQueue(level, n, "command");
        if (queue <= 0 && phase.fighting()) {
            NullRouterEntity c = chassis(level);
            if (c != null) victory(level, c);
        }
    }

    /** Open an ACK window now, as if the consoles had been matched (consoles are set to the head). */
    public boolean forceAck(ServerLevel level, @Nullable ServerPlayer by) {
        if (!phase.fighting() || ack != Ack.NONE) return false;
        if (!requestIssued()) issueRequest(level);
        int[] h = head();
        System.arraycopy(h, 0, consoles, 0, 3);
        disarm();
        openAck(level, by);
        return true;
    }

    /** Set the consoles to the head request as a player would (arming, then commit). */
    public boolean solve(ServerLevel level, ServerPlayer by) {
        if (!phase.fighting() || ack != Ack.NONE || !requestIssued()) return false;
        System.arraycopy(head(), 0, consoles, 0, 3);
        lastFlipper = by.getUUID();
        applyConsoles(level);
        evaluate(level, by.getUUID());
        return true;
    }

    /** Set the consoles to the decoy as a player would. */
    public boolean solveDecoy(ServerLevel level, ServerPlayer by) {
        int[] d = decoy();
        if (!phase.fighting() || ack != Ack.NONE || d == null) return false;
        System.arraycopy(d, 0, consoles, 0, 3);
        lastFlipper = by.getUUID();
        applyConsoles(level);
        evaluate(level, by.getUUID());
        return true;
    }

    public void forceMisroute(ServerLevel level, ServerPlayer p) {
        misroute(level, p);
    }

    public void setWet(ServerLevel level, int ticks) {
        NullRouterEntity c = chassis(level);
        wetTicks = ticks;
        if (ticks > 0 && ack != Ack.NONE) windowWet = true;
        if (c != null) c.setWet(ticks > 0);
    }

    public int spawnRetries(ServerLevel level, int n) {
        int made = 0;
        for (int k = 0; k < n; k++) {
            Vec3 at = VaultLayout.port(origin, k % VaultLayout.PORTS.length);
            if (spawnRetry(level, at, Vec3.ZERO, true) != null) made++;
        }
        return made;
    }

    public void setConsole(ServerLevel level, int i, int glyph, @Nullable ServerPlayer by) {
        consoles[i] = Math.floorMod(glyph, 3);
        if (by != null) lastFlipper = by.getUUID();
        applyConsoles(level);
        evaluate(level, by == null ? null : by.getUUID());
        setDirty();
    }

    public String describe(ServerLevel level) {
        NullRouterEntity c = chassis(level);
        int[] d = decoy();
        StringBuilder sb = new StringBuilder();
        sb.append("vault ").append(origin.toShortString()).append(" phase=").append(phase.id()).append(" t=").append(phaseTicks)
                .append(" queue=").append(queue).append('/').append(queueStart)
                .append(" ack=").append(ack.name().toLowerCase(java.util.Locale.ROOT)).append('(').append(ackTicks).append(')')
                .append(" serial=").append(serial)
                .append(" head=").append(csv(head())).append(" decoy=").append(d == null ? "-" : csv(d)).append(" headRow=").append(headB ? "B" : "A")
                .append(" consoles=").append(csv(consoles))
                .append(" arm=").append(armTicks).append(armTarget == 0 ? "h" : armTarget == 1 ? "d" : "")
                .append(" wet=").append(wetTicks).append(" windowWet=").append(windowWet)
                .append(" cleared=").append(ackCleared).append(String.format(java.util.Locale.ROOT, " credit=%.2f", ackCredit))
                .append(" acks=").append(acks).append(" wetAcks=").append(wetAcks).append(" misroutes=").append(misroutes)
                .append(" stalls=").append(stalls).append(" storms=").append(storms).append(" retries=").append(retries(level).size())
                .append(" killed=").append(retriesKilled)
                .append(" stall=").append(stallTicks).append('/').append(RouterConfig.stallTicks(phase) == Integer.MAX_VALUE ? "-" : RouterConfig.stallTicks(phase))
                .append(" alt=").append(altTicks).append(" sinceWet=").append(sinceWetAck).append(" low=").append(queueLow)
                .append(" held=").append(held)
                .append(" gates=").append(gateOpenAt[0] > 0 ? "C" : "o").append(gateOpenAt[1] > 0 ? "C" : "o");
        if (c != null) sb.append(System.lineSeparator()).append("  ").append(c.debugState());
        return sb.toString();
    }

    private static String csv(int[] a) {
        return a[0] + "," + a[1] + "," + a[2];
    }

    // ================================================================== helpers
    private void forceChunks(ServerLevel level, boolean on) {
        if (forced == on) return;
        forced = on;
        ChunkPos a = new ChunkPos(origin.offset(-VaultLayout.WALL - 8, 0, -VaultLayout.WALL - 8));
        ChunkPos b = new ChunkPos(origin.offset(VaultLayout.WALL + 8, 0, VaultLayout.WALL + 12));
        for (int x = a.x; x <= b.x; x++) for (int z = a.z; z <= b.z; z++) level.setChunkForced(x, z, on);
        setDirty();
    }

    /** First-contact teaching lines, at most one per player per {@code cooldown} ticks. */
    public boolean hint(ServerPlayer p, int cooldown) {
        long now = p.level().getGameTime();
        if (hintAt.getOrDefault(p.getUUID(), 0L) > now) return false;
        hintAt.put(p.getUUID(), now + cooldown);
        return true;
    }

    static void award(ServerPlayer p, String path) {
        var h = p.server.getAdvancements().get(SkyloreBosses.id("null_router/" + path));
        if (h != null) p.getAdvancements().award(h, "code");
    }

    private static void title(ServerPlayer p, String title, String sub, ChatFormatting color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(color)));
    }

    private static void log(List<ServerPlayer> ps, String key, ChatFormatting color, Object... args) {
        Component c = Component.translatable(key, args).withStyle(color);
        for (ServerPlayer p : ps) p.displayClientMessage(c, true);
    }

    // ================================================================== persistence
    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Origin", NbtUtils.writeBlockPos(origin));
        t.putString("Phase", phase.name());
        t.putInt("PhaseTicks", phaseTicks);
        t.putLong("FightTicks", fightTicks);
        t.putInt("Queue", queue);
        t.putInt("QueueStart", queueStart);
        t.putInt("QueueLow", queueLow);
        t.putInt("QueueRef", queueRef);
        t.putIntArray("Consoles", consoles);
        t.putIntArray("ReqA", reqA);
        if (reqB != null) t.putIntArray("ReqB", reqB);
        t.putBoolean("HeadB", headB);
        t.putInt("Serial", serial);
        t.putString("Ack", ack.name());
        t.putInt("AckTicks", ackTicks);
        t.putInt("AckCleared", ackCleared);
        t.putDouble("AckCredit", ackCredit);
        t.putBoolean("WindowWet", windowWet);
        t.putInt("WetTicks", wetTicks);
        t.putInt("Stall", stallTicks);
        t.putInt("SinceWet", sinceWetAck);
        t.putInt("Alt", altTicks);
        t.putInt("Storm", stormTicks);
        t.putInt("StormFlick", stormFlickTicks);
        t.putInt("Acks", acks);
        t.putInt("AcksInPhase", acksInPhase);
        t.putInt("WetAcks", wetAcks);
        t.putInt("Misroutes", misroutes);
        t.putInt("Stalls", stalls);
        t.putInt("Storms", storms);
        t.putInt("RetriesKilled", retriesKilled);
        t.putInt("Deaths", deaths);
        t.putBoolean("AnyMisroute", anyMisroute);
        t.putBoolean("StormSurvived", stormSurvived);
        t.putBoolean("Held", held);
        t.putBoolean("Forced", forced);
        t.putLong("ClearedAt", clearedAt);
        t.putLongArray("Gates", gateOpenAt);
        if (chassisId != null) t.putUUID("Chassis", chassisId);
        ListTag ev = new ListTag();
        for (UUID u : participantsEver) ev.add(NbtUtils.createUUID(u));
        t.put("Participants", ev);
        CompoundTag mr = new CompoundTag();
        for (Map.Entry<UUID, Long> e : misrouteUntil.entrySet()) mr.putLong(e.getKey().toString(), e.getValue());
        t.put("MisrouteUntil", mr);
        return t;
    }

    static Vault load(CompoundTag t) {
        Vault v = new Vault(NbtUtils.readBlockPos(t, "Origin").orElse(BlockPos.ZERO));
        v.phase = Phase.byName(t.getString("Phase"));
        v.phaseTicks = t.getInt("PhaseTicks");
        v.fightTicks = t.getLong("FightTicks");
        v.queue = t.getInt("Queue");
        v.queueStart = t.getInt("QueueStart");
        v.queueLow = t.getInt("QueueLow");
        v.queueRef = t.getInt("QueueRef");
        int[] c = t.getIntArray("Consoles");
        if (c.length == 3) System.arraycopy(c, 0, v.consoles, 0, 3);
        int[] a = t.getIntArray("ReqA");
        if (a.length == 3) v.reqA = a;
        int[] b = t.getIntArray("ReqB");
        v.reqB = b.length == 3 ? b : null;
        v.headB = t.getBoolean("HeadB");
        v.serial = t.getInt("Serial");
        try {
            v.ack = Ack.valueOf(t.getString("Ack"));
        } catch (IllegalArgumentException e) {
            v.ack = Ack.NONE;
        }
        v.ackTicks = t.getInt("AckTicks");
        v.ackCleared = t.getInt("AckCleared");
        v.ackCredit = t.getDouble("AckCredit");
        v.windowWet = t.getBoolean("WindowWet");
        v.wetTicks = t.getInt("WetTicks");
        v.stallTicks = t.getInt("Stall");
        v.sinceWetAck = t.getInt("SinceWet");
        v.altTicks = t.getInt("Alt");
        v.stormTicks = t.getInt("Storm");
        v.stormFlickTicks = t.getInt("StormFlick");
        v.acks = t.getInt("Acks");
        v.acksInPhase = t.getInt("AcksInPhase");
        v.wetAcks = t.getInt("WetAcks");
        v.misroutes = t.getInt("Misroutes");
        v.stalls = t.getInt("Stalls");
        v.storms = t.getInt("Storms");
        v.retriesKilled = t.getInt("RetriesKilled");
        v.deaths = t.getInt("Deaths");
        v.anyMisroute = t.getBoolean("AnyMisroute");
        v.stormSurvived = t.getBoolean("StormSurvived");
        v.held = t.getBoolean("Held");
        v.forced = t.getBoolean("Forced");
        v.clearedAt = t.getLong("ClearedAt");
        long[] g = t.getLongArray("Gates");
        System.arraycopy(g, 0, v.gateOpenAt, 0, Math.min(g.length, v.gateOpenAt.length));
        v.chassisId = t.hasUUID("Chassis") ? t.getUUID("Chassis") : null;
        for (Tag u : t.getList("Participants", Tag.TAG_INT_ARRAY)) v.participantsEver.add(NbtUtils.loadUUID(u));
        CompoundTag mr = t.getCompound("MisrouteUntil");
        for (String k : mr.getAllKeys()) {
            try {
                v.misrouteUntil.put(UUID.fromString(k), mr.getLong(k));
            } catch (IllegalArgumentException ignored) {
                // skip a malformed key
            }
        }
        return v;
    }
}
