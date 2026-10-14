package com.musiclibrary.service.cache;

import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtworkCacheTest {

    private ArtworkCache cache;

    @BeforeEach
    void setUp() {
        cache = new ArtworkCache(10);
    }

    private byte[] createTestImageBytes(int width, int height, Color color) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(color);
            g2.fillRect(0, 0, width, height);
            g2.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Singleton instance is non-null and preserves identity")
    void testSingletonInstance() {
        ArtworkCache instance1 = ArtworkCache.getInstance();
        ArtworkCache instance2 = ArtworkCache.getInstance();
        assertThat(instance1).isNotNull();
        assertThat(instance1).isSameAs(instance2);
        assertThat(instance1.getMaxCapacity()).isEqualTo(ArtworkCache.DEFAULT_MAX_CAPACITY);
    }

    @Test
    @DisplayName("Constructor rejects zero or negative capacity")
    void testConstructorValidation() {
        assertThatThrownBy(() -> new ArtworkCache(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ArtworkCache(-5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getImage and getDefaultImage reject non-positive dimensions")
    void testInvalidDimensions() {
        byte[] sample = createTestImageBytes(10, 10, Color.RED);
        assertThatThrownBy(() -> cache.getImage("t1", sample, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.getImage("t1", sample, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cache.getDefaultImage(0, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Cache hit returns same scaled image instance and increments hit count")
    void testCacheHitSameTrackAndSize() {
        byte[] artwork = createTestImageBytes(100, 100, Color.BLUE);
        Track track = Track.builder()
                .id("track-hit-1")
                .filePath("tracks/track-hit-1.mp3")
                .artworkData(artwork)
                .build();

        // 1st request: Miss
        Image image1 = cache.getImage(track, 50, 50);
        assertThat(image1).isNotNull();
        assertThat(cache.getMissCount()).isEqualTo(1);
        assertThat(cache.getHitCount()).isEqualTo(0);
        assertThat(cache.getCacheSize()).isEqualTo(1);

        // 2nd request: Hit
        Image image2 = cache.getImage(track, 50, 50);
        assertThat(image2).isSameAs(image1);
        assertThat(cache.getHitCount()).isEqualTo(1);
        assertThat(cache.getMissCount()).isEqualTo(1);
        assertThat(cache.getHitRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Different render dimensions for the same track produce separate cache entries")
    void testDifferentSizesProduceSeparateEntries() {
        byte[] artwork = createTestImageBytes(200, 200, Color.GREEN);
        Track track = Track.builder()
                .id("track-sizes-1")
                .filePath("tracks/track-sizes-1.mp3")
                .artworkData(artwork)
                .build();

        Image imgSmall = cache.getImage(track, 38, 38);
        Image imgLarge = cache.getImage(track, 150, 150);

        assertThat(imgSmall).isNotNull();
        assertThat(imgLarge).isNotNull();
        assertThat(imgSmall).isNotSameAs(imgLarge);
        assertThat(cache.getCacheSize()).isEqualTo(2);
        assertThat(cache.contains("track-sizes-1", 38, 38)).isTrue();
        assertThat(cache.contains("track-sizes-1", 150, 150)).isTrue();
    }

    @Test
    @DisplayName("Default placeholder image is returned for null or empty artwork and is cached")
    void testDefaultImageForNullOrEmptyArtwork() {
        Image def1 = cache.getImage((Track) null, 40, 40);
        assertThat(def1).isNotNull();

        Track trackNoArtwork = Track.builder().id("no-art").filePath("tracks/no-art.mp3").build();
        Image def2 = cache.getImage(trackNoArtwork, 40, 40);

        assertThat(def2).isSameAs(def1);
        assertThat(cache.getHitCount()).isEqualTo(1);

        Track trackEmptyBytes = Track.builder().id("empty-art").filePath("tracks/empty-art.mp3").artworkData(new byte[0]).build();
        Image def3 = cache.getImage(trackEmptyBytes, 40, 40);
        assertThat(def3).isSameAs(def1);
        assertThat(cache.getHitCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Corrupted image data gracefully returns default image")
    void testCorruptedArtworkReturnsDefaultImage() {
        byte[] corrupted = new byte[]{1, 2, 3, 4, 5};
        Image img = cache.getImage("corrupted-id", corrupted, 40, 40);

        assertThat(img).isNotNull();
        // Should return the cached placeholder image
        Image def = cache.getDefaultImage(40, 40);
        assertThat(img).isSameAs(def);
    }

    @Test
    @DisplayName("LRU eviction removes eldest entry when maximum capacity is exceeded")
    void testLruEviction() {
        ArtworkCache smallCache = new ArtworkCache(3);

        byte[] artA = createTestImageBytes(50, 50, Color.RED);
        byte[] artB = createTestImageBytes(50, 50, Color.GREEN);
        byte[] artC = createTestImageBytes(50, 50, Color.BLUE);
        byte[] artD = createTestImageBytes(50, 50, Color.YELLOW);

        // Fill cache with A, B, C
        smallCache.getImage("A", artA, 30, 30);
        smallCache.getImage("B", artB, 30, 30);
        smallCache.getImage("C", artC, 30, 30);

        assertThat(smallCache.getCacheSize()).isEqualTo(3);
        assertThat(smallCache.contains("A", 30, 30)).isTrue();
        assertThat(smallCache.contains("B", 30, 30)).isTrue();
        assertThat(smallCache.contains("C", 30, 30)).isTrue();

        // Access A again to make B the eldest (LRU)
        smallCache.getImage("A", artA, 30, 30);

        // Add D -> B should be evicted
        smallCache.getImage("D", artD, 30, 30);

        assertThat(smallCache.getCacheSize()).isEqualTo(3);
        assertThat(smallCache.contains("A", 30, 30)).isTrue();
        assertThat(smallCache.contains("C", 30, 30)).isTrue();
        assertThat(smallCache.contains("D", 30, 30)).isTrue();
        assertThat(smallCache.contains("B", 30, 30)).isFalse();
    }

    @Test
    @DisplayName("evict(trackId) removes all dimensions for the specified track")
    void testEvictSpecificTrack() {
        byte[] art1 = createTestImageBytes(50, 50, Color.MAGENTA);
        byte[] art2 = createTestImageBytes(50, 50, Color.CYAN);

        cache.getImage("track-1", art1, 38, 38);
        cache.getImage("track-1", art1, 150, 150);
        cache.getImage("track-2", art2, 38, 38);

        assertThat(cache.getCacheSize()).isEqualTo(3);

        cache.evict("track-1");

        assertThat(cache.getCacheSize()).isEqualTo(1);
        assertThat(cache.contains("track-1", 38, 38)).isFalse();
        assertThat(cache.contains("track-1", 150, 150)).isFalse();
        assertThat(cache.contains("track-2", 38, 38)).isTrue();

        // Safe no-op for null and non-existent IDs
        cache.evict(null);
        cache.evict("non-existent");
        assertThat(cache.getCacheSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("clear() empties cache completely")
    void testClear() {
        byte[] art = createTestImageBytes(50, 50, Color.WHITE);
        cache.getImage("t1", art, 30, 30);
        cache.getImage("t2", art, 30, 30);

        assertThat(cache.getCacheSize()).isEqualTo(2);

        cache.clear();

        assertThat(cache.getCacheSize()).isEqualTo(0);
        assertThat(cache.contains("t1", 30, 30)).isFalse();
        assertThat(cache.contains("t2", 30, 30)).isFalse();
    }

    @Test
    @DisplayName("resetStats() clears hit and miss counts")
    void testResetStats() {
        byte[] art = createTestImageBytes(50, 50, Color.BLACK);
        cache.getImage("t1", art, 30, 30); // miss
        cache.getImage("t1", art, 30, 30); // hit

        assertThat(cache.getHitCount()).isEqualTo(1);
        assertThat(cache.getMissCount()).isEqualTo(1);

        cache.resetStats();

        assertThat(cache.getHitCount()).isEqualTo(0);
        assertThat(cache.getMissCount()).isEqualTo(0);
        assertThat(cache.getHitRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Concurrent multithreaded access is thread-safe without exceptions or size overflow")
    void testConcurrentAccess() throws Exception {
        int threads = 8;
        int operationsPerThread = 50;
        int maxCapacity = 20;
        ArtworkCache concurrentCache = new ArtworkCache(maxCapacity);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        byte[] artA = createTestImageBytes(40, 40, Color.RED);
        byte[] artB = createTestImageBytes(40, 40, Color.BLUE);
        byte[] artC = createTestImageBytes(40, 40, Color.GREEN);

        for (int i = 0; i < threads; i++) {
            final int threadIndex = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String trackId = "track-" + ((threadIndex + j) % 30);
                        byte[] art = switch (j % 3) {
                            case 0 -> artA;
                            case 1 -> artB;
                            default -> artC;
                        };
                        int dim = (j % 2 == 0) ? 38 : 100;

                        concurrentCache.getImage(trackId, art, dim, dim);

                        if (j % 10 == 0) {
                            concurrentCache.evict("track-" + j);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        for (Future<?> f : futures) {
            f.get(); // verify no unexpected exceptions
        }

        assertThat(concurrentCache.getCacheSize()).isLessThanOrEqualTo(maxCapacity);
        assertThat(concurrentCache.getHitCount() + concurrentCache.getMissCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Cache smoothly renders and caches extreme dimensions: 1x1 and 512x512")
    void testExtremeResolutionsMinAndMax() {
        byte[] artwork = createTestImageBytes(64, 64, Color.ORANGE);

        // 1x1 minimum resolution
        Image img1x1 = cache.getImage("track-min", artwork, 1, 1);
        assertThat(img1x1).isNotNull();
        assertThat(img1x1.getWidth(null)).isEqualTo(1);
        assertThat(img1x1.getHeight(null)).isEqualTo(1);
        assertThat(cache.contains("track-min", 1, 1)).isTrue();

        // 512x512 large resolution
        Image img512 = cache.getImage("track-max", artwork, 512, 512);
        assertThat(img512).isNotNull();
        assertThat(img512.getWidth(null)).isEqualTo(512);
        assertThat(img512.getHeight(null)).isEqualTo(512);
        assertThat(cache.contains("track-max", 512, 512)).isTrue();

        // Placeholder at 1x1 and 512x512
        Image def1x1 = cache.getDefaultImage(1, 1);
        assertThat(def1x1).isNotNull();
        assertThat(def1x1.getWidth(null)).isEqualTo(1);
        assertThat(def1x1.getHeight(null)).isEqualTo(1);

        Image def512 = cache.getDefaultImage(512, 512);
        assertThat(def512).isNotNull();
        assertThat(def512.getWidth(null)).isEqualTo(512);
        assertThat(def512.getHeight(null)).isEqualTo(512);
    }

    @Test
    @DisplayName("Cache with capacity 1 immediately evicts previous entry on subsequent insertion")
    void testCapacityOneEvictionBehavior() {
        ArtworkCache singleCache = new ArtworkCache(1);
        byte[] artA = createTestImageBytes(30, 30, Color.RED);
        byte[] artB = createTestImageBytes(30, 30, Color.BLUE);

        // 1. Put A
        Image imgA = singleCache.getImage("A", artA, 30, 30);
        assertThat(singleCache.getCacheSize()).isEqualTo(1);
        assertThat(singleCache.contains("A", 30, 30)).isTrue();

        // 2. Put B -> A is immediately evicted
        Image imgB = singleCache.getImage("B", artB, 30, 30);
        assertThat(singleCache.getCacheSize()).isEqualTo(1);
        assertThat(singleCache.contains("B", 30, 30)).isTrue();
        assertThat(singleCache.contains("A", 30, 30)).isFalse();

        // 3. Request A again -> B is immediately evicted
        Image imgA2 = singleCache.getImage("A", artA, 30, 30);
        assertThat(singleCache.getCacheSize()).isEqualTo(1);
        assertThat(singleCache.contains("A", 30, 30)).isTrue();
        assertThat(singleCache.contains("B", 30, 30)).isFalse();
    }

    @Test
    @DisplayName("null or blank trackId derives hash key from bytes without throwing exceptions")
    void testTrackIdNullOrBlankDerivesHashKeySafely() {
        byte[] art = createTestImageBytes(20, 20, Color.PINK);

        // Null ID with artwork bytes
        Image imgNullId = cache.getImage(null, art, 20, 20);
        assertThat(imgNullId).isNotNull();
        assertThat(cache.getCacheSize()).isEqualTo(1);

        // Blank ID with same artwork bytes hits existing derived key
        Image imgBlankId = cache.getImage("   ", art, 20, 20);
        assertThat(imgBlankId).isSameAs(imgNullId);
        assertThat(cache.getHitCount()).isEqualTo(1);

        // Null ID with null artwork data returns placeholder
        Image placeholder = cache.getImage((String) null, null, 20, 20);
        assertThat(placeholder).isNotNull();
        assertThat(placeholder).isSameAs(cache.getDefaultImage(20, 20));
    }

    @Test
    @DisplayName("Access order is strictly maintained so frequently accessed entries survive eviction")
    void testAccessOrderStrictLruEviction() {
        ArtworkCache lruCache = new ArtworkCache(3);
        byte[] artA = createTestImageBytes(20, 20, Color.RED);
        byte[] artB = createTestImageBytes(20, 20, Color.GREEN);
        byte[] artC = createTestImageBytes(20, 20, Color.BLUE);
        byte[] artD = createTestImageBytes(20, 20, Color.YELLOW);

        // Insert A, B, C
        lruCache.getImage("A", artA, 20, 20);
        lruCache.getImage("B", artB, 20, 20);
        lruCache.getImage("C", artC, 20, 20);

        // Repeatedly access A and B so C remains the least recently accessed
        for (int i = 0; i < 5; i++) {
            lruCache.getImage("A", artA, 20, 20);
        }
        for (int i = 0; i < 3; i++) {
            lruCache.getImage("B", artB, 20, 20);
        }

        // Insert D -> C must be evicted
        lruCache.getImage("D", artD, 20, 20);

        assertThat(lruCache.getCacheSize()).isEqualTo(3);
        assertThat(lruCache.contains("A", 20, 20)).isTrue();
        assertThat(lruCache.contains("B", 20, 20)).isTrue();
        assertThat(lruCache.contains("D", 20, 20)).isTrue();
        assertThat(lruCache.contains("C", 20, 20)).isFalse();
    }

    @Test
    @DisplayName("Statistics correctly track hits, misses, and hit rate percentage")
    void testHitRateAndStatisticsAccuracy() {
        byte[] art = createTestImageBytes(20, 20, Color.BLACK);
        cache.resetStats();

        assertThat(cache.getHitCount()).isEqualTo(0);
        assertThat(cache.getMissCount()).isEqualTo(0);
        assertThat(cache.getHitRate()).isEqualTo(0.0);

        // 1st access: Miss
        cache.getImage("stat-1", art, 20, 20);
        assertThat(cache.getHitCount()).isEqualTo(0);
        assertThat(cache.getMissCount()).isEqualTo(1);
        assertThat(cache.getHitRate()).isEqualTo(0.0);

        // 2nd access: Hit
        cache.getImage("stat-1", art, 20, 20);
        assertThat(cache.getHitCount()).isEqualTo(1);
        assertThat(cache.getMissCount()).isEqualTo(1);
        assertThat(cache.getHitRate()).isEqualTo(0.5);

        // 3rd & 4th access: Hit, Hit (3 hits, 1 miss = 0.75 hit rate)
        cache.getImage("stat-1", art, 20, 20);
        cache.getImage("stat-1", art, 20, 20);
        assertThat(cache.getHitCount()).isEqualTo(3);
        assertThat(cache.getMissCount()).isEqualTo(1);
        assertThat(cache.getHitRate()).isEqualTo(0.75);
    }
}
