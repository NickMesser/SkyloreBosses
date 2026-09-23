package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisCalyxBoss;
import net.teamaof.skylorebosses.core.api.BossEvents;
import net.teamaof.skylorebosses.core.net.SBNetwork;
import net.teamaof.skylorebosses.core.registry.SBParticles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.api.MatrisEvents;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.SporeVentBlockEntity;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.AbstractAdd;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.AbstractRootedArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ChargingArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.bloom.CalyxBloom;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisBlocks;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisEntities;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisItems;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/**
 * The fight controller: one per level, persisted as SavedData so a fight survives relogs, restarts and
 * chunk unloads. Owns state, arm slots, vents, the bossbar, body attacks and the infection clock.
 * Entities are views of this state and are respawned from it if they go missing.
 */
public final class MatrisEncounter extends SavedData {
    public enum State { NONE, BUILDING, DORMANT, AWAKENING, LIMBS, HEART_SPLIT, BLOOM, VICTORY }

    private static final String ID = "matris_calyx_encounter";

    private BlockPos origin;
    private State state = State.NONE;
    private int stateTicks;
    private long fightTicks;
    private boolean autoStart, dome = true;
    private final UUID[] armIds = new UUID[6];
    private final boolean[] armAlive = {true, true, true, true, true, true};
    private final float[] armHp = new float[6];
    private final int[] armMissing = new int[6];
    private int killCount;
    private boolean nerveFirst;
    private float cadence = 1f;
    private UUID bloomId;
    private float bloomMax;
    private int deaths;
    private final List<BlockPos> vents = new ArrayList<>();
    private final Set<UUID> syringeClaims = new HashSet<>();
    private final Set<UUID> participantsEver = new HashSet<>();
    private boolean forced;

    private transient ArenaBuilder builder;
    private transient final MatrisBossBar bar = new MatrisBossBar();
    private transient final BodyAttacks attacks = new BodyAttacks();
    private transient int idleTicks;

    // ------------------------------------------------------------------ access
    private static Factory<MatrisEncounter> factory() {
        return new Factory<>(MatrisEncounter::new, MatrisEncounter::load, null);
    }

