package com.example.qte.client;

import com.example.qte.QteSettings;
import com.example.qte.net.Net;
import com.example.qte.net.Packets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Окно настройки QTE: пресеты, 4 вкладки, живой предпросмотр, тест и запуск. Открывается командой /qte gui. */
public class QteEditorScreen extends Screen {
    private static final ResourceLocation STONE = new ResourceLocation("minecraft", "textures/block/polished_blackstone.png");

    private static final int PW = 340;
    private static final int PH = 272;
    private static final int COL_W = 164;
    private static final int GAP = 12;
    private static final int ROW = 22;
    private static final int PREVIEW_W = 150;
    private static final int PREVIEW_H = 190;

    private static final int C_BONE = 0xFFF2E8D6;
    private static final int C_DIM = 0xFF9A9087;
    private static final int C_BLOOD = 0xFFA01818;

    private record Header(int x, int y, int w, String key) {}

    private List<QteSettings> presets;
    private int index;
    private int page;
    private boolean listening;
    private Button keyButton;
    private Component status = Component.empty();
    private long statusUntil;
    private final List<Header> headers = new ArrayList<>();
    private QteClient demo;
    /** Y строки с полями цвета на вкладке «Анимация» (для цветных квадратиков), или -1. */
    private int swatchY = -1;

    public QteEditorScreen(List<QteSettings> presets) {
        super(Component.translatable("qte.gui.title"));
        this.presets = new ArrayList<>(presets);
        if (this.presets.isEmpty()) this.presets.add(new QteSettings());
    }

    private QteSettings cur() {
        return presets.get(Math.max(0, Math.min(index, presets.size() - 1)));
    }

