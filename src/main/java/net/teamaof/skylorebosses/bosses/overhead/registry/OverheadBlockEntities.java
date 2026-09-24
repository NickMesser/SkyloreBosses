package net.teamaof.skylorebosses.bosses.overhead.registry;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.teamaof.skylorebosses.bosses.overhead.block.GeneratorPylonBlockEntity;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class OverheadBlockEntities {

    @SuppressWarnings("DataFlowIssue")
    public static final RegistrySupplier<BlockEntityType<GeneratorPylonBlockEntity>> GENERATOR_PYLON = SBRegistries.BLOCK_ENTITY_TYPES.register(
            "generator_pylon", () -> BlockEntityType.Builder.of(GeneratorPylonBlockEntity::new, OverheadBlocks.GENERATOR_PYLON.get()).build(null));

    private OverheadBlockEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
