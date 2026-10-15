package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrackTablePanelTest {

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("TrackTablePanel initializes table with 8 columns, sorter, context menu, and custom styling")
    void testInitialization() {
        TrackTablePanel panel = new TrackTablePanel();

        assertThat(panel.getTable()).isNotNull();
        assertThat(panel.getTable().getColumnCount()).isEqualTo(9);
        assertThat(panel.getTable().getRowSorter()).isNotNull();
        assertThat(panel.getTable().getRowHeight()).isEqualTo(26);
        assertThat(panel.getScrollPane()).isNotNull();
        assertThat(panel.getContextMenu()).isNotNull();
        assertThat(panel.getTrackCount()).isEqualTo(0);
        assertThat(panel.getSelectedTrack()).isNull();
        assertThat(panel.getSelectedTracks()).isEmpty();
    }

    @Test
    @DisplayName("setTracks populates rows with raw types for sorting and flyweight renderers format visuals")
    void testSetTracksAndFormatting() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder()
                .trackNumber(1)
                .title("Bohemian Rhapsody")
                .artist("Queen")
                .album("A Night at the Opera")
                .durationSeconds(354)
                .genre("Rock")
                .year(1975)
                .rating(5)
                .filePath("C:/music/1.mp3")
                .build();

        Track t2 = Track.builder()
                .trackNumber(0) // Boundary: 0 trackNumber
                .title("Untitled Track")
                .artist("Unknown Artist")
                .album("")
                .durationSeconds(0) // Boundary: 0 duration
                .genre("")
                .year(0) // Boundary: 0 year
                .rating(0) // Boundary: 0 rating
                .filePath("C:/music/2.mp3")
                .build();

        panel.setTracks(List.of(t1, t2));
        assertThat(panel.getTrackCount()).isEqualTo(2);

        // Row 1 raw model values (crucial for numeric sorting)
        assertThat(panel.getTable().getValueAt(0, 0)).isEqualTo("");
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo(1);
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Bohemian Rhapsody");
        assertThat(panel.getTable().getValueAt(0, 3)).isEqualTo("Queen");
        assertThat(panel.getTable().getValueAt(0, 4)).isEqualTo("A Night at the Opera");
        assertThat(panel.getTable().getValueAt(0, 5)).isEqualTo(354L);
        assertThat(panel.getTable().getValueAt(0, 6)).isEqualTo("Rock");
        assertThat(panel.getTable().getValueAt(0, 7)).isEqualTo(1975);
        assertThat(panel.getTable().getValueAt(0, 8)).isEqualTo(5);

        // Row 1 rendered visual text via Flyweight renderers
        JLabel numLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(numLabel1.getText()).isEqualTo("1");

        JLabel durLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 5)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 5), false, false, 0, 5);
        assertThat(durLabel1.getText()).isEqualTo("05:54");

        JLabel ratingLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 8), false, false, 0, 8);
        assertThat(ratingLabel1.getText()).isEqualTo("★★★★★");

        // Row 2 boundary rendered visual text
        JLabel numLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(numLabel2.getText()).isEqualTo("-");

        JLabel durLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 5)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 5), false, false, 1, 5);
        assertThat(durLabel2.getText()).isEqualTo("00:00");

        JLabel yearLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 7)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 7), false, false, 1, 7);
        assertThat(yearLabel2.getText()).isEqualTo("-");

        JLabel ratingLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 8)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 8), false, false, 1, 8);
        assertThat(ratingLabel2.getText()).isEqualTo("—");
    }

    @Test
    @DisplayName("addTracks batch appends rows incrementally without full reload")
    void testBatchAddTracks() {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().id("1").filePath("C:/1.mp3").title("Track 1").build();
        Track t2 = Track.builder().id("2").filePath("C:/2.mp3").title("Track 2").build();
        panel.setTracks(List.of(t1));

        assertThat(panel.getTrackCount()).isEqualTo(1);

        panel.addTracks(List.of(t2));
        assertThat(panel.getTrackCount()).isEqualTo(2);
        assertThat(panel.getTableModel().getTrackAt(1).getTitle()).isEqualTo("Track 2");

        // updateTrack
        Track updatedT1 = t1.toBuilder().title("Updated Track 1").build();
        panel.updateTrack(updatedT1);
        assertThat(panel.getTableModel().getTrackAt(0).getTitle()).isEqualTo("Updated Track 1");
    }

    @Test
    @DisplayName("setTracks safely handles empty lists and null")
    void testSetTracksNullAndEmpty() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder().filePath("C:/1.mp3").title("Song").build();
        panel.setTracks(List.of(t1));
        assertThat(panel.getTrackCount()).isEqualTo(1);

        panel.setTracks(Collections.emptyList());
        assertThat(panel.getTrackCount()).isEqualTo(0);

        panel.setTracks(null);
        assertThat(panel.getTrackCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Single and multi-row selection retrieval works correctly")
    void testSelection() {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().filePath("C:/1.mp3").title("Song 1").build();
        Track t2 = Track.builder().filePath("C:/2.mp3").title("Song 2").build();
        Track t3 = Track.builder().filePath("C:/3.mp3").title("Song 3").build();
        panel.setTracks(List.of(t1, t2, t3));

        // Single selection
        panel.getTable().setRowSelectionInterval(1, 1);
        assertThat(panel.getSelectedTrack()).isEqualTo(t2);
        assertThat(panel.getSelectedTracks()).containsExactly(t2);

        // Multi selection (rows 0 and 2)
        panel.getTable().clearSelection();
        panel.getTable().addRowSelectionInterval(0, 0);
        panel.getTable().addRowSelectionInterval(2, 2);
        assertThat(panel.getSelectedTracks()).containsExactlyInAnyOrder(t1, t3);

        // Clear selection
        panel.getTable().clearSelection();
        assertThat(panel.getSelectedTrack()).isNull();
        assertThat(panel.getSelectedTracks()).isEmpty();
    }

    @Test
    @DisplayName("updateLanguage updates all 9 column header names and context menu items in Japanese and English")
    void testUpdateLanguage() {
        TrackTablePanel panel = new TrackTablePanel();

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getTable().getColumnModel().getColumn(0).getHeaderValue()).isEqualTo("");
        assertThat(panel.getTable().getColumnModel().getColumn(1).getHeaderValue()).isEqualTo("トラック番号");
        assertThat(panel.getTable().getColumnModel().getColumn(2).getHeaderValue()).isEqualTo("タイトル");
        assertThat(panel.getTable().getColumnModel().getColumn(3).getHeaderValue()).isEqualTo("アーティスト");
        assertThat(panel.getTable().getColumnModel().getColumn(4).getHeaderValue()).isEqualTo("アルバム");
        assertThat(panel.getTable().getColumnModel().getColumn(5).getHeaderValue()).isEqualTo("時間");
        assertThat(panel.getTable().getColumnModel().getColumn(6).getHeaderValue()).isEqualTo("ジャンル");
        assertThat(panel.getTable().getColumnModel().getColumn(7).getHeaderValue()).isEqualTo("リリース年");
        assertThat(panel.getTable().getColumnModel().getColumn(8).getHeaderValue()).isEqualTo("レート");

        // Context menu item texts
        assertThat(panel.getContextMenu().getComponent(0)).isInstanceOf(javax.swing.JMenuItem.class);
        javax.swing.JMenuItem playItem = (javax.swing.JMenuItem) panel.getContextMenu().getComponent(0);
        assertThat(playItem.getText()).isEqualTo("再生");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getTable().getColumnModel().getColumn(0).getHeaderValue()).isEqualTo("");
        assertThat(panel.getTable().getColumnModel().getColumn(1).getHeaderValue()).isEqualTo("#");
        assertThat(panel.getTable().getColumnModel().getColumn(2).getHeaderValue()).isEqualTo("Title");
        assertThat(playItem.getText()).isEqualTo("Play");
    }

    @Test
    @DisplayName("Context menu actions trigger registered callbacks with selected track")
    void testContextMenuCallbacks() {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().id("1").filePath("C:/1.mp3").title("Song 1").build();
        panel.setTracks(List.of(t1));
        panel.getTable().setRowSelectionInterval(0, 0);

        AtomicReference<Track> playedTrack = new AtomicReference<>();
        AtomicReference<Track> playedNextTrack = new AtomicReference<>();
        AtomicReference<Track> infoTrack = new AtomicReference<>();
        AtomicReference<Track> deletedTrack = new AtomicReference<>();

        panel.setOnPlayAction(playedTrack::set);
        panel.setOnPlayNextAction(playedNextTrack::set);
        panel.setOnGetInfoAction(infoTrack::set);
        panel.setOnDeleteAction(deletedTrack::set);

        // 1. Play
        panel.getPlayMenuItem().doClick();
        assertThat(playedTrack.get()).isEqualTo(t1);

        // 2. Play Next
        panel.getPlayNextMenuItem().doClick();
        assertThat(playedNextTrack.get()).isEqualTo(t1);

        // 3. Get Info
        panel.getGetInfoMenuItem().doClick();
        assertThat(infoTrack.get()).isEqualTo(t1);

        // 4. Delete
        panel.getDeleteMenuItem().doClick();
        assertThat(deletedTrack.get()).isEqualTo(t1);
    }

    @Test
    @DisplayName("Duration and rating flyweight cell renderers handle comprehensive boundary values")
    void testFlyweightRendererBoundaries() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t = Track.builder()
                .filePath("C:/t.mp3")
                .trackNumber(100)
                .durationSeconds(59) // 00:59
                .rating(1)           // ★
                .year(2024)
                .build();
        panel.setTracks(List.of(t));

        // 59 seconds -> 00:59
        JLabel dur59 = (JLabel) panel.getTable().getCellRenderer(0, 5)
                .getTableCellRendererComponent(panel.getTable(), 59L, false, false, 0, 5);
        assertThat(dur59.getText()).isEqualTo("00:59");

        // 60 seconds -> 01:00
        JLabel dur60 = (JLabel) panel.getTable().getCellRenderer(0, 5)
                .getTableCellRendererComponent(panel.getTable(), 60L, false, false, 0, 5);
        assertThat(dur60.getText()).isEqualTo("01:00");

        // 605 seconds -> 10:05
        JLabel dur605 = (JLabel) panel.getTable().getCellRenderer(0, 5)
                .getTableCellRendererComponent(panel.getTable(), 605L, false, false, 0, 5);
        assertThat(dur605.getText()).isEqualTo("10:05");

        // 3661 seconds -> 01:01:01 (TimeFormatter format)
        JLabel dur3661 = (JLabel) panel.getTable().getCellRenderer(0, 5)
                .getTableCellRendererComponent(panel.getTable(), 3661L, false, false, 0, 5);
        assertThat(dur3661.getText()).isEqualTo("01:01:01");

        // Star ratings 1 to 5, and boundaries
        JLabel r1 = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), 1, false, false, 0, 8);
        assertThat(r1.getText()).isEqualTo("★");

        JLabel r2 = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), 2, false, false, 0, 8);
        assertThat(r2.getText()).isEqualTo("★★");

        JLabel r3 = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), 3, false, false, 0, 8);
        assertThat(r3.getText()).isEqualTo("★★★");

        JLabel r4 = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), 4, false, false, 0, 8);
        assertThat(r4.getText()).isEqualTo("★★★★");

        JLabel r5 = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), 5, false, false, 0, 8);
        assertThat(r5.getText()).isEqualTo("★★★★★");

        JLabel rOut = (JLabel) panel.getTable().getCellRenderer(0, 8)
                .getTableCellRendererComponent(panel.getTable(), 99, false, false, 0, 8);
        assertThat(rOut.getText()).isEqualTo("—");
    }

    @Test
    @DisplayName("Double-clicking a row triggers onPlayAction with the selected track")
    void testDoubleClickTriggersPlayAction() {
        TrackTablePanel panel = new TrackTablePanel();
        Track track = Track.builder().id("1").title("Double Click Song").filePath("song.mp3").build();
        panel.setTracks(List.of(track));
        panel.getTable().setRowSelectionInterval(0, 0);

        AtomicReference<Track> playedTrack = new AtomicReference<>();
        panel.setOnPlayAction(playedTrack::set);

        // Dispatch double click event
        java.awt.event.MouseEvent doubleClick = new java.awt.event.MouseEvent(
                panel.getTable(),
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                5, 5, // point in first row
                2,    // clickCount = 2
                false,
                java.awt.event.MouseEvent.BUTTON1
        );
        panel.getTable().dispatchEvent(doubleClick);

        assertThat(playedTrack.get()).isSameAs(track);
    }

    @Test
    @DisplayName("Pressing Enter key triggers onPlayAction with the selected track via ActionMap and KeyListener")
    void testEnterKeyTriggersPlayAction() {
        TrackTablePanel panel = new TrackTablePanel();
        Track track = Track.builder().id("2").title("Enter Key Song").filePath("enter.mp3").build();
        panel.setTracks(List.of(track));
        panel.getTable().setRowSelectionInterval(0, 0);

        AtomicReference<Track> playedTrack = new AtomicReference<>();
        panel.setOnPlayAction(playedTrack::set);

        // 1. Verify via JTable ActionMap bound to Enter KeyStroke
        javax.swing.Action playAction = panel.getTable().getActionMap().get("playSelectedTrack");
        assertThat(playAction).isNotNull();
        playAction.actionPerformed(new java.awt.event.ActionEvent(panel.getTable(), java.awt.event.ActionEvent.ACTION_PERFORMED, "playSelectedTrack"));
        assertThat(playedTrack.get()).isSameAs(track);

        // 2. Verify via KeyListener keyPressed
        playedTrack.set(null);
        java.awt.event.KeyEvent enterKey = new java.awt.event.KeyEvent(
                panel.getTable(),
                java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                java.awt.event.KeyEvent.VK_ENTER,
                '\n'
        );
        for (java.awt.event.KeyListener l : panel.getTable().getKeyListeners()) {
            l.keyPressed(enterKey);
        }
        assertThat(playedTrack.get()).isSameAs(track);

        // 3. Direct playSelectedTrack
        playedTrack.set(null);
        panel.playSelectedTrack();
        assertThat(playedTrack.get()).isSameAs(track);
    }

    @Test
    @DisplayName("Double-click ignores right clicks, single clicks, empty space clicks, and handles null action safely")
    void testDoubleClickEdgeCases() {
        TrackTablePanel panel = new TrackTablePanel();
        Track track = Track.builder().id("1").title("Song").filePath("song.mp3").build();
        panel.setTracks(List.of(track));
        panel.getTable().setRowSelectionInterval(0, 0);

        AtomicReference<Track> playedTrack = new AtomicReference<>();
        panel.setOnPlayAction(playedTrack::set);

        // 1. Right-click double-click (BUTTON3) should NOT trigger onPlayAction
        java.awt.event.MouseEvent rightDoubleClick = new java.awt.event.MouseEvent(
                panel.getTable(),
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                5, 5,
                2,
                false,
                java.awt.event.MouseEvent.BUTTON3
        );
        panel.getTable().dispatchEvent(rightDoubleClick);
        assertThat(playedTrack.get()).isNull();

        // 2. Middle-click double-click (BUTTON2) should NOT trigger onPlayAction
        java.awt.event.MouseEvent middleDoubleClick = new java.awt.event.MouseEvent(
                panel.getTable(),
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                5, 5,
                2,
                false,
                java.awt.event.MouseEvent.BUTTON2
        );
        panel.getTable().dispatchEvent(middleDoubleClick);
        assertThat(playedTrack.get()).isNull();

        // 3. Left-click single click (clickCount = 1) should NOT trigger onPlayAction
        java.awt.event.MouseEvent leftSingleClick = new java.awt.event.MouseEvent(
                panel.getTable(),
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                5, 5,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1
        );
        panel.getTable().dispatchEvent(leftSingleClick);
        assertThat(playedTrack.get()).isNull();

        // 4. Double-click on empty space outside rows (e.g. y = 500 when table height is small)
        java.awt.event.MouseEvent emptySpaceDoubleClick = new java.awt.event.MouseEvent(
                panel.getTable(),
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                5, 500, // point outside any row
                2,
                false,
                java.awt.event.MouseEvent.BUTTON1
        );
        assertThat(panel.getTable().rowAtPoint(emptySpaceDoubleClick.getPoint())).isEqualTo(-1);
        panel.getTable().dispatchEvent(emptySpaceDoubleClick);
        assertThat(playedTrack.get()).isNull();

        // 5. Left-click double click when onPlayAction is null should be safe no-op
        panel.setOnPlayAction(null);
        java.awt.event.MouseEvent validLeftDoubleClick = new java.awt.event.MouseEvent(
                panel.getTable(),
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                5, 5,
                2,
                false,
                java.awt.event.MouseEvent.BUTTON1
        );
        org.assertj.core.api.Assertions.assertThatCode(() -> {
            panel.getTable().dispatchEvent(validLeftDoubleClick);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Enter key input safely ignores invocation when no row is selected or model is empty")
    void testEnterKeyWithNoSelectionSafelyNoOp() {
        TrackTablePanel panel = new TrackTablePanel();
        Track track = Track.builder().id("1").title("Song").filePath("song.mp3").build();
        panel.setTracks(List.of(track));

        AtomicReference<Track> playedTrack = new AtomicReference<>();
        panel.setOnPlayAction(playedTrack::set);

        // Ensure no row is selected
        panel.getTable().clearSelection();
        assertThat(panel.getSelectedTrack()).isNull();

        // 1. ActionMap trigger with no selection
        javax.swing.Action playAction = panel.getTable().getActionMap().get("playSelectedTrack");
        assertThat(playAction).isNotNull();
        playAction.actionPerformed(new java.awt.event.ActionEvent(panel.getTable(), java.awt.event.ActionEvent.ACTION_PERFORMED, "playSelectedTrack"));
        assertThat(playedTrack.get()).isNull();

        // 2. KeyListener trigger with no selection
        java.awt.event.KeyEvent enterKey = new java.awt.event.KeyEvent(
                panel.getTable(),
                java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                java.awt.event.KeyEvent.VK_ENTER,
                '\n'
        );
        for (java.awt.event.KeyListener l : panel.getTable().getKeyListeners()) {
            l.keyPressed(enterKey);
        }
        assertThat(playedTrack.get()).isNull();

        // 3. Empty table trigger
        panel.clear();
        panel.playSelectedTrack();
        assertThat(playedTrack.get()).isNull();

        // 4. Null callback trigger
        panel.setOnPlayAction(null);
        org.assertj.core.api.Assertions.assertThatCode(panel::playSelectedTrack).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Drag and drop is enabled on table and TransferHandler exports selected track IDs")
    void testDragAndDropExportConfiguration() throws Exception {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().id("t-1").title("Song 1").filePath("s1.mp3").build();
        Track t2 = Track.builder().id("t-2").title("Song 2").filePath("s2.mp3").build();
        panel.setTracks(List.of(t1, t2));

        assertThat(panel.getTable().getDragEnabled()).isTrue();
        javax.swing.TransferHandler handler = panel.getTable().getTransferHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.getSourceActions(panel.getTable())).isEqualTo(javax.swing.TransferHandler.COPY);

        // 1. When nothing selected: exportToClipboard does not place data on clipboard
        panel.getTable().clearSelection();
        java.awt.datatransfer.Clipboard emptyClipboard = new java.awt.datatransfer.Clipboard("empty-clipboard");
        handler.exportToClipboard(panel.getTable(), emptyClipboard, javax.swing.TransferHandler.COPY);
        assertThat(emptyClipboard.getContents(null)).isNull();

        // 2. When t1 and t2 are selected: exportToClipboard places TrackTransferData containing both IDs
        panel.getTable().setRowSelectionInterval(0, 1);
        java.awt.datatransfer.Clipboard activeClipboard = new java.awt.datatransfer.Clipboard("active-clipboard");
        handler.exportToClipboard(panel.getTable(), activeClipboard, javax.swing.TransferHandler.COPY);

        java.awt.datatransfer.Transferable transferable = activeClipboard.getContents(null);
        assertThat(transferable).isNotNull();
        assertThat(transferable).isInstanceOf(com.musiclibrary.ui.dnd.TrackTransferData.class);

        List<String> ids = com.musiclibrary.ui.dnd.TrackTransferData.extractTrackIds(transferable);
        assertThat(ids).containsExactly("t-1", "t-2");
    }

    @Test
    @DisplayName("Delete key actions trigger onDeleteTracksAction callback with all selected tracks")
    void testDeleteKeyActionsTriggerCallback() {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().id("del-1").title("Song 1").filePath("1.mp3").build();
        Track t2 = Track.builder().id("del-2").title("Song 2").filePath("2.mp3").build();
        panel.setTracks(List.of(t1, t2));
        panel.getTable().setRowSelectionInterval(0, 1);

        java.util.concurrent.atomic.AtomicReference<List<Track>> deletedTracks = new java.util.concurrent.atomic.AtomicReference<>();
        panel.setOnDeleteTracksAction(deletedTracks::set);

        // 1. ActionMap bound to VK_DELETE
        javax.swing.Action delAction = panel.getTable().getActionMap().get("deleteSelectedTracks");
        assertThat(delAction).isNotNull();
        delAction.actionPerformed(new java.awt.event.ActionEvent(panel.getTable(), java.awt.event.ActionEvent.ACTION_PERFORMED, "deleteSelectedTracks"));
        assertThat(deletedTracks.get()).containsExactly(t1, t2);

        // 2. KeyListener keyPressed with VK_DELETE
        deletedTracks.set(null);
        java.awt.event.KeyEvent deleteKey = new java.awt.event.KeyEvent(
                panel.getTable(),
                java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                0,
                java.awt.event.KeyEvent.VK_DELETE,
                '\0'
        );
        for (java.awt.event.KeyListener l : panel.getTable().getKeyListeners()) {
            l.keyPressed(deleteKey);
        }
        assertThat(deletedTracks.get()).containsExactly(t1, t2);
    }

    @Test
    @DisplayName("triggerDeleteAction falls back to onDeleteAction when onDeleteTracksAction is null")
    void testTriggerDeleteActionFallbackToSingleTrackAction() {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().id("single-del").title("Song 1").filePath("1.mp3").build();
        panel.setTracks(List.of(t1));
        panel.getTable().setRowSelectionInterval(0, 0);

        java.util.concurrent.atomic.AtomicReference<Track> singleDeleted = new java.util.concurrent.atomic.AtomicReference<>();
        panel.setOnDeleteTracksAction(null);
        panel.setOnDeleteAction(singleDeleted::set);

        panel.triggerDeleteAction();
        assertThat(singleDeleted.get()).isEqualTo(t1);

        // When both callbacks are null: safe no-op
        panel.setOnDeleteAction(null);
        org.assertj.core.api.Assertions.assertThatCode(panel::triggerDeleteAction).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setPlaylistContext updates isPlaylistContext state and toggles context menu delete label")
    void testPlaylistContextLabelSwitching() {
        TrackTablePanel panel = new TrackTablePanel();
        assertThat(panel.isPlaylistContext()).isFalse();

        // 1. Default library context: "Delete from Library" in English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();
        javax.swing.JMenuItem deleteMenuItem = panel.getDeleteMenuItem();
        assertThat(deleteMenuItem.getText()).isEqualTo("Delete from Library");

        // 2. Switch to playlist context: "Remove from Playlist"
        panel.setPlaylistContext(true);
        assertThat(panel.isPlaylistContext()).isTrue();
        assertThat(deleteMenuItem.getText()).isEqualTo("Remove from Playlist");

        // 3. Switch language to Japanese while in playlist context: "プレイリストから除外"
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(deleteMenuItem.getText()).isEqualTo("プレイリストから除外");

        // 4. Switch back to library context: "ライブラリから削除"
        panel.setPlaylistContext(false);
        assertThat(deleteMenuItem.getText()).isEqualTo("ライブラリから削除");
    }

    @Test
    @DisplayName("Edit tags menu item triggers onEditTagsAction with selected tracks")
    void testEditTagsContextMenu() {
        TrackTablePanel panel = new TrackTablePanel();
        Track t1 = Track.builder().id("1").title("Track 1").filePath("1.mp3").build();
        Track t2 = Track.builder().id("2").title("Track 2").filePath("2.mp3").build();
        panel.setTracks(List.of(t1, t2));
        panel.getTable().setRowSelectionInterval(0, 1);

        java.util.concurrent.atomic.AtomicReference<List<Track>> editedRef = new java.util.concurrent.atomic.AtomicReference<>();
        panel.setOnEditTagsAction(editedRef::set);

        // Verify English text
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();
        assertThat(panel.getEditTagsMenuItem().getText()).isEqualTo("Edit Tags...");

        // Trigger via menu item
        panel.getEditTagsMenuItem().doClick();
        assertThat(editedRef.get()).containsExactly(t1, t2);

        // Verify Japanese text
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getEditTagsMenuItem().getText()).isEqualTo("タグを編集...");
    }

    @Test
    @DisplayName("TitleCellRenderer shows warning icon, color and tooltip for missing tracks")
    void testMissingTrackRendering() {
        TrackTablePanel panel = new TrackTablePanel();
        Track normalTrack = Track.builder().id("1").title("Normal Song").filePath("C:/music/1.mp3").build();
        Track missingTrack = Track.builder().id("2").title("Missing Song").filePath("C:/music/2.mp3").build();
        missingTrack.setMissing(true);

        panel.setTracks(List.of(normalTrack, missingTrack));

        // Column 2 is Title column (unselected)
        JLabel normalLabel = (JLabel) panel.getTable().getCellRenderer(0, 2)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 2), false, false, 0, 2);
        assertThat(normalLabel.getText()).isEqualTo("Normal Song");
        assertThat(normalLabel.getToolTipText()).isNull();

        JLabel missingLabel = (JLabel) panel.getTable().getCellRenderer(1, 2)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 2), false, false, 1, 2);
        assertThat(missingLabel.getText()).isEqualTo("⚠️ Missing Song");
        assertThat(missingLabel.getToolTipText()).isEqualTo(I18nManager.getInstance().get("missing.warning_tooltip"));
        assertThat(missingLabel.getForeground()).isEqualTo(new java.awt.Color(220, 80, 80));

        // When row is selected, renderer does not override selection color
        JLabel missingSelectedLabel = (JLabel) panel.getTable().getCellRenderer(1, 2)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 2), true, false, 1, 2);
        assertThat(missingSelectedLabel.getText()).isEqualTo("⚠️ Missing Song");
        assertThat(missingSelectedLabel.getToolTipText()).isEqualTo(I18nManager.getInstance().get("missing.warning_tooltip"));

        // updateMissingTracks triggers repaint and reflects state
        missingTrack.setMissing(false);
        panel.updateMissingTracks(java.util.Collections.emptyList());
        JLabel resolvedLabel = (JLabel) panel.getTable().getCellRenderer(1, 2)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 2), false, false, 1, 2);
        assertThat(resolvedLabel.getText()).isEqualTo("Missing Song");
        assertThat(resolvedLabel.getToolTipText()).isNull();

        // Null safety test
        org.assertj.core.api.Assertions.assertThatCode(() -> panel.updateMissingTracks(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TitleCellRenderer correctly resolves model row when table is sorted")
    void testMissingTrackRenderingUnderTableSorting() {
        TrackTablePanel panel = new TrackTablePanel();
        Track trackA = Track.builder().id("a").title("A Song (Normal)").filePath("C:/music/a.mp3").build();
        Track trackZ = Track.builder().id("z").title("Z Song (Missing)").filePath("C:/music/z.mp3").build();
        trackZ.setMissing(true);

        // Initial order: trackA at index 0, trackZ at index 1
        panel.setTracks(List.of(trackA, trackZ));

        // Sort by Title column descending: row 0 becomes "Z Song (Missing)", row 1 becomes "A Song (Normal)"
        javax.swing.RowSorter<?> sorter = panel.getTable().getRowSorter();
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE, javax.swing.SortOrder.DESCENDING)));

        // View Row 0 is now trackZ (Missing)
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Z Song (Missing)");
        JLabel viewRow0Label = (JLabel) panel.getTable().getCellRenderer(0, 2)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 2), false, false, 0, 2);
        assertThat(viewRow0Label.getText()).isEqualTo("⚠️ Z Song (Missing)");
        assertThat(viewRow0Label.getToolTipText()).isEqualTo(I18nManager.getInstance().get("missing.warning_tooltip"));

        // View Row 1 is now trackA (Normal)
        assertThat(panel.getTable().getValueAt(1, 2)).isEqualTo("A Song (Normal)");
        JLabel viewRow1Label = (JLabel) panel.getTable().getCellRenderer(1, 2)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 2), false, false, 1, 2);
        assertThat(viewRow1Label.getText()).isEqualTo("A Song (Normal)");
        assertThat(viewRow1Label.getToolTipText()).isNull();
    }

    @Test
    @DisplayName("updateTrack updates table model and visual cells across all columns when metadata is edited")
    void testUpdateTrackReflectsMetadataChangesInTable() {
        TrackTablePanel panel = new TrackTablePanel();
        Track original = Track.builder()
                .id("tr-10")
                .filePath("C:/music/10.mp3")
                .trackNumber(1)
                .title("Original Title")
                .artist("Original Artist")
                .album("Original Album")
                .durationSeconds(200)
                .genre("Old Genre")
                .year(2000)
                .rating(3)
                .build();

        panel.setTracks(List.of(original));

        assertThat(panel.getTable().getValueAt(0, 0)).isEqualTo("");
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo(1);
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Original Title");
        assertThat(panel.getTable().getValueAt(0, 3)).isEqualTo("Original Artist");
        assertThat(panel.getTable().getValueAt(0, 4)).isEqualTo("Original Album");
        assertThat(panel.getTable().getValueAt(0, 6)).isEqualTo("Old Genre");
        assertThat(panel.getTable().getValueAt(0, 7)).isEqualTo(2000);

        // Updated track
        Track updated = original.toBuilder()
                .trackNumber(9)
                .title("Updated Title")
                .artist("Updated Artist")
                .album("Updated Album")
                .genre("Modern Rock")
                .year(2025)
                .build();

        panel.updateTrack(updated);

        // Verify updated table cells
        assertThat(panel.getTable().getValueAt(0, 0)).isEqualTo("");
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo(9);
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Updated Title");
        assertThat(panel.getTable().getValueAt(0, 3)).isEqualTo("Updated Artist");
        assertThat(panel.getTable().getValueAt(0, 4)).isEqualTo("Updated Album");
        assertThat(panel.getTable().getValueAt(0, 6)).isEqualTo("Modern Rock");
        assertThat(panel.getTable().getValueAt(0, 7)).isEqualTo(2025);
    }

    @Test
    @DisplayName("getVisibleTracks and getVisibleSummary return accurate tracks and aggregate metrics")
    void testVisibleTracksAndSummary() {
        TrackTablePanel panel = new TrackTablePanel();

        // Empty state
        assertThat(panel.getVisibleTracks()).isEmpty();
        TrackTablePanel.TableSummary emptySummary = panel.getVisibleSummary();
        assertThat(emptySummary.trackCount()).isEqualTo(0);
        assertThat(emptySummary.totalDurationSeconds()).isEqualTo(0L);
        assertThat(emptySummary.totalFileSizeBytes()).isEqualTo(0L);

        // Populate tracks
        Track t1 = Track.builder()
                .id("tr-1")
                .title("Song A")
                .artist("Artist 1")
                .durationSeconds(120)
                .fileSizeBytes(1000L)
                .filePath("C:/music/1.mp3")
                .build();
        Track t2 = Track.builder()
                .id("tr-2")
                .title("Song B")
                .artist("Artist 2")
                .durationSeconds(180)
                .fileSizeBytes(2000L)
                .filePath("C:/music/2.mp3")
                .build();

        panel.setTracks(List.of(t1, t2));

        List<Track> visible = panel.getVisibleTracks();
        assertThat(visible).containsExactly(t1, t2);

        TrackTablePanel.TableSummary summary = panel.getVisibleSummary();
        assertThat(summary.trackCount()).isEqualTo(2);
        assertThat(summary.totalDurationSeconds()).isEqualTo(300L);
        assertThat(summary.totalFileSizeBytes()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("Column visibility toggle via setColumnVisible and header menu preserves column ordering and mandatory title")
    void testColumnVisibilityToggleAndHeaderMenu() {
        TrackTablePanel panel = new TrackTablePanel();

        // 1. Initial default columns: exactly 9 columns
        assertThat(panel.getTable().getColumnCount()).isEqualTo(9);
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ARTIST)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ALBUM)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DURATION)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_GENRE)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_YEAR)).isTrue();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_RATING)).isTrue();

        // Non-default columns initially hidden
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST)).isFalse();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DISC_NUMBER)).isFalse();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BPM)).isFalse();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BITRATE)).isFalse();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DATE_ADDED)).isFalse();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_PLAY_COUNT)).isFalse();

        // 2. Header popup menu verification
        assertThat(panel.getHeaderPopupMenu()).isNotNull();
        assertThat(panel.getColumnMenuItems()).hasSize(15);
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS]).isNull();
        javax.swing.JCheckBoxMenuItem titleItem = panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE];
        assertThat(titleItem.isEnabled()).isFalse();
        assertThat(titleItem.isSelected()).isTrue();

        // 3. Mandatory Status and Title columns cannot be hidden
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS, false);
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS)).isTrue();
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE, false);
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE)).isTrue();
        assertThat(panel.getTable().getColumnCount()).isEqualTo(9);

        // 4. Toggle Album Artist (modelIndex 4) -> should insert between Artist (3) and Album (5)
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST, true);
        assertThat(panel.getTable().getColumnCount()).isEqualTo(10);
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST)).isTrue();
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 14);

        // 5. Toggle BPM (10) and Bitrate (11) -> ordered insertion
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BPM, true);
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BITRATE, true);
        assertThat(panel.getTable().getColumnCount()).isEqualTo(12);
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 14);

        // 6. Hide Track Number (1)
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER, false);
        assertThat(panel.getTable().getColumnCount()).isEqualTo(11);
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER)).isFalse();
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 2, 3, 4, 5, 6, 7, 8, 10, 11, 14);

        // 7. Hide BPM (10) again
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BPM, false);
        assertThat(panel.getTable().getColumnCount()).isEqualTo(10);
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 2, 3, 4, 5, 6, 7, 8, 11, 14);

        // 8. Re-enable Track Number (1) -> goes back to position right after Status (0)
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER, true);
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 14);
    }

    @Test
    @DisplayName("New column renderers (Bitrate, DateAdded, PlayCount, BPM, DiscNumber) format values cleanly")
    void testNewColumnRenderersFormatting() {
        TrackTablePanel panel = new TrackTablePanel();

        // Enable all newly added columns
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DISC_NUMBER, true);
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BPM, true);
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BITRATE, true);
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DATE_ADDED, true);
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_PLAY_COUNT, true);

        java.time.Instant date = java.time.Instant.parse("2026-10-14T10:00:00Z");
        String formattedDate = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                .withZone(java.time.ZoneId.systemDefault())
                .format(date);

        Track t1 = Track.builder()
                .filePath("C:/music/track1.mp3")
                .discNumber(2)
                .bpm(128)
                .bitrate(320)
                .dateAdded(date)
                .playCount(42)
                .build();

        Track t2 = Track.builder()
                .filePath("C:/music/track2.mp3")
                .discNumber(0)
                .bpm(0)
                .bitrate(0)
                .dateAdded(null)
                .playCount(0)
                .build();

        panel.setTracks(List.of(t1, t2));

        // Locate view column indices for the new columns
        int discViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DISC_NUMBER);
        int bpmViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BPM);
        int bitrateViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BITRATE);
        int dateViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DATE_ADDED);
        int playCountViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_PLAY_COUNT);

        // Row 1 values
        JLabel discLabel1 = (JLabel) panel.getTable().getCellRenderer(0, discViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, discViewCol), false, false, 0, discViewCol);
        assertThat(discLabel1.getText()).isEqualTo("2");

        JLabel bpmLabel1 = (JLabel) panel.getTable().getCellRenderer(0, bpmViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, bpmViewCol), false, false, 0, bpmViewCol);
        assertThat(bpmLabel1.getText()).isEqualTo("128");

        JLabel bitrateLabel1 = (JLabel) panel.getTable().getCellRenderer(0, bitrateViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, bitrateViewCol), false, false, 0, bitrateViewCol);
        assertThat(bitrateLabel1.getText()).isEqualTo("320 kbps");

        JLabel dateLabel1 = (JLabel) panel.getTable().getCellRenderer(0, dateViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, dateViewCol), false, false, 0, dateViewCol);
        assertThat(dateLabel1.getText()).isEqualTo(formattedDate);

        JLabel playsLabel1 = (JLabel) panel.getTable().getCellRenderer(0, playCountViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, playCountViewCol), false, false, 0, playCountViewCol);
        assertThat(playsLabel1.getText()).isEqualTo("42");

        // Row 2 boundary/zero values
        JLabel discLabel2 = (JLabel) panel.getTable().getCellRenderer(1, discViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, discViewCol), false, false, 1, discViewCol);
        assertThat(discLabel2.getText()).isEqualTo("-");

        JLabel bpmLabel2 = (JLabel) panel.getTable().getCellRenderer(1, bpmViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, bpmViewCol), false, false, 1, bpmViewCol);
        assertThat(bpmLabel2.getText()).isEqualTo("-");

        JLabel bitrateLabel2 = (JLabel) panel.getTable().getCellRenderer(1, bitrateViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, bitrateViewCol), false, false, 1, bitrateViewCol);
        assertThat(bitrateLabel2.getText()).isEqualTo("-");

        JLabel dateLabel2 = (JLabel) panel.getTable().getCellRenderer(1, dateViewCol)
                .getTableCellRendererComponent(panel.getTable(), null, false, false, 1, dateViewCol);
        assertThat(dateLabel2.getText()).isEqualTo("-");

        JLabel playsLabel2 = (JLabel) panel.getTable().getCellRenderer(1, playCountViewCol)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, playCountViewCol), false, false, 1, playCountViewCol);
        assertThat(playsLabel2.getText()).isEqualTo("0");
    }

    @Test
    @DisplayName("Header context menu items reflect language change properly")
    void testHeaderContextMenuLanguageUpdate() {
        TrackTablePanel panel = new TrackTablePanel();

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BPM].getText()).isEqualTo("BPM");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST].getText()).isEqualTo("アルバムアーティスト");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BITRATE].getText()).isEqualTo("ビットレート");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DATE_ADDED].getText()).isEqualTo("追加日");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_PLAY_COUNT].getText()).isEqualTo("再生回数");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DISC_NUMBER].getText()).isEqualTo("ディスク番号");

        // Switch to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST].getText()).isEqualTo("Album Artist");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_BITRATE].getText()).isEqualTo("Bitrate");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DATE_ADDED].getText()).isEqualTo("Date Added");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_PLAY_COUNT].getText()).isEqualTo("Plays");
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_DISC_NUMBER].getText()).isEqualTo("Disc #");
    }

    @Test
    @DisplayName("All 14 columns visible and minimal (Title only) view both render and handle selection and sorting cleanly")
    void testAllColumnsVisibleAndMinimalColumnsSelection() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder()
                .id("t1")
                .filePath("C:/music/1.mp3")
                .trackNumber(1)
                .title("Alpha")
                .artist("Artist A")
                .albumArtist("Album Artist A")
                .album("Album A")
                .durationSeconds(200)
                .genre("Rock")
                .year(2020)
                .discNumber(1)
                .bpm(120)
                .bitrate(320)
                .playCount(10)
                .rating(5)
                .build();

        Track t2 = Track.builder()
                .id("t2")
                .filePath("C:/music/2.mp3")
                .trackNumber(2)
                .title("Beta")
                .artist("Artist B")
                .albumArtist("Album Artist B")
                .album("Album B")
                .durationSeconds(150)
                .genre("Pop")
                .year(2022)
                .discNumber(2)
                .bpm(90)
                .bitrate(256)
                .playCount(5)
                .rating(3)
                .build();

        panel.setTracks(List.of(t1, t2));

        // --- 1. Enable ALL 15 columns ---
        for (int i = 0; i < 15; i++) {
            panel.setColumnVisible(i, true);
        }
        assertThat(panel.getTable().getColumnCount()).isEqualTo(15);
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);

        // Verify selection on 15-column view
        panel.getTable().setRowSelectionInterval(1, 1);
        assertThat(panel.getSelectedTrack()).isEqualTo(t2);
        assertThat(panel.getSelectedTracks()).containsExactly(t2);

        // Verify all renderers render without throwing
        for (int c = 0; c < 15; c++) {
            panel.getTable().getCellRenderer(0, c)
                    .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, c), false, false, 0, c);
        }

        // --- 2. Minimal View: Hide ALL columns except mandatory Status (0) and Title (2) ---
        for (int i = 0; i < 15; i++) {
            if (i != com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS
                    && i != com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE) {
                panel.setColumnVisible(i, false);
            }
        }
        assertThat(panel.getTable().getColumnCount()).isEqualTo(2);
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(
                com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS,
                com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE
        );
        assertThat(panel.getTable().getColumnModel().getColumn(0).getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS);
        assertThat(panel.getTable().getColumnModel().getColumn(1).getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE);

        // Selection still works on minimal view
        panel.getTable().setRowSelectionInterval(0, 0);
        assertThat(panel.getSelectedTrack()).isEqualTo(t1);

        // Render Title column safely
        Object titleValue = panel.getTable().getValueAt(0, 1);
        assertThat(titleValue).isEqualTo("Alpha");
        panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), titleValue, false, false, 0, 1);
    }

    @Test
    @DisplayName("Random column toggle and re-enable always restores exact model index ordering")
    void testRandomColumnToggleOrderPreservation() {
        TrackTablePanel panel = new TrackTablePanel();

        // Start by enabling ALL 15 columns
        for (int i = 0; i < 15; i++) {
            panel.setColumnVisible(i, true);
        }
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);

        // Arbitrarily hide a set of columns in non-sequential order: 9, 4, 12, 1, 14, 6
        panel.setColumnVisible(9, false);
        panel.setColumnVisible(4, false);
        panel.setColumnVisible(12, false);
        panel.setColumnVisible(1, false);
        panel.setColumnVisible(14, false);
        panel.setColumnVisible(6, false);

        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 2, 3, 5, 7, 8, 10, 11, 13);

        // Re-enable in completely different reversed/shuffled order: 14, 1, 9, 6, 12, 4
        panel.setColumnVisible(14, true);
        panel.setColumnVisible(1, true);
        panel.setColumnVisible(9, true);
        panel.setColumnVisible(6, true);
        panel.setColumnVisible(12, true);
        panel.setColumnVisible(4, true);

        // Exact original natural model order must be fully restored
        assertThat(panel.getVisibleColumnModelIndices()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
    }

    @Test
    @DisplayName("Simulated doClick on header checkbox menu items toggles visibility and Title item remains disabled")
    void testHeaderMenuItemDoClick() {
        TrackTablePanel panel = new TrackTablePanel();

        // Genre is initially visible
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_GENRE)).isTrue();
        javax.swing.JCheckBoxMenuItem genreItem = panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_GENRE];
        assertThat(genreItem.isSelected()).isTrue();

        // Simulate user clicking the menu item to hide Genre
        genreItem.doClick();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_GENRE)).isFalse();
        assertThat(genreItem.isSelected()).isFalse();

        // Click again to re-show Genre
        genreItem.doClick();
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_GENRE)).isTrue();
        assertThat(genreItem.isSelected()).isTrue();

        // Title item must be disabled and selected
        javax.swing.JCheckBoxMenuItem titleItem = panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE];
        assertThat(titleItem.isEnabled()).isFalse();
        assertThat(titleItem.isSelected()).isTrue();
    }

    @Test
    @DisplayName("Language toggle updates both table column headers and popup menu items simultaneously")
    void testLanguageToggleUpdatesBothHeadersAndMenuItems() {
        TrackTablePanel panel = new TrackTablePanel();

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        // Check header values on visible columns
        int titleViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE);
        assertThat(panel.getTable().getColumnModel().getColumn(titleViewCol).getHeaderValue()).isEqualTo("タイトル");

        int artistViewCol = panel.getTable().convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_ARTIST);
        assertThat(panel.getTable().getColumnModel().getColumn(artistViewCol).getHeaderValue()).isEqualTo("アーティスト");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getTable().getColumnModel().getColumn(titleViewCol).getHeaderValue()).isEqualTo("Title");
        assertThat(panel.getTable().getColumnModel().getColumn(artistViewCol).getHeaderValue()).isEqualTo("Artist");
    }

    @Test
    @DisplayName("Status cell renderer displays PlaybackStatusIcon when playing or paused while Track# column always displays track number")
    void testPlaybackStatusIndicatorRendering() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder().id("track-1").trackNumber(1).title("Song 1").filePath("1.mp3").build();
        Track t2 = Track.builder().id("track-2").trackNumber(2).title("Song 2").filePath("2.mp3").build();
        panel.setTracks(List.of(t1, t2));

        // 1. Initial STOPPED state:
        // Status column (col 0): empty
        JLabel statusLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(statusLabel1.getIcon()).isNull();
        assertThat(statusLabel1.getText()).isEmpty();

        // Track# column (col 1): "1"
        JLabel trackNumLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(trackNumLabel1.getIcon()).isNull();
        assertThat(trackNumLabel1.getText()).isEqualTo("1");

        // 2. PLAYING on track-1
        panel.setPlaybackStatus("track-1", com.musiclibrary.model.PlaybackState.PLAYING);
        assertThat(panel.getCurrentPlayingTrackId()).isEqualTo("track-1");
        assertThat(panel.getCurrentPlaybackState()).isEqualTo(com.musiclibrary.model.PlaybackState.PLAYING);

        statusLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(statusLabel1.getIcon()).isNotNull().isInstanceOf(com.musiclibrary.ui.table.PlaybackStatusIcon.class);
        assertThat(statusLabel1.getText()).isEmpty();
        assertThat(statusLabel1.getToolTipText()).isEqualTo("Playing");

        // Track# column (col 1) ALWAYS displays the track number even when playing
        trackNumLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(trackNumLabel1.getIcon()).isNull();
        assertThat(trackNumLabel1.getText()).isEqualTo("1");

        // Track 2 remains normal
        JLabel statusLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(statusLabel2.getIcon()).isNull();
        assertThat(statusLabel2.getText()).isEmpty();

        JLabel trackNumLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(trackNumLabel2.getIcon()).isNull();
        assertThat(trackNumLabel2.getText()).isEqualTo("2");

        // 3. PAUSED on track-1
        panel.setPlaybackStatus("track-1", com.musiclibrary.model.PlaybackState.PAUSED);
        statusLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(statusLabel1.getIcon()).isNotNull();
        assertThat(statusLabel1.getText()).isEmpty();
        assertThat(statusLabel1.getToolTipText()).isEqualTo("Paused");

        trackNumLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(trackNumLabel1.getText()).isEqualTo("1");

        // 4. STOPPED
        panel.setPlaybackStatus(null, com.musiclibrary.model.PlaybackState.STOPPED);
        statusLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(statusLabel1.getIcon()).isNull();
        assertThat(statusLabel1.getText()).isEmpty();

        trackNumLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(trackNumLabel1.getText()).isEqualTo("1");
    }

    @Test
    @DisplayName("Playback status indicator switches cleanly between Track A and Track B")
    void testPlaybackStatusTrackSwitching() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder().id("trk-a").trackNumber(1).title("Song A").filePath("a.mp3").build();
        Track t2 = Track.builder().id("trk-b").trackNumber(2).title("Song B").filePath("b.mp3").build();
        panel.setTracks(List.of(t1, t2));

        // Start playing Track A
        panel.setPlaybackStatus("trk-a", com.musiclibrary.model.PlaybackState.PLAYING);

        JLabel statusA = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(statusA.getIcon()).isNotNull();
        assertThat(statusA.getText()).isEmpty();

        JLabel numA = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(numA.getText()).isEqualTo("1");

        JLabel statusB = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(statusB.getIcon()).isNull();
        assertThat(statusB.getText()).isEmpty();

        JLabel numB = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(numB.getText()).isEqualTo("2");

        // Switch to playing Track B
        panel.setPlaybackStatus("trk-b", com.musiclibrary.model.PlaybackState.PLAYING);

        statusA = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(statusA.getIcon()).isNull();
        assertThat(statusA.getText()).isEmpty();

        statusB = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(statusB.getIcon()).isNotNull();
        assertThat(statusB.getText()).isEmpty();
    }

    @Test
    @DisplayName("Playback status icon targets correct track even when table rows are sorted")
    void testPlaybackStatusWithTableSorting() {
        TrackTablePanel panel = new TrackTablePanel();

        // t1 has Title "Zeta", t2 has Title "Alpha"
        Track t1 = Track.builder().id("trk-1").trackNumber(1).title("Zeta").filePath("z.mp3").build();
        Track t2 = Track.builder().id("trk-2").trackNumber(2).title("Alpha").filePath("a.mp3").build();
        panel.setTracks(List.of(t1, t2));

        // Sort by Title ascending (Model index 2)
        panel.applySortConfig(new com.musiclibrary.settings.UserPreferences.SortConfig(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TITLE, "ASCENDING"));

        // Now View Row 0 is t2 ("Alpha"), View Row 1 is t1 ("Zeta")
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Alpha");
        assertThat(panel.getTable().getValueAt(1, 2)).isEqualTo("Zeta");

        // Set playback on trk-1 ("Zeta", which is at view row 1)
        panel.setPlaybackStatus("trk-1", com.musiclibrary.model.PlaybackState.PLAYING);

        // View Row 0 (trk-2) should NOT have icon
        JLabel viewRow0Status = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(viewRow0Status.getIcon()).isNull();
        JLabel viewRow0Num = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(viewRow0Num.getText()).isEqualTo("2");

        // View Row 1 (trk-1) MUST have icon
        JLabel viewRow1Status = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(viewRow1Status.getIcon()).isNotNull();
        assertThat(viewRow1Status.getText()).isEmpty();

        JLabel viewRow1Num = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(viewRow1Num.getText()).isEqualTo("1");
    }

    @Test
    @DisplayName("Track with missing track number displays dash in track# column and icon in status column when playing")
    void testPlaybackStatusWithoutTrackNumber() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t0 = Track.builder().id("trk-zero").trackNumber(0).title("Unknown Track Num").filePath("u.mp3").build();
        panel.setTracks(List.of(t0));

        // STOPPED: status is empty, trackNum displays "-"
        JLabel status = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(status.getIcon()).isNull();
        assertThat(status.getText()).isEmpty();

        JLabel trackNum = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(trackNum.getIcon()).isNull();
        assertThat(trackNum.getText()).isEqualTo("-");

        // PLAYING: displays icon in status column, trackNum remains "-"
        panel.setPlaybackStatus("trk-zero", com.musiclibrary.model.PlaybackState.PLAYING);
        status = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(status.getIcon()).isNotNull();
        assertThat(status.getText()).isEmpty();

        trackNum = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(trackNum.getIcon()).isNull();
        assertThat(trackNum.getText()).isEqualTo("-");
    }

    @Test
    @DisplayName("Playback status icon renders properly on selected table row")
    void testPlaybackStatusSelectedRowRendering() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder().id("trk-sel").trackNumber(5).title("Selected Song").filePath("s.mp3").build();
        panel.setTracks(List.of(t1));

        panel.setPlaybackStatus("trk-sel", com.musiclibrary.model.PlaybackState.PLAYING);

        // Render with isSelected = true
        JLabel selectedStatus = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), true, true, 0, 0);

        assertThat(selectedStatus.getIcon()).isNotNull();
        assertThat(selectedStatus.getText()).isEmpty();

        JLabel selectedNum = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), true, true, 0, 1);
        assertThat(selectedNum.getText()).isEqualTo("5");
    }

    @Test
    @DisplayName("Status column is permanently pinned to View Index 0 and cannot be hidden or reordered")
    void testStatusColumnPinnedPermanently() {
        TrackTablePanel panel = new TrackTablePanel();
        javax.swing.JTable table = panel.getTable();

        // 1. Initial assertion: View index 0 must be the Status column (model index 0)
        assertThat(table.getColumnModel().getColumn(0).getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS);

        // 2. Cannot be hidden via setColumnVisible(0, false)
        panel.setColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS, false);
        assertThat(panel.isColumnVisible(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS)).isTrue();
        assertThat(table.getColumnModel().getColumn(0).getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS);

        // 3. Status column is excluded from the header popup menu items
        javax.swing.JPopupMenu headerMenu = panel.getHeaderPopupMenu();
        assertThat(headerMenu).isNotNull();
        assertThat(panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS]).isNull();
        for (java.awt.Component comp : headerMenu.getComponents()) {
            if (comp instanceof javax.swing.JMenuItem item) {
                assertThat(item.getText()).isNotEqualTo("Status");
            }
        }

        // 4. Drag Status column away (move view column 0 to view column 3)
        // The TableColumnModelListener must immediately pin it back to view index 0
        table.moveColumn(0, 3);
        assertThat(table.getColumnModel().getColumn(0).getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS);

        // 5. Drag another column into position 0 (move view column 2 to view column 0)
        table.moveColumn(2, 0);
        assertThat(table.getColumnModel().getColumn(0).getModelIndex())
                .isEqualTo(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_STATUS);
    }

    @Test
    @DisplayName("Comprehensive status and track number separation across multiple tracks and playback states")
    void testStatusAndTrackNumberSeparationComprehensive() {
        TrackTablePanel panel = new TrackTablePanel();

        Track t1 = Track.builder().id("trk-1").trackNumber(1).title("Song 1").filePath("1.mp3").build();
        Track t2 = Track.builder().id("trk-2").trackNumber(2).title("Song 2").filePath("2.mp3").build();
        Track t3 = Track.builder().id("trk-3").trackNumber(0).title("Song 3 (No Track#)").filePath("3.mp3").build();
        panel.setTracks(List.of(t1, t2, t3));

        // State 1: All STOPPED
        for (int row = 0; row < 3; row++) {
            JLabel status = (JLabel) panel.getTable().getCellRenderer(row, 0)
                    .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(row, 0), false, false, row, 0);
            assertThat(status.getIcon()).isNull();
            assertThat(status.getText()).isEmpty();
        }
        JLabel num1 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(num1.getText()).isEqualTo("1");
        JLabel num2 = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(num2.getText()).isEqualTo("2");
        JLabel num3 = (JLabel) panel.getTable().getCellRenderer(2, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 1), false, false, 2, 1);
        assertThat(num3.getText()).isEqualTo("-");

        // State 2: PLAYING on trk-2
        panel.setPlaybackStatus("trk-2", com.musiclibrary.model.PlaybackState.PLAYING);

        // Row 0 (trk-1): Status is empty, Track# is "1"
        JLabel s0 = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(s0.getIcon()).isNull();
        assertThat(s0.getText()).isEmpty();
        JLabel n0 = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(n0.getText()).isEqualTo("1");

        // Row 1 (trk-2): Status has speaker icon, Track# is "2"
        JLabel s1 = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(s1.getIcon()).isNotNull();
        assertThat(s1.getText()).isEmpty();
        assertThat(s1.getToolTipText()).isEqualTo("Playing");
        JLabel n1 = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(n1.getText()).isEqualTo("2");

        // Row 2 (trk-3): Status is empty, Track# is "-"
        JLabel s2 = (JLabel) panel.getTable().getCellRenderer(2, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 0), false, false, 2, 0);
        assertThat(s2.getIcon()).isNull();
        assertThat(s2.getText()).isEmpty();
        JLabel n2 = (JLabel) panel.getTable().getCellRenderer(2, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 1), false, false, 2, 1);
        assertThat(n2.getText()).isEqualTo("-");

        // State 3: Switch to PAUSED on trk-3
        panel.setPlaybackStatus("trk-3", com.musiclibrary.model.PlaybackState.PAUSED);

        // Row 1 (trk-2) is cleared
        s1 = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(s1.getIcon()).isNull();
        n1 = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(n1.getText()).isEqualTo("2");

        // Row 2 (trk-3): Status has pause icon, Track# remains "-"
        s2 = (JLabel) panel.getTable().getCellRenderer(2, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 0), false, false, 2, 0);
        assertThat(s2.getIcon()).isNotNull();
        assertThat(s2.getText()).isEmpty();
        assertThat(s2.getToolTipText()).isEqualTo("Paused");
        n2 = (JLabel) panel.getTable().getCellRenderer(2, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 1), false, false, 2, 1);
        assertThat(n2.getText()).isEqualTo("-");

        // State 4: STOPPED
        panel.setPlaybackStatus(null, com.musiclibrary.model.PlaybackState.STOPPED);
        s2 = (JLabel) panel.getTable().getCellRenderer(2, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 0), false, false, 2, 0);
        assertThat(s2.getIcon()).isNull();
        n2 = (JLabel) panel.getTable().getCellRenderer(2, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(2, 1), false, false, 2, 1);
        assertThat(n2.getText()).isEqualTo("-");
    }

    @Test
    @DisplayName("Track number column has 80px preferred width and displays localized header without text truncation")
    void testTrackNumberColumnWidthAndHeaderLocalization() {
        TrackTablePanel panel = new TrackTablePanel();
        javax.swing.JTable table = panel.getTable();

        // 1. Initial preferred width check
        int trackNumViewIdx = table.convertColumnIndexToView(com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER);
        assertThat(trackNumViewIdx).isEqualTo(1);
        javax.swing.table.TableColumn trackCol = table.getColumnModel().getColumn(trackNumViewIdx);
        assertThat(trackCol.getPreferredWidth()).isEqualTo(80);

        // 2. Japanese locale header & context menu item
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(trackCol.getHeaderValue()).isEqualTo("トラック番号");
        javax.swing.JCheckBoxMenuItem jaItem = panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER];
        assertThat(jaItem).isNotNull();
        assertThat(jaItem.getText()).isEqualTo("トラック番号");

        // Verify that 80px is sufficiently wide for the Japanese header text "トラック番号"
        java.awt.Font headerFont = table.getTableHeader().getFont();
        if (headerFont != null) {
            java.awt.FontMetrics fm = table.getFontMetrics(headerFont);
            int textWidth = fm.stringWidth("トラック番号");
            // Standard font width for 6 Japanese characters is around 60-72px, well within 80px
            assertThat(textWidth).isLessThan(80);
        }

        // 3. English locale header & context menu item
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(trackCol.getHeaderValue()).isEqualTo("#");
        javax.swing.JCheckBoxMenuItem enItem = panel.getColumnMenuItems()[com.musiclibrary.ui.table.TrackTableModel.COLUMN_INDEX_TRACK_NUMBER];
        assertThat(enItem).isNotNull();
        assertThat(enItem.getText()).isEqualTo("#");
    }
}

