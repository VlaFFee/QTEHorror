package com.example.qte.client;

import com.example.qte.ModSounds;
import com.example.qte.QteSettings;
import com.example.qte.net.Net;
import com.example.qte.net.Packets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Состояние и логика одного активного QTE на клиенте. */
public final class QteClient {
    /** Сколько после завершения остаётся на экране финальная анимация. */
    public static final long END_MS = 1400;
    /** Длина зацикленного демо в редакторе. */
    private static final long DEMO_LOOP_MS = 5000;

    public static QteClient current;

    public static final class Particle {
        public final float vx, vy;
        public final long born;
        public final int life;
        public final int rgb;
        public final int size;

        Particle(float vx, float vy, long born, int life, int rgb, int size) {
            this.vx = vx;
            this.vy = vy;
            this.born = born;
            this.life = life;
            this.rgb = rgb;
            this.size = size;
        }
    }

    public final int id;
    public final QteSettings s;
    public final boolean preview;
    /** Демо для окна редактора: крутится по кругу, ввода и сети нет. */
    public final boolean demo;
    public final long startMs;
    public final InputConstants.Key key;
    private final Runnable onEnd;
    private final Random rnd = new Random();

    public float value;          // 0..1 общий прогресс
    public float shown;          // сглаженное значение для отрисовки
    public boolean held;
    public int hits;
    public int missCount;
    public float zoneCenter;     // градусы, 0 = верх, по часовой
    public boolean finished;
    public boolean success;
    public long endMs;
    public long lastPressMs = -100000;
    public long lastMissMs = -100000;
    public long lastHitMs = -100000;
    public long zoneMs;
    public long lastHeartMs;

    public final List<Long> ripples = new ArrayList<>();
    public final List<Particle> particles = new ArrayList<>();

    private long lastUpdate;
    private int lastTickSecond = Integer.MAX_VALUE;
    private float shakeAmp0;
    private long shakeT0;

