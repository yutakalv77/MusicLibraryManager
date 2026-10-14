package com.musiclibrary.ui.dnd;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackDropTargetListenerTest {

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    }

    @Test
    @DisplayName("Constructor throws NullPointerException when component or callback is null")
    void testConstructorValidation() {
        JPanel panel = new JPanel();
        assertThatThrownBy(() -> new TrackDropTargetListener(null, (p, pt) -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targetComponent cannot be null");

        assertThatThrownBy(() -> new TrackDropTargetListener(panel, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("onDropCallback cannot be null");
    }

    @Test
    @DisplayName("dragEnter with supported flavor highlights border; dragExit restores original border")
    void testBorderHighlightOnDragEnterAndExit() {
        JPanel panel = new JPanel();
        Border originalBorder = panel.getBorder();
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, p) -> {});

        assertThat(panel.getDropTarget()).isNotNull();
        assertThat(listener.isHighlighted()).isFalse();

        // Simulate dragEnter with supported flavor
        AtomicBoolean dragAccepted = new AtomicBoolean(false);
        DropTargetDragEvent dragEnterEvent = new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 10),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.javaFileListFlavor.equals(df);
            }

            @Override
            public void acceptDrag(int dragOperation) {
                dragAccepted.set(true);
            }
        };

        listener.dragEnter(dragEnterEvent);
        assertThat(dragAccepted.get()).isTrue();
        assertThat(listener.isHighlighted()).isTrue();
        assertThat(panel.getBorder()).isNotEqualTo(originalBorder);

        // Simulate dragExit
        listener.dragExit(null);
        assertThat(listener.isHighlighted()).isFalse();
        assertThat(panel.getBorder()).isEqualTo(originalBorder);
    }

    @Test
    @DisplayName("dragEnter with unsupported flavor rejects drag and does not highlight")
    void testDragEnterWithUnsupportedFlavorRejectsDrag() {
        JPanel panel = new JPanel();
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, p) -> {});

        AtomicBoolean dragRejected = new AtomicBoolean(false);
        DropTargetDragEvent dragEnterEvent = new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 10),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return false; // unsupported
            }

            @Override
            public void rejectDrag() {
                dragRejected.set(true);
            }
        };

        listener.dragEnter(dragEnterEvent);
        assertThat(dragRejected.get()).isTrue();
        assertThat(listener.isHighlighted()).isFalse();
    }

    @Test
    @DisplayName("drop with unsupported flavor rejects drop and does not invoke callback")
    void testDropWithUnsupportedFlavorRejectsDrop() {
        JPanel panel = new JPanel();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, p) -> callbackInvoked.set(true));

        AtomicBoolean dropRejected = new AtomicBoolean(false);
        DropTargetDropEvent dropEvent = new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(50, 50),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return false;
            }

            @Override
            public void rejectDrop() {
                dropRejected.set(true);
            }
        };

        listener.drop(dropEvent);
        assertThat(dropRejected.get()).isTrue();
        assertThat(callbackInvoked.get()).isFalse();
        assertThat(listener.isHighlighted()).isFalse();
    }

    @Test
    @DisplayName("drop parses mixed files and directories in javaFileListFlavor and passes coordinates")
    void testDropHandlingWithMixedFiles() throws Exception {
        JPanel panel = new JPanel();
        AtomicReference<List<Path>> droppedResult = new AtomicReference<>();
        AtomicReference<Point> pointResult = new AtomicReference<>();

        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, p) -> {
            droppedResult.set(paths);
            pointResult.set(p);
        });

        File dummyFile1 = new File("track1.mp3");
        File dummyFile2 = new File("track2.flac");
        File dummyFolder = new File("album_folder");
        List<File> fileList = List.of(dummyFile1, dummyFolder, dummyFile2);

        Transferable transferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return fileList;
            }
        };

        Point dropLoc = new Point(120, 240);
        AtomicBoolean dropCompletedSuccess = new AtomicBoolean(false);

        listener.drop(new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                dropLoc,
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.javaFileListFlavor.equals(df);
            }

            @Override
            public Transferable getTransferable() {
                return transferable;
            }

            @Override
            public void acceptDrop(int dropAction) {}

            @Override
            public void dropComplete(boolean success) {
                dropCompletedSuccess.set(success);
            }
        });

        assertThat(dropCompletedSuccess.get()).isTrue();
        assertThat(droppedResult.get()).isNotNull();
        assertThat(droppedResult.get()).hasSize(3);
        assertThat(droppedResult.get()).containsExactly(
                dummyFile1.toPath(),
                dummyFolder.toPath(),
                dummyFile2.toPath()
        );
        assertThat(pointResult.get()).isEqualTo(dropLoc);
        assertThat(listener.isHighlighted()).isFalse();
    }

    @Test
    @DisplayName("drop with empty list does not trigger callback")
    void testDropWithEmptyList() throws Exception {
        JPanel panel = new JPanel();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, p) -> callbackInvoked.set(true));

        Transferable emptyTransferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) {
                return List.of();
            }
        };

        listener.drop(new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 10),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.javaFileListFlavor.equals(df);
            }

            @Override
            public Transferable getTransferable() {
                return emptyTransferable;
            }

            @Override
            public void acceptDrop(int dropAction) {}

            @Override
            public void dropComplete(boolean success) {}
        });

        assertThat(callbackInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("drop handles transfer exceptions gracefully with dropComplete(false)")
    void testDropExceptionHandling() {
        JPanel panel = new JPanel();
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, p) -> callbackInvoked.set(true));

        Transferable throwingTransferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return true;
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws java.io.IOException {
                throw new java.io.IOException("Simulated I/O failure");
            }
        };

        AtomicBoolean dropCompletedSuccess = new AtomicBoolean(true);

        listener.drop(new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 10),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return true;
            }

            @Override
            public Transferable getTransferable() {
                return throwingTransferable;
            }

            @Override
            public void acceptDrop(int dropAction) {}

            @Override
            public void dropComplete(boolean success) {
                dropCompletedSuccess.set(success);
            }
        });

        assertThat(dropCompletedSuccess.get()).isFalse();
        assertThat(callbackInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("dragEnter with track ID flavor highlights border and accepts drag")
    void testDragEnterWithTrackIdFlavor() {
        JPanel panel = new JPanel();
        AtomicReference<List<String>> droppedTracks = new AtomicReference<>();
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, null, (ids, pt) -> droppedTracks.set(ids));

        AtomicBoolean dragAccepted = new AtomicBoolean(false);
        DropTargetDragEvent dragEnterEvent = new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 10),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return TrackTransferData.TRACK_ID_LIST_FLAVOR.equals(df);
            }

            @Override
            public void acceptDrag(int dragOperation) {
                dragAccepted.set(true);
            }
        };

        listener.dragEnter(dragEnterEvent);
        assertThat(dragAccepted.get()).isTrue();
        assertThat(listener.isHighlighted()).isTrue();

        listener.dragExit(null);
        assertThat(listener.isHighlighted()).isFalse();
    }

    @Test
    @DisplayName("drop with track ID flavor triggers onTracksDropCallback")
    void testDropWithTrackIdFlavor() {
        JPanel panel = new JPanel();
        AtomicReference<List<String>> droppedTracks = new AtomicReference<>();
        AtomicReference<Point> dropPoint = new AtomicReference<>();
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, null, (ids, pt) -> {
            droppedTracks.set(ids);
            dropPoint.set(pt);
        });

        List<String> trackIds = List.of("id-1", "id-2", "id-3");
        TrackTransferData transferData = new TrackTransferData(trackIds);

        AtomicBoolean dropCompletedSuccess = new AtomicBoolean(false);
        Point targetPoint = new Point(25, 40);

        listener.drop(new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                targetPoint,
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return TrackTransferData.TRACK_ID_LIST_FLAVOR.equals(df);
            }

            @Override
            public Transferable getTransferable() {
                return transferData;
            }

            @Override
            public void acceptDrop(int dropAction) {}

            @Override
            public void dropComplete(boolean success) {
                dropCompletedSuccess.set(success);
            }
        });

        assertThat(dropCompletedSuccess.get()).isTrue();
        assertThat(droppedTracks.get()).containsExactly("id-1", "id-2", "id-3");
        assertThat(dropPoint.get()).isEqualTo(targetPoint);
        assertThat(listener.isHighlighted()).isFalse();
    }

    @Test
    @DisplayName("drop with stringFlavor extracts IDs and triggers onTracksDropCallback")
    void testDropWithStringFlavorTriggersOnTracksDropCallback() {
        JPanel panel = new JPanel();
        AtomicReference<List<String>> droppedTracks = new AtomicReference<>();
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, null, (ids, pt) -> droppedTracks.set(ids));

        Transferable stringTransferable = new Transferable() {
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
                return "track-str-1\ntrack-str-2";
            }
        };

        AtomicBoolean dropCompletedSuccess = new AtomicBoolean(false);

        listener.drop(new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 20),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.stringFlavor.equals(df);
            }

            @Override
            public Transferable getTransferable() {
                return stringTransferable;
            }

            @Override
            public void acceptDrop(int dropAction) {}

            @Override
            public void dropComplete(boolean success) {
                dropCompletedSuccess.set(success);
            }
        });

        assertThat(dropCompletedSuccess.get()).isTrue();
        assertThat(droppedTracks.get()).containsExactly("track-str-1", "track-str-2");
    }

    @Test
    @DisplayName("dragOver and dropActionChanged accept supported drag and reject unsupported drag")
    void testDragOverAndDropActionChangedAcceptance() {
        JPanel panel = new JPanel();
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, (paths, pt) -> {});

        AtomicBoolean dragAccepted = new AtomicBoolean(false);
        AtomicBoolean dragRejected = new AtomicBoolean(false);

        DropTargetDragEvent supportedEvent = new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(5, 5),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.javaFileListFlavor.equals(df);
            }

            @Override
            public void acceptDrag(int dragOperation) {
                dragAccepted.set(true);
            }
        };

        DropTargetDragEvent unsupportedEvent = new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(5, 5),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return false;
            }

            @Override
            public void rejectDrag() {
                dragRejected.set(true);
            }
        };

        // 1. dragOver
        listener.dragOver(supportedEvent);
        assertThat(dragAccepted.get()).isTrue();

        listener.dragOver(unsupportedEvent);
        assertThat(dragRejected.get()).isTrue();

        // 2. dropActionChanged
        dragAccepted.set(false);
        dragRejected.set(false);

        listener.dropActionChanged(supportedEvent);
        assertThat(dragAccepted.get()).isTrue();

        listener.dropActionChanged(unsupportedEvent);
        assertThat(dragRejected.get()).isTrue();
    }

    @Test
    @DisplayName("Flavor is rejected when corresponding callback is null")
    void testFlavorRejectionWhenCallbacksAreNull() {
        JPanel panel = new JPanel();

        // Listener with ONLY onDropCallback (onTracksDropCallback is null)
        TrackDropTargetListener fileOnlyListener = new TrackDropTargetListener(panel, (p, pt) -> {}, null);
        AtomicBoolean trackDragRejected = new AtomicBoolean(false);
        fileOnlyListener.dragEnter(new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(0, 0),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return TrackTransferData.TRACK_ID_LIST_FLAVOR.equals(df);
            }

            @Override
            public void rejectDrag() {
                trackDragRejected.set(true);
            }
        });
        assertThat(trackDragRejected.get()).isTrue();

        // Listener with ONLY onTracksDropCallback (onDropCallback is null)
        TrackDropTargetListener trackOnlyListener = new TrackDropTargetListener(panel, null, (ids, pt) -> {});
        AtomicBoolean fileDragRejected = new AtomicBoolean(false);
        trackOnlyListener.dragEnter(new DropTargetDragEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(0, 0),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.javaFileListFlavor.equals(df);
            }

            @Override
            public void rejectDrag() {
                fileDragRejected.set(true);
            }
        });
        assertThat(fileDragRejected.get()).isTrue();
    }

    @Test
    @DisplayName("drop with blank or empty track IDs does not trigger onTracksDropCallback")
    void testDropWithEmptyTrackIdsDoesNotTriggerCallback() {
        JPanel panel = new JPanel();
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        TrackDropTargetListener listener = new TrackDropTargetListener(panel, null, (ids, pt) -> callbackCalled.set(true));

        Transferable blankTransferable = new Transferable() {
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

        listener.drop(new DropTargetDropEvent(
                panel.getDropTarget().getDropTargetContext(),
                new Point(10, 10),
                DnDConstants.ACTION_COPY,
                DnDConstants.ACTION_COPY
        ) {
            @Override
            public boolean isDataFlavorSupported(DataFlavor df) {
                return DataFlavor.stringFlavor.equals(df);
            }

            @Override
            public Transferable getTransferable() {
                return blankTransferable;
            }

            @Override
            public void acceptDrop(int dropAction) {}

            @Override
            public void dropComplete(boolean success) {}
        });

        assertThat(callbackCalled.get()).isFalse();
    }
}

