package com.example.qte;

import net.minecraft.nbt.CompoundTag;

/** Полный набор настроек одного QTE (пресет). Хранится в NBT и передаётся по сети. */
public final class QteSettings {
    public enum Mode { MASH, HOLD, TIMING }
    public enum Appear { POP, FADE, SLIDE, SPIN, GLITCH, CREEP }
    public enum PressFx { PULSE, RIPPLE, BURST, FLASH, JOLT, NONE }
    public enum SuccessFx { BURST, GLOW, NONE }
    public enum FailFx { SHATTER, BLOOD, SHAKE, NONE }
    public enum Position { CENTER, LOWER, UPPER, BOTTOM }
    public enum Pack { CLEAN, DARK }

    // --- основное
    public String name = "default";
    public Mode mode = Mode.MASH;
    /** Имя клавиши в формате Minecraft, например key.keyboard.space или key.mouse.left. */
    public String key = "key.keyboard.space";
    /** Сколько нажатий (MASH) или попаданий (TIMING). */
    public int count = 10;
    /** Общий лимит времени в секундах. */
    public double seconds = 4.0;
    /** Сколько секунд нужно удерживать (HOLD). */
    public double holdSeconds = 2.0;
    /** За сколько секунд стрелка делает полный круг (TIMING). */
    public double needleSeconds = 1.2;
    /** Размер зоны попадания в градусах (TIMING). */
    public int zoneDeg = 50;
    /** Сколько промахов допускается (TIMING). */
    public int misses = 0;
    /** Скорость «утекания» шкалы, % шкалы в секунду (MASH и HOLD). */
    public int decay = 15;
    public String prompt = "";
    public String successText = "";
    public String failText = "";
    public boolean showKey = true;
    public boolean showTimer = true;
    public boolean showProgress = true;

    // --- звук
    public Pack pack = Pack.DARK;
    public int volume = 70;
    public boolean sStart = true;
    public boolean sPress = true;
    public boolean sTick = true;
    public boolean sHeart = true;
    public boolean sSuccess = true;
    public boolean sFail = true;
    public boolean pitchRise = true;

    // --- анимации
    public Appear appear = Appear.GLITCH;
    public PressFx pressFx = PressFx.RIPPLE;
    public SuccessFx successFx = SuccessFx.BURST;
    public FailFx failFx = FailFx.BLOOD;
    public Position position = Position.CENTER;
    /** Размер в процентах. */
    public int scale = 100;
    /** Тряска камеры при нажатии, 0..10. */
    public int shake = 3;
    public boolean vignette = true;
    public String color = "D8D2C4";
    public String danger = "B01818";

    // --- действия
    /** Блокировать управление (движение, инвентарь, атаку) на время QTE. */
    public boolean lockInput = true;
    public String onSuccess = "";
    public String onFail = "";

    public QteSettings copy() {
        return load(save());
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("mode", mode.name());
        t.putString("key", key);
        t.putInt("count", count);
        t.putDouble("seconds", seconds);
        t.putDouble("hold", holdSeconds);
        t.putDouble("needle", needleSeconds);
        t.putInt("zone", zoneDeg);
        t.putInt("misses", misses);
        t.putInt("decay", decay);
        t.putString("prompt", prompt);
        t.putString("okText", successText);
        t.putString("failText", failText);
        t.putBoolean("showKey", showKey);
        t.putBoolean("showTimer", showTimer);
        t.putBoolean("showProgress", showProgress);
        t.putString("pack", pack.name());
        t.putInt("volume", volume);
        t.putBoolean("sStart", sStart);
        t.putBoolean("sPress", sPress);
        t.putBoolean("sTick", sTick);
        t.putBoolean("sHeart", sHeart);
        t.putBoolean("sSuccess", sSuccess);
        t.putBoolean("sFail", sFail);
        t.putBoolean("pitchRise", pitchRise);
        t.putString("appear", appear.name());
        t.putString("pressFx", pressFx.name());
        t.putString("successFx", successFx.name());
        t.putString("failFx", failFx.name());
        t.putString("position", position.name());
        t.putInt("scale", scale);
        t.putInt("shake", shake);
        t.putBoolean("vignette", vignette);
        t.putString("color", color);
        t.putString("danger", danger);
        t.putBoolean("lock", lockInput);
        t.putString("onSuccess", onSuccess);
        t.putString("onFail", onFail);
        return t;
    }

