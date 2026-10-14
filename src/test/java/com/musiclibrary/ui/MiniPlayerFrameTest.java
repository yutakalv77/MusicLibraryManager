package com.musiclibrary.ui;

import com.musiclibrary.controller.PlayerController;
import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.audio.AudioPlayer;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MiniPlayerFrameTest {

    private MockAudioPlayer mockAudioPlayer;
    private PlayerController playerController;
    private MiniPlayerFrame miniPlayerFrame;
    private AtomicBoolean restoreActionCalled;

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);

        mockAudioPlayer = new MockAudioPlayer();
        playerController = new PlayerController(mockAudioPlayer, null);
        restoreActionCalled = new AtomicBoolean(false);

        miniPlayerFrame = new MiniPlayerFrame(playerController, () -> restoreActionCalled.set(true));
    }

    @AfterEach
    void tearDown() {
        if (miniPlayerFrame != null) {
            miniPlayerFrame.dispose();
        }
        if (playerController != null) {
            playerController.dispose();
        }
    }

    @Test
    @DisplayName("MiniPlayerFrame initializes components, layout constraints, and default states")
    void testInitialization() {
        assertThat(miniPlayerFrame.getTitle()).isEqualTo("Mini Player");
        assertThat(miniPlayerFrame.isResizable()).isFalse();
        assertThat(miniPlayerFrame.getMinimumSize()).isEqualTo(new Dimension(380, 95));
        assertThat(miniPlayerFrame.isAlwaysOnTop()).isFalse();

        assertThat(miniPlayerFrame.getArtworkLabel()).isNotNull();
        assertThat(miniPlayerFrame.getTitleLabel()).isNotNull();
        assertThat(miniPlayerFrame.getArtistLabel()).isNotNull();
        assertThat(miniPlayerFrame.getPrevButton()).isNotNull();
        assertThat(miniPlayerFrame.getPlayPauseButton()).isNotNull();
        assertThat(miniPlayerFrame.getNextButton()).isNotNull();
        assertThat(miniPlayerFrame.getSeekSlider()).isNotNull();
        assertThat(miniPlayerFrame.getVolumeSlider()).isNotNull();
        assertThat(miniPlayerFrame.getPinButton()).isNotNull();
        assertThat(miniPlayerFrame.getRestoreButton()).isNotNull();
    }

    @Test
    @DisplayName("toggleAlwaysOnTop toggles pin state, icon, and tooltip")
    void testToggleAlwaysOnTop() {
        assertThat(miniPlayerFrame.isAlwaysOnTop()).isFalse();
        assertThat(miniPlayerFrame.getPinButton().getText()).isEqualTo("📌");

        miniPlayerFrame.getPinButton().doClick();
        assertThat(miniPlayerFrame.isAlwaysOnTop()).isTrue();
        assertThat(miniPlayerFrame.getPinButton().getText()).isEqualTo("📍");
        assertThat(miniPlayerFrame.getPinButton().getToolTipText()).isEqualTo("Unpin");

        miniPlayerFrame.getPinButton().doClick();
        assertThat(miniPlayerFrame.isAlwaysOnTop()).isFalse();
        assertThat(miniPlayerFrame.getPinButton().getText()).isEqualTo("📌");
        assertThat(miniPlayerFrame.getPinButton().getToolTipText()).isEqualTo("Always on Top");
    }

    @Test
    @DisplayName("Transport buttons trigger corresponding actions on PlayerController")
    void testTransportButtons() {
        Track t = Track.builder()
                .id("t1")
                .filePath("tracks/t1.mp3")
                .title("Sample Track")
                .artist("Artist")
                .build();
        playerController.getQueue().setTracks(List.of(t), 0);

        // Play/Pause button
        miniPlayerFrame.getPlayPauseButton().doClick();
        assertThat(mockAudioPlayer.getState()).isEqualTo(PlaybackState.PLAYING);

        miniPlayerFrame.getPlayPauseButton().doClick();
        assertThat(mockAudioPlayer.getState()).isEqualTo(PlaybackState.PAUSED);

        // Next and Prev buttons
        AtomicBoolean nextInvoked = new AtomicBoolean(false);
        playerController.setOnNextAction(() -> nextInvoked.set(true));
        miniPlayerFrame.getNextButton().doClick();
        assertThat(nextInvoked.get()).isTrue();

        AtomicBoolean prevInvoked = new AtomicBoolean(false);
        playerController.setOnPreviousAction(() -> prevInvoked.set(true));
        miniPlayerFrame.getPrevButton().doClick();
        assertThat(prevInvoked.get()).isTrue();
    }

    @Test
    @DisplayName("PlayerListener updates UI for playback state and track metadata")
    void testPlaybackStateAndTrackInfoUpdates() {
        // State update
        miniPlayerFrame.updatePlaybackState(PlaybackState.PLAYING);
        assertThat(miniPlayerFrame.getPlayPauseButton().getText()).isEqualTo("⏸");

        miniPlayerFrame.updatePlaybackState(PlaybackState.PAUSED);
        assertThat(miniPlayerFrame.getPlayPauseButton().getText()).isEqualTo("▶");

        miniPlayerFrame.updatePlaybackState(PlaybackState.STOPPED);
        assertThat(miniPlayerFrame.getPlayPauseButton().getText()).isEqualTo("▶");

        // Track info update
        Track track = Track.builder()
                .id("t1")
                .filePath("test.mp3")
                .title("Radioactive")
                .artist("Imagine Dragons")
                .album("Night Visions")
                .durationSeconds(186)
                .build();

        miniPlayerFrame.updateTrackInfo(track);
        assertThat(miniPlayerFrame.getTitleLabel().getText()).isEqualTo("Radioactive");
        assertThat(miniPlayerFrame.getArtistLabel().getText()).contains("Imagine Dragons");
        assertThat(miniPlayerFrame.getArtistLabel().getText()).contains("Night Visions");

        // Clear track info
        miniPlayerFrame.updateTrackInfo(null);
        assertThat(miniPlayerFrame.getTitleLabel().getText()).isEqualTo("No track selected");
        assertThat(miniPlayerFrame.getArtistLabel().getText()).isEmpty();
    }

    @Test
    @DisplayName("updateProgress syncs slider and time labels unless user is scrubbing")
    void testProgressAndScrubbingGuard() {
        miniPlayerFrame.updateProgress(30, 120);

        assertThat(miniPlayerFrame.getCurrentTimeLabel().getText()).isEqualTo("00:30");
        assertThat(miniPlayerFrame.getTotalTimeLabel().getText()).isEqualTo("02:00");
        assertThat(miniPlayerFrame.getSeekSlider().getValue()).isEqualTo(250); // 30/120 * 1000 = 250

        // Simulate mouse press to begin scrubbing
        MouseEvent pressEvent = new MouseEvent(
                miniPlayerFrame.getSeekSlider(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0, 10, 10, 1, false
        );
        for (var ml : miniPlayerFrame.getSeekSlider().getMouseListeners()) {
            ml.mousePressed(pressEvent);
        }

        // Setting progress while scrubbing should NOT update slider
        miniPlayerFrame.getSeekSlider().setValue(600);
        miniPlayerFrame.updateProgress(10, 120);
        assertThat(miniPlayerFrame.getSeekSlider().getValue()).isEqualTo(600);

        // Simulate mouse release to commit seek
        MouseEvent releaseEvent = new MouseEvent(
                miniPlayerFrame.getSeekSlider(),
                MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(),
                0, 10, 10, 1, false
        );
        for (var ml : miniPlayerFrame.getSeekSlider().getMouseListeners()) {
            ml.mouseReleased(releaseEvent);
        }

        // Seek should have been dispatched to AudioPlayer
        assertThat(mockAudioPlayer.getLastSeekSeconds()).isEqualTo(72); // 600/1000 * 120 = 72
    }

    @Test
    @DisplayName("Volume slider synchronizes volume changes with PlayerController")
    void testVolumeSliderSynchronization() {
        miniPlayerFrame.getVolumeSlider().setValue(85);
        assertThat(playerController.getVolumePercent()).isEqualTo(85);
    }

    @Test
    @DisplayName("Restore button and restore method invoke restoreAction callback")
    void testRestoreAction() {
        miniPlayerFrame.getRestoreButton().doClick();
        assertThat(restoreActionCalled.get()).isTrue();
    }

    @Test
    @DisplayName("updateLanguage updates texts dynamically")
    void testUpdateLanguage() {
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        assertThat(miniPlayerFrame.getTitle()).isEqualTo("ミニプレーヤー");
        assertThat(miniPlayerFrame.getRestoreButton().getToolTipText()).isEqualTo("メインウィンドウに戻す");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        assertThat(miniPlayerFrame.getTitle()).isEqualTo("Mini Player");
        assertThat(miniPlayerFrame.getRestoreButton().getToolTipText()).isEqualTo("Restore Main Window");
    }

    @Test
    @DisplayName("dispose unregisters listeners and sets disposed flag")
    void testDispose() {
        assertThat(miniPlayerFrame.isDisposed()).isFalse();
        int initialListenerCount = mockAudioPlayer.listeners.size();
        assertThat(initialListenerCount).isGreaterThan(1);

        miniPlayerFrame.dispose();
        assertThat(miniPlayerFrame.isDisposed()).isTrue();
        assertThat(mockAudioPlayer.listeners).hasSize(initialListenerCount - 1);

        // Idempotency: multiple dispose calls should be safe
        org.assertj.core.api.Assertions.assertThatCode(miniPlayerFrame::dispose)
                .doesNotThrowAnyException();

        // syncState after dispose is a safe no-op
        org.assertj.core.api.Assertions.assertThatCode(miniPlayerFrame::syncState)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Keyboard shortcuts (Space, Ctrl+M, Ctrl+T, Left, Right) execute corresponding actions")
    void testKeyboardShortcutsIntegration() {
        Track t = Track.builder()
                .id("t-shortcut")
                .filePath("shortcut.mp3")
                .title("Shortcut Song")
                .artist("Artist")
                .build();
        playerController.getQueue().setTracks(List.of(t), 0);

        javax.swing.ActionMap actionMap = miniPlayerFrame.getRootPane().getActionMap();

        // 1. Space: togglePlayPause
        javax.swing.Action playPauseAction = actionMap.get("togglePlayPause");
        assertThat(playPauseAction).isNotNull();
        playPauseAction.actionPerformed(new java.awt.event.ActionEvent(miniPlayerFrame, java.awt.event.ActionEvent.ACTION_PERFORMED, "togglePlayPause"));
        assertThat(mockAudioPlayer.getState()).isEqualTo(PlaybackState.PLAYING);

        playPauseAction.actionPerformed(new java.awt.event.ActionEvent(miniPlayerFrame, java.awt.event.ActionEvent.ACTION_PERFORMED, "togglePlayPause"));
        assertThat(mockAudioPlayer.getState()).isEqualTo(PlaybackState.PAUSED);

        // 2. Ctrl+M: restoreWindow
        javax.swing.Action restoreAction = actionMap.get("restoreWindow");
        assertThat(restoreAction).isNotNull();
        restoreAction.actionPerformed(new java.awt.event.ActionEvent(miniPlayerFrame, java.awt.event.ActionEvent.ACTION_PERFORMED, "restoreWindow"));
        assertThat(restoreActionCalled.get()).isTrue();

        // 3. Ctrl+T: toggleTheme
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        javax.swing.Action themeAction = actionMap.get("toggleTheme");
        assertThat(themeAction).isNotNull();
        themeAction.actionPerformed(new java.awt.event.ActionEvent(miniPlayerFrame, java.awt.event.ActionEvent.ACTION_PERFORMED, "toggleTheme"));
        assertThat(ThemeManager.getInstance().getCurrentTheme()).isEqualTo(AppTheme.LIGHT);

        // 4. Left: skipBackward (rewinds 5 seconds)
        mockAudioPlayer.seekTo(30);
        javax.swing.Action skipBackAction = actionMap.get("skipBackward");
        assertThat(skipBackAction).isNotNull();
        skipBackAction.actionPerformed(new java.awt.event.ActionEvent(miniPlayerFrame, java.awt.event.ActionEvent.ACTION_PERFORMED, "skipBackward"));
        assertThat(mockAudioPlayer.getLastSeekSeconds()).isEqualTo(25);

        // 5. Right: skipForward (advances 5 seconds)
        javax.swing.Action skipFwdAction = actionMap.get("skipForward");
        assertThat(skipFwdAction).isNotNull();
        skipFwdAction.actionPerformed(new java.awt.event.ActionEvent(miniPlayerFrame, java.awt.event.ActionEvent.ACTION_PERFORMED, "skipForward"));
        assertThat(mockAudioPlayer.getLastSeekSeconds()).isEqualTo(30);
    }

    @Test
    @DisplayName("windowClosing event triggers restore action and null restoreAction is safe")
    void testWindowClosingEventAndNullRestoreAction() {
        // 1. windowClosing event invokes restoreAction
        java.awt.event.WindowEvent closingEvent = new java.awt.event.WindowEvent(
                miniPlayerFrame,
                java.awt.event.WindowEvent.WINDOW_CLOSING
        );
        for (var wl : miniPlayerFrame.getWindowListeners()) {
            wl.windowClosing(closingEvent);
        }
        assertThat(restoreActionCalled.get()).isTrue();

        // 2. Null restoreAction fallback
        MiniPlayerFrame noRestoreFrame = new MiniPlayerFrame(playerController, null);
        try {
            noRestoreFrame.setVisible(true);
            assertThat(noRestoreFrame.isVisible()).isTrue();
            noRestoreFrame.restore();
            assertThat(noRestoreFrame.isVisible()).isFalse();
        } finally {
            noRestoreFrame.dispose();
        }
    }

    @Test
    @DisplayName("Volume slider synchronizes bi-directionally between UI and PlayerController without recursion")
    void testBidirectionalVolumeSynchronizationAndGuard() {
        // UI slider -> PlayerController
        miniPlayerFrame.getVolumeSlider().setValue(45);
        assertThat(playerController.getVolumePercent()).isEqualTo(45);

        // PlayerController programmatic update -> MiniPlayerFrame.syncState()
        playerController.setVolumePercent(88);
        miniPlayerFrame.syncState();
        assertThat(miniPlayerFrame.getVolumeSlider().getValue()).isEqualTo(88);
    }

    @Test
    @DisplayName("syncState synchronizes state without interrupting active playback or seek position")
    void testSyncStateMaintainsUninterruptedPlayback() {
        Track activeTrack = Track.builder()
                .id("t-active")
                .filePath("active.mp3")
                .title("Viva La Vida")
                .artist("Coldplay")
                .album("Viva la Vida or Death and All His Friends")
                .durationSeconds(242)
                .build();

        mockAudioPlayer.play(activeTrack);
        mockAudioPlayer.seekTo(120);
        playerController.setVolumePercent(65);

        // Call syncState
        miniPlayerFrame.syncState();

        assertThat(mockAudioPlayer.getState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(miniPlayerFrame.getPlayPauseButton().getText()).isEqualTo("⏸");
        assertThat(miniPlayerFrame.getTitleLabel().getText()).isEqualTo("Viva La Vida");
        assertThat(miniPlayerFrame.getArtistLabel().getText()).contains("Coldplay");
        assertThat(miniPlayerFrame.getArtistLabel().getText()).contains("Viva la Vida");
        assertThat(miniPlayerFrame.getCurrentTimeLabel().getText()).isEqualTo("02:00");
        assertThat(miniPlayerFrame.getTotalTimeLabel().getText()).isEqualTo("01:40"); // 100s total duration in mock
        assertThat(miniPlayerFrame.getVolumeSlider().getValue()).isEqualTo(65);
    }

    @Test
    @DisplayName("updateProgress handles totalSeconds=0 safely and respects valueIsAdjusting")
    void testScrubbingGuardBoundaries() {
        // 1. totalSeconds = 0: no division by zero error, slider set to 0
        miniPlayerFrame.updateProgress(0, 0);
        assertThat(miniPlayerFrame.getSeekSlider().getValue()).isEqualTo(0);
        assertThat(miniPlayerFrame.getCurrentTimeLabel().getText()).isEqualTo("00:00");
        assertThat(miniPlayerFrame.getTotalTimeLabel().getText()).isEqualTo("00:00");

        // 2. seekSlider.setValueIsAdjusting(true) guards against external updates
        miniPlayerFrame.getSeekSlider().setValue(300);
        miniPlayerFrame.getSeekSlider().setValueIsAdjusting(true);
        miniPlayerFrame.updateProgress(90, 180);
        assertThat(miniPlayerFrame.getSeekSlider().getValue()).isEqualTo(300); // untouched

        miniPlayerFrame.getSeekSlider().setValueIsAdjusting(false);

        // 3. mouseReleased when currentTotalSeconds == 0 does not trigger seek
        miniPlayerFrame.updateProgress(0, 0); // sets currentTotalSeconds = 0
        mockAudioPlayer.seekTo(-1);

        MouseEvent pressEvent = new MouseEvent(
                miniPlayerFrame.getSeekSlider(),
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0, 10, 10, 1, false
        );
        for (var ml : miniPlayerFrame.getSeekSlider().getMouseListeners()) {
            ml.mousePressed(pressEvent);
        }

        MouseEvent releaseEvent = new MouseEvent(
                miniPlayerFrame.getSeekSlider(),
                MouseEvent.MOUSE_RELEASED,
                System.currentTimeMillis(),
                0, 10, 10, 1, false
        );
        for (var ml : miniPlayerFrame.getSeekSlider().getMouseListeners()) {
            ml.mouseReleased(releaseEvent);
        }

        // Seek should NOT have been invoked
        assertThat(mockAudioPlayer.getLastSeekSeconds()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Track artwork scaling handles valid image bytes, corrupted bytes, and missing album name")
    void testTrackArtworkAndMetadataBoundaries() throws Exception {
        // 1. Valid PNG artwork
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        byte[] validPng = baos.toByteArray();

        Track trackWithArt = Track.builder()
                .id("t-art")
                .filePath("art.mp3")
                .title("Art Song")
                .artist("Artist")
                .album("Album")
                .artworkData(validPng)
                .build();

        miniPlayerFrame.updateTrackInfo(trackWithArt);
        assertThat(miniPlayerFrame.getArtworkLabel().getIcon()).isNotNull();
        assertThat(miniPlayerFrame.getArtworkLabel().getText()).isNull();

        // 2. Track with null or empty artwork falls back to emoji text icon
        Track trackWithoutArt = Track.builder()
                .id("t-noart")
                .filePath("noart.mp3")
                .title("No Art Song")
                .artist("Artist")
                .album("Album")
                .artworkData(null)
                .build();

        miniPlayerFrame.updateTrackInfo(trackWithoutArt);
        assertThat(miniPlayerFrame.getArtworkLabel().getText()).isEqualTo("🎵");
        assertThat(miniPlayerFrame.getArtworkLabel().getIcon()).isNull();

        // 3. Track with null or blank album shows artist only without separator
        Track trackNoAlbum = Track.builder()
                .id("t-no-album")
                .filePath("noalbum.mp3")
                .title("Solo Song")
                .artist("Solo Artist")
                .album("")
                .build();

        miniPlayerFrame.updateTrackInfo(trackNoAlbum);
        assertThat(miniPlayerFrame.getArtistLabel().getText()).isEqualTo("Solo Artist");
    }

    @Test
    @DisplayName("Dynamic theme change cascades to MiniPlayerFrame component tree")
    void testThemeChangeListenerDynamicUpdate() {
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            ThemeManager.getInstance().applyTheme(AppTheme.LIGHT);
            ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        }).doesNotThrowAnyException();
    }

    private byte[] createValidPngImage(int width, int height) {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("MiniPlayer artwork icon updates dynamically when ArtworkCache is evicted and track is updated")
    void testArtworkUpdatesDynamicallyWhenArtworkCacheEvicted() {
        byte[] art1 = createValidPngImage(40, 40);
        byte[] art2 = createValidPngImage(80, 80);

        Track track = Track.builder()
                .id("mini-dyn-art")
                .filePath("tracks/dyn.mp3")
                .title("Dynamic Artwork Song")
                .artist("Artist")
                .artworkData(art1)
                .build();

        // 1. Initial display with art1
        miniPlayerFrame.updateTrackInfo(track);
        javax.swing.Icon icon1 = miniPlayerFrame.getArtworkLabel().getIcon();
        assertThat(icon1).isNotNull();

        // 2. Update artwork bytes, evict cache, and update display
        com.musiclibrary.service.cache.ArtworkCache.getInstance().evict(track.getId());
        Track updatedTrack = track.toBuilder().artworkData(art2).build();

        miniPlayerFrame.updateTrackInfo(updatedTrack);
        javax.swing.Icon icon2 = miniPlayerFrame.getArtworkLabel().getIcon();
        assertThat(icon2).isNotNull();
        assertThat(icon2).isNotSameAs(icon1);

        // 3. Null track clears icon
        miniPlayerFrame.updateTrackInfo(null);
        assertThat(miniPlayerFrame.getArtworkLabel().getIcon()).isNull();
        assertThat(miniPlayerFrame.getArtworkLabel().getText()).isEqualTo("🎵");
    }

    // --- Mock AudioPlayer Double ---

    private static class MockAudioPlayer implements AudioPlayer {
        private PlaybackState state = PlaybackState.STOPPED;
        private Track currentTrack = null;
        private float volume = 0.7f;
        private long currentPositionSeconds = 0;
        private long totalDurationSeconds = 100;
        private long lastSeekSeconds = -1;
        private final List<PlayerListener> listeners = new ArrayList<>();

        @Override public void play(Track track) { this.currentTrack = track; this.state = PlaybackState.PLAYING; }
        @Override public void pause() { this.state = PlaybackState.PAUSED; }
        @Override public void resume() { this.state = PlaybackState.PLAYING; }
        @Override public void stop() { this.state = PlaybackState.STOPPED; }
        @Override public void seekTo(long seconds) { this.lastSeekSeconds = seconds; this.currentPositionSeconds = seconds; }
        @Override public void setVolume(float volume) { this.volume = volume; }
        @Override public float getVolume() { return volume; }
        @Override public PlaybackState getState() { return state; }
        @Override public Track getCurrentTrack() { return currentTrack; }
        @Override public long getCurrentPositionSeconds() { return currentPositionSeconds; }
        @Override public long getTotalDurationSeconds() { return totalDurationSeconds; }
        @Override public boolean isPlaying() { return state == PlaybackState.PLAYING; }
        @Override public void addPlayerListener(PlayerListener listener) { listeners.add(listener); }
        @Override public void removePlayerListener(PlayerListener listener) { listeners.remove(listener); }
        @Override public void close() {}

        public long getLastSeekSeconds() { return lastSeekSeconds; }
    }
}
