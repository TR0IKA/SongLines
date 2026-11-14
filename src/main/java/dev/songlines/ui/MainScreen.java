package dev.songlines.ui;

import dev.songlines.Config;
import dev.songlines.Dirs;
import dev.songlines.L;
import dev.songlines.NowPlaying;
import dev.songlines.Playback;
import dev.songlines.Songlines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class MainScreen extends Screen {
    public MainScreen() {
        super(Text.literal("Songlines"));
    }

    private ButtonWidget btn(String label, int x, int y, int w, Runnable r) {
        return ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 20).build();
    }

    @Override
    protected void init() {
        Config c = Songlines.cfg;
        Playback p = Songlines.playback;
        int cx = width / 2;
        int y = Math.max(40, height / 2 - 62);

        addDrawableChild(btn("<<", cx - 100, y, 50, p::prev));
        addDrawableChild(btn(L.t("Play / Pause", "Пуск / Пауза"), cx - 48, y, 96, p::togglePause));
        addDrawableChild(btn(">>", cx + 50, y, 50, p::next));
        y += 24;

        addDrawableChild(btn("★ " + L.t("Favorite", "В избранное"), cx - 100, y, 98, p::toggleFavorite));
        addDrawableChild(ButtonWidget.builder(shuffleText(), b -> {
            c.shuffle = !c.shuffle;
            b.setMessage(shuffleText());
        }).dimensions(cx + 2, y, 98, 20).build());
        y += 24;

        addDrawableChild(ButtonWidget.builder(repeatText(), b -> {
            c.repeat = Config.next(c.repeat);
            b.setMessage(repeatText());
        }).dimensions(cx - 100, y, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(sourceText(), b -> {
            p.setSource(Config.next(c.source));
            b.setMessage(sourceText());
        }).dimensions(cx + 2, y, 98, 20).build());
        y += 24;

        addDrawableChild(btn(L.t("Library", "Библиотека"), cx - 100, y, 98, () -> client.setScreen(new LibraryScreen(this))));
        addDrawableChild(btn(L.t("Settings", "Настройки"), cx + 2, y, 98, () -> client.setScreen(new SettingsScreen(this))));
        y += 24;

        addDrawableChild(btn(L.t("Open music folder", "Открыть папку с музыкой"), cx - 100, y, 200, this::openFolder));
        y += 24;
        addDrawableChild(btn(L.t("Done", "Готово"), cx - 100, y, 200, this::close));
    }

    private Text shuffleText() {
        return Text.literal(L.t("Shuffle: ", "Перемешать: ") + (Songlines.cfg.shuffle ? L.t("On", "Вкл") : L.t("Off", "Выкл")));
    }

    private Text repeatText() {
        return Text.literal(L.t("Repeat: ", "Повтор: ") + Opts.nice(Songlines.cfg.repeat));
    }

    private Text sourceText() {
        return Text.literal(L.t("Source: ", "Источник: ") + Opts.nice(Songlines.cfg.source));
    }

    private void openFolder() {
        try {
            Util.getOperatingSystem().open(Dirs.music.toUri());
        } catch (Throwable ignored) {
            // на Android (Zalith) системного проводника может не быть — путь показан на экране
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        NowPlaying np = Songlines.playback.np;
        String line = np.active ? np.title + (np.artist.isEmpty() ? "" : " — " + np.artist) : L.t("Nothing is playing", "Ничего не играет");
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(line, width - 20)), width / 2, 14, 0xFFFFFFFF);
        if (Songlines.cfg.source == Config.Source.BRIDGE) {
            String st = Songlines.playback.bridge.connected ? L.t("Bridge: connected", "Мост: подключён") : L.t("Bridge: not found", "Мост: не найден");
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(st), width / 2, 26, Songlines.playback.bridge.connected ? 0xFF7CFC9A : 0xFFFF8A8A);
        }
        String path = textRenderer.trimToWidth(Dirs.music.toString(), width - 10);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(path), width / 2, height - 12, 0xFFA0A0A0);
    }
}
