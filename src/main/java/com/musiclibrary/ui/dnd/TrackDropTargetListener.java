package com.musiclibrary.ui.dnd;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DropTargetListener that accepts external files and folders dragged from OS file managers
 * (Windows Explorer, macOS Finder), highlights the drop boundary, and passes paths to a callback.
 */
public class TrackDropTargetListener implements DropTargetListener {

    /**
     * Callback interface invoked when files are successfully dropped onto the target component.
     */
    @FunctionalInterface
    public interface OnDropCallback {
        /**
         * Invoked with the list of dropped file/folder paths and the coordinate of the drop.
         *
         * @param droppedPaths list of paths dropped
         * @param dropPoint    drop coordinates relative to target component
         */
        void onFilesDropped(List<Path> droppedPaths, Point dropPoint);
    }

    /**
     * Callback interface invoked when track IDs are dragged and dropped from the track table.
     */
    @FunctionalInterface
    public interface OnTracksDropCallback {
        /**
         * Invoked with the list of dropped track IDs and the coordinate of the drop.
         *
         * @param trackIds  list of track IDs dropped
         * @param dropPoint drop coordinates relative to target component
         */
        void onTracksDropped(List<String> trackIds, Point dropPoint);
    }

    private static final Color HIGHLIGHT_BORDER_COLOR = new Color(51, 153, 255);

    private final JComponent targetComponent;
    private final OnDropCallback onDropCallback;
    private final OnTracksDropCallback onTracksDropCallback;
    private Border originalBorder;
    private boolean isHighlighted = false;

    /**
     * Attaches this drop target listener to the target component for file drops.
     *
     * @param targetComponent component to accept file drops
     * @param onDropCallback  handler invoked with dropped paths and point
     */
    public TrackDropTargetListener(JComponent targetComponent, OnDropCallback onDropCallback) {
        this(targetComponent, Objects.requireNonNull(onDropCallback, "onDropCallback cannot be null"), null);
    }

    /**
     * Attaches this drop target listener supporting both external file drops and internal track drops.
     *
     * @param targetComponent      component to accept drops
     * @param onDropCallback       handler for external files (optional, may be null)
     * @param onTracksDropCallback handler for internal tracks (optional, may be null)
     */
    public TrackDropTargetListener(JComponent targetComponent, OnDropCallback onDropCallback, OnTracksDropCallback onTracksDropCallback) {
        this.targetComponent = Objects.requireNonNull(targetComponent, "targetComponent cannot be null");
        this.onDropCallback = onDropCallback;
        this.onTracksDropCallback = onTracksDropCallback;
        this.originalBorder = targetComponent.getBorder();

        // Install Swing DropTarget
        new DropTarget(targetComponent, DnDConstants.ACTION_COPY, this, true);
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
            highlightBorder(true);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
        if (isDragAcceptable(dtde)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
        highlightBorder(false);
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        highlightBorder(false);

        // 1. Try track ID list drop (from TrackTablePanel)
        if (onTracksDropCallback != null && (dtde.isDataFlavorSupported(TrackTransferData.TRACK_ID_LIST_FLAVOR)
                || dtde.isDataFlavorSupported(DataFlavor.stringFlavor))) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                List<String> trackIds = TrackTransferData.extractTrackIds(dtde.getTransferable());
                dtde.dropComplete(true);
                if (!trackIds.isEmpty()) {
                    onTracksDropCallback.onTracksDropped(trackIds, dtde.getLocation());
                    return;
                }
            } catch (Exception e) {
                dtde.dropComplete(false);
                return;
            }
        }

        // 2. Try file list drop (from OS file manager)
        if (onDropCallback != null && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                Transferable transferable = dtde.getTransferable();
                List<?> files = (List<?>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                List<Path> paths = new ArrayList<>();
                for (Object obj : files) {
                    if (obj instanceof File file) {
                        paths.add(file.toPath());
                    }
                }

                dtde.dropComplete(true);

                if (!paths.isEmpty()) {
                    onDropCallback.onFilesDropped(paths, dtde.getLocation());
                }
            } catch (Exception e) {
                dtde.dropComplete(false);
            }
            return;
        }

        dtde.rejectDrop();
    }

    private boolean isDragAcceptable(DropTargetDragEvent dtde) {
        if (onDropCallback != null && dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return true;
        }
        if (onTracksDropCallback != null && (dtde.isDataFlavorSupported(TrackTransferData.TRACK_ID_LIST_FLAVOR)
                || dtde.isDataFlavorSupported(DataFlavor.stringFlavor))) {
            return true;
        }
        return false;
    }

    private void highlightBorder(boolean highlight) {
        if (highlight && !isHighlighted) {
            this.originalBorder = targetComponent.getBorder();
            targetComponent.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(HIGHLIGHT_BORDER_COLOR, 2),
                    BorderFactory.createEmptyBorder(2, 2, 2, 2)
            ));
            isHighlighted = true;
            targetComponent.repaint();
        } else if (!highlight && isHighlighted) {
            targetComponent.setBorder(originalBorder);
            isHighlighted = false;
            targetComponent.repaint();
        }
    }

    public boolean isHighlighted() {
        return isHighlighted;
    }
}
