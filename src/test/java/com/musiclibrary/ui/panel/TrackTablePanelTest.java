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
        assertThat(panel.getTable().getColumnCount()).isEqualTo(8);
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
        assertThat(panel.getTable().getValueAt(0, 0)).isEqualTo(1);
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo("Bohemian Rhapsody");
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Queen");
        assertThat(panel.getTable().getValueAt(0, 3)).isEqualTo("A Night at the Opera");
        assertThat(panel.getTable().getValueAt(0, 4)).isEqualTo(354L);
        assertThat(panel.getTable().getValueAt(0, 5)).isEqualTo("Rock");
        assertThat(panel.getTable().getValueAt(0, 6)).isEqualTo(1975);
        assertThat(panel.getTable().getValueAt(0, 7)).isEqualTo(5);

        // Row 1 rendered visual text via Flyweight renderers
        JLabel numLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 0), false, false, 0, 0);
        assertThat(numLabel1.getText()).isEqualTo("1");

        JLabel durLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 4)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 4), false, false, 0, 4);
        assertThat(durLabel1.getText()).isEqualTo("05:54");

        JLabel ratingLabel1 = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 7), false, false, 0, 7);
        assertThat(ratingLabel1.getText()).isEqualTo("★★★★★");

        // Row 2 boundary rendered visual text
        JLabel numLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 0)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 0), false, false, 1, 0);
        assertThat(numLabel2.getText()).isEqualTo("-");

        JLabel durLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 4)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 4), false, false, 1, 4);
        assertThat(durLabel2.getText()).isEqualTo("00:00");

        JLabel yearLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 6)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 6), false, false, 1, 6);
        assertThat(yearLabel2.getText()).isEqualTo("-");

        JLabel ratingLabel2 = (JLabel) panel.getTable().getCellRenderer(1, 7)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 7), false, false, 1, 7);
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
    @DisplayName("updateLanguage updates all 8 column header names and context menu items in Japanese and English")
    void testUpdateLanguage() {
        TrackTablePanel panel = new TrackTablePanel();

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getTable().getColumnModel().getColumn(0).getHeaderValue()).isEqualTo("番号");
        assertThat(panel.getTable().getColumnModel().getColumn(1).getHeaderValue()).isEqualTo("タイトル");
        assertThat(panel.getTable().getColumnModel().getColumn(2).getHeaderValue()).isEqualTo("アーティスト");
        assertThat(panel.getTable().getColumnModel().getColumn(3).getHeaderValue()).isEqualTo("アルバム");
        assertThat(panel.getTable().getColumnModel().getColumn(4).getHeaderValue()).isEqualTo("時間");
        assertThat(panel.getTable().getColumnModel().getColumn(5).getHeaderValue()).isEqualTo("ジャンル");
        assertThat(panel.getTable().getColumnModel().getColumn(6).getHeaderValue()).isEqualTo("リリース年");
        assertThat(panel.getTable().getColumnModel().getColumn(7).getHeaderValue()).isEqualTo("レート");

        // Context menu item texts
        assertThat(panel.getContextMenu().getComponent(0)).isInstanceOf(javax.swing.JMenuItem.class);
        javax.swing.JMenuItem playItem = (javax.swing.JMenuItem) panel.getContextMenu().getComponent(0);
        assertThat(playItem.getText()).isEqualTo("再生");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getTable().getColumnModel().getColumn(0).getHeaderValue()).isEqualTo("#");
        assertThat(panel.getTable().getColumnModel().getColumn(1).getHeaderValue()).isEqualTo("Title");
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
        JLabel dur59 = (JLabel) panel.getTable().getCellRenderer(0, 4)
                .getTableCellRendererComponent(panel.getTable(), 59L, false, false, 0, 4);
        assertThat(dur59.getText()).isEqualTo("00:59");

        // 60 seconds -> 01:00
        JLabel dur60 = (JLabel) panel.getTable().getCellRenderer(0, 4)
                .getTableCellRendererComponent(panel.getTable(), 60L, false, false, 0, 4);
        assertThat(dur60.getText()).isEqualTo("01:00");

        // 605 seconds -> 10:05
        JLabel dur605 = (JLabel) panel.getTable().getCellRenderer(0, 4)
                .getTableCellRendererComponent(panel.getTable(), 605L, false, false, 0, 4);
        assertThat(dur605.getText()).isEqualTo("10:05");

        // 3661 seconds -> 01:01:01 (TimeFormatter format)
        JLabel dur3661 = (JLabel) panel.getTable().getCellRenderer(0, 4)
                .getTableCellRendererComponent(panel.getTable(), 3661L, false, false, 0, 4);
        assertThat(dur3661.getText()).isEqualTo("01:01:01");

        // Star ratings 1 to 5, and boundaries
        JLabel r1 = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), 1, false, false, 0, 7);
        assertThat(r1.getText()).isEqualTo("★");

        JLabel r2 = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), 2, false, false, 0, 7);
        assertThat(r2.getText()).isEqualTo("★★");

        JLabel r3 = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), 3, false, false, 0, 7);
        assertThat(r3.getText()).isEqualTo("★★★");

        JLabel r4 = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), 4, false, false, 0, 7);
        assertThat(r4.getText()).isEqualTo("★★★★");

        JLabel r5 = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), 5, false, false, 0, 7);
        assertThat(r5.getText()).isEqualTo("★★★★★");

        JLabel rOut = (JLabel) panel.getTable().getCellRenderer(0, 7)
                .getTableCellRendererComponent(panel.getTable(), 99, false, false, 0, 7);
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

        // Column 1 is Title column (unselected)
        JLabel normalLabel = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(normalLabel.getText()).isEqualTo("Normal Song");
        assertThat(normalLabel.getToolTipText()).isNull();

        JLabel missingLabel = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
        assertThat(missingLabel.getText()).isEqualTo("⚠️ Missing Song");
        assertThat(missingLabel.getToolTipText()).isEqualTo(I18nManager.getInstance().get("missing.warning_tooltip"));
        assertThat(missingLabel.getForeground()).isEqualTo(new java.awt.Color(220, 80, 80));

        // When row is selected, renderer does not override selection color
        JLabel missingSelectedLabel = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), true, false, 1, 1);
        assertThat(missingSelectedLabel.getText()).isEqualTo("⚠️ Missing Song");
        assertThat(missingSelectedLabel.getToolTipText()).isEqualTo(I18nManager.getInstance().get("missing.warning_tooltip"));

        // updateMissingTracks triggers repaint and reflects state
        missingTrack.setMissing(false);
        panel.updateMissingTracks(java.util.Collections.emptyList());
        JLabel resolvedLabel = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
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
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(1, javax.swing.SortOrder.DESCENDING)));

        // View Row 0 is now trackZ (Missing)
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo("Z Song (Missing)");
        JLabel viewRow0Label = (JLabel) panel.getTable().getCellRenderer(0, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(0, 1), false, false, 0, 1);
        assertThat(viewRow0Label.getText()).isEqualTo("⚠️ Z Song (Missing)");
        assertThat(viewRow0Label.getToolTipText()).isEqualTo(I18nManager.getInstance().get("missing.warning_tooltip"));

        // View Row 1 is now trackA (Normal)
        assertThat(panel.getTable().getValueAt(1, 1)).isEqualTo("A Song (Normal)");
        JLabel viewRow1Label = (JLabel) panel.getTable().getCellRenderer(1, 1)
                .getTableCellRendererComponent(panel.getTable(), panel.getTable().getValueAt(1, 1), false, false, 1, 1);
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

        assertThat(panel.getTable().getValueAt(0, 0)).isEqualTo(1);
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo("Original Title");
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Original Artist");
        assertThat(panel.getTable().getValueAt(0, 3)).isEqualTo("Original Album");
        assertThat(panel.getTable().getValueAt(0, 5)).isEqualTo("Old Genre");
        assertThat(panel.getTable().getValueAt(0, 6)).isEqualTo(2000);

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
        assertThat(panel.getTable().getValueAt(0, 0)).isEqualTo(9);
        assertThat(panel.getTable().getValueAt(0, 1)).isEqualTo("Updated Title");
        assertThat(panel.getTable().getValueAt(0, 2)).isEqualTo("Updated Artist");
        assertThat(panel.getTable().getValueAt(0, 3)).isEqualTo("Updated Album");
        assertThat(panel.getTable().getValueAt(0, 5)).isEqualTo("Modern Rock");
        assertThat(panel.getTable().getValueAt(0, 6)).isEqualTo(2025);
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
}

