package com.musiclibrary.service.search;

import com.musiclibrary.model.Track;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * Ultra-fast in-memory inverted index search engine.
 * Delivers sub-millisecond (< 1ms) incremental full-text and partial-match queries
 * across 10,000+ tracks for Title, Artist, Album, Genre, and Year.
 * <p>
 * Thread-safe via {@link ReentrantReadWriteLock} allowing concurrent lookups alongside
 * background ingestion.
 */
public class FastSearchIndex {

    private static final Pattern WHITESPACE_OR_PUNCTUATION = Pattern.compile("[\\s\\p{Punct}&&[^_]]+");
    private static final int MAX_PREFIX_LENGTH = 32;

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    // Inverted token mapping: normalized token -> set of track IDs
    private final Map<String, Set<String>> invertedIndex = new HashMap<>();

    // Track ID -> Track entity
    private final Map<String, Track> tracksById = new HashMap<>();

    // Track ID -> combined normalized searchable text
    private final Map<String, String> searchTextsById = new HashMap<>();

    // Track ID -> set of indexed tokens (used for efficient removals/updates)
    private final Map<String, Set<String>> tokensByTrackId = new HashMap<>();

    public FastSearchIndex() {
    }

    /**
     * Indexes a single track into the search index.
     *
     * @param track track to index (ignored if null or has blank id)
     */
    public void indexTrack(Track track) {
        if (track == null || track.getId() == null || track.getId().isBlank()) {
            return;
        }

        writeLock.lock();
        try {
            removeTrackInternal(track.getId());
            indexTrackInternal(track);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Batch-indexes multiple tracks under a single write lock acquisition.
     *
     * @param tracks collection of tracks to index
     */
    public void indexTracks(Collection<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        writeLock.lock();
        try {
            for (Track track : tracks) {
                if (track != null && track.getId() != null && !track.getId().isBlank()) {
                    removeTrackInternal(track.getId());
                    indexTrackInternal(track);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes a track from the index by its ID.
     *
     * @param trackId ID of track to remove
     */
    public void removeTrack(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }

        writeLock.lock();
        try {
            removeTrackInternal(trackId);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Updates an existing track's indexed metadata.
     *
     * @param track updated track entity
     */
    public void updateTrack(Track track) {
        indexTrack(track);
    }

    /**
     * Searches for track IDs matching all keywords in the query (AND search).
     * Sub-string and prefix matches across title, artist, album, genre, and year are matched.
     *
     * @param query search query
     * @return set of matching track IDs, or all track IDs if query is empty or blank
     */
    public Set<String> search(String query) {
        readLock.lock();
        try {
            if (query == null || query.isBlank()) {
                return new HashSet<>(tracksById.keySet());
            }

            String normalized = normalize(query);
            String[] rawTokens = WHITESPACE_OR_PUNCTUATION.split(normalized);
            List<String> queryTerms = new ArrayList<>();
            for (String t : rawTokens) {
                if (!t.isBlank()) {
                    queryTerms.add(t);
                }
            }

            if (queryTerms.isEmpty()) {
                return new HashSet<>(tracksById.keySet());
            }

            Set<String> candidateIds = null;

            for (String term : queryTerms) {
                Set<String> termMatches = findCandidatesForTerm(term);
                if (termMatches == null || termMatches.isEmpty()) {
                    return Collections.emptySet();
                }

                if (candidateIds == null) {
                    candidateIds = new HashSet<>(termMatches);
                } else {
                    candidateIds.retainAll(termMatches);
                    if (candidateIds.isEmpty()) {
                        return Collections.emptySet();
                    }
                }
            }

            if (candidateIds == null || candidateIds.isEmpty()) {
                return Collections.emptySet();
            }

            // High-precision verification: ensure full substring match on normalized text
            Set<String> verifiedMatches = new HashSet<>(candidateIds.size());
            for (String trackId : candidateIds) {
                String fullText = searchTextsById.get(trackId);
                if (fullText != null && matchesAllTerms(fullText, queryTerms)) {
                    verifiedMatches.add(trackId);
                }
            }

            return verifiedMatches;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Searches for tracks matching all query keywords.
     *
     * @param query search query
     * @return list of matching Track objects
     */
    public List<Track> searchTracks(String query) {
        Set<String> matchedIds = search(query);
        if (matchedIds.isEmpty()) {
            return Collections.emptyList();
        }

        readLock.lock();
        try {
            List<Track> result = new ArrayList<>(matchedIds.size());
            for (String id : matchedIds) {
                Track t = tracksById.get(id);
                if (t != null) {
                    result.add(t);
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Clears all indexed tracks and tokens.
     */
    public void clear() {
        writeLock.lock();
        try {
            invertedIndex.clear();
            tracksById.clear();
            searchTextsById.clear();
            tokensByTrackId.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns total number of indexed tracks.
     *
     * @return indexed track count
     */
    public int size() {
        readLock.lock();
        try {
            return tracksById.size();
        } finally {
            readLock.unlock();
        }
    }

    // --- Internal Indexing & Matching Logic ---

    private void indexTrackInternal(Track track) {
        String trackId = track.getId();
        tracksById.put(trackId, track);

        String combinedText = buildSearchableText(track);
        String normalizedCombined = normalize(combinedText);
        searchTextsById.put(trackId, normalizedCombined);

        Set<String> trackTokens = extractTokens(normalizedCombined);
        tokensByTrackId.put(trackId, trackTokens);

        for (String token : trackTokens) {
            invertedIndex.computeIfAbsent(token, k -> new HashSet<>()).add(trackId);
        }
    }

    private void removeTrackInternal(String trackId) {
        tracksById.remove(trackId);
        searchTextsById.remove(trackId);

        Set<String> registeredTokens = tokensByTrackId.remove(trackId);
        if (registeredTokens != null) {
            for (String token : registeredTokens) {
                Set<String> trackSet = invertedIndex.get(token);
                if (trackSet != null) {
                    trackSet.remove(trackId);
                    if (trackSet.isEmpty()) {
                        invertedIndex.remove(token);
                    }
                }
            }
        }
    }

    private String buildSearchableText(Track track) {
        StringBuilder sb = new StringBuilder(128);
        if (track.getTitle() != null) sb.append(track.getTitle()).append(' ');
        if (track.getArtist() != null) sb.append(track.getArtist()).append(' ');
        if (track.getAlbum() != null) sb.append(track.getAlbum()).append(' ');
        if (track.getGenre() != null) sb.append(track.getGenre()).append(' ');
        if (track.getYear() > 0) sb.append(track.getYear()).append(' ');
        return sb.toString();
    }

    private Set<String> extractTokens(String normalizedText) {
        Set<String> tokens = new HashSet<>();
        String[] words = WHITESPACE_OR_PUNCTUATION.split(normalizedText);

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            // 1. Add complete word
            tokens.add(word);

            // 2. Edge N-grams (prefixes) for incremental typing search
            int maxLen = Math.min(word.length(), MAX_PREFIX_LENGTH);
            for (int len = 1; len <= maxLen; len++) {
                tokens.add(word.substring(0, len));
            }

            // 3. Character N-grams (2-grams) for CJK and non-space-separated scripts
            if (word.length() >= 2) {
                for (int i = 0; i <= word.length() - 2; i++) {
                    tokens.add(word.substring(i, i + 2));
                }
            }
        }

        return tokens;
    }

    private Set<String> findCandidatesForTerm(String term) {
        // Direct inverted index lookup (matches word or prefix)
        Set<String> exact = invertedIndex.get(term);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }

        // Substring / n-gram lookup for mid-word matches or CJK characters
        if (term.length() >= 2) {
            Set<String> candidateIntersection = null;
            for (int i = 0; i <= term.length() - 2; i++) {
                String bigram = term.substring(i, i + 2);
                Set<String> bigramMatches = invertedIndex.get(bigram);
                if (bigramMatches == null || bigramMatches.isEmpty()) {
                    return Collections.emptySet();
                }
                if (candidateIntersection == null) {
                    candidateIntersection = new HashSet<>(bigramMatches);
                } else {
                    candidateIntersection.retainAll(bigramMatches);
                    if (candidateIntersection.isEmpty()) {
                        return Collections.emptySet();
                    }
                }
            }
            return candidateIntersection;
        }

        // For single-character mid-word lookup fallback to scanning indexed texts
        Set<String> fallbackMatches = new HashSet<>();
        for (Map.Entry<String, String> entry : searchTextsById.entrySet()) {
            if (entry.getValue().contains(term)) {
                fallbackMatches.add(entry.getKey());
            }
        }
        return fallbackMatches;
    }

    private static boolean matchesAllTerms(String normalizedText, List<String> queryTerms) {
        for (String term : queryTerms) {
            if (!normalizedText.contains(term)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalizes text by applying Unicode NFKC decomposition and lowercasing.
     *
     * @param text input string
     * @return normalized lowercase string
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}
