package dev.songlines.ui;

import dev.songlines.L;
import dev.songlines.Library;
import dev.songlines.Playback;
import dev.songlines.Songlines;
import dev.songlines.Track;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Список папок -> список треков. Постраничный (надёжно на маленьких экранах телефона). Файлы можно перетащить в окно (ПК). */
public class LibraryScreen extends Screen {
    private final Screen parent;
    private Library.Folder open; // null = список папок
    private int page;
    private TextFieldWidget nameField;

    public LibraryScreen(Screen parent) {
        super(Text.literal("Library"));
        this.parent = parent;
    }

    private List<Library.Folder> allFolders() {
        List<Library.Folder> l = new ArrayList<>();
        l.add(Songlines.playback.library.favorites(Songlines.playback.favorites));
        l.addAll(Songlines.playback.library.folders());
        return l;
    }

    private ButtonWidget btn(String label, int x, int y, int w, Runnable r) {
        return ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 20).build();
    }

    @Override
    protected void init() {
        int perPage = Math.max(3, Math.min(8, (height - 118) / 22));
        int rowW = Math.min(260, width - 20);
        int x0 = (width - rowW) / 2;
        int y = 28;
        int total;

        if (open == null) {
            List<Library.Folder> fs = allFolders();
            total = fs.size();
            page = Math.max(0, Math.min(page, (Math.max(1, total) - 1) / perPage));
            for (int i = page * perPage; i < Math.min(total, (page + 1) * perPage); i++) {
                Library.Folder f = fs.get(i);
                addDrawableChild(btn(f.name + "  (" + f.tracks.size() + ")", x0, y, rowW, () -> {
                    open = f;
                    page = 0;
                    clearAndInit();
                }));
                y += 22;
            }
            nameField = new TextFieldWidget(textRenderer, x0, height - 52, rowW - 84, 20, Text.literal(""));
            nameField.setPlaceholder(Text.literal(L.t("New folder name", "Имя новой папки")));
            nameField.setMaxLength(40);
            addDrawableChild(nameField);
            addDrawableChild(btn(L.t("Create", "Создать"), x0 + rowW - 80, height - 52, 80, this::createFolder));
        } else {
            List<Track> ts = open.tracks;
            total = ts.size();
            page = Math.max(0, Math.min(page, (Math.max(1, total) - 1) / perPage));
            for (int i = page * perPage; i < Math.min(total, (page + 1) * perPage); i++) {
                final int idx = i;
                Track t = ts.get(i);
                String label = t.title + (t.artist.isEmpty() ? "" : " — " + t.artist);
                addDrawableChild(btn("▶ " + textRenderer.trimToWidth(label, rowW - 50), x0, y, rowW - 26,
                        () -> Songlines.playback.playFolder(open, idx)));
                boolean fav = Songlines.playback.favorites.has(t);
                addDrawableChild(ButtonWidget.builder(Text.literal(fav ? "★" : "☆"), b -> {
                    Songlines.playback.toggleFavorite(t);
                    b.setMessage(Text.literal(Songlines.playback.favorites.has(t) ? "★" : "☆"));
                }).dimensions(x0 + rowW - 22, y, 22, 20).build());
                y += 22;
            }
            addDrawableChild(btn(L.t("Play all", "Играть всё"), x0, height - 52, rowW, () -> Songlines.playback.playFolder(open, 0)));
        }

        int pages = Math.max(1, (total + perPage - 1) / perPage);
        addDrawableChild(btn("<", x0, height - 74, 30, () -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }));
        addDrawableChild(btn(">", x0 + rowW - 30, height - 74, 30, () -> {
            page = Math.min(pages - 1, page + 1);
            clearAndInit();
        }));
        addDrawableChild(btn(open == null ? L.t("Back", "Назад") : L.t("Folders", "Папки"), x0, height - 28, rowW, () -> {
            if (open == null) client.setScreen(parent);
            else {
                open = null;
                page = 0;
                clearAndInit();
            }
        }));
    }

    private void createFolder() {
        String n = nameField.getText();
        CompletableFuture.supplyAsync(() -> Songlines.playback.library.createFolder(n)).thenAccept(ok ->
                MinecraftClient.getInstance().execute(() -> {
                    Playback.say(ok ? L.t("Folder created", "Папка создана") : L.t("Bad folder name", "Неверное имя папки"));
                    if (MinecraftClient.getInstance().currentScreen == this) clearAndInit();
                }));
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        final String dest = open == null || open.virtual ? null : open.name;
        CompletableFuture.supplyAsync(() -> Songlines.playback.library.importFiles(paths, dest)).thenAccept(n ->
                MinecraftClient.getInstance().execute(() -> {
                    Playback.say(L.t("Imported files: ", "Импортировано файлов: ") + n);
                    if (MinecraftClient.getInstance().currentScreen == this) clearAndInit();
                }));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        String head = open == null ? L.t("Folders", "Папки") : open.name;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(head), width / 2, 12, 0xFFFFFFFF);
        if (open == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(L.t("Drop mp3/ogg files into this window to import", "Перетащи mp3/ogg в окно, чтобы добавить")),
                    width / 2, height - 12, 0xFF909090);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
