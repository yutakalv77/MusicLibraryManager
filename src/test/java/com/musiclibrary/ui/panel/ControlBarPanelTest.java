package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.theme.AppTheme;
import com.musiclibrary.theme.ThemeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;

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
    @DisplayName("updateLanguage updates tooltips, placeholders, and labels in Japanese and English")
    void testUpdateLanguage() {
        ControlBarPanel panel = new ControlBarPanel();

        // Japanese update
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getPrevButton().getToolTipText()).isEqualTo("前の曲");
        assertThat(panel.getNextButton().getToolTipText()).isEqualTo("次の曲");
        assertThat(panel.getVolumeSlider().getToolTipText()).isEqualTo("音量");
        assertThat(panel.getSearchField().getClientProperty("JTextField.placeholderText"))
                .isEqualTo("タイトル、アーティスト、アルバム、ジャンルを検索...");

        // English update
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getPrevButton().getToolTipText()).isEqualTo("Previous Track");
        assertThat(panel.getNextButton().getToolTipText()).isEqualTo("Next Track");
        assertThat(panel.getVolumeSlider().getToolTipText()).isEqualTo("Volume");
        assertThat(panel.getSearchField().getClientProperty("JTextField.placeholderText"))
                .isEqualTo("Search by title, artist, album, genre...");
    }

    @Test
    @DisplayName("dispose cleans up without throwing exceptions")
    void testDispose() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.dispose();
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

    @Test
    @DisplayName("searchField is placed in the east panel with right-aligned layout and positioned on the right")
    void testSearchFieldRightAlignedPlacement() {
        ControlBarPanel panel = new ControlBarPanel();

        BorderLayout layout = (BorderLayout) panel.getLayout();
        Component eastComp = layout.getLayoutComponent(BorderLayout.EAST);
        assertThat(eastComp).isInstanceOf(JPanel.class);

        JPanel rightPanel = (JPanel) eastComp;
        assertThat(rightPanel.getLayout()).isInstanceOf(FlowLayout.class);
        FlowLayout flowLayout = (FlowLayout) rightPanel.getLayout();
        assertThat(flowLayout.getAlignment()).isEqualTo(FlowLayout.RIGHT);

        // Contains the search field and nothing else
        assertThat(rightPanel.getComponentCount()).isEqualTo(1);
        assertThat(rightPanel.getComponent(0)).isSameAs(panel.getSearchField());

        // Real layout test: set actual size and verify doLayout positions searchField to the right
        panel.setSize(1000, 60);
        panel.doLayout();
        rightPanel.doLayout();

        Point searchFieldPosInPanel = SwingUtilities.convertPoint(panel.getSearchField(), 0, 0, panel);
        assertThat(searchFieldPosInPanel.x).isGreaterThan(700);
        assertThat(panel.getSearchField().getWidth()).isGreaterThan(0);
        assertThat(panel.getSearchField().getHeight()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Mute button initializes with flat style, sound icon, correct tooltip, and is positioned left of volumeSlider in real layout")
    void testMuteButtonInitialization() {
        ControlBarPanel panel = new ControlBarPanel();
        JButton muteButton = panel.getMuteButton();
        javax.swing.JSlider volumeSlider = panel.getVolumeSlider();

        assertThat(muteButton).isNotNull();
        assertThat(muteButton.getClientProperty("JButton.buttonType")).isEqualTo("toolBarButton");
        assertThat(muteButton.getPreferredSize().width).isEqualTo(30);
        assertThat(muteButton.getPreferredSize().height).isEqualTo(28);
        assertThat(muteButton.getText()).isEqualTo("🔊");
        assertThat(muteButton.getToolTipText()).isEqualTo("Mute");
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(70);

        // Real UI layout invariant verification
        panel.setSize(1000, 60);
        panel.doLayout();

        BorderLayout layout = (BorderLayout) panel.getLayout();
        Component westComp = layout.getLayoutComponent(BorderLayout.WEST);
        assertThat(westComp).isInstanceOf(JPanel.class);
        JPanel transportPanel = (JPanel) westComp;
        transportPanel.doLayout();

        assertThat(muteButton.getParent()).isSameAs(transportPanel);
        assertThat(volumeSlider.getParent()).isSameAs(transportPanel);
        assertThat(muteButton.getWidth()).isGreaterThan(0);
        assertThat(volumeSlider.getWidth()).isGreaterThan(0);

        // Geometrical assertions: muteButton is placed to the left of volumeSlider
        assertThat(muteButton.getX()).isLessThan(volumeSlider.getX());

        Point mutePosInPanel = SwingUtilities.convertPoint(muteButton, 0, 0, panel);
        Point sliderPosInPanel = SwingUtilities.convertPoint(volumeSlider, 0, 0, panel);
        assertThat(mutePosInPanel.x).isLessThan(sliderPosInPanel.x);
    }

    @Test
    @DisplayName("Mute button toggles between mute (0) and restored previous volume in Japanese locale")
    void testMuteButtonToggleJapaneseLocale() {
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        ControlBarPanel panel = new ControlBarPanel();

        // 1. Initial state (🔊, ツールチップ: "ミュート", 音量70)
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(70);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート");

        // 2. Click -> mute (🔇, ツールチップ: "ミュート解除", 音量0)
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(0);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔇");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート解除");
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(70);

        // 3. Click again -> unmute (🔊, ツールチップ: "ミュート", 音量70復帰)
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(70);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート");

        // 4. Manual slider movement to 50 -> updates previousNonZeroVolume to 50 and stays unmuted
        panel.getVolumeSlider().setValue(50);
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(50);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート");

        // 5. Click mute -> volume 0, 🔇, ミュート解除
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(0);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔇");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート解除");

        // 6. Click unmute -> restores to 50
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(50);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート");
    }

    @Test
    @DisplayName("Mute button toggles between mute (0) and previous volume")
    void testMuteButtonToggle() {
        ControlBarPanel panel = new ControlBarPanel();

        // 1. Initial volume is 70; click mute -> volume 0, icon 🔇, tooltip Unmute
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(0);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔇");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Unmute");
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(70);

        // 2. Click again -> restores to 70, icon 🔊, tooltip Mute
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(70);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Mute");

        // 3. Change volume slider to 45 manually
        panel.getVolumeSlider().setValue(45);
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(45);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Mute");

        // 4. Click mute -> volume 0, icon 🔇
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(0);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔇");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Unmute");
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(45);

        // 5. Click unmute -> restores to 45
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(45);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
    }

    @Test
    @DisplayName("Moving volume slider to zero and non-zero updates mute button icon, tooltip, and retained volume")
    void testSliderDirectMovementUpdatesMuteState() {
        ControlBarPanel panel = new ControlBarPanel();
        panel.getVolumeSlider().setValue(80);
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(80);

        // Move slider to 0 directly
        panel.getVolumeSlider().setValue(0);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔇");
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Unmute");
        // previousNonZeroVolume should remain 80
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(80);

        // Clicking mute button should restore to 80
        panel.getMuteButton().doClick();
        assertThat(panel.getVolumeSlider().getValue()).isEqualTo(80);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");

        // Move slider from 80 to 25
        panel.getVolumeSlider().setValue(25);
        assertThat(panel.getPreviousNonZeroVolume()).isEqualTo(25);
        assertThat(panel.getMuteButton().getText()).isEqualTo("🔊");
    }

    @Test
    @DisplayName("Language switch updates mute button tooltip in both muted and unmuted states")
    void testMuteButtonLanguageSwitch() {
        ControlBarPanel panel = new ControlBarPanel();

        // 1. Unmuted (vol=70) English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Mute");

        // 2. Unmuted Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート");

        // 3. Muted (vol=0) Japanese
        panel.getVolumeSlider().setValue(0);
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("ミュート解除");

        // 4. Muted English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();
        assertThat(panel.getMuteButton().getToolTipText()).isEqualTo("Unmute");
    }

    @Test
    @DisplayName("Removed theme toggle button and mini player button are completely absent from ControlBarPanel")
    void testRemovedButtonsIntegrity() {
        ControlBarPanel panel = new ControlBarPanel();

        // 1. Reflection verification: ensure getter methods are completely removed
        assertThat(ControlBarPanel.class.getDeclaredMethods())
                .noneMatch(m -> m.getName().equalsIgnoreCase("getThemeToggleButton")
                        || m.getName().equalsIgnoreCase("getMiniPlayerButton")
                        || m.getName().equalsIgnoreCase("getThemeButton"));

        // 2. Reflection verification: ensure fields are completely removed
        assertThat(ControlBarPanel.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equalsIgnoreCase("themeToggleButton")
                        || f.getName().equalsIgnoreCase("miniPlayerButton")
                        || f.getName().equalsIgnoreCase("themeButton"));

        // 3. East (right) panel contains no buttons whatsoever
        BorderLayout layout = (BorderLayout) panel.getLayout();
        Component eastComp = layout.getLayoutComponent(BorderLayout.EAST);
        assertThat(eastComp).isInstanceOf(JPanel.class);
        JPanel rightPanel = (JPanel) eastComp;
        assertThat(rightPanel.getComponents()).noneMatch(c -> c instanceof JButton);

        // 4. Verify all JButtons across the entire ControlBarPanel are exclusively transport controls
        // (ignoring internal FlatLaf text field clear buttons)
        List<Component> allComponents = getAllComponents(panel);
        List<JButton> allButtons = allComponents.stream()
                .filter(JButton.class::isInstance)
                .map(JButton.class::cast)
                .filter(b -> !"TextField.clearButton".equals(b.getName()))
                .toList();

        assertThat(allButtons).containsExactlyInAnyOrder(
                panel.getPrevButton(),
                panel.getPlayPauseButton(),
                panel.getNextButton(),
                panel.getShuffleButton(),
                panel.getRepeatButton(),
                panel.getMuteButton()
        );
    }

    @Test
    @DisplayName("volumeSlider preferred size is expanded by 20% to 102x24 and verified in real UI layout")
    void testVolumeSliderPreferredSizeExpansion() {
        ControlBarPanel panel = new ControlBarPanel();
        JSlider volumeSlider = panel.getVolumeSlider();

        // Preferred size invariant
        assertThat(volumeSlider.getPreferredSize()).isEqualTo(new Dimension(102, 24));

        // Real UI layout invariant: size and layout within panel hierarchy
        panel.setSize(1000, 60);
        panel.doLayout();
        BorderLayout bl = (BorderLayout) panel.getLayout();
        JPanel transportPanel = (JPanel) bl.getLayoutComponent(BorderLayout.WEST);
        transportPanel.doLayout();

        assertThat(volumeSlider.getWidth()).isEqualTo(102);
        assertThat(volumeSlider.getHeight()).isEqualTo(24);
    }

    @Test
    @DisplayName("volumeSlider returns localized tooltip when idle and real-time numeric value (0-100) during drag in both EN and JA")
    void testVolumeSliderTooltipDuringAdjustment() {
        ControlBarPanel panel = new ControlBarPanel();
        JSlider volumeSlider = panel.getVolumeSlider();
        MouseEvent dummyEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, 50, 10, 0, false);

        // 1. Idle state (getValueIsAdjusting = false) in English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();
        assertThat(volumeSlider.getToolTipText()).isEqualTo("Volume");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("Volume");

        // 2. Dragging state (getValueIsAdjusting = true) in English
        volumeSlider.setValueIsAdjusting(true);

        volumeSlider.setValue(70);
        assertThat(volumeSlider.getToolTipText()).isEqualTo("70");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("70");

        volumeSlider.setValue(0);
        assertThat(volumeSlider.getToolTipText()).isEqualTo("0");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("0");

        volumeSlider.setValue(100);
        assertThat(volumeSlider.getToolTipText()).isEqualTo("100");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("100");

        volumeSlider.setValue(45);
        assertThat(volumeSlider.getToolTipText()).isEqualTo("45");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("45");

        // 3. Released state in Japanese
        volumeSlider.setValueIsAdjusting(false);
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(volumeSlider.getToolTipText()).isEqualTo("音量");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("音量");

        // 4. Dragging state in Japanese still returns numeric volume value (0-100)
        volumeSlider.setValueIsAdjusting(true);
        volumeSlider.setValue(85);
        assertThat(volumeSlider.getToolTipText()).isEqualTo("85");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("85");

        // 5. Release back to Japanese
        volumeSlider.setValueIsAdjusting(false);
        assertThat(volumeSlider.getToolTipText()).isEqualTo("音量");
        assertThat(volumeSlider.getToolTipText(dummyEvent)).isEqualTo("音量");
    }

    @Test
    @DisplayName("Volume slider drag mouse and lifecycle events operate safely without throwing in headless/hidden states")
    void testVolumeSliderMouseEventsSafelyHandled() {
        ControlBarPanel panel = new ControlBarPanel();
        JSlider volumeSlider = panel.getVolumeSlider();

        MouseEvent rightClickPress = new MouseEvent(volumeSlider, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON3_DOWN_MASK, 50, 12, 1, false, MouseEvent.BUTTON3);
        MouseEvent pressEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, 50, 12, 1, false, MouseEvent.BUTTON1);
        MouseEvent dragEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, 60, 12, 0, false, MouseEvent.BUTTON1);
        MouseEvent releaseEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, 60, 12, 1, false, MouseEvent.BUTTON1);
        FocusEvent focusLostEvent = new FocusEvent(volumeSlider, FocusEvent.FOCUS_LOST);
        ComponentEvent hiddenEvent = new ComponentEvent(volumeSlider, ComponentEvent.COMPONENT_HIDDEN);

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            // Right-click press is ignored safely
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mousePressed(rightClickPress);
            }
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mousePressed(pressEvent);
            }
            for (java.awt.event.MouseMotionListener mml : volumeSlider.getMouseMotionListeners()) {
                mml.mouseDragged(dragEvent);
            }
            for (java.awt.event.FocusListener fl : volumeSlider.getFocusListeners()) {
                fl.focusLost(focusLostEvent);
            }
            for (java.awt.event.ComponentListener cl : volumeSlider.getComponentListeners()) {
                cl.componentHidden(hiddenEvent);
            }
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mouseReleased(releaseEvent);
            }
            panel.dispose();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Volume slider drag popup lifecycle: show, update on drag/change, and cleanup on release, focusLost, hidden, and dispose")
    void testVolumeSliderDragPopupLifecycle() throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }

        javax.swing.JFrame testFrame = new javax.swing.JFrame("Volume Drag Test");
        ControlBarPanel panel = new ControlBarPanel();
        testFrame.add(panel);
        testFrame.pack();
        testFrame.setVisible(true);

        try {
            JSlider volumeSlider = panel.getVolumeSlider();
            assertThat(volumeSlider.isShowing()).isTrue();

            java.lang.reflect.Field popupField = ControlBarPanel.class.getDeclaredField("dragPopup");
            popupField.setAccessible(true);
            java.lang.reflect.Field tooltipField = ControlBarPanel.class.getDeclaredField("dragToolTip");
            tooltipField.setAccessible(true);

            // Initially popup is null
            assertThat(popupField.get(panel)).isNull();

            // 1. Mouse Pressed with Left Button -> popup shown
            MouseEvent pressEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    MouseEvent.BUTTON1_DOWN_MASK, 50, 12, 1, false, MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mousePressed(pressEvent);
            }
            assertThat(popupField.get(panel)).isNotNull();
            javax.swing.JToolTip tip = (javax.swing.JToolTip) tooltipField.get(panel);
            assertThat(tip).isNotNull();
            assertThat(tip.getTipText()).isEqualTo(String.valueOf(volumeSlider.getValue()));

            // 2. Mouse Dragged with new value -> tooltip updated to slider's new value
            volumeSlider.setValueIsAdjusting(true);
            MouseEvent dragEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                    MouseEvent.BUTTON1_DOWN_MASK, 30, 12, 0, false, MouseEvent.BUTTON1);
            for (java.awt.event.MouseMotionListener mml : volumeSlider.getMouseMotionListeners()) {
                mml.mouseDragged(dragEvent);
            }
            assertThat(popupField.get(panel)).isNotNull();
            assertThat(((javax.swing.JToolTip) tooltipField.get(panel)).getTipText())
                    .isEqualTo(String.valueOf(volumeSlider.getValue()));

            // 3. Mouse Released -> popup dismissed
            MouseEvent releaseEvent = new MouseEvent(volumeSlider, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                    0, 30, 12, 1, false, MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mouseReleased(releaseEvent);
            }
            assertThat(popupField.get(panel)).isNull();

            // 4. Mouse Pressed again, then focus lost -> popup dismissed
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mousePressed(pressEvent);
            }
            assertThat(popupField.get(panel)).isNotNull();

            FocusEvent focusLost = new FocusEvent(volumeSlider, FocusEvent.FOCUS_LOST);
            for (java.awt.event.FocusListener fl : volumeSlider.getFocusListeners()) {
                fl.focusLost(focusLost);
            }
            assertThat(popupField.get(panel)).isNull();

            // 5. Mouse Pressed again, then component hidden -> popup dismissed
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mousePressed(pressEvent);
            }
            assertThat(popupField.get(panel)).isNotNull();

            ComponentEvent hideEvent = new ComponentEvent(volumeSlider, ComponentEvent.COMPONENT_HIDDEN);
            for (java.awt.event.ComponentListener cl : volumeSlider.getComponentListeners()) {
                cl.componentHidden(hideEvent);
            }
            assertThat(popupField.get(panel)).isNull();

            // 6. Mouse Pressed again, then dispose() -> popup dismissed
            for (java.awt.event.MouseListener ml : volumeSlider.getMouseListeners()) {
                ml.mousePressed(pressEvent);
            }
            assertThat(popupField.get(panel)).isNotNull();

            panel.dispose();
            assertThat(popupField.get(panel)).isNull();

        } finally {
            testFrame.dispose();
        }
    }

    private List<Component> getAllComponents(Container container) {
        List<Component> list = new ArrayList<>();
        for (Component c : container.getComponents()) {
            list.add(c);
            if (c instanceof Container childContainer) {
                list.addAll(getAllComponents(childContainer));
            }
        }
        return list;
    }
}
