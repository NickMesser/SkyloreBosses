package net.teamaof.skylorebosses.bosses.matriscalyx.net;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.teamaof.skylorebosses.SkyloreBosses;

/** Matris-only S2C packet: the infection clock value for the HUD. */
public final class MatrisNetwork {
    /** Client-side copy of the local player's infection (read by the HUD). */
    public static int clientInfection;

    public record InfectionSync(int value) implements CustomPacketPayload {
        public static final Type<InfectionSync> TYPE = new Type<>(SkyloreBosses.id("matris_calyx/infection"));
        public static final StreamCodec<ByteBuf, InfectionSync> CODEC = ByteBufCodecs.VAR_INT.map(InfectionSync::new, InfectionSync::value);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private MatrisNetwork() {}

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), InfectionSync.TYPE, InfectionSync.CODEC,
                    (p, ctx) -> ctx.queue(() -> clientInfection = p.value()));
        } else {
            NetworkManager.registerS2CPayloadType(InfectionSync.TYPE, InfectionSync.CODEC);
        }
    }

    public static void sendInfection(ServerPlayer p, int value) {
        if (NetworkManager.canPlayerReceive(p, InfectionSync.TYPE)) NetworkManager.sendToPlayer(p, new InfectionSync(value));
    }
}
