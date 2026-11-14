package dev.songlines.ui;

import dev.songlines.Config;
import dev.songlines.L;
import dev.songlines.Songlines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Настройки по вкладкам: Текст / Мир / Панель / Обложка / Прочее. Сохраняются при закрытии. */
public class SettingsScreen extends Screen {
    private final Screen parent;
    private int tab, page;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Songlines settings"));
        this.parent = parent;
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String sec(double v) {
        return String.format("%.1f s", v);
    }

    private List<Opts.Opt> options(int t) {
        Config c = Songlines.cfg;
        List<Opts.Opt> o = new ArrayList<>();
        switch (t) {
            case 0 -> {
                o.add(Opts.cycle(L.t("Subtitles", "Субтитры"), () -> c.subMode, v -> c.subMode = v));
                o.add(Opts.toggle(L.t("Download lyrics", "Загружать тексты"), () -> c.fetchLyrics, v -> c.fetchLyrics = v));
                o.add(Opts.slider(L.t("Sync offset", "Сдвиг синхронизации"), -3000, 3000, 100, () -> c.lyricsOffsetMs, v -> c.lyricsOffsetMs = (int) v, v -> (int) v + " ms"));
                o.add(Opts.color(L.t("Text color", "Цвет текста"), () -> c.textColor, v -> c.textColor = v));
                o.add(Opts.color(L.t("Active line color", "Цвет активной строки"), () -> c.activeColor, v -> c.activeColor = v));
                o.add(Opts.slider(L.t("Text size", "Размер текста"), 0.5, 3.0, 0.1, () -> c.textScale, v -> c.textScale = (float) v, v -> pct(v)));
                o.add(Opts.toggle(L.t("Shadow", "Тень"), () -> c.textShadow, v -> c.textShadow = v));
                o.add(Opts.toggle(L.t("Bold", "Жирный"), () -> c.textBold, v -> c.textBold = v));
                o.add(Opts.toggle(L.t("Background box", "Подложка"), () -> c.textBackground, v -> c.textBackground = v));
                o.add(Opts.toggle(L.t("Karaoke fill (bottom)", "Караоке-заливка (внизу)"), () -> c.karaokeWipe, v -> c.karaokeWipe = v));
                o.add(Opts.slider(L.t("Bottom height", "Высота снизу"), 30, 200, 2, () -> c.bottomOffset, v -> c.bottomOffset = (int) v, v -> (int) v + " px"));
            }
            case 1 -> {
                o.add(Opts.slider(L.t("Stay time", "Время показа"), 0.5, 10, 0.1, () -> c.holdSeconds, v -> c.holdSeconds = (float) v, SettingsScreen::sec));
                o.add(Opts.slider(L.t("Fade in", "Появление"), 0, 3, 0.1, () -> c.fadeInSeconds, v -> c.fadeInSeconds = (float) v, SettingsScreen::sec));
                o.add(Opts.slider(L.t("Fade out", "Исчезновение"), 0, 3, 0.1, () -> c.fadeOutSeconds, v -> c.fadeOutSeconds = (float) v, SettingsScreen::sec));
                o.add(Opts.cycle(L.t("Animation", "Анимация"), () -> c.anim, v -> c.anim = v));
                o.add(Opts.slider(L.t("Spread", "Разброс"), 0, 40, 1, () -> c.spreadDegrees, v -> c.spreadDegrees = (int) v, v -> (int) v + "°"));
                o.add(Opts.slider(L.t("Lines on screen", "Строк на экране"), 1, 6, 1, () -> c.maxFloating, v -> c.maxFloating = (int) v, v -> String.valueOf((int) v)));
            }
            case 2 -> {
                o.add(Opts.toggle(L.t("Right side", "Справа"), () -> c.panelRight, v -> c.panelRight = v));
                o.add(Opts.slider(L.t("Visible lines", "Видимых строк"), 3, 9, 2, () -> c.panelLines, v -> c.panelLines = (int) v, v -> String.valueOf((int) v)));
            }
            case 3 -> {
                o.add(Opts.cycle(L.t("Card", "Карточка"), () -> c.cardMode, v -> c.cardMode = v));
                o.add(Opts.slider(L.t("Show for", "Показывать"), 2, 20, 1, () -> c.cardSeconds, v -> c.cardSeconds = (float) v, v -> (int) v + " s"));
                o.add(Opts.cycle(L.t("Corner", "Угол"), () -> c.corner, v -> c.corner = v));
                o.add(Opts.cycle(L.t("Style", "Стиль"), () -> c.cardStyle, v -> c.cardStyle = v));
                o.add(Opts.cycle(L.t("Background", "Фон"), () -> c.cardColor, v -> c.cardColor = v));
                o.add(Opts.slider(L.t("Opacity", "Непрозрачность"), 10, 100, 5, () -> c.cardOpacity, v -> c.cardOpacity = (int) v, v -> (int) v + "%"));
                o.add(Opts.slider(L.t("Cover size", "Размер обложки"), 20, 96, 2, () -> c.coverSize, v -> c.coverSize = (int) v, v -> (int) v + " px"));
                o.add(Opts.toggle(L.t("Progress bar", "Полоса прогресса"), () -> c.showProgress, v -> c.showProgress = v));
                o.add(Opts.toggle(L.t("Border", "Рамка"), () -> c.showBorder, v -> c.showBorder = v));
                o.add(Opts.color(L.t("Border color", "Цвет рамки"), () -> c.borderColor, v -> c.borderColor = v));
                o.add(Opts.slider(L.t("Margin", "Отступ"), 0, 30, 1, () -> c.margin, v -> c.margin = (int) v, v -> (int) v + " px"));
            }
            default -> {
                o.add(Opts.slider(L.t("Volume", "Громкость"), 0, 1, 0.05, () -> c.volume, v -> c.volume = (float) v, SettingsScreen::pct));
                o.add(Opts.toggle(L.t("Shuffle", "Перемешать"), () -> c.shuffle, v -> c.shuffle = v));
                o.add(Opts.cycle(L.t("Repeat", "Повтор"), () -> c.repeat, v -> c.repeat = v));
            }
        }
        return o;
    }

