package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;

/**
 * Interface defining audio playback capabilities, transport controls,
 * volume adjustment, and listener registration.
 */
public interface AudioPlayer extends AutoCloseable {

    /**
     * Begins or replaces playback with the specified track.
     *
     * @param track track to play
     */
    void play(Track track);

    /**
     * Pauses the currently playing track.
     * Has no effect if not currently playing.
     */
    void pause();

    /**
     * Resumes playback of a paused track.
     * Has no effect if not currently paused.
     */
    void resume();

    /**
     * Stops playback and resets playback position.
     */
    void stop();

    /**
     * Seeks playback position to the specified target position in seconds.
     *
     * @param seconds target playback position in seconds
     */
    void seekTo(long seconds);

    /**
     * Returns whether the player supports seeking.
     *
     * @return true if seeking is supported
     */
    default boolean isSeekable() {
        return true;
    }

    /**
     * Sets playback volume linearly in range [0.0f, 1.0f].
     *
     * @param volume volume between 0.0 (mute) and 1.0 (full)
     */
    void setVolume(float volume);

    /**
     * Gets the current playback volume in range [0.0f, 1.0f].
     *
     * @return current volume
     */
    float getVolume();

    /**
     * Returns the current playback state.
     *
     * @return PlaybackState (STOPPED, PLAYING, PAUSED)
     */
    PlaybackState getState();

    /**
     * Returns the track currently loaded in the player, or null if none.
     *
     * @return current Track or null
     */
    Track getCurrentTrack();

    /**
     * Returns elapsed playback position in seconds.
     *
     * @return elapsed seconds
     */
    long getCurrentPositionSeconds();

    /**
     * Returns total duration of the current track in seconds.
     *
     * @return total duration seconds
     */
    long getTotalDurationSeconds();

    /**
     * Returns whether the player is in PLAYING state.
     *
     * @return true if playing, false otherwise
     */
    boolean isPlaying();

    /**
     * Registers a listener for playback events.
     *
     * @param listener listener to register
     */
    void addPlayerListener(PlayerListener listener);

    /**
     * Unregisters a listener.
     *
     * @param listener listener to unregister
     */
    void removePlayerListener(PlayerListener listener);

    /**
     * Releases all audio resources and worker threads.
     */
    @Override
    void close();
}
