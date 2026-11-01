package com.example.qte.net;

import com.example.qte.QteMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(QteMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int i = 0;
        CHANNEL.registerMessage(i++, Packets.Presets.class, Packets.Presets::encode, Packets.Presets::decode, Packets.Presets::handle);
        CHANNEL.registerMessage(i++, Packets.Start.class, Packets.Start::encode, Packets.Start::decode, Packets.Start::handle);
        CHANNEL.registerMessage(i++, Packets.Stop.class, Packets.Stop::encode, Packets.Stop::decode, Packets.Stop::handle);
        CHANNEL.registerMessage(i++, Packets.SavePreset.class, Packets.SavePreset::encode, Packets.SavePreset::decode, Packets.SavePreset::handle);
        CHANNEL.registerMessage(i++, Packets.DeletePreset.class, Packets.DeletePreset::encode, Packets.DeletePreset::decode, Packets.DeletePreset::handle);
        CHANNEL.registerMessage(i++, Packets.RunPreset.class, Packets.RunPreset::encode, Packets.RunPreset::decode, Packets.RunPreset::handle);
        CHANNEL.registerMessage(i++, Packets.Result.class, Packets.Result::encode, Packets.Result::decode, Packets.Result::handle);
    }
}