    @Override
    protected void init() {
        String[] names = {L.t("Text", "Текст"), L.t("World", "Мир"), L.t("Panel", "Панель"), L.t("Cover", "Обложка"), L.t("More", "Ещё")};
        int tw = (width - 12) / names.length;
        for (int i = 0; i < names.length; i++) {
            final int ti = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((i == tab ? "[" : "") + names[i] + (i == tab ? "]" : "")), b -> {
                tab = ti;
                page = 0;
                clearAndInit();
            }).dimensions(6 + i * tw, 6, tw - 2, 20).build());
        }

        List<Opts.Opt> opts = options(tab);
        int perPage = Math.max(3, (height - 96) / 24);
        int pages = Math.max(1, (opts.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int rowW = Math.min(240, width - 20), x = (width - rowW) / 2, y = 34;
        for (int i = page * perPage; i < Math.min(opts.size(), (page + 1) * perPage); i++) {
            addDrawableChild(opts.get(i).make(x, y, rowW));
            y += 24;
        }
        if (pages > 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                page = Math.max(0, page - 1);
                clearAndInit();
            }).dimensions(x, height - 52, 30, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                page = Math.min(pages - 1, page + 1);
                clearAndInit();
            }).dimensions(x + rowW - 30, height - 52, 30, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Done", "Готово")), b -> close())
                .dimensions(x, height - 28, rowW, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int pages = Math.max(1, (options(tab).size() + Math.max(3, (height - 96) / 24) - 1) / Math.max(3, (height - 96) / 24));
        if (pages > 1) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal((page + 1) + " / " + pages), width / 2, height - 46, 0xFFC0C0C0);
        }
    }

    @Override
    public void removed() {
        Songlines.cfg.save();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
