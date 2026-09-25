package net.teamaof.skylorebosses.bosses.nullrouter.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vault;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.VaultLayout;
import net.teamaof.skylorebosses.bosses.nullrouter.encounter.Vaults;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.NullRouterEntity;
import net.teamaof.skylorebosses.bosses.nullrouter.entity.RouterAction;

/**
 * Designer commands (permission 2), as {@code /skylorebosses null_router ...} or the {@code /skylorenullrouter} alias:
 * <pre>
 * build [pos]                 place the test vault at pos (default: 30 blocks above you) and register it
 * register [pos]              adopt an existing vault (structure) at pos without placing blocks
 * unregister                  forget the nearest vault
 * start | reset               start the fight / reset the nearest vault to dormant
 * skipphase                   advance one beat (P0 -> P1, queue to the P2 / P3 thresholds, P3 -> P4, P4 -> back by queue)
 * setqueue &lt;n&gt;              set the unacked queue depth (0 wins)
 * forceack                    open an ACK window now (consoles set to the head request)
 * solve | decoy               set the consoles to the head (or decoy) pattern as if you had, so it arms and commits
 * setconsole &lt;ch&gt; &lt;glyph&gt;   set channel 0..2 (A, B, C) to glyph 0..2 (circle, triangle, square) as a player flip
 * misroute [player]           misroute a player (default: you)
 * setwet &lt;ticks&gt;             make the chassis wet (0 dries it)
 * spawnretries &lt;n&gt;           spawn n retry packets at the wall ports (ignores the cap)
 * coolant &lt;on|off&gt;           open or close the coolant vents
 * attack &lt;id&gt;                force a chassis action now (keeps its telegraph; ghost only)
 * hold &lt;on|off&gt;              freeze the attack bag and the pressure clocks (stall, rotation, storm, P4); forced actions still run
 * status | tp                 describe / teleport to the entry pad
 * </pre>
 */
public final class RouterCommands {
    private RouterCommands() {}

