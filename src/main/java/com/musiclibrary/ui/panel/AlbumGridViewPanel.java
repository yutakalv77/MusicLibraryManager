package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Track;
import com.musiclibrary.ui.layout.WrapLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Responsive scrollable grid panel displaying album artwork cards.
 * Features ultra-responsive incremental lazy chunk rendering (60 cards per chunk),
 * zero-blocking asynchronous artwork loading, and seamless search filtering.
 */
@SuppressWarnings({"this-escape"})
public class AlbumGridViewPanel extends JPanel {

    public static final int DEFAULT_CHUNK_SIZE = 60;

    private final JPanel cardsPanel;
    private final JScrollPane scrollPane;
    private final JLabel emptyLabel;

    private final int chunkSize;
    private List<Album> allAlbums = Collections.emptyList();
    private List<Album> displayedAlbums = Collections.emptyList();
    private final List<AlbumCardPanel> cardPanels = new ArrayList<>();
    private Consumer<Album> onAlbumSelectedAction;

    private int renderedCount = 0;
    private final java.util.concurrent.atomic.AtomicBoolean isLoadingChunk = new java.util.concurrent.atomic.AtomicBoolean(false);
    private int savedScrollPosition = 0;
    private Album lastSelectedAlbum = null;
    private Album selectedAlbum = null;

    public AlbumGridViewPanel() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public AlbumGridViewPanel(int chunkSize) {
        this.chunkSize = Math.max(1, chunkSize);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(150, 0));

        cardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 16, 16));
        cardsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        scrollPane = new JScrollPane(cardsPanel);
        scrollPane.setMinimumSize(new Dimension(150, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Listen for scroll adjustments to trigger incremental loading of next chunks
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> checkScrollAndLoadMore());

        emptyLabel = new JLabel();
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.PLAIN, 14.0f));
        emptyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        updateEmptyText();

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the full album collection and resets any active filters.
     * Scroll position is reset to 0.
     *
     * @param albums full collection of albums
     */
    public void setAlbums(List<Album> albums) {
        setAlbums(albums, false);
    }

    /**
     * Sets the full album collection and resets any active filters.
     * If preserveScroll is true, attempts to restore the saved scroll position without resetting to 0.
     *
     * @param albums full collection of albums
     * @param preserveScroll whether to preserve and restore the saved scroll position
     */
    public void setAlbums(List<Album> albums, boolean preserveScroll) {
        List<Album> newAlbums = (albums != null) ? List.copyOf(albums) : Collections.emptyList();
        boolean sameContent = hasSameAlbumsContent(this.allAlbums, newAlbums);

        this.allAlbums = newAlbums;
        this.displayedAlbums = this.allAlbums;

        if (preserveScroll && sameContent && renderedCount > 0) {
            if (selectedAlbum != null) {
                selectedAlbum = findMatchingAlbum(selectedAlbum, this.displayedAlbums);
                updateCardSelectionState();
            }
            restoreScrollPosition();
        } else {
            if (preserveScroll) {
                if (selectedAlbum != null) {
                    selectedAlbum = findMatchingAlbum(selectedAlbum, this.displayedAlbums);
                }
            } else {
                savedScrollPosition = 0;
                lastSelectedAlbum = null;
                selectedAlbum = null;
            }
            renderCards();
            if (preserveScroll) {
                restoreScrollPosition();
            } else {
                scrollPane.getVerticalScrollBar().setValue(0);
            }
        }
    }

    /**
     * Filters displayed albums by case-insensitive matching on title, artist, or track titles.
     *
     * @param query search query
     */
    public void filterAlbums(String query) {
        if (query == null || query.isBlank()) {
            this.displayedAlbums = this.allAlbums;
        } else {
            String lower = query.trim().toLowerCase(Locale.ROOT);
            List<Album> filtered = new ArrayList<>();
            for (Album album : allAlbums) {
                if (matchesAlbumQuery(album, lower)) {
                    filtered.add(album);
                }
            }
            this.displayedAlbums = filtered;
        }
        if (this.selectedAlbum != null) {
            this.selectedAlbum = findMatchingAlbum(this.selectedAlbum, this.displayedAlbums);
        } else if (this.lastSelectedAlbum != null) {
            this.selectedAlbum = findMatchingAlbum(this.lastSelectedAlbum, this.displayedAlbums);
        }
        renderCards();
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    private boolean matchesAlbumQuery(Album album, String lowerQuery) {
        if (album.getTitle() != null && album.getTitle().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (album.getArtist() != null && album.getArtist().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (album.getTracks() != null) {
            for (Track track : album.getTracks()) {
                if (track != null && track.getTitle() != null &&
                        track.getTitle().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void renderCards() {
        for (AlbumCardPanel card : cardPanels) {
            card.dispose();
        }
        cardsPanel.removeAll();
        cardPanels.clear();
        renderedCount = 0;

        if (displayedAlbums.isEmpty()) {
            cardsPanel.setLayout(new BorderLayout());
            cardsPanel.add(emptyLabel, BorderLayout.CENTER);
        } else {
            cardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 16, 16));
            loadNextChunk();
            // In case the viewport is tall enough that 1 chunk leaves remaining space, check scroll once layout completes
            SwingUtilities.invokeLater(this::checkScrollAndLoadMore);
        }

        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    /**
     * Checks scroll position and triggers loading of the next album chunk if near the bottom.
     */
    private void checkScrollAndLoadMore() {
        if (isLoadingChunk.get() || renderedCount >= displayedAlbums.size()) {
            return;
        }
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        int current = bar.getValue() + bar.getVisibleAmount();
        int max = bar.getMaximum();
        if (max <= 0 || current >= (int) (max * 0.70)) {
            loadNextChunk();
        }
    }

    /**
     * Incrementally loads and displays the next batch of album cards.
     */
    public synchronized void loadNextChunk() {
        if (renderedCount >= displayedAlbums.size() || !isLoadingChunk.compareAndSet(false, true)) {
            return;
        }
        try {
            int target = Math.min(renderedCount + chunkSize, displayedAlbums.size());
            for (int i = renderedCount; i < target; i++) {
                Album album = displayedAlbums.get(i);
                AlbumCardPanel card = new AlbumCardPanel(album, this::handleAlbumSelected);
                if (selectedAlbum != null && isSameAlbum(album, selectedAlbum)) {
                    card.setSelected(true);
                }
                cardPanels.add(card);
                cardsPanel.add(card);
            }
            renderedCount = target;
            cardsPanel.revalidate();
            cardsPanel.repaint();
        } finally {
            isLoadingChunk.set(false);
        }
    }

    private void handleAlbumSelected(Album album) {
        setSelectedAlbum(album);
        if (onAlbumSelectedAction != null) {
            onAlbumSelectedAction.accept(album);
        }
    }

    public void setSelectedAlbum(Album album) {
        this.selectedAlbum = album;
        if (album != null) {
            this.lastSelectedAlbum = album;
        }
        updateCardSelectionState();
    }

    public Album getSelectedAlbum() {
        return selectedAlbum;
    }

    private void updateCardSelectionState() {
        for (AlbumCardPanel card : cardPanels) {
            card.setSelected(selectedAlbum != null && isSameAlbum(card.getAlbum(), selectedAlbum));
        }
    }

    private boolean isSameAlbum(Album a, Album b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b) ||
                (a.getTitle().equalsIgnoreCase(b.getTitle()) &&
                 a.getArtist().equalsIgnoreCase(b.getArtist()));
    }

    private Album findMatchingAlbum(Album target, List<Album> candidates) {
        if (target == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (Album a : candidates) {
            if (isSameAlbum(a, target)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Deep-compares two album lists including track counts, durations, and individual track metadata.
     */
    public static boolean hasSameAlbumsContent(List<Album> aList, List<Album> bList) {
        if (aList == bList) {
            return true;
        }
        if (aList == null || bList == null || aList.size() != bList.size()) {
            return false;
        }
        for (int i = 0; i < aList.size(); i++) {
            Album a = aList.get(i);
            Album b = bList.get(i);
            if (!Objects.equals(a, b)
                    || a.getTrackCount() != b.getTrackCount()
                    || a.getTotalDurationSeconds() != b.getTotalDurationSeconds()
                    || !hasSameTracksContent(a.getTracks(), b.getTracks())) {
                return false;
            }
        }
        return true;
    }

    static boolean hasSameTracksContent(List<Track> t1, List<Track> t2) {
        if (t1 == t2) {
            return true;
        }
        if (t1 == null || t2 == null || t1.size() != t2.size()) {
            return false;
        }
        for (int i = 0; i < t1.size(); i++) {
            Track a = t1.get(i);
            Track b = t2.get(i);
            if (a == null || b == null) {
                if (a != b) return false;
                continue;
            }
            if (!Objects.equals(a.getId(), b.getId())
                    || !Objects.equals(a.getTitle(), b.getTitle())
                    || !Objects.equals(a.getArtist(), b.getArtist())
                    || !Objects.equals(a.getAlbum(), b.getAlbum())
                    || a.getTrackNumber() != b.getTrackNumber()
                    || a.getYear() != b.getYear()
                    || a.getDurationSeconds() != b.getDurationSeconds()
                    || a.getRating() != b.getRating()
                    || a.isMissing() != b.isMissing()) {
                return false;
            }
        }
        return true;
    }

    public List<AlbumCardPanel> getCardPanels() {
        return Collections.unmodifiableList(cardPanels);
    }

    public void setOnAlbumSelectedAction(Consumer<Album> action) {
        this.onAlbumSelectedAction = action;
        for (AlbumCardPanel card : cardPanels) {
            card.setOnAlbumSelected(this::handleAlbumSelected);
        }
    }

    public List<Album> getAllAlbums() {
        return allAlbums;
    }

    public List<Album> getDisplayedAlbums() {
        return Collections.unmodifiableList(displayedAlbums);
    }

    public int getRenderedCardCount() {
        return cardPanels.size();
    }

    public void updateLanguage() {
        updateEmptyText();
        for (AlbumCardPanel card : cardPanels) {
            card.updateLanguage();
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private void updateEmptyText() {
        String emptyText = I18nManager.getInstance().get("album.empty");
        if (emptyText == null || emptyText.isBlank() || emptyText.startsWith("!")) {
            emptyText = "No albums found.";
        }
        emptyLabel.setText(emptyText);
    }

    /**
     * Saves the current vertical scroll position and the selected album.
     *
     * @param album album selected before navigating away
     */
    public void saveScrollPosition(Album album) {
        int currentVal = scrollPane.getVerticalScrollBar().getValue();
        if (currentVal > 0) {
            this.savedScrollPosition = currentVal;
        }
        this.lastSelectedAlbum = album;
    }

    /**
     * Restores the previously saved scroll position and ensures the last selected album
     * (or saved scroll offset) is visible, loading chunks if necessary.
     */
    public void restoreScrollPosition() {
        if (displayedAlbums.isEmpty()) {
            return;
        }

        // 1. If lastSelectedAlbum is present, make sure chunks up to its index are loaded
        if (lastSelectedAlbum != null) {
            int targetIndex = -1;
            for (int i = 0; i < displayedAlbums.size(); i++) {
                Album a = displayedAlbums.get(i);
                if (a.equals(lastSelectedAlbum) ||
                        (a.getTitle().equalsIgnoreCase(lastSelectedAlbum.getTitle()) &&
                         a.getArtist().equalsIgnoreCase(lastSelectedAlbum.getArtist()))) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex >= 0) {
                while (renderedCount <= targetIndex && renderedCount < displayedAlbums.size()) {
                    loadNextChunk();
                }
            }
        }

        // 2. If savedScrollPosition > 0, make sure sufficient chunks are loaded
        if (savedScrollPosition > 0) {
            while (renderedCount < displayedAlbums.size() &&
                    (scrollPane.getVerticalScrollBar().getMaximum() - scrollPane.getVerticalScrollBar().getVisibleAmount()) < savedScrollPosition) {
                int before = renderedCount;
                loadNextChunk();
                if (renderedCount == before) {
                    break;
                }
            }
        }

        cardsPanel.validate();
        scrollPane.validate();

        // 3. Apply scroll position
        if (savedScrollPosition > 0) {
            scrollPane.getVerticalScrollBar().setValue(savedScrollPosition);
        }

        SwingUtilities.invokeLater(() -> {
            if (savedScrollPosition > 0) {
                scrollPane.getVerticalScrollBar().setValue(savedScrollPosition);
            }
        });
    }

    /**
     * Ensures the specified album card is rendered and scrolled into view.
     *
     * @param album album to scroll into view
     */
    public void scrollToAlbum(Album album) {
        if (album == null || displayedAlbums.isEmpty()) {
            return;
        }
        int targetIndex = -1;
        for (int i = 0; i < displayedAlbums.size(); i++) {
            Album a = displayedAlbums.get(i);
            if (a.equals(album) ||
                    (a.getTitle().equalsIgnoreCase(album.getTitle()) &&
                     a.getArtist().equalsIgnoreCase(album.getArtist()))) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex >= 0) {
            while (renderedCount <= targetIndex && renderedCount < displayedAlbums.size()) {
                loadNextChunk();
            }
            cardsPanel.validate();
            for (AlbumCardPanel card : cardPanels) {
                if (card.getAlbum() != null &&
                        (card.getAlbum().equals(album) ||
                         (card.getAlbum().getTitle().equalsIgnoreCase(album.getTitle()) &&
                          card.getAlbum().getArtist().equalsIgnoreCase(album.getArtist())))) {
                    cardsPanel.scrollRectToVisible(card.getBounds());
                    break;
                }
            }
        }
    }

    public int getSavedScrollPosition() {
        return savedScrollPosition;
    }

    public void setSavedScrollPosition(int savedScrollPosition) {
        this.savedScrollPosition = Math.max(0, savedScrollPosition);
    }

    public Album getLastSelectedAlbum() {
        return lastSelectedAlbum;
    }

    public void setLastSelectedAlbum(Album lastSelectedAlbum) {
        this.lastSelectedAlbum = lastSelectedAlbum;
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }
}
