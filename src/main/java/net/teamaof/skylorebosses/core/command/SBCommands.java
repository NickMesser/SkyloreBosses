package net.teamaof.skylorebosses.core.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.teamaof.skylorebosses.core.BossModule;
import net.teamaof.skylorebosses.core.BossModules;

/** {@code /skylorebosses list} and {@code /skylorebosses <boss> ...} (op level 2), plus per-boss aliases. */
public final class SBCommands {
    private SBCommands() {}

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, ctx, sel) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("skylorebosses").requires(s -> s.hasPermission(2));
            root.then(Commands.literal("list").executes(c -> {
                StringBuilder sb = new StringBuilder("Skylore bosses:");
                for (BossModule m : BossModules.all()) sb.append(' ').append(m.id());
                c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                return BossModules.all().size();
            }));
            for (BossModule m : BossModules.all()) {
                LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(m.id());
                m.buildCommands(node);
                root.then(node);
                for (String alias : m.commandAliases()) {
                    LiteralArgumentBuilder<CommandSourceStack> a = Commands.literal(alias).requires(s -> s.hasPermission(2));
                    m.buildCommands(a);
                    dispatcher.register(a);
                }
            }
            dispatcher.register(root);
        });
    }
}
