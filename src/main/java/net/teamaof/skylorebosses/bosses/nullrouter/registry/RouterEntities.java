package net.teamaof.skylorebosses.bosses.nullrouter.registry;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.NullRouterEntity;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.RetryPacketEntity;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class RouterEntities {

    private static <T extends Entity> RegistrySupplier<EntityType<T>> reg(String id, EntityType.Builder<T> b) {
        return SBRegistries.ENTITY_TYPES.register(id, () -> b.build(id));
    }

    /** The chassis: 2.4 wide, 2.8 tall, hovering over the pad. */
    public static final RegistrySupplier<EntityType<NullRouterEntity>> NULL_ROUTER = reg("null_router",
            EntityType.Builder.of(NullRouterEntity::new, MobCategory.MONSTER).sized(2.4f, 2.8f).fireImmune()
                    .clientTrackingRange(10).updateInterval(1));

    /** Retry packets: small, fast, fragile adds that undo consoles. */
    public static final RegistrySupplier<EntityType<RetryPacketEntity>> RETRY_PACKET = reg("retry_packet",
            EntityType.Builder.of(RetryPacketEntity::new, MobCategory.MONSTER).sized(0.7f, 0.7f).fireImmune()
                    .clientTrackingRange(8).updateInterval(2));

    private RouterEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}

    public static void registerAttributes() {
        EntityAttributeRegistry.register(NULL_ROUTER, NullRouterEntity::createAttributes);
        EntityAttributeRegistry.register(RETRY_PACKET, RetryPacketEntity::createAttributes);
    }
}
