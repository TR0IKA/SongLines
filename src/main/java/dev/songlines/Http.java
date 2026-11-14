package dev.songlines;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** HTTP только через java.base (HttpURLConnection) — работает и в урезанных JRE на Android. */
public final class Http {
    public static byte[] getBytes(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = open(url, timeoutMs);
        try {
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            c.disconnect();
        }
    }

    public static String get(String url, int timeoutMs) throws IOException {
        byte[] b = getBytes(url, timeoutMs);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    public static void post(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = open(url, timeoutMs);
        try {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.getOutputStream().close();
            c.getResponseCode();
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "Songlines/0.1 (Minecraft Fabric mod)");
        return c;
    }
}
