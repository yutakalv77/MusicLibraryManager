package com.musiclibrary.service.storage;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Library;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.model.Track;
import com.musiclibrary.repository.AccountRepository;
import com.musiclibrary.repository.PlaylistRepository;
import com.musiclibrary.repository.SqliteAccountRepository;
import com.musiclibrary.repository.SqlitePlaylistRepository;
import com.musiclibrary.repository.SqliteTrackRepository;
import com.musiclibrary.repository.TrackRepository;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Hybrid storage implementation backed by SQLite database.
 * Supports bulk memory hydration on startup and lightweight pinpoint updates.
 */
public class SqliteLibraryStorage implements LibraryStorage {

    private static final System.Logger LOGGER = System.getLogger(SqliteLibraryStorage.class.getName());

    private final DatabaseManager dbManager;
    private final AccountRepository accountRepository;
    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final String accountId;

    public SqliteLibraryStorage(DatabaseManager dbManager) {
        this(dbManager, Account.DEFAULT_ACCOUNT_ID);
    }

    public SqliteLibraryStorage(DatabaseManager dbManager, String accountId) {
        this(dbManager,
                new SqliteAccountRepository(dbManager),
                new SqliteTrackRepository(dbManager),
                new SqlitePlaylistRepository(dbManager),
                accountId);
    }

    public SqliteLibraryStorage(DatabaseManager dbManager,
                                AccountRepository accountRepository,
                                TrackRepository trackRepository,
                                PlaylistRepository playlistRepository,
                                String accountId) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.trackRepository = Objects.requireNonNull(trackRepository, "trackRepository must not be null");
        this.playlistRepository = Objects.requireNonNull(playlistRepository, "playlistRepository must not be null");
        this.accountId = (accountId != null && !accountId.isBlank()) ? accountId.trim() : Account.DEFAULT_ACCOUNT_ID;
    }

    private final java.util.concurrent.atomic.AtomicBoolean schemaInitialized = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void ensureInitialized() {
        if (!schemaInitialized.get()) {
            synchronized (schemaInitialized) {
                if (!schemaInitialized.get()) {
                    try {
                        SchemaInitializer.initialize(dbManager);
                        schemaInitialized.set(true);
                    } catch (SQLException e) {
                        throw new com.musiclibrary.repository.RepositoryException("Failed to initialize database schema", e);
                    }
                }
            }
        }
    }

    /**
     * Loads all tracks and playlists for this account from SQLite and hydrates an in-memory Library instance.
     *
     * @param ignored ignored for SQLite storage (uses configured database path)
     * @return fully populated in-memory Library
     * @throws IOException if schema initialization or database reading fails
     */
    @Override
    public Library load(Path ignored) throws IOException {
        try {
            ensureInitialized();
            ensureAccountExists();
            List<Track> tracks = trackRepository.findByAccountId(accountId);
            List<Playlist> playlists = playlistRepository.findByAccountId(accountId);
            LOGGER.log(System.Logger.Level.INFO, "Loaded {0} tracks and {1} playlists for account: {2}",
                    tracks.size(), playlists.size(), accountId);
            return new Library(tracks, playlists);
        } catch (Exception e) {
            throw new IOException("Failed to load library from SQLite database: " + e.getMessage(), e);
        }
    }

    /**
     * Synchronizes all tracks and playlists in the provided Library to SQLite.
     *
     * @param library the Library to synchronize
     * @param ignored ignored for SQLite storage
     * @throws IOException if saving to SQLite fails
     */
    @Override
    public void save(Library library, Path ignored) throws IOException {
        if (library == null) {
            return;
        }

        try {
            ensureInitialized();
            ensureAccountExists();

            // 1. Bulk import all tracks
            List<Track> tracks = new ArrayList<>(library.getTracks());
            if (!tracks.isEmpty()) {
                trackRepository.importTracksBatch(accountId, tracks);
            }

            // 2. Save all playlists
            List<Playlist> playlists = library.getPlaylists();
            if (playlists != null) {
                for (Playlist pl : playlists) {
                    Playlist targetPl = pl.toBuilder().accountId(accountId).build();
                    playlistRepository.save(targetPl);
                }
            }

            LOGGER.log(System.Logger.Level.DEBUG, "Synchronized library to SQLite ({0} tracks, {1} playlists)",
                    tracks.size(), playlists != null ? playlists.size() : 0);

        } catch (Exception e) {
            throw new IOException("Failed to save library to SQLite database: " + e.getMessage(), e);
        }
    }

    /**
     * Performs a fast pinpoint update of rating and playback statistics for a single track.
     *
     * @param track the updated track
     */
    public void updateTrackUserData(Track track) {
        if (track == null || track.getAccountTrackId() == null || track.getAccountTrackId().isBlank()) {
            return;
        }
        ensureInitialized();
        trackRepository.updateUserData(track.getAccountTrackId(), track.getRating(), track.getPlayCount(), track.getLastPlayed());
    }

    /**
     * Saves or updates a playlist in the SQLite database.
     *
     * @param playlist the playlist to save
     */
    public void savePlaylist(Playlist playlist) {
        if (playlist == null) {
            return;
        }
        ensureInitialized();
        Playlist targetPl = playlist.toBuilder().accountId(accountId).build();
        playlistRepository.save(targetPl);
    }

    /**
     * Deletes a playlist from the SQLite database.
     *
     * @param playlistId unique identifier of the playlist to delete
     */
    public void deletePlaylist(String playlistId) {
        if (playlistId == null || playlistId.isBlank()) {
            return;
        }
        ensureInitialized();
        playlistRepository.deleteById(playlistId);
    }

    /**
     * Batch imports tracks into SQLite.
     *
     * @param tracks collection of tracks to import
     * @return list of imported tracks
     */
    public List<Track> importTracks(Collection<Track> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return List.of();
        }
        ensureInitialized();
        List<Track> list = new ArrayList<>(tracks);
        trackRepository.importTracksBatch(accountId, list);
        return list;
    }

    private void ensureAccountExists() {
        if (Account.DEFAULT_ACCOUNT_ID.equals(accountId)) {
            accountRepository.getOrCreateDefaultAccount();
        } else {
            accountRepository.findById(accountId).orElseGet(() -> {
                Account account = new Account(accountId, accountId);
                accountRepository.save(account);
                return account;
            });
        }
    }

    public String getAccountId() {
        return accountId;
    }

    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }

    public AccountRepository getAccountRepository() {
        return accountRepository;
    }

    public TrackRepository getTrackRepository() {
        return trackRepository;
    }

    public PlaylistRepository getPlaylistRepository() {
        return playlistRepository;
    }
}