    private static MatrisEncounter data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(factory(), ID);
    }

    /** @return the encounter in this level, or null if no arena was ever started here */
    @Nullable
    public static MatrisEncounter get(ServerLevel level) {
        MatrisEncounter e = data(level);
        return e.origin == null ? null : e;
    }

    public BlockPos origin() { return origin; }
    public State state() { return state; }
    public BodyAttacks attacks() { return attacks; }

    // ------------------------------------------------------------------ lifecycle
    /** Build (or rebuild) the arena at {@code origin}. With autoStart the fight begins as soon as it is built. */
    public static MatrisEncounter start(ServerLevel level, BlockPos origin, boolean autoStart, boolean dome) {
        MatrisEncounter e = data(level);
        if (e.origin != null && e.state != State.NONE) e.reset(level, "restart");
        e.origin = origin.immutable();
        e.autoStart = autoStart;
        e.dome = dome && MatrisConfig.BUILD_DOME.get();
        e.builder = new ArenaBuilder(e.origin, e.dome);
        e.vents.clear();
        e.vents.addAll(e.builder.vents());
        e.setState(State.BUILDING);
        e.forceChunks(level, true);
        SkyloreBosses.LOG.info("Building Matris arena at {} ({} blocks)", origin, e.builder.total());
        return e;
    }

    /** Place every remaining arena block now (used when a player arrives so nobody falls into an unbuilt arena). */
    public void finishBuild(ServerLevel level) {
        if (state != State.BUILDING) return;
        if (builder == null) {
            builder = new ArenaBuilder(origin, dome);
            vents.clear();
            vents.addAll(builder.vents());
        }
        while (!builder.done()) builder.step(level, 100000);
        stateTicks = 0;
        tickBuilding(level);
    }

    /** Wake a dormant encounter immediately (commands, pack triggers). */
    public void awaken(ServerLevel level) {
        if (state == State.DORMANT || state == State.VICTORY) {
            for (int i = 0; i < 6; i++) { armAlive[i] = true; armIds[i] = null; armHp[i] = 0; }
            killCount = 0;
            nerveFirst = false;
            cadence = 1f;
            fightTicks = 0;
            deaths = 0;
            syringeClaims.clear();
            attacks.reset();
            setState(State.AWAKENING);
            forceChunks(level, true);
            forEachVent(level, v -> { v.repair(); v.setActive(true); });
            restoreHeart(level);
            List<ServerPlayer> ps = participants(level);
            for (ServerPlayer p : ps) {
                title(p, "matris_calyx.title.awaken", "matris_calyx.title.awaken.sub");
                AnimFx.play(level, p.position(), "matris_calyx.encounter.awaken", 1f, 1f);
                sendShake(p, 60);
            }
            MatrisEvents.ENCOUNTER_STARTED.invoker().started(level, origin, ps);
            BossEvents.STARTED.invoker().started(level, MatrisCalyxBoss.ID, origin, ps);
        }
    }

    public void reset(ServerLevel level, String reason) {
        for (UUID id : armIds) discard(level, id);
        discard(level, bloomId);
        bloomId = null;
        if (origin != null) {
            for (AbstractAdd add : level.getEntitiesOfClass(AbstractAdd.class, arenaBox())) add.discard();
            forEachVent(level, v -> { v.repair(); v.setActive(false); });
            restoreHeart(level);
        }
        for (int i = 0; i < 6; i++) { armIds[i] = null; armAlive[i] = true; }
        attacks.reset();
        bar.clear();
        killCount = 0;
        setState(origin == null ? State.NONE : (builder != null && !builder.done() ? State.BUILDING : State.DORMANT));
        if (origin != null) MatrisEvents.RESET.invoker().reset(level, origin, reason);
    }

    /** Designer command: advance to the next beat. */
    public void skipPhase(ServerLevel level) {
        switch (state) {
            case BUILDING -> { while (!builder.done()) builder.step(level, 50000); }
            case DORMANT, VICTORY -> awaken(level);
            case AWAKENING -> { for (ArmType t : ArmType.values()) if (armIds[t.slot] == null) spawnArm(level, t); setState(State.LIMBS); }
            case LIMBS -> {
                for (ArmType t : ArmType.values()) {
                    if (!armAlive[t.slot]) continue;
                    Entity e = armIds[t.slot] == null ? null : level.getEntity(armIds[t.slot]);
                    if (e instanceof AbstractRootedArm a) a.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
                    else markArmDead(level, t, null);
                    break;
                }
            }
            case HEART_SPLIT -> stateTicks = 159;
            case BLOOM -> {
                Entity b = bloomId == null ? null : level.getEntity(bloomId);
                if (b instanceof CalyxBloom cb) cb.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            }
            default -> {}
        }
    }

    private void setState(State s) {
        state = s;
        stateTicks = 0;
        setDirty();
    }

    // ------------------------------------------------------------------ tick
    public static void tickLevel(ServerLevel level) {
        MatrisEncounter e = get(level);
        if (e != null) e.tick(level);
    }

    private void tick(ServerLevel level) {
        stateTicks++;
        switch (state) {
            case BUILDING -> tickBuilding(level);
            case DORMANT -> {
                if (stateTicks % 10 == 0 && !level.getEntitiesOfClass(ServerPlayer.class,
                        new AABB(ArenaLayout.anchor(origin)).inflate(20), p -> !p.isSpectator()).isEmpty()) awaken(level);
            }
            case AWAKENING -> {
                if (stateTicks % 40 == 0) {
                    int k = stateTicks / 40 - 1;
                    if (k < 6) spawnArm(level, ArmType.bySlot(k));
                }
                bar.setLimbs(Math.min(1f, stateTicks / 240f));
                if (stateTicks >= 250) setState(State.LIMBS);
            }
            case LIMBS -> tickLimbs(level);
            case HEART_SPLIT -> tickHeartSplit(level);
            case BLOOM -> tickBloom(level);
            case VICTORY -> {
                if (stateTicks == 400) {
                    bar.clear();
                    forceChunks(level, false);
                }
            }
            default -> {}
        }
        if (isActive()) tickActive(level);
    }

    public boolean isActive() {
        return state == State.AWAKENING || state == State.LIMBS || state == State.HEART_SPLIT || state == State.BLOOM;
    }

    private void tickBuilding(ServerLevel level) {
        if (builder == null) {
            builder = new ArenaBuilder(origin, dome);
            vents.clear();
            vents.addAll(builder.vents());
        }
        builder.step(level, 6000);
        if (stateTicks % 20 == 0) {
            for (ServerPlayer p : participants(level)) {
                p.displayClientMessage(Component.translatable("matris_calyx.building", builder.placed() * 100 / Math.max(1, builder.total())), true);
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false));
            }
        }
        if (builder.done()) {
            forEachVent(level, v -> v.setActive(false));
            setState(State.DORMANT);
            SkyloreBosses.LOG.info("Matris arena built at {}", origin);
            if (autoStart) awaken(level);
        }
    }

    private void tickActive(ServerLevel level) {
        List<ServerPlayer> players = participants(level);
        if (players.isEmpty()) {
            // nobody here: pause everything; after the timeout the fight goes dormant (kills stay)
            if (++idleTicks > MatrisConfig.DORMANT_TIMEOUT_TICKS.get()) goDormant(level);
            return;
        }
        idleTicks = 0;
        fightTicks++;
        if (stateTicks % 20 == 0) {
            bar.syncPlayers(players);
            long sec = level.getGameTime() / 20;
            for (ServerPlayer p : players) {
                participantsEver.add(p.getUUID());
                Infection.tickSecond(p, sec, ArenaLayout.anchor(origin));
                if (p.getY() < origin.getY() - 100) {
                    p.hurt(level.damageSources().fellOutOfWorld(), 12f);
                    Vec3 a = Vec3.atBottomCenterOf(ArenaLayout.anchor(origin)).add(0, 1, 0);
                    p.teleportTo(level, a.x, a.y, a.z, p.getYRot(), p.getXRot());
                    p.fallDistance = 0;
                }
            }
        }
        if (stateTicks % 160 == 0) AnimFx.play(level, Vec3.atCenterOf(origin), "matris_calyx.ambient.stomach", 12f, 1f);
        if (state == State.LIMBS || state == State.BLOOM) attacks.tick(level, this, players);
    }

    private void goDormant(ServerLevel level) {
        idleTicks = 0;
        bar.clear();
        SkyloreBosses.LOG.info("Matris encounter at {} went dormant (no players)", origin);
        setState(State.DORMANT);
        // arms keep their HP and kills stay killed; entities are despawned and respawned on wake
        for (UUID id : armIds) discard(level, id);
        forceChunks(level, false);
    }

    private void tickLimbs(ServerLevel level) {
        if (stateTicks % 20 != 0) return;
        float total = 0;
        for (ArmType t : ArmType.values()) {
            int i = t.slot;
            if (!armAlive[i]) continue;
            Entity e = armIds[i] == null ? null : level.getEntity(armIds[i]);
            if (e instanceof AbstractRootedArm a && a.isAlive()) {
                armMissing[i] = 0;
                armHp[i] = a.getHealth();
                total += 100f * a.getHealth() / a.getMaxHealth();
            } else if (e == null) {
                // missing (unloaded, /kill'ed): respawn from saved state after 5 s
                if (++armMissing[i] >= 5) { spawnArm(level, t); armMissing[i] = 0; }
                total += 100f * (armHp[i] <= 0 ? 1f : Math.min(1f, armHp[i] / t.maxHp));
            }
        }
        bar.setLimbs(total / 600f);
    }

    private void tickHeartSplit(ServerLevel level) {
        if (stateTicks == 1) {
            for (ServerPlayer p : participants(level)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
                title(p, "matris_calyx.title.heart", "matris_calyx.title.heart.sub");
                sendShake(p, 120);
            }
            AnimFx.play(level, Vec3.atCenterOf(origin), "matris_calyx.bloom.heart_split", 16f, 1f);
            forEachVent(level, v -> v.setActive(false));
            for (AbstractAdd add : level.getEntitiesOfClass(AbstractAdd.class, arenaBox())) add.discard();
            openHeart(level);
            spawnBloom(level);
        }
        if (stateTicks >= 160) setState(State.BLOOM);
    }

    private void tickBloom(ServerLevel level) {
        if (stateTicks % 5 != 0) return;
        Entity e = bloomId == null ? null : level.getEntity(bloomId);
        if (e instanceof CalyxBloom b && b.isAlive()) {
            bar.setBloom(b.getHealth() / b.getMaxHealth());
            if (b.getHealth() < b.getMaxHealth() * 0.5f && stateTicks % 200 == 0) {
                // two heart vents re-open for the second half
                int open = 0;
                for (BlockPos v : vents) {
                    if (open >= 2) break;
                    if (v.closerThan(origin, 45) && level.getBlockEntity(v) instanceof SporeVentBlockEntity be && !be.isBroken()) {
                        be.setActive(true);
                        open++;
                    }
                }
            }
        } else if (e == null && stateTicks % 100 == 0) {
            spawnBloom(level);
        }
    }

    // ------------------------------------------------------------------ spawning
    private float hpMultiplier(ServerLevel level) {
        int n = Math.max(1, participants(level).size());
        return (float) Math.min(MatrisConfig.MP_HP_CAP.get(), 1 + MatrisConfig.MP_HP_PER_PLAYER.get() * (n - 1));
    }

    private void spawnArm(ServerLevel level, ArmType t) {
        if (!armAlive[t.slot]) return;
        discard(level, armIds[t.slot]);
        AbstractRootedArm arm = MatrisEntities.armType(t).create(level);
        if (arm == null) return;
        BlockPos at = ArenaLayout.armAnchor(origin, t);
        double yaw = Math.toDegrees(Math.atan2(origin.getZ() - at.getZ(), origin.getX() - at.getX())) - 90;
        arm.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) yaw, 0);
        arm.setYBodyRot((float) yaw);
        arm.setYHeadRot((float) yaw);
        arm.setAnchor(at);
        arm.setEncounterOrigin(origin);
        arm.setCadence(cadence);
        if (arm instanceof ChargingArm c) c.setLaneAxis(Direction.Axis.X);
        float max = t.maxHp * hpMultiplier(level);
        arm.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);
        arm.setHealth(armHp[t.slot] > 0 ? Math.min(max, armHp[t.slot]) : max);
        arm.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        level.addFreshEntity(arm);
        arm.triggerAnim("main", "emerge");
        armIds[t.slot] = arm.getUUID();
        armHp[t.slot] = arm.getHealth();
        AnimFx.play(level, Vec3.atCenterOf(at), "matris_calyx.arm.emerge", 8f, 1f);
        setDirty();
    }

    private void spawnBloom(ServerLevel level) {
        discard(level, bloomId);
        CalyxBloom b = MatrisEntities.CALYX_BLOOM.get().create(level);
        if (b == null) return;
        BlockPos at = ArenaLayout.heart(origin);
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
        bloomMax = (float) (MatrisConfig.BLOOM_HP.get() * hpMultiplier(level));
        b.setMaxHp(bloomMax);
        level.addFreshEntity(b);
        bloomId = b.getUUID();
        MatrisEvents.BLOOM_EMERGED.invoker().emerged(level, origin, b);
        setDirty();
    }

    // ------------------------------------------------------------------ deaths
    public static void onArmDied(ServerLevel level, AbstractRootedArm arm, DamageSource source) {
        MatrisEncounter e = get(level);
        if (e == null || arm.encounterOrigin() == null || !arm.encounterOrigin().equals(e.origin)) return;
        if (!arm.getUUID().equals(e.armIds[arm.armType.slot])) return;
        e.markArmDead(level, arm.armType, source.getEntity());
    }

    private void markArmDead(ServerLevel level, ArmType t, @Nullable Entity killer) {
        if (!armAlive[t.slot]) return;
        armAlive[t.slot] = false;
        armHp[t.slot] = 0;
        killCount++;
        List<ServerPlayer> ps = participants(level);
        if (killCount == 1) {
            ps.forEach(p -> award(p, "first_arm"));
            if (t == ArmType.NERVE) {
                nerveFirst = true;
                ps.forEach(p -> award(p, "nerve_first"));
            }
        }
        Component name = Component.translatable("entity.skylore_bosses." + t.model);
        for (ServerPlayer p : ps) p.displayClientMessage(Component.translatable("matris_calyx.arm_severed", name, killCount).withStyle(ChatFormatting.GOLD), true);
        if (t == ArmType.NERVE) {
            cadence = 0.8f;
            for (ArmType o : ArmType.values()) {
                Entity oe = armIds[o.slot] == null ? null : level.getEntity(armIds[o.slot]);
                if (oe instanceof AbstractRootedArm a && a.isAlive()) {
                    a.setCadence(cadence);
                    a.setBuffed(false);
                    a.triggerAnim("main", "hurt");
                }
            }
            for (ServerPlayer p : ps) p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("matris_calyx.nerve_dead").withStyle(ChatFormatting.DARK_AQUA)));
            for (ServerPlayer p : ps) p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        }
        if (killCount == 3) attacks.escalate();
        MatrisEvents.ARM_KILLED.invoker().killed(level, origin, t, killer, killCount);
        if (killCount >= 6) {
            ps.forEach(p -> award(p, "all_arms"));
            bar.setLimbs(0);
            setState(State.HEART_SPLIT);
        }
        setDirty();
    }

    public static void onBloomDied(ServerLevel level, CalyxBloom bloom, DamageSource source) {
        MatrisEncounter e = get(level);
        if (e == null || !bloom.getUUID().equals(e.bloomId)) return;
        e.victory(level);
    }

    private void victory(ServerLevel level) {
        setState(State.VICTORY);
        bar.setBloom(0);
        attacks.reset();
        forEachVent(level, v -> v.setActive(false));
        for (AbstractAdd add : level.getEntitiesOfClass(AbstractAdd.class, arenaBox())) add.discard();
        List<ServerPlayer> ps = participants(level);
        String fn = MatrisConfig.VICTORY_FUNCTION.get();
        for (ServerPlayer p : ps) {
            award(p, "bloom_slain");
            p.getInventory().placeItemBackInInventory(new ItemStack(MatrisItems.CALYX_HEART.get()));
            Infection.set(p, 0);
            title(p, "matris_calyx.title.victory", "matris_calyx.title.victory.sub");
            AnimFx.play(level, p.position(), "matris_calyx.encounter.victory", 1f, 1f);
            if (!fn.isEmpty()) {
                var server = level.getServer();
                server.getFunctions().get(ResourceLocation.parse(fn)).ifPresent(f ->
                        server.getFunctions().execute(f, p.createCommandSourceStack().withSuppressedOutput().withPermission(2)));
            }
        }
        MatrisEvents.VICTORY.invoker().victory(level, origin, ps, new MatrisEvents.EncounterStats(fightTicks, nerveFirst, deaths));
        BossEvents.DEFEATED.invoker().defeated(level, MatrisCalyxBoss.ID, origin, ps);
    }

    public void onPlayerDeath(ServerPlayer p) {
        if (isActive() && p.level() instanceof ServerLevel && inArena(p)) deaths++;
    }

    // ------------------------------------------------------------------ helpers
    public boolean claimSyringes(UUID player) {
        boolean ok = syringeClaims.add(player);
        if (ok) setDirty();
        return ok;
    }

    public boolean inArena(Entity e) {
        return origin != null && e.position().distanceTo(Vec3.atCenterOf(origin)) < MatrisConfig.ARENA_RADIUS.get();
    }

    private AABB arenaBox() {
        return new AABB(origin).inflate(MatrisConfig.ARENA_RADIUS.get());
    }

    public List<ServerPlayer> participants(ServerLevel level) {
        if (origin == null) return List.of();
        double r = MatrisConfig.ARENA_RADIUS.get();
        Vec3 c = Vec3.atCenterOf(origin);
        return level.players().stream().filter(p -> !p.isSpectator() && p.position().distanceTo(c) < r).toList();
    }

    public int liveVents(ServerLevel level) {
        int n = 0;
        for (BlockPos v : vents) if (level.getBlockEntity(v) instanceof SporeVentBlockEntity be && !be.isBroken()) n++;
        return n;
    }

    public void forEachVent(ServerLevel level, Consumer<SporeVentBlockEntity> c) {
        for (BlockPos v : vents) if (level.isLoaded(v) && level.getBlockEntity(v) instanceof SporeVentBlockEntity be) c.accept(be);
    }

    /** The heart splits: clear the heart mass so the Bloom can rise. */
    private void openHeart(ServerLevel level) {
        BlockPos o = origin;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-7, 1, -7), o.offset(7, 11, 7))) {
            var s = level.getBlockState(p);
            if (s.is(MatrisBlocks.HEART_CORE.get()) || s.is(MatrisBlocks.FLESH.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        AnimFx.serverBurst(level, net.teamaof.skylorebosses.core.registry.SBParticles.FLESH_CHUNKS.get(), Vec3.atCenterOf(o.above(5)), 120, 5, 0.5);
        AnimFx.serverBurst(level, net.teamaof.skylorebosses.core.registry.SBParticles.BLOOD_BURST.get(), Vec3.atCenterOf(o.above(5)), 120, 5, 0.5);
    }

    private void restoreHeart(ServerLevel level) {
        if (origin == null || state == State.BUILDING) return;
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = 0; dy <= 9; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    double d = Math.sqrt(dx * dx + (dy - 4.5) * (dy - 4.5) * 1.3 + dz * dz);
                    if (d <= 6) level.setBlock(origin.offset(dx, dy + 1, dz),
                            (d > 5 ? MatrisBlocks.FLESH.get() : MatrisBlocks.HEART_CORE.get()).defaultBlockState(), 2);
                }
    }

    private static void discard(ServerLevel level, @Nullable UUID id) {
        if (id == null) return;
        Entity e = level.getEntity(id);
        if (e != null) e.discard();
    }

    private void forceChunks(ServerLevel level, boolean on) {
        if (origin == null || forced == on) return;
        forced = on;
        List<BlockPos> centers = new ArrayList<>();
        centers.add(origin);
        for (ArmType t : ArmType.values()) centers.add(ArenaLayout.satellite(origin, t.slot));
        for (BlockPos c : centers) {
            ChunkPos cp = new ChunkPos(c);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.setChunkForced(cp.x + dx, cp.z + dz, on);
        }
        setDirty();
    }

    static void award(ServerPlayer p, String path) {
        var h = p.server.getAdvancements().get(SkyloreBosses.id("matris_calyx/" + path));
        if (h != null) p.getAdvancements().award(h, "code");
    }

    private static void title(ServerPlayer p, String title, String sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(sub).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.DARK_RED)));
    }

    private static void sendShake(ServerPlayer p, int ticks) {
        net.teamaof.skylorebosses.core.net.SBNetwork.sendScreenFx(p, net.teamaof.skylorebosses.core.net.SBNetwork.FX_SHAKE, ticks);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("state=").append(state).append(" t=").append(stateTicks).append(" origin=").append(origin.toShortString());
        sb.append(" kills=").append(killCount).append(" arms=[");
        for (ArmType t : ArmType.values()) sb.append(t.name().charAt(0)).append(armAlive[t.slot] ? "+" : "-");
        sb.append("] vents=").append(vents.size());
        if (builder != null && state == State.BUILDING) sb.append(" built=").append(builder.placed()).append('/').append(builder.total());
        return sb.toString();
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        if (origin == null) return tag;
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putString("State", state.name());
        tag.putInt("StateTicks", stateTicks);
        tag.putLong("FightTicks", fightTicks);
        tag.putBoolean("AutoStart", autoStart);
        tag.putBoolean("Dome", dome);
        tag.putInt("Kills", killCount);
        tag.putBoolean("NerveFirst", nerveFirst);
        tag.putFloat("Cadence", cadence);
        tag.putInt("Deaths", deaths);
        tag.putBoolean("Forced", forced);
        for (int i = 0; i < 6; i++) {
            CompoundTag a = new CompoundTag();
            if (armIds[i] != null) a.putUUID("Id", armIds[i]);
            a.putBoolean("Alive", armAlive[i]);
            a.putFloat("Hp", armHp[i]);
            tag.put("Arm" + i, a);
        }
        if (bloomId != null) tag.putUUID("Bloom", bloomId);
        tag.putFloat("BloomMax", bloomMax);
        ListTag vl = new ListTag();
        for (BlockPos v : vents) vl.add(LongTag.valueOf(v.asLong()));
        tag.put("Vents", vl);
        ListTag cl = new ListTag();
        for (UUID u : syringeClaims) cl.add(NbtUtils.createUUID(u));
        tag.put("Claims", cl);
        return tag;
    }

    private static MatrisEncounter load(CompoundTag tag, HolderLookup.Provider regs) {
        MatrisEncounter e = new MatrisEncounter();
        if (!tag.contains("Origin")) return e;
        e.origin = NbtUtils.readBlockPos(tag, "Origin").orElse(null);
        try {
            e.state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException ex) {
            e.state = State.DORMANT;
        }
        e.stateTicks = tag.getInt("StateTicks");
        e.fightTicks = tag.getLong("FightTicks");
        e.autoStart = tag.getBoolean("AutoStart");
        e.dome = tag.getBoolean("Dome");
        e.killCount = tag.getInt("Kills");
        e.nerveFirst = tag.getBoolean("NerveFirst");
        e.cadence = tag.contains("Cadence") ? tag.getFloat("Cadence") : 1f;
        e.deaths = tag.getInt("Deaths");
        e.forced = tag.getBoolean("Forced");
        for (int i = 0; i < 6; i++) {
            CompoundTag a = tag.getCompound("Arm" + i);
            e.armIds[i] = a.hasUUID("Id") ? a.getUUID("Id") : null;
            e.armAlive[i] = !a.contains("Alive") || a.getBoolean("Alive");
            e.armHp[i] = a.getFloat("Hp");
        }
        e.bloomId = tag.hasUUID("Bloom") ? tag.getUUID("Bloom") : null;
        e.bloomMax = tag.getFloat("BloomMax");
        for (Tag t : tag.getList("Vents", Tag.TAG_LONG)) e.vents.add(BlockPos.of(((LongTag) t).getAsLong()));
        for (Tag t : tag.getList("Claims", Tag.TAG_INT_ARRAY)) e.syringeClaims.add(NbtUtils.loadUUID(t));
        // a building arena restarts its queue (placement is idempotent)
        return e;
    }
}
