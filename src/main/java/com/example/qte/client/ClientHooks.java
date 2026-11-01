package com.example.qte.client;

import com.example.qte.QteSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/** Точки входа из сетевых пакетов (только клиент). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void onPresets(boolean open, List<CompoundTag> tags) {
        List<QteSettings> list = new ArrayList<>();
        for (CompoundTag t : tags) list.add(QteSettings.load(t));
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            mc.setScreen(new QteEditorScreen(list));
        } else if (mc.screen instanceof QteEditorScreen editor) {
            editor.refresh(list);
        }
    }

    public static void onStart(int id, QteSettings s) {
        QteClient.start(id, s);
    }

    public static void onStop() {
        QteClient.current = null;
    }
}
