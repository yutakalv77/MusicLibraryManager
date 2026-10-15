package com.musiclibrary.controller;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.model.TrackMetadataUpdate;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.service.metadata.Mp3TagWriter;
import com.musiclibrary.service.metadata.TagWriter;
import com.musiclibrary.service.search.FastSearchIndex;
import com.musiclibrary.service.storage.LibraryStorage;
import com.musiclibrary.service.storage.MissingTrackResolver;
import com.musiclibrary.ui.panel.SidebarPanel;
import com.musiclibrary.ui.panel.TrackTablePanel;
import static com.musiclibrary.util.SwingUtils.runOnEdt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Controller mediating between domain {@link Library}, {@link SidebarPanel},
 * {@link TrackTablePanel}, {@link FastSearchIndex}, and persistence via {@link LibraryStorage}.
 * Orchestrates playlist management (CRUD, reordering, drag-and-drop addition) and navigation views.
 */
public class LibraryController {

    /**
     * Listener for section and playlist navigation changes.
     */
    public interface ViewNavigationListener {
        void onSectionChanged(SidebarPanel.LibrarySection section);
        void onPlaylistChanged(String playlistId);
    }

    private final Library library;
    private final SidebarPanel sidebarPanel;
    private final TrackTablePanel trackTablePanel;
    private volatile LibraryStorage storage;
    private final Path storagePath;
    private final FastSearchIndex searchIndex;
    private final MissingTrackResolver missingTrackResolver;
    private final TagWriter tagWriter;

