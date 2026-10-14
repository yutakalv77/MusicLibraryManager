package com.musiclibrary.ui.dnd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackTransferDataTest {

    @Test
    @DisplayName("TrackTransferData supports TRACK_ID_LIST_FLAVOR and stringFlavor")
    void testSupportedFlavors() {
        TrackTransferData data = new TrackTransferData(List.of("id-1", "id-2"));

        DataFlavor[] flavors = data.getTransferDataFlavors();
        assertThat(flavors).hasSize(2);
        assertThat(flavors).containsExactly(TrackTransferData.TRACK_ID_LIST_FLAVOR, DataFlavor.stringFlavor);

        assertThat(data.isDataFlavorSupported(TrackTransferData.TRACK_ID_LIST_FLAVOR)).isTrue();
        assertThat(data.isDataFlavorSupported(DataFlavor.stringFlavor)).isTrue();
        assertThat(data.isDataFlavorSupported(DataFlavor.imageFlavor)).isFalse();
        assertThat(data.isDataFlavorSupported(DataFlavor.javaFileListFlavor)).isFalse();
    }

    @Test
    @DisplayName("getTransferData returns unmodifiable list for TRACK_ID_LIST_FLAVOR")
    void testGetTransferDataTrackIdListFlavor() throws Exception {
        List<String> ids = new ArrayList<>(List.of("track-a", "track-b"));
        TrackTransferData data = new TrackTransferData(ids);

        Object transferData = data.getTransferData(TrackTransferData.TRACK_ID_LIST_FLAVOR);
        assertThat(transferData).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<String> resultList = (List<String>) transferData;
        assertThat(resultList).containsExactly("track-a", "track-b");

        // Verify unmodifiable
        assertThatThrownBy(() -> resultList.add("track-c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getTransferData returns newline-delimited string for stringFlavor")
    void testGetTransferDataStringFlavor() throws Exception {
        TrackTransferData data = new TrackTransferData(List.of("id-1", "id-2", "id-3"));

        Object transferData = data.getTransferData(DataFlavor.stringFlavor);
        assertThat(transferData).isEqualTo("id-1\nid-2\nid-3");
    }

    @Test
    @DisplayName("getTransferData throws UnsupportedFlavorException for unsupported flavors")
    void testGetTransferDataUnsupportedFlavor() {
        TrackTransferData data = new TrackTransferData(List.of("id-1"));

        assertThatThrownBy(() -> data.getTransferData(DataFlavor.imageFlavor))
                .isInstanceOf(UnsupportedFlavorException.class);
        assertThatThrownBy(() -> data.getTransferData(DataFlavor.javaFileListFlavor))
                .isInstanceOf(UnsupportedFlavorException.class);
    }

    @Test
    @DisplayName("Constructor handles null and empty lists safely")
    void testConstructorNullAndEmpty() throws Exception {
        TrackTransferData nullData = new TrackTransferData(null);
        assertThat(nullData.getTransferData(TrackTransferData.TRACK_ID_LIST_FLAVOR))
                .isEqualTo(Collections.emptyList());
        assertThat(nullData.getTransferData(DataFlavor.stringFlavor)).isEqualTo("");

        TrackTransferData emptyData = new TrackTransferData(Collections.emptyList());
        assertThat(emptyData.getTransferData(TrackTransferData.TRACK_ID_LIST_FLAVOR))
                .isEqualTo(Collections.emptyList());
        assertThat(emptyData.getTransferData(DataFlavor.stringFlavor)).isEqualTo("");
    }

    @Test
    @DisplayName("extractTrackIds successfully extracts IDs from TRACK_ID_LIST_FLAVOR")
    void testExtractTrackIdsFromTrackFlavor() {
        TrackTransferData transferData = new TrackTransferData(List.of("t-1", "t-2"));

        List<String> extracted = TrackTransferData.extractTrackIds(transferData);
        assertThat(extracted).containsExactly("t-1", "t-2");
    }

    @Test
    @DisplayName("extractTrackIds filters out null, blank, and non-string elements from TRACK_ID_LIST_FLAVOR")
    void testExtractTrackIdsFiltersInvalidItems() {
        Transferable mockTransferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{TrackTransferData.TRACK_ID_LIST_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return TrackTransferData.TRACK_ID_LIST_FLAVOR.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                List<Object> mixed = new ArrayList<>();
                mixed.add("valid-1");
                mixed.add("");
                mixed.add("   ");
                mixed.add(null);
                mixed.add(12345); // Non-string object
                mixed.add("valid-2");
                return mixed;
            }
        };

        List<String> extracted = TrackTransferData.extractTrackIds(mockTransferable);
        assertThat(extracted).containsExactly("valid-1", "valid-2");
    }

    @Test
    @DisplayName("extractTrackIds falls back to stringFlavor with Windows and Unix newlines")
    void testExtractTrackIdsFromStringFlavor() {
        Transferable mockStringOnly = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.stringFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.stringFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return "id-win-1\r\nid-win-2\r\n\r\n  \r\nid-unix-3\n  id-unix-4  \n";
            }
        };

        List<String> extracted = TrackTransferData.extractTrackIds(mockStringOnly);
        assertThat(extracted).containsExactly("id-win-1", "id-win-2", "id-unix-3", "id-unix-4");
    }

    @Test
    @DisplayName("extractTrackIds safely handles null transferable, empty string, and exceptions")
    void testExtractTrackIdsEdgeCases() {
        // 1. null transferable
        assertThat(TrackTransferData.extractTrackIds(null)).isEmpty();

        // 2. empty string transferable
        Transferable emptyStringTransferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.stringFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.stringFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return "   \n\n  \r\n";
            }
        };
        assertThat(TrackTransferData.extractTrackIds(emptyStringTransferable)).isEmpty();

        // 3. throwing transferable
        Transferable throwingTransferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{TrackTransferData.TRACK_ID_LIST_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return true;
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws IOException {
                throw new IOException("Simulated read failure");
            }
        };
        assertThat(TrackTransferData.extractTrackIds(throwingTransferable)).isEmpty();
    }
}
