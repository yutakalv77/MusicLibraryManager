package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;

/**
 * Listener interface for monitoring audio playback lifecycle events.
 * Callbacks are dispatched safely on the Swing Event Dispatch Thread (EDT).
 */
public interface PlayerListener {

    /**
     * Called when the playback state transitions (e.g. STOPPED -> PLAYING, PLAYING -> PAUSED).
     *
     * @param newState new playback state
     */
    default void onStateChanged(PlaybackState newState) {}

    /**
     * Called when audio playback starts for a specific track.
     *
     * @param track track currently playing
     */
    default void onTrackStarted(Track track) {}

    /**
     * Called when a track finishes playing to the end (EOF).
     *
     * @param track track that completed playback
     */
    default void onTrackFinished(Track track) {}

    /**
     * Called periodically during playback to report elapsed position and duration.
     *
     * @param currentSeconds elapsed playback time in seconds
     * @param totalSeconds   total track duration in seconds
     */
    default void onProgressUpdate(long currentSeconds, long totalSeconds) {}

    /**
     * Called when an audio playback or decoding error occurs.
     *
     * @param error error details
     */
    default void onError(Throwable error) {}
}
