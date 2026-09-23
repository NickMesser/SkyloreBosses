package net.teamaof.skylorebosses.bosses.matriscalyx.registry;

import net.teamaof.skylorebosses.core.registry.SBRegistries;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.block.SporeVentBlockEntity;

public final class MatrisBlockEntities {

    @SuppressWarnings("DataFlowIssue")
    public static final RegistrySupplier<BlockEntityType<SporeVentBlockEntity>> SPORE_VENT = SBRegistries.BLOCK_ENTITY_TYPES.register("spore_vent",
            () -> BlockEntityType.Builder.of(SporeVentBlockEntity::new, MatrisBlocks.SPORE_VENT.get()).build(null));

    private MatrisBlockEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
