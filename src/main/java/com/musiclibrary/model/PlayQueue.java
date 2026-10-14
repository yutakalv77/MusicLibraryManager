package com.musiclibrary.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Thread-safe playback queue managing track order, Fisher-Yates shuffling,
 * loop repeat modes, and priority "Play Next" interruptions.
 */
public class PlayQueue {

    private final List<Track> originalTracks = new ArrayList<>();
    private final List<Track> playbackOrder = new ArrayList<>();
    private final LinkedList<Track> priorityQueue = new LinkedList<>();

    private int currentIndex = -1;
    private Track activeTrack = null;

    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean shuffle = false;
    private final Random random;

    public PlayQueue() {
        this(new Random());
    }

    public PlayQueue(Random random) {
        this.random = (random != null) ? random : new Random();
    }

    /**
     * Sets the queue with a list of tracks and sets playback index to startIndex.
     *
     * @param tracks     track list
     * @param startIndex initial index
     */
    public synchronized void setTracks(List<Track> tracks, int startIndex) {
        originalTracks.clear();
        playbackOrder.clear();
        priorityQueue.clear();

        if (tracks != null && !tracks.isEmpty()) {
            originalTracks.addAll(tracks);
            int safeStart = Math.max(0, Math.min(tracks.size() - 1, startIndex));
            Track initialTrack = tracks.get(safeStart);

            if (shuffle) {
                applyShuffleWithCurrent(initialTrack);
            } else {
                playbackOrder.addAll(tracks);
                currentIndex = safeStart;
                activeTrack = initialTrack;
            }
        } else {
            currentIndex = -1;
            activeTrack = null;
        }
    }

    /**
     * Sets the queue with a list of tracks and specifies the starting track entity.
     *
     * @param tracks       track list
     * @param currentTrack starting track
     */
    public synchronized void setTracks(List<Track> tracks, Track currentTrack) {
        int idx = 0;
        if (tracks != null && currentTrack != null) {
            int found = tracks.indexOf(currentTrack);
            if (found >= 0) {
                idx = found;
            }
        }
        setTracks(tracks, idx);
    }

    /**
     * Returns currently playing track, or null if queue is empty.
     *
     * @return active Track or null
     */
    public synchronized Track getCurrentTrack() {
        if (activeTrack != null) {
            return activeTrack;
        }
        if (currentIndex >= 0 && currentIndex < playbackOrder.size()) {
            return playbackOrder.get(currentIndex);
        }
        return null;
    }

    /**
     * Returns 0-based index of the currently active track in playbackOrder, or -1 if none.
     *
     * @return current index
     */
    public synchronized int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Returns total number of tracks in playbackOrder.
     *
     * @return size of queue
     */
    public synchronized int size() {
        return playbackOrder.size();
    }

    /**
     * Returns true if queue has no tracks and no priority tracks.
     *
     * @return true if empty
     */
    public synchronized boolean isEmpty() {
        return playbackOrder.isEmpty() && priorityQueue.isEmpty();
    }

    /**
     * Returns true if a subsequent track is available to play.
     *
     * @return true if has next
     */
    public synchronized boolean hasNext() {
        if (!priorityQueue.isEmpty()) {
            return true;
        }
        if (playbackOrder.isEmpty()) {
            return false;
        }
        if (repeatMode == RepeatMode.ONE || repeatMode == RepeatMode.ALL) {
            return true;
        }
        return currentIndex < playbackOrder.size() - 1;
    }

    /**
     * Moves to and returns the next track according to priority queue, repeat mode, and order.
     * Returns null if RepeatMode is OFF and end of queue is reached.
     *
     * @return next Track or null
     */
    public synchronized Track next() {
        // 1. Priority interruption queue has highest precedence
        if (!priorityQueue.isEmpty()) {
            activeTrack = priorityQueue.pollFirst();
            return activeTrack;
        }

        if (playbackOrder.isEmpty()) {
            activeTrack = null;
            return null;
        }

        // 2. RepeatMode.ONE repeats current track
        if (repeatMode == RepeatMode.ONE) {
            if (currentIndex >= 0 && currentIndex < playbackOrder.size()) {
                activeTrack = playbackOrder.get(currentIndex);
            } else {
                currentIndex = 0;
                activeTrack = playbackOrder.get(0);
            }
            return activeTrack;
        }

        // 3. Normal progression
        if (currentIndex < playbackOrder.size() - 1) {
            currentIndex++;
            activeTrack = playbackOrder.get(currentIndex);
            return activeTrack;
        }

        // 4. End reached: loop if RepeatMode.ALL
        if (repeatMode == RepeatMode.ALL) {
            currentIndex = 0;
            activeTrack = playbackOrder.get(0);
            return activeTrack;
        }

        // 5. RepeatMode.OFF reached end
        activeTrack = null;
        return null;
    }

    /**
     * Returns true if a previous track can be accessed.
     *
     * @return true if has previous
     */
    public synchronized boolean hasPrevious() {
        if (playbackOrder.isEmpty()) {
            return false;
        }
        if (repeatMode == RepeatMode.ALL) {
            return true;
        }
        return currentIndex > 0;
    }

    /**
     * Moves to and returns the previous track in the queue.
     *
     * @return previous Track or first track if at start
     */
    public synchronized Track previous() {
        if (playbackOrder.isEmpty()) {
            activeTrack = null;
            return null;
        }

        if (currentIndex > 0) {
            currentIndex--;
            activeTrack = playbackOrder.get(currentIndex);
            return activeTrack;
        }

        if (repeatMode == RepeatMode.ALL) {
            currentIndex = playbackOrder.size() - 1;
            activeTrack = playbackOrder.get(currentIndex);
            return activeTrack;
        }

        activeTrack = playbackOrder.get(0);
        return activeTrack;
    }

