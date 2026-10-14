package com.musiclibrary.ui.dialog;

import com.musiclibrary.i18n.I18nManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.Locale;

/**
 * Non-modal progress dialog shown during audio file scanning and import.
 * Features real-time progress bar, current file label, ETA/speed indicator, "Run in Background", and "Cancel".
 */
public class ImportProgressDialog extends JDialog {

    private final JLabel titleLabel;
    private final JLabel statusMessageLabel;
    private final JLabel fileNameLabel;
    private final JLabel metricsLabel;
    private final JProgressBar progressBar;
    private final JButton backgroundButton;
    private final JButton cancelButton;

    private Runnable onCancelCallback;
    private Runnable onBackgroundCallback;

    private int currentProcessed = 0;
    private int currentTotal = 0;
    private String currentFileName = "";
    private long startTimeMs = 0L;

    public ImportProgressDialog(Frame owner) {
        super(owner, false); // non-modal
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(460, 205);
        setResizable(false);
        if (owner != null) {
            setLocationRelativeTo(owner);
        }

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        // Center: Progress info
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        titleLabel = new JLabel();
        Font titleFont = titleLabel.getFont();
        titleLabel.setFont(titleFont != null ? titleFont.deriveFont(Font.BOLD, 13f) : new Font(Font.SANS_SERIF, Font.BOLD, 13));
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);

        statusMessageLabel = new JLabel();
        statusMessageLabel.setAlignmentX(LEFT_ALIGNMENT);

        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(410, 18));
        progressBar.setIndeterminate(true);
        progressBar.setAlignmentX(LEFT_ALIGNMENT);

        fileNameLabel = new JLabel(" ");
        fileNameLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        fileNameLabel.setAlignmentX(LEFT_ALIGNMENT);

        metricsLabel = new JLabel(" ");
        metricsLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        metricsLabel.setForeground(Color.GRAY);
        metricsLabel.setAlignmentX(LEFT_ALIGNMENT);

        infoPanel.add(titleLabel);
        infoPanel.add(Box.createVerticalStrut(6));
        infoPanel.add(statusMessageLabel);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(progressBar);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(fileNameLabel);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(metricsLabel);

        mainPanel.add(infoPanel, BorderLayout.CENTER);

        // Bottom: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        backgroundButton = new JButton();
        backgroundButton.addActionListener(e -> {
            setVisible(false);
            if (onBackgroundCallback != null) {
                onBackgroundCallback.run();
            }
        });

        cancelButton = new JButton();
        cancelButton.addActionListener(e -> {
            cancelButton.setEnabled(false);
            if (onCancelCallback != null) {
                onCancelCallback.run();
            }
            dispose();
        });

        buttonPanel.add(backgroundButton);
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        updateLanguage();
    }

    /**
     * Updates localized strings on dialog components.
     */
    public void updateLanguage() {
        I18nManager i18n = I18nManager.getInstance();

        setTitle(i18n.get("dialog.import.title"));
        titleLabel.setText(i18n.get("dialog.import.title"));
        backgroundButton.setText(i18n.get("dialog.import.background"));
        cancelButton.setText(i18n.get("dialog.cancel"));

        updateStatusText();
        updateMetricsText();
    }

    /**
     * Updates the progress bar, status message, and speed/ETA indicators.
     *
     * @param processed number of processed files
     * @param total     total files discovered
     * @param fileName  current file name being parsed
     */
    public void updateProgress(int processed, int total, String fileName) {
        if (this.startTimeMs == 0L) {
            this.startTimeMs = System.currentTimeMillis();
        }

        this.currentProcessed = processed;
        this.currentTotal = total;
        this.currentFileName = fileName != null ? fileName : "";

        if (total > 0) {
            progressBar.setIndeterminate(false);
            progressBar.setMinimum(0);
            progressBar.setMaximum(total);
            progressBar.setValue(processed);
        } else {
            progressBar.setIndeterminate(true);
        }

        fileNameLabel.setText(truncate(this.currentFileName, 50));
        updateStatusText();
        updateMetricsText();
    }

    private void updateStatusText() {
        I18nManager i18n = I18nManager.getInstance();
        if (currentTotal > 0) {
            statusMessageLabel.setText(i18n.get("dialog.import.scanning") + " " + currentProcessed + " / " + currentTotal);
        } else {
            statusMessageLabel.setText(i18n.get("dialog.import.scanning"));
        }
    }

    private void updateMetricsText() {
        I18nManager i18n = I18nManager.getInstance();
        if (currentTotal <= 0 || currentProcessed <= 0) {
            metricsLabel.setText(" ");
            return;
        }

        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        // Start calculation when at least 1s elapsed and at least 5 tracks processed
        if (elapsedMs < 1000 || currentProcessed < 5) {
            metricsLabel.setText(i18n.get("dialog.import.calculating"));
            return;
        }

        double elapsedSec = elapsedMs / 1000.0;
        double speed = currentProcessed / elapsedSec;
        int remainingTracks = Math.max(0, currentTotal - currentProcessed);

        if (remainingTracks == 0) {
            metricsLabel.setText(" ");
            return;
        }

        long remainingSec = speed > 0 ? Math.round((double) remainingTracks / speed) : 0L;
        String etaStr = formatEta(remainingSec);
        String speedStr = String.format(Locale.US, "%.1f", speed);

        metricsLabel.setText(i18n.format("dialog.import.metrics", speedStr, etaStr));
    }

    private static String formatEta(long seconds) {
        I18nManager i18n = I18nManager.getInstance();
        if (seconds < 60) {
            return i18n.format("eta.seconds", Math.max(1, seconds));
        }
        long minutes = seconds / 60;
        long remSec = seconds % 60;
        if (minutes < 60) {
            return i18n.format("eta.minutes_seconds", minutes, remSec);
        }
        long hours = minutes / 60;
        long remMin = minutes % 60;
        if (hours < 24) {
            return i18n.format("eta.hours_minutes", hours, remMin);
        }
        long days = hours / 24;
        long remHours = hours % 24;
        return i18n.format("eta.days_hours", days, remHours);
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return "..." + text.substring(text.length() - maxLen + 3);
    }

    // --- Callback Setters ---

    public void setOnCancel(Runnable onCancelCallback) {
        this.onCancelCallback = onCancelCallback;
    }

    public void setOnBackground(Runnable onBackgroundCallback) {
        this.onBackgroundCallback = onBackgroundCallback;
    }

    // --- Component Getters & Setters ---

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public JButton getBackgroundButton() {
        return backgroundButton;
    }

    public JButton getCancelButton() {
        return cancelButton;
    }

    public JLabel getStatusMessageLabel() {
        return statusMessageLabel;
    }

    public JLabel getFileNameLabel() {
        return fileNameLabel;
    }

    public JLabel getMetricsLabel() {
        return metricsLabel;
    }

    public void resetStartTime() {
        this.startTimeMs = System.currentTimeMillis();
    }

    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }
}
