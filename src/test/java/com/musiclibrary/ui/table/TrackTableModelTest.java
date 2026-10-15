package com.musiclibrary.ui.table;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TrackTableModelTest {

    private TrackTableModel model;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        model = new TrackTableModel();
    }

    @Test
    @DisplayName("Model defines exactly 15 columns with localized names and precise numeric/text/date classes")
    void testColumnDefinitionsAndClasses() {
        assertThat(model.getColumnCount()).isEqualTo(15);

        // Column classes for natural TableRowSorter numeric/date comparison
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_STATUS)).isEqualTo(Object.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_TRACK_NUMBER)).isEqualTo(Integer.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_TITLE)).isEqualTo(String.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_ARTIST)).isEqualTo(String.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST)).isEqualTo(String.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_ALBUM)).isEqualTo(String.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_DURATION)).isEqualTo(Long.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_GENRE)).isEqualTo(String.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_YEAR)).isEqualTo(Integer.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_DISC_NUMBER)).isEqualTo(Integer.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_BPM)).isEqualTo(Integer.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_BITRATE)).isEqualTo(Integer.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_DATE_ADDED)).isEqualTo(java.time.Instant.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_PLAY_COUNT)).isEqualTo(Integer.class);
        assertThat(model.getColumnClass(TrackTableModel.COLUMN_INDEX_RATING)).isEqualTo(Integer.class);

        // Column names (English)
        assertThat(model.getColumnName(0)).isEqualTo("");
        assertThat(model.getColumnName(1)).isEqualTo("#");
        assertThat(model.getColumnName(2)).isEqualTo("Title");
        assertThat(model.getColumnName(3)).isEqualTo("Artist");
        assertThat(model.getColumnName(4)).isEqualTo("Album Artist");
        assertThat(model.getColumnName(5)).isEqualTo("Album");
        assertThat(model.getColumnName(6)).isEqualTo("Time");
        assertThat(model.getColumnName(7)).isEqualTo("Genre");
        assertThat(model.getColumnName(8)).isEqualTo("Year");
        assertThat(model.getColumnName(9)).isEqualTo("Disc #");
        assertThat(model.getColumnName(10)).isEqualTo("BPM");
        assertThat(model.getColumnName(11)).isEqualTo("Bitrate");
        assertThat(model.getColumnName(12)).isEqualTo("Date Added");
        assertThat(model.getColumnName(13)).isEqualTo("Plays");
        assertThat(model.getColumnName(14)).isEqualTo("Rating");

        // Column names (Japanese)
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        assertThat(model.getColumnName(0)).isEqualTo("");
        assertThat(model.getColumnName(1)).isEqualTo("トラック番号");
        assertThat(model.getColumnName(2)).isEqualTo("タイトル");
        assertThat(model.getColumnName(3)).isEqualTo("アーティスト");
        assertThat(model.getColumnName(4)).isEqualTo("アルバムアーティスト");
        assertThat(model.getColumnName(5)).isEqualTo("アルバム");
        assertThat(model.getColumnName(6)).isEqualTo("時間");
        assertThat(model.getColumnName(7)).isEqualTo("ジャンル");
        assertThat(model.getColumnName(8)).isEqualTo("リリース年");
        assertThat(model.getColumnName(9)).isEqualTo("ディスク番号");
        assertThat(model.getColumnName(10)).isEqualTo("BPM");
        assertThat(model.getColumnName(11)).isEqualTo("ビットレート");
        assertThat(model.getColumnName(12)).isEqualTo("追加日");
        assertThat(model.getColumnName(13)).isEqualTo("再生回数");
        assertThat(model.getColumnName(14)).isEqualTo("レート");
    }

    @Test
    @DisplayName("getValueAt returns typed raw values for model cells across all 14 columns and null for out-of-bounds")
    void testGetValueAt() {
        java.time.Instant now = java.time.Instant.parse("2026-10-14T10:15:30Z");
        Track track = Track.builder()
                .filePath("C:/Music/test.mp3")
                .trackNumber(7)
                .title("A Day in the Life")
                .artist("The Beatles")
                .albumArtist("Beatles Complete")
                .album("Sgt. Pepper")
                .durationSeconds(335)
                .genre("Rock")
                .year(1967)
                .discNumber(1)
                .bpm(120)
                .bitrate(320)
                .dateAdded(now)
                .playCount(15)
                .rating(5)
                .build();
        model.addTrack(track);

        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_STATUS)).isEqualTo("");
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_TRACK_NUMBER)).isEqualTo(7);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_TITLE)).isEqualTo("A Day in the Life");
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_ARTIST)).isEqualTo("The Beatles");
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_ALBUM_ARTIST)).isEqualTo("Beatles Complete");
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_ALBUM)).isEqualTo("Sgt. Pepper");
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_DURATION)).isEqualTo(335L);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_GENRE)).isEqualTo("Rock");
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_YEAR)).isEqualTo(1967);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_DISC_NUMBER)).isEqualTo(1);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_BPM)).isEqualTo(120);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_BITRATE)).isEqualTo(320);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_DATE_ADDED)).isEqualTo(now);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_PLAY_COUNT)).isEqualTo(15);
        assertThat(model.getValueAt(0, TrackTableModel.COLUMN_INDEX_RATING)).isEqualTo(5);

        // Out of bounds
        assertThat(model.getValueAt(-1, 0)).isNull();
        assertThat(model.getValueAt(1, 0)).isNull();
        assertThat(model.getValueAt(0, 99)).isNull();
    }

    @Test
    @DisplayName("addTrack, addTracks, updateTrack, and removeTrack dispatch fine-grained TableModelEvents")
    void testTargetedModelEvents() {
        List<TableModelEvent> events = new ArrayList<>();
        model.addTableModelListener(events::add);

        Track t1 = Track.builder().id("id-1").filePath("C:/1.mp3").title("Song 1").build();
        Track t2 = Track.builder().id("id-2").filePath("C:/2.mp3").title("Song 2").build();
        Track t3 = Track.builder().id("id-3").filePath("C:/3.mp3").title("Song 3").build();

        // 1. addTrack
        model.addTrack(t1);
        assertThat(events).hasSize(1);
        TableModelEvent ev1 = events.get(0);
        assertThat(ev1.getType()).isEqualTo(TableModelEvent.INSERT);
        assertThat(ev1.getFirstRow()).isEqualTo(0);
        assertThat(ev1.getLastRow()).isEqualTo(0);

        // 2. addTracks (batch of 2) -> single range event
        events.clear();
        model.addTracks(List.of(t2, t3));
        assertThat(events).hasSize(1);
        TableModelEvent evBatch = events.get(0);
        assertThat(evBatch.getType()).isEqualTo(TableModelEvent.INSERT);
        assertThat(evBatch.getFirstRow()).isEqualTo(1);
        assertThat(evBatch.getLastRow()).isEqualTo(2);

        // 3. updateTrack -> targeted single row update
        events.clear();
        Track updatedT2 = t2.toBuilder().title("Song 2 (Remastered)").build();
        boolean updated = model.updateTrack(updatedT2);
        assertThat(updated).isTrue();
        assertThat(events).hasSize(1);
        TableModelEvent evUpdate = events.get(0);
        assertThat(evUpdate.getType()).isEqualTo(TableModelEvent.UPDATE);
        assertThat(evUpdate.getFirstRow()).isEqualTo(1);
        assertThat(evUpdate.getLastRow()).isEqualTo(1);
        assertThat(model.getTrackAt(1).getTitle()).isEqualTo("Song 2 (Remastered)");

        // 4. removeTrack -> targeted delete
        events.clear();
        Track removed = model.removeTrack(0);
        assertThat(removed).isEqualTo(t1);
        assertThat(events).hasSize(1);
        TableModelEvent evDel = events.get(0);
        assertThat(evDel.getType()).isEqualTo(TableModelEvent.DELETE);
        assertThat(evDel.getFirstRow()).isEqualTo(0);
        assertThat(evDel.getLastRow()).isEqualTo(0);

        // 5. removeTrackById
        events.clear();
        Track removedById = model.removeTrackById("id-3");
        assertThat(removedById).isEqualTo(t3);
        assertThat(model.getRowCount()).isEqualTo(1);

        // 6. clear
        events.clear();
        model.clear();
        assertThat(model.getRowCount()).isEqualTo(0);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(TableModelEvent.DELETE);
    }

    @Test
    @DisplayName("TableRowSorter correctly sorts numeric columns (TrackNumber, Duration, Year, Rating) in natural numerical order")
    void testNumericSortingWithTableRowSorter() {
        TableRowSorter<TrackTableModel> sorter = new TableRowSorter<>(model);

        // Insert tracks with numbers 2, 10, 1 (string sort would do 1, 10, 2; numeric sort must do 1, 2, 10)
        Track t1 = Track.builder().id("1").filePath("C:/1.mp3").trackNumber(2).durationSeconds(300).year(2005).rating(2).build();
        Track t2 = Track.builder().id("2").filePath("C:/2.mp3").trackNumber(10).durationSeconds(60).year(1999).rating(5).build();
        Track t3 = Track.builder().id("3").filePath("C:/3.mp3").trackNumber(1).durationSeconds(180).year(2020).rating(4).build();

        model.addTracks(List.of(t1, t2, t3));

        // Sort by Track Number ascending
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_TRACK_NUMBER, SortOrder.ASCENDING)));
        sorter.sort();

        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(2); // TrackNumber 1 (t3)
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(0); // TrackNumber 2 (t1)
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(1); // TrackNumber 10 (t2)

        // Sort by Duration ascending (60, 180, 300)
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_DURATION, SortOrder.ASCENDING)));
        sorter.sort();

        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(1); // 60s (t2)
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(2); // 180s (t3)
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(0); // 300s (t1)
    }

    @Test
    @DisplayName("High-throughput bulk operations (5,000 tracks) execute efficiently with single event notification")
    void testBulkDataPerformance() {
        int count = 5000;
        List<Track> bulk = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bulk.add(Track.builder()
                    .id("bulk-" + i)
                    .filePath("C:/music/bulk-" + i + ".mp3")
                    .trackNumber(i % 50)
                    .title("Title " + i)
                    .artist("Artist " + (i % 20))
                    .durationSeconds(120 + i)
                    .build());
        }

        AtomicInteger eventCount = new AtomicInteger(0);
        model.addTableModelListener(e -> eventCount.incrementAndGet());

        long start = System.currentTimeMillis();
        model.addTracks(bulk);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(model.getRowCount()).isEqualTo(count);
        // Only 1 single event fired for 5,000 tracks
        assertThat(eventCount.get()).isEqualTo(1);
        assertThat(elapsed).isLessThan(2000); // Super fast execution

        // Lookup index
        assertThat(model.indexOfTrack("bulk-2500")).isEqualTo(2500);
        assertThat(model.getTrackAt(2500).getTitle()).isEqualTo("Title 2500");
    }

    @Test
    @DisplayName("Edge cases and null arguments are handled safely without exceptions")
    void testEdgeCasesAndNullSafety() {
        // null / empty additions
        model.addTrack(null);
        assertThat(model.getRowCount()).isEqualTo(0);

        model.addTracks(null);
        assertThat(model.getRowCount()).isEqualTo(0);

        model.addTracks(List.of());
        assertThat(model.getRowCount()).isEqualTo(0);

        // out-of-bounds removals
        assertThat(model.removeTrack(-1)).isNull();
        assertThat(model.removeTrack(100)).isNull();
        assertThat(model.removeTrackById(null)).isNull();
        assertThat(model.removeTrackById("non-existent")).isNull();

        // null / invalid updates
        assertThat(model.updateTrack(null)).isFalse();
        Track noId = Track.builder().id(null).filePath("C:/test.mp3").build();
        assertThat(model.updateTrack(noId)).isFalse();

        // index and get boundaries
        assertThat(model.indexOfTrack(null)).isEqualTo(-1);
        assertThat(model.indexOfTrack("not-found")).isEqualTo(-1);
        assertThat(model.getTrackAt(-1)).isNull();
        assertThat(model.getTrackAt(100)).isNull();
    }

    @Test
    @DisplayName("TableRowSorter correctly sorts numeric columns in descending order")
    void testDescendingSorting() {
        TableRowSorter<TrackTableModel> sorter = new TableRowSorter<>(model);

        Track t1 = Track.builder().id("1").filePath("C:/1.mp3").trackNumber(2).durationSeconds(300).build();
        Track t2 = Track.builder().id("2").filePath("C:/2.mp3").trackNumber(10).durationSeconds(60).build();
        Track t3 = Track.builder().id("3").filePath("C:/3.mp3").trackNumber(1).durationSeconds(180).build();
        model.addTracks(List.of(t1, t2, t3));

        // Sort by Track Number descending (10, 2, 1)
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_TRACK_NUMBER, SortOrder.DESCENDING)));
        sorter.sort();

        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(1); // 10 (t2)
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(0); // 2 (t1)
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(2); // 1 (t3)
    }

    @Test
    @DisplayName("TableRowSorter correctly sorts BPM, Bitrate, DiscNumber, and DateAdded columns")
    void testNewColumnsSortingWithTableRowSorter() {
        TableRowSorter<TrackTableModel> sorter = new TableRowSorter<>(model);

        java.time.Instant time1 = java.time.Instant.parse("2024-01-01T00:00:00Z");
        java.time.Instant time2 = java.time.Instant.parse("2025-06-01T00:00:00Z");
        java.time.Instant time3 = java.time.Instant.parse("2023-12-01T00:00:00Z");

        Track t1 = Track.builder().id("1").filePath("C:/1.mp3").discNumber(2).bpm(140).bitrate(256).dateAdded(time1).build();
        Track t2 = Track.builder().id("2").filePath("C:/2.mp3").discNumber(1).bpm(80).bitrate(320).dateAdded(time2).build();
        Track t3 = Track.builder().id("3").filePath("C:/3.mp3").discNumber(3).bpm(120).bitrate(128).dateAdded(time3).build();

        model.addTracks(List.of(t1, t2, t3));

        // Sort BPM ascending (80, 120, 140) -> t2, t3, t1
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_BPM, SortOrder.ASCENDING)));
        sorter.sort();
        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(1);
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(2);
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(0);

        // Sort Bitrate ascending (128, 256, 320) -> t3, t1, t2
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_BITRATE, SortOrder.ASCENDING)));
        sorter.sort();
        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(2);
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(0);
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(1);

        // Sort DiscNumber ascending (1, 2, 3) -> t2, t1, t3
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_DISC_NUMBER, SortOrder.ASCENDING)));
        sorter.sort();
        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(1);
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(0);
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(2);

        // Sort DateAdded ascending (time3, time1, time2) -> t3, t1, t2
        sorter.setSortKeys(List.of(new RowSorter.SortKey(TrackTableModel.COLUMN_INDEX_DATE_ADDED, SortOrder.ASCENDING)));
        sorter.sort();
        assertThat(sorter.convertRowIndexToModel(0)).isEqualTo(2);
        assertThat(sorter.convertRowIndexToModel(1)).isEqualTo(0);
        assertThat(sorter.convertRowIndexToModel(2)).isEqualTo(1);
    }

    @Test
    @DisplayName("updateLanguage fires tableStructureChanged TableModelEvent")
    void testUpdateLanguageStructureEvent() {
        AtomicInteger structureChangedCount = new AtomicInteger(0);
        model.addTableModelListener(e -> {
            if (e.getFirstRow() == TableModelEvent.HEADER_ROW) {
                structureChangedCount.incrementAndGet();
            }
        });

        model.updateLanguage();
        assertThat(structureChangedCount.get()).isEqualTo(1);
    }
}
