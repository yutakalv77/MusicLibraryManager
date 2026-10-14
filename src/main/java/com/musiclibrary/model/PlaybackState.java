package com.musiclibrary.model;

/**
 * Represents the playback state of the audio player.
 */
public enum PlaybackState {
    /**
     * Audio playback is currently stopped.
     */
    STOPPED,

    /**
     * Audio is actively playing.
     */
    PLAYING,

    /**
     * Audio playback is paused and can be resumed.
     */
    PAUSED
}
