package com.musiclibrary.service.encoding;

import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncodingRepairService Tests")
class EncodingRepairServiceTest {

    private EncodingRepairService repairService;

    @BeforeEach
    void setUp() {
        repairService = EncodingRepairService.getInstance();
    }

    private String createMojibake(String text, Charset originalCharset) {
        byte[] bytes = text.getBytes(originalCharset);
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    @Nested
    @DisplayName("Manual Charset Repair Tests")
    class ManualRepairTests {

        @Test
        @DisplayName("Repairs Shift_JIS (Windows-31J) mojibake with Hiragana, Katakana, and Kanji")
        void testRepairShiftJisStandard() {
            String original = "夜に駆ける - 音楽プレイヤー";
            String mojibake = createMojibake(original, EncodingRepairService.CHARSET_CP932);

            assertNotEquals(original, mojibake);
            String repaired = repairService.repair(mojibake, EncodingRepairService.CHARSET_CP932);
            assertEquals(original, repaired);
        }

        @Test
        @DisplayName("Repairs Windows-31J NEC/IBM extended characters (circle numbers, special kanji)")
        void testRepairWindows31JExtendedChars() {
            String original = "① トラック 髙木 﨑山 №1 ㈱";
            String mojibake = createMojibake(original, EncodingRepairService.CHARSET_CP932);

            String repaired = repairService.repair(mojibake, EncodingRepairService.CHARSET_CP932);
            assertEquals(original, repaired);
        }

        @Test
        @DisplayName("Repairs Shift_JIS half-width katakana")
        void testRepairShiftJisHalfWidthKatakana() {
            String original = "ﾃｽﾄｱﾙﾊﾞﾑ ﾐｭｰｼﾞｯｸ";
            String mojibake = createMojibake(original, EncodingRepairService.CHARSET_CP932);

            String repaired = repairService.repair(mojibake, EncodingRepairService.CHARSET_CP932);
            assertEquals(original, repaired);
        }

        @Test
        @DisplayName("Repairs UTF-8 mojibake")
        void testRepairUtf8() {
            String original = "こんにちは世界 - Special Track ♪";
            String mojibake = createMojibake(original, StandardCharsets.UTF_8);

            assertNotEquals(original, mojibake);
            String repaired = repairService.repair(mojibake, StandardCharsets.UTF_8);
            assertEquals(original, repaired);
        }

        @Test
        @DisplayName("Repairs EUC-JP mojibake")
        void testRepairEucJp() {
            String original = "日本の名曲選集 - 懐メロ";
            String mojibake = createMojibake(original, EncodingRepairService.CHARSET_EUCJP);

            assertNotEquals(original, mojibake);
            String repaired = repairService.repair(mojibake, EncodingRepairService.CHARSET_EUCJP);
            assertEquals(original, repaired);
        }

        @Test
        @DisplayName("Returns original text when target charset is ISO-8859-1 or null")
        void testRepairOriginalOrNullCharset() {
            String text = "Sample Text";
            assertEquals(text, repairService.repair(text, StandardCharsets.ISO_8859_1));
            assertEquals(text, repairService.repair(text, null));
        }

        @Test
        @DisplayName("Handles null and empty string safely")
        void testRepairNullAndEmpty() {
            assertNull(repairService.repair(null, EncodingRepairService.CHARSET_CP932));
            assertEquals("", repairService.repair("", EncodingRepairService.CHARSET_CP932));
        }
    }

    @Nested
    @DisplayName("Automatic Detection and Repair Tests")
    class AutoRepairTests {

        @Test
        @DisplayName("Auto-detects and repairs Shift_JIS mojibake")
        void testAutoRepairShiftJis() {
            String original = "春の歌 - スピッツ";
            String mojibake = createMojibake(original, EncodingRepairService.CHARSET_CP932);

            String repaired = repairService.autoRepair(mojibake);
            assertEquals(original, repaired);
            assertEquals(EncodingRepairService.CHARSET_CP932, repairService.detectBestCharset(mojibake));
        }

        @Test
        @DisplayName("Auto-detects and repairs UTF-8 mojibake")
        void testAutoRepairUtf8() {
            String original = "紅蓮華 - LiSA (鬼滅の刃)";
            String mojibake = createMojibake(original, StandardCharsets.UTF_8);

            String repaired = repairService.autoRepair(mojibake);
            assertEquals(original, repaired);
            assertEquals(StandardCharsets.UTF_8, repairService.detectBestCharset(mojibake));
        }

        @Test
        @DisplayName("Auto-detects and repairs EUC-JP mojibake")
        void testAutoRepairEucJp() {
            String original = "津軽海峡冬景色 - 石川さゆり";
            String mojibake = createMojibake(original, EncodingRepairService.CHARSET_EUCJP);

            String repaired = repairService.autoRepair(mojibake);
            assertEquals(original, repaired);
            assertEquals(EncodingRepairService.CHARSET_EUCJP, repairService.detectBestCharset(mojibake));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Highway to Hell",
                "Track 01 - Rock & Roll (1998)",
                "100% Love [Extended Mix]",
                "Bass, Treble & Melody!",
                ""
        })
        @DisplayName("Pure ASCII strings are preserved untouched without modification")
        void testPureAsciiPreserved(String asciiText) {
            assertEquals(asciiText, repairService.autoRepair(asciiText));
            assertEquals(StandardCharsets.ISO_8859_1, repairService.detectBestCharset(asciiText));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "café au lait",
                "Château Margaux",
                "Über den Wolken",
                "Das Mädchen aus Hamburg",
                "El señor de los anillos",
                "smörgåsbord delight",
                "Noël d'amour",
                "São Paulo & Brasília",
                "René & François"
        })
        @DisplayName("Valid Latin-1 European text is NOT corrupted (False Positive Prevention)")
        void testEuropeanLatin1FalsePositivePrevention(String europeanText) {
            // Must retain the valid European characters without decoding into nonsensical Japanese/Chinese
            String result = repairService.autoRepair(europeanText);
            assertEquals(europeanText, result, "European Latin-1 string should not be falsely repaired");
            assertEquals(StandardCharsets.ISO_8859_1, repairService.detectBestCharset(europeanText));
        }

