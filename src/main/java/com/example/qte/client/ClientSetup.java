package com.example.qte.client;

import com.example.qte.QteMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    private ClientSetup() {}

    @Mod.EventBusSubscriber(modid = QteMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("qte", (gui, g, pt, w, h) -> QteRender.render(g, w, h));
        }
    }

    @Mod.EventBusSubscriber(modid = QteMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        @SubscribeEvent
        public static void onTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            QteClient q = QteClient.current;
            if (q == null) return;
            // блокируем обычное управление: сбрасываем все нажатия клавиш игры, пока идёт QTE
            if (!q.finished && q.s.lockInput && Minecraft.getInstance().screen == null) {
                KeyMapping.releaseAll();
            }
            QteClient.tick();
        }

        @SubscribeEvent
        public static void onKey(InputEvent.Key e) {
            QteClient q = QteClient.current;
            if (q == null || q.finished || Minecraft.getInstance().screen != null) return;
            if (!q.matchesKey(e.getKey())) return;
            if (e.getAction() == GLFW.GLFW_PRESS) q.onDown();
            else if (e.getAction() == GLFW.GLFW_RELEASE) q.onUp();
        }

        @SubscribeEvent
        public static void onMouse(InputEvent.MouseButton.Pre e) {
            QteClient q = QteClient.current;
            if (q == null || q.finished || Minecraft.getInstance().screen != null) return;
            if (!q.matchesMouse(e.getButton())) return;
            if (e.getAction() == GLFW.GLFW_PRESS) q.onDown();
            else if (e.getAction() == GLFW.GLFW_RELEASE) q.onUp();
        }

        @SubscribeEvent
        public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
            QteClient q = QteClient.current;
            if (q == null) return;
            float[] sh = q.cameraShake(net.minecraft.Util.getMillis());
            if (sh == null) return;
            e.setRoll(e.getRoll() + sh[0]);
            e.setPitch(e.getPitch() + sh[1]);
            e.setYaw(e.getYaw() + sh[2]);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            QteClient.current = null;
        }
    }
}
