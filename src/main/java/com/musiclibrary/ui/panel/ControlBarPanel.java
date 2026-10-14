package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.RepeatMode;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.theme.ThemeChangeListener;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.util.TimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.util.function.LongConsumer;

/**
 * Top control bar panel containing playback transport controls,
 * volume slider, LCD-style track display, search field, and theme toggle.
 */
public class ControlBarPanel extends JPanel {

    // Transport buttons
    private final JButton prevButton;
    private final JButton playPauseButton;
    private final JButton nextButton;
    private final JButton shuffleButton;
    private final JButton repeatButton;
    private final JSlider volumeSlider;

    // LCD display panel components
    private final JPanel lcdPanel;
    private final JLabel artworkLabel;
    private final JLabel trackTitleLabel;
    private final JLabel currentTimeLabel;
    private final JSlider seekSlider;
    private final JLabel totalTimeLabel;

    // Search and utility components
    private final JTextField searchField;
    private final JButton themeToggleButton;
    private final JButton miniPlayerButton;

    private PlaybackState currentPlaybackState = PlaybackState.STOPPED;
    private final ThemeChangeListener themeChangeListener;

    private volatile long currentTotalSeconds = 0;
    private boolean isUpdatingProgress = false;
    private LongConsumer seekListener = null;

    private boolean isShuffle = false;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private Runnable shuffleToggleListener = null;
    private Runnable repeatCycleListener = null;

