package net.teamaof.skylorebosses.bosses.nullrouter.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vaults;

/**
 * One of the three channel consoles (DESIGN.md §6). Right-click (empty hand or any item) cycles its glyph
 * ○ → ▲ → □ → ○. The vault owns the glyph and the status; the block state is the view, so every player (and every LOD
 * renderer) reads the same thing. Unbreakable and explosion-proof: consoles are set, never destroyed.
 */
public class ChannelConsoleBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ChannelConsoleBlock> CODEC = simpleCodec(ChannelConsoleBlock::new);
    public static final EnumProperty<Glyph> GLYPH = EnumProperty.create("glyph", Glyph.class);
    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);

    /** What the console is doing, shown on its bezel. */
    public enum Status implements StringRepresentable {
        /** Settable. */
        IDLE,
        /** All three consoles match a displayed request; it commits after armTicks unless someone flips again. */
        ARMING,
        /** An ACK window is open: consoles are locked until it closes. */
        LOCKED,
        /** Something is uplinking to this console (a retry packet or console_flick); it is about to be flipped. */
        ALERT;

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public ChannelConsoleBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(GLYPH, Glyph.CIRCLE).setValue(STATUS, Status.IDLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, GLYPH, STATUS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) Vaults.get(sl).onConsoleUse(sl, pos, sp);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static int light(BlockState s) {
        return switch (s.getValue(STATUS)) {
            case IDLE -> 5;
            case ARMING, ALERT -> 9;
            case LOCKED -> 12;
        };
    }
}
