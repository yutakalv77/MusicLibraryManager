package com.musiclibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PlayQueueTest {

    private List<Track> createTestTracks(int count) {
        List<Track> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(Track.builder()
                    .id("t" + i)
                    .title("Track " + i)
                    .filePath("track" + i + ".mp3")
                    .durationSeconds(180)
                    .build());
        }
        return list;
    }

    @Test
    @DisplayName("PlayQueue initializes with empty state, default OFF repeat, and shuffle false")
    void testInitialState() {
        PlayQueue queue = new PlayQueue();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isEqualTo(0);
        assertThat(queue.getCurrentIndex()).isEqualTo(-1);
        assertThat(queue.getCurrentTrack()).isNull();
        assertThat(queue.getRepeatMode()).isEqualTo(RepeatMode.OFF);
        assertThat(queue.isShuffle()).isFalse();
        assertThat(queue.hasNext()).isFalse();
        assertThat(queue.hasPrevious()).isFalse();
    }

    @Test
    @DisplayName("setTracks sets tracks and starting position accurately")
    void testSetTracks() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(5);

        queue.setTracks(tracks, 2);
        assertThat(queue.size()).isEqualTo(5);
        assertThat(queue.getCurrentIndex()).isEqualTo(2);
        assertThat(queue.getCurrentTrack()).isEqualTo(tracks.get(2));

        // By Track entity
        queue.setTracks(tracks, tracks.get(4));
        assertThat(queue.getCurrentIndex()).isEqualTo(4);
        assertThat(queue.getCurrentTrack()).isEqualTo(tracks.get(4));
    }

    @Test
    @DisplayName("Normal linear progression advances and halts at end when RepeatMode is OFF")
    void testLinearProgressionRepeatOff() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(3);
        queue.setTracks(tracks, 0);

        assertThat(queue.hasNext()).isTrue();
        Track t2 = queue.next();
        assertThat(t2).isEqualTo(tracks.get(1));
        assertThat(queue.getCurrentIndex()).isEqualTo(1);

        assertThat(queue.hasNext()).isTrue();
        Track t3 = queue.next();
        assertThat(t3).isEqualTo(tracks.get(2));
        assertThat(queue.getCurrentIndex()).isEqualTo(2);

        // Reached end with RepeatMode.OFF
        assertThat(queue.hasNext()).isFalse();
        Track end = queue.next();
        assertThat(end).isNull();
    }

    @Test
    @DisplayName("RepeatMode.ALL loops from end to start and start to end seamlessly")
    void testRepeatAllLooping() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(3);
        queue.setTracks(tracks, 2); // Start at last track
        queue.setRepeatMode(RepeatMode.ALL);

        assertThat(queue.hasNext()).isTrue();
        Track looped = queue.next();
        assertThat(looped).isEqualTo(tracks.get(0));
        assertThat(queue.getCurrentIndex()).isEqualTo(0);

        // Previous from start loops back to end
        assertThat(queue.hasPrevious()).isTrue();
        Track prevLooped = queue.previous();
        assertThat(prevLooped).isEqualTo(tracks.get(2));
        assertThat(queue.getCurrentIndex()).isEqualTo(2);
    }

    @Test
    @DisplayName("RepeatMode.ONE keeps returning the active track indefinitely")
    void testRepeatOne() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(3);
        queue.setTracks(tracks, 1);
        queue.setRepeatMode(RepeatMode.ONE);

        assertThat(queue.hasNext()).isTrue();
        Track repeated1 = queue.next();
        assertThat(repeated1).isEqualTo(tracks.get(1));
        assertThat(queue.getCurrentIndex()).isEqualTo(1);

        Track repeated2 = queue.next();
        assertThat(repeated2).isEqualTo(tracks.get(1));
        assertThat(queue.getCurrentIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("enqueueNext priority queue takes precedence and preserves underlying playback position")
    void testPriorityQueuePlayNext() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(4);
        queue.setTracks(tracks, 0); // Currently on track 1 (index 0)

        Track prioritySongA = Track.builder().id("pA").title("Priority A").filePath("pA.mp3").build();
        Track prioritySongB = Track.builder().id("pB").title("Priority B").filePath("pB.mp3").build();

        queue.enqueueNext(prioritySongA);
        queue.enqueueNext(prioritySongB);

        assertThat(queue.getPriorityQueue()).containsExactly(prioritySongA, prioritySongB);

        // 1. Next should yield Priority A
        Track next1 = queue.next();
        assertThat(next1).isEqualTo(prioritySongA);
        assertThat(queue.getCurrentIndex()).isEqualTo(0); // Underlying index unchanged

        // 2. Next should yield Priority B
        Track next2 = queue.next();
        assertThat(next2).isEqualTo(prioritySongB);
        assertThat(queue.getCurrentIndex()).isEqualTo(0);

        // 3. Priority exhausted -> resumes normal track 2 (index 1)
        Track next3 = queue.next();
        assertThat(next3).isEqualTo(tracks.get(1));
        assertThat(queue.getCurrentIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("setShuffle pins active track to index 0 and restores original order upon unshuffle")
    void testShuffleAndUnshuffle() {
        PlayQueue queue = new PlayQueue(new Random(42));
        List<Track> tracks = createTestTracks(6);
        queue.setTracks(tracks, 3); // Active track is Track 4

        Track current = tracks.get(3);
        assertThat(queue.getCurrentTrack()).isEqualTo(current);

        // 1. Enable Shuffle
        queue.setShuffle(true);
        assertThat(queue.isShuffle()).isTrue();
        assertThat(queue.getCurrentIndex()).isEqualTo(0);
        assertThat(queue.getCurrentTrack()).isEqualTo(current);
        assertThat(queue.getPlaybackOrder().get(0)).isEqualTo(current);
        assertThat(queue.getPlaybackOrder()).hasSize(6);

        // 2. Disable Shuffle
        queue.setShuffle(false);
        assertThat(queue.isShuffle()).isFalse();
        assertThat(queue.getPlaybackOrder()).containsExactlyElementsOf(tracks);
        assertThat(queue.getCurrentTrack()).isEqualTo(current);
        assertThat(queue.getCurrentIndex()).isEqualTo(3);
    }

    @Test
    @DisplayName("cycleRepeatMode cycles accurately between OFF, ALL, and ONE")
    void testCycleRepeatMode() {
        PlayQueue queue = new PlayQueue();
        assertThat(queue.getRepeatMode()).isEqualTo(RepeatMode.OFF);

        assertThat(queue.cycleRepeatMode()).isEqualTo(RepeatMode.ALL);
        assertThat(queue.cycleRepeatMode()).isEqualTo(RepeatMode.ONE);
        assertThat(queue.cycleRepeatMode()).isEqualTo(RepeatMode.OFF);
    }

    @Test
    @DisplayName("removeTrack removes tracks and maintains valid current index")
    void testRemoveTrack() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(4);
        queue.setTracks(tracks, 2); // currently on track 3 (index 2)

        // Remove track before current index
        queue.removeTrack(tracks.get(0));
        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.getCurrentIndex()).isEqualTo(1); // Index shifted by -1
        assertThat(queue.getCurrentTrack()).isEqualTo(tracks.get(2));

        // Remove by ID
        queue.removeTrack("t4");
        assertThat(queue.size()).isEqualTo(2);

        // Clear
        queue.clear();
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.getCurrentIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Empty queue and single-track queue behave safely across all repeat and shuffle modes")
    void testEmptyAndSingleTrackBoundaries() {
        PlayQueue emptyQueue = new PlayQueue();

        // 1. Empty queue boundary checks
        emptyQueue.setTracks(null, 0);
        assertThat(emptyQueue.isEmpty()).isTrue();
        assertThat(emptyQueue.hasNext()).isFalse();
        assertThat(emptyQueue.next()).isNull();
        assertThat(emptyQueue.hasPrevious()).isFalse();
        assertThat(emptyQueue.previous()).isNull();

        // Empty with ALL and ONE repeat
        emptyQueue.setRepeatMode(RepeatMode.ALL);
        assertThat(emptyQueue.hasNext()).isFalse();
        assertThat(emptyQueue.next()).isNull();
        assertThat(emptyQueue.hasPrevious()).isFalse();
        assertThat(emptyQueue.previous()).isNull();

        emptyQueue.setRepeatMode(RepeatMode.ONE);
        assertThat(emptyQueue.hasNext()).isFalse();
        assertThat(emptyQueue.next()).isNull();

        // Empty with shuffle
        emptyQueue.setShuffle(true);
        assertThat(emptyQueue.isEmpty()).isTrue();
        emptyQueue.setShuffle(false);
        assertThat(emptyQueue.isEmpty()).isTrue();

        // 2. Single-track queue checks
        PlayQueue singleQueue = new PlayQueue();
        Track solo = createTestTracks(1).get(0);
        singleQueue.setTracks(List.of(solo), 0);
        assertThat(singleQueue.size()).isEqualTo(1);
        assertThat(singleQueue.getCurrentTrack()).isEqualTo(solo);

        // Repeat OFF: no next, hasPrevious false, previous returns solo
        singleQueue.setRepeatMode(RepeatMode.OFF);
        assertThat(singleQueue.hasNext()).isFalse();
        assertThat(singleQueue.next()).isNull();
        assertThat(singleQueue.hasPrevious()).isFalse();
        assertThat(singleQueue.previous()).isEqualTo(solo);

        // Repeat ALL: hasNext true, next returns solo, hasPrevious true, previous returns solo
        singleQueue.setRepeatMode(RepeatMode.ALL);
        assertThat(singleQueue.hasNext()).isTrue();
        assertThat(singleQueue.next()).isEqualTo(solo);
        assertThat(singleQueue.hasPrevious()).isTrue();
        assertThat(singleQueue.previous()).isEqualTo(solo);

        // Repeat ONE: hasNext true, next returns solo
        singleQueue.setRepeatMode(RepeatMode.ONE);
        assertThat(singleQueue.hasNext()).isTrue();
        assertThat(singleQueue.next()).isEqualTo(solo);

        // Single track with shuffle: retains single element pinned at 0
        singleQueue.setShuffle(true);
        assertThat(singleQueue.size()).isEqualTo(1);
        assertThat(singleQueue.getCurrentIndex()).isEqualTo(0);
        assertThat(singleQueue.getCurrentTrack()).isEqualTo(solo);
        singleQueue.setShuffle(false);
        assertThat(singleQueue.size()).isEqualTo(1);
        assertThat(singleQueue.getCurrentTrack()).isEqualTo(solo);
    }

    @Test
    @DisplayName("Fisher-Yates shuffle produces complete permutation without omissions or duplicates")
    void testFisherYatesShufflePermutationIntegrity() {
        PlayQueue queue = new PlayQueue(new Random(12345));
        List<Track> tracks = createTestTracks(10);
        queue.setTracks(tracks, 4); // Track 5 active

        Track activeTrack = tracks.get(4);
        queue.setShuffle(true);

        List<Track> shuffledOrder = queue.getPlaybackOrder();
        assertThat(shuffledOrder).hasSize(10);
        assertThat(shuffledOrder.get(0)).isEqualTo(activeTrack);
        // Integrity: exactly the original tracks with no duplicates
        assertThat(shuffledOrder).containsExactlyInAnyOrderElementsOf(tracks);
        assertThat(shuffledOrder.stream().distinct().count()).isEqualTo(10);

        // Disabling shuffle restores exact original list
        queue.setShuffle(false);
        assertThat(queue.getPlaybackOrder()).containsExactlyElementsOf(tracks);
        assertThat(queue.getCurrentIndex()).isEqualTo(4);
        assertThat(queue.getCurrentTrack()).isEqualTo(activeTrack);
    }

    @Test
    @DisplayName("enqueueNext batch and null guards operate safely")
    void testPriorityQueueBatchAndNullSafety() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(3);
        queue.setTracks(tracks, 0);

        // Null checks
        queue.enqueueNext((Track) null);
        queue.enqueueNext((List<Track>) null);
        assertThat(queue.getPriorityQueue()).isEmpty();

        // Batch addition
        Track p1 = Track.builder().id("p1").title("P1").filePath("p1.mp3").build();
        Track p2 = Track.builder().id("p2").title("P2").filePath("p2.mp3").build();
        queue.enqueueNext(List.of(p1, p2));
        assertThat(queue.getPriorityQueue()).containsExactly(p1, p2);
        assertThat(queue.hasNext()).isTrue();

        // Consumption
        assertThat(queue.next()).isEqualTo(p1);
        assertThat(queue.next()).isEqualTo(p2);
        assertThat(queue.getPriorityQueue()).isEmpty();

        // Enqueue to empty queue
        queue.clear();
        Track lonely = Track.builder().id("lonely").title("Lonely").filePath("lonely.mp3").build();
        queue.enqueue(lonely);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.getCurrentTrack()).isEqualTo(lonely);
        assertThat(queue.getCurrentIndex()).isEqualTo(0);

        // Enqueue null safe
        queue.enqueue(null);
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("removeTrack handles middle, last, currently active, priority, and non-existent tracks safely")
    void testRemoveTrackComprehensiveScenarios() {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(5);
        queue.setTracks(tracks, 2); // on track 3 (index 2)

        // 1. Remove track AFTER currentIndex (index 4)
        boolean removedAfter = queue.removeTrack(tracks.get(4));
        assertThat(removedAfter).isTrue();
        assertThat(queue.size()).isEqualTo(4);
        assertThat(queue.getCurrentIndex()).isEqualTo(2); // unchanged
        assertThat(queue.getCurrentTrack()).isEqualTo(tracks.get(2));

        // 2. Remove CURRENTLY active track (index 2)
        boolean removedCurrent = queue.removeTrack(tracks.get(2));
        assertThat(removedCurrent).isTrue();
        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.getCurrentIndex()).isEqualTo(2); // new item at index 2 (former track 4)
        assertThat(queue.getCurrentTrack()).isEqualTo(tracks.get(3));

        // 3. Remove last track when currently at last index
        queue.next(); // now at index 2 (last track)
        assertThat(queue.getCurrentIndex()).isEqualTo(2);
        boolean removedLast = queue.removeTrack(queue.getCurrentTrack());
        assertThat(removedLast).isTrue();
        assertThat(queue.size()).isEqualTo(2);
        assertThat(queue.getCurrentIndex()).isEqualTo(1); // clamped to new last index

        // 4. Remove track in priorityQueue
        Track priorityTrack = Track.builder().id("pri").title("Pri").filePath("pri.mp3").build();
        queue.enqueueNext(priorityTrack);
        assertThat(queue.getPriorityQueue()).contains(priorityTrack);
        boolean removedPri = queue.removeTrack(priorityTrack);
        assertThat(removedPri).isFalse(); // not in playbackOrder/originalTracks, but removed from priorityQueue
        assertThat(queue.getPriorityQueue()).doesNotContain(priorityTrack);

        // 5. Remove null or non-existent
        assertThat(queue.removeTrack((Track) null)).isFalse();
        assertThat(queue.removeTrack((String) null)).isFalse();
        assertThat(queue.removeTrack("non-existent-id")).isFalse();
    }

    @Test
    @DisplayName("PlayQueue maintains thread safety and data integrity under concurrent multi-threaded access")
    void testThreadSafetyConcurrentOperations() throws Exception {
        PlayQueue queue = new PlayQueue();
        List<Track> tracks = createTestTracks(50);
        queue.setTracks(tracks, 0);

        int threadCount = 4;
        int operationsPerThread = 200;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int op = 0; op < operationsPerThread; op++) {
                        switch ((op + threadId) % 6) {
                            case 0 -> queue.next();
                            case 1 -> queue.previous();
                            case 2 -> queue.setShuffle(op % 2 == 0);
                            case 3 -> queue.cycleRepeatMode();
                            case 4 -> {
                                Track pri = Track.builder().id("pri_" + threadId + "_" + op).title("P").filePath("p.mp3").build();
                                queue.enqueueNext(pri);
                            }
                            case 5 -> {
                                List<Track> currentOrder = queue.getPlaybackOrder();
                                if (!currentOrder.isEmpty()) {
                                    queue.removeTrack(currentOrder.get(0));
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    errorRef.compareAndSet(null, t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errorRef.get()).isNull();
    }
}
