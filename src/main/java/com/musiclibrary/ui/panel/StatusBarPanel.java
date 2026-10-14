package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.util.FormatUtils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * Bottom status bar panel displaying library summary metrics (track count, total duration,
 * total file size) and a background progress indicator.
 */
public class StatusBarPanel extends JPanel {

    private final JLabel summaryLabel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    private int currentTrackCount = 0;
    private long currentTotalDuration = 0;
    private long currentTotalBytes = 0;
    private String customStatusText = null;

    public StatusBarPanel() {
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, getBackground().darker()),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));
        setPreferredSize(new Dimension(0, 26));

        // Left: Summary metrics
        summaryLabel = new JLabel();
        add(summaryLabel, BorderLayout.WEST);

        // Right: Status text & Progress Bar
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        statusLabel = new JLabel();

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(140, 16));
        progressBar.setVisible(false);

        rightPanel.add(statusLabel);
        rightPanel.add(progressBar);
        add(rightPanel, BorderLayout.EAST);

        updateLanguage();
    }

    /**
     * Updates localized summary and status strings.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();

        String countStr = String.format("%,d", currentTrackCount);
        String durationStr = formatDuration(currentTotalDuration);
        String sizeStr = FormatUtils.formatFileSize(currentTotalBytes);
        summaryLabel.setText(i18n.format("status.summary", countStr, durationStr, sizeStr));

        if (customStatusText != null) {
            statusLabel.setText(customStatusText);
        } else {
            statusLabel.setText(i18n.get("status.ready"));
        }

        revalidate();
        repaint();
    }

    /**
     * Updates library summary metrics.
     *
     * @param trackCount           number of tracks
     * @param totalDurationSeconds total duration in seconds
     * @param totalBytes           total file size in bytes
     */
    public void updateSummary(int trackCount, long totalDurationSeconds, long totalBytes) {
        this.currentTrackCount = trackCount;
        this.currentTotalDuration = totalDurationSeconds;
        this.currentTotalBytes = totalBytes;
        updateLanguage();
    }

    /**
     * Sets a custom status message (e.g. during scanning or background tasks).
     * Pass null to restore the default ready status.
     *
     * @param status custom status text, or null for default
     */
    public void setStatus(String status) {
        this.customStatusText = status;
        updateLanguage();
    }

    /**
     * Displays and configures the progress bar.
     *
     * @param indeterminate true for indeterminate spinning animation
     * @param value         current progress value
     * @param max           maximum progress value
     */
    public void showProgress(boolean indeterminate, int value, int max) {
        progressBar.setIndeterminate(indeterminate);
        if (!indeterminate) {
            progressBar.setMinimum(0);
            progressBar.setMaximum(max);
            progressBar.setValue(value);
        }
        progressBar.setVisible(true);
    }

    /**
     * Hides the progress bar.
     */
    public void hideProgress() {
        progressBar.setVisible(false);
    }

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 86400L;

    private static String formatDuration(long seconds) {
        I18nManager i18n = I18nManager.getInstance();
        if (seconds <= 0) {
            return i18n.get("duration.zero");
        }
        long days = seconds / SECONDS_PER_DAY;
        long hours = (seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long secs = seconds % SECONDS_PER_MINUTE;

        if (days > 0) {
            return i18n.format("duration.days_hours", days, hours);
        } else if (hours > 0) {
            return i18n.format("duration.hours_minutes", hours, minutes);
        } else {
            return i18n.format("duration.minutes_seconds", minutes, secs);
        }
    }

    // --- Component Getters ---

    public JLabel getSummaryLabel() {
        return summaryLabel;
    }

    public JLabel getStatusLabel() {
        return statusLabel;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }
}