    public static void build(LiteralArgumentBuilder<CommandSourceStack> node) {
        node
                .then(Commands.literal("build")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).offset(0, 30, 0), true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), true))))
                .then(Commands.literal("register")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).below(), false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), false))))
                .then(Commands.literal("unregister").executes(c -> withVault(c, v -> {
                    Vaults.get(c.getSource().getLevel()).remove(c.getSource().getLevel(), v.origin);
                    return "unregistered " + v.origin.toShortString();
                })))
                .then(Commands.literal("start").executes(c -> withVault(c, v -> {
                    boolean ok = v.tryStart(c.getSource().getLevel(), c.getSource().getPlayer());
                    return ok ? "started: " + v.describe(c.getSource().getLevel()) : "not dormant (or vetoed): " + v.phase().id();
                })))
                .then(Commands.literal("reset").executes(c -> withVault(c, v -> {
                    v.reset(c.getSource().getLevel(), "command");
                    return "reset: " + v.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("skipphase").executes(c -> withVault(c, v -> {
                    v.skipPhase(c.getSource().getLevel());
                    return "skipped: " + v.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("setqueue").then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(c -> withVault(c, v -> {
                    v.setQueue(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "n"));
                    return v.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("forceack").executes(c -> withVault(c, v -> {
                    boolean ok = v.forceAck(c.getSource().getLevel(), c.getSource().getPlayer());
                    return (ok ? "ACK opened: " : "cannot open an ACK now: ") + v.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("solve").executes(c -> withVault(c, v -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    boolean ok = p != null && v.solve(c.getSource().getLevel(), p);
                    return (ok ? "consoles set to the head: " : "cannot solve now: ") + v.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("decoy").executes(c -> withVault(c, v -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    boolean ok = p != null && v.solveDecoy(c.getSource().getLevel(), p);
                    return (ok ? "consoles set to the decoy: " : "no decoy now: ") + v.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("setconsole").then(Commands.argument("channel", IntegerArgumentType.integer(0, 2))
                        .then(Commands.argument("glyph", IntegerArgumentType.integer(0, 2)).executes(c -> withVault(c, v -> {
                            v.setConsole(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "channel"), IntegerArgumentType.getInteger(c, "glyph"),
                                    c.getSource().getPlayer());
                            return v.describe(c.getSource().getLevel());
                        })))))
                .then(Commands.literal("misroute")
                        .executes(c -> withVault(c, v -> {
                            ServerPlayer p = c.getSource().getPlayer();
                            if (p == null) return "players only";
                            v.forceMisroute(c.getSource().getLevel(), p);
                            return v.describe(c.getSource().getLevel());
                        }))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            return withVault(c, v -> {
                                v.forceMisroute(c.getSource().getLevel(), p);
                                return v.describe(c.getSource().getLevel());
                            });
                        })))
                .then(Commands.literal("setwet").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 12000)).executes(c -> withVault(c, v -> {
                    v.setWet(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "ticks"));
                    return v.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("spawnretries").then(Commands.argument("n", IntegerArgumentType.integer(1, 32)).executes(c -> withVault(c, v -> {
                    int n = v.spawnRetries(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "n"));
                    return "spawned " + n + ": " + v.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("coolant").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> withVault(c, v -> {
                    v.coolant(c.getSource().getLevel(), BoolArgumentType.getBool(c, "on"));
                    return "coolant " + BoolArgumentType.getBool(c, "on");
                }))))
                .then(Commands.literal("hold").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> withVault(c, v -> {
                    v.setHeld(BoolArgumentType.getBool(c, "on"));
                    return "hold " + v.held();
                }))))
                .then(Commands.literal("attack").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RouterAction.values()).map(RouterAction::id), b))
                        .executes(RouterCommands::attack)))
                .then(Commands.literal("status").executes(c -> withVault(c, v -> v.describe(c.getSource().getLevel()))))
                .then(Commands.literal("tp").executes(c -> withVault(c, v -> {
                    try {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Vec3 a = Vec3.atBottomCenterOf(VaultLayout.entryPad(v.origin));
                        p.teleportTo(c.getSource().getLevel(), a.x, a.y, a.z, 180, 0);
                        return "teleported to " + v.origin.toShortString();
                    } catch (CommandSyntaxException e) {
                        return "players only";
                    }
                })));
    }

    private static int create(CommandContext<CommandSourceStack> c, BlockPos pos, boolean build) {
        ServerLevel level = c.getSource().getLevel();
        Vaults.get(level).create(level, pos, build);
        c.getSource().sendSuccess(() -> Component.literal((build ? "Built" : "Registered") + " Automaton vault at " + pos.toShortString()
                + ". Walk in (or use the service terminal at " + VaultLayout.terminal(pos).toShortString() + ") to start."), true);
        return 1;
    }

    private static int attack(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        RouterAction a;
        try {
            a = RouterAction.byId(id);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("unknown action " + id));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        Vault v = find(c);
        NullRouterEntity r = v == null ? null : v.chassis(level);
        if (r == null) {
            var list = level.getEntitiesOfClass(NullRouterEntity.class, new AABB(BlockPos.containing(c.getSource().getPosition())).inflate(64));
            r = list.isEmpty() ? null : list.get(0);
        }
        if (r == null) {
            c.getSource().sendFailure(Component.literal("no Null Router nearby"));
            return 0;
        }
        boolean ok = a == RouterAction.MISROUTE_PULSE ? r.script(a, c.getSource().getPlayer()) : r.force(a);
        NullRouterEntity f = r;
        c.getSource().sendSuccess(() -> Component.literal((ok ? "forcing " : "refused (not a ghost) ") + a.id() + ": " + f.debugState()), false);
        return ok ? 1 : 0;
    }

    private interface VaultFn { String run(Vault v); }

    /** The vault nearest the command source; if none is within 128 blocks, the nearest one in the dimension. */
    private static Vault find(CommandContext<CommandSourceStack> c) {
        Vaults all = Vaults.get(c.getSource().getLevel());
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        Vault v = all.nearest(at, 128);
        return v != null ? v : all.nearest(at, 3.0e7);
    }

    private static int withVault(CommandContext<CommandSourceStack> c, VaultFn fn) {
        Vault v = find(c);
        if (v == null) {
            c.getSource().sendFailure(Component.literal("No Automaton vault in this dimension. Use /skylorenullrouter build"));
            return 0;
        }
        String msg = fn.run(v);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
