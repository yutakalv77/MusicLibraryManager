package com.musiclibrary.service.metadata;

import com.musiclibrary.model.Track;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mp3MetadataExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("supports returns true for mp3 files case-insensitively and false for other types or null")
    void testSupports() {
        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();

        assertThat(extractor.supports(Path.of("test.mp3"))).isTrue();
        assertThat(extractor.supports(Path.of("test.MP3"))).isTrue();
        assertThat(extractor.supports(Path.of("song.Mp3"))).isTrue();
        assertThat(extractor.supports(Path.of("folder/track.mp3"))).isTrue();

        assertThat(extractor.supports(Path.of("song.flac"))).isFalse();
        assertThat(extractor.supports(Path.of("audio.wav"))).isFalse();
        assertThat(extractor.supports(Path.of("music.m4a"))).isFalse();
        assertThat(extractor.supports(Path.of("lyrics.txt"))).isFalse();
        assertThat(extractor.supports(Path.of("no_ext"))).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    @DisplayName("extract applies smart completion from two-level directory tree (Artist/Album/File) when tags are missing")
    void testSmartCompletionFullHierarchy() throws IOException {
        // Hierarchy: tempDir / Pink Floyd / The Dark Side of the Moon / 01 - Speak to Me.mp3
        Path artistDir = Files.createDirectory(tempDir.resolve("Pink Floyd"));
        Path albumDir = Files.createDirectory(artistDir.resolve("The Dark Side of the Moon"));
        Path mp3File = albumDir.resolve("01 - Speak to Me.mp3");

        Files.write(mp3File, new byte[]{1, 2, 3});

        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();
        Track track = extractor.extract(mp3File);

        assertThat(track).isNotNull();
        assertThat(track.getTitle()).isEqualTo("Speak to Me");
        assertThat(track.getTrackNumber()).isEqualTo(1);
        assertThat(track.getAlbum()).isEqualTo("The Dark Side of the Moon");
        assertThat(track.getArtist()).isEqualTo("Pink Floyd");
        assertThat(track.getFilePath()).isEqualTo(mp3File.toAbsolutePath().normalize().toString());
        assertThat(track.getFileSizeBytes()).isEqualTo(3);
    }

    @Test
    @DisplayName("extract applies smart completion with single parent directory (Album only) or root level safely")
    void testSmartCompletionPartialHierarchy() throws IOException {
        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();

        // 1. Single parent level: album directory only
        Path albumDir = Files.createDirectory(tempDir.resolve("Greatest Hits"));
        Path mp3File = albumDir.resolve("05 - Classic Track.mp3");
        Files.write(mp3File, new byte[]{1, 2, 3});

        Track track = extractor.extract(mp3File);
        assertThat(track.getTitle()).isEqualTo("Classic Track");
        assertThat(track.getTrackNumber()).isEqualTo(5);
        assertThat(track.getAlbum()).isEqualTo("Greatest Hits");
        assertThat(track.getArtist()).isEqualTo(tempDir.getFileName().toString()); // grandParent is tempDir

        // 2. File named just ".mp3"
        Path dotMp3 = tempDir.resolve(".mp3");
        Files.write(dotMp3, new byte[]{1, 2, 3});
        Track dotTrack = extractor.extract(dotMp3);
        assertThat(dotTrack.getTitle()).isEqualTo(".mp3");
    }

    @ParameterizedTest
    @CsvSource({
            "'01 - Song Title.mp3', 1, 'Song Title'",
            "'02. Another Track.mp3', 2, 'Another Track'",
            "'03_Underscore_Song.mp3', 3, 'Underscore_Song'",
            "'4 Space Track.mp3', 4, 'Space Track'",
            "'10 - Multi - Dash - Title.mp3', 10, 'Multi - Dash - Title'",
            "'123 - Track.mp3', 123, 'Track'",
            "'Just Track Name.mp3', 0, 'Just Track Name'"
    })
    @DisplayName("extract parses various leading track number filename formats accurately")
    void testFilenameTrackNumberFormats(String filename, int expectedTrackNumber, String expectedTitle) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.write(file, new byte[]{1, 2, 3});

        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();
        Track track = extractor.extract(file);

        assertThat(track.getTrackNumber()).isEqualTo(expectedTrackNumber);
        assertThat(track.getTitle()).isEqualTo(expectedTitle);
    }

    @Test
    @DisplayName("extract parses valid ID3v2 tags and embedded APIC artwork correctly")
    void testValidId3v2TagsAndArtwork() throws Exception {
        Path mp3Path = tempDir.resolve("tagged_song.mp3");
        byte[] artwork = new byte[]{0x11, 0x22, 0x33, 0x44};

        byte[] mp3Data = createMinimalValidMp3WithId3v2(
                "Custom Song Title",
                "Custom Artist Name",
                "Custom Album Name",
                "Pop",
                "2024",
                "3/10",
                artwork
        );
        Files.write(mp3Path, mp3Data);

        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();
        Track track = extractor.extract(mp3Path);

        assertThat(track.getTitle()).isEqualTo("Custom Song Title");
        assertThat(track.getArtist()).isEqualTo("Custom Artist Name");
        assertThat(track.getAlbum()).isEqualTo("Custom Album Name");
        assertThat(track.getGenre()).isEqualTo("Pop");
        assertThat(track.getYear()).isEqualTo(2024);
        assertThat(track.getTrackNumber()).isEqualTo(3);
        assertThat(track.getArtworkData()).containsExactly((byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44);
        assertThat(track.hasArtwork()).isTrue();
    }

    @Test
    @DisplayName("extract safely falls back for 0-byte and corrupt binary files without throwing unhandled exceptions")
    void testZeroByteAndCorruptFileFallback() throws IOException {
        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();

        // 1. 0-byte file
        Path emptyMp3 = tempDir.resolve("zero_byte.mp3");
        Files.createFile(emptyMp3);
        Track emptyTrack = extractor.extract(emptyMp3);

        assertThat(emptyTrack).isNotNull();
        assertThat(emptyTrack.getTitle()).isEqualTo("zero_byte");
        assertThat(emptyTrack.getFileSizeBytes()).isEqualTo(0);
        assertThat(emptyTrack.getDurationSeconds()).isEqualTo(0);

        // 2. Corrupt / garbage binary file
        Path corruptMp3 = tempDir.resolve("corrupt_noise.mp3");
        Files.write(corruptMp3, new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, 0x00, 0x11});
        Track corruptTrack = extractor.extract(corruptMp3);

        assertThat(corruptTrack).isNotNull();
        assertThat(corruptTrack.getTitle()).isEqualTo("corrupt_noise");
        assertThat(corruptTrack.getFileSizeBytes()).isEqualTo(6);
    }

    @Test
    @DisplayName("extract throws IllegalArgumentException for null and IOException for non-existent path")
    void testExceptionHandling() {
        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();

        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> extractor.extract(tempDir.resolve("phantom.mp3")))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("extract retrieves duration from ID3v2 TLEN frame when present")
    void testDurationFromTlenFrame() throws Exception {
        Path mp3Path = tempDir.resolve("tlen_song.mp3");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ByteArrayOutputStream tagBody = new ByteArrayOutputStream();
        writeTextFrame(tagBody, "TIT2", "TLEN Test Song");
        writeTextFrame(tagBody, "TLEN", "185000"); // 185 seconds

        byte[] bodyBytes = tagBody.toByteArray();
        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(bodyBytes.length));
        baos.write(bodyBytes);

        // 1 MPEG frame (128 kbps)
        int frameSize = 417;
        byte[] frame = new byte[frameSize];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        baos.write(frame);

        Files.write(mp3Path, baos.toByteArray());

        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();
        Track track = extractor.extract(mp3Path);

        assertThat(track.getTitle()).isEqualTo("TLEN Test Song");
        assertThat(track.getDurationSeconds()).isEqualTo(185);
    }

    @Test
    @DisplayName("extract estimates duration from audio data size and bitrate when TLEN is absent")
    void testDurationEstimatedFromAudioDataSizeAndBitrate() throws Exception {
        Path mp3Path = tempDir.resolve("estimated_duration.mp3");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ByteArrayOutputStream tagBody = new ByteArrayOutputStream();
        writeTextFrame(tagBody, "TIT2", "Bitrate Estimate Song");

        byte[] bodyBytes = tagBody.toByteArray();
        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(bodyBytes.length));
        baos.write(bodyBytes);

        // 128 kbps MPEG frame is 417 bytes (approx 26.12ms each)
        // 200 frames * 417 bytes = 83,400 bytes of audio data
        // Expected duration = (83,400 * 8) / (128 * 1000) = 667,200 / 128,000 = 5.2125 seconds -> rounded to 5 seconds
        int frameSize = 417;
        byte[] frame = new byte[frameSize];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;

        for (int i = 0; i < 200; i++) {
            baos.write(frame);
        }

        Files.write(mp3Path, baos.toByteArray());

        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();
        Track track = extractor.extract(mp3Path);

        assertThat(track.getTitle()).isEqualTo("Bitrate Estimate Song");
        assertThat(track.getDurationSeconds()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "INVALID", "abc123", "--", "-185000", "not_a_number"})
    @DisplayName("extract safely falls back to bitrate estimation when TLEN format is empty, non-numeric, or negative")
    void testDurationFromTlenInvalidFormatsFallback(String invalidTlen) throws Exception {
        String safeName = invalidTlen.replaceAll("[^a-zA-Z0-9]", "_");
        Path mp3Path = tempDir.resolve("invalid_tlen_" + safeName + "_" + System.nanoTime() + ".mp3");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ByteArrayOutputStream tagBody = new ByteArrayOutputStream();
        writeTextFrame(tagBody, "TIT2", "Fallback Song");
        writeTextFrame(tagBody, "TLEN", invalidTlen);

        byte[] bodyBytes = tagBody.toByteArray();
        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(bodyBytes.length));
        baos.write(bodyBytes);

        // 200 frames of 128 kbps audio (approx 5.2 seconds -> rounds to 5s)
        int frameSize = 417;
        byte[] frame = new byte[frameSize];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;

        for (int i = 0; i < 200; i++) {
            baos.write(frame);
        }

        Files.write(mp3Path, baos.toByteArray());

        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();
        Track track = extractor.extract(mp3Path);

        assertThat(track.getTitle()).isEqualTo("Fallback Song");
        // TLEN fallback succeeded without throwing exception, and bitrate estimation yielded 5 seconds
        assertThat(track.getDurationSeconds()).isEqualTo(5);
    }

    @ParameterizedTest
    @CsvSource({
            "'', 0",
            "'   ', 0",
            "'INVALID', 0",
            "'-100', 0",
            "'-185000', 0",
            "'abc', 0",
            "'185000', 185000",
            "'185000ms', 185000",
            "'185000\\0', 185000",
            "'99999999999999999999999999999', 0"
    })
    @DisplayName("parseTlenString correctly parses valid numeric durations and safely returns 0 for blanks or invalid values")
    void testParseTlenStringBoundaryValues(String input, long expected) {
        assertThat(Mp3MetadataExtractor.parseTlenString(input)).isEqualTo(expected);
        assertThat(Mp3MetadataExtractor.parseTlenString(null)).isEqualTo(0);
    }

    @ParameterizedTest
    @CsvSource({
            "83400, 0, 5",
            "83400, -1, 5",
            "83400, -128, 5",
            "83400, 128, 5",
            "160000, 128, 10",
            "160000, 320, 4"
    })
    @DisplayName("calculateDurationFromAudioData falls back to default 128kbps when bitrate is 0 or negative")
    void testDurationEstimatedWithZeroOrNegativeBitrateFallback(long bytes, int bitrate, long expectedSeconds) {
        long duration = Mp3MetadataExtractor.calculateDurationFromAudioData(bytes, bitrate);
        assertThat(duration).isEqualTo(expectedSeconds);
    }

    @Test
    @DisplayName("extract safely returns 0 duration when audio data bytes is 0 or negative (header-only or tiny file)")
    void testDurationZeroWhenAudioDataBytesZeroOrNegative() throws Exception {
        Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();

        // 1. Header-only file: ID3v2 tag present with metadata/artwork, but NO audio frames (audioDataBytes = 0)
        Path headerOnlyMp3 = tempDir.resolve("header_only_no_audio.mp3");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayOutputStream tagBody = new ByteArrayOutputStream();
        writeTextFrame(tagBody, "TIT2", "Tag Only Song");
        writeTextFrame(tagBody, "TALB", "Tag Only Album");
        writeApicFrame(tagBody, new byte[]{0x01, 0x02, 0x03, 0x04});

        byte[] bodyBytes = tagBody.toByteArray();
        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(bodyBytes.length));
        baos.write(bodyBytes);
        // NO MPEG frames appended!
        Files.write(headerOnlyMp3, baos.toByteArray());

        Track headerOnlyTrack = extractor.extract(headerOnlyMp3);
        // mp3agic safely falls back for missing MPEG frames, smart completion sets title and duration is 0
        assertThat(headerOnlyTrack).isNotNull();
        assertThat(headerOnlyTrack.getTitle()).isEqualTo("header_only_no_audio");
        assertThat(headerOnlyTrack.getDurationSeconds()).isEqualTo(0);

        // 2. Tiny file (e.g., 16 bytes incomplete ID3 header)
        Path tinyMp3 = tempDir.resolve("tiny_file.mp3");
        Files.write(tinyMp3, new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
        Track tinyTrack = extractor.extract(tinyMp3);
        assertThat(tinyTrack).isNotNull();
        assertThat(tinyTrack.getDurationSeconds()).isEqualTo(0);

        // 3. ID3v1 tag only (128 bytes) with no audio frames
        Path id3v1OnlyMp3 = tempDir.resolve("id3v1_only.mp3");
        byte[] id3v1Data = new byte[128];
        id3v1Data[0] = 'T';
        id3v1Data[1] = 'A';
        id3v1Data[2] = 'G';
        Files.write(id3v1OnlyMp3, id3v1Data);
        Track id3v1Track = extractor.extract(id3v1OnlyMp3);
        assertThat(id3v1Track).isNotNull();
        assertThat(id3v1Track.getDurationSeconds()).isEqualTo(0);

        // 4. Direct audioDataBytes <= 0 calculation verification
        assertThat(Mp3MetadataExtractor.calculateDurationFromAudioData(0, 128)).isEqualTo(0);
        assertThat(Mp3MetadataExtractor.calculateDurationFromAudioData(-1, 128)).isEqualTo(0);
        assertThat(Mp3MetadataExtractor.calculateDurationFromAudioData(-50000, 128)).isEqualTo(0);
    }

    @Test
    @DisplayName("estimateDurationFromBitrate safely returns 0 for non-positive fileSize or zero/negative audio data")
    void testEstimateDurationFromBitrateDirectEdgeCases() throws Exception {
        Path validMp3 = tempDir.resolve("direct_test.mp3");
        byte[] mp3Data = createMinimalValidMp3WithId3v2("Direct", "Test", "Album", "Pop", "2024", "1", null);
        Files.write(validMp3, mp3Data);

        com.mpatric.mp3agic.Mp3File mp3File = new com.mpatric.mp3agic.Mp3File(validMp3.toFile(), 65536, false);

        // fileSize <= 0 -> 0
        assertThat(Mp3MetadataExtractor.estimateDurationFromBitrate(mp3File, 0)).isEqualTo(0);
        assertThat(Mp3MetadataExtractor.estimateDurationFromBitrate(mp3File, -500)).isEqualTo(0);

        // audioDataBytes <= 0: when fileSize is smaller than or equal to startOffset
        long startOffset = mp3File.getStartOffset();
        assertThat(Mp3MetadataExtractor.estimateDurationFromBitrate(mp3File, startOffset)).isEqualTo(0);
        assertThat(Mp3MetadataExtractor.estimateDurationFromBitrate(mp3File, Math.max(0, startOffset - 10))).isEqualTo(0);
    }

    // --- Helper for creating minimal valid MP3 with ID3v2.4 and MPEG audio frame ---

    private static byte[] createMinimalValidMp3WithId3v2(
            String title, String artist, String album, String genre, String year, String track, byte[] artwork)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 1. Build ID3v2 body
        ByteArrayOutputStream tagBody = new ByteArrayOutputStream();
        writeTextFrame(tagBody, "TIT2", title);
        writeTextFrame(tagBody, "TPE1", artist);
        writeTextFrame(tagBody, "TALB", album);
        writeTextFrame(tagBody, "TCON", genre);
        writeTextFrame(tagBody, "TYER", year);
        writeTextFrame(tagBody, "TRCK", track);
        if (artwork != null) {
            writeApicFrame(tagBody, artwork);
        }

        byte[] bodyBytes = tagBody.toByteArray();

        // ID3v2 Header: "ID3", version 2.4.0 (0x04 0x00), flags (0x00), synchsafe size
        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(bodyBytes.length));
        baos.write(bodyBytes);

        // 2. Valid MPEG 1 Layer III frame (128 kbps, 44.1 kHz -> 417 bytes)
        int frameSize = 417;
        byte[] frame = new byte[frameSize];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = (byte) 0x64;
        baos.write(frame);

        return baos.toByteArray();
    }

    private static void writeTextFrame(ByteArrayOutputStream out, String frameId, String text) throws IOException {
        if (text == null) return;
        byte[] textBytes = text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] frameBody = new byte[1 + textBytes.length];
        frameBody[0] = 0x00; // ISO-8859-1 encoding
        System.arraycopy(textBytes, 0, frameBody, 1, textBytes.length);

        out.write(frameId.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        out.write(encodeSynchsafe(frameBody.length));
        out.write(new byte[]{0x00, 0x00}); // flags
        out.write(frameBody);
    }

    private static void writeApicFrame(ByteArrayOutputStream out, byte[] imageBytes) throws IOException {
        ByteArrayOutputStream apicBody = new ByteArrayOutputStream();
        apicBody.write(0x00); // encoding: ISO-8859-1
        apicBody.write("image/jpeg\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)); // mime
        apicBody.write(0x03); // picture type: Cover (front)
        apicBody.write(0x00); // description null terminator
        apicBody.write(imageBytes);

        byte[] body = apicBody.toByteArray();
        out.write("APIC".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        out.write(encodeSynchsafe(body.length));
        out.write(new byte[]{0x00, 0x00}); // flags
        out.write(body);
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