    private QteClient(int id, QteSettings s, boolean preview, boolean demo, Runnable onEnd) {
        this.id = id;
        this.s = s;
        this.preview = preview;
        this.demo = demo;
        this.onEnd = onEnd;
        this.startMs = Util.getMillis();
        this.lastUpdate = startMs;
        this.lastHeartMs = startMs;
        this.zoneMs = startMs + 250;
        InputConstants.Key k;
        try {
            k = InputConstants.getKey(s.key);
        } catch (IllegalArgumentException e) {
            k = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_SPACE);
        }
        this.key = k;
        if (s.mode == QteSettings.Mode.TIMING) zoneCenter = rnd.nextFloat() * 360f;
        if (demo) zoneCenter = 70f;
    }

    // --------------------------------------------------------------- запуск

    public static void start(int id, QteSettings s) {
        begin(new QteClient(id, s, false, false, null));
    }

    public static void startPreview(QteSettings s, Runnable onEnd) {
        begin(new QteClient(0, s, true, false, onEnd));
    }

    /** Живой предпросмотр для окна редактора (читает настройки напрямую, поэтому меняется вместе с ними). */
    public static QteClient demo(QteSettings s) {
        return new QteClient(0, s, true, true, null);
    }

    private static void begin(QteClient q) {
        current = q;
        q.play(ModSounds.Kind.START, 1f, q.s.sStart, 1f);
    }

    public static void stop() {
        QteClient q = current;
        current = null;
        if (q != null && q.onEnd != null) q.onEnd.run();
    }

    // ---------------------------------------------------------------- звук

    private void play(ModSounds.Kind kind, float pitch, boolean enabled, float volScale) {
        if (!enabled || demo) return;
        float vol = s.volume / 100f * volScale;
        if (vol <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.get(s.pack, kind), pitch, Math.min(1f, vol)));
    }

    // ---------------------------------------------------------------- ввод

    public boolean matchesKey(int keyCode) {
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() == keyCode;
    }

    public boolean matchesMouse(int button) {
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() == button;
    }

    public void onDown() {
        if (finished || demo) return;
        long now = Util.getMillis();
        update(now);
        if (finished) return;
        switch (s.mode) {
            case MASH -> {
                value = Math.min(1f, value + 1f / s.count);
                pressEffects(now);
                play(ModSounds.Kind.PRESS, s.pitchRise ? 0.8f + 0.8f * value : 1f, s.sPress, 0.8f);
                if (value >= 0.999f) finish(true);
            }
            case HOLD -> {
                held = true;
                pressEffects(now);
                play(ModSounds.Kind.PRESS, 1f, s.sPress, 0.8f);
            }
            case TIMING -> {
                float needle = needleAngle(now);
                float diff = Math.abs(wrap180(needle - zoneCenter));
                if (diff <= s.zoneDeg / 2f + 5f) {
                    hits++;
                    value = hits / (float) s.count;
                    lastHitMs = now;
                    pressEffects(now);
                    play(ModSounds.Kind.HIT, s.pitchRise ? 0.85f + 0.5f * value : 1f, s.sPress, 0.9f);
                    if (hits >= s.count) {
                        finish(true);
                    } else {
                        zoneCenter = (needle + 100f + rnd.nextFloat() * 160f) % 360f;
                        zoneMs = now;
                    }
                } else {
                    missCount++;
                    lastMissMs = now;
                    addShake(s.shake * 0.35f, now);
                    play(ModSounds.Kind.MISS, 1f, s.sPress, 0.9f);
                    if (missCount > s.misses) finish(false);
                }
            }
        }
    }

    public void onUp() {
        if (finished || s.mode != QteSettings.Mode.HOLD) return;
        held = false;
    }

    private void pressEffects(long now) {
        lastPressMs = now;
        addShake(s.shake * 0.12f, now);
        switch (s.pressFx) {
            case RIPPLE -> ripples.add(now);
            case BURST -> burst(8, 85f, QteSettings.rgb(s.color), now, 480, 2);
            default -> { }
        }
    }

    // ------------------------------------------------------------- логика

    public float needleAngle(long now) {
        double t = (now - startMs) / 1000.0 / s.needleSeconds;
        return (float) ((t - Math.floor(t)) * 360.0);
    }

    private static float wrap180(float a) {
        a = a % 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }

    /** Порог «мало времени»: после него включаются сердцебиение, дрожь и виньетка. */
    public float lowAt() {
        return Math.max(2.5f, (float) s.seconds * 0.25f);
    }

    /** Сколько мс прошло с начала (в демо — внутри зацикленного отрезка). */
    public long age(long now) {
        return demo ? (now - startMs) % DEMO_LOOP_MS : now - startMs;
    }

    public float timeLeft(long now) {
        if (demo) {
            double ph = ((now - startMs) % DEMO_LOOP_MS) / (double) DEMO_LOOP_MS;
            return (float) (s.seconds * (1.0 - ph));
        }
        long ref = finished ? endMs : now;
        return Math.max(0f, (float) (s.seconds - (ref - startMs) / 1000.0));
    }

    /** Заполняет значения демо (вызывается из отрисовки). */
    public void demoStep(long now) {
        long a = age(now);
        switch (s.mode) {
            case MASH -> {
                value = (a % 2400L) / 2400f;
                lastPressMs = now - (a % 300L);
                held = false;
            }
            case HOLD -> {
                long p = a % 2600L;
                held = p < 1800L;
                value = held ? p / 1800f : Math.max(0f, 1f - (p - 1800L) / 800f);
            }
            case TIMING -> {
                value = (a % 3000L) / 3000f;
                held = false;
            }
        }
        shown = value;
        if (timeLeft(now) <= lowAt() && now - lastHeartMs > 750) lastHeartMs = now;
    }

    /** Обновляет таймеры. Вызывается каждый кадр и каждый тик, повторные вызовы безопасны. */
    public void update(long now) {
        if (finished || demo) return;
        float dt = Math.min(0.1f, (now - lastUpdate) / 1000f);
        lastUpdate = now;
        if (dt <= 0) return;

        if (s.mode == QteSettings.Mode.MASH) {
            value = Math.max(0f, value - s.decay / 100f * dt);
        } else if (s.mode == QteSettings.Mode.HOLD) {
            if (held) value += dt / (float) s.holdSeconds;
            else value = Math.max(0f, value - s.decay / 100f * dt);
            if (value >= 1f) {
                value = 1f;
                finish(true);
                return;
            }
        }
        // отображаемое значение догоняет настоящее: шкала «ползёт», а не прыгает
        shown += (value - shown) * Math.min(1f, dt * 16f);

        float left = timeLeft(now);
        if (left <= 0f) {
            finish(false);
            return;
        }
        if (left <= 3f) {
            int sec = (int) Math.ceil(left);
            if (sec < lastTickSecond) {
                lastTickSecond = sec;
                play(ModSounds.Kind.TICK, 1f, s.sTick, 0.7f);
            }
        }
        if (left <= lowAt() && now - lastHeartMs > 750) {
            lastHeartMs = now;
            play(ModSounds.Kind.HEART, 1f, s.sHeart, 0.9f);
            addShake(s.shake * 0.08f, now);
        }
    }

    private void finish(boolean ok) {
        if (finished) return;
        finished = true;
        success = ok;
        held = false;
        endMs = Util.getMillis();
        if (ok) shown = 1f;
        if (!preview) Net.CHANNEL.sendToServer(new Packets.Result(id, ok));
        if (ok) {
            play(ModSounds.Kind.SUCCESS, 1f, s.sSuccess, 1f);
            addShake(s.shake * 0.2f, endMs);
            if (s.successFx == QteSettings.SuccessFx.BURST) burst(40, 150f, 0x9FD18B, endMs, 950, 2);
        } else {
            play(ModSounds.Kind.FAIL, 1f, s.sFail, 1f);
            addShake(s.shake * 0.6f, endMs);
            int danger = QteSettings.rgb(s.danger);
            if (s.failFx == QteSettings.FailFx.SHATTER) burst(18, 110f, danger, endMs, 850, 2);
            else if (s.failFx == QteSettings.FailFx.BLOOD) burst(26, 75f, 0x8A0808, endMs, 1000, 2);
        }
    }

    /** Вызывается каждый тик клиента: убирает завершённое QTE. */
    public static void tick() {
        QteClient q = current;
        if (q == null) return;
        long now = Util.getMillis();
        q.update(now);
        if (q.finished && now - q.endMs > END_MS) stop();
    }

    // -------------------------------------------------- частицы и тряска

    private void burst(int n, float speed, int rgb, long now, int life, int size) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            float v = speed * (0.4f + rnd.nextFloat() * 0.8f);
            particles.add(new Particle((float) Math.cos(a) * v, (float) Math.sin(a) * v, now, life, rgb, size));
        }
        if (particles.size() > 200) particles.subList(0, particles.size() - 200).clear();
    }

    private void addShake(float deg, long now) {
        if (s.shake <= 0) return;
        shakeAmp0 = Math.min(6f, shakeAmp(now) + deg);
        shakeT0 = now;
    }

    private float shakeAmp(long now) {
        return shakeAmp0 * (float) Math.exp(-(now - shakeT0) / 1000.0 * 8.0);
    }

    /** Смещения камеры {roll, pitch, yaw} в градусах. */
    public float[] cameraShake(long now) {
        float a = shakeAmp(now);
        if (a < 0.01f) return null;
        double t = now;
        return new float[]{
                (float) Math.sin(t * 0.09) * a,
                (float) Math.cos(t * 0.13) * a * 0.6f,
                (float) Math.sin(t * 0.11 + 1.0) * a * 0.6f
        };
    }
}
