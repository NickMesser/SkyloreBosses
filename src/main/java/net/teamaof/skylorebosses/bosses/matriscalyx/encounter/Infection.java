package net.teamaof.skylorebosses.bosses.matriscalyx.encounter;

import net.teamaof.skylorebosses.core.net.SBNetwork;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.MatrisConfig;
import net.teamaof.skylorebosses.bosses.matriscalyx.net.MatrisNetwork;
import net.teamaof.skylorebosses.bosses.matriscalyx.registry.MatrisAttachments;
import net.teamaof.skylorebosses.core.fx.AnimFx;

/**
 * The infection clock (DESIGN.md section 8). Canon: this is ambient spread inside her body, an accident,
 * never tied to the path the player chose. Cures always exist and always work.
 */
public final class Infection {
    public static final int MAX = 100;
    public static final TagKey<Item> CURES = ItemTags.create(SkyloreBosses.id("matris_calyx/cures"));

    private static InfectionBridge bridge;
    private static Map<ResourceLocation, Integer> cureValues;

    private Infection() {}

    public static InfectionBridge bridge() {
        if (bridge == null) bridge = InfectionBridge.create();
        return bridge;
    }

    public static int get(Player p) {
        return p.getData(MatrisAttachments.INFECTION.get());
    }

    public static void set(ServerPlayer p, int value) {
        p.setData(MatrisAttachments.INFECTION_FRACTION.get(), 0f);
        write(p, value);
    }

    private static void write(ServerPlayer p, int value) {
        int old = get(p);
        int v = Math.max(0, Math.min(MAX, value));
        if (v == old) return;
        p.setData(MatrisAttachments.INFECTION.get(), v);
        MatrisNetwork.sendInfection(p, v);
        bridge().onChanged(p, old, v);
    }

    /** Ambient or hit-based spread. Scaled by the bridge (e.g. an existing Immortuos Calyx infection). */
    public static void add(Player p, int amount) {
        if (!(p instanceof ServerPlayer sp) || amount <= 0 || !MatrisConfig.INFECTION_ENABLED.get()) return;
        if (sp.isCreative() || sp.isSpectator()) return;
        float fraction = sp.getData(MatrisAttachments.INFECTION_FRACTION.get()) + amount * bridge().incomingMultiplier(sp);
        int whole = (int) Math.floor(fraction);
        fraction -= whole;
        if (fraction < 0f) fraction = 0f;
        sp.setData(MatrisAttachments.INFECTION_FRACTION.get(), fraction);
        if (whole > 0) write(sp, get(sp) + whole);
    }

    /** Cure {@code target} by {@code amount}; doctors cure better (never exclusively). */
    public static void cure(ServerPlayer target, int amount, @Nullable Player healer) {
        Player h = healer == null ? target : healer;
        int a = isDoctor(h) ? Math.round(amount * MatrisConfig.DOCTOR_CURE_MULTIPLIER.get().floatValue()) : amount;
        set(target, get(target) - a);
        AnimFx.play(target.level(), target.position(), "matris_calyx.syringe.inject", 0.8f, 1.2f);
    }

    public static boolean isDoctor(Player p) {
        return net.teamaof.skylorebosses.core.Doctors.isDoctor(p);
    }

    /** Called when a player finishes eating/drinking. */
    public static void onItemConsumed(ServerPlayer p, ItemStack stack) {
        if (cureValues == null) {
            cureValues = new HashMap<>();
            for (String s : MatrisConfig.CURE_VALUES.get()) {
                String[] kv = s.split("=");
                try {
                    cureValues.put(ResourceLocation.parse(kv[0].trim()), Integer.parseInt(kv[1].trim()));
                } catch (Exception e) {
                    SkyloreBosses.LOG.warn("Bad cureValues entry {}", s);
                }
            }
        }
        Integer v = cureValues.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (v == null && stack.is(CURES)) v = 15;
        if (v != null && get(p) > 0) cure(p, v, null);
    }

    /** Once per second for players inside an active arena. */
    public static void tickSecond(ServerPlayer p, long second, BlockPos anchor) {
        if (!MatrisConfig.INFECTION_ENABLED.get() || p.isCreative() || p.isSpectator()) return;
        if (second % MatrisConfig.INFECTION_PASSIVE_SECONDS.get() == 0) add(p, 1);
        int v = get(p);
        if (v >= 25) p.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, true, false));
        if (v >= 50) p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false));
        if (v >= 75) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false));
        if (v >= 75 && second % 4 == 0) AnimFx.play(p.level(), p.position(), "matris_calyx.infection.rise", 0.6f, 1f);
        if (v >= MAX) taken(p, anchor);
    }

    /** At 100 the player is "Taken": knocked out and returned to the anchor. Not a death, no item loss. */
    public static void taken(ServerPlayer p, BlockPos anchor) {
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
        Vec3 a = Vec3.atBottomCenterOf(anchor).add(0, 1, 0);
        p.teleportTo(p.serverLevel(), a.x, a.y, a.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        set(p, 40);
        AnimFx.play(p.level(), a, "matris_calyx.infection.taken", 1.0f, 1f);
        p.displayClientMessage(Component.translatable("matris_calyx.infection.taken").withStyle(ChatFormatting.DARK_RED), true);
        SBNetwork.sendScreenFx(p, SBNetwork.FX_TAKEN, 60);
    }
}
