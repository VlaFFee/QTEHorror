package com.example.qte;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/** Звуки QTE: 8 событий в двух наборах (clean и dark). */
public final class ModSounds {
    public enum Kind { START, PRESS, HIT, MISS, SUCCESS, FAIL, TICK, HEART }

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, QteMod.MODID);
    private static final Map<QteSettings.Pack, Map<Kind, RegistryObject<SoundEvent>>> MAP = new EnumMap<>(QteSettings.Pack.class);

    static {
        for (QteSettings.Pack p : QteSettings.Pack.values()) {
            Map<Kind, RegistryObject<SoundEvent>> m = new EnumMap<>(Kind.class);
            for (Kind k : Kind.values()) {
                String name = k.name().toLowerCase() + "_" + p.name().toLowerCase();
                m.put(k, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(QteMod.MODID, name))));
            }
            MAP.put(p, m);
        }
    }

    private ModSounds() {}

    public static SoundEvent get(QteSettings.Pack pack, Kind kind) {
        return MAP.get(pack).get(kind).get();
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
