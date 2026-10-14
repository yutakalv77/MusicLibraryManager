package com.musiclibrary.ui;

import com.musiclibrary.controller.PlayerController;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.i18n.LanguageChangeListener;
import com.musiclibrary.model.PlaybackState;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.audio.PlayerListener;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.theme.ThemeChangeListener;
import com.musiclibrary.theme.ThemeManager;
import com.musiclibrary.util.TimeFormatter;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import static com.musiclibrary.util.SwingUtils.runOnEdt;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

/**
 * Compact, desktop-resident mini player window coordinating seamlessly
 * with the shared {@link PlayerController} and uninterrupted audio playback.
 */
public class MiniPlayerFrame extends JFrame {

    private final PlayerController playerController;
    private final Runnable restoreAction;

    // UI Components
    private final JLabel artworkLabel;
    private final JLabel titleLabel;
    private final JLabel artistLabel;
    private final JLabel currentTimeLabel;
    private final JSlider seekSlider;
    private final JLabel totalTimeLabel;

    private final JButton prevButton;
    private final JButton playPauseButton;
    private final JButton nextButton;
    private final JSlider volumeSlider;
    private final JButton pinButton;
    private final JButton restoreButton;

    // Listeners for cleanup
    private final PlayerListener playerListener;
    private final LanguageChangeListener languageListener;
    private final ThemeChangeListener themeListener;

    // State tracking
    private volatile boolean isScrubbing = false;
    private volatile long currentTotalSeconds = 0;
    private volatile boolean isUpdatingVolume = false;
    private volatile boolean isUpdatingProgress = false;
    private volatile boolean disposed = false;

