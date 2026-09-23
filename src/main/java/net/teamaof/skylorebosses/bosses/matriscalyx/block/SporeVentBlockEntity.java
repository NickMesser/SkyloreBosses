package net.teamaof.skylorebosses.bosses.matriscalyx.block;

import net.teamaof.skylorebosses.core.registry.SBSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.AbstractAdd;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.SporeThrall;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisBlockEntities;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisEntities;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SporeVentBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String A = "animation.spore_vent.";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int hp = -1;
    private boolean broken;
    /** Encounter-controlled; a hand-placed vent is always active. */
    private boolean active = true;
    private int spawnTimer = 200;
    private int puffTimer = 160;
    private int openTicks;
    private int birthIn = -1;

    public SporeVentBlockEntity(BlockPos pos, BlockState state) {
        super(MatrisBlockEntities.SPORE_VENT.get(), pos, state);
    }

    public boolean isBroken() { return broken; }
    public boolean isOpen() { return openTicks > 0; }
    public void setActive(boolean a) { active = a; setChanged(); }

    public void repair() {
        broken = false;
        hp = MatrisConfig.VENT_HP.get();
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SporeVentBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        if (hp < 0) hp = MatrisConfig.VENT_HP.get();
        if (broken || !active) return;
        if (openTicks > 0) openTicks--;
        Vec3 top = spout();
        if (level.getNearestPlayer(top.x, top.y, top.z, 48, p -> !((Player) p).isSpectator()) == null) return;
        if (birthIn >= 0 && birthIn-- == 0) birth(level);
        if (--puffTimer <= 0) {
            puffTimer = 180 + level.random.nextInt(80);
            puff();
        }
        if (--spawnTimer <= 0) {
            spawnTimer = MatrisConfig.VENT_SPAWN_TICKS.get() + level.random.nextInt(200) - 100;
            int players = level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(64)).size();
            int cap = MatrisConfig.ADD_CAP_BASE.get() + MatrisConfig.ADD_CAP_PER_PLAYER.get() * Math.max(1, players);
            if (level.getEntitiesOfClass(AbstractAdd.class, new AABB(worldPosition).inflate(96)).size() < cap) {
                triggerAnim("main", "spawn_add");
                openTicks = 40;
                birthIn = 22;
            }
        }
    }

    /** Also used by the Spore Exhale body attack. */
    public void puff() {
        if (broken) return;
        triggerAnim("main", "puff");
        openTicks = Math.max(openTicks, 28);
    }

    private Vec3 spout() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, 2.2, 0);
    }

    private void birth(ServerLevel level) {
        int roll = level.random.nextInt(10);
        EntityType<? extends AbstractAdd> type = roll < 5 ? MatrisEntities.SPORE_THRALL.get()
                : roll < 8 ? MatrisEntities.SPORE_MITE.get() : MatrisEntities.INFECTED_DRIFTER.get();
        int count = type == MatrisEntities.SPORE_MITE.get() ? 3 : 1;
        for (int i = 0; i < count; i++) {
            AbstractAdd add = type.create(level);
            if (add == null) return;
            Vec3 at = spout().add(level.random.nextDouble() - 0.5, 0.2, level.random.nextDouble() - 0.5);
            add.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
            add.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.MOB_SUMMONED, null);
            add.setPersistenceRequired();
            level.addFreshEntity(add);
            if (add instanceof SporeThrall) add.triggerAnim("main", "emerge");
        }
    }

    /** Player hit the vent. Only counts while open. */
    public void strike(Player player) {
        if (broken || !(level instanceof ServerLevel sl)) return;
        if (!isOpen()) {
            AnimFx.play(sl, spout(), "matris_calyx.arm.thunk", 0.8f, 1.2f);
            return;
        }
        double dmg = Math.max(1, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        hp -= (int) Math.ceil(dmg);
        triggerAnim("main", "hurt");
        AnimFx.serverBurst(sl, SBParticles.SPORE_PUFF.get(), spout(), 8, 0.4, 0.05);
        if (hp <= 0) {
            broken = true;
            triggerAnim("main", "broken");
            Infection.add(player, 3);
            sync();
        }
        setChanged();
    }

    /** Spore Exhale visuals; the encounter applies the infection once per player. */
    public void exhale(ServerLevel level) {
        if (broken) return;
        puff();
        AnimFx.serverBurst(level, SBParticles.SPORE_HAZE.get(), spout(), 40, 4, 0.03);
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        tag.putInt("Hp", hp);
        tag.putBoolean("Broken", broken);
        tag.putBoolean("Active", active);
        tag.putInt("SpawnTimer", spawnTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        hp = tag.contains("Hp") ? tag.getInt("Hp") : -1;
        broken = tag.getBoolean("Broken");
        active = !tag.contains("Active") || tag.getBoolean("Active");
        if (tag.contains("SpawnTimer")) spawnTimer = tag.getInt("SpawnTimer");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        CompoundTag t = super.getUpdateTag(regs);
        t.putBoolean("Broken", broken);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<SporeVentBlockEntity> c = new AnimationController<>(this, "main", 3, s -> broken
                ? s.setAndContinue(RawAnimation.begin().thenPlayAndHold(A + "broken"))
                : s.setAndContinue(RawAnimation.begin().thenLoop(A + "idle")));
        for (String n : new String[]{"puff", "spawn_add", "hurt"}) c.triggerableAnim(n, RawAnimation.begin().thenPlay(A + n));
        c.triggerableAnim("broken", RawAnimation.begin().thenPlayAndHold(A + "broken"));
        c.setSoundKeyframeHandler(e -> {
            if (level != null && level.isClientSide) {
                String id = e.getKeyframeData().getSound();
                var s = AnimFx.keyframeSoundEvent(id);
                if (s != null) level.playLocalSound(worldPosition, s, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 1f, false);
            }
        });
        c.setParticleKeyframeHandler(e -> {
            if (level != null && level.isClientSide) {
                var type = SBParticles.get(e.getKeyframeData().getEffect().replace("matris_calyx:", ""));
                if (type != null) AnimFx.burst(level, type, e.getKeyframeData().getEffect().replace("matris_calyx:", ""), spout(), 1f);
            }
        });
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
