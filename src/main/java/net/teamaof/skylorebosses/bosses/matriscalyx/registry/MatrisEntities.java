package net.teamaof.skylorebosses.bosses.matriscalyx.registry;

import net.teamaof.skylorebosses.core.registry.SBRegistries;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.InfectedDrifter;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.SporeMite;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.add.SporeThrall;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.AbstractRootedArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ArmType;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.ChargingArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.GraspingArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.MouthArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.NerveArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.SlamArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.SpittingArm;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.bloom.CalyxBloom;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.projectile.BileGlob;

public final class MatrisEntities {

    private static <T extends Entity> RegistrySupplier<EntityType<T>> reg(String id, EntityType.Builder<T> b) {
        return SBRegistries.ENTITY_TYPES.register(id, () -> b.build(id));
    }

    private static <T extends AbstractRootedArm> EntityType.Builder<T> arm(EntityType.EntityFactory<T> f, ArmType t) {
        return EntityType.Builder.of(f, MobCategory.MONSTER).sized(t.width, t.height).fireImmune()
                .clientTrackingRange(16).updateInterval(2);
    }

    public static final RegistrySupplier<EntityType<NerveArm>> NERVE_ARM = reg("nerve_arm", arm(NerveArm::new, ArmType.NERVE));
    public static final RegistrySupplier<EntityType<GraspingArm>> GRASPING_ARM = reg("grasping_arm", arm(GraspingArm::new, ArmType.GRASPING));
    public static final RegistrySupplier<EntityType<SlamArm>> SLAM_ARM = reg("slam_arm", arm(SlamArm::new, ArmType.SLAM));
    public static final RegistrySupplier<EntityType<ChargingArm>> CHARGING_ARM = reg("charging_arm", arm(ChargingArm::new, ArmType.CHARGING));
    public static final RegistrySupplier<EntityType<MouthArm>> MOUTH_ARM = reg("mouth_arm", arm(MouthArm::new, ArmType.MOUTH));
    public static final RegistrySupplier<EntityType<SpittingArm>> SPITTING_ARM = reg("spitting_arm", arm(SpittingArm::new, ArmType.SPITTING));

    public static final RegistrySupplier<EntityType<CalyxBloom>> CALYX_BLOOM = reg("calyx_bloom",
            EntityType.Builder.of(CalyxBloom::new, MobCategory.MONSTER).sized(10f, 30f).fireImmune().clientTrackingRange(20).updateInterval(2));

    public static final RegistrySupplier<EntityType<BileGlob>> BILE_GLOB = reg("bile_glob",
            EntityType.Builder.<BileGlob>of(BileGlob::new, MobCategory.MISC).sized(0.9f, 0.9f).clientTrackingRange(12).updateInterval(1));

    public static final RegistrySupplier<EntityType<SporeThrall>> SPORE_THRALL = reg("spore_thrall",
            EntityType.Builder.of(SporeThrall::new, MobCategory.MONSTER).sized(0.7f, 2.0f).clientTrackingRange(8));
    public static final RegistrySupplier<EntityType<SporeMite>> SPORE_MITE = reg("spore_mite",
            EntityType.Builder.of(SporeMite::new, MobCategory.MONSTER).sized(0.7f, 0.5f).clientTrackingRange(8));
    public static final RegistrySupplier<EntityType<InfectedDrifter>> INFECTED_DRIFTER = reg("infected_drifter",
            EntityType.Builder.of(InfectedDrifter::new, MobCategory.MONSTER).sized(1.6f, 2.4f).clientTrackingRange(10));

    private MatrisEntities() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}

    public static EntityType<? extends AbstractRootedArm> armType(ArmType t) {
        return switch (t) {
            case NERVE -> NERVE_ARM.get();
            case GRASPING -> GRASPING_ARM.get();
            case SLAM -> SLAM_ARM.get();
            case CHARGING -> CHARGING_ARM.get();
            case MOUTH -> MOUTH_ARM.get();
            case SPITTING -> SPITTING_ARM.get();
        };
    }

    public static void registerAttributes() {
        EntityAttributeRegistry.register(NERVE_ARM, () -> AbstractRootedArm.createAttributes(ArmType.NERVE));
        EntityAttributeRegistry.register(GRASPING_ARM, () -> AbstractRootedArm.createAttributes(ArmType.GRASPING));
        EntityAttributeRegistry.register(SLAM_ARM, () -> AbstractRootedArm.createAttributes(ArmType.SLAM));
        EntityAttributeRegistry.register(CHARGING_ARM, () -> AbstractRootedArm.createAttributes(ArmType.CHARGING));
        EntityAttributeRegistry.register(MOUTH_ARM, () -> AbstractRootedArm.createAttributes(ArmType.MOUTH));
        EntityAttributeRegistry.register(SPITTING_ARM, () -> AbstractRootedArm.createAttributes(ArmType.SPITTING));
        EntityAttributeRegistry.register(CALYX_BLOOM, CalyxBloom::createAttributes);
        EntityAttributeRegistry.register(SPORE_THRALL, SporeThrall::createAttributes);
        EntityAttributeRegistry.register(SPORE_MITE, SporeMite::createAttributes);
        EntityAttributeRegistry.register(INFECTED_DRIFTER, InfectedDrifter::createAttributes);
    }
}
