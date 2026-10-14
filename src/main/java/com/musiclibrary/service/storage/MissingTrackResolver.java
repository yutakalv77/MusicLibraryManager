package com.musiclibrary.service.storage;

import com.musiclibrary.model.Track;
import com.musiclibrary.service.importer.FileScanner;
import com.musiclibrary.service.metadata.MetadataExtractor;
import com.musiclibrary.service.metadata.Mp3MetadataExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service detecting missing tracks in the library and automatically relinking them
 * to relocated or renamed audio files within a target directory tree using multi-factor scoring.
 * Preserves playback history, ratings, playlists, and adheres to non-destructive file policies.
 */
public class MissingTrackResolver {

    /**
     * Minimum score required for a candidate file to be accepted as a valid match.
     */
    public static final int MATCH_THRESHOLD = 50;

    public interface ResolveProgressListener {
        /**
         * Notified during candidate scanning and track evaluation.
         *
         * @param current     current track index being evaluated
         * @param total       total missing tracks to resolve
         * @param currentPath current file or track path being inspected
         */
        void onProgress(int current, int total, Path currentPath);

        /**
         * Notified whenever a missing track is successfully matched and relinked.
         *
         * @param track   relinked track entity
         * @param newPath new resolved audio file path
         */
        void onRelinked(Track track, Path newPath);

        /**
         * Notified upon completion of resolution process.
         *
         * @param relinkedCount  number of tracks successfully relinked
         * @param remainingCount number of tracks still missing
         */
        void onComplete(int relinkedCount, int remainingCount);

        /**
         * Notified if an unrecoverable error occurs during resolution.
         *
         * @param error error encountered
         */
        default void onError(Throwable error) {}
    }

    public interface ResolveTask {
        void cancel();
        boolean isCancelled();
        boolean isDone();
    }

    private final FileScanner fileScanner;
    private final MetadataExtractor metadataExtractor;
    private final ExecutorService executorService;

