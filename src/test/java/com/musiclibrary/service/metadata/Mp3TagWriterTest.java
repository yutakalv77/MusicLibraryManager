package com.musiclibrary.service.metadata;

import com.musiclibrary.model.Track;
import com.musiclibrary.model.TrackMetadataUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mp3TagWriterTest {

    @TempDir
    Path tempDir;

    private Mp3TagWriter tagWriter;
    private Mp3MetadataExtractor extractor;

    @BeforeEach
    void setUp() {
        tagWriter = new Mp3TagWriter();
        extractor = new Mp3MetadataExtractor();
    }

    @Test
    @DisplayName("supports returns true for mp3 files and false for others")
    void testSupports() {
        assertThat(tagWriter.supports(Path.of("test.mp3"))).isTrue();
        assertThat(tagWriter.supports(Path.of("TEST.MP3"))).isTrue();
        assertThat(tagWriter.supports(Path.of("song.flac"))).isFalse();
        assertThat(tagWriter.supports(null)).isFalse();
    }

    @Test
    @DisplayName("write throws FileNotFoundException when file does not exist")
    void testFileNotFound() {
        Path missing = tempDir.resolve("missing.mp3");
        TrackMetadataUpdate update = TrackMetadataUpdate.builder().title("Title").build();

        assertThatThrownBy(() -> tagWriter.write(missing, update))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("write updates title, artist, album, genre, year, track number, and artwork")
    void testWriteFullMetadata() throws Exception {
        Path mp3Path = tempDir.resolve("song_to_tag.mp3");
        byte[] initialMp3 = createMinimalValidMp3(
                "Initial Title",
                "Initial Artist",
                "Initial Album",
                "Classical",
                "2000",
                "1/10",
                new byte[]{1, 2, 3}
        );
        Files.write(mp3Path, initialMp3);

        byte[] newArtwork = new byte[]{0x10, 0x20, 0x30, 0x40, 0x50};
        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .title("Updated Title")
                .artist("Updated Artist")
                .album("Updated Album")
                .genre("Rock")
                .year(2025)
                .trackNumber(7)
                .artwork(newArtwork, "image/jpeg")
                .build();

        tagWriter.write(mp3Path, update);

        // Re-read with extractor
        Track extracted = extractor.extract(mp3Path);
        assertThat(extracted.getTitle()).isEqualTo("Updated Title");
        assertThat(extracted.getArtist()).isEqualTo("Updated Artist");
        assertThat(extracted.getAlbum()).isEqualTo("Updated Album");
        assertThat(extracted.getGenre()).isEqualTo("Rock");
        assertThat(extracted.getYear()).isEqualTo(2025);
        assertThat(extracted.getTrackNumber()).isEqualTo(7);
        assertThat(extracted.getArtworkData()).containsExactly(newArtwork);
    }

    @Test
    @DisplayName("write clears artwork when clearArtwork is set")
    void testClearArtwork() throws Exception {
        Path mp3Path = tempDir.resolve("song_clear_art.mp3");
        byte[] initialMp3 = createMinimalValidMp3(
                "Song",
                "Artist",
                "Album",
                "Jazz",
                "2010",
                "2",
                new byte[]{0x0A, 0x0B}
        );
        Files.write(mp3Path, initialMp3);

        Track before = extractor.extract(mp3Path);
        assertThat(before.hasArtwork()).isTrue();

        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .clearArtwork()
                .build();

        tagWriter.write(mp3Path, update);

        Track after = extractor.extract(mp3Path);
        assertThat(after.hasArtwork()).isFalse();
        assertThat(after.getTitle()).isEqualTo("Song"); // Unmodified fields preserved
        assertThat(after.getArtist()).isEqualTo("Artist");
    }

    @Test
    @DisplayName("write selectively updates only specified fields preserving untouched fields")
    void testPartialUpdate() throws Exception {
        Path mp3Path = tempDir.resolve("song_partial.mp3");
        byte[] initialMp3 = createMinimalValidMp3(
                "Preserve This Title",
                "Old Artist",
                "Old Album",
                "Pop",
                "1999",
                "5",
                null
        );
        Files.write(mp3Path, initialMp3);

        // Batch update scenario: change Artist and Genre only
        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .artist("Brand New Artist")
                .genre("Alternative")
                .build();

        tagWriter.write(mp3Path, update);

        Track result = extractor.extract(mp3Path);
        assertThat(result.getTitle()).isEqualTo("Preserve This Title");
        assertThat(result.getArtist()).isEqualTo("Brand New Artist");
        assertThat(result.getAlbum()).isEqualTo("Old Album");
        assertThat(result.getGenre()).isEqualTo("Alternative");
        assertThat(result.getYear()).isEqualTo(1999);
        assertThat(result.getTrackNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("write preserves Unicode Japanese, special symbols, and long strings with round-trip integrity")
    void testUnicodeJapaneseAndSpecialCharacters() throws Exception {
        Path mp3Path = tempDir.resolve("song_unicode.mp3");
        Files.write(mp3Path, createMinimalValidMp3("Init", "Init", "Init", "Init", "2000", "1", null));

        String japaneseTitle = "夜に駆ける (Into The Night)";
        String japaneseArtist = "YOASOBI feat. 初音ミク";
        String japaneseAlbum = "THE BOOK (完全生産限定盤)";
        String specialGenre = "J-Pop / 邦楽 ★ 🎵";
        String longDescription = "A".repeat(500);

        TrackMetadataUpdate update = TrackMetadataUpdate.builder()
                .title(japaneseTitle)
                .artist(japaneseArtist)
                .album(japaneseAlbum)
                .genre(specialGenre)
                .year(2021)
                .trackNumber(1)
                .build();

        tagWriter.write(mp3Path, update);

        Track extracted = extractor.extract(mp3Path);
        assertThat(extracted.getTitle()).isEqualTo(japaneseTitle);
        assertThat(extracted.getArtist()).isEqualTo(japaneseArtist);
        assertThat(extracted.getAlbum()).isEqualTo(japaneseAlbum);
        assertThat(extracted.getGenre()).isEqualTo(specialGenre);
        assertThat(extracted.getYear()).isEqualTo(2021);
        assertThat(extracted.getTrackNumber()).isEqualTo(1);

        // Update with 500-char string
        TrackMetadataUpdate longUpdate = TrackMetadataUpdate.builder()
                .title(longDescription)
                .build();
        tagWriter.write(mp3Path, longUpdate);

        Track extractedLong = extractor.extract(mp3Path);
        assertThat(extractedLong.getTitle()).isEqualTo(longDescription);
    }

    @Test
    @DisplayName("write on invalid or corrupted file throws IOException and cleans up temporary files")
    void testInvalidFileAndTemporaryFileCleanup() throws IOException {
        Path corruptedMp3 = tempDir.resolve("corrupted.mp3");
        Files.writeString(corruptedMp3, "Not a valid MP3 file format");

        TrackMetadataUpdate update = TrackMetadataUpdate.builder().title("Fails").build();

        assertThatThrownBy(() -> tagWriter.write(corruptedMp3, update))
                .isInstanceOf(IOException.class);

        // Ensure no leftover temp files exist in tempDir
        try (var stream = Files.list(tempDir)) {
            List<Path> files = stream.toList();
            assertThat(files).allMatch(p -> !p.getFileName().toString().startsWith(".tmp_tag_"));
        }
    }

    @Test
    @DisplayName("write handles artwork lifecycle: initial addition, replacement, and deletion with binary integrity")
    void testArtworkLifecycleAndBinaryIntegrity() throws Exception {
        Path mp3Path = tempDir.resolve("artwork_lifecycle.mp3");
        Files.write(mp3Path, createMinimalValidMp3("Art Song", "Artist", "Album", "Pop", "2020", "1", null));

        // 1. Initial addition: PNG image
        byte[] pngArtwork = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        TrackMetadataUpdate addArtUpdate = TrackMetadataUpdate.builder()
                .artwork(pngArtwork, "image/png")
                .build();
        tagWriter.write(mp3Path, addArtUpdate);

        Track afterAdd = extractor.extract(mp3Path);
        assertThat(afterAdd.hasArtwork()).isTrue();
        assertThat(afterAdd.getArtworkData()).containsExactly(pngArtwork);

        // 2. Replacement: JPEG image with different bytes and length
        byte[] jpegArtwork = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 10, 20, 30, 40, 50, 60, 70};
        TrackMetadataUpdate replaceArtUpdate = TrackMetadataUpdate.builder()
                .artwork(jpegArtwork, "image/jpeg")
                .build();
        tagWriter.write(mp3Path, replaceArtUpdate);

        Track afterReplace = extractor.extract(mp3Path);
        assertThat(afterReplace.hasArtwork()).isTrue();
        assertThat(afterReplace.getArtworkData()).containsExactly(jpegArtwork);

        // 3. Clear artwork
        TrackMetadataUpdate clearArtUpdate = TrackMetadataUpdate.builder()
                .clearArtwork()
                .build();
        tagWriter.write(mp3Path, clearArtUpdate);

        Track afterClear = extractor.extract(mp3Path);
        assertThat(afterClear.hasArtwork()).isFalse();
        assertThat(afterClear.getArtworkData()).isNull();
    }

    @Test
    @DisplayName("write enforces NullPointerException on null path or update")
    void testNullArguments() {
        TrackMetadataUpdate update = TrackMetadataUpdate.builder().title("Title").build();
        Path validPath = tempDir.resolve("some.mp3");

        assertThatThrownBy(() -> tagWriter.write(null, update))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> tagWriter.write(validPath, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Helper for creating minimal valid MP3 with ID3v2.4 and MPEG audio frame ---

    private static byte[] createMinimalValidMp3(
            String title, String artist, String album, String genre, String year, String track, byte[] artwork)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

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

        baos.write(new byte[]{'I', 'D', '3', 0x04, 0x00, 0x00});
        baos.write(encodeSynchsafe(bodyBytes.length));
        baos.write(bodyBytes);

        // MPEG 1 Layer III frame
        byte[] frame = new byte[417];
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
        frameBody[0] = 0x00;
        System.arraycopy(textBytes, 0, frameBody, 1, textBytes.length);

        out.write(frameId.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        out.write(encodeSynchsafe(frameBody.length));
        out.write(new byte[]{0x00, 0x00});
        out.write(frameBody);
    }

    private static void writeApicFrame(ByteArrayOutputStream out, byte[] imageBytes) throws IOException {
        ByteArrayOutputStream apicBody = new ByteArrayOutputStream();
        apicBody.write(0x00);
        apicBody.write("image/jpeg\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        apicBody.write(0x03);
        apicBody.write(0x00);
        apicBody.write(imageBytes);

        byte[] body = apicBody.toByteArray();
        out.write("APIC".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        out.write(encodeSynchsafe(body.length));
        out.write(new byte[]{0x00, 0x00});
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
