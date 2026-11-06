package com.musiclibrary.service.encoding;

import com.musiclibrary.model.Track;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * High-precision character encoding repair and detection service.
 * <p>
 * Fixes ID3 tag mojibake caused by legacy rippers writing Shift_JIS (Windows-31J),
 * UTF-8, or EUC-JP bytes into tags declared as ISO-8859-1 (Latin-1).
 * Recovers raw bytes reversibly using ISO-8859-1 byte mapping and evaluates
 * candidates using Japanese linguistic quality scoring with strict false-positive prevention.
 */
public class EncodingRepairService {

    public static final Charset CHARSET_CP932 = Charset.forName("Windows-31J");
    public static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;
    public static final Charset CHARSET_EUCJP = Charset.forName("EUC-JP");
    public static final Charset CHARSET_ISO8859_1 = StandardCharsets.ISO_8859_1;

    /**
     * Supported candidate charsets for auto detection, ordered by priority when scores tie.
     */
    private static final List<Charset> CANDIDATE_CHARSETS = List.of(
            CHARSET_UTF8,
            CHARSET_CP932,
            CHARSET_EUCJP
    );

    /**
     * Minimum score required to accept an auto-repair candidate.
     * Prevents false positives where European Latin-1 text (e.g. "café", "Über")
     * coincidentally decodes to an isolated Chinese/Japanese kanji character (+2).
     * Score >= 4 requires either at least one Hiragana (+5) or at least two Kanji (+4).
     */
    private static final int MIN_AUTO_REPAIR_SCORE = 4;

    private static final EncodingRepairService INSTANCE = new EncodingRepairService();

    public static EncodingRepairService getInstance() {
        return INSTANCE;
    }