    public MissingTrackResolver() {
        this(new FileScanner(), new Mp3MetadataExtractor(), Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MissingTrackResolver-Worker");
            t.setDaemon(true);
            return t;
        }));
    }

    public MissingTrackResolver(FileScanner fileScanner, MetadataExtractor metadataExtractor, ExecutorService executorService) {
        this.fileScanner = Objects.requireNonNull(fileScanner, "fileScanner cannot be null");
        this.metadataExtractor = (metadataExtractor != null) ? metadataExtractor : new Mp3MetadataExtractor();
        this.executorService = Objects.requireNonNull(executorService, "executorService cannot be null");
    }

    /**
     * Checks if the backing file for the given track does not exist on disk.
     *
     * @param track track to inspect
     * @return true if file path is missing or inaccessible, false otherwise
     */
    public static boolean isMissing(Track track) {
        if (track == null) {
            return false;
        }
        String pathStr = track.getFilePath();
        if (pathStr == null || pathStr.isBlank()) {
            return true;
        }
        try {
            return !Files.exists(Path.of(pathStr));
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Identifies all tracks within the collection whose physical files are missing on disk,
     * updating their internal missing flag.
     *
     * @param tracks collection of tracks to inspect
     * @return list of missing tracks
     */
    public List<Track> findMissingTracks(Collection<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Track> missing = new ArrayList<>();
        for (Track t : tracks) {
            if (t != null) {
                boolean miss = isMissing(t);
                t.setMissing(miss);
                if (miss) {
                    missing.add(t);
                }
            }
        }
        return missing;
    }

    /**
     * Updates track path, file size, last modified timestamp, and clears missing flag.
     *
     * @param track   track entity to update
     * @param newPath new resolved audio file path
     * @return true if updated successfully, false if new path does not exist
     */
    public boolean relink(Track track, Path newPath) {
        if (track == null || newPath == null) {
            return false;
        }
        try {
            if (!Files.exists(newPath) || Files.isDirectory(newPath)) {
                return false;
            }
            track.setFilePath(newPath.toAbsolutePath().normalize().toString());
            track.setFileSizeBytes(Files.size(newPath));
            track.setLastModified(Files.getLastModifiedTime(newPath).toInstant());
            track.setMissing(false);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Scores and finds the best matching candidate audio file for a missing track.
     *
     * @param track          missing track
     * @param candidateFiles list of candidate audio files
     * @param excludedPaths  set of paths already matched to other tracks
     * @return best matching Path, or null if no candidate satisfies threshold
     */
    public Path findBestMatch(Track track, List<Path> candidateFiles, Set<Path> excludedPaths) {
        if (track == null || candidateFiles == null || candidateFiles.isEmpty()) {
            return null;
        }

        Path origPath = null;
        String origFileName = "";
        String origBaseName = "";
        try {
            if (track.getFilePath() != null && !track.getFilePath().isBlank()) {
                origPath = Path.of(track.getFilePath());
                Path fn = origPath.getFileName();
                if (fn != null) {
                    origFileName = fn.toString();
                    origBaseName = stripExtension(origFileName);
                }
            }
        } catch (Exception ignored) {
        }

        Path bestCandidate = null;
        int highestScore = 0;

        for (Path candidate : candidateFiles) {
            if (excludedPaths != null && excludedPaths.contains(candidate)) {
                continue;
            }

            int score = calculateMatchScore(track, candidate, origFileName, origBaseName);
            if (score > highestScore && score >= MATCH_THRESHOLD) {
                highestScore = score;
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    /**
     * Calculates match confidence score [0..120] between a missing track and a candidate file.
     */
    public int calculateMatchScore(Track track, Path candidate, String origFileName, String origBaseName) {
        int score = 0;
        String candFileName = candidate.getFileName() != null ? candidate.getFileName().toString() : "";
        String candBaseName = stripExtension(candFileName);

        // 1. Filename match
        if (!origFileName.isEmpty() && candFileName.equalsIgnoreCase(origFileName)) {
            score += 40; // Exact filename match
        } else if (!origBaseName.isEmpty() && candBaseName.equalsIgnoreCase(origBaseName)) {
            score += 35; // Base name match (e.g. extension case or slight extension change)
        } else if (track.getTitle() != null && !track.getTitle().isBlank()
                && candBaseName.equalsIgnoreCase(track.getTitle().trim())) {
            score += 30; // Candidate filename matches track title exactly
        } else if (!origBaseName.isEmpty() && (candBaseName.toLowerCase(Locale.ROOT).contains(origBaseName.toLowerCase(Locale.ROOT))
                || origBaseName.toLowerCase(Locale.ROOT).contains(candBaseName.toLowerCase(Locale.ROOT)))) {
            score += 15; // Partial filename overlap
        }

        // 2. File size match
        try {
            long candSize = Files.size(candidate);
            if (track.getFileSizeBytes() > 0 && candSize == track.getFileSizeBytes()) {
                score += 30; // Exact byte length match
            }
        } catch (IOException ignored) {
        }

        // If score is already strong or promising, perform metadata verification
        if (score >= 25 && metadataExtractor != null && metadataExtractor.supports(candidate)) {
            try {
                Track candTrack = metadataExtractor.extract(candidate);
                if (candTrack != null) {
                    // Duration match (within +/- 1 second)
                    if (track.getDurationSeconds() > 0 && candTrack.getDurationSeconds() > 0) {
                        if (Math.abs(track.getDurationSeconds() - candTrack.getDurationSeconds()) <= 1) {
                            score += 20;
                        }
                    }

                    // Title match
                    if (track.getTitle() != null && !track.getTitle().isBlank()
                            && candTrack.getTitle() != null && !candTrack.getTitle().isBlank()) {
                        if (track.getTitle().equalsIgnoreCase(candTrack.getTitle().trim())) {
                            score += 20;
                        }
                    }

                    // Artist match
                    if (track.getArtist() != null && !track.getArtist().isBlank()
                            && candTrack.getArtist() != null && !candTrack.getArtist().isBlank()) {
                        if (track.getArtist().equalsIgnoreCase(candTrack.getArtist().trim())) {
                            score += 10;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return score;
    }

    /**
     * Asynchronously scans target folder and relinks all missing tracks.
     *
     * @param missingTracks collection of missing tracks to resolve
     * @param searchFolder  folder to scan recursively for relocated files
     * @param listener      progress observer
     * @return task handle supporting cancellation
     */
    public ResolveTask resolveMissingTracksAsync(Collection<Track> missingTracks,
                                                 Path searchFolder,
                                                 ResolveProgressListener listener) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);

        Future<?> future = executorService.submit(() -> {
            try {
                if (missingTracks == null || missingTracks.isEmpty() || searchFolder == null || !Files.exists(searchFolder)) {
                    if (listener != null) {
                        listener.onComplete(0, missingTracks != null ? missingTracks.size() : 0);
                    }
                    done.set(true);
                    return;
                }

                // 1. Scan folder for candidate audio files
                List<Path> candidateFiles = fileScanner.scan(searchFolder);
                if (cancelled.get()) {
                    done.set(true);
                    return;
                }

                Set<Path> usedPaths = new HashSet<>();
                List<Track> targets = new ArrayList<>(missingTracks);
                int total = targets.size();
                int relinkedCount = 0;

                for (int i = 0; i < total; i++) {
                    if (cancelled.get()) {
                        break;
                    }

                    Track track = targets.get(i);
                    if (listener != null) {
                        Path currentP = null;
                        try {
                            if (track.getFilePath() != null && !track.getFilePath().isBlank()) {
                                currentP = Path.of(track.getFilePath());
                            }
                        } catch (Exception ignored) {
                        }
                        listener.onProgress(i + 1, total, currentP != null ? currentP : Path.of("missing"));
                    }

                    Path best = findBestMatch(track, candidateFiles, usedPaths);
                    if (best != null && relink(track, best)) {
                        usedPaths.add(best);
                        relinkedCount++;
                        if (listener != null) {
                            listener.onRelinked(track, best);
                        }
                    } else {
                        track.setMissing(true);
                    }
                }

                int remaining = total - relinkedCount;
                if (listener != null && !cancelled.get()) {
                    listener.onComplete(relinkedCount, remaining);
                }
            } catch (Throwable t) {
                if (listener != null && !cancelled.get()) {
                    listener.onError(t);
                }
            } finally {
                done.set(true);
            }
        });

        return new ResolveTask() {
            @Override
            public void cancel() {
                cancelled.set(true);
                future.cancel(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public boolean isDone() {
                return done.get() || future.isDone();
            }
        };
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public void shutdownNow() {
        executorService.shutdownNow();
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }
}
