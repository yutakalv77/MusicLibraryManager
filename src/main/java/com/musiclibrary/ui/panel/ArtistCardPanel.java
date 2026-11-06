package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Artist;
import com.musiclibrary.model.Track;
import com.musiclibrary.service.cache.ArtworkCache;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Interactive card displaying an artist's representative artwork (async loaded),
 * artist name, album count, and track count with hover effects and selection callbacks.
 */
@SuppressWarnings({"this-escape"})
public class ArtistCardPanel extends JPanel {

    public static final int CARD_WIDTH = 152;
    public static final int CARD_HEIGHT = 215;
    public static final int ARTWORK_SIZE = 140;

    private static final ExecutorService ARTWORK_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ArtistCard-Artwork-Loader");
        t.setDaemon(true);
        return t;
    });

    private final Artist artist;
    private final JLabel artworkLabel;
    private final JLabel titleLabel;
    private final JLabel albumCountLabel;
    private final JLabel metaLabel;

    private boolean isHovered = false;
    private boolean selected = false;
    private Consumer<Artist> onArtistSelected;
    private volatile boolean disposed = false;

    public ArtistCardPanel(Artist artist) {
        this(artist, null);
    }

    public ArtistCardPanel(Artist artist, Consumer<Artist> onArtistSelected) {
        this.artist = Objects.requireNonNull(artist, "artist cannot be null");
        this.onArtistSelected = onArtistSelected;

        setLayout(new BorderLayout(0, 6));
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 1. Artwork container
        artworkLabel = new JLabel();
        artworkLabel.setPreferredSize(new Dimension(ARTWORK_SIZE, ARTWORK_SIZE));
        artworkLabel.setMaximumSize(new Dimension(ARTWORK_SIZE, ARTWORK_SIZE));
        artworkLabel.setHorizontalAlignment(SwingConstants.CENTER);
        artworkLabel.setVerticalAlignment(SwingConstants.CENTER);
        artworkLabel.setIcon(new ImageIcon(ArtworkCache.getInstance().getDefaultImage(ARTWORK_SIZE, ARTWORK_SIZE)));

        add(artworkLabel, BorderLayout.NORTH);

        // 2. Info container
        JPanel infoPanel = new JPanel();
        infoPanel.setOpaque(false);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        titleLabel = new JLabel(artist.getName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.0f));
        titleLabel.setToolTipText(artist.getName());

        albumCountLabel = new JLabel(formatAlbumCountText());
        albumCountLabel.setFont(albumCountLabel.getFont().deriveFont(Font.PLAIN, 11.5f));
        albumCountLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        metaLabel = new JLabel(formatTrackCountText());
        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 10.5f));
        metaLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        infoPanel.add(titleLabel);
        infoPanel.add(albumCountLabel);
        infoPanel.add(metaLabel);

        add(infoPanel, BorderLayout.CENTER);

        // 3. Mouse interactions
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (ArtistCardPanel.this.onArtistSelected != null) {
                    ArtistCardPanel.this.onArtistSelected.accept(ArtistCardPanel.this.artist);
                }
            }
        });

        // 4. Asynchronously load artwork without blocking EDT
        loadArtworkAsync();
    }

    private String formatAlbumCountText() {
        I18nManager i18n = I18nManager.getInstance();
        if (artist.getAlbumCount() == 1) {
            String single = i18n.get("artist.album_count_single");
            if (single != null && !single.startsWith("!")) {
                return single;
            }
        }
        String formatted = i18n.format("artist.album_count", artist.getAlbumCount());
        if (formatted == null || formatted.startsWith("!")) {
            return artist.getAlbumCount() + " albums";
        }
        return formatted;
    }

    private String formatTrackCountText() {
        I18nManager i18n = I18nManager.getInstance();
        if (artist.getTrackCount() == 1) {
            String single = i18n.get("album.track_count_single");
            if (single != null && !single.startsWith("!")) {
                return single;
            }
        }
        String formatted = i18n.format("album.track_count", artist.getTrackCount());
        if (formatted == null || formatted.startsWith("!")) {
            return artist.getTrackCount() + " songs";
        }
        return formatted;
    }

    private void loadArtworkAsync() {
        Track repTrack = artist.getRepresentativeTrack();
        if (repTrack == null || !repTrack.hasArtwork()) {
            return;
        }

        // 1. Fast-path: Check memory cache synchronously (0ms)
        Image cached = ArtworkCache.getInstance().getIfPresent(repTrack, ARTWORK_SIZE, ARTWORK_SIZE);
        if (cached != null) {
            artworkLabel.setIcon(new ImageIcon(cached));
            return;
        }

        // 2. Off-thread loading via dedicated lightweight bounded thread pool
        ARTWORK_EXECUTOR.submit(() -> {
            if (disposed) {
                return;
            }
            try {
                Image image = ArtworkCache.getInstance().getImage(repTrack, ARTWORK_SIZE, ARTWORK_SIZE);
                if (image != null && !disposed) {
                    SwingUtilities.invokeLater(() -> {
                        if (!disposed) {
                            artworkLabel.setIcon(new ImageIcon(image));
                        }
                    });
                }
            } catch (Exception ignored) {
                // Fallback remains as placeholder
            }
        });
    }

    public void dispose() {
        this.disposed = true;
    }

    public Artist getArtist() {
        return artist;
    }

    public JLabel getTitleLabel() {
        return titleLabel;
    }

    public JLabel getAlbumCountLabel() {
        return albumCountLabel;
    }

    public JLabel getMetaLabel() {
        return metaLabel;
    }

    public void setOnArtistSelected(Consumer<Artist> onArtistSelected) {
        this.onArtistSelected = onArtistSelected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            repaint();
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void updateLanguage() {
        albumCountLabel.setText(formatAlbumCountText());
        metaLabel.setText(formatTrackCountText());
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (selected || isHovered) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color baseBg = UIManager.getColor("List.selectionBackground");
            if (baseBg == null) {
                baseBg = new Color(60, 120, 216);
            }
            if (selected) {
                g2.setColor(new Color(baseBg.getRed(), baseBg.getGreen(), baseBg.getBlue(), 75));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(baseBg.getRed(), baseBg.getGreen(), baseBg.getBlue(), 190));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            } else {
                g2.setColor(new Color(baseBg.getRed(), baseBg.getGreen(), baseBg.getBlue(), 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
