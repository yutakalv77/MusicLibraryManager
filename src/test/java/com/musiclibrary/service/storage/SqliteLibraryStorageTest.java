package com.musiclibrary.service.storage;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteLibraryStorageTest {

    private DatabaseManager dbManager;
    private SqliteLibraryStorage storage;

    @BeforeEach
    void setUp() {
        dbManager = new DatabaseManager(":memory:");
        storage = new SqliteLibraryStorage(dbManager, Account.DEFAULT_ACCOUNT_ID);
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("load on empty database returns fresh empty library")
    void testLoadEmpty() throws IOException {
        Library library = storage.load(null);
        assertThat(library).isNotNull();
        assertThat(library.getTrackCount()).isEqualTo(0);
        assertThat(library.getPlaylistCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("save and load round-trip preserves tracks, user data, and playlists")
    void testSaveAndLoadRoundTrip() throws IOException {
        Track t1 = Track.builder()
                .filePath("C:/Music/Song1.mp3")
                .title("Song 1")
                .artist("Artist 1")
                .album("Album 1")
                .rating(5)
                .playCount(12)
                .build();

        Track t2 = Track.builder()
                .filePath("C:/Music/Song2.mp3")
                .title("Song 2")
                .artist("Artist 2")
                .album("Album 2")
                .rating(3)
                .playCount(4)
                .build();

        Playlist pl = new Playlist("pl-1", "Favorites", List.of(t1.getId(), t2.getId()));

        Library original = new Library(List.of(t1, t2), List.of(pl));

        // Save to SQLite
        storage.save(original, null);

        // Load back from SQLite
        Library reloaded = storage.load(null);

        assertThat(reloaded.getTrackCount()).isEqualTo(2);
        assertThat(reloaded.getPlaylistCount()).isEqualTo(1);

        Track loadedT1 = reloaded.findByFilePath("C:/Music/Song1.mp3");
        assertThat(loadedT1).isNotNull();
        assertThat(loadedT1.getTitle()).isEqualTo("Song 1");
        assertThat(loadedT1.getRating()).isEqualTo(5);
        assertThat(loadedT1.getPlayCount()).isEqualTo(12);
        assertThat(loadedT1.getAccountTrackId()).isNotBlank();

        Playlist loadedPl = reloaded.getPlaylists().get(0);
        assertThat(loadedPl.getName()).isEqualTo("Favorites");
        assertThat(loadedPl.getTrackIds()).containsExactly(t1.getId(), t2.getId());
    }

    @Test
    @DisplayName("updateTrackUserData immediately updates database and persists across reloads")
    void testUpdateTrackUserData() throws IOException {
        Track track = Track.builder()
                .filePath("C:/Music/Dynamic.mp3")
                .title("Dynamic Song")
                .rating(1)
                .playCount(0)
                .build();

        Library lib = new Library();
        lib.addTrack(track);
        storage.save(lib, null);

        // Reload to get accountTrackId
        Library loaded = storage.load(null);
        Track loadedTrack = loaded.findByFilePath("C:/Music/Dynamic.mp3");
        assertThat(loadedTrack.getAccountTrackId()).isNotNull();

        // Pinpoint update
        loadedTrack.setRating(5);
        loadedTrack.setPlayCount(99);
        Instant now = Instant.now();
        loadedTrack.setLastPlayed(now);

        storage.updateTrackUserData(loadedTrack);

        // Reload and verify
        Library reloaded = storage.load(null);
        Track finalTrack = reloaded.findByFilePath("C:/Music/Dynamic.mp3");
        assertThat(finalTrack.getRating()).isEqualTo(5);
        assertThat(finalTrack.getPlayCount()).isEqualTo(99);
    }

    @Test
    @DisplayName("savePlaylist and deletePlaylist operate correctly on SQLite")
    void testPlaylistOperations() throws IOException {
        storage.load(null); // Ensure schema

        Playlist pl = Playlist.builder()
                .id("pl-fast")
                .name("Fast Beat")
                .position(0)
                .build();

        storage.savePlaylist(pl);

        Library lib1 = storage.load(null);
        assertThat(lib1.getPlaylistCount()).isEqualTo(1);
        assertThat(lib1.getPlaylists().get(0).getName()).isEqualTo("Fast Beat");

        storage.deletePlaylist("pl-fast");

        Library lib2 = storage.load(null);
        assertThat(lib2.getPlaylistCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("importTracks batch imports tracks into SQLite")
    void testImportTracks() throws IOException {
        Track t1 = Track.builder().filePath("C:/M/1.mp3").title("One").build();
        Track t2 = Track.builder().filePath("C:/M/2.mp3").title("Two").build();

        List<Track> imported = storage.importTracks(List.of(t1, t2));
        assertThat(imported).hasSize(2);

        Library loaded = storage.load(null);
        assertThat(loaded.getTrackCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Comprehensive round-trip preserves all audio metadata, tags, and stats exactly")
    void testFullMetadataAndPlaylistRoundTrip() throws IOException {
        Instant addedTime = Instant.parse("2026-01-15T10:30:00Z");
        Instant lastPlayedTime = Instant.parse("2026-02-20T18:45:00Z");
        Instant fileModifiedTime = Instant.parse("2025-12-01T12:00:00Z");

        Track richTrack = Track.builder()
                .filePath("C:/Music/RichTrack.mp3")
                .title("Complete Symphonic Allegro")
                .artist("Ludwig van Beethoven")
                .albumArtist("Vienna Philharmonic")
                .album("Symphony No. 9")
                .genre("Classical")
                .year(1824)
                .trackNumber(4)
                .discNumber(1)
                .bpm(132)
                .bitrate(320)
                .durationSeconds(1420)
                .fileSizeBytes(45000000)
                .rating(5)
                .playCount(77)
                .dateAdded(addedTime)
                .lastPlayed(lastPlayedTime)
                .lastModified(fileModifiedTime)
                .hasArtwork(true)
                .build();

        Playlist playlist = Playlist.builder()
                .id("pl-rich")
                .name("Masterworks")
                .position(0)
                .trackIds(List.of(richTrack.getId()))
                .build();

        Library lib = new Library(List.of(richTrack), List.of(playlist));
        storage.save(lib, null);

        // Load through a new fresh storage instance to verify pure database persistence
        SqliteLibraryStorage newStorageInstance = new SqliteLibraryStorage(dbManager, Account.DEFAULT_ACCOUNT_ID);
        Library reloaded = newStorageInstance.load(null);

        assertThat(reloaded.getTrackCount()).isEqualTo(1);
        Track loaded = reloaded.findByFilePath("C:/Music/RichTrack.mp3");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getTitle()).isEqualTo("Complete Symphonic Allegro");
        assertThat(loaded.getArtist()).isEqualTo("Ludwig van Beethoven");
        assertThat(loaded.getAlbumArtist()).isEqualTo("Vienna Philharmonic");
        assertThat(loaded.getAlbum()).isEqualTo("Symphony No. 9");
        assertThat(loaded.getGenre()).isEqualTo("Classical");
        assertThat(loaded.getYear()).isEqualTo(1824);
        assertThat(loaded.getTrackNumber()).isEqualTo(4);
        assertThat(loaded.getDiscNumber()).isEqualTo(1);
        assertThat(loaded.getBpm()).isEqualTo(132);
        assertThat(loaded.getBitrate()).isEqualTo(320);
        assertThat(loaded.getDurationSeconds()).isEqualTo(1420);
        assertThat(loaded.getFileSizeBytes()).isEqualTo(45000000);
        assertThat(loaded.getRating()).isEqualTo(5);
        assertThat(loaded.getPlayCount()).isEqualTo(77);
        assertThat(loaded.hasArtwork()).isTrue();

        assertThat(reloaded.getPlaylistCount()).isEqualTo(1);
        Playlist loadedPl = reloaded.getPlaylists().get(0);
        assertThat(loadedPl.getId()).isEqualTo("pl-rich");
        assertThat(loadedPl.getName()).isEqualTo("Masterworks");
        assertThat(loadedPl.getTrackIds()).containsExactly(richTrack.getId());
    }

    @Test
    @DisplayName("updateTrackUserData isolates modifications strictly to target track")
    void testPinpointUpdateIsolationAcrossMultipleTracks() throws IOException {
        Track t1 = Track.builder().filePath("C:/M/A.mp3").title("A").rating(1).playCount(1).build();
        Track t2 = Track.builder().filePath("C:/M/B.mp3").title("B").rating(2).playCount(2).build();
        Track t3 = Track.builder().filePath("C:/M/C.mp3").title("C").rating(3).playCount(3).build();

        storage.save(new Library(List.of(t1, t2, t3), List.of()), null);

        Library loaded = storage.load(null);
        Track targetB = loaded.findByFilePath("C:/M/B.mp3");
        assertThat(targetB).isNotNull();

        // Pinpoint modify ONLY track B
        targetB.setRating(5);
        targetB.setPlayCount(50);
        Instant now = Instant.now();
        targetB.setLastPlayed(now);
        storage.updateTrackUserData(targetB);

        // Reload and assert B was updated, while A and C remained untouched
        Library reloaded = storage.load(null);
        Track reloadedA = reloaded.findByFilePath("C:/M/A.mp3");
        Track reloadedB = reloaded.findByFilePath("C:/M/B.mp3");
        Track reloadedC = reloaded.findByFilePath("C:/M/C.mp3");

        assertThat(reloadedA.getRating()).isEqualTo(1);
        assertThat(reloadedA.getPlayCount()).isEqualTo(1);
        assertThat(reloadedA.getLastPlayed()).isNull();

        assertThat(reloadedB.getRating()).isEqualTo(5);
        assertThat(reloadedB.getPlayCount()).isEqualTo(50);
        assertThat(reloadedB.getLastPlayed()).isNotNull();

        assertThat(reloadedC.getRating()).isEqualTo(3);
        assertThat(reloadedC.getPlayCount()).isEqualTo(3);
        assertThat(reloadedC.getLastPlayed()).isNull();
    }

    @Test
    @DisplayName("Multiple accounts maintain absolute data isolation in shared SQLite database")
    void testMultiAccountDataIsolation() throws IOException {
        SqliteLibraryStorage aliceStorage = new SqliteLibraryStorage(dbManager, "alice");
        SqliteLibraryStorage bobStorage = new SqliteLibraryStorage(dbManager, "bob");

        Track sharedPhysicalTrack = Track.builder()
                .filePath("C:/Music/SharedFile.mp3")
                .title("Shared Audio")
                .build();

        Track aliceOnlyTrack = Track.builder()
                .filePath("C:/Music/AliceOnly.mp3")
                .title("Alice Song")
                .build();

        Track bobOnlyTrack = Track.builder()
                .filePath("C:/Music/BobOnly.mp3")
                .title("Bob Song")
                .build();

        // Alice imports SharedFile (rating 5, playCount 20) and AliceSong
        sharedPhysicalTrack.setRating(5);
        sharedPhysicalTrack.setPlayCount(20);
        Playlist alicePl = new Playlist("pl-alice", "Alice Favs", List.of(sharedPhysicalTrack.getId()));
        aliceStorage.save(new Library(List.of(sharedPhysicalTrack, aliceOnlyTrack), List.of(alicePl)), null);

        // Bob imports SharedFile (rating 1, playCount 2) and BobSong
        sharedPhysicalTrack.setRating(1);
        sharedPhysicalTrack.setPlayCount(2);
        Playlist bobPl = new Playlist("pl-bob", "Bob Jams", List.of(sharedPhysicalTrack.getId()));
        bobStorage.save(new Library(List.of(sharedPhysicalTrack, bobOnlyTrack), List.of(bobPl)), null);

        // 1. Verify Alice's isolated perspective
        Library aliceLib = aliceStorage.load(null);
        assertThat(aliceLib.getTrackCount()).isEqualTo(2);
        assertThat(aliceLib.findTracks("Alice Song")).hasSize(1);
        assertThat(aliceLib.findTracks("Bob Song")).isEmpty(); // Bob's track is invisible to Alice

        Track aliceShared = aliceLib.findByFilePath("C:/Music/SharedFile.mp3");
        assertThat(aliceShared).isNotNull();
        assertThat(aliceShared.getRating()).isEqualTo(5);
        assertThat(aliceShared.getPlayCount()).isEqualTo(20);

        assertThat(aliceLib.getPlaylistCount()).isEqualTo(1);
        assertThat(aliceLib.getPlaylists().get(0).getName()).isEqualTo("Alice Favs");

        // 2. Verify Bob's isolated perspective
        Library bobLib = bobStorage.load(null);
        assertThat(bobLib.getTrackCount()).isEqualTo(2);
        assertThat(bobLib.findTracks("Bob Song")).hasSize(1);
        assertThat(bobLib.findTracks("Alice Song")).isEmpty(); // Alice's track is invisible to Bob

        Track bobShared = bobLib.findByFilePath("C:/Music/SharedFile.mp3");
        assertThat(bobShared).isNotNull();
        assertThat(bobShared.getRating()).isEqualTo(1);
        assertThat(bobShared.getPlayCount()).isEqualTo(2);

        assertThat(bobLib.getPlaylistCount()).isEqualTo(1);
        assertThat(bobLib.getPlaylists().get(0).getName()).isEqualTo("Bob Jams");
    }

    @Test
    @DisplayName("Edge cases and null safety across SqliteLibraryStorage methods")
    void testEdgeCasesAndNullSafety() throws IOException {
        // Null library on save -> safely no-op
        storage.save(null, null);

        // Null track on updateTrackUserData -> safely no-op
        storage.updateTrackUserData(null);
        storage.updateTrackUserData(Track.builder().filePath("C:/test.mp3").build()); // accountTrackId is null

        // Null playlist on savePlaylist -> safely no-op
        storage.savePlaylist(null);

        // Null / blank on deletePlaylist -> safely no-op
        storage.deletePlaylist(null);
        storage.deletePlaylist("");
        storage.deletePlaylist("   ");

        // Null / empty importTracks
        assertThat(storage.importTracks(null)).isEmpty();
        assertThat(storage.importTracks(List.of())).isEmpty();

        // Null DatabaseManager throws NPE
        assertThatThrownBy(() -> new SqliteLibraryStorage(null))
                .isInstanceOf(NullPointerException.class);

        // Blank or null accountId defaults to default account
        SqliteLibraryStorage fallbackStorage = new SqliteLibraryStorage(dbManager, "   ");
        assertThat(fallbackStorage.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
    }
}
