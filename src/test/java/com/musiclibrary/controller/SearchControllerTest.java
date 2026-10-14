package com.musiclibrary.controller;

import com.musiclibrary.model.Track;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.ui.panel.TrackTablePanel;
import com.musiclibrary.ui.table.TrackTableModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SearchControllerTest {

    private FastSearchIndex searchIndex;
    private JTextField searchField;
    private TrackTableModel tableModel;
    private TrackTablePanel trackTablePanel;
    private SearchController controller;

    private Track t1;
    private Track t2;
    private Track t3;

    @BeforeEach
    void setUp() {
        searchIndex = new FastSearchIndex();
        searchField = new JTextField();
        tableModel = new TrackTableModel();
        trackTablePanel = new TrackTablePanel(tableModel);

        t1 = Track.builder()
                .id("1")
                .filePath("tracks/1.mp3")
                .title("Bohemian Rhapsody")
                .artist("Queen")
                .album("A Night at the Opera")
                .genre("Rock")
                .year(1975)
                .build();

        t2 = Track.builder()
                .id("2")
                .filePath("tracks/2.mp3")
                .title("Another One Bites the Dust")
                .artist("Queen")
                .album("The Game")
                .genre("Funk Rock")
                .year(1980)
                .build();

        t3 = Track.builder()
                .id("3")
                .filePath("tracks/3.mp3")
                .title("Hotel California")
                .artist("Eagles")
                .album("Hotel California")
                .genre("Rock")
                .year(1976)
                .build();

        List<Track> all = List.of(t1, t2, t3);
        tableModel.setTracks(all);
        searchIndex.indexTracks(all);

        controller = new SearchController(searchIndex, searchField, trackTablePanel);
    }

    @Test
    @DisplayName("Initial state displays all rows with no RowFilter")
    void testInitialState() {
        assertThat(trackTablePanel.getRowSorter().getRowFilter()).isNull();
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Typing query applies RowFilter and filters table rows instantaneously")
    void testTypingQueryFiltersRows() {
        // Search "queen" -> matches t1 and t2
        searchField.setText("queen");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(2);

        // Search "eagles" -> matches t3 only
        searchField.setText("eagles");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(1);

        // Search "1975" -> matches t1 only
        searchField.setText("1975");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(1);

        // Search with non-matching query -> 0 rows
        searchField.setText("nonexistent");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Clearing search text removes RowFilter and restores full row count")
    void testClearingSearchRestoresAllRows() {
        searchField.setText("eagles");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(1);

        searchField.setText("");
        assertThat(trackTablePanel.getRowSorter().getRowFilter()).isNull();
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Escape key clears search field and restores full table view")
    void testEscapeKeyClearsSearch() {
        searchField.setText("bohemian");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(1);

        // Simulate ESCAPE key press event
        KeyEvent escapeEvent = new KeyEvent(
                searchField,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                KeyEvent.VK_ESCAPE,
                KeyEvent.CHAR_UNDEFINED
        );
        for (var listener : searchField.getKeyListeners()) {
            listener.keyPressed(escapeEvent);
        }

        assertThat(searchField.getText()).isEmpty();
        assertThat(trackTablePanel.getRowSorter().getRowFilter()).isNull();
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("matchCountListener receives matched row count on every query change")
    void testMatchCountListener() {
        AtomicInteger lastCount = new AtomicInteger(-1);
        controller.addMatchCountListener(lastCount::set);

        searchField.setText("queen");
        assertThat(lastCount.get()).isEqualTo(2);

        searchField.setText("opera");
        assertThat(lastCount.get()).isEqualTo(1);

        searchField.setText("");
        assertThat(lastCount.get()).isEqualTo(3);

        // Remove listener
        controller.removeMatchCountListener(lastCount::set);
    }

    @Test
    @DisplayName("dispose unbinds document and key listeners and prevents further filtering")
    void testDispose() {
        controller.dispose();
        assertThat(controller.isDisposed()).isTrue();

        // Multiple dispose calls are safe and idempotent
        assertThatCode(controller::dispose).doesNotThrowAnyException();

        // Further text changes do not affect row filter or throw exceptions
        assertThatCode(() -> searchField.setText("queen")).doesNotThrowAnyException();
        assertThat(trackTablePanel.getRowSorter().getRowFilter()).isNull();
    }

    @Test
    @DisplayName("Whitespace-only query immediately resets row filter and restores full count")
    void testWhitespaceQueryResetsRowFilter() {
        searchField.setText("eagles");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(1);

        AtomicInteger count = new AtomicInteger(-1);
        controller.addMatchCountListener(count::set);

        searchField.setText("   ");
        assertThat(trackTablePanel.getRowSorter().getRowFilter()).isNull();
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(3);
        assertThat(count.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Incremental keystroke typing continuously updates filter and match counts")
    void testIncrementalTypingSimulation() {
        AtomicInteger reportedCount = new AtomicInteger(-1);
        controller.addMatchCountListener(reportedCount::set);

        // 'q' -> matches 2 (Queen)
        searchField.setText("q");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(2);
        assertThat(reportedCount.get()).isEqualTo(2);

        // 'qu' -> matches 2
        searchField.setText("qu");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(2);

        // 'quee' -> matches 2
        searchField.setText("quee");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(2);

        // 'queen b' -> matches 2 (Bohemian / Bites)
        searchField.setText("queen b");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(2);

        // 'queen bohemian' -> matches 1
        searchField.setText("queen bohemian");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(1);
        assertThat(reportedCount.get()).isEqualTo(1);

        // backspace to empty -> matches 3
        searchField.setText("");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(3);
        assertThat(reportedCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Query yielding zero matches notifies listener with 0 and displays 0 rows")
    void testZeroMatchesHandling() {
        AtomicInteger count = new AtomicInteger(-1);
        controller.addMatchCountListener(count::set);

        searchField.setText("xyz123_no_match");
        assertThat(trackTablePanel.getRowSorter().getViewRowCount()).isEqualTo(0);
        assertThat(count.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("clearSearch is safe when search field is already empty or controller is disposed")
    void testClearSearchWhenAlreadyEmptyOrDisposed() {
        assertThat(searchField.getText()).isEmpty();
        assertThatCode(() -> controller.clearSearch()).doesNotThrowAnyException();

        controller.dispose();
        searchField.setText("some text");
        assertThatCode(() -> controller.clearSearch()).doesNotThrowAnyException();
        // Since disposed, clearSearch should not modify text
        assertThat(searchField.getText()).isEqualTo("some text");
    }
}
