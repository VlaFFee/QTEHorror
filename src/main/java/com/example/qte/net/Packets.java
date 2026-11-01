package com.example.qte.net;

import com.example.qte.QteSettings;
import com.example.qte.client.ClientHooks;
import com.example.qte.server.QteServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class Packets {
    private Packets() {}

    // ------------------------------------------------------ сервер → клиент

    /** Список пресетов; open = true открывает окно редактора. */
    public static final class Presets {
        public final boolean open;
        public final List<CompoundTag> list;

        public Presets(boolean open, List<CompoundTag> list) {
            this.open = open;
            this.list = list;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBoolean(open);
            b.writeVarInt(list.size());
            for (CompoundTag t : list) b.writeNbt(t);
        }

        public static Presets decode(FriendlyByteBuf b) {
            boolean open = b.readBoolean();
            int n = Math.min(b.readVarInt(), 128);
            List<CompoundTag> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(b.readNbt());
            return new Presets(open, l);
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.onPresets(open, list)));
            c.setPacketHandled(true);
        }
    }

    /** Запустить QTE у игрока. */
    public static final class Start {
        public final int id;
        public final CompoundTag settings;

        public Start(int id, CompoundTag settings) {
            this.id = id;
            this.settings = settings;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeNbt(settings);
        }

        public static Start decode(FriendlyByteBuf b) {
            return new Start(b.readVarInt(), b.readNbt());
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientHooks.onStart(id, QteSettings.load(settings))));
            c.setPacketHandled(true);
        }
    }

    /** Прервать QTE без результата. */
    public static final class Stop {
        public Stop() {}

        public void encode(FriendlyByteBuf b) {}

        public static Stop decode(FriendlyByteBuf b) {
            return new Stop();
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::onStop));
            c.setPacketHandled(true);
        }
    }

    // ------------------------------------------------------ клиент → сервер

    public static final class SavePreset {
        public final CompoundTag settings;

        public SavePreset(CompoundTag settings) {
            this.settings = settings;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeNbt(settings);
        }

        public static SavePreset decode(FriendlyByteBuf b) {
            return new SavePreset(b.readNbt());
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> QteServer.onSave(c.getSender(), settings));
            c.setPacketHandled(true);
        }
    }

    public static final class DeletePreset {
        public final String name;

        public DeletePreset(String name) {
            this.name = name;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
        }

        public static DeletePreset decode(FriendlyByteBuf b) {
            return new DeletePreset(b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> QteServer.onDelete(c.getSender(), name));
            c.setPacketHandled(true);
        }
    }

    /** Оператор запускает пресет на самом себе из окна редактора. */
    public static final class RunPreset {
        public final String name;

        public RunPreset(String name) {
            this.name = name;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
        }

        public static RunPreset decode(FriendlyByteBuf b) {
            return new RunPreset(b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> QteServer.onRun(c.getSender(), name));
            c.setPacketHandled(true);
        }
    }

    public static final class Result {
        public final int id;
        public final boolean success;

        public Result(int id, boolean success) {
            this.id = id;
            this.success = success;
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeBoolean(success);
        }

        public static Result decode(FriendlyByteBuf b) {
            return new Result(b.readVarInt(), b.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context c = sup.get();
            c.enqueueWork(() -> QteServer.onResult(c.getSender(), id, success));
            c.setPacketHandled(true);
        }
    }
}