    /** Сервер прислал свежий список после сохранения/удаления. */
    public void refresh(List<QteSettings> list) {
        String keep = cur().name;
        presets = new ArrayList<>(list);
        if (presets.isEmpty()) presets.add(new QteSettings());
        index = 0;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.equals(keep)) index = i;
        }
        setStatus(Component.translatable("qte.gui.saved"));
        rebuild();
    }

    private void setStatus(Component c) {
        status = c;
        statusUntil = System.currentTimeMillis() + 3000;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private int left() {
        return (this.width - PW) / 2;
    }

    private int top() {
        return Math.max(14, (this.height - PH) / 2);
    }

    private boolean hasPreview() {
        return left() + PW + 18 + PREVIEW_W <= this.width - 4;
    }

    // ------------------------------------------------------------ построение

    private void rebuild() {
        clearWidgets();
        headers.clear();
        swatchY = -1;
        listening = false;
        int l = left();
        int t = top();
        QteSettings s = cur();
        demo = QteClient.demo(s);

        // строка пресетов: выбор, новый, копия, удалить
        List<String> names = new ArrayList<>();
        for (QteSettings p : presets) names.add(p.name);
        addRenderableWidget(CycleButton.<String>builder(n -> Component.literal(n))
                .withValues(names).withInitialValue(s.name)
                .create(l, t + 24, 150, 20, Component.translatable("qte.gui.preset"), (b, v) -> {
                    index = names.indexOf(v);
                    rebuild();
                }));
        addRenderableWidget(tip(Button.builder(Component.translatable("qte.gui.new"), b -> {
            QteSettings n = new QteSettings();
            n.name = freeName("preset");
            presets.add(n);
            index = presets.size() - 1;
            rebuild();
        }).bounds(l + 156, t + 24, 56, 20).build(), "qte.tip.new"));
        addRenderableWidget(tip(Button.builder(Component.translatable("qte.gui.copy"), b -> {
            QteSettings n = cur().copy();
            n.name = freeName(cur().name);
            presets.add(n);
            index = presets.size() - 1;
            rebuild();
        }).bounds(l + 216, t + 24, 56, 20).build(), "qte.tip.copy"));
        addRenderableWidget(tip(Button.builder(Component.translatable("qte.gui.delete"), b -> {
            if (presets.size() <= 1) {
                setStatus(Component.translatable("qte.gui.last"));
                return;
            }
            Net.CHANNEL.sendToServer(new Packets.DeletePreset(cur().name));
            presets.remove(index);
            index = 0;
            rebuild();
        }).bounds(l + 276, t + 24, 64, 20).build(), "qte.tip.delete"));

        // вкладки
        String[] tabs = {"qte.tab.main", "qte.tab.sound", "qte.tab.anim", "qte.tab.actions"};
        int tw = (PW - 3 * 3) / 4;
        for (int i = 0; i < 4; i++) {
            final int p = i;
            addRenderableWidget(new TabButton(l + i * (tw + 3), t + 50, tw, 20, Component.translatable(tabs[i]),
                    page == i, b -> {
                page = p;
                rebuild();
            }));
        }

        int y0 = t + 78;
        switch (page) {
            case 0 -> pageMain(s, l, y0);
            case 1 -> pageSound(s, l, y0);
            case 2 -> pageAnim(s, l, y0);
            default -> pageActions(s, l, y0);
        }

        // нижняя строка
        int by = t + PH - 22;
        int bw = (PW - 3 * 4) / 4;
        addRenderableWidget(tip(Button.builder(Component.translatable("qte.gui.test"), b -> {
            QteSettings copy = cur().copy();
            this.minecraft.setScreen(null);
            QteClient.startPreview(copy, () -> Minecraft.getInstance().setScreen(this));
        }).bounds(l, by, bw, 20).build(), "qte.tip.test"));
        addRenderableWidget(tip(Button.builder(Component.translatable("qte.gui.run").withStyle(ChatFormatting.RED), b -> {
            save();
            Net.CHANNEL.sendToServer(new Packets.RunPreset(cur().name));
            this.minecraft.setScreen(null);
        }).bounds(l + (bw + 4), by, bw, 20).build(), "qte.tip.run"));
        addRenderableWidget(tip(Button.builder(Component.translatable("qte.gui.save"), b -> save())
                .bounds(l + (bw + 4) * 2, by, bw, 20).build(), "qte.tip.save"));
        addRenderableWidget(Button.builder(Component.translatable("qte.gui.close"), b -> onClose())
                .bounds(l + (bw + 4) * 3, by, bw, 20).build());
    }

    private String freeName(String base) {
        String b = QteSettings.cleanName(base);
        if (b.isEmpty()) b = "preset";
        if (b.length() > 20) b = b.substring(0, 20);
        int k = 2;
        String n = b + k;
        while (hasName(n)) n = b + (++k);
        return n;
    }

    private boolean hasName(String n) {
        for (QteSettings p : presets) if (p.name.equals(n)) return true;
        return false;
    }

    private void save() {
        QteSettings s = cur();
        s.sanitize();
        Net.CHANNEL.sendToServer(new Packets.SavePreset(s.save()));
    }

    private int cx(int col, int l) {
        return l + col * (COL_W + GAP);
    }

    private void header(int x, int y, int w, String key) {
        headers.add(new Header(x, y, w, key));
    }

    // -------------------------------------------------------------- вкладки

    private void pageMain(QteSettings s, int l, int y) {
        addRenderableWidget(text(cx(0, l), y, COL_W, "qte.f.name", s.name, 24, v -> s.name = QteSettings.cleanName(v),
                v -> v.matches("[A-Za-z0-9_-]*"), 0));
        addRenderableWidget(enumBtn(cx(1, l), y, COL_W, "qte.f.mode", QteSettings.Mode.class, s.mode, v -> {
            s.mode = v;
            rebuild();
        }));
        y += ROW;
        keyButton = Button.builder(keyLabel(s), b -> {
            listening = true;
            keyButton.setMessage(Component.translatable("qte.gui.press_key"));
        }).bounds(cx(0, l), y, COL_W, 20).build();
        keyButton.setTooltip(Tooltip.create(Component.translatable("qte.tip.key")));
        addRenderableWidget(keyButton);
        boolean hold = s.mode == QteSettings.Mode.HOLD;
        boolean timing = s.mode == QteSettings.Mode.TIMING;
        addRenderableWidget(active(slider(cx(1, l), y, COL_W, "qte.f.count", 1, 100, 1, s.count, "", v -> s.count = (int) Math.round(v)), !hold));
        y += ROW;
        addRenderableWidget(slider(cx(0, l), y, COL_W, "qte.f.seconds", 0.5, 60, 0.5, s.seconds, " s", v -> s.seconds = v));
        addRenderableWidget(active(slider(cx(1, l), y, COL_W, "qte.f.hold", 0.2, 20, 0.1, s.holdSeconds, " s", v -> s.holdSeconds = v), hold));
        y += ROW;
        addRenderableWidget(active(slider(cx(0, l), y, COL_W, "qte.f.needle", 0.4, 4, 0.1, s.needleSeconds, " s", v -> s.needleSeconds = v), timing));
        addRenderableWidget(active(slider(cx(1, l), y, COL_W, "qte.f.zone", 10, 120, 5, s.zoneDeg, "°", v -> s.zoneDeg = (int) Math.round(v)), timing));
        y += ROW;
        addRenderableWidget(active(slider(cx(0, l), y, COL_W, "qte.f.misses", 0, 10, 1, s.misses, "", v -> s.misses = (int) Math.round(v)), timing));
        addRenderableWidget(active(slider(cx(1, l), y, COL_W, "qte.f.decay", 0, 100, 5, s.decay, "%/s", v -> s.decay = (int) Math.round(v)), !timing));
        y += ROW + 2;
        header(l, y, PW, "qte.sec.texts");
        y += 12;
        addRenderableWidget(text(cx(0, l), y, COL_W, "qte.f.prompt", s.prompt, 40, v -> s.prompt = v, null, 0));
        addRenderableWidget(text(cx(1, l), y, COL_W, "qte.f.ok_text", s.successText, 40, v -> s.successText = v, null, 0));
        y += ROW;
        addRenderableWidget(text(cx(0, l), y, COL_W, "qte.f.fail_text", s.failText, 40, v -> s.failText = v, null, 0));
        addRenderableWidget(toggle(cx(1, l), y, COL_W, "qte.f.show_timer", s.showTimer, v -> s.showTimer = v));
    }

    private void pageSound(QteSettings s, int l, int y) {
        addRenderableWidget(enumBtn(cx(0, l), y, COL_W, "qte.f.pack", QteSettings.Pack.class, s.pack, v -> s.pack = v));
        addRenderableWidget(slider(cx(1, l), y, COL_W, "qte.f.volume", 0, 100, 5, s.volume, "%", v -> s.volume = (int) Math.round(v)));
        y += ROW + 2;
        header(l, y, PW, "qte.sec.events");
        y += 12;
        addRenderableWidget(toggle(cx(0, l), y, COL_W, "qte.f.s_start", s.sStart, v -> s.sStart = v));
        addRenderableWidget(toggle(cx(1, l), y, COL_W, "qte.f.s_press", s.sPress, v -> s.sPress = v));
        y += ROW;
        addRenderableWidget(toggle(cx(0, l), y, COL_W, "qte.f.s_tick", s.sTick, v -> s.sTick = v));
        addRenderableWidget(toggle(cx(1, l), y, COL_W, "qte.f.s_heart", s.sHeart, v -> s.sHeart = v));
        y += ROW;
        addRenderableWidget(toggle(cx(0, l), y, COL_W, "qte.f.s_success", s.sSuccess, v -> s.sSuccess = v));
        addRenderableWidget(toggle(cx(1, l), y, COL_W, "qte.f.s_fail", s.sFail, v -> s.sFail = v));
        y += ROW;
        addRenderableWidget(toggle(cx(0, l), y, COL_W, "qte.f.pitch_rise", s.pitchRise, v -> s.pitchRise = v));
    }

    private void pageAnim(QteSettings s, int l, int y) {
        header(l, y, PW, "qte.sec.fx");
        y += 12;
        addRenderableWidget(enumBtn(cx(0, l), y, COL_W, "qte.f.appear", QteSettings.Appear.class, s.appear, v -> s.appear = v));
        addRenderableWidget(enumBtn(cx(1, l), y, COL_W, "qte.f.press_fx", QteSettings.PressFx.class, s.pressFx, v -> s.pressFx = v));
        y += ROW;
        addRenderableWidget(enumBtn(cx(0, l), y, COL_W, "qte.f.success_fx", QteSettings.SuccessFx.class, s.successFx, v -> s.successFx = v));
        addRenderableWidget(enumBtn(cx(1, l), y, COL_W, "qte.f.fail_fx", QteSettings.FailFx.class, s.failFx, v -> s.failFx = v));
        y += ROW + 2;
        header(l, y, PW, "qte.sec.screen");
        y += 12;
        addRenderableWidget(enumBtn(cx(0, l), y, COL_W, "qte.f.position", QteSettings.Position.class, s.position, v -> s.position = v));
        addRenderableWidget(slider(cx(1, l), y, COL_W, "qte.f.scale", 50, 250, 10, s.scale, "%", v -> s.scale = (int) Math.round(v)));
        y += ROW;
        addRenderableWidget(slider(cx(0, l), y, COL_W, "qte.f.shake", 0, 10, 1, s.shake, "", v -> s.shake = (int) Math.round(v)));
        addRenderableWidget(toggle(cx(1, l), y, COL_W, "qte.f.vignette", s.vignette, v -> s.vignette = v));
        y += ROW;
        swatchY = y;
        Predicate<String> hexOnly = v -> v.matches("#?[0-9A-Fa-f]{0,6}");
        addRenderableWidget(text(cx(0, l), y, COL_W, "qte.f.color", s.color, 7, v -> s.color = v.startsWith("#") ? v.substring(1) : v, hexOnly, 18));
        addRenderableWidget(text(cx(1, l), y, COL_W, "qte.f.danger", s.danger, 7, v -> s.danger = v.startsWith("#") ? v.substring(1) : v, hexOnly, 18));
        y += ROW;
        addRenderableWidget(toggle(cx(0, l), y, COL_W, "qte.f.show_key", s.showKey, v -> s.showKey = v));
        addRenderableWidget(toggle(cx(1, l), y, COL_W, "qte.f.show_progress", s.showProgress, v -> s.showProgress = v));
    }

    private void pageActions(QteSettings s, int l, int y) {
        header(l, y, PW, "qte.sec.control");
        y += 12;
        addRenderableWidget(toggle(l, y, PW, "qte.f.lock", s.lockInput, v -> s.lockInput = v));
        y += ROW + 2;
        header(l, y, PW, "qte.sec.commands");
        y += 12;
        addRenderableWidget(text(l, y, PW, "qte.f.on_success", s.onSuccess, 256, v -> s.onSuccess = v, null, 0));
        y += ROW;
        addRenderableWidget(text(l, y, PW, "qte.f.on_fail", s.onFail, 256, v -> s.onFail = v, null, 0));
        y += ROW + 4;
        header(l, y, PW, "qte.sec.safety");
    }

    // ------------------------------------------------------ фабрики виджетов

    private static <T extends AbstractWidget> T active(T w, boolean on) {
        w.active = on;
        return w;
    }

    private static <T extends AbstractWidget> T tip(T w, String tipKey) {
        w.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        return w;
    }

    private static String tipKey(String fieldKey) {
        return fieldKey.replace("qte.f.", "qte.tip.");
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String key, boolean value, Consumer<Boolean> set) {
        CycleButton<Boolean> b = CycleButton.onOffBuilder(value).create(x, y, w, 20, Component.translatable(key), (bb, v) -> set.accept(v));
        return tip(b, tipKey(key));
    }

    private <E extends Enum<E>> CycleButton<E> enumBtn(int x, int y, int w, String key, Class<E> cls, E value, Consumer<E> set) {
        String prefix = "qte.enum." + cls.getSimpleName().toLowerCase(Locale.ROOT) + ".";
        CycleButton<E> b = CycleButton.<E>builder(e -> Component.translatable(prefix + e.name().toLowerCase(Locale.ROOT)))
                .withValues(cls.getEnumConstants()).withInitialValue(value)
                .create(x, y, w, 20, Component.translatable(key), (bb, v) -> set.accept(v));
        return tip(b, tipKey(key));
    }

    private LabeledBox text(int x, int y, int w, String key, String value, int max, Consumer<String> set,
                            Predicate<String> filter, int rightPad) {
        LabeledBox box = new LabeledBox(this.font, x, y, w, 20, Component.translatable(key), rightPad);
        box.setMaxLength(max);
        if (filter != null) box.setFilter(filter);
        box.setValue(value);
        box.setResponder(set);
        return tip(box, tipKey(key));
    }

    private Slider slider(int x, int y, int w, String key, double min, double max, double step, double value, String suffix,
                          Consumer<Double> set) {
        return tip(new Slider(x, y, w, Component.translatable(key), min, max, step, value, suffix, set), tipKey(key));
    }

    /** Поле ввода, у которого название остаётся видимым справа даже когда в поле уже что-то написано. */
    private static final class LabeledBox extends EditBox {
        private final Component label;
        private final int rightPad;

        LabeledBox(Font font, int x, int y, int w, int h, Component label, int rightPad) {
            super(font, x, y, w, h, label);
            this.label = label;
            this.rightPad = rightPad;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            super.renderWidget(g, mx, my, pt);
            Font f = Minecraft.getInstance().font;
            int lw = f.width(label);
            int tw = f.width(getValue());
            if (tw + lw + 18 + rightPad < getWidth()) {
                g.drawString(f, label, getX() + getWidth() - lw - 5 - rightPad, getY() + 6, 0xFF6F675F, false);
            }
        }
    }

    private static final class Slider extends AbstractSliderButton {
        private final Component label;
        private final double min, max, step;
        private final String suffix;
        private final Consumer<Double> set;

        Slider(int x, int y, int w, Component label, double min, double max, double step, double value, String suffix,
               Consumer<Double> set) {
            super(x, y, w, 20, Component.empty(), (Math.max(min, Math.min(max, value)) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.suffix = suffix;
            this.set = set;
            updateMessage();
        }

        private double actual() {
            double v = min + (max - min) * this.value;
            v = Math.round(v / step) * step;
            return Math.max(min, Math.min(max, v));
        }

        @Override
        protected void updateMessage() {
            double v = actual();
            String txt = step >= 1 ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
            setMessage(Component.empty().append(label).append(": " + txt + suffix));
        }

        @Override
        protected void applyValue() {
            set.accept(actual());
        }
    }

    /** Вкладка в тёмном каменном стиле: активная подсвечена красной полосой сверху. */
    private static final class TabButton extends Button {
        private final boolean selected;

        TabButton(int x, int y, int w, int h, Component msg, boolean selected, Button.OnPress onPress) {
            super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
            this.selected = selected;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            g.fill(x, y, x + w, y + h, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, selected ? 0xFF3A3532 : hov ? 0xFF2C2826 : 0xFF1C1918);
            if (selected) {
                g.fill(x + 1, y + 1, x + w - 1, y + 3, C_BLOOD);
                g.fill(x + 1, y + 3, x + w - 1, y + 4, 0xFF5A0D0D);
            } else {
                g.fill(x + 1, y + 1, x + w - 1, y + 2, hov ? 0xFF6A625A : 0xFF3A3532);
            }
            int col = selected ? C_BONE : hov ? 0xFFD8CFC0 : C_DIM;
            g.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2 + (selected ? 1 : 0), col);
        }
    }

    // ------------------------------------------------------------- клавиша

    private Component keyLabel(QteSettings s) {
        Component k;
        try {
            k = InputConstants.getKey(s.key).getDisplayName();
        } catch (IllegalArgumentException e) {
            k = Component.literal(s.key);
        }
        return Component.translatable("qte.f.key").append(": ").append(k);
    }

    private void assignKey(InputConstants.Key k) {
        cur().key = k.getName();
        listening = false;
        keyButton.setMessage(keyLabel(cur()));
        demo = QteClient.demo(cur());
    }

    @Override
    public boolean keyPressed(int code, int scan, int mods) {
        if (listening) {
            if (code != GLFW.GLFW_KEY_ESCAPE) assignKey(InputConstants.getKey(code, scan));
            else {
                listening = false;
                keyButton.setMessage(keyLabel(cur()));
            }
            return true;
        }
        return super.keyPressed(code, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (listening) {
            // клик по самой кнопке выбора клавиши не считаем назначением
            if (keyButton != null && keyButton.isMouseOver(mx, my) && button == 0) return super.mouseClicked(mx, my, button);
            assignKey(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------ отрисовка

    /** Каменная панель с фасками: чёрная обводка, текстура чёрного камня, светлый верх и тёмный низ. */
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.setColor(0.30f, 0.28f, 0.28f, 1f);
        g.blit(STONE, x, y, 0, 0, w, h, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        g.fill(x, y, x + w, y + 2, 0x55FFFFFF);
        g.fill(x, y, x + 2, y + h, 0x40FFFFFF);
        g.fill(x, y + h - 2, x + w, y + h, 0x99000000);
        g.fill(x + w - 2, y, x + w, y + h, 0x80000000);
    }

    /** Утопленное поле, как слот инвентаря: тёмное, с тенью сверху-слева и светлой кромкой снизу-справа. */
    private void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, 0xCC000000);
        g.fill(x, y, x + 1, y + h, 0xCC000000);
        g.fill(x, y + h - 1, x + w, y + h, 0x30FFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0x30FFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD8140606);
        int l = left();
        int t = top();

        // главная панель
        panel(g, l - 10, t - 8, PW + 20, PH + 16);
        // шапка
        g.fill(l - 8, t - 6, l + PW + 8, t + 14, 0xCC000000);
        g.fill(l - 8, t + 14, l + PW + 8, t + 16, 0xFF7A1010);
        g.drawString(this.font, this.title, l, t + 1, C_BONE, true);
        if (!status.getString().isEmpty() && System.currentTimeMillis() < statusUntil) {
            g.drawString(this.font, status, l + PW - this.font.width(status), t + 1, 0xFF8FD18B, true);
        }
        // утопленная зона содержимого
        inset(g, l - 6, t + 74, PW + 12, PH - 74 - 26, 0x77000000);

        // заголовки секций
        for (Header h : headers) {
            Component c = Component.translatable(h.key());
            g.drawString(this.font, c, h.x(), h.y() + 1, 0xFFB8A892, true);
            int lx = h.x() + this.font.width(c) + 5;
            g.fill(lx, h.y() + 5, h.x() + h.w(), h.y() + 6, 0xFF3A3330);
            g.fill(lx, h.y() + 6, h.x() + h.w(), h.y() + 7, 0xFF0E0C0B);
        }

        // подсказки на вкладке «Действия»
        if (page == 3) {
            int y = top() + 78 + 12 + ROW + 2 + 12 + ROW * 2 + 4 + 14;
            for (String k : new String[]{"qte.gui.hint1", "qte.gui.hint2", "qte.gui.hint3", "qte.gui.hint4"}) {
                g.drawString(this.font, Component.translatable(k), l, y, 0xFFA89E92, false);
                y += 11;
            }
        }

        super.render(g, mx, my, pt);

        // цветные квадратики у полей цвета
        if (swatchY >= 0) {
            drawSwatch(g, cx(0, l) + COL_W - 17, swatchY + 4, cur().color);
            drawSwatch(g, cx(1, l) + COL_W - 17, swatchY + 4, cur().danger);
        }

        if (hasPreview()) drawPreview(g, l + PW + 18, t);
    }

    private void drawSwatch(GuiGraphics g, int x, int y, String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        int rgb = h.length() == 6 ? QteSettings.rgb(h) : 0x333333;
        g.fill(x - 1, y - 1, x + 13, y + 13, 0xFF000000);
        g.fill(x, y, x + 12, y + 12, 0xFF000000 | rgb);
    }

    private void drawPreview(GuiGraphics g, int x, int y) {
        panel(g, x, y - 8, PREVIEW_W, PREVIEW_H + 16);
        g.fill(x + 2, y - 6, x + PREVIEW_W - 2, y + 14, 0xCC000000);
        g.fill(x + 2, y + 14, x + PREVIEW_W - 2, y + 16, 0xFF7A1010);
        g.drawString(this.font, Component.translatable("qte.gui.preview"), x + 8, y + 1, C_BONE, true);

        int bx = x + 6, by = y + 22, bw = PREVIEW_W - 12, bh = PREVIEW_H - 22 - 6;
        inset(g, bx, by, bw, bh, 0xFF0A0707);
        g.enableScissor(bx + 1, by + 1, bx + bw - 1, by + bh - 1);
        if (demo != null) {
            QteRender.renderDemo(g, demo, bx + bw / 2f, by + bh / 2f - 6f, 0.95f);
        }
        g.disableScissor();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