    public static QteSettings load(CompoundTag t) {
        QteSettings s = new QteSettings();
        if (t == null) return s;
        if (t.contains("name")) s.name = t.getString("name");
        s.mode = en(Mode.class, t, "mode", s.mode);
        if (t.contains("key")) s.key = t.getString("key");
        if (t.contains("count")) s.count = t.getInt("count");
        if (t.contains("seconds")) s.seconds = t.getDouble("seconds");
        if (t.contains("hold")) s.holdSeconds = t.getDouble("hold");
        if (t.contains("needle")) s.needleSeconds = t.getDouble("needle");
        if (t.contains("zone")) s.zoneDeg = t.getInt("zone");
        if (t.contains("misses")) s.misses = t.getInt("misses");
        if (t.contains("decay")) s.decay = t.getInt("decay");
        s.prompt = t.getString("prompt");
        s.successText = t.getString("okText");
        s.failText = t.getString("failText");
        s.showKey = bool(t, "showKey", s.showKey);
        s.showTimer = bool(t, "showTimer", s.showTimer);
        s.showProgress = bool(t, "showProgress", s.showProgress);
        s.pack = en(Pack.class, t, "pack", s.pack);
        if (t.contains("volume")) s.volume = t.getInt("volume");
        s.sStart = bool(t, "sStart", s.sStart);
        s.sPress = bool(t, "sPress", s.sPress);
        s.sTick = bool(t, "sTick", s.sTick);
        s.sHeart = bool(t, "sHeart", s.sHeart);
        s.sSuccess = bool(t, "sSuccess", s.sSuccess);
        s.sFail = bool(t, "sFail", s.sFail);
        s.pitchRise = bool(t, "pitchRise", s.pitchRise);
        s.appear = en(Appear.class, t, "appear", s.appear);
        s.pressFx = en(PressFx.class, t, "pressFx", s.pressFx);
        s.successFx = en(SuccessFx.class, t, "successFx", s.successFx);
        s.failFx = en(FailFx.class, t, "failFx", s.failFx);
        s.position = en(Position.class, t, "position", s.position);
        if (t.contains("scale")) s.scale = t.getInt("scale");
        if (t.contains("shake")) s.shake = t.getInt("shake");
        s.vignette = bool(t, "vignette", s.vignette);
        if (t.contains("color")) s.color = t.getString("color");
        if (t.contains("danger")) s.danger = t.getString("danger");
        s.lockInput = bool(t, "lock", s.lockInput);
        s.onSuccess = t.getString("onSuccess");
        s.onFail = t.getString("onFail");
        s.sanitize();
        return s;
    }

    private static boolean bool(CompoundTag t, String k, boolean def) {
        return t.contains(k) ? t.getBoolean(k) : def;
    }

    private static <E extends Enum<E>> E en(Class<E> cls, CompoundTag t, String k, E def) {
        if (!t.contains(k)) return def;
        try {
            return Enum.valueOf(cls, t.getString(k));
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    // ------------------------------------------------------------ проверка

    /** Приводит все значения к допустимым диапазонам (вызывается и на сервере, и на клиенте). */
    public void sanitize() {
        name = cleanName(name);
        if (name.isEmpty()) name = "default";
        key = key == null || key.isEmpty() ? "key.keyboard.space" : cut(key, 64);
        count = clamp(count, 1, 200);
        seconds = clamp(seconds, 0.5, 120.0);
        holdSeconds = clamp(holdSeconds, 0.2, 60.0);
        needleSeconds = clamp(needleSeconds, 0.3, 10.0);
        zoneDeg = clamp(zoneDeg, 10, 180);
        misses = clamp(misses, 0, 20);
        decay = clamp(decay, 0, 300);
        prompt = cut(prompt, 40);
        successText = cut(successText, 40);
        failText = cut(failText, 40);
        volume = clamp(volume, 0, 100);
        scale = clamp(scale, 50, 300);
        shake = clamp(shake, 0, 10);
        color = hex(color, "D8D2C4");
        danger = hex(danger, "B01818");
        onSuccess = cut(onSuccess, 256);
        onFail = cut(onFail, 256);
    }

    public static String cleanName(String n) {
        if (n == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length() && sb.length() < 24; i++) {
            char c = n.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-') sb.append(c);
        }
        return sb.toString();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String hex(String s, String def) {
        if (s == null) return def;
        String h = s.startsWith("#") ? s.substring(1) : s;
        if (h.length() != 6) return def;
        for (int i = 0; i < 6; i++) {
            if (Character.digit(h.charAt(i), 16) < 0) return def;
        }
        return h.toUpperCase();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    /** Цвет 0xRRGGBB из строки настроек. */
    public static int rgb(String hex6) {
        try {
            return Integer.parseInt(hex6, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
