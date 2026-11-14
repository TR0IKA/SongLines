package dev.songlines;

import dev.songlines.audio.AudioPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** Центральный контроллер: очередь, переключение источника (локальные файлы / мост), избранное. */
public final class Playback {
    public final Library library = new Library();
    public final Favorites favorites = new Favorites();
    public final NowPlaying np = new NowPlaying();
    public final AudioPlayer audio = new AudioPlayer();
    public final BridgeClient bridge = new BridgeClient(np);

    private final Random rnd = new Random();
    private List<Track> queue = new ArrayList<>();
    private final Deque<Integer> history = new ArrayDeque<>();
    private int qi = -1;
    public Track current;
    private int lastSerial = -1;

    public Playback() {
        audio.onEnded = () -> MinecraftClient.getInstance().execute(this::onEnded);
        audio.onError = msg -> MinecraftClient.getInstance().execute(() -> say(L.t("Audio error: ", "Ошибка аудио: ") + msg));
        CompletableFuture.runAsync(library::rescan);
        if (Songlines.cfg.source == Config.Source.BRIDGE) bridge.start();
    }

    public static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    // ---------- источник ----------

    public void setSource(Config.Source s) {
        Songlines.cfg.source = s;
        current = null;
        audio.stop();
        np.clear();
        if (s == Config.Source.BRIDGE) bridge.start();
        else bridge.stop();
    }

    // ---------- локальное воспроизведение ----------

    public void playFolder(Library.Folder f, int index) {
        if (f.tracks.isEmpty()) return;
        if (Songlines.cfg.source != Config.Source.LOCAL) setSource(Config.Source.LOCAL);
        queue = new ArrayList<>(f.tracks);
        history.clear();
        qi = Math.max(0, Math.min(index, queue.size() - 1));
        Songlines.cfg.lastFolder = f.name;
        playCurrent();
    }

    private void playCurrent() {
        Track t = queue.get(qi);
        current = t;
        np.begin(t.title, t.artist, L.t("Local", "Локально"));
        final int ser = np.serial;
        final Path path = t.path;
        CompletableFuture.supplyAsync(() -> {
            byte[] c = Tags.scan(path, true).cover;
            return c != null ? c : Tags.folderCover(path);
        }).thenAccept(c -> {
            if (np.serial == ser) CoverTexture.set(c);
        });
        audio.play(path);
    }

    private void onEnded() {
        if (Songlines.cfg.source != Config.Source.LOCAL || queue.isEmpty()) return;
        Config.Repeat r = Songlines.cfg.repeat;
        if (r == Config.Repeat.ONE) {
            playCurrent();
        } else if (!Songlines.cfg.shuffle && r == Config.Repeat.OFF && qi >= queue.size() - 1) {
            audio.stop();
        } else {
            next();
        }
    }

    public void next() {
        if (Songlines.cfg.source == Config.Source.BRIDGE) {
            bridge.command("next");
            return;
        }
        if (queue.isEmpty()) return;
        history.push(qi);
        if (history.size() > 100) history.removeLast();
        if (Songlines.cfg.shuffle && queue.size() > 1) {
            int n;
            do {
                n = rnd.nextInt(queue.size());
            } while (n == qi);
            qi = n;
        } else {
            qi = (qi + 1) % queue.size();
        }
        playCurrent();
    }

    public void prev() {
        if (Songlines.cfg.source == Config.Source.BRIDGE) {
            bridge.command("prev");
            return;
        }
        if (queue.isEmpty()) return;
        if (np.positionMs() > 3000) {
            // больше трёх секунд — просто начать трек заново
        } else if (!history.isEmpty()) {
            qi = history.pop();
        } else {
            qi = (qi - 1 + queue.size()) % queue.size();
        }
        playCurrent();
    }

    public void togglePause() {
        if (Songlines.cfg.source == Config.Source.BRIDGE) bridge.command("playpause");
        else if (audio.active) audio.togglePause();
    }

    public void toggleFavorite() {
        if (Songlines.cfg.source != Config.Source.LOCAL || current == null) {
            say(L.t("Favorites work for local tracks only (for now)", "Избранное пока только для локальных треков"));
            return;
        }
        boolean now = favorites.toggle(current);
        say((now ? "★ " : "☆ ") + current.title);
    }

    public void toggleFavorite(Track t) {
        favorites.toggle(t);
    }

    // ---------- такт ----------

    public void tick(MinecraftClient mc) {
        Config c = Songlines.cfg;
        audio.volume = c.volume * mc.options.getSoundVolume(SoundCategory.MASTER);
        if (c.source == Config.Source.LOCAL && np.active) {
            np.sync(audio.playing, audio.positionMs, audio.durationMs);
        }
        np.favorite = c.source == Config.Source.LOCAL && current != null && favorites.has(current);
        if (np.serial != lastSerial) {
            lastSerial = np.serial;
            Subtitles.reset();
            if (np.active) requestLyrics();
        }
    }

    private void requestLyrics() {
        final int ser = np.serial;
        Path file = (Songlines.cfg.source == Config.Source.LOCAL && current != null) ? current.path : null;
        LyricsFetcher.request(np.title, np.artist, np.durationMs, file, Songlines.cfg.fetchLyrics,
                lines -> MinecraftClient.getInstance().execute(() -> {
                    if (np.serial == ser) {
                        np.lyrics = lines;
                        Subtitles.reset();
                    }
                }));
    }

    public void shutdown() {
        bridge.stop();
        audio.shutdown();
    }
}
