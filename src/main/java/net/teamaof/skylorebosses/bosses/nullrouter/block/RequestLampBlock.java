package net.teamaof.skylorebosses.bosses.nullrouter.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Request display lamp (DESIGN.md §6). One glyph of one request: the lamp stack over each console and the board on
 * the north wall are made of these. HEAD is the request at the head of the queue (bright), NEXT the second request of
 * the dual queue (dim: the decoy), OFF an unused slot.
 */
public class RequestLampBlock extends Block {
    public static final EnumProperty<Glyph> GLYPH = ChannelConsoleBlock.GLYPH;
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    public enum Mode implements StringRepresentable {
        OFF, HEAD, NEXT;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public RequestLampBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(GLYPH, Glyph.CIRCLE).setValue(MODE, Mode.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(GLYPH, MODE);
    }

    public static int light(BlockState s) {
        return switch (s.getValue(MODE)) {
            case OFF -> 0;
            case NEXT -> 4;
            case HEAD -> 13;
        };
    }
}
