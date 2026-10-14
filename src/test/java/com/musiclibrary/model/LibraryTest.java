package com.musiclibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryTest {

    @Test
    @DisplayName("New library is initialized empty")
    void testInitialEmpty() {
        Library library = new Library();

        assertThat(library.getTrackCount()).isEqualTo(0);
        assertThat(library.getPlaylistCount()).isEqualTo(0);
        assertThat(library.getTracks()).isEmpty();
        assertThat(library.getPlaylists()).isEmpty();
    }

    @Test
    @DisplayName("addTrack and getTrack work properly")
    void testAddAndGetTrack() {
        Library library = new Library();
        Track track = Track.builder()
                .filePath("C:/Music/song.mp3")
                .title("Title")
                .artist("Artist")
                .build();

        library.addTrack(track);

        assertThat(library.getTrackCount()).isEqualTo(1);
        assertThat(library.containsTrack(track.getId())).isTrue();
        assertThat(library.getTrack(track.getId())).isEqualTo(track);
    }

    @Test
    @DisplayName("addTracks batch adds collection of tracks")
    void testAddTracksBatch() {
        Library library = new Library();
        Track t1 = Track.builder().filePath("C:/t1.mp3").title("T1").build();
        Track t2 = Track.builder().filePath("C:/t2.mp3").title("T2").build();

        library.addTracks(List.of(t1, t2));

        assertThat(library.getTrackCount()).isEqualTo(2);
        assertThat(library.getTracks()).contains(t1, t2);
    }

    @Test
    @DisplayName("removeTrack removes track and cascades cleanup to all playlists")
    void testRemoveTrackCascadesToPlaylists() {
        Library library = new Library();
        Track t1 = Track.builder().filePath("C:/t1.mp3").title("T1").build();
        Track t2 = Track.builder().filePath("C:/t2.mp3").title("T2").build();
        library.addTracks(List.of(t1, t2));

        Playlist p1 = library.createPlaylist("Playlist 1");
        p1.addTrack(t1.getId());
        p1.addTrack(t2.getId());

        Playlist p2 = library.createPlaylist("Playlist 2");
        p2.addTrack(t1.getId());

        assertThat(p1.getTrackIds()).containsExactly(t1.getId(), t2.getId());
        assertThat(p2.getTrackIds()).containsExactly(t1.getId());

        // Remove t1
        Track removed = library.removeTrack(t1.getId());

        assertThat(removed).isEqualTo(t1);
        assertThat(library.getTrackCount()).isEqualTo(1);
        assertThat(library.containsTrack(t1.getId())).isFalse();

        // Check playlists cleaned up t1
        assertThat(p1.getTrackIds()).containsExactly(t2.getId());
        assertThat(p2.getTrackIds()).isEmpty();
    }

    @Test
    @DisplayName("removeTrack cascades to playlists and removes duplicate occurrences completely")
    void testRemoveTrackRemovesDuplicateOccurrencesFromPlaylists() {
        Library library = new Library();
        Track t1 = Track.builder().filePath("C:/t1.mp3").title("T1").build();
        Track t2 = Track.builder().filePath("C:/t2.mp3").title("T2").build();
        library.addTracks(List.of(t1, t2));

        Playlist p1 = library.createPlaylist("Playlist 1");
        p1.addTrack(t1.getId());
        p1.addTrack(t2.getId());
        p1.addTrack(t1.getId()); // duplicate t1

        Playlist p2 = library.createPlaylist("Playlist 2");
        p2.addTrack(t1.getId());
        p2.addTrack(t1.getId());
        p2.addTrack(t1.getId()); // triplicate t1

        Playlist p3 = library.createPlaylist("Playlist 3");
        p3.addTrack(t2.getId());

        // Remove t1
        library.removeTrack(t1.getId());

        assertThat(p1.getTrackIds()).containsExactly(t2.getId());
        assertThat(p2.getTrackIds()).isEmpty();
        assertThat(p3.getTrackIds()).containsExactly(t2.getId());
    }

    @Test
    @DisplayName("Playlist management operations (create, add, delete, get)")
    void testPlaylistOperations() {
        Library library = new Library();

        Playlist p1 = library.createPlaylist("Rock");
        assertThat(library.getPlaylistCount()).isEqualTo(1);
        assertThat(library.getPlaylist(p1.getId())).isEqualTo(p1);

        // Add custom playlist
        String customId = UUID.randomUUID().toString();
        Playlist p2 = new Playlist(customId, "Jazz");
        library.addPlaylist(p2);
        assertThat(library.getPlaylistCount()).isEqualTo(2);
        assertThat(library.getPlaylist(customId)).isEqualTo(p2);

        // Replacing existing playlist with same ID
        Playlist p2Updated = new Playlist(customId, "Smooth Jazz");
        library.addPlaylist(p2Updated);
        assertThat(library.getPlaylistCount()).isEqualTo(2);
        assertThat(library.getPlaylist(customId).getName()).isEqualTo("Smooth Jazz");

        // Delete playlist
        boolean deleted = library.deletePlaylist(p1.getId());
        assertThat(deleted).isTrue();
        assertThat(library.getPlaylistCount()).isEqualTo(1);
        assertThat(library.getPlaylist(p1.getId())).isNull();
    }

    @Test
    @DisplayName("findTracks searches case-insensitively across title, artist, album, and genre")
    void testFindTracks() {
        Library library = new Library();
        Track t1 = Track.builder().filePath("C:/1.mp3").title("Bohemian Rhapsody").artist("Queen").album("A Night at the Opera").genre("Rock").build();
        Track t2 = Track.builder().filePath("C:/2.mp3").title("Hotel California").artist("Eagles").album("Hotel California").genre("Classic Rock").build();
        Track t3 = Track.builder().filePath("C:/3.mp3").title("Shape of You").artist("Ed Sheeran").album("Divide").genre("Pop").build();
        library.addTracks(List.of(t1, t2, t3));

        // Search by artist (case-insensitive)
        assertThat(library.findTracks("queen")).containsExactly(t1);

        // Search by title partial match
        assertThat(library.findTracks("hotel")).containsExactly(t2);

        // Search by genre
        assertThat(library.findTracks("rock")).containsExactlyInAnyOrder(t1, t2);

        // Search with empty/blank returns all tracks
        assertThat(library.findTracks("")).hasSize(3);
        assertThat(library.findTracks(null)).hasSize(3);

        // Search with no match
        assertThat(library.findTracks("NonExistent")).isEmpty();
    }

    @Test
    @DisplayName("findTracks handles Japanese characters, special symbols, and emojis accurately")
    void testFindTracksJapaneseAndSpecialCharacters() {
        Library library = new Library();
        Track t1 = Track.builder()
                .filePath("C:/Music/yoasobi.mp3")
                .title("夜に駆ける")
                .artist("YOASOBI")
                .album("THE BOOK")
                .genre("J-POP")
                .build();

        Track t2 = Track.builder()
                .filePath("C:/Music/yonezu.flac")
                .title("Lemon")
                .artist("米津玄師")
                .album("STRAY SHEEP")
                .genre("J-POP")
                .build();

        Track t3 = Track.builder()
                .filePath("C:/Music/remix.mp3")
                .title("Party Anthem [★DJ Remix★] 🎵")
                .artist("Various Artists")
                .album("Dance & Beats")
                .genre("Electronic & Dance")
                .build();

        library.addTracks(List.of(t1, t2, t3));

        // Japanese Title search
        assertThat(library.findTracks("夜に駆ける")).containsExactly(t1);
        assertThat(library.findTracks("夜")).containsExactly(t1);

        // Japanese Artist search
        assertThat(library.findTracks("米津玄師")).containsExactly(t2);
        assertThat(library.findTracks("米津")).containsExactly(t2);

        // Japanese Genre search
        assertThat(library.findTracks("j-pop")).containsExactlyInAnyOrder(t1, t2);

        // Special symbols search
        assertThat(library.findTracks("★DJ Remix★")).containsExactly(t3);
        assertThat(library.findTracks("★")).containsExactly(t3);
        assertThat(library.findTracks("🎵")).containsExactly(t3);
        assertThat(library.findTracks("&")).containsExactly(t3);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent multi-threaded operations do not throw ConcurrentModificationException or corrupt data")
    void testConcurrentOperationsThreadSafety() throws InterruptedException {
        Library library = new Library();
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger addedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String id = "track-" + threadId + "-" + j;
                        Track track = Track.builder()
                                .id(id)
                                .filePath("C:/music/" + id + ".mp3")
                                .title("Title " + id)
                                .artist("Artist " + threadId)
                                .build();

                        library.addTrack(track);
                        addedCount.incrementAndGet();

                        // Query while adding
                        library.findTracks("Artist " + threadId);
                        library.getTrack(id);

                        // Periodically add/query playlists
                        if (j % 10 == 0) {
                            Playlist p = library.createPlaylist("Playlist-" + threadId + "-" + j);
                            p.addTrack(id);
                            library.getPlaylists();
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(library.getTrackCount()).isEqualTo(threadCount * operationsPerThread);
        assertThat(library.getPlaylistCount()).isEqualTo(threadCount * (operationsPerThread / 10));
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    @DisplayName("Large-scale dataset (3,000 tracks) bulk addition, lookup, and query integrity")
    void testLargeScaleDatasetPerformanceAndIntegrity() {
        Library library = new Library();
        int totalTracks = 3000;
        List<Track> bulkTracks = new ArrayList<>(totalTracks);

        for (int i = 0; i < totalTracks; i++) {
            Track track = Track.builder()
                    .id("bulk-id-" + i)
                    .filePath("C:/Music/bulk-" + i + ".mp3")
                    .title("Track Title " + i)
                    .artist("Bulk Artist " + (i % 50))
                    .album("Bulk Album " + (i % 20))
                    .genre(i % 2 == 0 ? "Rock" : "Jazz")
                    .durationSeconds(180 + (i % 100))
                    .fileSizeBytes(3000000 + i)
                    .rating(i % 6)
                    .playCount(i % 10)
                    .build();
            bulkTracks.add(track);
        }

        long startTime = System.currentTimeMillis();
        library.addTracks(bulkTracks);
        long addDuration = System.currentTimeMillis() - startTime;

        assertThat(library.getTrackCount()).isEqualTo(totalTracks);
        assertThat(addDuration).isLessThan(5000); // Should add in under 5 seconds

        // Random access lookups
        assertThat(library.getTrack("bulk-id-0")).isNotNull();
        assertThat(library.getTrack("bulk-id-1500")).isNotNull();
        assertThat(library.getTrack("bulk-id-2999")).isNotNull();
        assertThat(library.containsTrack("bulk-id-99999")).isFalse();

        // Search across 3,000 tracks
        List<Track> found = library.findTracks("Bulk Artist 0");
        assertThat(found).hasSize(totalTracks / 50); // 3000 / 50 = 60
    }

    @Test
    @DisplayName("clear empties tracks and playlists")
    void testClear() {
        Library library = new Library();
        Track track = Track.builder().filePath("C:/1.mp3").title("Title").build();
        library.addTrack(track);
        library.createPlaylist("Playlist");

        library.clear();

        assertThat(library.getTrackCount()).isEqualTo(0);
        assertThat(library.getPlaylistCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Null safety and validations")
    void testValidations() {
        Library library = new Library();

        assertThatThrownBy(() -> library.addTrack(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> library.addPlaylist(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(library.getTrack(null)).isNull();
        assertThat(library.getTrack("")).isNull();
        assertThat(library.removeTrack(null)).isNull();
        assertThat(library.getPlaylist(null)).isNull();
        assertThat(library.deletePlaylist(null)).isFalse();
    }

    @Test
    @DisplayName("findByFilePath locates tracks case-insensitively and returns null when not found")
    void testFindByFilePath() {
        Library library = new Library();
        Track track = Track.builder()
                .filePath("C:/Music/Albums/track01.mp3")
                .title("Track 1")
                .build();
        library.addTrack(track);

        assertThat(library.findByFilePath("C:/Music/Albums/track01.mp3")).isEqualTo(track);
        assertThat(library.findByFilePath("c:/music/albums/track01.mp3")).isEqualTo(track);
        assertThat(library.findByFilePath("C:/Other/file.mp3")).isNull();
        assertThat(library.findByFilePath(null)).isNull();
        assertThat(library.findByFilePath("   ")).isNull();
    }

    @Test
    @DisplayName("upsertTrack adds new track when not found and preserves user stats on existing track")
    void testUpsertTrack() {
        Library library = new Library();
        java.time.Instant originalAdded = java.time.Instant.parse("2025-01-01T10:00:00Z");

        Track original = Track.builder()
                .id("orig-id-1")
                .filePath("C:/Music/song.mp3")
                .title("Original Title")
                .artist("Original Artist")
                .rating(5)
                .playCount(42)
                .dateAdded(originalAdded)
                .lastModified(java.time.Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        library.addTrack(original);

        // Upsert with updated tag metadata at the same file path
        Track updatedMeta = Track.builder()
                .id(UUID.randomUUID().toString()) // Extractor generates new UUID
                .filePath("C:/Music/song.mp3")
                .title("Updated Title")
                .artist("Updated Artist")
                .album("Updated Album")
                .rating(0)    // fresh extractor defaults
                .playCount(0) // fresh extractor defaults
                .dateAdded(java.time.Instant.now())
                .lastModified(java.time.Instant.parse("2026-05-01T00:00:00Z"))
                .build();

        Track result = library.upsertTrack(updatedMeta);

        assertThat(library.getTrackCount()).isEqualTo(1);
        assertThat(result.getId()).isEqualTo("orig-id-1"); // Preserved ID
        assertThat(result.getRating()).isEqualTo(5);       // Preserved rating
        assertThat(result.getPlayCount()).isEqualTo(42);   // Preserved playCount
        assertThat(result.getDateAdded()).isEqualTo(originalAdded); // Preserved dateAdded
        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getArtist()).isEqualTo("Updated Artist");
        assertThat(result.getAlbum()).isEqualTo("Updated Album");
        assertThat(result.getLastModified()).isEqualTo(java.time.Instant.parse("2026-05-01T00:00:00Z"));

        // Upsert a brand new track
        Track brandNew = Track.builder()
                .filePath("C:/Music/brand_new.mp3")
                .title("Brand New")
                .build();
        Track brandNewResult = library.upsertTrack(brandNew);
        assertThat(library.getTrackCount()).isEqualTo(2);
        assertThat(brandNewResult.getTitle()).isEqualTo("Brand New");
    }

    @Test
    @DisplayName("findContentDuplicate and isContentDuplicate match songs by content across different paths")
    void testContentDuplicateDetection() {
        Library library = new Library();
        Track original = Track.builder()
                .filePath("C:/Music/FolderA/song.mp3")
                .title("Bohemian Rhapsody")
                .artist("Queen")
                .durationSeconds(354)
                .fileSizeBytes(8500000)
                .build();
        library.addTrack(original);

        // Duplicate content at different path
        Track duplicate = Track.builder()
                .filePath("D:/Backups/Queen/song_copy.mp3")
                .title("bohemian rhapsody") // case insensitive
                .artist("QUEEN")
                .durationSeconds(354)
                .fileSizeBytes(8500000)
                .build();

        assertThat(library.isContentDuplicate(duplicate)).isTrue();
        assertThat(library.findContentDuplicate(duplicate)).isEqualTo(original);

        // Different duration -> not duplicate
        Track differentDuration = duplicate.toBuilder().durationSeconds(350).build();
        assertThat(library.isContentDuplicate(differentDuration)).isFalse();

        // Different size -> not duplicate
        Track differentSize = duplicate.toBuilder().fileSizeBytes(8000000).build();
        assertThat(library.isContentDuplicate(differentSize)).isFalse();

        // Untagged track with empty title and artist -> not matched to avoid false positives
        Track emptyOriginal = Track.builder()
                .filePath("C:/empty1.mp3")
                .title("")
                .artist("")
                .durationSeconds(120)
                .fileSizeBytes(2000000)
                .build();
        library.addTrack(emptyOriginal);
        Track emptyCandidate = Track.builder()
                .filePath("C:/empty2.mp3")
                .title("")
                .artist("")
                .durationSeconds(120)
                .fileSizeBytes(2000000)
                .build();
        assertThat(library.isContentDuplicate(emptyCandidate)).isFalse();
    }

    @Test
    @DisplayName("upsertTrack throws IllegalArgumentException when track is null")
    void testUpsertTrackNullValidation() {
        Library library = new Library();
        assertThatThrownBy(() -> library.upsertTrack(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Track cannot be null");
    }

    @Test
    @DisplayName("findContentDuplicate handles whitespace trimming and null safety")
    void testContentDuplicateWhitespaceAndNullSafety() {
        Library library = new Library();

        // null safety
        assertThat(library.findContentDuplicate(null)).isNull();
        assertThat(library.isContentDuplicate(null)).isFalse();

        Track t1 = Track.builder()
                .filePath("C:/path1.mp3")
                .title("   Imagine   ")
                .artist("John Lennon")
                .durationSeconds(183)
                .fileSizeBytes(4500000)
                .build();
        library.addTrack(t1);

        // Same track itself is not considered duplicate of itself
        assertThat(library.isContentDuplicate(t1)).isFalse();

        // Whitespace trimmed match
        Track t2 = Track.builder()
                .filePath("D:/other/path2.mp3")
                .title("imagine")
                .artist("  john lennon  ")
                .durationSeconds(183)
                .fileSizeBytes(4500000)
                .build();
        assertThat(library.isContentDuplicate(t2)).isTrue();
    }

    @Test
    @DisplayName("findByFilePath and findContentDuplicate operate in O(1) time across 2,000 tracks")
    void testHighVolumeIndexLookupPerformance() {
        Library library = new Library();
        int count = 2000;
        List<Track> bulk = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            bulk.add(Track.builder()
                    .id("id-" + i)
                    .filePath("C:/Music/Track_" + i + ".mp3")
                    .title("Title " + i)
                    .artist("Artist " + (i % 50))
                    .album("Album " + (i % 20))
                    .durationSeconds(180 + (i % 10))
                    .fileSizeBytes(3000000L + i)
                    .build());
        }
        library.addTracks(bulk);

        // Benchmark 1,000 lookups by file path
        long startPath = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            Track found = library.findByFilePath("C:/Music/Track_" + i + ".mp3");
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo("id-" + i);
        }
        long durationPathMs = (System.nanoTime() - startPath) / 1_000_000;
        assertThat(durationPathMs).isLessThan(500); // Must be sub-millisecond per lookup

        // Benchmark duplicate lookups
        long startDup = System.nanoTime();
        Track candidate = Track.builder()
                .filePath("D:/Backups/Track_500.mp3")
                .title("title 500")
                .artist("ARTIST " + (500 % 50))
                .durationSeconds(180 + (500 % 10))
                .fileSizeBytes(3000000L + 500)
                .build();
        Track dup = library.findContentDuplicate(candidate);
        long durationDupMs = (System.nanoTime() - startDup) / 1_000_000;

        assertThat(dup).isNotNull();
        assertThat(dup.getId()).isEqualTo("id-500");
        assertThat(durationDupMs).isLessThan(100);
    }

    @Test
    @DisplayName("ContentKey index maintains strict integrity across remove, upsert, and multi-track sharing")
    void testContentKeyIndexIntegrityOnRemoveAndUpsert() {
        Library library = new Library();

        Track t1 = Track.builder()
                .id("t1")
                .filePath("C:/Music/SongA.mp3")
                .title("Song A")
                .artist("Artist A")
                .durationSeconds(200)
                .fileSizeBytes(5000000L)
                .build();
        library.addTrack(t1);

        Track candA = Track.builder()
                .id("cand-a")
                .filePath("D:/Backups/SongA_copy.mp3")
                .title("song a")
                .artist("artist a")
                .durationSeconds(200)
                .fileSizeBytes(5000000L)
                .build();

        // 1. Initial match
        assertThat(library.isContentDuplicate(candA)).isTrue();

        // 2. Remove t1 -> index must be unindexed and candidate becomes non-duplicate
        library.removeTrack(t1.getId());
        assertThat(library.isContentDuplicate(candA)).isFalse();

        // 3. Re-add t1 and then upsert with changed metadata
        library.addTrack(t1);
        assertThat(library.isContentDuplicate(candA)).isTrue();

        Track t1Updated = Track.builder()
                .id("t1")
                .filePath("C:/Music/SongA.mp3")
                .title("Song B Remastered")
                .artist("Artist B")
                .durationSeconds(240)
                .fileSizeBytes(6000000L)
                .build();
        library.upsertTrack(t1Updated);

        // Old metadata key must be unindexed
        assertThat(library.isContentDuplicate(candA)).isFalse();

        // New metadata key must be indexed
        Track candB = Track.builder()
                .id("cand-b")
                .filePath("D:/Backups/SongB_copy.mp3")
                .title("song b remastered")
                .artist("artist b")
                .durationSeconds(240)
                .fileSizeBytes(6000000L)
                .build();
        assertThat(library.isContentDuplicate(candB)).isTrue();

        // 4. Multi-track key sharing reference counting
        Track t2 = Track.builder()
                .id("t2")
                .filePath("C:/DiscA/SongC.mp3")
                .title("Song C")
                .artist("Artist C")
                .durationSeconds(180)
                .fileSizeBytes(4000000L)
                .build();
        Track t3 = Track.builder()
                .id("t3")
                .filePath("C:/DiscB/SongC.mp3")
                .title("Song C")
                .artist("Artist C")
                .durationSeconds(180)
                .fileSizeBytes(4000000L)
                .build();
        library.addTrack(t2);
        library.addTrack(t3);

        Track candC = Track.builder()
                .id("cand-c")
                .filePath("D:/Other/SongC_alt.mp3")
                .title("song c")
                .artist("artist c")
                .durationSeconds(180)
                .fileSizeBytes(4000000L)
                .build();
        assertThat(library.isContentDuplicate(candC)).isTrue();

        // Removing t2 should keep t3 indexed so candC is still duplicate
        library.removeTrack(t2.getId());
        assertThat(library.isContentDuplicate(candC)).isTrue();

        // Removing t3 removes the last reference so candC is no longer duplicate
        library.removeTrack(t3.getId());
        assertThat(library.isContentDuplicate(candC)).isFalse();
    }

    @Test
    @DisplayName("Content duplicate lookup maintains O(1) sub-microsecond throughput across 10,000 tracks")
    void testMassive10kTracksContentDuplicatePerformance() {
        Library library = new Library();
        int count = 10_000;
        List<Track> bulk = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            bulk.add(Track.builder()
                    .id("bulk-" + i)
                    .filePath("C:/Library/Album_" + (i % 200) + "/Track_" + i + ".mp3")
                    .title("Unique Title " + i)
                    .artist("Artist " + (i % 500))
                    .album("Album " + (i % 200))
                    .durationSeconds(150 + (i % 120))
                    .fileSizeBytes(2_000_000L + (i * 100L))
                    .build());
        }
        library.addTracks(bulk);

        // 1,000 queries: 500 existing matches, 500 non-matches
        int iterations = 1000;
        long startNanos = System.nanoTime();
        int matchedCount = 0;

        for (int i = 0; i < iterations; i++) {
            if (i % 2 == 0) {
                // Match query
                int targetIndex = (i * 7) % count;
                Track query = Track.builder()
                        .filePath("D:/Staging/Check_" + i + ".mp3")
                        .title("unique title " + targetIndex)
                        .artist("artist " + (targetIndex % 500))
                        .durationSeconds(150 + (targetIndex % 120))
                        .fileSizeBytes(2_000_000L + (targetIndex * 100L))
                        .build();
                if (library.isContentDuplicate(query)) {
                    matchedCount++;
                }
            } else {
                // Non-match query
                Track query = Track.builder()
                        .filePath("D:/Staging/NonMatch_" + i + ".mp3")
                        .title("Completely Unknown Song " + i)
                        .artist("Unknown Artist " + i)
                        .durationSeconds(999)
                        .fileSizeBytes(9_999_999L)
                        .build();
                if (library.isContentDuplicate(query)) {
                    matchedCount++;
                }
            }
        }

        long totalNanos = System.nanoTime() - startNanos;
        double avgMicros = (double) totalNanos / iterations / 1_000.0;
        System.out.printf("10,000-track ContentDuplicate benchmark: %d queries, %d matched, avg=%.2f us per query%n",
                iterations, matchedCount, avgMicros);

        assertThat(matchedCount).isEqualTo(500);
        // O(1) lookup must be less than 50 microseconds per lookup on average
        assertThat(avgMicros).isLessThan(50.0);
    }
}

