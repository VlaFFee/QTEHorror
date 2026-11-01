package com.example.qte;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Сохранённые в мире пресеты QTE и список игроков, которые QTE не видят. */
public final class QteData extends SavedData {
    public static final String NAME = "qte_presets";
    public static final int MAX_PRESETS = 64;

    private final Map<String, QteSettings> presets = new LinkedHashMap<>();
    /** Игроки, которым QTE не показывается и не засчитывается (создатель карты, тестеры). */
    private final Set<UUID> ignored = new LinkedHashSet<>();
    /** Не запускать QTE у игроков в режиме наблюдателя. */
    private boolean skipSpectator = true;
    /** Не запускать QTE у игроков в творческом режиме. */
    private boolean skipCreative = true;

    public static QteData get(MinecraftServer server) {
        QteData d = server.overworld().getDataStorage().computeIfAbsent(QteData::load, QteData::new, NAME);
        if (d.presets.isEmpty()) {
            d.presets.put("default", new QteSettings());
            d.setDirty();
        }
        return d;
    }

    public static QteData load(CompoundTag tag) {
        QteData d = new QteData();
        ListTag list = tag.getList("Presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            QteSettings s = QteSettings.load(list.getCompound(i));
            d.presets.put(s.name, s);
        }
        ListTag ig = tag.getList("Ignored", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < ig.size(); i++) {
            d.ignored.add(NbtUtils.loadUUID(ig.get(i)));
        }
        d.skipSpectator = !tag.contains("SkipSpectator") || tag.getBoolean("SkipSpectator");
        d.skipCreative = !tag.contains("SkipCreative") || tag.getBoolean("SkipCreative");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (QteSettings s : presets.values()) list.add(s.save());
        tag.put("Presets", list);
        ListTag ig = new ListTag();
        for (UUID u : ignored) ig.add(NbtUtils.createUUID(u));
        tag.put("Ignored", ig);
        tag.putBoolean("SkipSpectator", skipSpectator);
        tag.putBoolean("SkipCreative", skipCreative);
        return tag;
    }

    // ----------------------------------------------------------- пресеты

    public QteSettings get(String name) {
        QteSettings s = presets.get(name);
        return s == null ? null : s.copy();
    }

    public List<String> names() {
        return new ArrayList<>(presets.keySet());
    }

    public List<CompoundTag> all() {
        List<CompoundTag> out = new ArrayList<>();
        for (QteSettings s : presets.values()) out.add(s.save());
        return out;
    }

    /** @return false, если достигнут лимит пресетов. */
    public boolean put(QteSettings s) {
        if (!presets.containsKey(s.name) && presets.size() >= MAX_PRESETS) return false;
        presets.put(s.name, s);
        setDirty();
        return true;
    }

    public boolean remove(String name) {
        if (presets.remove(name) == null) return false;
        setDirty();
        return true;
    }

    // ------------------------------------------------------------ игнор

    public boolean isIgnored(UUID id) {
        return ignored.contains(id);
    }

    /** @return true, если список изменился. */
    public boolean setIgnored(UUID id, boolean on) {
        boolean changed = on ? ignored.add(id) : ignored.remove(id);
        if (changed) setDirty();
        return changed;
    }

    public int clearIgnored() {
        int n = ignored.size();
        if (n > 0) {
            ignored.clear();
            setDirty();
        }
        return n;
    }

    public List<UUID> ignoredList() {
        return new ArrayList<>(ignored);
    }

    public boolean skipSpectator() {
        return skipSpectator;
    }

    public boolean skipCreative() {
        return skipCreative;
    }

    public void setSkipSpectator(boolean v) {
        skipSpectator = v;
        setDirty();
    }

    public void setSkipCreative(boolean v) {
        skipCreative = v;
        setDirty();
    }
}
