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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance, thread-safe LRU (Least Recently Used) in-memory cache for audio album artworks.
 * Supports embedded artwork tags and folder artwork images (cover.jpg, folder.jpg, etc.) with
 * configurable source priority.
 * <p>
 * Decodes and smoothly scales images on demand, caching them by {@code (trackId, width, height)}
 * to deliver ultra-responsive UI playback and table rendering without blocking EDT or exhausting heap memory.
 */
public class ArtworkCache {

    public static final int DEFAULT_MAX_CAPACITY = 300;
    private static final String DEFAULT_PLACEHOLDER_ID = "__default_placeholder__";

    public static final List<String> CANDIDATE_NAMES = List.of(
            "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
            "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
            "front.jpg", "front.jpeg", "front.png",
            "albumart.jpg", "albumart.jpeg", "albumart.png",
            "album.jpg", "album.png"
    );

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
    private final Map<Path, Optional<byte[]>> folderArtworkCache = new ConcurrentHashMap<>();

    private volatile ArtworkPriority priority = ArtworkPriority.EMBEDDED_FIRST;

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
     * Returns the current artwork resolution priority.
     */
    public ArtworkPriority getPriority() {
        return priority;
    }

    /**
     * Sets the artwork resolution priority and clears cached images if priority changed.
     *
     * @param priority target priority
     */
    public void setPriority(ArtworkPriority priority) {
        ArtworkPriority target = (priority != null) ? priority : ArtworkPriority.EMBEDDED_FIRST;
        if (this.priority != target) {
            this.priority = target;
            clear();
        }
    }

    /**
     * Clears all cached folder artwork binary entries.
     */
    public void clearFolderCache() {
        folderArtworkCache.clear();
    }

    /**
     * Retrieves or decodes and scales artwork image for the given track according to the configured priority.
     * If track is null or contains no artwork, returns a cached default placeholder image.
     *
     * @param track  track containing metadata and file path
     * @param width  target width in pixels
     * @param height target height in pixels
     * @return scaled Image instance
     */
    public Image getImage(Track track, int width, int height) {
        if (track == null) {
            return getDefaultImage(width, height);
        }
        byte[] artworkData = resolveArtworkData(track);
        return getImage(track.getId(), artworkData, width, height);
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
     * Resolves external folder artwork bytes for the track's parent directory if present.
     *
     * @param track track containing file path
     * @return raw image bytes, or null if absent or not found
     */
    public byte[] getFolderArtworkData(Track track) {
        if (track == null || track.getFilePath() == null || track.getFilePath().isBlank()) {
            return null;
        }
        try {
            Path trackPath = Path.of(track.getFilePath());
            Path parentDir = trackPath.getParent();
            if (parentDir == null) {
                return null;
            }
            return getFolderArtworkData(parentDir);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolves external folder artwork bytes for the specified folder directory.
     * Results are cached by folder path to prevent repeated disk I/O.
     *
     * @param folder folder directory
     * @return raw image bytes, or null if absent or not found
     */
    public byte[] getFolderArtworkData(Path folder) {
        if (folder == null) {
            return null;
        }
        try {
            Path normalized = folder.toAbsolutePath().normalize();
            Optional<byte[]> cached = folderArtworkCache.computeIfAbsent(normalized, this::findFolderArtworkBytes);
            return cached.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if artwork is available for the given track under the current priority setting.
     */
    public boolean hasArtwork(Track track) {
        if (track == null) {
            return false;
        }
        return resolveArtworkData(track) != null;
    }

    /**
     * Resolves raw artwork binary data for a track according to the active priority.
     */
    public byte[] resolveArtworkData(Track track) {
        if (track == null) {
            return null;
        }
        byte[] embedded = track.getArtworkData();
        boolean hasEmbedded = (embedded != null && embedded.length > 0);

        return switch (priority) {
            case EMBEDDED_FIRST -> {
                if (hasEmbedded) {
                    yield embedded;
                }
                yield getFolderArtworkData(track);
            }
            case FOLDER_FIRST -> {
                byte[] folderData = getFolderArtworkData(track);
                if (folderData != null && folderData.length > 0) {
                    yield folderData;
                }
                yield hasEmbedded ? embedded : null;
            }
            case EMBEDDED_ONLY -> hasEmbedded ? embedded : null;
            case FOLDER_ONLY -> getFolderArtworkData(track);
        };
    }

    private Optional<byte[]> findFolderArtworkBytes(Path folder) {
        try {
            if (folder == null || !Files.isDirectory(folder)) {
                return Optional.empty();
            }

            Map<String, Path> lowerMap = new HashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                        lowerMap.putIfAbsent(name, entry);
                    }
                }
            }

            for (String candidate : CANDIDATE_NAMES) {
                Path match = lowerMap.get(candidate.toLowerCase(Locale.ROOT));
                if (match != null) {
                    try {
                        byte[] bytes = Files.readAllBytes(match);
                        if (bytes != null && bytes.length > 0) {
                            return Optional.of(bytes);
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
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
     * Clears all entries from the scaled image cache and the folder artwork cache.
     */
    public void clear() {
        synchronized (lock) {
            cache.clear();
        }
        clearFolderCache();
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