    private final List<ViewNavigationListener> navigationListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> libraryContentChangeListeners = new CopyOnWriteArrayList<>();

    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LibraryController-Save-Thread");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService tagExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LibraryController-Tag-Thread");
        t.setDaemon(true);
        return t;
    });

    private SidebarPanel.LibrarySection currentSection = SidebarPanel.LibrarySection.ALL_SONGS;
    private String currentPlaylistId = null;

    private Consumer<List<Track>> onPlayTracksAction;
    private volatile boolean disposed = false;

    public LibraryController(Library library,
                             SidebarPanel sidebarPanel,
                             TrackTablePanel trackTablePanel,
                             LibraryStorage storage,
                             Path storagePath,
                             FastSearchIndex searchIndex) {
        this(library, sidebarPanel, trackTablePanel, storage, storagePath, searchIndex, null, null);
    }

    public LibraryController(Library library,
                             SidebarPanel sidebarPanel,
                             TrackTablePanel trackTablePanel,
                             LibraryStorage storage,
                             Path storagePath,
                             FastSearchIndex searchIndex,
                             MissingTrackResolver missingTrackResolver) {
        this(library, sidebarPanel, trackTablePanel, storage, storagePath, searchIndex, missingTrackResolver, null);
    }

    public LibraryController(Library library,
                             SidebarPanel sidebarPanel,
                             TrackTablePanel trackTablePanel,
                             LibraryStorage storage,
                             Path storagePath,
                             FastSearchIndex searchIndex,
                             MissingTrackResolver missingTrackResolver,
                             TagWriter tagWriter) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.sidebarPanel = Objects.requireNonNull(sidebarPanel, "sidebarPanel cannot be null");
        this.trackTablePanel = Objects.requireNonNull(trackTablePanel, "trackTablePanel cannot be null");
        this.storage = storage;
        this.storagePath = storagePath;
        this.searchIndex = searchIndex;
        this.missingTrackResolver = (missingTrackResolver != null) ? missingTrackResolver : new MissingTrackResolver();
        this.tagWriter = (tagWriter != null) ? tagWriter : new Mp3TagWriter();

        wireUiListeners();
        syncPlaylistsToSidebar();
    }

    private void wireUiListeners() {
        this.sidebarPanel.setOnSectionSelectedAction(this::showLibrarySection);
        this.sidebarPanel.setOnPlaylistSelectedAction(p -> showPlaylist(p.getId()));
        this.sidebarPanel.setOnPlaylistPlayAction(this::playPlaylist);
        this.sidebarPanel.setOnTracksDroppedOnPlaylistAction((playlist, trackIds) -> {
            addTracksToPlaylist(playlist.getId(), trackIds);
        });
        this.trackTablePanel.setOnDeleteTracksAction(this::deleteTracks);
    }

    // --- Playlist CRUD Operations ---

    /**
     * Creates a new playlist with the specified name, adds it to library, and saves storage.
     *
     * @param name playlist name (falls back to localized default if blank)
     * @return created playlist
     */
    public Playlist createPlaylist(String name) {
        String finalName = (name != null && !name.isBlank())
                ? name.trim()
                : I18nManager.getInstance().get("sidebar.playlist.default_name");

        Playlist playlist = library.createPlaylist(finalName);
        runOnEdt(() -> {
            sidebarPanel.setPlaylists(library.getPlaylists());
            sidebarPanel.selectPlaylist(playlist.getId());
        });
        showPlaylist(playlist.getId());
        saveLibraryAsync();
        return playlist;
    }

    /**
     * Deletes a playlist by ID. If currently viewing this playlist, falls back to ALL_SONGS.
     *
     * @param playlistId unique playlist ID
     * @return true if deleted
     */
    public boolean deletePlaylist(String playlistId) {
        boolean removed = library.deletePlaylist(playlistId);
        if (removed) {
            runOnEdt(() -> {
                sidebarPanel.setPlaylists(library.getPlaylists());
                if (Objects.equals(currentPlaylistId, playlistId)) {
                    sidebarPanel.selectSection(SidebarPanel.LibrarySection.ALL_SONGS);
                }
            });
            if (Objects.equals(currentPlaylistId, playlistId)) {
                showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
            }
            saveLibraryAsync();
        }
        return removed;
    }

    /**
     * Renames an existing playlist.
     *
     * @param playlistId playlist ID
     * @param newName    new playlist name
     * @return true if renamed
     */
    public boolean renamePlaylist(String playlistId, String newName) {
        Playlist playlist = library.getPlaylist(playlistId);
        if (playlist == null) {
            return false;
        }

        String validName = (newName != null && !newName.isBlank())
                ? newName.trim()
                : I18nManager.getInstance().get("sidebar.playlist.default_name");

        playlist.setName(validName);
        runOnEdt(() -> {
            sidebarPanel.setPlaylists(library.getPlaylists());
            if (Objects.equals(currentPlaylistId, playlistId)) {
                sidebarPanel.selectPlaylist(playlistId);
            }
        });
        saveLibraryAsync();
        return true;
    }

    /**
     * Appends track IDs to a playlist and refreshes current view if viewing that playlist.
     *
     * @param playlistId target playlist ID
     * @param trackIds   list of track IDs to add
     * @return number of tracks added
     */
    public int addTracksToPlaylist(String playlistId, List<String> trackIds) {
        Playlist playlist = library.getPlaylist(playlistId);
        if (playlist == null || trackIds == null || trackIds.isEmpty()) {
            return 0;
        }

        int addedCount = 0;
        for (String trackId : trackIds) {
            if (trackId != null && !trackId.isBlank() && library.getTrack(trackId) != null) {
                playlist.addTrack(trackId);
                addedCount++;
            }
        }

        if (addedCount > 0) {
            if (Objects.equals(currentPlaylistId, playlistId)) {
                showPlaylist(playlistId);
            }
            saveLibraryAsync();
        }
        return addedCount;
    }

    /**
     * Removes a track ID occurrence from a playlist.
     *
     * @param playlistId target playlist ID
     * @param trackId    track ID to remove
     * @return true if removed
     */
    public boolean removeTrackFromPlaylist(String playlistId, String trackId) {
        Playlist playlist = library.getPlaylist(playlistId);
        if (playlist == null || trackId == null) {
            return false;
        }

        boolean removed = playlist.removeTrack(trackId);
        if (removed) {
            if (Objects.equals(currentPlaylistId, playlistId)) {
                showPlaylist(playlistId);
            }
            saveLibraryAsync();
        }
        return removed;
    }

    /**
     * Reorders a track within a playlist from one index to another.
     *
     * @param playlistId target playlist ID
     * @param fromIndex  source index
     * @param toIndex    destination index
     * @return true if reordered
     */
    public boolean reorderTracksInPlaylist(String playlistId, int fromIndex, int toIndex) {
        Playlist playlist = library.getPlaylist(playlistId);
        if (playlist == null) {
            return false;
        }

        try {
            playlist.reorder(fromIndex, toIndex);
            if (Objects.equals(currentPlaylistId, playlistId)) {
                showPlaylist(playlistId);
            }
            saveLibraryAsync();
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    // --- Navigation Views ---

    /**
     * Displays a library section (All Songs, Artists, Albums, Recently Added) on TrackTablePanel.
     *
     * @param section target section
     */
    public void showLibrarySection(SidebarPanel.LibrarySection section) {
        this.currentSection = (section != null) ? section : SidebarPanel.LibrarySection.ALL_SONGS;
        this.currentPlaylistId = null;
        this.trackTablePanel.setPlaylistContext(false);

        List<Track> tracks = new ArrayList<>(library.getTracks());
        switch (this.currentSection) {
            case ARTISTS -> tracks.sort(Comparator.comparing(Track::getArtist, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Track::getAlbum, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Track::getTrackNumber));
            case ALBUMS -> tracks.sort(Comparator.comparing(Track::getAlbum, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(Track::getTrackNumber)
                    .thenComparing(Track::getTitle, String.CASE_INSENSITIVE_ORDER));
            case RECENTLY_ADDED -> tracks.sort(Comparator.comparing(Track::getDateAdded, Comparator.nullsLast(Comparator.reverseOrder())));
            case ALL_SONGS -> {
                // Natural order
            }
        }

        trackTablePanel.setTracks(tracks);
        notifySectionChanged(this.currentSection);
    }

    /**
     * Displays a user playlist on TrackTablePanel maintaining track sequence.
     *
     * @param playlistId playlist ID
     */
    public void showPlaylist(String playlistId) {
        Playlist playlist = library.getPlaylist(playlistId);
        if (playlist == null) {
            showLibrarySection(SidebarPanel.LibrarySection.ALL_SONGS);
            return;
        }

        this.currentSection = null;
        this.currentPlaylistId = playlistId;
        this.trackTablePanel.setPlaylistContext(true);

        List<Track> playlistTracks = new ArrayList<>();
        for (String trackId : playlist.getTrackIds()) {
            Track t = library.getTrack(trackId);
            if (t != null) {
                playlistTracks.add(t);
            }
        }

        trackTablePanel.setTracks(playlistTracks);
        notifyPlaylistChanged(playlistId);
    }

    /**
     * Displays tracks belonging to a specific album on TrackTablePanel.
     *
     * @param album album whose tracks to display
     */
    public void showAlbum(Album album) {
        if (album == null) {
            return;
        }
        this.currentSection = SidebarPanel.LibrarySection.ALBUMS;
        this.currentPlaylistId = null;
        this.trackTablePanel.setPlaylistContext(false);
        this.trackTablePanel.setTracks(album.getTracks());
    }

    public void addNavigationListener(ViewNavigationListener listener) {
        if (listener != null) {
            navigationListeners.add(listener);
        }
    }

    public void removeNavigationListener(ViewNavigationListener listener) {
        navigationListeners.remove(listener);
    }

    private void notifySectionChanged(SidebarPanel.LibrarySection section) {
        for (ViewNavigationListener listener : navigationListeners) {
            try {
                listener.onSectionChanged(section);
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyPlaylistChanged(String playlistId) {
        for (ViewNavigationListener listener : navigationListeners) {
            try {
                listener.onPlaylistChanged(playlistId);
            } catch (Throwable ignored) {
            }
        }
    }

    public void addLibraryContentChangeListener(Runnable listener) {
        if (listener != null) {
            libraryContentChangeListeners.add(listener);
        }
    }

    public void removeLibraryContentChangeListener(Runnable listener) {
        libraryContentChangeListeners.remove(listener);
    }

    private void notifyLibraryContentChanged() {
        for (Runnable listener : libraryContentChangeListeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Refreshes the currently displayed view (section or playlist).
     */
    public void refreshCurrentView() {
        if (currentPlaylistId != null) {
            showPlaylist(currentPlaylistId);
        } else {
            showLibrarySection(currentSection);
        }
    }

    // --- Deletion Handling ---

    /**
     * Deletes the currently selected tracks with context sensitivity:
     * - In a Playlist: removes from the playlist only (non-destructive).
     * - In a Library section: removes from library, cascading to all playlists and search index.
     */
    public void deleteSelectedTracks() {
        deleteTracks(trackTablePanel.getSelectedTracks());
    }

    /**
     * Deletes specified tracks with context sensitivity.
     *
     * @param tracks tracks to delete
     */
    public void deleteTracks(List<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        if (currentPlaylistId != null) {
            Playlist playlist = library.getPlaylist(currentPlaylistId);
            if (playlist != null) {
                for (Track t : tracks) {
                    playlist.removeTrack(t.getId());
                }
                showPlaylist(currentPlaylistId);
                saveLibraryAsync();
            }
        } else {
            for (Track t : tracks) {
                library.removeTrack(t.getId());
                if (searchIndex != null) {
                    searchIndex.removeTrack(t.getId());
                }
            }
            refreshCurrentView();
            saveLibraryAsync();
            notifyLibraryContentChanged();
        }
    }

    // --- Playback Integration ---

    /**
     * Begins playback of a playlist.
     *
     * @param playlist playlist to play
     */
    public void playPlaylist(Playlist playlist) {
        if (playlist == null) {
            return;
        }

        List<Track> playlistTracks = new ArrayList<>();
        for (String id : playlist.getTrackIds()) {
            Track t = library.getTrack(id);
            if (t != null) {
                playlistTracks.add(t);
            }
        }

        if (!playlistTracks.isEmpty() && onPlayTracksAction != null) {
            onPlayTracksAction.accept(playlistTracks);
        }
    }

    public void setOnPlayTracksAction(Consumer<List<Track>> onPlayTracksAction) {
        this.onPlayTracksAction = onPlayTracksAction;
    }

    public void syncPlaylistsToSidebar() {
        runOnEdt(() -> sidebarPanel.setPlaylists(library.getPlaylists()));
    }

    /**
     * Asynchronously saves library to storage.
     */
    public void saveLibraryAsync() {
        if (storage != null && storagePath != null && !disposed) {
            saveExecutor.submit(() -> {
                try {
                    storage.save(library, storagePath);
                } catch (Exception e) {
                    System.err.println("LibraryController: failed to save library: " + e.getMessage());
                }
            });
        }
    }

    // --- Missing Track Resolution ---

    /**
     * Identifies all tracks in the library whose backing files are missing on disk.
     *
     * @return list of missing tracks
     */
    public List<Track> getMissingTracks() {
        return missingTrackResolver.findMissingTracks(library.getTracks());
    }

    /**
     * Relinks a missing track to a new audio file location.
     *
     * @param track   track entity to relink
     * @param newPath new resolved audio file path
     * @return true if successfully relinked
     */
    public boolean relinkTrack(Track track, Path newPath) {
        if (track == null || newPath == null) {
            return false;
        }

        boolean relinked = missingTrackResolver.relink(track, newPath);
        if (relinked) {
            if (searchIndex != null) {
                searchIndex.indexTrack(track);
            }
            refreshCurrentView();
            saveLibraryAsync();
            notifyLibraryContentChanged();
        }
        return relinked;
    }

    /**
     * Asynchronously scans candidate directory and relinks missing tracks.
     *
     * @param searchFolder directory to scan
     * @param listener     progress and completion observer
     * @return task handle supporting cancellation
     */
    public MissingTrackResolver.ResolveTask resolveMissingTracksAsync(Path searchFolder,
                                                                      MissingTrackResolver.ResolveProgressListener listener) {
        List<Track> missing = getMissingTracks();
        return missingTrackResolver.resolveMissingTracksAsync(missing, searchFolder, new MissingTrackResolver.ResolveProgressListener() {
            @Override
            public void onProgress(int current, int total, Path currentPath) {
                runOnEdt(() -> {
                    if (listener != null) {
                        listener.onProgress(current, total, currentPath);
                    }
                });
            }

            @Override
            public void onRelinked(Track track, Path newPath) {
                if (searchIndex != null) {
                    searchIndex.indexTrack(track);
                }
                runOnEdt(() -> {
                    refreshCurrentView();
                    if (listener != null) {
                        listener.onRelinked(track, newPath);
                    }
                });
                saveLibraryAsync();
            }

            @Override
            public void onComplete(int relinkedCount, int remainingCount) {
                runOnEdt(() -> {
                    refreshCurrentView();
                    if (listener != null) {
                        listener.onComplete(relinkedCount, remainingCount);
                    }
                });
                saveLibraryAsync();
            }

            @Override
            public void onError(Throwable error) {
                runOnEdt(() -> {
                    if (listener != null) {
                        listener.onError(error);
                    }
                });
            }
        });
    }

    /**
     * Asynchronously writes updated metadata to the track's audio file and synchronizes library state.
     *
     * @param track       track entity to update
     * @param update      metadata changes
     * @param onCompleted optional completion callback invoked on EDT
     * @param onError     optional error callback invoked on EDT
     */
    public void updateTrackMetadata(Track track,
                                    TrackMetadataUpdate update,
                                    Runnable onCompleted,
                                    Consumer<Throwable> onError) {
        if (track == null || update == null) {
            return;
        }
        updateTracksMetadata(List.of(track), update, onCompleted, onError);
    }

    /**
     * Asynchronously writes updated metadata to multiple tracks' audio files and synchronizes library state.
     *
     * @param tracks      collection of tracks to update
     * @param update      metadata changes
     * @param onCompleted optional completion callback invoked on EDT
     * @param onError     optional error callback invoked on EDT
     */
    public void updateTracksMetadata(List<Track> tracks,
                                     TrackMetadataUpdate update,
                                     Runnable onCompleted,
                                     Consumer<Throwable> onError) {
        if (tracks == null || tracks.isEmpty() || update == null) {
            if (onCompleted != null) {
                runOnEdt(onCompleted);
            }
            return;
        }

        tagExecutor.submit(() -> {
            try {
                for (Track track : tracks) {
                    if (track.getFilePath() != null && !track.getFilePath().isBlank()) {
                        Path p = Path.of(track.getFilePath());
                        if (Files.exists(p) && tagWriter != null && tagWriter.supports(p)) {
                            tagWriter.write(p, update);
                            try {
                                track.setFileSizeBytes(Files.size(p));
                                track.setLastModified(Files.getLastModifiedTime(p).toInstant());
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    update.applyTo(track);
                    if (update.isUpdateArtwork()) {
                        ArtworkCache.getInstance().evict(track.getId());
                    }
                }

                runOnEdt(() -> {
                    if (searchIndex != null) {
                        searchIndex.indexTracks(library.getTracks());
                    }
                    refreshCurrentView();
                    if (onCompleted != null) {
                        onCompleted.run();
                    }
                });

                saveLibraryAsync();
                notifyLibraryContentChanged();
            } catch (Throwable t) {
                runOnEdt(() -> {
                    if (onError != null) {
                        onError.accept(t);
                    }
                });
            }
        });
    }

    /**
     * Releases controller resources, shuts down background save executor and resolver executor.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        saveExecutor.shutdown();
        tagExecutor.shutdown();
        missingTrackResolver.shutdown();
        navigationListeners.clear();
    }

    // --- Getters ---

    public Library getLibrary() {
        return library;
    }

    public SidebarPanel getSidebarPanel() {
        return sidebarPanel;
    }

    public TrackTablePanel getTrackTablePanel() {
        return trackTablePanel;
    }

    public SidebarPanel.LibrarySection getCurrentSection() {
        return currentSection;
    }

    public String getCurrentPlaylistId() {
        return currentPlaylistId;
    }

    public boolean isInPlaylistContext() {
        return currentPlaylistId != null;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public MissingTrackResolver getMissingTrackResolver() {
        return missingTrackResolver;
    }

    public TagWriter getTagWriter() {
        return tagWriter;
    }

    public LibraryStorage getStorage() {
        return storage;
    }

    public void setStorage(LibraryStorage storage) {
        this.storage = storage;
    }
}
