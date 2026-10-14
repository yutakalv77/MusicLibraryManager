package com.musiclibrary.controller;

import com.musiclibrary.model.PlayQueue;
import com.musiclibrary.model.RepeatMode;

/**
 * Listener interface for observing playback queue mode and order modifications.
 */
public interface QueueChangeListener {

    /**
     * Called when repeat mode changes.
     *
     * @param newMode new repeat mode
     */
    default void onRepeatModeChanged(RepeatMode newMode) {}

    /**
     * Called when shuffle state toggles.
     *
     * @param shuffle true if shuffle is enabled
     */
    default void onShuffleChanged(boolean shuffle) {}

    /**
     * Called when tracks in the queue are updated or reordered.
     *
     * @param queue updated play queue
     */
    default void onQueueUpdated(PlayQueue queue) {}
}
