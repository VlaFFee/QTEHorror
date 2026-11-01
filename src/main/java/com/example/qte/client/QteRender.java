package com.example.qte.client;

import com.example.qte.QteSettings;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Отрисовка QTE в стиле Minecraft: всё собрано из пикселей и квадратов (как кольцо часов или полоска опыта),
 * без гладких кругов. Тёмная палитра, дрожь и сердцебиение при нехватке времени, кровь при провале.
 */
public final class QteRender {
    private static final int OUT_R = 34;     // радиус кольца времени
    private static final int OUT_N = 48;     // клеток в кольце времени
    private static final int DIAL_R = 21;    // радиус циферблата точного момента
    private static final int DIAL_N = 72;
    private static final int BAR_W = 100;
    private static final int BAR_H = 7;
    private static final int SUCCESS_RGB = 0x9FD18B;
    private static final int BONE = 0xF0E8D8;

    private QteRender() {}

    // ------------------------------------------------------------ утилиты

    private static int argb(int rgb, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static int lerpRgb(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Детерминированный «шум» 0..1 по целому числу: нужен для дрожи и мерцания без состояния. */
    static float hash(long n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float wrap180(float a) {
        a = a % 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }

    private static void cellAt(GuiGraphics g, float x, float y, int size, int argb) {
        if ((argb >>> 24) < 3) return;
        int x0 = Math.round(x - size / 2f);
        int y0 = Math.round(y - size / 2f);
        g.fill(x0, y0, x0 + size, y0 + size, argb);
    }

    /** Кольцо из квадратиков: i-я клетка из n, 0° — вверх, по часовой. */
    private static void cellRing(GuiGraphics g, int n, float r, int size, int rgb, float alpha) {
        if (alpha < 0.01f) return;
        for (int i = 0; i < n; i++) {
            float rad = (float) Math.toRadians(i * 360f / n);
            cellAt(g, (float) Math.sin(rad) * r, -(float) Math.cos(rad) * r, size, argb(rgb, alpha));
        }
    }

    /** Пиксельный «круг» из горизонтальных строк, как круглые предметы в Minecraft. */
    private static void pixelDisc(GuiGraphics g, int r, int argb) {
        if ((argb >>> 24) < 3) return;
        for (int dy = -r; dy < r; dy++) {
            float yy = dy + 0.5f;
            int hw = (int) Math.round(Math.sqrt(Math.max(0f, r * r - yy * yy)));
            g.fill(-hw, dy, hw, dy + 1, argb);
        }
    }

    private static void text(GuiGraphics g, Font f, String s, float x, float y, float scale, int argb) {
        if ((argb >>> 24) < 8 || s.isEmpty()) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(scale, scale, 1f);
        g.drawString(f, s, -f.width(s) / 2, 0, argb, true);
        ps.popPose();
    }

    /** Текст с «призраком»: при ghost > 0 рядом мелькают смещённые тёмно-красная и светлая копии. */
    private static void ghostText(GuiGraphics g, Font f, String s, float x, float y, float scale, int rgb, float a,
                                  float ghost) {
        if (ghost > 0.02f) {
            float d = 1f + ghost * 2.5f;
            text(g, f, s, x - d, y, scale, argb(0x8A0808, a * 0.75f));
            text(g, f, s, x + d, y + 1f, scale, argb(0xC8D8D8, a * 0.25f));
        }
        text(g, f, s, x, y, scale, argb(rgb, a));
    }

    /** Клавиша: чёрная обводка, светлые верх/лево, тёмные низ/право, как кнопка в интерфейсе. */
    private static void cap(GuiGraphics g, int h, int face, int hi, int sh, float a) {
        g.fill(-h - 1, -h - 1, h + 1, h + 1, argb(0x000000, a));
        g.fill(-h, -h, h, h, argb(face, a));
        g.fill(-h, -h, h, -h + 2, argb(hi, a));
        g.fill(-h, -h, -h + 2, h, argb(hi, a));
        g.fill(-h, h - 2, h, h, argb(sh, a));
        g.fill(h - 2, -h, h, h, argb(sh, a));
    }

    // ------------------------------------------------------------ экранный кадр

    public static void render(GuiGraphics g, int w, int h) {
        QteClient q = QteClient.current;
        if (q == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = Util.getMillis();
        q.update(now);

        QteSettings s = q.s;
        float endT = q.finished ? (now - q.endMs) / (float) QteClient.END_MS : 0f;
        if (endT >= 1f) return;
        float master = q.finished ? clamp01((1f - endT) / 0.3f) : 1f;

        float left = q.timeLeft(now);
        float frac = (float) (left / s.seconds);
        boolean low = !q.finished && left <= q.lowAt();
        float urg = frac < 0.35f ? 1f - frac / 0.35f : 0f;
        float beat = low ? clamp01(1f - (now - q.lastHeartMs) / 260f) : 0f;
        int dangerRgb = QteSettings.rgb(s.danger);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // виньетка: постоянная тёмная по краям + кровавая, которая бьётся в такт сердцу
        if (s.vignette) {
            float ramp = clamp01((now - q.startMs) / 700f) * (q.finished ? master : 1f);
            vignette(g, w, h, 0x000000, 0.34f * ramp, 0.30f);
            float danger = 0f;
            if (low) danger = (0.35f + 0.65f * urg) * (0.7f + 0.3f * beat * beat);
            if (q.finished && !q.success) danger = Math.max(danger, (1f - endT) * 0.95f);
            vignette(g, w, h, dangerRgb, danger * 0.65f, 0.38f);
        }

        float cx = w / 2f;
        float cy = switch (s.position) {
            case CENTER -> h / 2f;
            case LOWER -> h * 0.66f;
            case UPPER -> h * 0.25f;
            case BOTTOM -> h - 100f;
        };
        draw(g, q, cx, cy, now, false, -1f);

        RenderSystem.disableBlend();
    }

    /** Кадр живого предпросмотра в редакторе. */
    public static void renderDemo(GuiGraphics g, QteClient q, float cx, float cy, float scale) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        draw(g, q, cx, cy, Util.getMillis(), true, scale);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------ сам виджет

    private static void draw(GuiGraphics g, QteClient q, float cx, float cy, long now, boolean demo, float forcedScale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        QteSettings s = q.s;
        if (demo) q.demoStep(now);

        float endT = q.finished ? (now - q.endMs) / (float) QteClient.END_MS : 0f;
        float endSec = q.finished ? (now - q.endMs) / 1000f : 0f;
        float master = q.finished ? clamp01((1f - endT) / 0.3f) : 1f;

        int accentRgb = QteSettings.rgb(s.color);
        int dangerRgb = QteSettings.rgb(s.danger);
        float left = q.timeLeft(now);
        float frac = clamp01((float) (left / s.seconds));
        boolean low = !q.finished && left <= q.lowAt();
        float urg = frac < 0.35f ? 1f - frac / 0.35f : 0f;
        float beat = low ? clamp01(1f - (now - q.lastHeartMs) / 260f) : 0f;
        boolean failed = q.finished && !q.success;
        int mainRgb = q.finished ? (q.success ? SUCCESS_RGB : dangerRgb) : accentRgb;
        int ringRgb = q.finished ? mainRgb : lerpRgb(accentRgb, dangerRgb, urg);

        // ---- появление
        long age = q.age(now);
        float dur = switch (s.appear) {
            case GLITCH -> 450f;
            case CREEP -> 900f;
            default -> 380f;
        };
        float ap = clamp01(age / dur);
        float scaleF = 1f, alphaF = 1f, yOff = 0f, xOff = 0f, rot = 0f;
        switch (s.appear) {
            case POP -> {
                scaleF = Math.max(0.01f, easeOutBack(ap));
                alphaF = clamp01(ap * 4f);
            }
            case FADE -> {
                scaleF = 0.92f + 0.08f * ap;
                alphaF = ap;
            }
            case SLIDE -> {
                yOff = (1f - easeOut(ap)) * 70f;
                alphaF = ap;
            }
            case SPIN -> {
                rot = (1f - easeOut(ap)) * 270f;
                scaleF = Math.max(0.01f, easeOut(ap));
                alphaF = clamp01(ap * 3f);
            }
            case GLITCH -> {
                if (ap < 1f) {
                    alphaF = hash(now / 45L) < (1f - ap) * 0.85f ? 0.1f : 1f;
                    xOff = (hash(now / 30L + 11L) - 0.5f) * 26f * (1f - ap);
                }
            }
            case CREEP -> {
                alphaF = ap * ap;
                scaleF = 1.22f - 0.22f * easeOut(ap);
                yOff = -10f * (1f - ap);
            }
        }

        // ---- дрожь и мерцание при нехватке времени
        float flick = 1f, jx = 0f, jy = 0f;
        if (low) {
            flick = 0.78f + 0.22f * hash(now / 60L);
            float amp = 0.6f + 1.4f * urg;
            jx = (hash(now / 55L + 3L) - 0.5f) * 2f * amp;
            jy = (hash(now / 55L + 9L) - 0.5f) * 2f * amp;
        }
        if (s.pressFx == QteSettings.PressFx.JOLT) {
            float jl = clamp01(1f - (now - q.lastPressMs) / 140f);
            jx += (hash(now / 20L + 1L) - 0.5f) * 10f * jl;
            jy += (hash(now / 20L + 2L) - 0.5f) * 10f * jl;
        }
        if (failed && s.failFx == QteSettings.FailFx.SHAKE) {
            jx += (float) Math.sin(endSec * 70f) * 7f * (1f - endT);
        }

        float A = alphaF * master * flick;
        float pulse = s.pressFx == QteSettings.PressFx.PULSE ? clamp01(1f - (now - q.lastPressMs) / 170f) : 0f;
        float capPulse = clamp01(1f - (now - q.lastPressMs) / 130f);
        float flash = s.pressFx == QteSettings.PressFx.FLASH ? clamp01(1f - (now - q.lastPressMs) / 160f) : 0f;
        float missFlash = clamp01(1f - (now - q.lastMissMs) / 240f);
        float hitFlash = clamp01(1f - (now - q.lastHitMs) / 200f);
        float baseScale = forcedScale > 0f ? forcedScale : s.scale / 100f;
        float S = baseScale * scaleF * (1f + 0.12f * pulse + 0.05f * beat * beat);

        boolean shatter = failed && s.failFx == QteSettings.FailFx.SHATTER;
        boolean bloody = failed && s.failFx == QteSettings.FailFx.BLOOD;

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx + xOff + jx, cy + yOff + jy, 0f);
        if (rot != 0f) ps.mulPose(Axis.ZP.rotationDegrees(rot));
        ps.scale(S, S, 1f);

        // ---- подложка
        if (!shatter || endT < 0.1f) {
            float tint = urg * 0.30f + missFlash * 0.5f + (failed ? 0.3f : 0f);
            pixelDisc(g, OUT_R + 5, argb(lerpRgb(0x000000, dangerRgb, tint), 0.58f * A));
        }

        // ---- кольцо времени
        if (s.showTimer || q.finished) {
            float lit = clamp01(q.finished && q.success ? 1f : frac) * OUT_N;
            if (shatter) {
                for (int i = 0; i < OUT_N; i++) {
                    float cf = clamp01(lit - i);
                    if (cf <= 0f) continue;
                    float rad = (float) Math.toRadians(i * 360f / OUT_N);
                    float sn = (float) Math.sin(rad), cs = (float) Math.cos(rad);
                    float sp = 35f + hash(i + 100L) * 90f;
                    float ox = sn * sp * endSec + (hash(i + 200L) - 0.5f) * 30f * endSec;
                    float oy = -cs * sp * endSec * 0.6f + 160f * endSec * endSec;
                    float al = clamp01(1f - endSec / 0.9f);
                    cellAt(g, sn * OUT_R + ox, -cs * OUT_R + oy, 3, argb(ringRgb, al * A));
                }
            } else {
                for (int i = 0; i < OUT_N; i++) {
                    float rad = (float) Math.toRadians(i * 360f / OUT_N);
                    cellAt(g, (float) Math.sin(rad) * OUT_R, -(float) Math.cos(rad) * OUT_R, 5, argb(0x000000, 0.55f * A));
                }
                int head = (int) Math.ceil(lit) - 1;
                for (int i = 0; i < OUT_N; i++) {
                    float cf = clamp01(lit - i);
                    float rad = (float) Math.toRadians(i * 360f / OUT_N);
                    float x = (float) Math.sin(rad) * OUT_R, y = -(float) Math.cos(rad) * OUT_R;
                    if (cf <= 0f) {
                        cellAt(g, x, y, 3, argb(0x2B2424, 0.85f * A));
                        continue;
                    }
                    float whiten = flash * 0.8f + (i == head ? 0.35f : 0f);
                    if (q.finished && q.success) whiten += clamp01(1f - endSec / 0.4f);
                    cellAt(g, x, y, 3, argb(lerpRgb(ringRgb, 0xFFFFFF, whiten), (0.55f + 0.45f * cf) * A));
                }
            }
        }

        // ---- циферблат «точного момента»
        if (s.mode == QteSettings.Mode.TIMING && (!shatter || endT < 0.15f)) {
            float da = shatter ? 1f - endT / 0.15f : 1f;
            float zc = q.zoneCenter;
            float half = s.zoneDeg / 2f * clamp01((now - q.zoneMs) / 240f);
            int zoneRgb = lerpRgb(mainRgb, 0xFFFFFF, Math.max(hitFlash * 0.9f, flash * 0.6f));
            for (int i = 0; i < DIAL_N; i++) {
                float deg = i * 360f / DIAL_N;
                boolean in = Math.abs(wrap180(deg - zc)) <= half;
                float rad = (float) Math.toRadians(deg);
                cellAt(g, (float) Math.sin(rad) * DIAL_R, -(float) Math.cos(rad) * DIAL_R, 3,
                        in ? argb(zoneRgb, 0.95f * A * da) : argb(0x2E2727, 0.85f * A * da));
            }
            if (!q.finished) {
                float na = q.needleAngle(now);
                for (int k = 6; k >= 0; k--) {
                    float rad = (float) Math.toRadians(na - k * 6f);
                    int col = k == 0 ? 0xFFFFFF : lerpRgb(0xFFFFFF, dangerRgb, k / 6f);
                    cellAt(g, (float) Math.sin(rad) * DIAL_R, -(float) Math.cos(rad) * DIAL_R, k == 0 ? 5 : 3,
                            argb(col, (1f - k / 7f) * A));
                }
                float rad = (float) Math.toRadians(na);
                cellAt(g, (float) Math.sin(rad) * (DIAL_R + 6), -(float) Math.cos(rad) * (DIAL_R + 6), 3, argb(dangerRgb, A));
            }
        }

        // ---- клавиша
        if (s.showKey && (!shatter || endT < 0.55f)) {
            int hh = 11;
            float press = Math.max(q.held ? 1f : 0f, capPulse);
            int face = lerpRgb(0x6B6560, mainRgb, press * 0.30f);
            face = lerpRgb(face, dangerRgb, urg * 0.5f + (failed ? 0.6f : 0f));
            face = lerpRgb(face, 0xFFFFFF, flash * 0.6f);
            int hi = lerpRgb(face, 0xFFFFFF, 0.28f);
            int sh = lerpRgb(face, 0x000000, 0.55f);
            if (shatter) {
                // клавиша раскалывается на четыре куска и падает
                float al = clamp01(1f - endSec / 0.8f) * A;
                for (int qi = 0; qi < 4; qi++) {
                    int sx = (qi & 1) == 0 ? -1 : 1, sy = (qi & 2) == 0 ? -1 : 1;
                    float dx = sx * (4f + 40f * endSec);
                    float dy = sy * 6f * endSec + 110f * endSec * endSec;
                    int x0 = Math.round((sx < 0 ? -hh : 0) + dx), y0 = Math.round((sy < 0 ? -hh : 0) + dy);
                    g.fill(x0 - 1, y0 - 1, x0 + hh + 1, y0 + hh + 1, argb(0x000000, al));
                    g.fill(x0, y0, x0 + hh, y0 + hh, argb(face, al));
                    g.fill(x0, y0, x0 + hh, y0 + 2, argb(hi, al));
                }
            } else {
                ps.pushPose();
                g.fill(-hh - 1, -hh + 1, hh + 1, hh + 3, argb(0x000000, 0.9f * A));
                g.fill(-hh, -hh + 2, hh, hh + 2, argb(0x2A2420, 0.95f * A));
                ps.translate(0f, lerp(-2f, 1f, press), 0f);
                cap(g, hh, face, hi, sh, 0.98f * A);
                String label = q.key.getDisplayName().getString().toUpperCase();
                float ls = label.length() > 5 ? 0.6f : label.length() > 2 ? 0.9f : 1.5f;
                text(g, font, label, 0f, -3.5f * ls, ls, argb(BONE, A));
                if (failed) {
                    // трещина через клавишу
                    int[][] crack = {{-2, -11}, {1, -7}, {-1, -3}, {2, 1}, {0, 5}, {3, 9}};
                    for (int[] c : crack) g.fill(c[0], c[1], c[0] + 2, c[1] + 4, argb(0x000000, 0.9f * A));
                }
                ps.popPose();
            }
        }

        // ---- шкала прогресса (как полоска опыта)
        float by0 = OUT_R + 10f;
        if (s.showProgress) {
            int bx0 = -BAR_W / 2;
            int y0 = (int) by0;
            g.fill(bx0 - 1, y0 - 1, bx0 + BAR_W + 1, y0 + BAR_H + 1, argb(0x000000, 0.88f * A));
            g.fill(bx0, y0, bx0 + BAR_W, y0 + BAR_H, argb(0x241C1C, 0.92f * A));
            int fw = Math.round(BAR_W * clamp01(q.shown));
            if (fw > 0) {
                int barRgb = lerpRgb(mainRgb, 0xFFFFFF, hitFlash * 0.5f + capPulse * 0.25f);
                g.fill(bx0, y0, bx0 + fw, y0 + BAR_H, argb(barRgb, A));
                g.fill(bx0, y0, bx0 + fw, y0 + 1, argb(lerpRgb(barRgb, 0xFFFFFF, 0.45f), A));
                g.fill(bx0, y0 + BAR_H - 1, bx0 + fw, y0 + BAR_H, argb(lerpRgb(barRgb, 0x000000, 0.4f), A));
            }
            int segs = s.mode == QteSettings.Mode.HOLD ? 10 : Math.min(s.count, 20);
            for (int k = 1; k < segs; k++) {
                int x = bx0 + k * BAR_W / segs;
                g.fill(x, y0, x + 1, y0 + BAR_H, argb(0x000000, 0.6f * A));
            }
        }

        // ---- волны от нажатий (пиксельные кольца)
        for (int i = q.ripples.size() - 1; i >= 0; i--) {
            float ra = (now - q.ripples.get(i)) / 520f;
            if (ra >= 1f) {
                q.ripples.remove(i);
                continue;
            }
            cellRing(g, 28, 14f + 34f * easeOut(ra), 2, mainRgb, (1f - ra) * 0.8f * A);
        }

        // ---- финал: успех
        if (q.finished && q.success) {
            if (s.successFx == QteSettings.SuccessFx.BURST) {
                float a = clamp01(endSec / 0.8f);
                cellRing(g, 44, OUT_R + 6f + a * 70f, 3, SUCCESS_RGB, (1f - a) * 0.9f);
            } else if (s.successFx == QteSettings.SuccessFx.GLOW) {
                for (int i = 0; i < 3; i++) {
                    float a = clamp01((endSec - i * 0.12f) / 0.8f);
                    if (a <= 0f) continue;
                    cellRing(g, 40, OUT_R + 4f + a * (26f + i * 14f), 2, SUCCESS_RGB, (1f - a) * 0.6f);
                }
            }
        }

        // ---- финал: кровь стекает вниз
        if (bloody) {
            int drops = 16;
            for (int i = 0; i < drops; i++) {
                float x0 = (i / (float) (drops - 1) - 0.5f) * 84f + (hash(i) - 0.5f) * 8f;
                float t0 = endSec - hash(i + 40L) * 0.3f;
                if (t0 <= 0f) continue;
                float yTop = -30f + hash(i + 60L) * 44f;
                float len = Math.min(26f + hash(i + 80L) * 60f, (40f + hash(i + 20L) * 90f) * t0);
                int wd = hash(i + 7L) > 0.5f ? 3 : 2;
                int ix = Math.round(x0), iy = Math.round(yTop), il = Math.round(len);
                g.fill(ix, iy, ix + wd, iy + il, argb(0x8A0808, 0.92f * master));
                g.fill(ix, iy, ix + 1, iy + il, argb(0xB01818, 0.9f * master));
                g.fill(ix - 1, iy + il - 2, ix + wd + 1, iy + il + 1, argb(0x6A0505, 0.95f * master));
            }
        }

        // ---- частицы-пиксели
        for (int i = q.particles.size() - 1; i >= 0; i--) {
            QteClient.Particle p = q.particles.get(i);
            float pa = (now - p.born) / (float) p.life;
            if (pa >= 1f) {
                q.particles.remove(i);
                continue;
            }
            float sec = (now - p.born) / 1000f;
            float px = p.vx * sec * (1f - pa * 0.4f);
            float py = p.vy * sec * (1f - pa * 0.4f) + 70f * sec * sec;
            int ix = Math.round(px), iy = Math.round(py);
            g.fill(ix, iy, ix + p.size, iy + p.size, argb(p.rgb, (1f - pa) * master));
        }

        // ---- тексты
        if (q.finished) {
            String msg = q.success
                    ? (s.successText.isEmpty() ? t("qte.success") : s.successText)
                    : (s.failText.isEmpty() ? t("qte.fail") : s.failText);
            float pop = easeOutBack(clamp01(endSec / 0.3f));
            ghostText(g, font, msg, 0f, -OUT_R - 24f, 1.7f * Math.max(0.1f, pop), q.success ? SUCCESS_RGB : dangerRgb,
                    master, failed ? clamp01(1f - endSec / 0.8f) : 0f);
        } else {
            String prompt = !s.prompt.isEmpty() ? s.prompt : switch (s.mode) {
                case MASH -> t("qte.prompt.mash");
                case HOLD -> t("qte.prompt.hold");
                case TIMING -> t("qte.prompt.timing");
            };
            float ghost = low && hash(now / 90L + 5L) > 0.6f ? 0.4f + 0.6f * urg : 0f;
            ghostText(g, font, prompt, 0f, -OUT_R - 22f, 1.5f + 0.12f * beat, lerpRgb(BONE, dangerRgb, urg * 0.8f), A, ghost);
        }
        float ty = by0 + (s.showProgress ? BAR_H + 5f : 0f);
        if (s.showProgress) {
            String counter = switch (s.mode) {
                case MASH -> Math.round(q.value * s.count) + " / " + s.count;
                case HOLD -> Math.round(q.value * 100f) + "%";
                case TIMING -> q.hits + " / " + s.count;
            };
            text(g, font, counter, 0f, ty, 1.0f, argb(BONE, 0.9f * A));
            ty += 11f;
        }
        if (s.mode == QteSettings.Mode.TIMING && s.misses > 0) {
            text(g, font, q.missCount + " / " + s.misses + " " + t("qte.misses"), 0f, ty, 0.8f, argb(dangerRgb, 0.95f * A));
            ty += 10f;
        }
        if (s.showTimer) {
            text(g, font, String.format(java.util.Locale.ROOT, "%.1f", left), 0f, ty, 1.0f, argb(lerpRgb(BONE, dangerRgb, urg), 0.95f * A));
        }

        ps.popPose();
    }

    // ------------------------------------------------------------ виньетка

    /** Ступенчатое затемнение краёв: вложенные рамки, как «ступеньки» пикселей. */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float k, float depthFrac) {
        if (k < 0.01f) return;
        int steps = 8;
        float depth = Math.min(w, h) * depthFrac;
        int c = argb(rgb, k / steps * 2.2f);
        for (int i = 0; i < steps; i++) {
            int th = Math.round(depth * (i + 1) / steps);
            g.fill(0, 0, w, th, c);
            g.fill(0, h - th, w, h, c);
            g.fill(0, th, th, h - th, c);
            g.fill(w - th, th, w, h - th, c);
        }
    }
}
