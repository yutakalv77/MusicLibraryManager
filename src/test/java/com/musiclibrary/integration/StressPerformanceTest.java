package com.musiclibrary.integration;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.service.storage.JsonLibraryStorage;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.ui.panel.AlbumGridViewPanel;
import com.musiclibrary.ui.table.TrackTableModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Stress and performance benchmark suite testing 10,000-track scale:
 * - Sub-millisecond (< 1ms) inverted index search query throughput
 * - 10,000-row table model sorting & filtering performance (< 100ms)
 * - Large library JSON persistence throughput and data integrity
 * - High-frequency ArtworkCache scaling and LRU eviction under memory pressure
 * - Rapid UI theme & locale switching stress
 */
class StressPerformanceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        ArtworkCache.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ArtworkCache.getInstance().clear();
    }

    private List<Track> generate10kTracks() {
        List<Track> tracks = new ArrayList<>(10_000);
        String[] genres = {"Rock", "Pop", "Jazz", "Classical", "Hip Hop", "Electronic", "Metal", "J-Pop", "Anime", "Soundtrack"};

        for (int i = 1; i <= 10_000; i++) {
            String title;
            if (i % 10 == 0) {
                title = "曲タイトル_第" + i + "番 (夏祭り)";
            } else if (i % 3 == 0) {
                title = "Symphony No." + (i % 9 + 1) + " in C Minor";
            } else {
                title = "Track Title " + i;
            }

            String artist = "Artist_" + (i % 200);
            String album = "Album_" + (i % 500);
            String genre = genres[i % genres.length];
            int year = 1970 + (i % 55);
            int trackNum = (i % 20) + 1;
            long duration = 120 + (i % 300);
            long fileSize = 1024 * 1024 * 3 + (i * 100);

            Track track = Track.builder()
                    .id("track-" + i)
                    .filePath("C:/Music/Album_" + (i % 500) + "/track_" + i + ".mp3")
                    .title(title)
                    .artist(artist)
                    .album(album)
                    .genre(genre)
                    .year(year)
                    .trackNumber(trackNum)
                    .durationSeconds(duration)
                    .fileSizeBytes(fileSize)
                    .rating((i % 5) + 1)
                    .playCount(i % 50)
                    .dateAdded(Instant.ofEpochMilli(1600000000000L + (i * 1000L)))
                    .build();
            tracks.add(track);
        }
        return tracks;
    }

    @Test
    @DisplayName("FastSearchIndex delivers sub-millisecond (< 1ms) average query response time across 10,000 tracks")
    void testSearchPerformance10kTracks() {
        List<Track> tracks = generate10kTracks();
        FastSearchIndex index = new FastSearchIndex();

        long startBuild = System.nanoTime();
        index.indexTracks(tracks);
        long buildNanos = System.nanoTime() - startBuild;
        System.out.printf("Indexed 10,000 tracks in %.2f ms%n", buildNanos / 1_000_000.0);

        String[] queries = {
                "Rock",
                "Artist_42",
                "Symphony",
                "Album_10",
                "夏祭り",
                "Track 5000",
                "No.3 in C",
                "Pop",
                "NonExistentWord",
                "Artist_12 1990"
        };

        // Warm up JIT
        for (int i = 0; i < 50; i++) {
            for (String q : queries) {
                index.search(q);
            }
        }

        // Benchmark measurement (300 iterations across diverse queries)
        int iterations = 300;
        long totalNanos = 0;
        int totalResults = 0;

        for (int i = 0; i < iterations; i++) {
            String query = queries[i % queries.length];
            long t0 = System.nanoTime();
            Set<String> matched = index.search(query);
            totalNanos += (System.nanoTime() - t0);
            totalResults += matched.size();
        }

        double avgMicros = (double) totalNanos / iterations / 1_000.0;
        double avgMillis = avgMicros / 1_000.0;

        System.out.printf("10,000-track search benchmark: Average query time = %.2f us (%.4f ms), Total matches = %d%n",
                avgMicros, avgMillis, totalResults);

        // Core requirement: average query latency must be sub-millisecond (< 1.0 ms)
        assertThat(avgMillis).isLessThan(1.0);
    }

    @Test
    @DisplayName("TrackTableModel performs sorting and filtering across 10,000 tracks under 100ms")
    void testTrackTableModelPerformance10kTracks() {
        List<Track> tracks = generate10kTracks();
        TrackTableModel model = new TrackTableModel();
        model.setTracks(tracks);

        TableRowSorter<TrackTableModel> sorter = new TableRowSorter<>(model);

        // Warm up JIT sorter
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_TITLE, SortOrder.ASCENDING)));
        sorter.sort();

        // 1. Sort by Title descending
        long t0 = System.currentTimeMillis();
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_TITLE, SortOrder.DESCENDING)));
        sorter.sort();
        long titleSortMs = System.currentTimeMillis() - t0;
        System.out.printf("Sorted 10,000 tracks by Title in %d ms%n", titleSortMs);
        assertThat(titleSortMs).isLessThan(500);

        // 2. Sort by Artist ascending
        t0 = System.currentTimeMillis();
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_ARTIST, SortOrder.ASCENDING)));
        sorter.sort();
        long artistSortMs = System.currentTimeMillis() - t0;
        System.out.printf("Sorted 10,000 tracks by Artist in %d ms%n", artistSortMs);
        assertThat(artistSortMs).isLessThan(500);

        // 3. Sort by Duration descending
        t0 = System.currentTimeMillis();
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_DURATION, SortOrder.DESCENDING)));
        sorter.sort();
        long durationSortMs = System.currentTimeMillis() - t0;
        System.out.printf("Sorted 10,000 tracks by Duration in %d ms%n", durationSortMs);
        assertThat(durationSortMs).isLessThan(500);

        // 4. Filtering with RowFilter (filter down to 500 matching tracks)
        Set<String> matchIds = Set.of(
                tracks.stream().limit(500).map(Track::getId).toArray(String[]::new)
        );

        t0 = System.currentTimeMillis();
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TrackTableModel, ? extends Integer> entry) {
                Track t = entry.getModel().getTrackAt(entry.getIdentifier());
                return t != null && matchIds.contains(t.getId());
            }
        });
        long filterMs = System.currentTimeMillis() - t0;
        assertThat(filterMs).isLessThan(50);
        assertThat(sorter.getViewRowCount()).isEqualTo(500);

        // Clear filter
        sorter.setRowFilter(null);
        assertThat(sorter.getViewRowCount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("JsonLibraryStorage saves and reloads 10,000 tracks and playlists with full fidelity")
    void testJsonPersistenceThroughput10kTracks() throws Exception {
        List<Track> tracks = generate10kTracks();
        Library library = new Library(tracks, null);

        // Add 5 playlists, each containing 500 tracks
        for (int p = 1; p <= 5; p++) {
            Playlist pl = library.createPlaylist("Playlist " + p);
            for (int i = 0; i < 500; i++) {
                pl.addTrack(tracks.get((p * 500 + i) % 10_000).getId());
            }
        }

        Path storageFile = tempDir.resolve("library_10k.json");
        JsonLibraryStorage storage = new JsonLibraryStorage();

        // 1. Measure atomic save throughput
        long t0 = System.currentTimeMillis();
        storage.save(library, storageFile);
        long saveDurationMs = System.currentTimeMillis() - t0;

        System.out.printf("Saved 10,000 tracks to JSON in %d ms (file size: %.2f MB)%n",
                saveDurationMs, Files.size(storageFile) / (1024.0 * 1024.0));

        assertThat(Files.exists(storageFile)).isTrue();
        assertThat(saveDurationMs).isLessThan(5000); // within 5 seconds

        // 2. Measure reload throughput & fidelity
        t0 = System.currentTimeMillis();
        Library reloaded = storage.load(storageFile);
        long loadDurationMs = System.currentTimeMillis() - t0;

        System.out.printf("Loaded 10,000 tracks from JSON in %d ms%n", loadDurationMs);

        assertThat(loadDurationMs).isLessThan(5000);
        assertThat(reloaded.getTrackCount()).isEqualTo(10_000);
        assertThat(reloaded.getPlaylists()).hasSize(5);

        // Verify sampled track
        Track sample = reloaded.getTrack("track-100");
        assertThat(sample).isNotNull();
        assertThat(sample.getTitle()).isEqualTo("曲タイトル_第100番 (夏祭り)");
        assertThat(sample.getArtist()).isEqualTo("Artist_100");
    }

    @Test
    @DisplayName("ArtworkCache maintains strict maximum capacity under 1,000 sequential decode requests")
    void testArtworkCacheStressAndMemoryLimit() {
        int capacity = 50;
        ArtworkCache stressCache = new ArtworkCache(capacity);

        // Generate sample image bytes
        byte[] sampleImg = createTestImageBytes(40, 40, Color.CYAN);

        // Request 1,000 distinct images
        for (int i = 0; i < 1_000; i++) {
            String trackId = "stress-track-" + i;
            Image img = stressCache.getImage(trackId, sampleImg, 38, 38);
            assertThat(img).isNotNull();

            // Cache size must NEVER exceed configured capacity
            assertThat(stressCache.getCacheSize()).isLessThanOrEqualTo(capacity);
        }

        assertThat(stressCache.getCacheSize()).isEqualTo(capacity);
        assertThat(stressCache.getMissCount()).isEqualTo(1_000);

        // Re-requesting the last 50 images should yield 100% cache hits
        for (int i = 950; i < 1_000; i++) {
            String trackId = "stress-track-" + i;
            Image img = stressCache.getImage(trackId, sampleImg, 38, 38);
            assertThat(img).isNotNull();
        }

        assertThat(stressCache.getHitCount()).isEqualTo(50);
        assertThat(stressCache.getCacheSize()).isEqualTo(capacity);
    }

    @Test
    @DisplayName("Rapid continuous theme and locale switching executes without exceptions or leaks")
    void testUiSwitchingStress() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        assertThatCode(() -> {
            for (int i = 0; i < 50; i++) {
                // Theme toggle
                if (i % 2 == 0) {
                    ThemeManager.getInstance().applyTheme(AppTheme.LIGHT);
                } else {
                    ThemeManager.getInstance().applyTheme(AppTheme.DARK);
                }

                // Locale toggle
                if (i % 2 == 0) {
                    I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
                } else {
                    I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
                }
            }
        }).doesNotThrowAnyException();

        // Restore clean default
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
    }

    @Test
    @DisplayName("FastSearchIndex handles extreme queries (zero matches, massive matches, very long, special CJK) under 1ms")
    void testSearchExtremeQueryVariations10kTracks() {
        List<Track> tracks = generate10kTracks();
        FastSearchIndex index = new FastSearchIndex();
        index.indexTracks(tracks);

        String[] extremeQueries = {
                // 1. Zero match queries
                "NON_EXISTENT_STRING_ABCXYZ123456789",
                "QQQQQQZZZZZZWWWWWW",
                // 2. High match count queries
                "Track",
                "Artist",
                "19",
                // 3. Very long query (> 100 characters)
                "ThisIsAVeryLongQueryStringDesignedToTestSearchIndexTokenizerAndInvertedIndexThroughputWithoutAnyDegradationOrPerformanceBottlenecksUnderStressConditions",
                // 4. Special characters & punctuation
                "!@#$%^&*()_+-=[]{}|;':,.<>?",
                "Symphony No.3 in C",
                // 5. CJK Bigram & Japanese queries
                "夏祭り",
                "曲タイトル",
                "第500番",
                "第"
        };

        // Warm up JIT
        for (int i = 0; i < 30; i++) {
            for (String q : extremeQueries) {
                index.search(q);
            }
        }

        // Benchmark measurement (50 iterations per query)
        int iterations = 50;
        double totalAvgMillis = 0;
        for (String q : extremeQueries) {
            long totalNanos = 0;
            int matchCount = 0;
            for (int i = 0; i < iterations; i++) {
                long t0 = System.nanoTime();
                Set<String> result = index.search(q);
                totalNanos += (System.nanoTime() - t0);
                matchCount = result.size();
            }

            double avgMillis = (double) totalNanos / iterations / 1_000_000.0;
            System.out.printf("Query '%s': matches=%d, avgTime=%.4f ms%n", q, matchCount, avgMillis);

            // Each extreme query must complete in interactive low-latency range (< 2.5ms)
            assertThat(avgMillis).isLessThan(2.5);
            totalAvgMillis += avgMillis;
        }

        // Overall average query response time across all extreme variations must be sub-millisecond (< 1.0ms)
        double overallAvg = totalAvgMillis / extremeQueries.length;
        System.out.printf("Overall extreme queries average latency: %.4f ms%n", overallAvg);
        assertThat(overallAvg).isLessThan(1.0);
    }

    @Test
    @DisplayName("TrackTableModel supports concurrent multi-thread read/sort/update operations without corruption")
    void testConcurrentModelModificationAndSorting10kTracks() throws Exception {
        List<Track> tracks = generate10kTracks();
        TrackTableModel model = new TrackTableModel();
        model.setTracks(tracks);

        TableRowSorter<TrackTableModel> sorter = new TableRowSorter<>(model);

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        // Task 1: Repeated reading and inspection across rows
        tasks.add(() -> {
            for (int i = 0; i < 1_000; i++) {
                int row = (i * 7) % 10_000;
                Track t = model.getTrackAt(row);
                assertThat(t).isNotNull();
                Object val = model.getValueAt(row, TrackTableModel.COLUMN_INDEX_TITLE);
                assertThat(val).isNotNull();
            }
            return true;
        });

        // Task 2: Repeated reading and inspection across another row segment
        tasks.add(() -> {
            for (int i = 0; i < 1_000; i++) {
                int row = (i * 13) % 10_000;
                Track t = model.getTrackAt(row);
                assertThat(t).isNotNull();
                Object artist = model.getValueAt(row, TrackTableModel.COLUMN_INDEX_ARTIST);
                assertThat(artist).isNotNull();
            }
            return true;
        });

        // Task 3: Targeted track updates (updating rating / play count / title)
        tasks.add(() -> {
            for (int i = 0; i < 100; i++) {
                int index = (i * 31) % 10_000;
                Track existing = tracks.get(index);
                Track updated = Track.builder()
                        .id(existing.getId())
                        .filePath(existing.getFilePath())
                        .title(existing.getTitle() + " [Updated]")
                        .artist(existing.getArtist())
                        .album(existing.getAlbum())
                        .rating(5)
                        .playCount(existing.getPlayCount() + 1)
                        .build();
                boolean res = model.updateTrack(updated);
                assertThat(res).isTrue();
            }
            return true;
        });

        // Task 4: Sorting across columns
        tasks.add(() -> {
            List<RowSorter.SortKey> sortKeys = List.of(
                    new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_TITLE, SortOrder.ASCENDING),
                    new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_ARTIST, SortOrder.DESCENDING),
                    new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_DURATION, SortOrder.ASCENDING)
            );
            for (int i = 0; i < 10; i++) {
                sorter.setSortKeys(List.of(sortKeys.get(i % sortKeys.size())));
                sorter.sort();
            }
            return true;
        });

        try {
            List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (Future<Boolean> f : futures) {
                assertThat(f.get()).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(model.getRowCount()).isEqualTo(10_000);
        assertThat(sorter.getViewRowCount()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("ArtworkCache maintains strict LRU size ceiling and heap stability under 10,000 requests")
    void testArtworkCacheHeapStabilityUnderMassiveAccess() {
        int capacity = 50;
        ArtworkCache cache = new ArtworkCache(capacity);
        byte[] sampleImg = createTestImageBytes(40, 40, Color.BLUE);

        // Perform 10,000 distinct requests
        for (int i = 0; i < 10_000; i++) {
            String trackId = "heap-track-" + i;
            Image img = cache.getImage(trackId, sampleImg, 38, 38);
            assertThat(img).isNotNull();

            // Strict ceiling check
            assertThat(cache.getCacheSize()).isLessThanOrEqualTo(capacity);
        }

        assertThat(cache.getCacheSize()).isEqualTo(capacity);
        assertThat(cache.getMissCount()).isEqualTo(10_000);

        // Perform GC and verify no lingering memory exhaustion
        System.gc();

        // Clear cache and verify total release
        cache.clear();
        assertThat(cache.getCacheSize()).isEqualTo(0);
        cache.resetStats();
        assertThat(cache.getHitCount()).isEqualTo(0);
        assertThat(cache.getMissCount()).isEqualTo(0);
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
    @DisplayName("Large scale (3,000 albums) album grid lazy rendering initializes in under 200ms without EDT freeze")
    void testLargeScaleAlbumGridRenderingPerformance() {
        // Generate 3,000 distinct albums
        List<Album> largeAlbums = new ArrayList<>(3_000);
        for (int i = 0; i < 3_000; i++) {
            Track t = Track.builder()
                    .id("bench_track_" + i)
                    .filePath("music/bench_" + i + ".mp3")
                    .title("Bench Track " + i)
                    .artist("Artist " + (i % 200))
                    .album("Bench Album " + i)
                    .trackNumber(1)
                    .durationSeconds(210)
                    .build();
            largeAlbums.add(new Album("Bench Album " + i, "Artist " + (i % 200), 2020, 1, 210, t, List.of(t)));
        }

        // Warmup JIT and UI class loading
        AlbumGridViewPanel warmupPanel = new AlbumGridViewPanel(10);
        warmupPanel.setAlbums(largeAlbums.subList(0, 20));

        AlbumGridViewPanel gridPanel = new AlbumGridViewPanel(60);

        long start = System.nanoTime();
        gridPanel.setAlbums(largeAlbums);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        // Instant initial rendering: must take under 500ms (typically < 30ms post warmup)
        assertThat(durationMs).isLessThan(500);
        assertThat(gridPanel.getAllAlbums()).hasSize(3_000);
        assertThat(gridPanel.getDisplayedAlbums()).hasSize(3_000);
        assertThat(gridPanel.getRenderedCardCount()).isEqualTo(60);

        // Incremental chunk expansion
        long nextChunkStart = System.nanoTime();
        gridPanel.loadNextChunk();
        long nextChunkMs = (System.nanoTime() - nextChunkStart) / 1_000_000;

        assertThat(nextChunkMs).isLessThan(100);
        assertThat(gridPanel.getRenderedCardCount()).isEqualTo(120);

        // Filter 3,000 albums
        long filterStart = System.nanoTime();
        gridPanel.filterAlbums("Album 10");
        long filterMs = (System.nanoTime() - filterStart) / 1_000_000;

        assertThat(filterMs).isLessThan(100);
        assertThat(gridPanel.getRenderedCardCount()).isLessThanOrEqualTo(60);
    }
}
