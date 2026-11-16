package dev.songlines.ui;

import dev.songlines.Config;
import dev.songlines.L;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Фабрики строк настроек: переключатель, выбор из списка, палитра цвета, ползунок. */
public final class Opts {
    public interface Opt {
        ClickableWidget make(int x, int y, int w);
    }

    private static Text txt(String label, String value) {
        return Text.literal(label + ": " + value);
    }

    public static String nice(Enum<?> e) {
        String s = e.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static Opt toggle(String label, BooleanSupplier g, Consumer<Boolean> s) {
        return (x, y, w) -> ButtonWidget.builder(txt(label, g.getAsBoolean() ? L.t("On", "Вкл") : L.t("Off", "Выкл")), b -> {
            s.accept(!g.getAsBoolean());
            b.setMessage(txt(label, g.getAsBoolean() ? L.t("On", "Вкл") : L.t("Off", "Выкл")));
        }).dimensions(x, y, w, 20).build();
    }

    public static <E extends Enum<E>> Opt cycle(String label, Supplier<E> g, Consumer<E> s) {
        return (x, y, w) -> ButtonWidget.builder(txt(label, nice(g.get())), b -> {
            s.accept(Config.next(g.get()));
            b.setMessage(txt(label, nice(g.get())));
        }).dimensions(x, y, w, 20).build();
    }

    private static Text colorTxt(String label, String hex) {
        int rgb = Config.rgb(hex, 0xFFFFFF);
        return Text.literal(label + ": ").append(Text.literal("■ " + hex).setStyle(Style.EMPTY.withColor(rgb)));
    }

    /** Кнопка перебирает палитру; точный #RRGGBB можно вписать в songlines/config.json. */
    public static Opt color(String label, Supplier<String> g, Consumer<String> s) {
        return (x, y, w) -> ButtonWidget.builder(colorTxt(label, g.get()), b -> {
            s.accept(Config.nextColor(g.get()));
            b.setMessage(colorTxt(label, g.get()));
        }).dimensions(x, y, w, 20).build();
    }

    private static String shortName(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    /** Перебор значений из списка (например, шрифты). */
    public static Opt choice(String label, Supplier<String> g, Consumer<String> s, Supplier<List<String>> values) {
        return (x, y, w) -> ButtonWidget.builder(txt(label, shortName(g.get())), b -> {
            List<String> v = values.get();
            int i = v.indexOf(g.get());
            s.accept(v.get((i + 1) % v.size()));
            b.setMessage(txt(label, shortName(g.get())));
        }).dimensions(x, y, w, 20).build();
    }

    /** Ползунок одного канала (shift: 16 = R, 8 = G, 0 = B) для цвета в формате "#RRGGBB". */
    public static Opt rgbSlider(String label, int shift, Supplier<String> g, Consumer<String> s) {
        return slider(label, 0, 255, 1,
                () -> (Config.rgb(g.get(), 0) >> shift) & 255,
                v -> {
                    int rgb = Config.rgb(g.get(), 0);
                    rgb = (rgb & ~(255 << shift)) | (((int) v & 255) << shift);
                    s.accept(String.format("#%06X", rgb));
                },
                v -> String.valueOf((int) v));
    }

    public static Opt slider(String label, double min, double max, double step,
                             DoubleSupplier g, DoubleConsumer s, DoubleFunction<String> fmt) {
        return (x, y, w) -> new ValueSlider(x, y, w, 20, label, min, max, step, g, s, fmt);
    }

    static final class ValueSlider extends SliderWidget {
        private final String label;
        private final double min, max, step;
        private final DoubleConsumer setter;
        private final DoubleFunction<String> fmt;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double step,
                    DoubleSupplier g, DoubleConsumer s, DoubleFunction<String> fmt) {
            super(x, y, w, h, Text.empty(), (Math.max(min, Math.min(max, g.getAsDouble())) - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = s;
            this.fmt = fmt;
            updateMessage();
        }

        private double current() {
            double v = min + value * (max - min);
            return Math.round(v / step) * step;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + fmt.apply(current())));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
