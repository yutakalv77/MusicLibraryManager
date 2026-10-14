package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
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
 * Interactive card displaying an album's artwork (async loaded), title, artist,
 * and track metadata with hover effects and selection callbacks.
 */
public class AlbumCardPanel extends JPanel {

    public static final int CARD_WIDTH = 152;
    public static final int CARD_HEIGHT = 215;
    public static final int ARTWORK_SIZE = 140;

    private static final ExecutorService ARTWORK_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "AlbumCard-Artwork-Loader");
        t.setDaemon(true);
        return t;
    });

    private final Album album;
    private final JLabel artworkLabel;
    private final JLabel titleLabel;
    private final JLabel artistLabel;
    private final JLabel metaLabel;

    private boolean isHovered = false;
    private Consumer<Album> onAlbumSelected;
    private volatile boolean disposed = false;

    public AlbumCardPanel(Album album) {
        this(album, null);
    }

    public AlbumCardPanel(Album album, Consumer<Album> onAlbumSelected) {
        this.album = Objects.requireNonNull(album, "album cannot be null");
        this.onAlbumSelected = onAlbumSelected;

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

        titleLabel = new JLabel(album.getTitle());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12.0f));
        titleLabel.setToolTipText(album.getTitle());

        artistLabel = new JLabel(album.getArtist());
        artistLabel.setFont(artistLabel.getFont().deriveFont(Font.PLAIN, 11.5f));
        artistLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        artistLabel.setToolTipText(album.getArtist());

        metaLabel = new JLabel(formatMetaText());
        metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 10.5f));
        metaLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        infoPanel.add(titleLabel);
        infoPanel.add(artistLabel);
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
                if (AlbumCardPanel.this.onAlbumSelected != null) {
                    AlbumCardPanel.this.onAlbumSelected.accept(AlbumCardPanel.this.album);
                }
            }
        });

        // 4. Asynchronously load high-res artwork without blocking EDT
        loadArtworkAsync();
    }

    private String formatMetaText() {
        String countPattern = (album.getTrackCount() == 1)
                ? I18nManager.getInstance().get("album.track_count_single")
                : I18nManager.getInstance().get("album.track_count");
        if (countPattern == null || countPattern.isBlank()) {
            countPattern = "{0} tracks";
        }
        String trackCountText = I18nManager.getInstance().format("album.track_count", album.getTrackCount());
        if (album.getTrackCount() == 1 && I18nManager.getInstance().get("album.track_count_single") != null) {
            trackCountText = I18nManager.getInstance().get("album.track_count_single");
        }

        if (album.getYear() > 0) {
            return album.getYear() + " \u2022 " + trackCountText;
        }
        return trackCountText;
    }

    private void loadArtworkAsync() {
        Track repTrack = album.getRepresentativeTrack();
        if (repTrack == null || !repTrack.hasArtwork()) {
            return;
        }

        // 1. Fast-path: Check memory cache synchronously (0ms, zero background thread cost)
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

    /**
     * Marks this card as disposed to ignore pending background artwork loading.
     */
    public void dispose() {
        this.disposed = true;
    }

    public Album getAlbum() {
        return album;
    }

    public void setOnAlbumSelected(Consumer<Album> onAlbumSelected) {
        this.onAlbumSelected = onAlbumSelected;
    }

    public void updateLanguage() {
        metaLabel.setText(formatMetaText());
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isHovered) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color hoverBg = UIManager.getColor("List.selectionBackground");
            if (hoverBg != null) {
                g2.setColor(new Color(hoverBg.getRed(), hoverBg.getGreen(), hoverBg.getBlue(), 35));
            } else {
                g2.setColor(new Color(128, 128, 128, 35));
            }
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
