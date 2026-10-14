package com.musiclibrary.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Default constructor should assign UUID, default non-null fields, and current timestamp")
    void testDefaultConstructor() {
        Track track = new Track();

        assertThat(track.getId()).isNotBlank();
        assertThat(track.getFilePath()).isEqualTo("");
        assertThat(track.getTitle()).isEqualTo("");
        assertThat(track.getArtist()).isEqualTo("");
        assertThat(track.getAlbum()).isEqualTo("");
        assertThat(track.getGenre()).isEqualTo("");
        assertThat(track.getYear()).isEqualTo(0);
        assertThat(track.getTrackNumber()).isEqualTo(0);
        assertThat(track.getDurationSeconds()).isEqualTo(0L);
        assertThat(track.getFileSizeBytes()).isEqualTo(0L);
        assertThat(track.getRating()).isEqualTo(0);
        assertThat(track.getPlayCount()).isEqualTo(0);
        assertThat(track.getDateAdded()).isNotNull();
        assertThat(track.getArtworkData()).isNull();
        assertThat(track.hasArtwork()).isFalse();
    }

    @Test
    @DisplayName("Builder pattern builds Track with specified fields")
    void testBuilder() {
        byte[] artwork = new byte[]{1, 2, 3, 4};
        Instant now = Instant.now();
        String customId = UUID.randomUUID().toString();

        Track track = Track.builder()
                .id(customId)
                .filePath("C:/Music/test.mp3")
                .title("Test Title")
                .artist("Test Artist")
                .album("Test Album")
                .genre("Rock")
                .year(2023)
                .trackNumber(1)
                .durationSeconds(215)
                .fileSizeBytes(5000000)
                .rating(5)
                .playCount(10)
                .dateAdded(now)
                .artworkData(artwork)
                .build();

        assertThat(track.getId()).isEqualTo(customId);
        assertThat(track.getFilePath()).isEqualTo("C:/Music/test.mp3");
        assertThat(track.getTitle()).isEqualTo("Test Title");
        assertThat(track.getArtist()).isEqualTo("Test Artist");
        assertThat(track.getAlbum()).isEqualTo("Test Album");
        assertThat(track.getGenre()).isEqualTo("Rock");
        assertThat(track.getYear()).isEqualTo(2023);
        assertThat(track.getTrackNumber()).isEqualTo(1);
        assertThat(track.getDurationSeconds()).isEqualTo(215L);
        assertThat(track.getFileSizeBytes()).isEqualTo(5000000L);
        assertThat(track.getRating()).isEqualTo(5);
        assertThat(track.getPlayCount()).isEqualTo(10);
        assertThat(track.getDateAdded()).isEqualTo(now);
        assertThat(track.getArtworkData()).containsExactly(1, 2, 3, 4);
        assertThat(track.hasArtwork()).isTrue();
    }

    @Test
    @DisplayName("Builder falls back to auto-generated UUID if id is null or blank")
    void testBuilderIdFallback() {
        Track trackWithNullId = Track.builder().filePath("C:/Music/song.mp3").id(null).build();
        assertThat(trackWithNullId.getId()).isNotBlank();

        Track trackWithBlankId = Track.builder().filePath("C:/Music/song.mp3").id("   ").build();
        assertThat(trackWithBlankId.getId()).isNotBlank();
    }

    @Test
    @DisplayName("Builder rejects build if filePath is null or blank")
    void testBuilderFilePathValidation() {
        assertThatThrownBy(() -> Track.builder().build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Track.builder().filePath(null).build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Track.builder().filePath("   ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("setId rejects null and blank strings")
    void testSetIdValidation() {
        Track track = Track.builder().filePath("C:/test.mp3").build();

        assertThatThrownBy(() -> track.setId(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> track.setId("   "))
                .isInstanceOf(IllegalArgumentException.class);

        track.setId("custom-valid-id");
        assertThat(track.getId()).isEqualTo("custom-valid-id");
    }

    @Test
    @DisplayName("toBuilder creates mutable builder keeping current values")
    void testToBuilder() {
        Track track = Track.builder()
                .filePath("C:/Music/original.mp3")
                .title("Original Title")
                .artist("Original Artist")
                .rating(3)
                .build();

        Track modified = track.toBuilder()
                .title("Updated Title")
                .rating(4)
                .build();

        assertThat(modified.getId()).isEqualTo(track.getId());
        assertThat(modified.getFilePath()).isEqualTo(track.getFilePath());
        assertThat(modified.getTitle()).isEqualTo("Updated Title");
        assertThat(modified.getArtist()).isEqualTo("Original Artist");
        assertThat(modified.getRating()).isEqualTo(4);
    }

    @Test
    @DisplayName("toBuilder guarantees deep copy of artwork data across instances")
    void testToBuilderDeepCopyArtworkData() {
        byte[] originalArtwork = new byte[]{10, 20, 30};
        Track track1 = Track.builder()
                .filePath("C:/Music/test.mp3")
                .artworkData(originalArtwork)
                .build();

        Track track2 = track1.toBuilder().build();

        // Mutating track1 artwork should not affect track2
        track1.setArtworkData(new byte[]{99, 99});
        assertThat(track2.getArtworkData()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("Artwork data is defensively copied on get, set, and builder")
    void testDefensiveCopyArtworkData() {
        byte[] original = new byte[]{10, 20, 30};

        Track track = Track.builder()
                .filePath("C:/music.mp3")
                .artworkData(original)
                .build();

        // Mutate original array
        original[0] = 99;
        assertThat(track.getArtworkData()[0]).isEqualTo((byte) 10);

        // Mutate returned array
        byte[] retrieved = track.getArtworkData();
        retrieved[1] = 88;
        assertThat(track.getArtworkData()[1]).isEqualTo((byte) 20);

        // Set new array and mutate it
        byte[] newArtwork = new byte[]{40, 50};
        track.setArtworkData(newArtwork);
        newArtwork[0] = 77;
        assertThat(track.getArtworkData()[0]).isEqualTo((byte) 40);

        // Set null artwork
        track.setArtworkData(null);
        assertThat(track.getArtworkData()).isNull();
        assertThat(track.hasArtwork()).isFalse();

        // Set empty array artwork
        track.setArtworkData(new byte[0]);
        assertThat(track.getArtworkData()).isEmpty();
        assertThat(track.hasArtwork()).isFalse(); // length == 0 implies hasArtwork is false
    }

    @Test
    @DisplayName("Validation for filePath rejects null and blank strings on setter")
    void testFilePathSetterValidation() {
        Track track = Track.builder().filePath("C:/test.mp3").build();

        assertThatThrownBy(() -> track.setFilePath(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> track.setFilePath(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> track.setFilePath("   "))
                .isInstanceOf(IllegalArgumentException.class);

        track.setFilePath("C:/new_path.mp3");
        assertThat(track.getFilePath()).isEqualTo("C:/new_path.mp3");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    @DisplayName("Valid rating values (0 to 5) are accepted by setter and builder")
    void testRatingValidValues(int validRating) {
        Track track = Track.builder().filePath("C:/test.mp3").rating(validRating).build();
        assertThat(track.getRating()).isEqualTo(validRating);

        track.setRating(validRating);
        assertThat(track.getRating()).isEqualTo(validRating);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -10, 6, 10})
    @DisplayName("Rating must be between 0 and 5, invalid values throw IllegalArgumentException")
    void testRatingValidation(int invalidRating) {
        assertThatThrownBy(() -> Track.builder().filePath("C:/test.mp3").rating(invalidRating).build())
                .isInstanceOf(IllegalArgumentException.class);

        Track track = Track.builder().filePath("C:/test.mp3").build();
        assertThatThrownBy(() -> track.setRating(invalidRating))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Negative numbers are rejected for counts and sizes in setters and builder")
    void testNegativeValidation() {
        Track track = Track.builder().filePath("C:/test.mp3").build();

        assertThatThrownBy(() -> track.setYear(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> track.setTrackNumber(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> track.setDurationSeconds(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> track.setFileSizeBytes(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> track.setPlayCount(-1))
                .isInstanceOf(IllegalArgumentException.class);

        // Builder negative value checks
        assertThatThrownBy(() -> Track.builder().filePath("C:/test.mp3").year(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Track.builder().filePath("C:/test.mp3").trackNumber(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Track.builder().filePath("C:/test.mp3").durationSeconds(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Track.builder().filePath("C:/test.mp3").fileSizeBytes(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Track.builder().filePath("C:/test.mp3").playCount(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Boundary values (0) are accepted for year, trackNumber, durationSeconds, fileSizeBytes, playCount")
    void testZeroBoundaryValidation() {
        Track track = Track.builder()
                .filePath("C:/test.mp3")
                .year(0)
                .trackNumber(0)
                .durationSeconds(0)
                .fileSizeBytes(0)
                .playCount(0)
                .build();

        assertThat(track.getYear()).isEqualTo(0);
        assertThat(track.getTrackNumber()).isEqualTo(0);
        assertThat(track.getDurationSeconds()).isEqualTo(0L);
        assertThat(track.getFileSizeBytes()).isEqualTo(0L);
        assertThat(track.getPlayCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Null string fields are safely normalized to empty string")
    void testNullStringFieldNormalization() {
        Track track = Track.builder()
                .filePath("C:/test.mp3")
                .title(null)
                .artist(null)
                .album(null)
                .genre(null)
                .build();

        assertThat(track.getTitle()).isEqualTo("");
        assertThat(track.getArtist()).isEqualTo("");
        assertThat(track.getAlbum()).isEqualTo("");
        assertThat(track.getGenre()).isEqualTo("");

        track.setTitle(null);
        track.setArtist(null);
        track.setAlbum(null);
        track.setGenre(null);

        assertThat(track.getTitle()).isEqualTo("");
        assertThat(track.getArtist()).isEqualTo("");
        assertThat(track.getAlbum()).isEqualTo("");
        assertThat(track.getGenre()).isEqualTo("");
    }

    @Test
    @DisplayName("dateAdded defaults to non-null Instant if null is passed")
    void testDateAddedNullSafety() {
        Track track = Track.builder()
                .filePath("C:/test.mp3")
                .dateAdded(null)
                .build();

        assertThat(track.getDateAdded()).isNotNull();

        track.setDateAdded(null);
        assertThat(track.getDateAdded()).isNotNull();

        Instant customInstant = Instant.parse("2025-01-01T00:00:00Z");
        track.setDateAdded(customInstant);
        assertThat(track.getDateAdded()).isEqualTo(customInstant);
    }

    @Test
    @DisplayName("incrementPlayCount correctly increases play count by 1")
    void testIncrementPlayCount() {
        Track track = Track.builder().filePath("C:/test.mp3").playCount(3).build();
        track.incrementPlayCount();
        assertThat(track.getPlayCount()).isEqualTo(4);

        track.incrementPlayCount();
        assertThat(track.getPlayCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("equals and hashCode contracts are satisfied")
    void testEqualsAndHashCode() {
        String sameId = UUID.randomUUID().toString();
        Track track1 = Track.builder().id(sameId).filePath("C:/path1.mp3").title("Song 1").build();
        Track track2 = Track.builder().id(sameId).filePath("C:/path2.mp3").title("Song 2").build();
        Track track3 = Track.builder().filePath("C:/path1.mp3").title("Song 1").build();

        // Reflexivity
        assertThat(track1).isEqualTo(track1);

        // Symmetry & consistency
        assertThat(track1).isEqualTo(track2);
        assertThat(track2).isEqualTo(track1);
        assertThat(track1.hashCode()).isEqualTo(track2.hashCode());

        // Inequality
        assertThat(track1).isNotEqualTo(track3);
        assertThat(track1).isNotEqualTo(null);
        assertThat(track1).isNotEqualTo("some string");
    }

    @Test
    @DisplayName("toString provides detailed information including artwork length or null")
    void testToString() {
        Track trackWithoutArtwork = Track.builder().filePath("C:/song.mp3").title("Song A").build();
        assertThat(trackWithoutArtwork.toString())
                .contains("title='Song A'")
                .contains("artworkData=null");

        Track trackWithArtwork = Track.builder()
                .filePath("C:/song.mp3")
                .artworkData(new byte[]{1, 2, 3})
                .build();
        assertThat(trackWithArtwork.toString())
                .contains("artworkData=[3 bytes]");
    }

    @Test
    @DisplayName("Gson serialization and deserialization works seamlessly")
    void testGsonSerialization() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new com.google.gson.TypeAdapter<Instant>() {
                    @Override
                    public void write(com.google.gson.stream.JsonWriter out, Instant value) throws java.io.IOException {
                        if (value == null) {
                            out.nullValue();
                        } else {
                            out.value(value.toString());
                        }
                    }

                    @Override
                    public Instant read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        return Instant.parse(in.nextString());
                    }
                })
                .create();
        Track original = Track.builder()
                .filePath("C:/Music/test.mp3")
                .title("Gson Song")
                .artist("Gson Artist")
                .album("Gson Album")
                .genre("Pop")
                .year(2024)
                .trackNumber(3)
                .durationSeconds(180)
                .fileSizeBytes(4000000)
                .rating(5)
                .playCount(42)
                .artworkData(new byte[]{10, 20, 30})
                .build();

        String json = gson.toJson(original);
        Track deserialized = gson.fromJson(json, Track.class);

        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getFilePath()).isEqualTo(original.getFilePath());
        assertThat(deserialized.getTitle()).isEqualTo(original.getTitle());
        assertThat(deserialized.getArtist()).isEqualTo(original.getArtist());
        assertThat(deserialized.getAlbum()).isEqualTo(original.getAlbum());
        assertThat(deserialized.getGenre()).isEqualTo(original.getGenre());
        assertThat(deserialized.getYear()).isEqualTo(original.getYear());
        assertThat(deserialized.getTrackNumber()).isEqualTo(original.getTrackNumber());
        assertThat(deserialized.getDurationSeconds()).isEqualTo(original.getDurationSeconds());
        assertThat(deserialized.getFileSizeBytes()).isEqualTo(original.getFileSizeBytes());
        assertThat(deserialized.getRating()).isEqualTo(original.getRating());
        assertThat(deserialized.getPlayCount()).isEqualTo(original.getPlayCount());
        assertThat(deserialized.getDateAdded()).isEqualTo(original.getDateAdded());
        assertThat(deserialized.getLastModified()).isEqualTo(original.getLastModified());
        assertThat(deserialized.getArtworkData()).containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("lastModified field supports Getter, Setter, null fallback to EPOCH, and toBuilder preservation")
    void testLastModifiedFieldBehavior() {
        Track track = new Track();
        assertThat(track.getLastModified()).isEqualTo(Instant.EPOCH);

        Instant modified = Instant.parse("2026-10-13T10:15:30Z");
        track.setLastModified(modified);
        assertThat(track.getLastModified()).isEqualTo(modified);

        // Null safely defaults to Instant.EPOCH
        track.setLastModified(null);
        assertThat(track.getLastModified()).isEqualTo(Instant.EPOCH);

        // Builder & toBuilder verification
        Track built = Track.builder()
                .filePath("C:/Music/test.mp3")
                .lastModified(modified)
                .build();
        assertThat(built.getLastModified()).isEqualTo(modified);

        Track copied = built.toBuilder().build();
        assertThat(copied.getLastModified()).isEqualTo(modified);

        Track nullBuilt = Track.builder()
                .filePath("C:/Music/test.mp3")
                .lastModified(null)
                .build();
        assertThat(nullBuilt.getLastModified()).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("hasArtwork flag synchronizes with setArtworkData, Builder, and toBuilder")
    void testHasArtworkAndArtworkDataSynchronization() {
        Track track = new Track();
        assertThat(track.hasArtwork()).isFalse();

        track.setArtworkData(new byte[]{1, 2, 3});
        assertThat(track.hasArtwork()).isTrue();
        assertThat(track.getArtworkData()).containsExactly((byte) 1, (byte) 2, (byte) 3);

        track.setArtworkData(null);
        assertThat(track.hasArtwork()).isFalse();
        assertThat(track.getArtworkData()).isNull();

        track.setHasArtwork(true);
        assertThat(track.hasArtwork()).isTrue();

        // Builder with hasArtwork flag only
        Track flagOnlyTrack = Track.builder()
                .filePath("dummy.mp3")
                .hasArtwork(true)
                .build();
        assertThat(flagOnlyTrack.hasArtwork()).isTrue();

        // Builder with artworkData automatically sets hasArtwork = true
        Track artTrack = Track.builder()
                .filePath("dummy.mp3")
                .artworkData(new byte[]{9, 8, 7})
                .build();
        assertThat(artTrack.hasArtwork()).isTrue();

        // toBuilder preserves hasArtwork
        Track copied = flagOnlyTrack.toBuilder().build();
        assertThat(copied.hasArtwork()).isTrue();
    }

    @Test
    @DisplayName("getArtworkData lazily loads embedded artwork from disk when hasArtwork is true and artworkData is null")
    void testLazyLoadingArtworkFromDisk() throws Exception {
        byte[] expectedImage = new byte[]{0x12, 0x34, 0x56, 0x78};
        Path mp3File = tempDir.resolve("lazy_artwork.mp3");
        byte[] mp3Data = createMp3WithApic(expectedImage);
        Files.write(mp3File, mp3Data);

        // Track with hasArtwork=true and artworkData=null (as imported)
        Track track = Track.builder()
                .filePath(mp3File.toString())
                .title("Lazy Song")
                .hasArtwork(true)
                .build();

        assertThat(track.hasArtwork()).isTrue();
        // Lazy load should read from disk on demand
        byte[] loaded = track.getArtworkData();
        assertThat(loaded).isNotNull().containsExactly(expectedImage);

        // Non-existent file gracefully returns null without throwing
        Track missingFileTrack = Track.builder()
                .filePath(tempDir.resolve("ghost.mp3").toString())
                .hasArtwork(true)
                .build();
        assertThat(missingFileTrack.getArtworkData()).isNull();
    }

    @Test
    @DisplayName("getArtworkData safely returns null without throwing exceptions when backing file is deleted, non-existent, or path is invalid")
    void testLazyLoadingDeletedOrInvalidFileReturnsNullSafely() throws Exception {
        byte[] image = new byte[]{1, 2, 3, 4};
        Path mp3File = tempDir.resolve("will_delete.mp3");
        Files.write(mp3File, createMp3WithApic(image));

        Track track = Track.builder()
                .filePath(mp3File.toString())
                .hasArtwork(true)
                .build();

        // 1. Initial lazy load succeeds
        assertThat(track.getArtworkData()).containsExactly(image);

        // 2. File is deleted from disk -> subsequent getArtworkData safely returns null
        Files.delete(mp3File);
        assertThat(track.getArtworkData()).isNull();

        // 3. Ghost / non-existent file path returns null safely
        Track ghostTrack = Track.builder()
                .filePath(tempDir.resolve("does_not_exist_ever.mp3").toString())
                .hasArtwork(true)
                .build();
        assertThat(ghostTrack.getArtworkData()).isNull();
    }

    @Test
    @DisplayName("getArtworkData safely returns null when backing file is corrupt, 0-byte, or lacks APIC tag")
    void testLazyLoadingCorruptOrEmptyFileReturnsNullSafely() throws Exception {
        // 1. 0-byte file
        Path emptyFile = tempDir.resolve("zero_byte_art.mp3");
        Files.createFile(emptyFile);
        Track emptyTrack = Track.builder()
                .filePath(emptyFile.toString())
                .hasArtwork(true)
                .build();
        assertThat(emptyTrack.getArtworkData()).isNull();

        // 2. Corrupt / garbage binary file
        Path corruptFile = tempDir.resolve("corrupt_art.mp3");
        Files.write(corruptFile, new byte[]{0x00, (byte) 0xFF, 0x12, 0x34, 0x56, 0x78});
        Track corruptTrack = Track.builder()
                .filePath(corruptFile.toString())
                .hasArtwork(true)
                .build();
        assertThat(corruptTrack.getArtworkData()).isNull();

        // 3. Valid MP3 audio file but has NO ID3v2 tag
        Path noTagFile = tempDir.resolve("no_tag_art.mp3");
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        Files.write(noTagFile, frame);
        Track noTagTrack = Track.builder()
                .filePath(noTagFile.toString())
                .hasArtwork(true)
                .build();
        assertThat(noTagTrack.getArtworkData()).isNull();
    }

    @Test
    @DisplayName("setArtworkData with null or empty array automatically turns off hasArtwork flag")
    void testSetArtworkDataNullOrEmptyResetsHasArtworkFlag() {
        Track track = Track.builder()
                .filePath("C:/music.mp3")
                .artworkData(new byte[]{1, 2, 3})
                .build();

        assertThat(track.hasArtwork()).isTrue();
        assertThat(track.getArtworkData()).containsExactly((byte) 1, (byte) 2, (byte) 3);

        // 1. Set to null -> hasArtwork becomes false
        track.setArtworkData(null);
        assertThat(track.hasArtwork()).isFalse();
        assertThat(track.getArtworkData()).isNull();

        // 2. Reset to non-empty
        track.setArtworkData(new byte[]{4, 5});
        assertThat(track.hasArtwork()).isTrue();

        // 3. Set to empty array -> hasArtwork becomes false
        track.setArtworkData(new byte[0]);
        assertThat(track.hasArtwork()).isFalse();
        assertThat(track.getArtworkData()).isEmpty();

        // 4. Builder with null or empty artworkData
        Track nullBuilt = Track.builder()
                .filePath("C:/music.mp3")
                .artworkData(null)
                .build();
        assertThat(nullBuilt.hasArtwork()).isFalse();

        Track emptyBuilt = Track.builder()
                .filePath("C:/music.mp3")
                .artworkData(new byte[0])
                .build();
        assertThat(emptyBuilt.hasArtwork()).isFalse();
    }

    private static byte[] createMp3WithApic(byte[] imageBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ByteArrayOutputStream apicBody = new ByteArrayOutputStream();
        apicBody.write(0x00); // encoding: ISO-8859-1
        apicBody.write("image/jpeg\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        apicBody.write(0x03); // Cover front
        apicBody.write(0x00); // description null terminator
        apicBody.write(imageBytes);

        byte[] apicData = apicBody.toByteArray();

        ByteArrayOutputStream tagBody = new ByteArrayOutputStream();
        tagBody.write("APIC".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        tagBody.write(encodeSynchsafe(apicData.length));
        tagBody.write(new byte[]{0x00, 0x00}); // flags
        tagBody.write(apicData);

        byte[] body = tagBody.toByteArray();
        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(body.length));
        baos.write(body);

        // 1 dummy frame (128 kbps)
        int frameSize = 417;
        byte[] frame = new byte[frameSize];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        baos.write(frame);

        return baos.toByteArray();
    }

    private static byte[] encodeSynchsafe(int value) {
        byte[] result = new byte[4];
        result[0] = (byte) ((value >> 21) & 0x7F);
        result[1] = (byte) ((value >> 14) & 0x7F);
        result[2] = (byte) ((value >> 7) & 0x7F);
        result[3] = (byte) (value & 0x7F);
        return result;
    }
}
