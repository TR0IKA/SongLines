package dev.songlines;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;

/** Все настройки. Хранятся в songlines/config.json (цвета можно править руками: "#RRGGBB"). */
public final class Config {
    public enum SubMode { BOTTOM, WORLD, ENTITY, PANEL, OFF }
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public enum CardStyle { CARD, COMPACT, COVER_ONLY }
    public enum CardMode { ALWAYS, ON_CHANGE, OFF }
    public enum CardColor { AUTO, DARK, CUSTOM }
    public enum Anim { FADE, RISE, POP, SLIDE, TYPEWRITER, NONE }
    public enum Repeat { OFF, ALL, ONE }
    public enum Source { LOCAL, BRIDGE }
    public enum Facing { PLAYER, FIXED }
    public enum EntityAnim { FADE, POP, TYPEWRITER, NONE }
    public enum ColorMode { CUSTOM, COVER }

    // воспроизведение
    public Source source = Source.LOCAL;
    public float volume = 0.8f;
    public boolean shuffle = false;
    public Repeat repeat = Repeat.ALL;
    public String lastFolder = "";

    // субтитры (общие)
    public SubMode subMode = SubMode.BOTTOM;
    public boolean fetchLyrics = true;
    public int lyricsOffsetMs = 0;
    public String textColor = "#FFFFFF";
    public String activeColor = "#FFD166";
    public float textScale = 1.0f;
    public boolean textShadow = true;
    public boolean textBold = false;
    public boolean textBackground = false;
    public boolean karaokeWipe = true;
    public int wordsPerChunk = 0;          // 0 = строка целиком, иначе по N слов
    public ColorMode colorMode = ColorMode.CUSTOM;
    public boolean fetchCovers = true;
    public int bottomOffset = 68;
    public String font = "default"; // "default", "minecraft:uniform" или "songlines:<имя файла из songlines/fonts>"

    // всплывающие в мире
    public float holdSeconds = 3.0f;
    public float fadeInSeconds = 0.4f;
    public float fadeOutSeconds = 0.6f;
    public Anim anim = Anim.RISE;
    public int spreadDegrees = 18;
    public int maxFloating = 3;

    // "сущность": текст стоит на месте в мире и не двигается
    public float entityDistance = 3.5f;
    public Facing entityFacing = Facing.PLAYER; // PLAYER — всегда лицом к игроку, FIXED — лицом к игроку в момент появления
    public EntityAnim entityAnim = EntityAnim.FADE;
    public boolean entityHideBehindBlocks = true;
    public boolean entityScaleByDistance = true;

    // панель сбоку
    public boolean panelRight = false;
    public int panelLines = 5;

    // обложка / карточка
    public CardMode cardMode = CardMode.ALWAYS;
    public float cardSeconds = 6f;
    public Corner corner = Corner.TOP_RIGHT;
    public CardStyle cardStyle = CardStyle.CARD;
    public CardColor cardColor = CardColor.AUTO;
    public String cardCustomColor = "#1E1E2E";
    public int cardOpacity = 80;
    public int coverSize = 40;
    public boolean showProgress = true;
    public boolean showBorder = false;
    public String borderColor = "#FFFFFF";
    /** Цвет рамки: свой или основной цвет обложки. */
    public ColorMode borderMode = ColorMode.COVER;
    public int margin = 6;

    // мост (приложение на телефоне/ПК, сообщает что играет)
    public int bridgePort = 38917;

    public static final String[] PALETTE = {
            "#FFFFFF", "#FFD166", "#06D6A0", "#4CC9F0", "#F72585", "#FF9F1C", "#B5179E", "#A0A0A0", "#000000"
    };

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Цвет активной строки: свой или основной цвет обложки. */
    public int activeRgb() {
        return colorMode == ColorMode.COVER ? CoverTexture.accent() : rgb(activeColor, 0xFFD166);
    }

    /** Цвет остального текста: свой или светлый оттенок цвета обложки. */
    public int baseRgb() {
        return colorMode == ColorMode.COVER ? mix(CoverTexture.accent(), 0xFFFFFF, 0.78f) : rgb(textColor, 0xFFFFFF);
    }

    /** Цвет рамки карточки: свой или основной цвет обложки. */
    public int borderRgb() {
        return borderMode == ColorMode.COVER ? CoverTexture.accent() : rgb(borderColor, 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int rgb(String hex, int def) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            return Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return def;
        }
    }

    public static String nextColor(String cur) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i].equalsIgnoreCase(cur)) return PALETTE[(i + 1) % PALETTE.length];
        }
        return PALETTE[0];
    }

    public static <E extends Enum<E>> E next(E e) {
        E[] v = e.getDeclaringClass().getEnumConstants();
        return v[(e.ordinal() + 1) % v.length];
    }

    public static Config load() {
        try {
            if (Files.exists(Dirs.config)) {
                Config c = GSON.fromJson(Files.readString(Dirs.config), Config.class);
                if (c != null) return c.fixed();
            }
        } catch (Exception e) {
            Songlines.LOG.warn("config load failed, using defaults", e);
        }
        return new Config();
    }

    /** Защита от null после ручной правки файла. */
    private Config fixed() {
        Config d = new Config();
        if (source == null) source = d.source;
        if (repeat == null) repeat = d.repeat;
        if (subMode == null) subMode = d.subMode;
        if (anim == null) anim = d.anim;
        if (cardMode == null) cardMode = d.cardMode;
        if (corner == null) corner = d.corner;
        if (cardStyle == null) cardStyle = d.cardStyle;
        if (cardColor == null) cardColor = d.cardColor;
        if (lastFolder == null) lastFolder = "";
        if (entityFacing == null) entityFacing = d.entityFacing;
        if (entityAnim == null) entityAnim = d.entityAnim;
        if (colorMode == null) colorMode = d.colorMode;
        if (borderMode == null) borderMode = d.borderMode;
        return this;
    }

    public void save() {
        try {
            Files.writeString(Dirs.config, GSON.toJson(this));
        } catch (IOException e) {
            Songlines.LOG.warn("config save failed", e);
        }
    }
}
