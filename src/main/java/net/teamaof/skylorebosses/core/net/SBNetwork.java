package net.teamaof.skylorebosses.core.net;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.teamaof.skylorebosses.SkyloreBosses;

/** Shared S2C screen effects any boss can use: whiteout flash, camera shake, dark "taken" fade. */
public final class SBNetwork {
    public static final int FX_WHITEOUT = 0, FX_SHAKE = 1, FX_TAKEN = 2;

    public record ScreenFx(int kind, int ticks) implements CustomPacketPayload {
        public static final Type<ScreenFx> TYPE = new Type<>(SkyloreBosses.id("screen_fx"));
        public static final StreamCodec<ByteBuf, ScreenFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ScreenFx::kind, ByteBufCodecs.VAR_INT, ScreenFx::ticks, ScreenFx::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private SBNetwork() {}

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), ScreenFx.TYPE, ScreenFx.CODEC,
                    (p, ctx) -> ctx.queue(() -> ClientFxState.trigger(p.kind(), p.ticks())));
        } else {
            NetworkManager.registerS2CPayloadType(ScreenFx.TYPE, ScreenFx.CODEC);
        }
    }

    public static void sendScreenFx(ServerPlayer p, int kind, int ticks) {
        if (NetworkManager.canPlayerReceive(p, ScreenFx.TYPE)) NetworkManager.sendToPlayer(p, new ScreenFx(kind, ticks));
    }

    /** Plain holder read by the client overlay (no client classes, safe to load on a server). */
    public static final class ClientFxState {
        public static int whiteout, whiteoutMax, shake, taken;

        public static void trigger(int kind, int ticks) {
            switch (kind) {
                case FX_WHITEOUT -> { whiteout = ticks; whiteoutMax = ticks; }
                case FX_SHAKE -> shake = Math.max(shake, ticks);
                case FX_TAKEN -> taken = ticks;
                default -> {}
            }
        }

        public static void tick() {
            if (whiteout > 0) whiteout--;
            if (shake > 0) shake--;
            if (taken > 0) taken--;
        }
    }
}
