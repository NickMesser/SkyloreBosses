package net.teamaof.skylorebosses.core;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.teamaof.skylorebosses.SkyloreBosses;
import net.teamaof.skylorebosses.core.config.SBConfig;

/** The Skylore doctor role, shared by every boss: better at curing, never the only one who can. */
public final class Doctors {
    public static final TagKey<Item> DOCTOR_TOOLS = ItemTags.create(SkyloreBosses.id("doctor_tools"));

    private Doctors() {}

    public static boolean isDoctor(Player p) {
        String tag = SBConfig.DOCTOR_PLAYER_TAG.get();
        if (!tag.isEmpty() && p.getTags().contains(tag)) return true;
        return p.getOffhandItem().is(DOCTOR_TOOLS) || p.getMainHandItem().is(DOCTOR_TOOLS);
    }
}
