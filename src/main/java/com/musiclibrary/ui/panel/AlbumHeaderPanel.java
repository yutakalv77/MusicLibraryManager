package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.util.TimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.util.Objects;

/**
 * Top header panel displayed in album detail view, providing a "Back to albums" navigation button,
 * representative artwork thumbnail, album title, artist, track count, and total duration.
 */
public class AlbumHeaderPanel extends JPanel {

    private static final int THUMBNAIL_SIZE = 80;

    private final JButton backButton;
    private final JLabel artworkLabel;
    private final JLabel titleLabel;
    private final JLabel artistLabel;
    private final JLabel metaLabel;

    private Album currentAlbum;
    private Runnable onBackAction;

    public AlbumHeaderPanel() {
        setLayout(new BorderLayout(16, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor")
                        : UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));

        // 1. Navigation Back Button
        backButton = new JButton();
        updateBackButtonText();
        backButton.setFocusPainted(false);
        backButton.addActionListener(e -> {
            if (onBackAction != null) {
                onBackAction.run();
            }
        });

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(backButton, BorderLayout.WEST);
        add(topBar, BorderLayout.NORTH);

        // 2. Center Content (Thumbnail + Details)
        JPanel contentPanel = new JPanel(new BorderLayout(14, 0));
        contentPanel.setOpaque(false);

        artworkLabel = new JLabel();
        artworkLabel.setPreferredSize(new Dimension(THUMBNAIL_SIZE, THUMBNAIL_SIZE));
        artworkLabel.setMinimumSize(new Dimension(THUMBNAIL_SIZE, THUMBNAIL_SIZE));
        artworkLabel.setHorizontalAlignment(SwingConstants.CENTER);
        artworkLabel.setVerticalAlignment(SwingConstants.CENTER);
        artworkLabel.setIcon(new ImageIcon(ArtworkCache.getInstance().getDefaultImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE)));
        contentPanel.add(artworkLabel, BorderLayout.WEST);

        JPanel detailsPanel = new JPanel();
        detailsPanel.setOpaque(false);
        detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));

        titleLabel = new JLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18.0f));

        artistLabel = new JLabel();
        artistLabel.setFont(artistLabel.getFont().deriveFont(Font.PLAIN, 13.5f));
        artistLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        metaLabel = new JLabel();
        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 12.0f));
        metaLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        detailsPanel.add(titleLabel);
        detailsPanel.add(Box.createVerticalStrut(4));
        detailsPanel.add(artistLabel);
        detailsPanel.add(Box.createVerticalStrut(4));
        detailsPanel.add(metaLabel);

        contentPanel.add(detailsPanel, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);
    }

    /**
     * Sets the album to be displayed in the header.
     *
     * @param album album instance
     */
    public void setAlbum(Album album) {
        this.currentAlbum = album;
        if (album == null) {
            titleLabel.setText("");
            artistLabel.setText("");
            metaLabel.setText("");
            artworkLabel.setIcon(new ImageIcon(ArtworkCache.getInstance().getDefaultImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE)));
            return;
        }

        titleLabel.setText(album.getTitle());
        titleLabel.setToolTipText(album.getTitle());
        artistLabel.setText(album.getArtist());
        artistLabel.setToolTipText(album.getArtist());
        metaLabel.setText(formatMetaText());

        loadArtworkAsync(album);
    }

    public Album getAlbum() {
        return currentAlbum;
    }

    public void setOnBackAction(Runnable onBackAction) {
        this.onBackAction = onBackAction;
    }

    public void updateLanguage() {
        updateBackButtonText();
        if (currentAlbum != null) {
            metaLabel.setText(formatMetaText());
        }
    }

    private void updateBackButtonText() {
        String text = I18nManager.getInstance().get("album.back_to_albums");
        if (text == null || text.isBlank() || text.startsWith("!")) {
            text = "\u2190 Back to Albums";
        }
        backButton.setText(text);
    }

    private String formatMetaText() {
        if (currentAlbum == null) {
            return "";
        }
        String countText = (currentAlbum.getTrackCount() == 1 && I18nManager.getInstance().get("album.track_count_single") != null)
                ? I18nManager.getInstance().get("album.track_count_single")
                : I18nManager.getInstance().format("album.track_count", currentAlbum.getTrackCount());

        String durationText = TimeFormatter.format(currentAlbum.getTotalDurationSeconds());

        StringBuilder sb = new StringBuilder();
        if (currentAlbum.getYear() > 0) {
            sb.append(currentAlbum.getYear()).append(" \u2022 ");
        }
        sb.append(countText).append(" \u2022 ").append(durationText);
        return sb.toString();
    }

    private void loadArtworkAsync(Album album) {
        artworkLabel.setIcon(new ImageIcon(ArtworkCache.getInstance().getDefaultImage(THUMBNAIL_SIZE, THUMBNAIL_SIZE)));
        Track rep = album.getRepresentativeTrack();
        if (rep == null || !rep.hasArtwork()) {
            return;
        }

        SwingWorker<Image, Void> worker = new SwingWorker<>() {
            @Override
            protected Image doInBackground() {
                return ArtworkCache.getInstance().getImage(rep, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            }

            @Override
            protected void done() {
                try {
                    // Check if currentAlbum hasn't changed during async loading
                    if (Objects.equals(currentAlbum, album)) {
                        Image img = get();
                        if (img != null) {
                            artworkLabel.setIcon(new ImageIcon(img));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
    }
}