        @Test
        @DisplayName("Handles null and empty string safely")
        void testAutoRepairNullAndEmpty() {
            assertNull(repairService.autoRepair(null));
            assertEquals("", repairService.autoRepair(""));
            assertEquals(StandardCharsets.ISO_8859_1, repairService.detectBestCharset(null));
            assertEquals(StandardCharsets.ISO_8859_1, repairService.detectBestCharset(""));
        }
    }

    @Nested
    @DisplayName("Quality Scoring and ASCII Verification Tests")
    class ScoringTests {

        @Test
        @DisplayName("Score calculation gives appropriate weights to character types")
        void testCalculateQualityScore() {
            // Hiragana: +5 points each
            assertEquals(15, repairService.calculateQualityScore("あいう"));

            // Katakana: +3 points each
            assertEquals(9, repairService.calculateQualityScore("アイウ"));

            // Kanji: +2 points each
            assertEquals(6, repairService.calculateQualityScore("音楽隊"));

            // Half-width Katakana: +1 point each
            assertEquals(3, repairService.calculateQualityScore("ｱｲｳ"));

            // Replacement char (\uFFFD): -10 points each
            assertEquals(-10, repairService.calculateQualityScore("\uFFFD"));

            // Control characters: -10 points
            assertEquals(-10, repairService.calculateQualityScore("\u0001"));

            // Mixed
            // "あ" (+5) + "イ" (+3) + "音" (+2) + "ｳ" (-5 penalty for mixed half-width kana) = 5
            assertEquals(5, repairService.calculateQualityScore("あイ音ｳ"));

            // Null and empty
            assertEquals(0, repairService.calculateQualityScore(null));
            assertEquals(0, repairService.calculateQualityScore(""));
        }

        @Test
        @DisplayName("isPureAscii correctly differentiates ASCII from non-ASCII")
        void testIsPureAscii() {
            assertTrue(repairService.isPureAscii(null));
            assertTrue(repairService.isPureAscii(""));
            assertTrue(repairService.isPureAscii("Hello World 123 !?#"));
            assertFalse(repairService.isPureAscii("café"));
            assertFalse(repairService.isPureAscii("日本語"));
            assertFalse(repairService.isPureAscii("ﾃｽﾄ"));
        }
    }

    @Nested
    @DisplayName("Track Repair Integration Tests")
    class TrackRepairTests {

        private Track createTestTrack(String title, String artist, String albumArtist, String album, String genre) {
            return Track.builder()
                    .id("test-track-1")
                    .filePath("C:/music/test.mp3")
                    .fileSizeBytes(5000000L)
                    .lastModified(Instant.now())
                    .durationSeconds(240)
                    .bitrate(320)
                    .trackNumber(3)
                    .year(2021)
                    .artworkData(new byte[]{1, 2, 3})
                    .title(title)
                    .artist(artist)
                    .albumArtist(albumArtist)
                    .album(album)
                    .genre(genre)
                    .build();
        }

        @Test
        @DisplayName("repairTrack successfully updates metadata fields while keeping non-string fields intact")
        void testRepairTrackWithExplicitCharset() {
            String origTitle = "群青";
            String origArtist = "YOASOBI";
            String origAlbum = "THE BOOK";
            String origGenre = "J-POP";

            String mbTitle = createMojibake(origTitle, EncodingRepairService.CHARSET_CP932);
            String mbArtist = origArtist; // ASCII
            String mbAlbum = origAlbum; // ASCII
            String mbGenre = origGenre; // ASCII

            Track track = createTestTrack(mbTitle, mbArtist, mbArtist, mbAlbum, mbGenre);

            Track repaired = repairService.repairTrack(track, EncodingRepairService.CHARSET_CP932);

            assertNotNull(repaired);
            assertEquals("群青", repaired.getTitle());
            assertEquals("YOASOBI", repaired.getArtist());
            assertEquals("THE BOOK", repaired.getAlbum());
            assertEquals("J-POP", repaired.getGenre());

            // Non-metadata fields preserved
            assertEquals("test-track-1", repaired.getId());
            assertEquals("C:/music/test.mp3", repaired.getFilePath());
            assertEquals(5000000L, repaired.getFileSizeBytes());
            assertEquals(240, repaired.getDurationSeconds());
            assertEquals(320, repaired.getBitrate());
            assertEquals(3, repaired.getTrackNumber());
            assertEquals(2021, repaired.getYear());
            assertArrayEquals(new byte[]{1, 2, 3}, repaired.getArtworkData());
        }

        @Test
        @DisplayName("autoRepairTrack successfully updates metadata fields using auto-detection")
        void testAutoRepairTrack() {
            String origTitle = "白日";
            String origArtist = "King Gnu";
            String origAlbum = "CEREMONY";
            String origGenre = "邦楽ロック";

            String mbTitle = createMojibake(origTitle, StandardCharsets.UTF_8);
            String mbArtist = origArtist;
            String mbAlbum = origAlbum;
            String mbGenre = createMojibake(origGenre, StandardCharsets.UTF_8);

            Track track = createTestTrack(mbTitle, mbArtist, mbArtist, mbAlbum, mbGenre);

            Track repaired = repairService.autoRepairTrack(track);

            assertNotNull(repaired);
            assertEquals("白日", repaired.getTitle());
            assertEquals("King Gnu", repaired.getArtist());
            assertEquals("CEREMONY", repaired.getAlbum());
            assertEquals("邦楽ロック", repaired.getGenre());
            assertEquals(240, repaired.getDurationSeconds());
        }

        @Test
        @DisplayName("repairTrack and autoRepairTrack handle null track gracefully")
        void testRepairTrackNull() {
            assertNull(repairService.repairTrack(null, EncodingRepairService.CHARSET_CP932));
            assertNull(repairService.autoRepairTrack(null));
        }
    }
}
