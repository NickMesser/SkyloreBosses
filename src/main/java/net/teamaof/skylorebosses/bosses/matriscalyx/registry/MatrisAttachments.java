package net.teamaof.skylorebosses.bosses.matriscalyx.registry;

import net.teamaof.skylorebosses.core.registry.SBRegistries;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.teamaof.skylorebosses.SkyloreBosses;

/** Player data attachments (NeoForge-only: Architectury has no attachment abstraction). */
public final class MatrisAttachments {

    /** Infection clock 0..100. Survives death: it is ambient spread, not a debuff. */
    public static final Supplier<AttachmentType<Integer>> INFECTION = SBRegistries.ATTACHMENT_TYPES.register("infection",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    private MatrisAttachments() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