    public ControlBarPanel() {
        setLayout(new BorderLayout(15, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        // 1. Left: Transport Controls
        JPanel transportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

        prevButton = new JButton("⏮");
        playPauseButton = new JButton("▶");
        nextButton = new JButton("⏭");
        shuffleButton = new JButton("🔀");
        repeatButton = new JButton("🔁");

        Dimension btnSize = new Dimension(44, 34);
        prevButton.setPreferredSize(btnSize);
        playPauseButton.setPreferredSize(new Dimension(50, 34));
        nextButton.setPreferredSize(btnSize);
        shuffleButton.setPreferredSize(new Dimension(42, 34));
        repeatButton.setPreferredSize(new Dimension(42, 34));

        shuffleButton.addActionListener(e -> {
            if (shuffleToggleListener != null) {
                shuffleToggleListener.run();
            }
        });

        repeatButton.addActionListener(e -> {
            if (repeatCycleListener != null) {
                repeatCycleListener.run();
            }
        });

        // Volume slider
        volumeSlider = new JSlider(0, 100, 70);
        volumeSlider.setPreferredSize(new Dimension(85, 24));

        transportPanel.add(prevButton);
        transportPanel.add(playPauseButton);
        transportPanel.add(nextButton);
        transportPanel.add(Box.createHorizontalStrut(2));
        transportPanel.add(shuffleButton);
        transportPanel.add(repeatButton);
        transportPanel.add(Box.createHorizontalStrut(4));
        transportPanel.add(volumeSlider);

        add(transportPanel, BorderLayout.WEST);

        // 2. Center: LCD Panel
        lcdPanel = new JPanel(new BorderLayout(8, 4));
        lcdPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
        lcdPanel.setPreferredSize(new Dimension(380, 56));

        artworkLabel = new JLabel("🎵");
        artworkLabel.setPreferredSize(new Dimension(42, 42));
        artworkLabel.setHorizontalAlignment(SwingConstants.CENTER);
        artworkLabel.setBorder(BorderFactory.createEtchedBorder());
        lcdPanel.add(artworkLabel, BorderLayout.WEST);

        JPanel trackInfoPanel = new JPanel();
        trackInfoPanel.setLayout(new BoxLayout(trackInfoPanel, BoxLayout.Y_AXIS));

        trackTitleLabel = new JLabel();
        Font currentFont = trackTitleLabel.getFont();
        if (currentFont != null) {
            trackTitleLabel.setFont(currentFont.deriveFont(Font.BOLD, 12.5f));
        } else {
            trackTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        }
        trackTitleLabel.setAlignmentX(LEFT_ALIGNMENT);

        JPanel progressPanel = new JPanel(new BorderLayout(6, 0));
        currentTimeLabel = new JLabel("00:00");
        currentTimeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        seekSlider = new JSlider(0, 1000, 0);
        seekSlider.setPreferredSize(new Dimension(180, 18));
        seekSlider.addChangeListener(e -> {
            if (isUpdatingProgress) {
                return;
            }
            if (seekSlider.getValueIsAdjusting()) {
                if (currentTotalSeconds > 0) {
                    long previewSec = (seekSlider.getValue() * currentTotalSeconds) / 1000L;
                    currentTimeLabel.setText(TimeFormatter.format(previewSec));
                }
            } else {
                if (currentTotalSeconds > 0) {
                    long targetSec = (seekSlider.getValue() * currentTotalSeconds) / 1000L;
                    currentTimeLabel.setText(TimeFormatter.format(targetSec));
                    if (seekListener != null) {
                        seekListener.accept(targetSec);
                    }
                }
            }
        });

        totalTimeLabel = new JLabel("00:00");
        totalTimeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));

        progressPanel.add(currentTimeLabel, BorderLayout.WEST);
        progressPanel.add(seekSlider, BorderLayout.CENTER);
        progressPanel.add(totalTimeLabel, BorderLayout.EAST);
        progressPanel.setAlignmentX(LEFT_ALIGNMENT);

        trackInfoPanel.add(trackTitleLabel);
        trackInfoPanel.add(Box.createVerticalStrut(2));
        trackInfoPanel.add(progressPanel);

        lcdPanel.add(trackInfoPanel, BorderLayout.CENTER);
        add(lcdPanel, BorderLayout.CENTER);

        // 3. Right: Search & Actions Panel
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        searchField = new JTextField(15);
        searchField.setPreferredSize(new Dimension(180, 30));
        // FlatLaf placeholder and clear button properties
        searchField.putClientProperty("JTextField.showClearButton", true);

        themeToggleButton = new JButton();
        themeToggleButton.setPreferredSize(new Dimension(36, 30));
        themeToggleButton.addActionListener(e -> ThemeManager.getInstance().toggleTheme());

        miniPlayerButton = new JButton("⤢");
        miniPlayerButton.setPreferredSize(new Dimension(36, 30));

        rightPanel.add(searchField);
        rightPanel.add(themeToggleButton);
        rightPanel.add(miniPlayerButton);

        add(rightPanel, BorderLayout.EAST);

        updateLanguage();
        updateThemeIcon();

        // Listen for theme changes to update theme toggle button icon
        themeChangeListener = theme -> updateThemeIcon();
        ThemeManager.getInstance().addThemeChangeListener(themeChangeListener);
    }

    /**
     * Releases listeners to prevent memory leaks when disposing the panel.
     */
    public void dispose() {
        if (themeChangeListener != null) {
            ThemeManager.getInstance().removeThemeChangeListener(themeChangeListener);
        }
    }