    public MiniPlayerFrame(PlayerController playerController, Runnable restoreAction) {
        this.playerController = Objects.requireNonNull(playerController, "playerController cannot be null");
        this.restoreAction = restoreAction;

        setTitle(I18nManager.getInstance().get("mini_player.title"));
        setSize(420, 105);
        setMinimumSize(new Dimension(380, 95));
        setResizable(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                restore();
            }
        });

        // 1. Initialize UI Elements
        // Artwork
        artworkLabel = new JLabel("🎵", SwingConstants.CENTER);
        artworkLabel.setPreferredSize(new Dimension(36, 36));
        artworkLabel.setBorder(BorderFactory.createEtchedBorder());

        // Track Info
        titleLabel = new JLabel(I18nManager.getInstance().get("control.no_track"));
        Font defFont = titleLabel.getFont();
        titleLabel.setFont(defFont != null ? defFont.deriveFont(Font.BOLD, 12f) : new Font(Font.SANS_SERIF, Font.BOLD, 12));

        artistLabel = new JLabel("");
        artistLabel.setFont(defFont != null ? defFont.deriveFont(Font.PLAIN, 10.5f) : new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        // Window Controls (Pin & Restore)
        pinButton = new JButton("📌");
        pinButton.setPreferredSize(new Dimension(28, 24));
        pinButton.setFocusable(false);
        pinButton.addActionListener(e -> toggleAlwaysOnTop());

        restoreButton = new JButton("⤢");
        restoreButton.setPreferredSize(new Dimension(28, 24));
        restoreButton.setFocusable(false);
        restoreButton.addActionListener(e -> restore());

        // Transport Controls
        prevButton = new JButton("⏮");
        prevButton.setPreferredSize(new Dimension(28, 26));
        prevButton.setFocusable(false);
        prevButton.addActionListener(e -> playerController.previous());

        playPauseButton = new JButton("▶");
        playPauseButton.setPreferredSize(new Dimension(32, 26));
        playPauseButton.setFocusable(false);
        playPauseButton.addActionListener(e -> playerController.togglePlayPause());

        nextButton = new JButton("⏭");
        nextButton.setPreferredSize(new Dimension(28, 26));
        nextButton.setFocusable(false);
        nextButton.addActionListener(e -> playerController.next());

        // Seek Bar
        currentTimeLabel = new JLabel("00:00");
        currentTimeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));

        seekSlider = new JSlider(0, 1000, 0);
        seekSlider.setPreferredSize(new Dimension(100, 20));
        seekSlider.setFocusable(false);
        setupSeekSliderBehavior();

        totalTimeLabel = new JLabel("00:00");
        totalTimeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));

        // Volume Slider
        volumeSlider = new JSlider(0, 100, 70);
        volumeSlider.setPreferredSize(new Dimension(50, 20));
        volumeSlider.setFocusable(false);
        volumeSlider.addChangeListener(e -> {
            if (!isUpdatingVolume) {
                playerController.setVolumePercent(volumeSlider.getValue());
            }
        });

        // 2. Assemble Layout
        JPanel mainPanel = new JPanel(new BorderLayout(6, 4));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        // Top Row: [Artwork] [Title + Artist] [Pin] [Restore]
        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.add(artworkLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new javax.swing.BoxLayout(textPanel, javax.swing.BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        artistLabel.setAlignmentX(LEFT_ALIGNMENT);
        textPanel.add(titleLabel);
        textPanel.add(artistLabel);
        topRow.add(textPanel, BorderLayout.CENTER);

        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        topControls.add(pinButton);
        topControls.add(restoreButton);
        topRow.add(topControls, BorderLayout.EAST);

        // Bottom Row: [Prev/Play/Next] [0:00 --- 3:00] [Vol]
        JPanel bottomRow = new JPanel(new BorderLayout(4, 0));

        JPanel transportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        transportPanel.add(prevButton);
        transportPanel.add(playPauseButton);
        transportPanel.add(nextButton);
        bottomRow.add(transportPanel, BorderLayout.WEST);

        JPanel seekPanel = new JPanel(new BorderLayout(4, 0));
        seekPanel.add(currentTimeLabel, BorderLayout.WEST);
        seekPanel.add(seekSlider, BorderLayout.CENTER);
        seekPanel.add(totalTimeLabel, BorderLayout.EAST);
        bottomRow.add(seekPanel, BorderLayout.CENTER);

        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        JLabel volumeIcon = new JLabel("🔊");
        volumeIcon.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        volumePanel.add(volumeIcon);
        volumePanel.add(volumeSlider);
        bottomRow.add(volumePanel, BorderLayout.EAST);

        mainPanel.add(topRow, BorderLayout.NORTH);
        mainPanel.add(bottomRow, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        // 3. Setup Listeners
        playerListener = new PlayerListener() {
            @Override
            public void onStateChanged(PlaybackState newState) {
                runOnEdt(() -> updatePlaybackState(newState));
            }

            @Override
            public void onTrackStarted(Track track) {
                runOnEdt(() -> updateTrackInfo(track));
            }

            @Override
            public void onProgressUpdate(long currentSeconds, long totalSeconds) {
                runOnEdt(() -> updateProgress(currentSeconds, totalSeconds));
            }

            @Override
            public void onTrackFinished(Track track) {
                // Handled by PlayerController
            }
        };
        this.playerController.getAudioPlayer().addPlayerListener(playerListener);

        languageListener = newLocale -> updateLanguage();
        I18nManager.getInstance().addLanguageChangeListener(languageListener);

        themeListener = newTheme -> updateTheme();
        ThemeManager.getInstance().addThemeChangeListener(themeListener);

        // 4. Setup Shortcuts
        setupKeyboardShortcuts();

        // 5. Initial State Synchronization
        updateLanguage();
        syncState();
    }

    private void setupSeekSliderBehavior() {
        seekSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isScrubbing = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isScrubbing = false;
                if (currentTotalSeconds > 0) {
                    long targetSeconds = (long) ((seekSlider.getValue() / 1000.0) * currentTotalSeconds);
                    playerController.seekTo(targetSeconds);
                }
            }
        });
    }

    private void setupKeyboardShortcuts() {
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();

        // Space: Toggle Play/Pause
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "togglePlayPause");
        actionMap.put("togglePlayPause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playerController.togglePlayPause();
            }
        });

        // Ctrl + M: Restore main window
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK), "restoreWindow");
        actionMap.put("restoreWindow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                restore();
            }
        });

        // Ctrl + T: Toggle theme
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), "toggleTheme");
        actionMap.put("toggleTheme", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ThemeManager.getInstance().toggleTheme();
            }
        });

        // Left: Skip backward
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "skipBackward");
        actionMap.put("skipBackward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playerController.skipBackward();
            }
        });

        // Right: Skip forward
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "skipForward");
        actionMap.put("skipForward", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playerController.skipForward();
            }
        });
    }

    /**
     * Synchronizes current playback state, track metadata, volume, and progress
     * from {@link PlayerController}.
     */
    public void syncState() {
        if (disposed) {
            return;
        }

        // State & Play/Pause button
        updatePlaybackState(playerController.getAudioPlayer().getState());

        // Track info
        Track current = playerController.getAudioPlayer().getCurrentTrack();
        updateTrackInfo(current);

        // Progress
        long currentPos = playerController.getAudioPlayer().getCurrentPositionSeconds();
        long totalDur = playerController.getAudioPlayer().getTotalDurationSeconds();
        updateProgress(currentPos, totalDur);

        // Volume
        isUpdatingVolume = true;
        try {
            volumeSlider.setValue(playerController.getVolumePercent());
        } finally {
            isUpdatingVolume = false;
        }
    }

    public void updatePlaybackState(PlaybackState state) {
        if (state == PlaybackState.PLAYING) {
            playPauseButton.setText("⏸");
        } else {
            playPauseButton.setText("▶");
        }
    }

    public void updateTrackInfo(Track track) {
        if (track == null) {
            titleLabel.setText(I18nManager.getInstance().get("control.no_track"));
            artistLabel.setText("");
            artworkLabel.setIcon(null);
            artworkLabel.setText("🎵");
            return;
        }

        titleLabel.setText(track.getTitle());
        String artist = track.getArtist();
        if (track.getAlbum() != null && !track.getAlbum().isBlank()) {
            artist += " — " + track.getAlbum();
        }
        artistLabel.setText(artist != null ? artist : "");

        byte[] artwork = track.getArtworkData();
        if (artwork != null && artwork.length > 0) {
            try {
                Image scaled = ArtworkCache.getInstance().getImage(track, 34, 34);
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

    public void updateProgress(long currentSeconds, long totalSeconds) {
        this.currentTotalSeconds = totalSeconds;

        if (isScrubbing || seekSlider.getValueIsAdjusting()) {
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
     * Toggles always-on-top window pin state.
     */
    public void toggleAlwaysOnTop() {
        boolean newState = !isAlwaysOnTop();
        setAlwaysOnTop(newState);
        updatePinButtonState();
    }

    private void updatePinButtonState() {
        I18nManager i18n = I18nManager.getInstance();
        if (isAlwaysOnTop()) {
            pinButton.setText("📍");
            pinButton.setToolTipText(i18n.get("mini_player.unpin"));
        } else {
            pinButton.setText("📌");
            pinButton.setToolTipText(i18n.get("mini_player.pin"));
        }
    }

    /**
     * Triggers main window restoration and hides this mini player.
     */
    public void restore() {
        if (restoreAction != null) {
            restoreAction.run();
        } else {
            setVisible(false);
        }
    }

    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();
        setTitle(i18n.get("mini_player.title"));
        updatePinButtonState();
        restoreButton.setToolTipText(i18n.get("mini_player.restore"));
        prevButton.setToolTipText(i18n.get("menu.controls.previous"));
        nextButton.setToolTipText(i18n.get("menu.controls.next"));
        playPauseButton.setToolTipText(i18n.get("menu.controls.play"));

        if (playerController.getAudioPlayer().getCurrentTrack() == null) {
            titleLabel.setText(i18n.get("control.no_track"));
        }
    }

    public void updateTheme() {
        SwingUtilities.updateComponentTreeUI(this);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        if (playerController != null && playerController.getAudioPlayer() != null) {
            playerController.getAudioPlayer().removePlayerListener(playerListener);
        }
        I18nManager.getInstance().removeLanguageChangeListener(languageListener);
        ThemeManager.getInstance().removeThemeChangeListener(themeListener);

        super.dispose();
    }

    // --- Component Getters ---

    public JLabel getArtworkLabel() {
        return artworkLabel;
    }

    public JLabel getTitleLabel() {
        return titleLabel;
    }

    public JLabel getArtistLabel() {
        return artistLabel;
    }

    public JLabel getCurrentTimeLabel() {
        return currentTimeLabel;
    }

    public JSlider getSeekSlider() {
        return seekSlider;
    }

    public JLabel getTotalTimeLabel() {
        return totalTimeLabel;
    }

    public JButton getPrevButton() {
        return prevButton;
    }

    public JButton getPlayPauseButton() {
        return playPauseButton;
    }

    public JButton getNextButton() {
        return nextButton;
    }

    public JSlider getVolumeSlider() {
        return volumeSlider;
    }

    public JButton getPinButton() {
        return pinButton;
    }

    public JButton getRestoreButton() {
        return restoreButton;
    }

    public boolean isDisposed() {
        return disposed;
    }
}
