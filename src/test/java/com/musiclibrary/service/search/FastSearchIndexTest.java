package com.musiclibrary.service.search;

import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FastSearchIndexTest {

    private FastSearchIndex searchIndex;

    @BeforeEach
    void setUp() {
        searchIndex = new FastSearchIndex();
    }

    private Track createTrack(String id, String title, String artist, String album, String genre, int year) {
        return Track.builder()
                .id(id)
                .filePath("tracks/" + id + ".mp3")
                .title(title)
                .artist(artist)
                .album(album)
                .genre(genre)
                .year(year)
                .build();
    }

    private Track createTrack(String id, String title, String artist) {
        return createTrack(id, title, artist, "", "", 0);
    }

    private Track createTrack(String id, String title) {
        return createTrack(id, title, "", "", "", 0);
    }

    @Test
    @DisplayName("Empty index returns empty set and empty list")
    void testEmptyIndex() {
        assertThat(searchIndex.size()).isEqualTo(0);
        assertThat(searchIndex.search("test")).isEmpty();
        assertThat(searchIndex.searchTracks("test")).isEmpty();
        assertThat(searchIndex.search("")).isEmpty();
    }

    @Test
    @DisplayName("Indexing tracks allows searching across title, artist, album, genre, and year")
    void testSearchAcrossAllMetadataFields() {
        Track t1 = createTrack("1", "Bohemian Rhapsody", "Queen", "A Night at the Opera", "Classic Rock", 1975);
        Track t2 = createTrack("2", "Hotel California", "Eagles", "Hotel California", "Rock", 1976);

        searchIndex.indexTracks(List.of(t1, t2));
        assertThat(searchIndex.size()).isEqualTo(2);

        // Title match
        assertThat(searchIndex.search("bohemian")).containsExactly("1");
        // Artist match
        assertThat(searchIndex.search("queen")).containsExactly("1");
        // Album match
        assertThat(searchIndex.search("opera")).containsExactly("1");
        // Genre match
        assertThat(searchIndex.search("classic")).containsExactly("1");
        // Year match
        assertThat(searchIndex.search("1975")).containsExactly("1");

        // Common term in genre matches both
        assertThat(searchIndex.search("rock")).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("Search is case-insensitive and normalizes full-width characters")
    void testCaseInsensitiveAndUnicodeNormalization() {
        Track t1 = createTrack("1", "Ｓｈａｐｅ　ｏｆ　Ｙｏｕ", "Ed Sheeran", "Divide", "Pop", 2017);

        searchIndex.indexTrack(t1);

        // Half-width lowercase query
        assertThat(searchIndex.search("shape")).containsExactly("1");
        // Mixed-case query
        assertThat(searchIndex.search("ShApE")).containsExactly("1");
        // Artist lowercase query
        assertThat(searchIndex.search("ed sheeran")).containsExactly("1");
    }

    @Test
    @DisplayName("Multiple keywords perform strict AND search")
    void testMultipleKeywordsAndSearch() {
        Track t1 = createTrack("1", "Take Five", "Dave Brubeck", "Time Out", "Jazz", 1959);
        Track t2 = createTrack("2", "So What", "Miles Davis", "Kind of Blue", "Jazz", 1959);
        Track t3 = createTrack("3", "Autumn Leaves", "Bill Evans", "Portrait in Jazz", "Jazz", 1960);

        searchIndex.indexTracks(List.of(t1, t2, t3));

        // "jazz 1959" matches t1 and t2 only
        assertThat(searchIndex.search("jazz 1959")).containsExactlyInAnyOrder("1", "2");

        // "dave jazz" matches t1 only
        assertThat(searchIndex.search("dave jazz")).containsExactly("1");

        // "jazz 1999" matches none
        assertThat(searchIndex.search("jazz 1999")).isEmpty();
    }

    @Test
    @DisplayName("Prefix and substring matching supports incremental search")
    void testPrefixAndSubstringMatching() {
        Track t1 = createTrack("1", "Yesterday", "The Beatles");
        searchIndex.indexTrack(t1);

        // Prefix match on title
        assertThat(searchIndex.search("yes")).containsExactly("1");
        assertThat(searchIndex.search("yest")).containsExactly("1");

        // Prefix match on artist
        assertThat(searchIndex.search("beat")).containsExactly("1");

        // Substring match
        assertThat(searchIndex.search("terday")).containsExactly("1");
    }

    @Test
    @DisplayName("Japanese non-spaced text matches via CJK bigrams")
    void testJapaneseAndCjkMatching() {
        Track t1 = createTrack("1", "夜に駆ける", "YOASOBI");
        Track t2 = createTrack("2", "群青", "YOASOBI");
        Track t3 = createTrack("3", "First Love", "宇多田ヒカル");

        searchIndex.indexTracks(List.of(t1, t2, t3));

        assertThat(searchIndex.search("夜に")).containsExactly("1");
        assertThat(searchIndex.search("駆ける")).containsExactly("1");
        assertThat(searchIndex.search("群青")).containsExactly("2");
        assertThat(searchIndex.search("宇多田")).containsExactly("3");
        assertThat(searchIndex.search("ヒカル")).containsExactly("3");
    }

    @Test
    @DisplayName("Null or blank query returns all indexed track IDs")
    void testBlankQueryReturnsAllTracks() {
        Track t1 = createTrack("1", "A");
        Track t2 = createTrack("2", "B");
        searchIndex.indexTracks(List.of(t1, t2));

        assertThat(searchIndex.search(null)).containsExactlyInAnyOrder("1", "2");
        assertThat(searchIndex.search("")).containsExactlyInAnyOrder("1", "2");
        assertThat(searchIndex.search("   ")).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("removeTrack and updateTrack maintain index consistency")
    void testRemoveAndUpdateTrack() {
        Track t1 = createTrack("1", "Old Title", "Artist A");
        searchIndex.indexTrack(t1);

        assertThat(searchIndex.search("old")).containsExactly("1");

        // Update track metadata
        Track t1Updated = createTrack("1", "New Title", "Artist A");
        searchIndex.updateTrack(t1Updated);

        assertThat(searchIndex.search("old")).isEmpty();
        assertThat(searchIndex.search("new")).containsExactly("1");

        // Remove track
        searchIndex.removeTrack("1");
        assertThat(searchIndex.size()).isEqualTo(0);
        assertThat(searchIndex.search("new")).isEmpty();
    }

    @Test
    @DisplayName("clear empties all index data")
    void testClear() {
        Track t1 = createTrack("1", "Song 1");
        Track t2 = createTrack("2", "Song 2");
        searchIndex.indexTracks(List.of(t1, t2));

        assertThat(searchIndex.size()).isEqualTo(2);
        searchIndex.clear();
        assertThat(searchIndex.size()).isEqualTo(0);
        assertThat(searchIndex.search("song")).isEmpty();
    }

    @Test
    @DisplayName("searchTracks returns matching Track entity instances")
    void testSearchTracksEntities() {
        Track t1 = createTrack("1", "Alpha");
        Track t2 = createTrack("2", "Beta");
        searchIndex.indexTracks(List.of(t1, t2));

        List<Track> tracks = searchIndex.searchTracks("alph");
        assertThat(tracks).containsExactly(t1);
    }

    @Test
    @DisplayName("Concurrent reads and writes operate safely without corruption")
    void testConcurrentThreadSafety() throws Exception {
        int threadCount = 4;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger writeCounter = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        if (i % 3 == 0) {
                            int id = writeCounter.incrementAndGet();
                            searchIndex.indexTrack(createTrack("c-" + id, "Concurrent Track " + id, "Artist " + (id % 10)));
                        } else {
                            searchIndex.search("concurrent");
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(finished).isTrue();
        assertThat(searchIndex.size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Benchmark: 10,000 tracks search query executes in under 1 millisecond (< 1ms)")
    void testTenThousandTracksSearchPerformanceSubMillisecond() {
        List<Track> largeLibrary = new ArrayList<>(10_000);
        String[] genres = {"Rock", "Pop", "Jazz", "Classical", "Hip Hop", "Electronic", "Metal", "R&B"};
        String[] artists = {"Beatles", "Miles Davis", "Queen", "Daft Punk", "Taylor Swift", "Bach", "Kendrick Lamar", "Pink Floyd"};

        for (int i = 0; i < 10_000; i++) {
            largeLibrary.add(Track.builder()
                    .id("t-" + i)
                    .filePath("tracks/t-" + i + ".mp3")
                    .title("Track Title Number " + i + " Special Deluxe")
                    .artist(artists[i % artists.length] + " " + (i % 100))
                    .album("Album Edition Vol " + (i % 50))
                    .genre(genres[(i * 3) % genres.length])
                    .year(1970 + (i % 54))
                    .build());
        }

        searchIndex.indexTracks(largeLibrary);
        assertThat(searchIndex.size()).isEqualTo(10_000);

        // Warm up JIT compiler
        for (int i = 0; i < 1_000; i++) {
            searchIndex.search("queen deluxe");
            searchIndex.search("beatles");
            searchIndex.search("jazz 1980");
            searchIndex.search("deluxe 50");
        }

        // Measure latency over 500 search queries
        int iterations = 500;
        long startNanos = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Set<String> matches = searchIndex.search("queen deluxe");
            assertThat(matches).isNotEmpty();
        }
        long totalNanos = System.nanoTime() - startNanos;
        double avgMillis = (totalNanos / 1_000_000.0) / iterations;

        System.out.printf("Average search latency over 10,000 tracks: %.4f ms%n", avgMillis);

        // Must strictly meet the < 1ms requirement defined in docs/DESIGN.md
        assertThat(avgMillis).as("Search latency must be strictly under 1ms").isLessThan(1.0);
    }

    @Test
    @DisplayName("Boundary and null inputs are handled safely without exceptions")
    void testBoundaryAndNullInputs() {
        // Null or empty track collections
        searchIndex.indexTrack(null);
        searchIndex.indexTracks(null);
        searchIndex.indexTracks(List.of());
        assertThat(searchIndex.size()).isEqualTo(0);

        // Track with null/empty metadata fields
        Track nullMeta = Track.builder()
                .id("null-meta")
                .filePath("tracks/null.mp3")
                .title(null)
                .artist(null)
                .album(null)
                .genre(null)
                .year(0)
                .build();
        searchIndex.indexTrack(nullMeta);
        assertThat(searchIndex.size()).isEqualTo(1);
        assertThat(searchIndex.search("anything")).isEmpty();
        assertThat(searchIndex.search(null)).containsExactly("null-meta");

        // Safe removals
        searchIndex.removeTrack(null);
        searchIndex.removeTrack("");
        searchIndex.removeTrack("   ");
        searchIndex.removeTrack("non-existent");
        assertThat(searchIndex.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Punctuation, symbols, and emoji queries match safely without regex exceptions")
    void testPunctuationSymbolsAndEmojiQueries() {
        Track t1 = createTrack("1", "AC/DC - Back in Black [FLAC]", "AC/DC", "Back in Black", "Hard Rock/Heavy Metal", 1980);
        Track t2 = createTrack("2", "Guns N' Roses - Sweet Child O' Mine", "Guns N' Roses", "Appetite for Destruction", "Rock", 1987);
        Track t3 = createTrack("3", "Special Edition ★ (Remastered 2024) 🔥", "Artist feat. Singer", "Album & Co.", "Pop & Rock", 2024);

        searchIndex.indexTracks(List.of(t1, t2, t3));

        // Exact punctuation queries
        assertThat(searchIndex.search("AC/DC")).containsExactly("1");
        assertThat(searchIndex.search("AC DC")).containsExactly("1");
        assertThat(searchIndex.search("[FLAC]")).containsExactly("1");
        assertThat(searchIndex.search("FLAC")).containsExactly("1");

        // Apostrophe & hyphen queries
        assertThat(searchIndex.search("Guns N' Roses")).containsExactly("2");
        assertThat(searchIndex.search("Guns Roses")).containsExactly("2");

        // Symbols and emojis
        assertThat(searchIndex.search("2024 Remastered")).containsExactly("3");
        assertThat(searchIndex.search("Pop Rock")).containsExactly("3");

        // Punctuation-only queries do not throw PatternSyntaxException and return all or none
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            searchIndex.search("//");
            searchIndex.search("[]");
            searchIndex.search("***");
            searchIndex.search("!@#$%^&*()");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Japanese half-width kana, full-width numbers, and single kanji match accurately via NFKC normalization")
    void testJapaneseKanaAndHalfWidthNormalization() {
        // Track with half-width katakana and full-width numbers
        Track t1 = createTrack("1", "ｻｶﾅｸｼｮﾝ (Sakanaction)", "サカナクション", "８３４．１９４", "Ｊ－ＰＯＰ", 2019);
        Track t2 = createTrack("2", "夜に駆ける", "YOASOBI", "THE BOOK", "J-Pop", 2020);

        searchIndex.indexTracks(List.of(t1, t2));

        // Search with full-width katakana matches half-width indexed text
        assertThat(searchIndex.search("サカナクション")).containsExactly("1");
        // Search with half-width katakana matches full-width indexed text
        assertThat(searchIndex.search("ｻｶﾅｸｼｮﾝ")).containsExactly("1");

        // Half-width query for full-width album "834.194"
        assertThat(searchIndex.search("834")).containsExactly("1");

        // Full-width query for year
        assertThat(searchIndex.search("２０１９")).containsExactly("1");

        // Single character kanji query matches via fallback lookup
        assertThat(searchIndex.search("夜")).containsExactly("2");
        assertThat(searchIndex.search("駆")).containsExactly("2");
    }

    @Test
    @DisplayName("AND search results are identical regardless of query word order")
    void testWordOrderInvariance() {
        Track t1 = createTrack("1", "Bohemian Rhapsody", "Queen", "A Night at the Opera", "Rock", 1975);
        Track t2 = createTrack("2", "Another One Bites the Dust", "Queen", "The Game", "Rock", 1980);

        searchIndex.indexTracks(List.of(t1, t2));

        Set<String> order1 = searchIndex.search("Queen 1975 Rock");
        Set<String> order2 = searchIndex.search("1975 Rock Queen");
        Set<String> order3 = searchIndex.search("Rock Queen 1975");

        assertThat(order1).containsExactly("1");
        assertThat(order2).isEqualTo(order1);
        assertThat(order3).isEqualTo(order1);
    }

    @Test
    @DisplayName("Single character and short substring queries match accurately")
    void testSingleCharacterAndSubstrings() {
        Track t1 = createTrack("1", "Jazz Standards", "Bill Evans");
        searchIndex.indexTrack(t1);

        // 1-char word prefix
        assertThat(searchIndex.search("j")).containsExactly("1");
        assertThat(searchIndex.search("b")).containsExactly("1");

        // 1-char mid-word character (e.g. 'z' in Jazz)
        assertThat(searchIndex.search("z")).containsExactly("1");

        // 2-char mid-word substring
        assertThat(searchIndex.search("and")).containsExactly("1");
    }

    @Test
    @DisplayName("Heavy concurrent multi-threaded reads, writes, updates, and deletes maintain integrity")
    void testHeavyConcurrentStress() throws Exception {
        int readThreads = 3;
        int writeThreads = 2;
        int totalThreads = readThreads + writeThreads;
        int iterations = 400;

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
        AtomicInteger idGen = new AtomicInteger(1000);

        // Prepopulate index with 50 tracks
        for (int i = 0; i < 50; i++) {
            searchIndex.indexTrack(createTrack("init-" + i, "Title " + i, "Artist " + (i % 5)));
        }

        // Readers
        for (int r = 0; r < readThreads; r++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        searchIndex.search("title");
                        searchIndex.search("artist " + (i % 5));
                        searchIndex.searchTracks("5");
                    }
                } catch (Throwable t) {
                    errorRef.compareAndSet(null, t);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Writers (insert, update, remove)
        for (int w = 0; w < writeThreads; w++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        int id = idGen.incrementAndGet();
                        Track track = createTrack("dyn-" + id, "Dynamic " + id, "Artist");
                        searchIndex.indexTrack(track);

                        // Update
                        Track updated = createTrack("dyn-" + id, "Updated Dynamic " + id, "Artist");
                        searchIndex.updateTrack(updated);

                        // Occasional remove
                        if (i % 4 == 0) {
                            searchIndex.removeTrack("dyn-" + id);
                        }
                    }
                } catch (Throwable t) {
                    errorRef.compareAndSet(null, t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errorRef.get()).isNull();
        assertThat(searchIndex.size()).isGreaterThan(0);
    }
}