    /**
     * Updates all localized texts, tooltips, and placeholders.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();

        prevButton.setToolTipText(i18n.get("control.previous"));
        nextButton.setToolTipText(i18n.get("control.next"));
        updatePlayPauseButtonText();
        shuffleButton.setToolTipText(i18n.get("control.shuffle"));
        repeatButton.setToolTipText(getRepeatToolTipText(repeatMode, i18n));
        volumeSlider.setToolTipText(i18n.get("control.volume"));

        if (trackTitleLabel.getText() == null || trackTitleLabel.getText().isBlank()
                || trackTitleLabel.getText().equals(i18n.get("control.no_track"))) {
            trackTitleLabel.setText(i18n.get("control.no_track"));
        }

        searchField.putClientProperty("JTextField.placeholderText", i18n.get("control.search_placeholder"));
        searchField.setToolTipText(i18n.get("control.search_placeholder"));

        themeToggleButton.setToolTipText(i18n.get("menu.view.theme"));
        miniPlayerButton.setToolTipText(i18n.get("menu.view.mini_player"));

        revalidate();
        repaint();
    }

    private void updateThemeIcon() {
        boolean dark = ThemeManager.getInstance().isDarkMode();
        themeToggleButton.setText(dark ? "☀️" : "🌙");
    }

    private void updatePlayPauseButtonText() {
        I18nManager i18n = I18nManager.getInstance();
        if (currentPlaybackState == PlaybackState.PLAYING) {
            playPauseButton.setText("⏸");
            playPauseButton.setToolTipText(i18n.get("control.pause"));
        } else {
            playPauseButton.setText("▶");
            playPauseButton.setToolTipText(i18n.get("control.play"));
        }
    }

    /**
     * Updates track information displayed in the LCD panel.
     *
     * @param title       track title
     * @param artist      track artist
     * @param album       track album
     * @param artworkData embedded artwork binary
     */
    /**
     * Updates track information displayed in the LCD panel using the given Track entity.
     *
     * @param track active playing track (or null if stopped)
     */
    public void updateTrackInfo(Track track) {
        if (track == null) {
            updateTrackInfo(null, null, null, null, null);
            return;
        }
        updateTrackInfo(track.getId(), track.getTitle(), track.getArtist(), track.getAlbum(), track.getArtworkData());
    }

    /**
     * Updates track information displayed in the LCD panel.
     *
     * @param title       track title
     * @param artist      track artist
     * @param album       track album
     * @param artworkData embedded artwork binary
     */
    public void updateTrackInfo(String title, String artist, String album, byte[] artworkData) {
        updateTrackInfo(null, title, artist, album, artworkData);
    }

    /**
     * Updates track information displayed in the LCD panel with track identifier for cache lookup.
     *
     * @param trackId     track unique ID (or null)
     * @param title       track title
     * @param artist      track artist
     * @param album       track album
     * @param artworkData embedded artwork binary
     */
    public void updateTrackInfo(String trackId, String title, String artist, String album, byte[] artworkData) {
        if (title == null || title.isBlank()) {
            trackTitleLabel.setText(I18nManager.getInstance().get("control.no_track"));
            artworkLabel.setIcon(null);
            artworkLabel.setText("🎵");
            this.currentTotalSeconds = 0;
            currentTimeLabel.setText(TimeFormatter.format(0));
            totalTimeLabel.setText(TimeFormatter.format(0));
            isUpdatingProgress = true;
            try {
                seekSlider.setValue(0);
            } finally {
                isUpdatingProgress = false;
            }
            return;
        }

        String displayText = title;
        if (artist != null && !artist.isBlank()) {
            displayText += " — " + artist;
        }
        trackTitleLabel.setText(displayText);

        if (artworkData != null && artworkData.length > 0) {
            try {
                Image scaled = ArtworkCache.getInstance().getImage(trackId, artworkData, 38, 38);
                artworkLabel.setText(null);
                artworkLabel.setIcon(new ImageIcon(scaled));
            } catch (Exception e) {
                artworkLabel.setIcon(null);
                artworkLabel.setText("🎵");
            }
        } else {
            artworkLabel.setIcon(null);
            artworkLabel.setText("🎵");
        }
    }

    /**
     * Updates playback progress and timing labels in the LCD panel.
     * Prevents overwriting slider and time display while the user is actively dragging.
     *
     * @param currentSeconds current playback position in seconds
     * @param totalSeconds   total track length in seconds
     */
    public void updateProgress(long currentSeconds, long totalSeconds) {
        this.currentTotalSeconds = totalSeconds;

        if (seekSlider.getValueIsAdjusting()) {
            return;
        }

        currentTimeLabel.setText(TimeFormatter.format(currentSeconds));
        totalTimeLabel.setText(TimeFormatter.format(totalSeconds));

        isUpdatingProgress = true;
        try {
            if (totalSeconds > 0) {
                int progress = (int) Math.min(1000, (currentSeconds * 1000) / totalSeconds);
                seekSlider.setValue(progress);
            } else {
                seekSlider.setValue(0);
            }
        } finally {
            isUpdatingProgress = false;
        }
    }

