package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Artist;
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
 * Responsive scrollable grid panel displaying artist cards.
 * Features incremental lazy chunk rendering (60 cards per chunk),
 * zero-blocking asynchronous artwork loading, and seamless search filtering.
 */
@SuppressWarnings({"serial", "this-escape"})
public class ArtistGridViewPanel extends JPanel {

    public static final int DEFAULT_CHUNK_SIZE = 60;

    private final JPanel cardsPanel;
    private final JScrollPane scrollPane;
    private final JLabel emptyLabel;

    private final int chunkSize;
    private List<Artist> allArtists = Collections.emptyList();
    private List<Artist> displayedArtists = Collections.emptyList();
    private final List<ArtistCardPanel> cardPanels = new ArrayList<>();
    private Consumer<Artist> onArtistSelectedAction;

    private int renderedCount = 0;
    private final java.util.concurrent.atomic.AtomicBoolean isLoadingChunk = new java.util.concurrent.atomic.AtomicBoolean(false);
    private int savedScrollPosition = 0;
    private Artist lastSelectedArtist = null;
    private Artist selectedArtist = null;

    public ArtistGridViewPanel() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public ArtistGridViewPanel(int chunkSize) {
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

        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> checkScrollAndLoadMore());

        emptyLabel = new JLabel();
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.PLAIN, 14.0f));
        emptyLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        updateEmptyText();

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Sets the full artist collection and resets any active filters.
     *
     * @param artists full collection of artists
     */
    public void setArtists(List<Artist> artists) {
        setArtists(artists, false);
    }

    /**
     * Sets the full artist collection and resets any active filters.
     * If preserveScroll is true, attempts to restore the saved scroll position.
     *
     * @param artists        full collection of artists
     * @param preserveScroll whether to preserve and restore the saved scroll position
     */
    public void setArtists(List<Artist> artists, boolean preserveScroll) {
        List<Artist> newArtists = (artists != null) ? List.copyOf(artists) : Collections.emptyList();
        boolean sameContent = hasSameArtistsContent(this.allArtists, newArtists);

        this.allArtists = newArtists;
        this.displayedArtists = this.allArtists;

        if (preserveScroll && sameContent && renderedCount > 0) {
            if (selectedArtist != null) {
                selectedArtist = findMatchingArtist(selectedArtist, this.displayedArtists);
                updateCardSelectionState();
            }
            restoreScrollPosition();
        } else {
            if (preserveScroll) {
                if (selectedArtist != null) {
                    selectedArtist = findMatchingArtist(selectedArtist, this.displayedArtists);
                }
            } else {
                savedScrollPosition = 0;
                lastSelectedArtist = null;
                selectedArtist = null;
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
     * Filters displayed artists by case-insensitive matching on artist name, tracks, or albums.
     *
     * @param query search query
     */
    public void filterArtists(String query) {
        if (query == null || query.isBlank()) {
            this.displayedArtists = this.allArtists;
        } else {
            String lower = query.trim().toLowerCase(Locale.ROOT);
            List<Artist> filtered = new ArrayList<>();
            for (Artist artist : allArtists) {
                if (matchesArtistQuery(artist, lower)) {
                    filtered.add(artist);
                }
            }
            this.displayedArtists = filtered;
        }
        if (this.selectedArtist != null) {
            this.selectedArtist = findMatchingArtist(this.selectedArtist, this.displayedArtists);
        } else if (this.lastSelectedArtist != null) {
            this.selectedArtist = findMatchingArtist(this.lastSelectedArtist, this.displayedArtists);
        }
        renderCards();
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    private boolean matchesArtistQuery(Artist artist, String lowerQuery) {
        if (artist.getName() != null && artist.getName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (artist.getTracks() != null) {
            for (Track track : artist.getTracks()) {
                if (track != null) {
                    if (track.getTitle() != null && track.getTitle().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        return true;
                    }
                    if (track.getAlbum() != null && track.getAlbum().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void renderCards() {
        for (ArtistCardPanel card : cardPanels) {
            card.dispose();
        }
        cardsPanel.removeAll();
        cardPanels.clear();
        renderedCount = 0;

        if (displayedArtists.isEmpty()) {
            cardsPanel.setLayout(new BorderLayout());
            cardsPanel.add(emptyLabel, BorderLayout.CENTER);
        } else {
            cardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 16, 16));
            loadNextChunk();
            SwingUtilities.invokeLater(this::checkScrollAndLoadMore);
        }

        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private void checkScrollAndLoadMore() {
        if (isLoadingChunk.get() || renderedCount >= displayedArtists.size()) {
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
     * Incrementally loads and displays the next batch of artist cards.
     */
    public synchronized void loadNextChunk() {
        if (renderedCount >= displayedArtists.size() || !isLoadingChunk.compareAndSet(false, true)) {
            return;
        }
        try {
            int target = Math.min(renderedCount + chunkSize, displayedArtists.size());
            for (int i = renderedCount; i < target; i++) {
                Artist artist = displayedArtists.get(i);
                ArtistCardPanel card = new ArtistCardPanel(artist, this::handleArtistSelected);
                if (selectedArtist != null && isSameArtist(artist, selectedArtist)) {
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

    private void handleArtistSelected(Artist artist) {
        setSelectedArtist(artist);
        if (onArtistSelectedAction != null) {
            onArtistSelectedAction.accept(artist);
        }
    }

    public void setSelectedArtist(Artist artist) {
        this.selectedArtist = artist;
        if (artist != null) {
            this.lastSelectedArtist = artist;
        }
        updateCardSelectionState();
    }

    public Artist getSelectedArtist() {
        return selectedArtist;
    }

    private void updateCardSelectionState() {
        for (ArtistCardPanel card : cardPanels) {
            card.setSelected(selectedArtist != null && isSameArtist(card.getArtist(), selectedArtist));
        }
    }

    private boolean isSameArtist(Artist a, Artist b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b) || a.getName().equalsIgnoreCase(b.getName());
    }

    private Artist findMatchingArtist(Artist target, List<Artist> candidates) {
        if (target == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (Artist a : candidates) {
            if (isSameArtist(a, target)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Deep-compares two artist lists including album count, track count, duration, and individual track metadata.
     */
    public static boolean hasSameArtistsContent(List<Artist> aList, List<Artist> bList) {
        if (aList == bList) {
            return true;
        }
        if (aList == null || bList == null || aList.size() != bList.size()) {
            return false;
        }
        for (int i = 0; i < aList.size(); i++) {
            Artist a = aList.get(i);
            Artist b = bList.get(i);
            if (!Objects.equals(a, b)
                    || a.getAlbumCount() != b.getAlbumCount()
                    || a.getTrackCount() != b.getTrackCount()
                    || a.getTotalDurationSeconds() != b.getTotalDurationSeconds()
                    || !AlbumGridViewPanel.hasSameTracksContent(a.getTracks(), b.getTracks())) {
                return false;
            }
        }
        return true;
    }

    public List<ArtistCardPanel> getCardPanels() {
        return Collections.unmodifiableList(cardPanels);
    }

    public void setOnArtistSelectedAction(Consumer<Artist> action) {
        this.onArtistSelectedAction = action;
        for (ArtistCardPanel card : cardPanels) {
            card.setOnArtistSelected(this::handleArtistSelected);
        }
    }

    public List<Artist> getAllArtists() {
        return allArtists;
    }

    public List<Artist> getDisplayedArtists() {
        return Collections.unmodifiableList(displayedArtists);
    }

    public int getRenderedCardCount() {
        return cardPanels.size();
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void updateLanguage() {
        updateEmptyText();
        for (ArtistCardPanel card : cardPanels) {
            card.updateLanguage();
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
    }

    private void updateEmptyText() {
        String emptyText = I18nManager.getInstance().get("artist.empty");
        if (emptyText == null || emptyText.isBlank() || emptyText.startsWith("!")) {
            emptyText = "No artists found";
        }
        emptyLabel.setText(emptyText);
    }

    public void saveScrollPosition() {
        this.savedScrollPosition = scrollPane.getVerticalScrollBar().getValue();
    }

    public void saveScrollPosition(Artist artist) {
        int currentVal = scrollPane.getVerticalScrollBar().getValue();
        if (currentVal > 0) {
            this.savedScrollPosition = currentVal;
        }
        this.lastSelectedArtist = artist;
    }

    public void restoreScrollPosition() {
        if (displayedArtists.isEmpty()) {
            return;
        }

        if (lastSelectedArtist != null) {
            int targetIndex = -1;
            for (int i = 0; i < displayedArtists.size(); i++) {
                Artist a = displayedArtists.get(i);
                if (isSameArtist(a, lastSelectedArtist)) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex >= 0) {
                while (renderedCount <= targetIndex && renderedCount < displayedArtists.size()) {
                    loadNextChunk();
                }
            }
        }

        if (savedScrollPosition > 0) {
            while (renderedCount < displayedArtists.size() &&
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

        if (savedScrollPosition > 0) {
            scrollPane.getVerticalScrollBar().setValue(savedScrollPosition);
        }

        SwingUtilities.invokeLater(() -> {
            if (savedScrollPosition > 0) {
                scrollPane.getVerticalScrollBar().setValue(savedScrollPosition);
            }
        });
    }

    public void scrollToArtist(Artist artist) {
        if (artist == null || displayedArtists.isEmpty()) {
            return;
        }
        int targetIndex = -1;
        for (int i = 0; i < displayedArtists.size(); i++) {
            Artist a = displayedArtists.get(i);
            if (isSameArtist(a, artist)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex >= 0) {
            while (renderedCount <= targetIndex && renderedCount < displayedArtists.size()) {
                loadNextChunk();
            }
            cardsPanel.validate();
            for (ArtistCardPanel card : cardPanels) {
                if (card.getArtist() != null && isSameArtist(card.getArtist(), artist)) {
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

    public Artist getLastSelectedArtist() {
        return lastSelectedArtist;
    }

    public void setLastSelectedArtist(Artist lastSelectedArtist) {
        this.lastSelectedArtist = lastSelectedArtist;
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public JLabel getEmptyLabel() {
        return emptyLabel;
    }
}
