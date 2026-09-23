package net.teamaof.skylorebosses.bosses.matriscalyx.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;
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
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.ArenaLayout;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.BodyAttacks;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.MatrisCommonEvents;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.Infection;
import net.teamaof.skylorebosses.bosses.matriscalyx.encounter.MatrisEncounter;
import net.teamaof.skylorebosses.bosses.matriscalyx.entity.arm.AbstractRootedArm;

/**
 * Designer commands (permission 2):
 * /skylorebosses matris_calyx (alias /skylorecalyx): start [pos] [nodome] | reset | skipphase | status | tp | attack &lt;kind&gt; | windows | infection &lt;players&gt; &lt;value&gt;
 */
public final class MatrisCommands {
    private MatrisCommands() {}

    /** Adds the Matris subcommands to {@code node} ({@code /skylorebosses matris_calyx} or the {@code /skylorecalyx} alias). */
    public static void build(LiteralArgumentBuilder<CommandSourceStack> node) {
        node
                .then(Commands.literal("start")
                        .executes(c -> start(c, BlockPos.containing(c.getSource().getPosition()).offset(0, 30, 0), true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> start(c, BlockPosArgument.getLoadedBlockPos(c, "pos"), true))
                                .then(Commands.literal("nodome").executes(c -> start(c, BlockPosArgument.getLoadedBlockPos(c, "pos"), false)))))
                .then(Commands.literal("reset").executes(c -> withEnc(c, e -> {
                    e.reset(c.getSource().getLevel(), "command");
                    return "reset: " + e.describe();
                })))
                .then(Commands.literal("skipphase").executes(c -> withEnc(c, e -> {
                    e.skipPhase(c.getSource().getLevel());
                    return "skipped: " + e.describe();
                })))
                .then(Commands.literal("status").executes(c -> withEnc(c, e -> {
                    StringBuilder sb = new StringBuilder(e.describe());
                    ServerLevel l = c.getSource().getLevel();
                    for (AbstractRootedArm a : l.getEntitiesOfClass(AbstractRootedArm.class, new AABB(e.origin()).inflate(300)))
                        sb.append(System.lineSeparator()).append("  ").append(a.debugState());
                    return sb.toString();
                })))
                .then(Commands.literal("windows").executes(c -> {
                    ServerLevel l = c.getSource().getLevel();
                    var arms = l.getEntitiesOfClass(AbstractRootedArm.class, new AABB(BlockPos.containing(c.getSource().getPosition())).inflate(400));
                    arms.forEach(a -> a.openWindowSoon(1));
                    c.getSource().sendSuccess(() -> Component.literal("Opening windows on " + arms.size() + " arms"), true);
                    return arms.size();
                }))
                .then(Commands.literal("attack").then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BodyAttacks.Kind.values()).map(BodyAttacks.Kind::id), b))
                        .executes(c -> withEnc(c, e -> {
                            String k = StringArgumentType.getString(c, "kind");
                            BodyAttacks.Kind kind = BodyAttacks.Kind.valueOf(k.toUpperCase(java.util.Locale.ROOT));
                            e.attacks().force(kind);
                            return "forcing body attack " + kind.id();
                        }))))
                .then(Commands.literal("tp").executes(MatrisCommands::tp))
                .then(Commands.literal("infection").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                            Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "players");
                            int v = IntegerArgumentType.getInteger(c, "value");
                            ps.forEach(p -> Infection.set(p, v));
                            c.getSource().sendSuccess(() -> Component.literal("infection = " + v + " for " + ps.size()), true);
                            return ps.size();
                        }))));
    }

    private static int start(CommandContext<CommandSourceStack> c, BlockPos pos, boolean dome) {
        MatrisEncounter e = MatrisEncounter.start(c.getSource().getLevel(), pos, true, dome);
        c.getSource().sendSuccess(() -> Component.literal("Matris Calyx arena building at " + pos.toShortString()
                + (dome ? "" : " (no dome)") + " - the fight starts when it is built. Anchor: "
                + ArenaLayout.anchor(pos).toShortString()), true);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel proto = p.server.getLevel(MatrisCommonEvents.PROTO_WORLD);
        if (proto == null) {
            c.getSource().sendFailure(Component.literal("Proto-World dimension missing (datapack disabled?)"));
            return 0;
        }
        MatrisEncounter e = MatrisEncounter.get(proto);
        if (e == null) e = MatrisEncounter.start(proto, MatrisCommonEvents.PROTO_ORIGIN, false, true);
        e.finishBuild(proto);
        // arrive on the anchor platform: stepping onto it wakes her
        Vec3 a = Vec3.atBottomCenterOf(ArenaLayout.anchor(MatrisCommonEvents.PROTO_ORIGIN)).add(0, 0.2, -2);
        p.teleportTo(proto, a.x, a.y, a.z, 180, 0);
        return 1;
    }

    private interface EncFn { String run(MatrisEncounter e); }

    private static int withEnc(CommandContext<CommandSourceStack> c, EncFn fn) {
        MatrisEncounter e = MatrisEncounter.get(c.getSource().getLevel());
        if (e == null) {
            c.getSource().sendFailure(Component.literal("No Matris encounter in this dimension. Use /skylorebosses matris_calyx start"));
            return 0;
        }
        String msg = fn.run(e);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
