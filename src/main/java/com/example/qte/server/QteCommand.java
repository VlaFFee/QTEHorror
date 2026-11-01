package com.example.qte.server;

import com.example.qte.QteData;
import com.example.qte.QteSettings;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * /qte start &lt;игроки&gt; &lt;пресет&gt; [force]
 * /qte quick &lt;игроки&gt; &lt;нажатий&gt; &lt;секунд&gt;
 * /qte stop &lt;игроки&gt;
 * /qte ignore add|remove &lt;игроки&gt; | list | clear
 * /qte skip [spectator|creative &lt;true|false&gt;]
 * /qte gui | list | delete &lt;пресет&gt;
 */
public final class QteCommand {
    private QteCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("qte")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                QteData.get(c.getSource().getServer()).names(), b))
                                        .executes(c -> start(c, false))
                                        .then(Commands.literal("force").executes(c -> start(c, true))))))
                .then(Commands.literal("quick")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("presses", IntegerArgumentType.integer(1, 200))
                                        .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.5, 120))
                                                .executes(QteCommand::quick)))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(QteCommand::stop)))
                .then(Commands.literal("ignore")
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> ignore(c, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> ignore(c, false))))
                        .then(Commands.literal("list").executes(QteCommand::ignoreList))
                        .then(Commands.literal("clear").executes(QteCommand::ignoreClear)))
                .then(Commands.literal("skip")
                        .executes(QteCommand::skipStatus)
                        .then(Commands.literal("spectator")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(c -> skip(c, true))))
                        .then(Commands.literal("creative")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(c -> skip(c, false)))))
                .then(Commands.literal("gui").executes(QteCommand::gui))
                .then(Commands.literal("list").executes(QteCommand::list))
                .then(Commands.literal("delete")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        QteData.get(c.getSource().getServer()).names(), b))
                                .executes(QteCommand::delete))));
    }

    // ------------------------------------------------------------- запуск

    /** Запускает QTE и сообщает, сколько игроков запущено и сколько пропущено. */
    private static int launch(CommandSourceStack src, String label, Collection<ServerPlayer> targets,
                              QteSettings base, boolean force) {
        int started = 0;
        int skipped = 0;
        for (ServerPlayer p : targets) {
            if (QteServer.start(p, base.copy(), force)) started++;
            else skipped++;
        }
        final int st = started;
        final int sk = skipped;
        if (st > 0) src.sendSuccess(() -> Component.translatable("commands.qte.start", label, st), true);
        if (sk > 0) src.sendSuccess(() -> Component.translatable("commands.qte.skipped", sk), false);
        return st;
    }

    private static int start(CommandContext<CommandSourceStack> c, boolean force) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        String name = StringArgumentType.getString(c, "preset");
        QteSettings base = QteData.get(src.getServer()).get(name);
        if (base == null) {
            src.sendFailure(Component.translatable("commands.qte.unknown", name));
            return 0;
        }
        return launch(src, name, EntityArgument.getPlayers(c, "targets"), base, force);
    }

    private static int quick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        QteSettings s = QteData.get(src.getServer()).get("default");
        if (s == null) s = new QteSettings();
        s.mode = QteSettings.Mode.MASH;
        s.count = IntegerArgumentType.getInteger(c, "presses");
        s.seconds = DoubleArgumentType.getDouble(c, "seconds");
        return launch(src, "quick", EntityArgument.getPlayers(c, "targets"), s, false);
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) QteServer.stop(p);
        c.getSource().sendSuccess(() -> Component.translatable("commands.qte.stop", targets.size()), true);
        return targets.size();
    }

    // -------------------------------------------------------------- игнор

    private static int ignore(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        QteData data = QteData.get(src.getServer());
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        int changed = 0;
        for (ServerPlayer p : targets) {
            if (data.setIgnored(p.getUUID(), on)) changed++;
            if (on) {
                // если QTE уже идёт — убираем его сразу, без команд успеха/провала
                QteServer.stop(p);
            }
        }
        final int n = changed;
        src.sendSuccess(() -> Component.translatable(on ? "commands.qte.ignore.add" : "commands.qte.ignore.remove",
                n, targets.size()), true);
        return n;
    }

    private static int ignoreList(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        MinecraftServer server = src.getServer();
        List<String> names = new ArrayList<>();
        for (UUID id : QteData.get(server).ignoredList()) names.add(nameOf(server, id));
        if (names.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("commands.qte.ignore.empty"), false);
        } else {
            src.sendSuccess(() -> Component.translatable("commands.qte.ignore.list", names.size(), String.join(", ", names)), false);
        }
        return names.size();
    }

    private static int ignoreClear(CommandContext<CommandSourceStack> c) {
        int n = QteData.get(c.getSource().getServer()).clearIgnored();
        c.getSource().sendSuccess(() -> Component.translatable("commands.qte.ignore.cleared", n), true);
        return n;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        if (server.getProfileCache() == null) return id.toString();
        return server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString());
    }

    // ------------------------------------------------------------- пропуск

    private static int skip(CommandContext<CommandSourceStack> c, boolean spectator) {
        QteData data = QteData.get(c.getSource().getServer());
        boolean v = BoolArgumentType.getBool(c, "value");
        if (spectator) data.setSkipSpectator(v);
        else data.setSkipCreative(v);
        return skipStatus(c);
    }

    private static int skipStatus(CommandContext<CommandSourceStack> c) {
        QteData data = QteData.get(c.getSource().getServer());
        boolean sp = data.skipSpectator();
        boolean cr = data.skipCreative();
        c.getSource().sendSuccess(() -> Component.translatable("commands.qte.skip.status",
                Component.translatable(sp ? "commands.qte.on" : "commands.qte.off"),
                Component.translatable(cr ? "commands.qte.on" : "commands.qte.off")), false);
        return 1;
    }

    // ------------------------------------------------------------- прочее

    private static int gui(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        QteServer.sendPresets(c.getSource().getPlayerOrException(), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        var names = QteData.get(src.getServer()).names();
        src.sendSuccess(() -> Component.translatable("commands.qte.list", names.size(), String.join(", ", names)), false);
        return names.size();
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        String name = StringArgumentType.getString(c, "preset");
        QteData d = QteData.get(src.getServer());
        if (d.names().size() <= 1) {
            src.sendFailure(Component.translatable("commands.qte.last"));
            return 0;
        }
        if (!d.remove(name)) {
            src.sendFailure(Component.translatable("commands.qte.unknown", name));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("commands.qte.deleted", name), true);
        return 1;
    }
}
