package net.teamaof.skylorebosses.bosses.staticdeacon.command;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Crypt;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.CryptLayout;
import net.teamaof.skylorebosses.bosses.staticdeacon.encounter.Crypts;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.DeaconAction;
import net.teamaof.skylorebosses.bosses.staticdeacon.entity.StaticDeaconEntity;

/**
 * Designer commands (permission 2), as {@code /skylorebosses static_deacon ...} or the {@code /skyloredeacon} alias:
 * <pre>
 * build [pos]            place the test undercroft at pos (default: 30 blocks above you) and register it
 * register [pos]         adopt an existing crypt (structure) at pos without placing blocks
 * unregister             forget the nearest crypt
 * start | reset          start the fight / reset the nearest crypt to dormant
 * skipphase              advance one beat (P0 -> P1, strip below 70%, strip all, end the vigil clock, finish the rite)
 * setfloor &lt;0..48&gt;      force the live flagstone count and re-evaluate the phase
 * strip                  desecrate the live flagstone nearest to you
 * breakplinthlock        break the Deacon's plinth hold (vigil, or the rite while channelling) as if the burst landed
 * reseed                 start the P4 reseed rite now
 * attack &lt;id&gt;           force an attack now (keeps its telegraph)
 * status | tp            describe / teleport to the entry pad
 * </pre>
 */
public final class DeaconCommands {
    private DeaconCommands() {}

    public static void build(LiteralArgumentBuilder<CommandSourceStack> node) {
        node
                .then(Commands.literal("build")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).offset(0, 30, 0), true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), true))))
                .then(Commands.literal("register")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).below(), false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), false))))
                .then(Commands.literal("unregister").executes(c -> withCrypt(c, y -> {
                    Crypts.get(c.getSource().getLevel()).remove(c.getSource().getLevel(), y.origin);
                    return "unregistered " + y.origin.toShortString();
                })))
                .then(Commands.literal("start").executes(c -> withCrypt(c, y -> {
                    boolean ok = y.tryStart(c.getSource().getLevel(), c.getSource().getPlayer());
                    return ok ? "started: " + y.describe(c.getSource().getLevel()) : "not dormant (or vetoed): " + y.phase().id();
                })))
                .then(Commands.literal("reset").executes(c -> withCrypt(c, y -> {
                    y.reset(c.getSource().getLevel(), "command");
                    return "reset: " + y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("skipphase").executes(c -> withCrypt(c, y -> {
                    y.skipPhase(c.getSource().getLevel());
                    return "skipped: " + y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("setfloor").then(Commands.argument("count", IntegerArgumentType.integer(0, 200)).executes(c -> withCrypt(c, y -> {
                    y.setFloor(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "count"));
                    return y.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("strip").executes(c -> withCrypt(c, y -> {
                    int i = y.strip(c.getSource().getLevel(), c.getSource().getPosition(), c.getSource().getPlayer());
                    return (i < 0 ? "no live flagstone" : "desecrated flagstone " + i) + ": " + y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("breakplinthlock").executes(c -> withCrypt(c, y -> {
                    StaticDeaconEntity d = y.deacon(c.getSource().getLevel());
                    if (d == null) return "no Deacon";
                    d.breakVigil(c.getSource().getPlayer(), d.channeling());
                    return y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("reseed").executes(c -> withCrypt(c, y -> {
                    boolean ok = y.forceReseed(c.getSource().getLevel());
                    return (ok ? "reseed rite started: " : "cannot reseed now: ") + y.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("attack").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DeaconAction.values()).map(DeaconAction::id), b))
                        .executes(DeaconCommands::attack)))
                .then(Commands.literal("status").executes(c -> withCrypt(c, y -> y.describe(c.getSource().getLevel()))))
                .then(Commands.literal("tp").executes(c -> withCrypt(c, y -> {
                    try {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Vec3 a = Vec3.atBottomCenterOf(CryptLayout.entryPad(y.origin));
                        p.teleportTo(c.getSource().getLevel(), a.x, a.y, a.z, 180, 0);
                        return "teleported to " + y.origin.toShortString();
                    } catch (CommandSyntaxException e) {
                        return "players only";
                    }
                })));
    }

    private static int create(CommandContext<CommandSourceStack> c, BlockPos pos, boolean build) {
        ServerLevel level = c.getSource().getLevel();
        Crypts.get(level).create(level, pos, build);
        c.getSource().sendSuccess(() -> Component.literal((build ? "Built" : "Registered") + " undercroft at " + pos.toShortString()
                + ". Walk in (or ring the bell at " + CryptLayout.bell(pos).toShortString() + ") to start."), true);
        return 1;
    }

    private static int attack(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        DeaconAction a;
        try {
            a = DeaconAction.byId(id);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("unknown attack " + id));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        Crypt y = find(c);
        StaticDeaconEntity d = y == null ? null : y.deacon(level);
        if (d == null) {
            var list = level.getEntitiesOfClass(StaticDeaconEntity.class, new AABB(BlockPos.containing(c.getSource().getPosition())).inflate(64));
            d = list.isEmpty() ? null : list.get(0);
        }
        if (d == null) {
            c.getSource().sendFailure(Component.literal("no Static Deacon nearby"));
            return 0;
        }
        d.force(a);
        StaticDeaconEntity f = d;
        c.getSource().sendSuccess(() -> Component.literal("forcing " + a.id() + ": " + f.debugState()), false);
        return 1;
    }

    private interface CryptFn { String run(Crypt y); }

    /** The crypt nearest the command source; if none is within 128 blocks, the nearest one in the dimension. */
    private static Crypt find(CommandContext<CommandSourceStack> c) {
        Crypts all = Crypts.get(c.getSource().getLevel());
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        Crypt y = all.nearest(at, 128);
        return y != null ? y : all.nearest(at, 3.0e7);
    }

    private static int withCrypt(CommandContext<CommandSourceStack> c, CryptFn fn) {
        Crypt y = find(c);
        if (y == null) {
            c.getSource().sendFailure(Component.literal("No undercroft in this dimension. Use /skyloredeacon build"));
            return 0;
        }
        String msg = fn.run(y);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
