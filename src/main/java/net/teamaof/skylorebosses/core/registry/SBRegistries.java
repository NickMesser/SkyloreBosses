package net.teamaof.skylorebosses.core.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.core.BossModule;
import net.teamaof.skylorebosses.core.BossModules;

/**
 * Shared registers for every boss. Boss registry classes add entries here from their static
 * initialisers (see {@link BossModule#registerContent()}); everything is registered once, together.
 */
public final class SBRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.BLOCK);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.ENTITY_TYPE);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.SOUND_EVENT);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.PARTICLE_TYPE);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(SkyloreBosses.MOD_ID, Registries.CREATIVE_MODE_TAB);
    /** NeoForge-only (Architectury has no attachment abstraction). */
    public static final net.neoforged.neoforge.registries.DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            net.neoforged.neoforge.registries.DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, SkyloreBosses.MOD_ID);

    /** One tab for all bosses; lists every item in registration order. */
    public static final RegistrySupplier<CreativeModeTab> TAB = CREATIVE_TABS.register("main", () -> CreativeTabRegistry.create(b -> b
            .title(Component.translatable("itemGroup.skylore_bosses"))
            .icon(() -> ITEMS.getRegistrar().get(SkyloreBosses.id("calyx_heart")) instanceof Item i ? new ItemStack(i) : new ItemStack(Items.WITHER_SKELETON_SKULL))
            .displayItems((params, out) -> ITEMS.forEach(s -> out.accept(s.get())))));

    private SBRegistries() {}

    public static void init(IEventBus modBus) {
        SBParticles.init();
        for (BossModule m : BossModules.all()) {
            m.registerContent();
            SBSounds.registerModule(m);
        }
        SOUND_EVENTS.register();
        PARTICLE_TYPES.register();
        BLOCKS.register();
        ENTITY_TYPES.register();
        BLOCK_ENTITY_TYPES.register();
        ITEMS.register();
        CREATIVE_TABS.register();
        ATTACHMENT_TYPES.register(modBus);
    }
}
