package net.teamaof.skylorebosses.bosses.amanita.command;

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
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollow;
import net.teamaof.skylorebosses.bosses.amanita.encounter.HollowLayout;
import net.teamaof.skylorebosses.bosses.amanita.encounter.Hollows;
import net.teamaof.skylorebosses.bosses.amanita.entity.AmanitaAction;
import net.teamaof.skylorebosses.bosses.amanita.entity.AmanitaEntity;
import net.teamaof.skylorebosses.bosses.amanita.registry.AmanitaEntities;

/**
 * Designer commands (permission 2), as {@code /skylorebosses amanita ...} or the {@code /skyloreamanita} alias:
 * <pre>
 * build [pos]                 place the test hollow at pos (default: 30 blocks above you) and register it
 * register [pos]              adopt an existing hollow (structure) at pos without placing blocks
 * unregister                  forget the nearest hollow
 * start | reset               start the fight / reset the nearest hollow to dormant
 * skipphase                   advance one beat (P0 -> P1, P1 -> P2 at the snuff HP, P2 -> P3, P3 -> P4, P4 forced open)
 * snuff [radius]              snuff every light within radius of Amanita (no radius: the whole hollow) + Darkness
 * setlight &lt;-1..15&gt;          override the light the gate reads at Amanita (-1 = real light again)
 * spawnservants &lt;eater|spawn&gt; [n]   spawn lamp-eaters or hollow-spawn at the vents
 * attack &lt;id&gt;                force an attack now (keeps its telegraph)
 * hold &lt;true|false&gt;          freeze the attack bag, the phase clocks and the add clocks
 * lights | status | tp        list the light census / describe / teleport to the entry pad
 * </pre>
 */
public final class AmanitaCommands {
    private AmanitaCommands() {}

    public static void build(LiteralArgumentBuilder<CommandSourceStack> node) {
        node
                .then(Commands.literal("build")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).offset(0, 30, 0), true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), true))))
                .then(Commands.literal("register")
                        .executes(c -> create(c, BlockPos.containing(c.getSource().getPosition()).below(), false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> create(c, BlockPosArgument.getBlockPos(c, "pos"), false))))
                .then(Commands.literal("unregister").executes(c -> withHollow(c, h -> {
                    Hollows.get(c.getSource().getLevel()).remove(c.getSource().getLevel(), h.origin);
                    return "unregistered " + h.origin.toShortString();
                })))
                .then(Commands.literal("start").executes(c -> withHollow(c, h -> {
                    boolean ok = h.tryStart(c.getSource().getLevel(), c.getSource().getPlayer());
                    return ok ? "started: " + h.describe(c.getSource().getLevel()) : "not dormant (or vetoed): " + h.phase().id();
                })))
                .then(Commands.literal("reset").executes(c -> withHollow(c, h -> {
                    h.reset(c.getSource().getLevel(), "command");
                    return "reset: " + h.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("skipphase").executes(c -> withHollow(c, h -> {
                    h.skipPhase(c.getSource().getLevel());
                    return "skipped: " + h.describe(c.getSource().getLevel());
                })))
                .then(Commands.literal("snuff")
                        .executes(c -> withHollow(c, h -> "snuffed " + h.forceSnuff(c.getSource().getLevel(), -1) + " lights"))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 64)).executes(c -> withHollow(c, h ->
                                "snuffed " + h.forceSnuff(c.getSource().getLevel(), IntegerArgumentType.getInteger(c, "radius")) + " lights"))))
                .then(Commands.literal("setlight").then(Commands.argument("light", IntegerArgumentType.integer(-1, 15)).executes(c -> withHollow(c, h -> {
                    h.setLightOverride(IntegerArgumentType.getInteger(c, "light"));
                    return h.describe(c.getSource().getLevel());
                }))))
                .then(Commands.literal("spawnservants").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"eater", "spawn"}, b))
                        .executes(c -> servants(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 16)).executes(c -> servants(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("attack").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AmanitaAction.values()).map(AmanitaAction::id), b))
                        .executes(AmanitaCommands::attack)))
                .then(Commands.literal("hold").then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> withHollow(c, h -> {
                    h.setHold(BoolArgumentType.getBool(c, "on"));
                    return "hold=" + h.held();
                }))))
                .then(Commands.literal("lights").executes(c -> withHollow(c, h -> h.describeLights(c.getSource().getLevel()))))
                .then(Commands.literal("status").executes(c -> withHollow(c, h -> h.describe(c.getSource().getLevel()))))
                .then(Commands.literal("tp").executes(c -> withHollow(c, h -> {
                    try {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Vec3 a = Vec3.atBottomCenterOf(HollowLayout.entryPad(h.origin));
                        p.teleportTo(c.getSource().getLevel(), a.x, a.y, a.z, 180, 0);
                        return "teleported to " + h.origin.toShortString();
                    } catch (CommandSyntaxException e) {
                        return "players only";
                    }
                })));
    }

    private static int create(CommandContext<CommandSourceStack> c, BlockPos pos, boolean build) {
        ServerLevel level = c.getSource().getLevel();
        Hollows.get(level).create(level, pos, build);
        c.getSource().sendSuccess(() -> Component.literal((build ? "Built" : "Registered") + " hollow at " + pos.toShortString()
                + ". Walk in (or rap the knocker at " + HollowLayout.knocker(pos).toShortString() + ") to start."), true);
        return 1;
    }

    private static int servants(CommandContext<CommandSourceStack> c, int n) {
        String kind = StringArgumentType.getString(c, "kind");
        return withHollow(c, h -> {
            ServerLevel level = c.getSource().getLevel();
            for (int i = 0; i < n; i++) h.spawnAdd(level, kind.startsWith("e") ? AmanitaEntities.LAMP_EATER.get() : AmanitaEntities.HOLLOW_SPAWN.get(), i);
            return "spawned " + n + " " + (kind.startsWith("e") ? "lamp_eater" : "hollow_spawn");
        });
    }

    private static int attack(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        AmanitaAction a;
        try {
            a = AmanitaAction.byId(id);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("unknown attack " + id));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        Hollow h = find(c);
        AmanitaEntity d = h == null ? null : h.amanita(level);
        if (d == null) {
            var list = level.getEntitiesOfClass(AmanitaEntity.class, new AABB(BlockPos.containing(c.getSource().getPosition())).inflate(64));
            d = list.isEmpty() ? null : list.get(0);
        }
        if (d == null) {
            c.getSource().sendFailure(Component.literal("no Amanita nearby"));
            return 0;
        }
        d.force(a);
        AmanitaEntity f = d;
        c.getSource().sendSuccess(() -> Component.literal("forcing " + a.id() + ": " + f.debugState()), false);
        return 1;
    }

    private interface HollowFn { String run(Hollow h); }

    /** The hollow nearest the command source; if none is within 128 blocks, the nearest one in the dimension. */
    private static Hollow find(CommandContext<CommandSourceStack> c) {
        Hollows all = Hollows.get(c.getSource().getLevel());
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        Hollow h = all.nearest(at, 128);
        return h != null ? h : all.nearest(at, 3.0e7);
    }

    private static int withHollow(CommandContext<CommandSourceStack> c, HollowFn fn) {
        Hollow h = find(c);
        if (h == null) {
            c.getSource().sendFailure(Component.literal("No hollow in this dimension. Use /skyloreamanita build"));
            return 0;
        }
        String msg = fn.run(h);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
