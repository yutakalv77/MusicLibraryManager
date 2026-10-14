package com.musiclibrary.service.cache;

import com.musiclibrary.model.Track;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, thread-safe LRU (Least Recently Used) in-memory cache for audio album artworks.
 * <p>
 * Decodes and smoothly scales images on demand, caching them by {@code (trackId, width, height)}
 * to deliver ultra-responsive UI playback and table rendering without blocking EDT or exhausting heap memory.
 */
public class ArtworkCache {

    public static final int DEFAULT_MAX_CAPACITY = 300;
    private static final String DEFAULT_PLACEHOLDER_ID = "__default_placeholder__";

    private static final ArtworkCache INSTANCE = new ArtworkCache();

    /**
     * Cache key combining identifier and target render dimensions.
     */
    public record CacheKey(String id, int width, int height) {
        public CacheKey {
            Objects.requireNonNull(id, "id cannot be null");
        }
    }

    private final int maxCapacity;
    private final Object lock = new Object();
    private final Map<CacheKey, Image> cache;

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    /**
     * Returns the global singleton instance with default maximum capacity.
     */
    public static ArtworkCache getInstance() {
        return INSTANCE;
    }

    /**
     * Constructs an ArtworkCache with default maximum capacity.
     */
    public ArtworkCache() {
        this(DEFAULT_MAX_CAPACITY);
    }

    /**
     * Constructs an ArtworkCache with the specified maximum capacity.
     *
     * @param maxCapacity maximum number of cached scaled images before LRU eviction
     */
    public ArtworkCache(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be greater than 0");
        }
        this.maxCapacity = maxCapacity;
        this.cache = new LinkedHashMap<>(Math.min(maxCapacity, 16), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, Image> eldest) {
                return size() > maxCapacity;
            }
        };
    }

    /**
     * Retrieves or decodes and scales artwork image for the given track.
     * If track is null or contains no artwork, returns a cached default placeholder image.
     *
     * @param track  track containing artwork bytes
     * @param width  target width in pixels
     * @param height target height in pixels
     * @return scaled Image instance
     */
    public Image getImage(Track track, int width, int height) {
        if (track == null) {
            return getDefaultImage(width, height);
        }
        return getImage(track.getId(), track.getArtworkData(), width, height);
    }

    /**
     * Retrieves or decodes and scales artwork binary data for the given track ID and dimensions.
     * If artwork data is null or empty, returns a cached default placeholder image.
     *
     * @param trackId     unique track identifier (or null/blank for data-derived key)
     * @param artworkData raw image binary data (JPEG / PNG)
     * @param width       target width in pixels
     * @param height      target height in pixels
     * @return scaled Image instance
     */
    public Image getImage(String trackId, byte[] artworkData, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive numbers");
        }

        if (artworkData == null || artworkData.length == 0) {
            return getDefaultImage(width, height);
        }

        String keyId = (trackId != null && !trackId.isBlank())
                ? trackId
                : "bytes_" + Integer.toHexString(Arrays.hashCode(artworkData));

        CacheKey key = new CacheKey(keyId, width, height);

        synchronized (lock) {
            Image cached = cache.get(key);
            if (cached != null) {
                hitCount.incrementAndGet();
                return cached;
            }
        }

        missCount.incrementAndGet();

        // Decode and scale image outside synchronized block to minimize lock contention
        Image scaled = decodeAndScale(artworkData, width, height);
        if (scaled == null) {
            return getDefaultImage(width, height);
        }

        synchronized (lock) {
            cache.put(key, scaled);
        }

        return scaled;
    }

    /**
     * Returns a cached default placeholder image for the specified dimensions.
     *
     * @param width  target width
     * @param height target height
     * @return placeholder Image
     */
    public Image getDefaultImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive numbers");
        }

        CacheKey key = new CacheKey(DEFAULT_PLACEHOLDER_ID, width, height);

        synchronized (lock) {
            Image cached = cache.get(key);
            if (cached != null) {
                hitCount.incrementAndGet();
                return cached;
            }
        }

        missCount.incrementAndGet();

        Image placeholder = createPlaceholderImage(width, height);

        synchronized (lock) {
            cache.put(key, placeholder);
        }

        return placeholder;
    }

    /**
     * Evicts all cached sizes of artwork for the specified track ID.
     * Call this when a track's metadata/artwork has been updated or removed.
     *
     * @param trackId unique track identifier
     */
    public void evict(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }

        synchronized (lock) {
            cache.keySet().removeIf(k -> trackId.equals(k.id()));
        }
    }

    /**
     * Clears all entries from the artwork cache.
     */
    public void clear() {
        synchronized (lock) {
            cache.clear();
        }
    }

    /**
     * Resets hit and miss counters to zero.
     */
    public void resetStats() {
        hitCount.set(0);
        missCount.set(0);
    }

    /**
     * Returns true if an entry for the specified ID and dimensions is currently cached.
     *
     * @param id     track ID or placeholder ID
     * @param width  width in pixels
     * @param height height in pixels
     * @return true if cached
     */
    public boolean contains(String id, int width, int height) {
        if (id == null) {
            return false;
        }
        synchronized (lock) {
            return cache.containsKey(new CacheKey(id, width, height));
        }
    }

    /**
     * Returns the cached image if already decoded and present in memory, otherwise null.
     * Does NOT perform decoding or disk I/O.
     *
     * @param track  track containing ID
     * @param width  target width
     * @param height target height
     * @return cached Image, or null if not cached
     */
    public Image getIfPresent(Track track, int width, int height) {
        if (track == null || width <= 0 || height <= 0) {
            return null;
        }
        return getIfPresent(track.getId(), width, height);
    }

    /**
     * Returns the cached image if already decoded and present in memory, otherwise null.
     * Does NOT perform decoding or disk I/O.
     *
     * @param id     track ID or placeholder ID
     * @param width  target width
     * @param height target height
     * @return cached Image, or null if not cached
     */
    public Image getIfPresent(String id, int width, int height) {
        if (id == null || width <= 0 || height <= 0) {
            return null;
        }
        synchronized (lock) {
            Image img = cache.get(new CacheKey(id, width, height));
            if (img != null) {
                hitCount.incrementAndGet();
            }
            return img;
        }
    }

    // --- Statistics & Metrics ---

    public int getCacheSize() {
        synchronized (lock) {
            return cache.size();
        }
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public double getHitRate() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    // --- Image Processing Helpers ---

    private Image decodeAndScale(byte[] artworkData, int width, int height) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(artworkData)) {
            BufferedImage original = ImageIO.read(bais);
            if (original == null) {
                return null;
            }

            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(original, 0, 0, width, height, null);
            } finally {
                g2.dispose();
            }
            return scaled;
        } catch (IOException e) {
            return null;
        }
    }

    private Image createPlaceholderImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // Subtle dark rounded rectangle background
            g2.setColor(new Color(60, 63, 65));
            int arc = Math.max(4, Math.min(width, height) / 5);
            g2.fillRoundRect(0, 0, width, height, arc, arc);

            // Border
            g2.setColor(new Color(90, 93, 95));
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);

            // Musical note icon
            int fontSize = Math.max(10, Math.min(width, height) / 2);
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontSize);
            g2.setFont(font);
            g2.setColor(new Color(160, 160, 160));
            FontMetrics fm = g2.getFontMetrics();
            String note = "🎵";
            int strWidth = fm.stringWidth(note);
            int strHeight = fm.getAscent();
            int x = (width - strWidth) / 2;
            int y = (height + strHeight) / 2 - 2;
            g2.drawString(note, x, y);
        } finally {
            g2.dispose();
        }
        return img;
    }
}
