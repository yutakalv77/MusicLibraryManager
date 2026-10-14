package com.musiclibrary.service.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class FileScannerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Default supported extensions include mp3, wav, m4a, and flac")
    void testDefaultExtensions() {
        FileScanner scanner = new FileScanner();
        assertThat(scanner.getSupportedExtensions())
                .containsExactlyInAnyOrder(".mp3", ".wav", ".m4a", ".flac");
    }

    @Test
    @DisplayName("Custom extensions are normalized to lowercase with leading dot, and empty falls back to defaults")
    void testCustomExtensionsNormalization() {
        FileScanner scanner = new FileScanner(List.of("MP3", ".OGG", "  flac  ", "WAV"));
        assertThat(scanner.getSupportedExtensions())
                .containsExactlyInAnyOrder(".mp3", ".ogg", ".flac", ".wav");

        // Null and empty collections fall back to default
        FileScanner fallbackScanner1 = new FileScanner(null);
        assertThat(fallbackScanner1.getSupportedExtensions())
                .containsExactlyInAnyOrder(".mp3", ".wav", ".m4a", ".flac");

        FileScanner fallbackScanner2 = new FileScanner(Collections.emptyList());
        assertThat(fallbackScanner2.getSupportedExtensions())
                .containsExactlyInAnyOrder(".mp3", ".wav", ".m4a", ".flac");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "test.mp3", "test.MP3", "test.Mp3",
            "audio.wav", "audio.WAV", "audio.Wav",
            "track.flac", "track.FLAC", "track.Flac",
            "music.m4a", "music.M4A", "music.M4a"
    })
    @DisplayName("isSupported checks case-insensitive supported audio extensions")
    void testIsSupportedCaseInsensitive(String filename) {
        FileScanner scanner = new FileScanner();
        assertThat(scanner.isSupported(Path.of(filename))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "image.png", "text.txt", "doc.pdf", "archive.zip",
            "audio.ogg", "song.wma", "no_extension", ""
    })
    @DisplayName("isSupported returns false for unsupported file types and null")
    void testIsSupportedUnsupported(String filename) {
        FileScanner scanner = new FileScanner();
        assertThat(scanner.isSupported(Path.of(filename))).isFalse();
        assertThat(scanner.isSupported(null)).isFalse();
    }

    @Test
    @DisplayName("scan recursively discovers audio files across deeply nested directories")
    void testDeeplyNestedDirectoryScan() throws IOException {
        Path lvl1 = Files.createDirectory(tempDir.resolve("artist"));
        Path lvl2 = Files.createDirectory(lvl1.resolve("album"));
        Path lvl3 = Files.createDirectory(lvl2.resolve("disc1"));
        Path lvl4 = Files.createDirectory(lvl3.resolve("bonus"));

        Path file1 = Files.createFile(lvl1.resolve("track1.mp3"));
        Path file2 = Files.createFile(lvl2.resolve("track2.FLAC"));
        Path file3 = Files.createFile(lvl3.resolve("track3.Wav"));
        Path file4 = Files.createFile(lvl4.resolve("track4.m4a"));
        Path nonAudio = Files.createFile(lvl4.resolve("lyrics.txt"));

        FileScanner scanner = new FileScanner();
        List<Path> results = scanner.scan(tempDir);

        assertThat(results).containsExactlyInAnyOrder(
                file1.toAbsolutePath().normalize(),
                file2.toAbsolutePath().normalize(),
                file3.toAbsolutePath().normalize(),
                file4.toAbsolutePath().normalize()
        );
        assertThat(results).doesNotContain(nonAudio.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("scan strictly skips hidden files, hidden directories, and their subtrees")
    void testHiddenExclusion() throws IOException {
        // Dot-prefixed hidden directory and its contents
        Path hiddenDir = Files.createDirectory(tempDir.resolve(".hiddenFolder"));
        Path subInHidden = Files.createDirectory(hiddenDir.resolve("sub"));
        Path fileInHiddenDir = Files.createFile(hiddenDir.resolve("hidden_song.mp3"));
        Path fileInSubHidden = Files.createFile(subInHidden.resolve("deep_hidden.mp3"));

        // Dot-prefixed hidden file
        Path hiddenFile = Files.createFile(tempDir.resolve(".dotfile.mp3"));
        Path normalFile = Files.createFile(tempDir.resolve("normal.mp3"));

        FileScanner scanner = new FileScanner();
        List<Path> results = scanner.scan(tempDir);

        assertThat(results).containsExactly(normalFile.toAbsolutePath().normalize());
        assertThat(results).doesNotContain(
                fileInHiddenDir.toAbsolutePath().normalize(),
                fileInSubHidden.toAbsolutePath().normalize(),
                hiddenFile.toAbsolutePath().normalize()
        );
    }

    @Test
    @DisplayName("scan terminates immediately and returns empty list when cancel flag is preset")
    void testCancellationImmediate() throws IOException {
        for (int i = 0; i < 5; i++) {
            Files.createFile(tempDir.resolve("song_" + i + ".mp3"));
        }

        FileScanner scanner = new FileScanner();
        AtomicBoolean cancelFlag = new AtomicBoolean(true);

        List<Path> results = scanner.scan(tempDir, cancelFlag);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("scanAll deduplicates redundant file and directory paths in mixed input collections")
    void testScanAllMixedAndDeduplicated() throws IOException {
        Path dir = Files.createDirectory(tempDir.resolve("album"));
        Path song1 = Files.createFile(dir.resolve("song1.mp3"));
        Path song2 = Files.createFile(dir.resolve("song2.mp3"));
        Path rootSong = Files.createFile(tempDir.resolve("standalone.mp3"));

        FileScanner scanner = new FileScanner();
        // Pass the album dir, individual song1 twice, song2, and standalone song twice
        List<Path> mixed = List.of(dir, song1, song1, song2, rootSong, rootSong);
        List<Path> results = scanner.scanAll(mixed);

        assertThat(results).containsExactlyInAnyOrder(
                song1.toAbsolutePath().normalize(),
                song2.toAbsolutePath().normalize(),
                rootSong.toAbsolutePath().normalize()
        );
        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("scan and scanAll handle empty, non-existent, and null paths safely without throwing exceptions")
    void testSafeHandling() throws IOException {
        Path emptyDir = Files.createDirectory(tempDir.resolve("emptyDir"));

        FileScanner scanner = new FileScanner();
        assertThat(scanner.scan(null)).isEmpty();
        assertThat(scanner.scan(tempDir.resolve("non_existent"))).isEmpty();
        assertThat(scanner.scan(emptyDir)).isEmpty();

        assertThat(scanner.scanAll(null)).isEmpty();
        assertThat(scanner.scanAll(Collections.emptyList())).isEmpty();
        assertThat(scanner.scanAll(List.of(tempDir.resolve("ghost")))).isEmpty();
    }
}
