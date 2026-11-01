package com.example.qte;

import com.example.qte.net.Net;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(QteMod.MODID)
public class QteMod {
    public static final String MODID = "qte";
    public static final Logger LOGGER = LogUtils.getLogger();

    public QteMod() {
        Net.register();
        ModSounds.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
