package net.teamaof.skylorebosses.bosses.overhead.registry;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.teamaof.skylorebosses.bosses.overhead.entity.Ordnance;
import net.teamaof.skylorebosses.bosses.overhead.entity.OverheadEntity;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class OverheadEntities {

    private static <T extends Entity> RegistrySupplier<EntityType<T>> reg(String id, EntityType.Builder<T> b) {
        return SBRegistries.ENTITY_TYPES.register(id, () -> b.build(id));
    }

    /** The chassis. Hitbox is generous on purpose: ranged players must be able to land hits on a hovering target. */
    public static final RegistrySupplier<EntityType<OverheadEntity>> OVERHEAD = reg("overhead",
            EntityType.Builder.of(OverheadEntity::new, MobCategory.MONSTER).sized(6.0f, 3.5f).fireImmune()
                    .clientTrackingRange(16).updateInterval(1));

    /** Every shell, missile, bolt, flare and flak round (one class, a synced kind). */
    public static final RegistrySupplier<EntityType<Ordnance>> ORDNANCE = reg("overhead_ordnance",
            EntityType.Builder.<Ordnance>of(Ordnance::new, MobCategory.MISC).sized(0.5f, 0.5f).fireImmune()
                    .clientTrackingRange(12).updateInterval(1));

    private OverheadEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}

    public static void registerAttributes() {
        EntityAttributeRegistry.register(OVERHEAD, OverheadEntity::createAttributes);
    }
}
