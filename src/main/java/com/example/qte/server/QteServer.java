package com.example.qte.server;

import com.example.qte.QteData;
import com.example.qte.QteMod;
import com.example.qte.QteSettings;
import com.example.qte.net.Net;
import com.example.qte.net.Packets;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Серверная часть: хранит активные QTE, принимает результат, выполняет команды и тайм-ауты. */
public final class QteServer {
    private static final class Active {
        final int id;
        final QteSettings settings;
        final long startTick;
        /** QTE запущено принудительно (кнопка «Запустить» в редакторе или start ... force). */
        final boolean force;

        Active(int id, QteSettings settings, long startTick, boolean force) {
            this.id = id;
            this.settings = settings;
            this.startTick = startTick;
            this.force = force;
        }
    }

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static int nextId = 1;

    private QteServer() {}

    // ----------------------------------------------------------- исключения

    /** Игрок не должен видеть QTE: в списке игнора, наблюдатель или творческий режим (если включено). */
    public static boolean isExempt(ServerPlayer p, QteData d) {
        return d.isIgnored(p.getUUID())
                || (d.skipSpectator() && p.isSpectator())
                || (d.skipCreative() && p.isCreative());
    }

    // ------------------------------------------------------------- запуск

    /**
     * @return true, если QTE запущено; false, если игрок исключён (тогда никакие команды не выполняются,
     * игроку выдаётся тег qte_skipped).
     */
    public static boolean start(ServerPlayer p, QteSettings s, boolean force) {
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        if (!force && isExempt(p, QteData.get(server))) {
            p.removeTag("qte_success");
            p.removeTag("qte_fail");
            p.addTag("qte_skipped");
            return false;
        }
        p.removeTag("qte_skipped");
        s.sanitize();
        int id = nextId++;
        ACTIVE.put(p.getUUID(), new Active(id, s, server.overworld().getGameTime(), force));
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Packets.Start(id, s.save()));
        return true;
    }

    public static void stop(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID()) != null) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Packets.Stop());
        }
    }

    public static void forget(ServerPlayer p) {
        ACTIVE.remove(p.getUUID());
    }

    // ----------------------------------------------------------- результат

    public static void onResult(ServerPlayer p, int id, boolean success) {
        if (p == null || p.getServer() == null) return;
        Active a = ACTIVE.get(p.getUUID());
        if (a == null || a.id != id) return;
        // игрока успели исключить (игнор / смена режима) — результат не засчитываем, команд не будет
        if (!a.force && isExempt(p, QteData.get(p.getServer()))) {
            stop(p);
            return;
        }
        long elapsed = p.getServer().overworld().getGameTime() - a.startTick;
        finish(p, a, success && elapsed >= 2);
    }

    private static void finish(ServerPlayer p, Active a, boolean success) {
        ACTIVE.remove(p.getUUID());
        MinecraftServer server = p.getServer();
        if (server == null) return;
        p.removeTag("qte_success");
        p.removeTag("qte_fail");
        p.addTag(success ? "qte_success" : "qte_fail");
        String cmd = success ? a.settings.onSuccess : a.settings.onFail;
        if (cmd != null && !cmd.isBlank()) {
            try {
                CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, cmd.startsWith("/") ? cmd.substring(1) : cmd);
            } catch (Exception e) {
                QteMod.LOGGER.warn("[QTE] command failed: {}", cmd, e);
            }
        }
    }

    /** Раз в тик: отмена для исключённых, провал по тайм-ауту, если клиент не прислал результат. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = server.overworld().getGameTime();
        QteData data = QteData.get(server);
        List<UUID> ids = new ArrayList<>(ACTIVE.keySet());
        for (UUID id : ids) {
            Active a = ACTIVE.get(id);
            if (a == null) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                ACTIVE.remove(id);
                continue;
            }
            // создатель перешёл в spectator/creative или попал в игнор посреди QTE — тихо отменяем
            if (!a.force && isExempt(p, data)) {
                ACTIVE.remove(id);
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Packets.Stop());
                continue;
            }
            if (now - a.startTick > (long) ((a.settings.seconds + 4.0) * 20)) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Packets.Stop());
                finish(p, a, false);
            }
        }
    }

    // --------------------------------------------------------- редактор

    public static void sendPresets(ServerPlayer p, boolean open) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Packets.Presets(open, QteData.get(server).all()));
    }

    public static void onSave(ServerPlayer p, net.minecraft.nbt.CompoundTag tag) {
        if (p == null || !p.hasPermissions(2) || p.getServer() == null || tag == null) return;
        QteSettings s = QteSettings.load(tag);
        QteData.get(p.getServer()).put(s);
        sendPresets(p, false);
    }

    public static void onDelete(ServerPlayer p, String name) {
        if (p == null || !p.hasPermissions(2) || p.getServer() == null) return;
        QteData d = QteData.get(p.getServer());
        if (d.names().size() > 1) d.remove(QteSettings.cleanName(name));
        sendPresets(p, false);
    }

    /** Кнопка «Запустить» в редакторе: оператор осознанно запускает QTE у себя, даже в creative/spectator. */
    public static void onRun(ServerPlayer p, String name) {
        if (p == null || !p.hasPermissions(2) || p.getServer() == null) return;
        QteSettings s = QteData.get(p.getServer()).get(QteSettings.cleanName(name));
        if (s != null) start(p, s, true);
    }
}
