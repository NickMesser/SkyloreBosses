package net.teamaof.skylorebosses.bosses.overhead.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.overhead.encounter.PylonState;
import net.teamaof.skylorebosses.bosses.overhead.registry.OverheadBlockEntities;
import net.teamaof.skylorebosses.core.fx.AnimFx;
import net.teamaof.skylorebosses.core.registry.SBParticles;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * View of one pylon slot. The yard ({@code encounter.Yard}) owns the real state and pushes it here with
 * {@link #mirror}; this class only stores what the client needs to draw it (state + integrity fraction).
 */
public class GeneratorPylonBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final String A = "animation.generator_pylon.";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private PylonState state = PylonState.ONLINE;
    private float fraction = 1f;
    private int index = -1;

    public GeneratorPylonBlockEntity(BlockPos pos, BlockState state) {
        super(OverheadBlockEntities.GENERATOR_PYLON.get(), pos, state);
    }

    public PylonState state() { return state; }
    public float fraction() { return fraction; }
    public int index() { return index; }

    /** Server: copy the yard's state. Sends an update only when something visible changed. */
    public void mirror(int index, PylonState s, float frac) {
        boolean changed = s != state || this.index != index || Math.abs(frac - fraction) > 0.02f || (frac >= 1f) != (fraction >= 1f);
        this.index = index;
        this.state = s;
        this.fraction = frac;
        if (changed) {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public Vec3 top() { return Vec3.atBottomCenterOf(worldPosition).add(0, 5, 0); }

    public Vec3 core() { return Vec3.atBottomCenterOf(worldPosition).add(0, 4.2, 0); }

    public static void clientTick(Level level, BlockPos pos, BlockState bs, GeneratorPylonBlockEntity be) {
        long t = level.getGameTime() + pos.asLong() % 20;
        switch (be.state) {
            case OFFLINE -> {
                if (t % 6 == 0) AnimFx.burst(level, SBParticles.SMOKE.get(), "smoke", be.core().add(0, 0.5, 0), 1f);
                if (t % 23 == 0) AnimFx.burst(level, SBParticles.SPARK.get(), "spark", be.core(), 0.6f);
            }
            case REBUILDING -> {
                if (t % 5 == 0) AnimFx.burst(level, SBParticles.SPARK.get(), "spark", be.top(), 0.5f);
                if (t % 3 == 0) level.addParticle(SBParticles.GRID_ARC.get(), be.core().x, be.core().y - 3 + 3 * be.fraction, be.core().z, 0, 0.05, 0);
            }
            case ONLINE -> {
                if (t % 9 == 0) AnimFx.burst(level, SBParticles.GRID_ARC.get(), "grid_arc", be.core(), 0.4f);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.saveAdditional(tag, regs);
        tag.putInt("State", state.ordinal());
        tag.putFloat("Fraction", fraction);
        tag.putInt("Index", index);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider regs) {
        super.loadAdditional(tag, regs);
        state = PylonState.byOrdinal(tag.getInt("State"));
        fraction = tag.contains("Fraction") ? tag.getFloat("Fraction") : 1f;
        index = tag.contains("Index") ? tag.getInt("Index") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider regs) {
        CompoundTag t = super.getUpdateTag(regs);
        saveAdditional(t, regs);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public AABB renderBox() {
        return new AABB(worldPosition).inflate(2, 0, 2).expandTowards(0, 6, 0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<GeneratorPylonBlockEntity> c = new AnimationController<>(this, "main", 4, s -> switch (state) {
            case OFFLINE -> s.setAndContinue(RawAnimation.begin().thenLoop(A + "offline"));
            case REBUILDING -> s.setAndContinue(RawAnimation.begin().thenLoop(A + "rebuild"));
            default -> s.setAndContinue(RawAnimation.begin().thenLoop(A + "online"));
        });
        for (String n : new String[]{"hurt", "overcharge"}) c.triggerableAnim(n, RawAnimation.begin().thenPlay(A + n));
        c.triggerableAnim("break", RawAnimation.begin().thenPlayAndHold(A + "break"));
        c.setSoundKeyframeHandler(e -> {
            if (level != null && level.isClientSide) {
                var s = AnimFx.keyframeSoundEvent(e.getKeyframeData().getSound());
                if (s != null) level.playLocalSound(worldPosition, s, SoundSource.HOSTILE, 1.4f, 1f, false);
            }
        });
        c.setParticleKeyframeHandler(e -> {
            if (level != null && level.isClientSide) {
                String name = e.getKeyframeData().getEffect();
                name = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
                var type = SBParticles.get(name);
                if (type != null) AnimFx.burst(level, type, name, core(), 1f);
            }
        });
        controllers.add(c);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