    /**
     * Re-decodes a string from its underlying Latin-1 raw bytes using the specified target charset.
     * Used for manual encoding repair where the user explicitly chooses the desired charset.
     *
     * @param text          input string (interpreted as ISO-8859-1)
     * @param targetCharset charset to re-interpret raw bytes with
     * @return repaired string, or original text if unchanged or null
     */
    public String repair(String text, Charset targetCharset) {
        if (text == null || text.isEmpty() || targetCharset == null || targetCharset.equals(CHARSET_ISO8859_1)) {
            return text;
        }

        try {
            byte[] rawBytes = text.getBytes(CHARSET_ISO8859_1);
            CharsetDecoder decoder = targetCharset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            CharBuffer out = decoder.decode(ByteBuffer.wrap(rawBytes));
            return out.toString();
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * Automatically detects and repairs mojibake in the input string.
     * Uses strict byte sequence validation and Japanese linguistic scoring
     * to safely restore Japanese characters without breaking valid Latin-1 text.
     *
     * @param text input string
     * @return repaired string, or original text if no confident repair is detected
     */
    public String autoRepair(String text) {
        if (text == null || text.isEmpty() || isPureAscii(text)) {
            return text;
        }

        byte[] rawBytes = text.getBytes(CHARSET_ISO8859_1);
        int originalScore = calculateQualityScore(text);

        String bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;

        for (Charset charset : CANDIDATE_CHARSETS) {
            String candidate = decodeStrict(rawBytes, charset);
            if (candidate == null) {
                continue;
            }

            int score = calculateQualityScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        if (bestCandidate != null && bestScore >= MIN_AUTO_REPAIR_SCORE && bestScore > originalScore) {
            return bestCandidate;
        }

        return text;
    }

    /**
     * Detects the best-matching charset for the given text among candidate charsets.
     *
     * @param text input string
     * @return detected Charset, or ISO-8859-1 if no candidate exceeds threshold
     */
    public Charset detectBestCharset(String text) {
        if (text == null || text.isEmpty() || isPureAscii(text)) {
            return CHARSET_ISO8859_1;
        }

        byte[] rawBytes = text.getBytes(CHARSET_ISO8859_1);
        int originalScore = calculateQualityScore(text);

        Charset bestCharset = CHARSET_ISO8859_1;
        int bestScore = originalScore;

        for (Charset charset : CANDIDATE_CHARSETS) {
            String candidate = decodeStrict(rawBytes, charset);
            if (candidate == null) {
                continue;
            }

            int score = calculateQualityScore(candidate);
            if (score > bestScore && score >= MIN_AUTO_REPAIR_SCORE) {
                bestScore = score;
                bestCharset = charset;
            }
        }

        return bestCharset;
    }

    /**
     * Repairs metadata fields of a track with a manually selected charset.
     *
     * @param track         source track
     * @param targetCharset charset to re-interpret metadata bytes with
     * @return a new Track instance with repaired metadata
     */
    public Track repairTrack(Track track, Charset targetCharset) {
        if (track == null) {
            return null;
        }
        return track.toBuilder()
                .title(repair(track.getTitle(), targetCharset))
                .artist(repair(track.getArtist(), targetCharset))
                .albumArtist(repair(track.getAlbumArtist(), targetCharset))
                .album(repair(track.getAlbum(), targetCharset))
                .genre(repair(track.getGenre(), targetCharset))
                .build();
    }

    /**
     * Automatically repairs metadata fields of a track using language scoring.
     *
     * @param track source track
     * @return a new Track instance with repaired metadata
     */
    public Track autoRepairTrack(Track track) {
        if (track == null) {
            return null;
        }
        return track.toBuilder()
                .title(autoRepair(track.getTitle()))
                .artist(autoRepair(track.getArtist()))
                .albumArtist(autoRepair(track.getAlbumArtist()))
                .album(autoRepair(track.getAlbum()))
                .genre(autoRepair(track.getGenre()))
                .build();
    }

    /**
     * Attempts strict decoding of raw bytes with the specified charset.
     * Returns null if any byte sequence is malformed or unmappable.
     */
    private String decodeStrict(byte[] rawBytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer out = decoder.decode(ByteBuffer.wrap(rawBytes));
            return out.toString();
        } catch (CharacterCodingException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Calculates Japanese linguistic quality score for the given text.
     * <ul>
     *   <li>Hiragana (\u3040-\u309F): +5 points (definitive indicator of Japanese text)</li>
     *   <li>Katakana (\u30A0-\u30FF): +3 points</li>
     *   <li>CJK Unified Ideographs (\u4E00-\u9FFF): +2 points</li>
     *   <li>Half-width Katakana (\uFF61-\uFF9F): +1 point</li>
     *   <li>Replacement character (\uFFFD): -10 points</li>
     *   <li>Invalid/unwanted control characters: -10 points</li>
     * </ul>
     */
    public int calculateQualityScore(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        boolean hasKanjiOrHiragana = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u4E00' && c <= '\u9FFF') || (c >= '\u3040' && c <= '\u309F')) {
                hasKanjiOrHiragana = true;
                break;
            }
        }

        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c >= '\u3040' && c <= '\u309F') {
                score += 5; // Hiragana
            } else if (c >= '\u30A0' && c <= '\u30FF') {
                score += 3; // Katakana
            } else if (c >= '\u4E00' && c <= '\u9FFF') {
                score += 2; // CJK Kanji

                // Check for unnatural embedding adjacent to Latin alphabet (accidental CP932 decoding of accented Latin-1)
                boolean prevIsAlpha = (i > 0 && isAsciiAlpha(text.charAt(i - 1)));
                boolean nextIsAlpha = (i + 1 < text.length() && isAsciiAlpha(text.charAt(i + 1)));

                if (prevIsAlpha && nextIsAlpha) {
                    score -= 15; // Directly embedded between Latin letters (e.g. "Bras蛹ia")
                } else if (prevIsAlpha || nextIsAlpha) {
                    boolean prevIsJapanese = (i > 0 && isJapaneseChar(text.charAt(i - 1)));
                    boolean nextIsJapanese = (i + 1 < text.length() && isJapaneseChar(text.charAt(i + 1)));
                    if (!prevIsJapanese && !nextIsJapanese) {
                        score -= 10; // Isolated kanji adjacent to Latin letter (e.g. "S邵 ")
                    }
                }
            } else if (c >= '\uFF61' && c <= '\uFF9F') {
                // Half-width Katakana: If mixed with Kanji or Hiragana, it is almost certainly a mojibake artifact
                if (hasKanjiOrHiragana) {
                    score -= 5;
                } else {
                    score += 1; // Pure half-width Katakana title
                }
            } else if (c == '\uFFFD') {
                score -= 10; // Replacement character
            } else if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
                score -= 10; // Control character
            }
        }
        return score;
    }

    private boolean isAsciiAlpha(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private boolean isJapaneseChar(char c) {
        return (c >= '\u3040' && c <= '\u309F')
                || (c >= '\u30A0' && c <= '\u30FF')
                || (c >= '\u4E00' && c <= '\u9FFF');
    }

    /**
     * Checks if all characters in the string are pure 7-bit ASCII (0x00 to 0x7F).
     */
    public boolean isPureAscii(String text) {
        if (text == null) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
