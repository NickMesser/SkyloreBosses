package net.teamaof.skylorebosses.bosses.amanita.registry;

import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.teamaof.skylorebosses.bosses.amanita.entity.AmanitaEntity;
import net.teamaof.skylorebosses.bosses.amanita.entity.HollowBoltEntity;
import net.teamaof.skylorebosses.bosses.amanita.entity.HollowSpawnEntity;
import net.teamaof.skylorebosses.bosses.amanita.entity.LampEaterEntity;
import net.teamaof.skylorebosses.core.registry.SBRegistries;

public final class AmanitaEntities {

    private static <T extends Entity> RegistrySupplier<EntityType<T>> reg(String id, EntityType.Builder<T> b) {
        return SBRegistries.ENTITY_TYPES.register(id, () -> b.build(id));
    }

    /** Amanita. 1.4 wide so she paths between the gill shelves and the columns; 3.2 tall. */
    public static final RegistrySupplier<EntityType<AmanitaEntity>> AMANITA = reg("amanita",
            EntityType.Builder.of(AmanitaEntity::new, MobCategory.MONSTER).sized(1.4f, 3.2f).fireImmune()
                    .clientTrackingRange(10).updateInterval(1));

    /** Lamp-eater: a pale moth-grub that eats lights. */
    public static final RegistrySupplier<EntityType<LampEaterEntity>> LAMP_EATER = reg("lamp_eater",
            EntityType.Builder.of(LampEaterEntity::new, MobCategory.MONSTER).sized(0.9f, 0.7f).clientTrackingRange(8));

    /** Hollow-spawn: a spore-husk thrall that fights players. */
    public static final RegistrySupplier<EntityType<HollowSpawnEntity>> HOLLOW_SPAWN = reg("hollow_spawn",
            EntityType.Builder.of(HollowSpawnEntity::new, MobCategory.MONSTER).sized(0.7f, 1.95f).clientTrackingRange(8));

    /** hollow_bolt projectile. */
    public static final RegistrySupplier<EntityType<HollowBoltEntity>> HOLLOW_BOLT = reg("hollow_bolt",
            EntityType.Builder.<HollowBoltEntity>of(HollowBoltEntity::new, MobCategory.MISC).sized(0.45f, 0.45f).fireImmune()
                    .clientTrackingRange(8).updateInterval(1));

    private AmanitaEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}

    public static void registerAttributes() {
        EntityAttributeRegistry.register(AMANITA, AmanitaEntity::createAttributes);
        EntityAttributeRegistry.register(LAMP_EATER, LampEaterEntity::createAttributes);
        EntityAttributeRegistry.register(HOLLOW_SPAWN, HollowSpawnEntity::createAttributes);
    }
}
