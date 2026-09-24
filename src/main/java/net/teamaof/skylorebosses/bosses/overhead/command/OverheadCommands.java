package net.teamaof.skylorebosses.bosses.overhead.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.overhead.encounter.OverheadYards;
import net.teamaof.skylorebosses.bosses.overhead.encounter.Yard;
import net.teamaof.skylorebosses.bosses.overhead.encounter.YardLayout;
import net.teamaof.skylorebosses.bosses.overhead.entity.Action;
import net.teamaof.skylorebosses.bosses.overhead.entity.OverheadEntity;

/**
 * Designer commands (permission 2), as {@code /skylorebosses overhead ...} or the {@code /skyloreoverhead} alias:
 * <pre>
 * build [pos]            place the test yard at pos (default: 40 blocks above you) and register it
 * register [pos]         adopt an existing yard (structure) at pos without placing blocks
 * unregister             forget the nearest yard
 * start | reset          start the fight / reset the nearest yard to dormant
 * skipphase              advance one beat (P0 -> P1, break a pylon, end exposure, finish re-arm)
 * breakpylon &lt;0..3&gt;     break one pylon (opens a window, fires events)
 * setpylons &lt;0..4&gt;      force the live pylon count (no windows)
 * attack &lt;id&gt;           force an attack now (keeps its telegraph)
 * status | tp            describe / teleport to the entry pad
 * </pre>
 */
public final class OverheadCommands {
    private OverheadCommands() {}

    public static void build(LiteralArgumentBuilder<CommandSourceStack> node) {
        node
                .then(Commands.literal("build")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).offset(0, 40, 0), true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), true))))
                .then(Commands.literal("register")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).below(), false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), false))))
                .then(Commands.literal("unregister").executes(c -> withYard(c, y -> {
                    OverheadYards.get(c.getSource().getLevel()).remove(c.getSource().getLevel(), y.origin);
                    return "unregistered " + y.origin.toShortString();
                })))
                .then(Commands.literal("start").executes(c -> withYard(c, y -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    boolean ok = y.tryStart(c.getSource().getLevel(), p);
                    return ok ? "started: " + y.describe(c.getSource().getLevel()) : "not dormant (or vetoed): " + y.phase().id();
                })))
                .then(Commands.literal("reset").executes(c -> withYard(c, y -> {
                    y.reset(c.getSource().getLevel(), "command");
                    return "reset: " + y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("skipphase").executes(c -> withYard(c, y -> {
                    y.skipPhase(c.getSource().getLevel());
                    return "skipped: " + y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("breakpylon").then(Commands.argument("index", IntegerArgumentType.integer(0, 3)).executes(c -> withYard(c, y -> {
                    y.breakPylon(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "index"), null);
                    return y.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("setpylons").then(Commands.argument("count", IntegerArgumentType.integer(0, 4)).executes(c -> withYard(c, y -> {
                    y.setPylons(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "count"));
                    return y.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("attack").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Action.values()).map(Action::id), b))
                        .executes(OverheadCommands::attack)))
                .then(Commands.literal("status").executes(c -> withYard(c, y -> y.describe(c.getSource().getLevel()))))
                .then(Commands.literal("tp").executes(c -> withYard(c, y -> {
                    try {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Vec3 a = Vec3.atBottomCenterOf(YardLayout.entryPad(y.origin));
                        p.teleportTo(c.getSource().getLevel(), a.x, a.y, a.z, 180, 0);
                        return "teleported to " + y.origin.toShortString();
                    } catch (CommandSyntaxException e) {
                        return "players only";
                    }
                })));
    }

    private static int create(CommandContext<CommandSourceStack> c, BlockPos pos, boolean build) {
        ServerLevel level = c.getSource().getLevel();
        Yard y = OverheadYards.get(level).create(level, pos, build);
        c.getSource().sendSuccess(() -> Component.literal((build ? "Built" : "Registered") + " Teknari yard at " + pos.toShortString()
                + ". Walk in (or use the console at " + YardLayout.console(pos).toShortString() + ") to start."), true);
        return 1;
    }

    private static int attack(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        Action a;
        try {
            a = Action.byId(id);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("unknown attack " + id));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        Yard y = OverheadYards.get(level).nearest(BlockPos.containing(c.getSource().getPosition()), 128);
        OverheadEntity oh = y == null ? null : y.overhead(level);
        if (oh == null) {
            var list = level.getEntitiesOfClass(OverheadEntity.class, new net.minecraft.world.phys.AABB(BlockPos.containing(c.getSource().getPosition())).inflate(96));
            oh = list.isEmpty() ? null : list.get(0);
        }
        if (oh == null) {
            c.getSource().sendFailure(Component.literal("no Overhead nearby"));
            return 0;
        }
        oh.force(a);
        OverheadEntity f = oh;
        c.getSource().sendSuccess(() -> Component.literal("forcing " + a.id() + ": " + f.debugState()), false);
        return 1;
    }

    private interface YardFn { String run(Yard y); }

    private static int withYard(CommandContext<CommandSourceStack> c, YardFn fn) {
        Yard y = OverheadYards.get(c.getSource().getLevel()).nearest(BlockPos.containing(c.getSource().getPosition()), 128);
        if (y == null) {
            c.getSource().sendFailure(Component.literal("No Teknari yard within 128 blocks. Use /skyloreoverhead build"));
            return 0;
        }
        String msg = fn.run(y);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
