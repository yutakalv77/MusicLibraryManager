package com.musiclibrary.ui.dnd;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Transferable implementation for transporting audio track ID lists during Drag and Drop operations.
 * Supports both high-fidelity {@link #TRACK_ID_LIST_FLAVOR} and standard {@link DataFlavor#stringFlavor}.
 */
public class TrackTransferData implements Transferable {

    public static final DataFlavor TRACK_ID_LIST_FLAVOR = new DataFlavor(List.class, "application/x-track-ids");

    private static final DataFlavor[] SUPPORTED_FLAVORS = {
            TRACK_ID_LIST_FLAVOR,
            DataFlavor.stringFlavor
    };

    private final List<String> trackIds;

    public TrackTransferData(List<String> trackIds) {
        this.trackIds = trackIds != null ? new ArrayList<>(trackIds) : Collections.emptyList();
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return SUPPORTED_FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor f : SUPPORTED_FLAVORS) {
            if (f.equals(flavor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (TRACK_ID_LIST_FLAVOR.equals(flavor)) {
            return Collections.unmodifiableList(trackIds);
        } else if (DataFlavor.stringFlavor.equals(flavor)) {
            return String.join("\n", trackIds);
        }
        throw new UnsupportedFlavorException(flavor);
    }

    /**
     * Extracts track IDs from a transferable supporting either TRACK_ID_LIST_FLAVOR or stringFlavor.
     *
     * @param transferable transfer object
     * @return extracted list of track IDs, or empty list
     */
    public static List<String> extractTrackIds(Transferable transferable) {
        if (transferable == null) {
            return Collections.emptyList();
        }
        try {
            if (transferable.isDataFlavorSupported(TRACK_ID_LIST_FLAVOR)) {
                Object data = transferable.getTransferData(TRACK_ID_LIST_FLAVOR);
                if (data instanceof List<?> list) {
                    List<String> ids = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof String s && !s.isBlank()) {
                            ids.add(s);
                        }
                    }
                    return ids;
                }
            }
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String str = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                if (str != null && !str.isBlank()) {
                    return Arrays.stream(str.split("\\r?\\n"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                }
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }
}
