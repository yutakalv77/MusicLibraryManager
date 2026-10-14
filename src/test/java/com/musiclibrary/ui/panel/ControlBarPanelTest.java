package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;

class ControlBarPanelTest {

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
    }

    @Test
    @DisplayName("ControlBarPanel initializes all controls and LCD components with expected default properties")
    void testInitialization() {
        ControlBarPanel panel = new ControlBarPanel();

        assertThat(panel.getPrevButton()).isNotNull();
        assertThat(panel.getPlayPauseButton()).isNotNull();
        assertThat(panel.getNextButton()).isNotNull();

        assertThat(panel.getVolumeSlider()).isNotNull();
        assertThat(panel.getVolumeSlider().getMinimum()).isEqualTo(0);
        assertThat(panel.getVolumeSlider().getMaximum()).isEqualTo(100);
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(70);

        assertThat(panel.getSeekSlider()).isNotNull();
        assertThat(panel.getSeekSlider().getMinimum()).isEqualTo(0);
        assertThat(panel.getSeekSlider().getMaximum()).isEqualTo(1000);
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(0);

        assertThat(panel.getSearchField()).isNotNull();
        assertThat(panel.getSearchField().getClientProperty("JTextField.showClearButton")).isEqualTo(true);

        assertThat(panel.getThemeToggleButton()).isNotNull();
        assertThat(panel.getMiniPlayerButton()).isNotNull();
    }

    @Test
    @DisplayName("updateTrackInfo displays metadata and fallback labels safely")
    void testUpdateTrackInfo() throws IOException {
        ControlBarPanel panel = new ControlBarPanel();

        // 1. With title and artist
        panel.updateTrackInfo("Bohemian Rhapsody", "Queen", "A Night at the Opera", null);

        // 2. With artwork data
        BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] artworkBytes = baos.toByteArray();

        panel.updateTrackInfo("Starman", "David Bowie", "Ziggy Stardust", artworkBytes);

        // 3. Fallback when title is null or blank
        panel.updateTrackInfo(null, null, null, null);
        panel.updateTrackInfo("   ", "Artist", "Album", null);
    }

    @Test
    @DisplayName("updateProgress formats timing and sets seek slider ratio accurately")
    void testUpdateProgress() {
        ControlBarPanel panel = new ControlBarPanel();

        // 50% progress (60s out of 120s)
        panel.updateProgress(60, 120);
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(500);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("01:00");
        assertThat(panel.getTotalTimeLabel().getText()).isEqualTo("02:00");

        // 0s total duration
        panel.updateProgress(0, 0);
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(0);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("00:00");
        assertThat(panel.getTotalTimeLabel().getText()).isEqualTo("00:00");

        // Long duration (over an hour)
        panel.updateProgress(3665, 7200);
        assertThat(panel.getSeekSlider().getValue()).isGreaterThan(500);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("01:01:05");
        assertThat(panel.getTotalTimeLabel().getText()).isEqualTo("02:00:00");

        // Negative duration safety
        panel.updateProgress(-10, -50);
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("Seek slider fires seekListener callback when user commits seek")
    void testSeekListenerCallback() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.updateProgress(10, 200); // 200s total duration
        assertThat(panel.getCurrentTotalSeconds()).isEqualTo(200);

        java.util.concurrent.atomic.AtomicLong targetSecondsReceived = new java.util.concurrent.atomic.AtomicLong(-1);
        panel.setSeekListener(targetSecondsReceived::set);
        assertThat(panel.getSeekListener()).isNotNull();

        // User sets slider to 500 (50% of 200s = 100s)
        panel.getSeekSlider().setValue(500);
        assertThat(targetSecondsReceived.get()).isEqualTo(100);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("01:40");
    }

    @Test
    @DisplayName("Dragging slider updates preview time and prevents background progress overwrite")
    void testScrubbingGuardsBackgroundUpdate() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.updateProgress(20, 200);

        // Simulate user dragging (valueIsAdjusting = true)
        panel.getSeekSlider().setValueIsAdjusting(true);
        panel.getSeekSlider().setValue(750); // 75% = 150s

        // Time label previews dragged position immediately (150s = 02:30)
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("02:30");

        // Background playback progress update arrives while user is still scrubbing
        panel.updateProgress(25, 200);

        // Slider value and current time must NOT be overwritten while adjusting
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(750);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("02:30");

        // Release slider
        panel.getSeekSlider().setValueIsAdjusting(false);
    }

    @Test
    @DisplayName("updatePlaybackState updates play/pause button icon and tooltip across states")
    void testPlaybackState() {
        ControlBarPanel panel = new ControlBarPanel();

        // PLAYING -> Pause icon
        panel.updatePlaybackState(PlaybackState.PLAYING);
        assertThat(panel.getPlayPauseButton().getText()).isEqualTo("⏸");
        assertThat(panel.getPlayPauseButton().getToolTipText()).isEqualTo("Pause");

        // PAUSED -> Play icon
        panel.updatePlaybackState(PlaybackState.PAUSED);
        assertThat(panel.getPlayPauseButton().getText()).isEqualTo("▶");
        assertThat(panel.getPlayPauseButton().getToolTipText()).isEqualTo("Play");

        // STOPPED -> Play icon
        panel.updatePlaybackState(PlaybackState.STOPPED);
        assertThat(panel.getPlayPauseButton().getText()).isEqualTo("▶");
        assertThat(panel.getPlayPauseButton().getToolTipText()).isEqualTo("Play");

        // null state falls back to STOPPED
        panel.updatePlaybackState(null);
        assertThat(panel.getPlayPauseButton().getText()).isEqualTo("▶");
    }

    @Test
    @DisplayName("Theme toggle button updates icon based on current theme")
    void testThemeToggleIntegration() {
        ControlBarPanel panel = new ControlBarPanel();

        ThemeManager.getInstance().applyTheme(AppTheme.DARK);
        assertThat(panel.getThemeToggleButton().getText()).isEqualTo("☀️");

        ThemeManager.getInstance().applyTheme(AppTheme.LIGHT);
        assertThat(panel.getThemeToggleButton().getText()).isEqualTo("🌙");
    }

    @Test
    @DisplayName("updateLanguage updates tooltips, placeholders, and labels in Japanese and English")
    void testUpdateLanguage() {
        ControlBarPanel panel = new ControlBarPanel();

        // Japanese update
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getPrevButton().getToolTipText()).isEqualTo("前の曲");
        assertThat(panel.getNextButton().getToolTipText()).isEqualTo("次の曲");
        assertThat(panel.getVolumeSlider().getToolTipText()).isEqualTo("音量");
        assertThat(panel.getThemeToggleButton().getToolTipText()).isEqualTo("テーマ");
        assertThat(panel.getMiniPlayerButton().getToolTipText()).isEqualTo("ミニプレーヤー");
        assertThat(panel.getSearchField().getClientProperty("JTextField.placeholderText"))
                .isEqualTo("タイトル、アーティスト、アルバム、ジャンルを検索...");

        // English update
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getPrevButton().getToolTipText()).isEqualTo("Previous Track");
        assertThat(panel.getNextButton().getToolTipText()).isEqualTo("Next Track");
        assertThat(panel.getVolumeSlider().getToolTipText()).isEqualTo("Volume");
        assertThat(panel.getThemeToggleButton().getToolTipText()).isEqualTo("Theme");
        assertThat(panel.getSearchField().getClientProperty("JTextField.placeholderText"))
                .isEqualTo("Search by title, artist, album, genre...");
    }

    @Test
    @DisplayName("dispose unregisters ThemeChangeListener to prevent memory leaks")
    void testDispose() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.dispose();

        // Toggling theme after dispose should not throw
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            ThemeManager.getInstance().toggleTheme();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Seek slider operates safely without throwing when seekListener is null")
    void testSeekWithoutListenerThrowsNoException() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.updateProgress(30, 100);
        panel.setSeekListener(null);
        assertThat(panel.getSeekListener()).isNull();

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            panel.getSeekSlider().setValueIsAdjusting(true);
            panel.getSeekSlider().setValue(600);
            panel.getSeekSlider().setValueIsAdjusting(false);
        }).doesNotThrowAnyException();

        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("01:00");
    }

    @Test
    @DisplayName("Seek slider interaction when total duration is zero is safe and does not trigger seek callback")
    void testSeekWithZeroTotalDuration() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.updateProgress(0, 0);
        assertThat(panel.getCurrentTotalSeconds()).isEqualTo(0);

        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean(false);
        panel.setSeekListener(s -> called.set(true));

        panel.getSeekSlider().setValue(500);
        assertThat(called.get()).isFalse();
    }

    @Test
    @DisplayName("Slider adjustment triggers seekListener exactly once when adjusting completes (mouse released)")
    void testSeekListenerFiresOnlyOnAdjustmentComplete() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.updateProgress(0, 300); // 5 minutes total

        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicLong seekTarget = new java.util.concurrent.atomic.AtomicLong(-1);
        panel.setSeekListener(s -> {
            callCount.incrementAndGet();
            seekTarget.set(s);
        });

        // 1. User starts dragging slider (valueIsAdjusting = true)
        panel.getSeekSlider().setValueIsAdjusting(true);
        panel.getSeekSlider().setValue(200); // 20%
        panel.getSeekSlider().setValue(400); // 40%
        panel.getSeekSlider().setValue(600); // 60% = 180s

        // Listener must NOT be called while dragging
        assertThat(callCount.get()).isEqualTo(0);
        // Current time label tracks intermediate preview positions
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("03:00");

        // 2. User releases mouse (valueIsAdjusting = false)
        panel.getSeekSlider().setValueIsAdjusting(false);

        // Listener called exactly once with final commit position
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(seekTarget.get()).isEqualTo(180);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("03:00");
    }

    @Test
    @DisplayName("updateTrackInfo resets LCD display and slider to zero when null title is passed")
    void testUpdateTrackInfoResetsProgress() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.updateProgress(50, 100);
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(500);
        assertThat(panel.getCurrentTotalSeconds()).isEqualTo(100);

        // Reset track
        panel.updateTrackInfo(null, null, null, null);

        assertThat(panel.getCurrentTotalSeconds()).isEqualTo(0);
        assertThat(panel.getSeekSlider().getValue()).isEqualTo(0);
        assertThat(panel.getCurrentTimeLabel().getText()).isEqualTo("00:00");
        assertThat(panel.getTotalTimeLabel().getText()).isEqualTo("00:00");
    }

    @Test
    @DisplayName("Shuffle button triggers listener and updates visual appearance")
    void testShuffleButtonInteractionAndVisualState() {
        ControlBarPanel panel = new ControlBarPanel();
        java.util.concurrent.atomic.AtomicBoolean shuffleClicked = new java.util.concurrent.atomic.AtomicBoolean(false);
        panel.setShuffleToggleListener(() -> shuffleClicked.set(true));

        panel.getShuffleButton().doClick();
        assertThat(shuffleClicked.get()).isTrue();

        // Visual state updates
        panel.updateShuffleState(true);
        assertThat(panel.getShuffleButton().getClientProperty("JButton.buttonType")).isEqualTo("roundRect");

        panel.updateShuffleState(false);
        assertThat(panel.getShuffleButton().getClientProperty("JButton.buttonType")).isNull();
    }

    @Test
    @DisplayName("Repeat button triggers cycle listener and updates icons, visual state, and localized tooltips")
    void testRepeatButtonInteractionAndVisualState() {
        ControlBarPanel panel = new ControlBarPanel();
        java.util.concurrent.atomic.AtomicBoolean repeatClicked = new java.util.concurrent.atomic.AtomicBoolean(false);
        panel.setRepeatCycleListener(() -> repeatClicked.set(true));

        panel.getRepeatButton().doClick();
        assertThat(repeatClicked.get()).isTrue();

        // 1. RepeatMode.OFF
        panel.updateRepeatMode(com.musiclibrary.model.RepeatMode.OFF);
        assertThat(panel.getRepeatButton().getText()).isEqualTo("🔁");
        assertThat(panel.getRepeatButton().getClientProperty("JButton.buttonType")).isNull();
        assertThat(panel.getRepeatButton().getToolTipText()).isEqualTo("Repeat Off");

        // 2. RepeatMode.ALL
        panel.updateRepeatMode(com.musiclibrary.model.RepeatMode.ALL);
        assertThat(panel.getRepeatButton().getText()).isEqualTo("🔁");
        assertThat(panel.getRepeatButton().getClientProperty("JButton.buttonType")).isEqualTo("roundRect");
        assertThat(panel.getRepeatButton().getToolTipText()).isEqualTo("Repeat All");

        // 3. RepeatMode.ONE
        panel.updateRepeatMode(com.musiclibrary.model.RepeatMode.ONE);
        assertThat(panel.getRepeatButton().getText()).isEqualTo("🔂");
        assertThat(panel.getRepeatButton().getClientProperty("JButton.buttonType")).isEqualTo("roundRect");
        assertThat(panel.getRepeatButton().getToolTipText()).isEqualTo("Repeat One");

        // Japanese tooltips
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getRepeatButton().getToolTipText()).isEqualTo("1曲リピート");
        assertThat(panel.getShuffleButton().getToolTipText()).isEqualTo("シャッフル");

        panel.updateRepeatMode(com.musiclibrary.model.RepeatMode.ALL);
        assertThat(panel.getRepeatButton().getToolTipText()).isEqualTo("全曲リピート");

        panel.updateRepeatMode(com.musiclibrary.model.RepeatMode.OFF);
        assertThat(panel.getRepeatButton().getToolTipText()).isEqualTo("リピートオフ");
    }

    @Test
    @DisplayName("updateTrackInfo uses ArtworkCache to render artwork icon and clears it on empty artwork")
    void testArtworkIconUpdatesViaArtworkCache() throws IOException {
        ControlBarPanel panel = new ControlBarPanel();

        // 1. Initially placeholder "🎵" and no icon
        assertThat(panel.getArtworkLabel().getIcon()).isNull();
        assertThat(panel.getArtworkLabel().getText()).isEqualTo("🎵");

        // 2. Track with artwork
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] artworkBytes = baos.toByteArray();

        com.musiclibrary.model.Track track = com.musiclibrary.model.Track.builder()
                .id("cb-track-1")
                .filePath("tracks/cb1.mp3")
                .title("Artwork Song")
                .artist("Artist")
                .artworkData(artworkBytes)
                .build();

        panel.updateTrackInfo(track);

        // ArtworkCache returns image and icon is populated
        assertThat(panel.getArtworkLabel().getIcon()).isNotNull();
        assertThat(panel.getArtworkLabel().getText()).isNull();

        // 3. Update to track without artwork -> icon cleared and "🎵" restored
        com.musiclibrary.model.Track noArtTrack = com.musiclibrary.model.Track.builder()
                .id("cb-track-2")
                .filePath("tracks/cb2.mp3")
                .title("No Art Song")
                .build();

        panel.updateTrackInfo(noArtTrack);
        assertThat(panel.getArtworkLabel().getIcon()).isNull();
        assertThat(panel.getArtworkLabel().getText()).isEqualTo("🎵");

        // 4. Update with null track -> icon cleared and "🎵" restored
        panel.updateTrackInfo((com.musiclibrary.model.Track) null);
        assertThat(panel.getArtworkLabel().getIcon()).isNull();
        assertThat(panel.getArtworkLabel().getText()).isEqualTo("🎵");
    }
}