    /**
     * Enqueues a track to play immediately after the current song.
     *
     * @param track track to enqueue next
     */
    public synchronized void enqueueNext(Track track) {
        if (track != null) {
            priorityQueue.add(track);
        }
    }

    /**
     * Enqueues a batch of tracks to play immediately after the current song.
     *
     * @param tracks batch of tracks to enqueue next
     */
    public synchronized void enqueueNext(List<Track> tracks) {
        if (tracks != null) {
            for (Track t : tracks) {
                if (t != null) {
                    priorityQueue.add(t);
                }
            }
        }
    }

    /**
     * Appends a track to the end of the queue.
     *
     * @param track track to append
     */
    public synchronized void enqueue(Track track) {
        if (track == null) {
            return;
        }
        originalTracks.add(track);
        playbackOrder.add(track);
        if (currentIndex == -1) {
            currentIndex = 0;
            activeTrack = track;
        }
    }

    /**
     * Removes a track from the queue and adjusts current index safely.
     *
     * @param track track to remove
     * @return true if track was removed
     */
    public synchronized boolean removeTrack(Track track) {
        if (track == null) {
            return false;
        }
        priorityQueue.remove(track);
        boolean removedOrig = originalTracks.remove(track);

        int removeIdx = playbackOrder.indexOf(track);
        boolean removedOrder = false;
        if (removeIdx >= 0) {
            playbackOrder.remove(removeIdx);
            removedOrder = true;
            if (removeIdx < currentIndex) {
                currentIndex--;
            } else if (removeIdx == currentIndex) {
                if (currentIndex >= playbackOrder.size()) {
                    currentIndex = playbackOrder.size() - 1;
                }
                activeTrack = (currentIndex >= 0) ? playbackOrder.get(currentIndex) : null;
            }
        }
        return removedOrig || removedOrder;
    }

    /**
     * Removes track by ID from queue.
     *
     * @param trackId track ID
     * @return true if removed
     */
    public synchronized boolean removeTrack(String trackId) {
        if (trackId == null) {
            return false;
        }
        priorityQueue.removeIf(t -> trackId.equals(t.getId()));
        Track target = null;
        for (Track t : playbackOrder) {
            if (trackId.equals(t.getId())) {
                target = t;
                break;
            }
        }
        if (target != null) {
            return removeTrack(target);
        }
        return originalTracks.removeIf(t -> trackId.equals(t.getId()));
    }

    /**
     * Toggles or sets shuffle mode. When enabled, pins active track to index 0 and
     * shuffles remaining songs via Fisher-Yates algorithm.
     *
     * @param enabled true to enable shuffle
     */
    public synchronized void setShuffle(boolean enabled) {
        if (this.shuffle == enabled) {
            return;
        }
        this.shuffle = enabled;

        if (originalTracks.isEmpty()) {
            return;
        }

        Track current = getCurrentTrack();
        if (enabled) {
            applyShuffleWithCurrent(current);
        } else {
            playbackOrder.clear();
            playbackOrder.addAll(originalTracks);
            if (current != null) {
                currentIndex = playbackOrder.indexOf(current);
                if (currentIndex < 0) {
                    currentIndex = 0;
                }
            } else {
                currentIndex = -1;
            }
        }
    }

    private void applyShuffleWithCurrent(Track current) {
        List<Track> remaining = new ArrayList<>(originalTracks);
        if (current != null) {
            remaining.remove(current);
        }

        // Fisher-Yates shuffle
        for (int i = remaining.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Track temp = remaining.get(i);
            remaining.set(i, remaining.get(j));
            remaining.set(j, temp);
        }

        playbackOrder.clear();
        if (current != null) {
            playbackOrder.add(current);
            currentIndex = 0;
            activeTrack = current;
        } else {
            currentIndex = -1;
            activeTrack = null;
        }
        playbackOrder.addAll(remaining);
    }

    /**
     * Returns whether shuffle mode is enabled.
     *
     * @return true if shuffle is active
     */
    public synchronized boolean isShuffle() {
        return shuffle;
    }

    /**
     * Sets repeat mode.
     *
     * @param mode new repeat mode
     */
    public synchronized void setRepeatMode(RepeatMode mode) {
        this.repeatMode = (mode != null) ? mode : RepeatMode.OFF;
    }

    /**
     * Cycles to next repeat mode: OFF -> ALL -> ONE -> OFF.
     *
     * @return updated repeat mode
     */
    public synchronized RepeatMode cycleRepeatMode() {
        this.repeatMode = this.repeatMode.next();
        return this.repeatMode;
    }

    /**
     * Returns active repeat mode.
     *
     * @return current RepeatMode
     */
    public synchronized RepeatMode getRepeatMode() {
        return repeatMode;
    }

    /**
     * Returns an unmodifiable snapshot of current playbackOrder.
     *
     * @return unmodifiable track list
     */
    public synchronized List<Track> getPlaybackOrder() {
        return Collections.unmodifiableList(new ArrayList<>(playbackOrder));
    }

    /**
     * Returns an unmodifiable snapshot of priority tracks.
     *
     * @return unmodifiable priority list
     */
    public synchronized List<Track> getPriorityQueue() {
        return Collections.unmodifiableList(new ArrayList<>(priorityQueue));
    }

    /**
     * Clears all tracks, priority tracks, and resets indices.
     */
    public synchronized void clear() {
        originalTracks.clear();
        playbackOrder.clear();
        priorityQueue.clear();
        currentIndex = -1;
        activeTrack = null;
    }
}
