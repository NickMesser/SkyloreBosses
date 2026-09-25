package net.teamaof.skylorebosses.bosses.nullrouter.block;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;

/**
 * The request alphabet (DESIGN.md §6): three glyphs per channel, each a distinct shape and colour so the pattern reads
 * without colour vision and without text. A request is one glyph per channel A, B, C.
 */
public enum Glyph implements StringRepresentable {
    CIRCLE("○", ChatFormatting.AQUA, 0x40E0FF),
    TRIANGLE("▲", ChatFormatting.GOLD, 0xFFB030),
    SQUARE("□", ChatFormatting.LIGHT_PURPLE, 0xFF50E0);

    public final String symbol;
    public final ChatFormatting chat;
    public final int rgb;

    Glyph(String symbol, ChatFormatting chat, int rgb) {
        this.symbol = symbol;
        this.chat = chat;
        this.rgb = rgb;
    }

    public Glyph next() { return values()[(ordinal() + 1) % 3]; }

    public static Glyph of(int i) { return values()[Math.floorMod(i, 3)]; }

    @Override
    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

    /** "○" in the glyph's colour, for boss bar titles and action-bar lines. */
    public MutableComponent chip() {
        return Component.literal(symbol).withStyle(chat, ChatFormatting.BOLD);
    }

    /** Three glyphs as "○ ▲ □" with colours. */
    public static MutableComponent pattern(int[] p) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) c.append(Component.literal(" "));
            c.append(of(p[i]).chip());
        }
        return c;
    }
}
