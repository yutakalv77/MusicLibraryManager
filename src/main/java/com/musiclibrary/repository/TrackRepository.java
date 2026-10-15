package com.musiclibrary.repository;

import com.musiclibrary.model.Track;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing audio tracks (tracks master and account_tracks).
 */
public interface TrackRepository {

    Optional<Track> findById(String trackId);

    Optional<Track> findByFilePath(String filePath);

    Optional<Track> findByAccountAndTrackId(String accountId, String trackId);

    /**
     * Retrieves all tracks associated with the specified account.
     * Performs an INNER JOIN of account_tracks and tracks to load complete metadata,
     * account-specific user data (rating, play count, date added), and IDs into memory.
     * Ordered by artist, album, disc_number, track_number.
     *
     * @param accountId target account ID
     * @return list of fully populated Track instances
     */
    List<Track> findByAccountId(String accountId);

    /**
     * Imports or updates a track in the master repository and links it to the specified account.
     *
     * @param accountId target account ID
     * @param track     track metadata to import
     * @return Track with finalized master ID and accountTrackId
     */
    Track importTrack(String accountId, Track track);

    /**
     * Imports a batch of tracks within a single transaction for maximum throughput.
     *
     * @param accountId target account ID
     * @param tracks    list of tracks to import
     */
    void importTracksBatch(String accountId, List<Track> tracks);

    /**
     * High-performance pinpoint update for account-specific playback stats and user rating.
     *
     * @param accountTrackId account-track entry ID (account_tracks.id)
     * @param rating         user rating (0 to 5)
     * @param playCount      play count (>= 0)
     * @param lastPlayedAt   timestamp of last playback (or null)
     */
    void updateUserData(String accountTrackId, int rating, int playCount, Instant lastPlayedAt);

    /**
     * Removes a track link from the specified account without deleting the master audio entry.
     *
     * @param accountId target account ID
     * @param trackId   master track ID
     */
    void deleteFromAccount(String accountId, String trackId);

    /**
     * Deletes a master track and cascades deletion to all account links and playlists.
     *
     * @param trackId master track ID
     */
    void deleteTrackMaster(String trackId);
}
