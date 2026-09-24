package net.teamaof.skylorebosses.bosses.staticdeacon.registry;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.DeaconProjectile;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.StaticDeaconEntity;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class DeaconEntities {

    private static <T extends Entity> RegistrySupplier<EntityType<T>> reg(String id, EntityType.Builder<T> b) {
        return SBRegistries.ENTITY_TYPES.register(id, () -> b.build(id));
    }

    /** The Deacon. 1.2 wide so it paths through the 2-wide gaps between pews and pillars; 3.4 tall. */
    public static final RegistrySupplier<EntityType<StaticDeaconEntity>> STATIC_DEACON = reg("static_deacon",
            EntityType.Builder.of(StaticDeaconEntity::new, MobCategory.MONSTER).sized(1.2f, 3.4f).fireImmune()
                    .clientTrackingRange(10).updateInterval(1));

    /** Static bolts and homing shards (one class, a synced kind). */
    public static final RegistrySupplier<EntityType<DeaconProjectile>> PROJECTILE = reg("deacon_projectile",
            EntityType.Builder.<DeaconProjectile>of(DeaconProjectile::new, MobCategory.MISC).sized(0.5f, 0.5f).fireImmune()
                    .clientTrackingRange(8).updateInterval(1));

    private DeaconEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}

    public static void registerAttributes() {
        EntityAttributeRegistry.register(STATIC_DEACON, StaticDeaconEntity::createAttributes);
    }
}