    /**
     * Updates the playback state (PLAYING, PAUSED, STOPPED).
     *
     * @param state new state
     */
    public void updatePlaybackState(PlaybackState state) {
        this.currentPlaybackState = state != null ? state : PlaybackState.STOPPED;
        updatePlayPauseButtonText();
    }

    /**
     * Registers a listener to be notified when the user commits a seek operation.
     *
     * @param seekListener callback receiving target seconds
     */
    public void setSeekListener(LongConsumer seekListener) {
        this.seekListener = seekListener;
    }

    /**
     * Returns current seek listener callback.
     *
     * @return seek listener
     */
    public LongConsumer getSeekListener() {
        return seekListener;
    }

    /**
     * Returns current total seconds of the active track.
     *
     * @return total seconds
     */
    public long getCurrentTotalSeconds() {
        return currentTotalSeconds;
    }

    /**
     * Updates shuffle button visual state.
     *
     * @param shuffle true if shuffle is enabled
     */
    public void updateShuffleState(boolean shuffle) {
        this.isShuffle = shuffle;
        if (shuffle) {
            shuffleButton.putClientProperty("JButton.buttonType", "roundRect");
        } else {
            shuffleButton.putClientProperty("JButton.buttonType", null);
        }
        shuffleButton.repaint();
    }

    /**
     * Updates repeat button icon, visual state, and tooltip based on repeat mode.
     *
     * @param mode active repeat mode
     */
    public void updateRepeatMode(RepeatMode mode) {
        this.repeatMode = (mode != null) ? mode : RepeatMode.OFF;
        switch (this.repeatMode) {
            case ONE -> {
                repeatButton.setText("🔂");
                repeatButton.putClientProperty("JButton.buttonType", "roundRect");
            }
            case ALL -> {
                repeatButton.setText("🔁");
                repeatButton.putClientProperty("JButton.buttonType", "roundRect");
            }
            case OFF -> {
                repeatButton.setText("🔁");
                repeatButton.putClientProperty("JButton.buttonType", null);
            }
        }
        repeatButton.setToolTipText(getRepeatToolTipText(this.repeatMode, I18nManager.getInstance()));
        repeatButton.repaint();
    }

    private static String getRepeatToolTipText(RepeatMode mode, I18nManager i18n) {
        if (mode == null) return i18n.get("control.repeat_off");
        return switch (mode) {
            case ONE -> i18n.get("control.repeat_one");
            case ALL -> i18n.get("control.repeat_all");
            case OFF -> i18n.get("control.repeat_off");
        };
    }

    public void setShuffleToggleListener(Runnable shuffleToggleListener) {
        this.shuffleToggleListener = shuffleToggleListener;
    }

    public void setRepeatCycleListener(Runnable repeatCycleListener) {
        this.repeatCycleListener = repeatCycleListener;
    }

    public boolean isShuffle() {
        return isShuffle;
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    // --- Component Getters ---

    public JButton getPrevButton() {
        return prevButton;
    }

    public JButton getPlayPauseButton() {
        return playPauseButton;
    }

    public JButton getNextButton() {
        return nextButton;
    }

    public JButton getShuffleButton() {
        return shuffleButton;
    }

    public JButton getRepeatButton() {
        return repeatButton;
    }

    public JSlider getVolumeSlider() {
        return volumeSlider;
    }

    public JSlider getSeekSlider() {
        return seekSlider;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public JButton getThemeToggleButton() {
        return themeToggleButton;
    }

    public JButton getMiniPlayerButton() {
        return miniPlayerButton;
    }

    public JLabel getCurrentTimeLabel() {
        return currentTimeLabel;
    }

    public JLabel getTotalTimeLabel() {
        return totalTimeLabel;
    }

    public JLabel getArtworkLabel() {
        return artworkLabel;
    }
}
