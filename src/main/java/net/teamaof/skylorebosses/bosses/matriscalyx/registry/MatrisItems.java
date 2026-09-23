package net.teamaof.skylorebosses.bosses.matriscalyx.registry;

import net.teamaof.skylorebosses.core.registry.SBRegistries;
import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.bosses.matriscalyx.item.PurgativeSyringeItem;

public final class MatrisItems {

    public static final RegistrySupplier<Item> PURGATIVE_SYRINGE = SBRegistries.ITEMS.register("purgative_syringe",
            () -> new PurgativeSyringeItem(new Item.Properties().stacksTo(16)));
    public static final RegistrySupplier<Item> CALYX_HEART = SBRegistries.ITEMS.register("calyx_heart",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    static {
        for (RegistrySupplier<Block> b : List.of(MatrisBlocks.FLESH, MatrisBlocks.HARDENED_FLESH, MatrisBlocks.FLESH_MEMBRANE, MatrisBlocks.GLOW_VEIN,
                MatrisBlocks.HEART_CORE, MatrisBlocks.RIB_BONE, MatrisBlocks.RUPTURED_VENT, MatrisBlocks.SPORE_VENT, MatrisBlocks.SYRINGE_CACHE)) {
            SBRegistries.ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
        egg("nerve_arm", MatrisEntities.NERVE_ARM, 0x3c6e8f, 0x9ff0ff);
        egg("grasping_arm", MatrisEntities.GRASPING_ARM, 0x96404a, 0xd8c6a4);
        egg("slam_arm", MatrisEntities.SLAM_ARM, 0x5c2230, 0xe6dcb4);
        egg("charging_arm", MatrisEntities.CHARGING_ARM, 0x6e4a3a, 0xd6c6a4);
        egg("mouth_arm", MatrisEntities.MOUTH_ARM, 0x96404a, 0xffe6be);
        egg("spitting_arm", MatrisEntities.SPITTING_ARM, 0x96404a, 0x96c828);
        egg("calyx_bloom", MatrisEntities.CALYX_BLOOM, 0xe2d6c4, 0xe6aa28);
        egg("spore_thrall", MatrisEntities.SPORE_THRALL, 0xbe968c, 0xaa966e);
        egg("spore_mite", MatrisEntities.SPORE_MITE, 0x46343c, 0xaa966e);
        egg("infected_drifter", MatrisEntities.INFECTED_DRIFTER, 0xbe968c, 0x96c828);
    }

    private static void egg(String id, RegistrySupplier<? extends EntityType<? extends Mob>> type, int bg, int fg) {
        SBRegistries.ITEMS.register(id + "_spawn_egg", () -> new ArchitecturySpawnEggItem(type, bg, fg, new Item.Properties()));
    }


    private MatrisItems() {}

    /** Forces class init so the entries are queued before registration. */
    public static void init() {}
}
