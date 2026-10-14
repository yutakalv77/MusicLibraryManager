package com.musiclibrary.service.storage;

import com.google.gson.JsonSyntaxException;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonLibraryStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Round-trip save and load preserves all library data and Instant timestamps")
    void testSaveAndLoadRoundTrip() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library original = new Library();

        Instant timestamp = Instant.parse("2026-10-13T12:34:56.789Z");
        Track track1 = Track.builder()
                .filePath("C:/Music/song1.mp3")
                .title("Song One")
                .artist("Artist One")
                .album("Album One")
                .genre("Rock")
                .year(2024)
                .trackNumber(1)
                .durationSeconds(240)
                .fileSizeBytes(8000000)
                .rating(5)
                .playCount(12)
                .dateAdded(timestamp)
                .artworkData(new byte[]{10, 20, 30})
                .build();

        Track track2 = Track.builder()
                .filePath("C:/Music/song2.mp3")
                .title("Song Two")
                .artist("Artist Two")
                .rating(3)
                .playCount(4)
                .build();

        original.addTracks(List.of(track1, track2));

        Playlist playlist = original.createPlaylist("Favorites");
        playlist.addTrack(track1.getId());
        playlist.addTrack(track2.getId());

        Path savePath = tempDir.resolve("library.json");
        storage.save(original, savePath);

        // Verify file exists and .tmp does not remain
        assertThat(Files.exists(savePath)).isTrue();
        Path tmpFile = tempDir.resolve("library.json.tmp");
        assertThat(Files.exists(tmpFile)).isFalse();

        // Verify JSON is pretty-printed and contains ISO-8601 string
        String jsonContent = Files.readString(savePath);
        assertThat(jsonContent).contains("\n");
        assertThat(jsonContent).contains("2026-10-13T12:34:56.789Z");

        // Load and verify equality
        Library loaded = storage.load(savePath);
        assertThat(loaded.getTrackCount()).isEqualTo(2);
        assertThat(loaded.getPlaylistCount()).isEqualTo(1);

        Track loadedTrack1 = loaded.getTrack(track1.getId());
        assertThat(loadedTrack1).isNotNull();
        assertThat(loadedTrack1.getTitle()).isEqualTo("Song One");
        assertThat(loadedTrack1.getArtist()).isEqualTo("Artist One");
        assertThat(loadedTrack1.getRating()).isEqualTo(5);
        assertThat(loadedTrack1.getPlayCount()).isEqualTo(12);
        assertThat(loadedTrack1.getDateAdded()).isEqualTo(timestamp);
        assertThat(loadedTrack1.getArtworkData()).containsExactly((byte) 10, (byte) 20, (byte) 30);

        Playlist loadedPlaylist = loaded.getPlaylist(playlist.getId());
        assertThat(loadedPlaylist).isNotNull();
        assertThat(loadedPlaylist.getName()).isEqualTo("Favorites");
        assertThat(loadedPlaylist.getTrackIds()).containsExactly(track1.getId(), track2.getId());
    }

    @Test
    @DisplayName("Round-trip save and load supports Japanese metadata, file paths, and special symbols (emojis, quotes, newlines)")
    void testJapaneseAndSpecialCharactersRoundTrip() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library original = new Library();

        String specialTitle = "夜に駆ける (Special \"Remix\" \\ ライブ版)\nSub-title 🎵★";
        String japaneseArtist = "YOASOBI / 米津玄師";
        String japaneseAlbum = "STRAY SHEEP (ストレイ・シープ) & THE BOOK";
        String japanesePath = "C:/音楽/プレイリスト/2026/夜に駆ける.flac";
        String playlistName = "お気に入り邦楽コレクション 🎧✨";

        Track track = Track.builder()
                .filePath(japanesePath)
                .title(specialTitle)
                .artist(japaneseArtist)
                .album(japaneseAlbum)
                .genre("J-POP / アニソン")
                .year(2025)
                .trackNumber(1)
                .durationSeconds(260)
                .fileSizeBytes(15000000)
                .rating(5)
                .playCount(88)
                .artworkData(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})
                .build();

        original.addTrack(track);

        Playlist playlist = original.createPlaylist(playlistName);
        playlist.addTrack(track.getId());

        Path savePath = tempDir.resolve("japanese_library.json");
        storage.save(original, savePath);

        Library loaded = storage.load(savePath);
        assertThat(loaded.getTrackCount()).isEqualTo(1);
        assertThat(loaded.getPlaylistCount()).isEqualTo(1);

        Track loadedTrack = loaded.getTrack(track.getId());
        assertThat(loadedTrack).isNotNull();
        assertThat(loadedTrack.getTitle()).isEqualTo(specialTitle);
        assertThat(loadedTrack.getArtist()).isEqualTo(japaneseArtist);
        assertThat(loadedTrack.getAlbum()).isEqualTo(japaneseAlbum);
        assertThat(loadedTrack.getFilePath()).isEqualTo(japanesePath);
        assertThat(loadedTrack.getArtworkData()).containsExactly((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);

        Playlist loadedPlaylist = loaded.getPlaylist(playlist.getId());
        assertThat(loadedPlaylist).isNotNull();
        assertThat(loadedPlaylist.getName()).isEqualTo(playlistName);
        assertThat(loadedPlaylist.getTrackIds()).containsExactly(track.getId());
    }

    @Test
    @DisplayName("Overwriting existing library file replaces data cleanly with no corruption or residue")
    void testOverwriteExistingFileCleanly() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path filePath = tempDir.resolve("overwrite_test.json");

        // First version: 2 tracks, 1 playlist
        Library v1 = new Library();
        Track t1 = Track.builder().id("t1").filePath("C:/t1.mp3").title("Track 1").build();
        Track t2 = Track.builder().id("t2").filePath("C:/t2.mp3").title("Track 2").build();
        v1.addTracks(List.of(t1, t2));
        v1.createPlaylist("Playlist V1");
        storage.save(v1, filePath);

        Library loadedV1 = storage.load(filePath);
        assertThat(loadedV1.getTrackCount()).isEqualTo(2);

        // Second version: 1 different track, 2 playlists
        Library v2 = new Library();
        Track t3 = Track.builder().id("t3").filePath("C:/t3.mp3").title("Track 3").build();
        v2.addTrack(t3);
        v2.createPlaylist("Playlist V2 - Alpha");
        v2.createPlaylist("Playlist V2 - Beta");
        storage.save(v2, filePath);

        // Verify overwrite
        Library loadedV2 = storage.load(filePath);
        assertThat(loadedV2.getTrackCount()).isEqualTo(1);
        assertThat(loadedV2.getPlaylistCount()).isEqualTo(2);
        assertThat(loadedV2.containsTrack("t1")).isFalse();
        assertThat(loadedV2.containsTrack("t2")).isFalse();
        assertThat(loadedV2.containsTrack("t3")).isTrue();

        // Temp file should not remain
        Path tmpFile = tempDir.resolve("overwrite_test.json.tmp");
        assertThat(Files.exists(tmpFile)).isFalse();
    }

    @Test
    @DisplayName("load throws JsonSyntaxException when encountering corrupt or malformed JSON")
    void testLoadMalformedJsonThrowsException() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path corruptJsonPath = tempDir.resolve("corrupt.json");

        Files.writeString(corruptJsonPath, "{ \"tracks\": { \"broken_entry\": ");

        assertThatThrownBy(() -> storage.load(corruptJsonPath))
                .isInstanceOf(JsonSyntaxException.class);
    }

    @Test
    @DisplayName("save creates deeply nested parent directories automatically")
    void testSaveCreatesDeeplyNestedDirectories() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library library = new Library();
        Track track = Track.builder().filePath("C:/music.mp3").title("Test").build();
        library.addTrack(track);

        Path deeplyNestedPath = tempDir.resolve("dir1")
                .resolve("dir2")
                .resolve("dir3")
                .resolve("dir4")
                .resolve("nested_library.json");

        storage.save(library, deeplyNestedPath);

        assertThat(Files.exists(deeplyNestedPath)).isTrue();
        Library loaded = storage.load(deeplyNestedPath);
        assertThat(loaded.getTrackCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("load returns empty Library if file does not exist")
    void testLoadNonExistentFileReturnsEmptyLibrary() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path nonExistent = tempDir.resolve("not_found.json");

        Library library = storage.load(nonExistent);
        assertThat(library).isNotNull();
        assertThat(library.getTrackCount()).isEqualTo(0);
        assertThat(library.getPlaylistCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("load returns empty Library if file is 0 bytes")
    void testLoadEmptyFileReturnsEmptyLibrary() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path emptyFile = tempDir.resolve("empty.json");
        Files.createFile(emptyFile);

        Library library = storage.load(emptyFile);
        assertThat(library).isNotNull();
        assertThat(library.getTrackCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Validations on null arguments")
    void testArgumentValidations() {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library library = new Library();
        Path path = tempDir.resolve("test.json");

        assertThatThrownBy(() -> storage.save(null, path))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.save(library, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.load(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hasArtwork is persisted to JSON and artworkData is omitted when null, drastically slimming JSON size")
    void testHasArtworkSavedWithoutArtworkDataInJson() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library library = new Library();
        Track track = Track.builder()
                .filePath("C:/music/track_with_flag.mp3")
                .title("Slim Track")
                .hasArtwork(true)
                .build();
        library.addTrack(track);

        Path savePath = tempDir.resolve("slim_library.json");
        storage.save(library, savePath);

        String json = Files.readString(savePath);
        assertThat(json).contains("\"hasArtwork\": true");
        assertThat(json).doesNotContain("\"artworkData\"");

        Library loaded = storage.load(savePath);
        Track loadedTrack = loaded.getTrack(track.getId());
        assertThat(loadedTrack).isNotNull();
        assertThat(loadedTrack.hasArtwork()).isTrue();
    }

    @Test
    @DisplayName("Deserialization supports legacy numerical byte array format [10, 20, 30] for backward compatibility")
    void testLegacyNumericalArrayArtworkDataDeserialization() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path legacyJsonPath = tempDir.resolve("legacy_array.json");

        String legacyJson = """
                {
                  "tracks": {
                    "legacy-track-1": {
                      "id": "legacy-track-1",
                      "filePath": "C:/music/legacy.mp3",
                      "title": "Legacy Track",
                      "hasArtwork": true,
                      "artworkData": [10, 20, 30]
                    }
                  },
                  "playlists": []
                }
                """;
        Files.writeString(legacyJsonPath, legacyJson);

        Library loaded = storage.load(legacyJsonPath);
        Track loadedTrack = loaded.getTrack("legacy-track-1");
        assertThat(loadedTrack).isNotNull();
        assertThat(loadedTrack.hasArtwork()).isTrue();
        assertThat(loadedTrack.getArtworkData()).containsExactly((byte) 10, (byte) 20, (byte) 30);
    }

    @Test
    @DisplayName("save serializes artworkData as Base64 string and load restores exact binary data")
    void testBase64ArtworkDataRoundTrip() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Library library = new Library();

        byte[] fakeJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00};
        Track track = Track.builder()
                .filePath("C:/music/base64_test.mp3")
                .title("Base64 Song")
                .artworkData(fakeJpeg)
                .build();
        library.addTrack(track);

        Path jsonPath = tempDir.resolve("base64_library.json");
        storage.save(library, jsonPath);

        // 1. Verify JSON file contains Base64 encoded string and NOT a numerical JSON array
        String json = Files.readString(jsonPath);
        String base64Prefix = java.util.Base64.getEncoder().encodeToString(fakeJpeg).replace("=", "");
        assertThat(json).contains("\"artworkData\": \"");
        assertThat(json).contains(base64Prefix);
        assertThat(json).doesNotContain("\"artworkData\": [");

        // 2. Load and verify 100% binary fidelity
        Library loaded = storage.load(jsonPath);
        Track loadedTrack = loaded.getTrack(track.getId());
        assertThat(loadedTrack).isNotNull();
        assertThat(loadedTrack.hasArtwork()).isTrue();
        assertThat(loadedTrack.getArtworkData()).containsExactly(fakeJpeg);
    }

    @Test
    @DisplayName("load supports legacy numerical array with signed and unsigned bytes, empty array, empty base64 string, and null")
    void testLegacyNumericalArrayExtendedCompatibility() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path jsonPath = tempDir.resolve("legacy_extended.json");

        // Signed (-128 to 127) and unsigned (0 to 255) numbers in legacy array, empty array, null
        String json = """
                {
                  "tracks": {
                    "signed-track": {
                      "id": "signed-track",
                      "filePath": "C:/music/signed.mp3",
                      "title": "Signed Legacy",
                      "hasArtwork": true,
                      "artworkData": [-1, -40, -1, 32, 0, 100]
                    },
                    "unsigned-track": {
                      "id": "unsigned-track",
                      "filePath": "C:/music/unsigned.mp3",
                      "title": "Unsigned Legacy",
                      "hasArtwork": true,
                      "artworkData": [255, 216, 255, 32, 0, 100]
                    },
                    "empty-array-track": {
                      "id": "empty-array-track",
                      "filePath": "C:/music/empty.mp3",
                      "title": "Empty Array Track",
                      "hasArtwork": false,
                      "artworkData": []
                    },
                    "empty-string-base64-track": {
                      "id": "empty-string-base64-track",
                      "filePath": "C:/music/empty_b64.mp3",
                      "title": "Empty Base64 Track",
                      "hasArtwork": false,
                      "artworkData": ""
                    },
                    "null-artwork-track": {
                      "id": "null-artwork-track",
                      "filePath": "C:/music/null.mp3",
                      "title": "Null Art Track",
                      "hasArtwork": false,
                      "artworkData": null
                    }
                  },
                  "playlists": []
                }
                """;
        Files.writeString(jsonPath, json);

        Library loaded = storage.load(jsonPath);

        // signed-track: -1 as byte is (byte)0xFF, -40 is (byte)0xD8, etc.
        Track signedTrack = loaded.getTrack("signed-track");
        assertThat(signedTrack).isNotNull();
        assertThat(signedTrack.getArtworkData()).containsExactly((byte) -1, (byte) -40, (byte) -1, (byte) 32, (byte) 0, (byte) 100);

        // unsigned-track: 255 cast to (byte) is -1 ((byte)0xFF)
        Track unsignedTrack = loaded.getTrack("unsigned-track");
        assertThat(unsignedTrack).isNotNull();
        assertThat(unsignedTrack.getArtworkData()).containsExactly((byte) 255, (byte) 216, (byte) 255, (byte) 32, (byte) 0, (byte) 100);

        // empty-array-track
        Track emptyTrack = loaded.getTrack("empty-array-track");
        assertThat(emptyTrack).isNotNull();
        assertThat(emptyTrack.getArtworkData()).isEmpty();

        // empty-string-base64-track
        Track emptyB64Track = loaded.getTrack("empty-string-base64-track");
        assertThat(emptyB64Track).isNotNull();
        assertThat(emptyB64Track.getArtworkData()).isEmpty();

        // null-artwork-track
        Track nullTrack = loaded.getTrack("null-artwork-track");
        assertThat(nullTrack).isNotNull();
        assertThat(nullTrack.getArtworkData()).isNull();
    }

    @Test
    @DisplayName("save creates .bak backup file when target library already exists")
    void testBackupCreatedOnSave() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path targetPath = tempDir.resolve("library.json");
        Path backupPath = tempDir.resolve("library.json.bak");

        Library lib1 = new Library();
        lib1.addTrack(Track.builder().filePath("C:/music/1.mp3").title("First Version").build());
        storage.save(lib1, targetPath);

        assertThat(Files.exists(targetPath)).isTrue();
        assertThat(Files.exists(backupPath)).isFalse();

        // Second save should backup the first version to .bak
        Library lib2 = new Library();
        lib2.addTrack(Track.builder().filePath("C:/music/2.mp3").title("Second Version").build());
        storage.save(lib2, targetPath);

        assertThat(Files.exists(backupPath)).isTrue();
        Library loadedBackup = storage.load(backupPath);
        assertThat(loadedBackup.getTrackCount()).isEqualTo(1);
        assertThat(loadedBackup.findTracks("First Version")).hasSize(1);
    }

    @Test
    @DisplayName("Safeguard preserves populated backup when saving empty library over populated library")
    void testEmptyDataSafeguardPreservesPopulatedBackup() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path targetPath = tempDir.resolve("library.json");
        Path backupPath = tempDir.resolve("library.json.bak");

        // 1. Save populated library
        Library populated = new Library();
        for (int i = 0; i < 10; i++) {
            populated.addTrack(Track.builder().filePath("C:/music/track" + i + ".mp3").title("Track " + i).build());
        }
        storage.save(populated, targetPath);
        assertThat(Files.size(targetPath)).isGreaterThan(JsonLibraryStorage.EMPTY_JSON_THRESHOLD_BYTES);

        // 2. Save 0-track library
        Library empty = new Library();
        storage.save(empty, targetPath);

        // Primary is saved, but backup exists and holds the populated data
        assertThat(Files.exists(backupPath)).isTrue();
        assertThat(Files.size(backupPath)).isGreaterThan(JsonLibraryStorage.EMPTY_JSON_THRESHOLD_BYTES);
        Library backupLib = storage.load(backupPath);
        assertThat(backupLib.getTrackCount()).isEqualTo(10);

        // 3. Loading primary auto-recovers from populated backup!
        Library recovered = storage.load(targetPath);
        assertThat(recovered.getTrackCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("load automatically recovers library from .bak when primary file is corrupt or empty")
    void testAutoRecoveryFromBackupWhenPrimaryCorrupted() throws IOException {
        JsonLibraryStorage storage = new JsonLibraryStorage();
        Path targetPath = tempDir.resolve("library.json");
        Path backupPath = tempDir.resolve("library.json.bak");

        // Prepare backup with 2 tracks
        Library goodLibrary = new Library();
        goodLibrary.addTrack(Track.builder().filePath("C:/music/t1.mp3").title("Track One").build());
        goodLibrary.addTrack(Track.builder().filePath("C:/music/t2.mp3").title("Track Two").build());
        storage.save(goodLibrary, backupPath);

        // Corrupt primary file
        Files.writeString(targetPath, "BROKEN JSON CONTENT");

        // load(targetPath) should transparently recover from backup
        Library loaded = storage.load(targetPath);
        assertThat(loaded.getTrackCount()).isEqualTo(2);
        assertThat(loaded.findTracks("Track One")).hasSize(1);
        assertThat(loaded.findTracks("Track Two")).hasSize(1);
    }
}
