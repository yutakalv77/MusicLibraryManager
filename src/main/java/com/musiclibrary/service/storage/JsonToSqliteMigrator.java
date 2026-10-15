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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service to automatically migrate legacy JSON library data to the SQLite database.
 */
public class JsonToSqliteMigrator {

    private static final System.Logger LOGGER = System.getLogger(JsonToSqliteMigrator.class.getName());
    private static final DateTimeFormatter BACKUP_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC);

    private final DatabaseManager dbManager;
    private final AccountRepository accountRepository;
    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final JsonLibraryStorage jsonStorage;

    public JsonToSqliteMigrator(DatabaseManager dbManager) {
        this(dbManager,
                new SqliteAccountRepository(dbManager),
                new SqliteTrackRepository(dbManager),
                new SqlitePlaylistRepository(dbManager));
    }

    public JsonToSqliteMigrator(DatabaseManager dbManager,
                                AccountRepository accountRepository,
                                TrackRepository trackRepository,
                                PlaylistRepository playlistRepository) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.trackRepository = Objects.requireNonNull(trackRepository, "trackRepository must not be null");
        this.playlistRepository = Objects.requireNonNull(playlistRepository, "playlistRepository must not be null");
        this.jsonStorage = new JsonLibraryStorage();
    }

    /**
     * Checks if migration is necessary.
     * Returns true if jsonPath exists, is a non-empty regular file, and the target account
     * currently contains 0 tracks and 0 playlists.
     *
     * @param jsonPath  path to the JSON library file
     * @param accountId target account identifier (or default if null/blank)
     * @return true if migration is needed, false otherwise
     */
    public boolean isMigrationNeeded(Path jsonPath, String accountId) {
        if (jsonPath == null || !Files.exists(jsonPath) || !Files.isRegularFile(jsonPath)) {
            return false;
        }
        try {
            if (Files.size(jsonPath) == 0) {
                return false;
            }
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to read file size of " + jsonPath, e);
            return false;
        }

        String targetAccountId = (accountId != null && !accountId.isBlank()) ? accountId.trim() : Account.DEFAULT_ACCOUNT_ID;
        try {
            List<Track> existingTracks = trackRepository.findByAccountId(targetAccountId);
            if (!existingTracks.isEmpty()) {
                return false;
            }
            List<Playlist> existingPlaylists = playlistRepository.findByAccountId(targetAccountId);
            return existingPlaylists.isEmpty();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to check existing library data for migration: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Executes migration if needed; returns a skipped result if migration is not needed.
     *
     * @param jsonPath  path to the JSON library file
     * @param accountId target account identifier
     * @return MigrationResult
     */
    public MigrationResult migrateIfNeeded(Path jsonPath, String accountId) {
        if (!isMigrationNeeded(jsonPath, accountId)) {
            return MigrationResult.skipped("Migration not required for " + (jsonPath != null ? jsonPath : "null"));
        }
        return migrate(jsonPath, accountId);
    }

    /**
     * Performs lossless migration from JSON to SQLite and backs up the original JSON file.
     *
     * @param jsonPath  path to the JSON library file
     * @param accountId target account identifier
     * @return MigrationResult
     */
    public MigrationResult migrate(Path jsonPath, String accountId) {
        if (jsonPath == null) {
            return MigrationResult.failure("JSON file path cannot be null", new IllegalArgumentException("jsonPath is null"));
        }

        String targetAccountId = (accountId != null && !accountId.isBlank()) ? accountId.trim() : Account.DEFAULT_ACCOUNT_ID;

        if (!Files.exists(jsonPath) || !Files.isRegularFile(jsonPath)) {
            return MigrationResult.skipped("JSON file not found: " + jsonPath);
        }

        try {
            if (Files.size(jsonPath) == 0) {
                return MigrationResult.skipped("JSON file is empty: " + jsonPath);
            }
        } catch (IOException e) {
            return MigrationResult.failure("Failed to read JSON file attributes: " + jsonPath, e);
        }

        LOGGER.log(System.Logger.Level.INFO, "Starting migration from {0} to SQLite account {1}...", jsonPath, targetAccountId);

        try {
            // 1. Ensure target account exists
            ensureAccountExists(targetAccountId);

            // 2. Load existing Library from JSON
            Library library = jsonStorage.load(jsonPath);
            List<Track> tracks = new ArrayList<>(library.getTracks());
            List<Playlist> playlists = library.getPlaylists();

            // 3. Import tracks into SQLite
            if (!tracks.isEmpty()) {
                trackRepository.importTracksBatch(targetAccountId, tracks);
                LOGGER.log(System.Logger.Level.INFO, "Migrated {0} tracks to account {1}", tracks.size(), targetAccountId);
            }

            // 4. Import playlists into SQLite
            int playlistsCount = 0;
            if (playlists != null) {
                for (Playlist playlist : playlists) {
                    Playlist targetPl = playlist.toBuilder()
                            .accountId(targetAccountId)
                            .build();
                    playlistRepository.save(targetPl);
                    playlistsCount++;
                }
                LOGGER.log(System.Logger.Level.INFO, "Migrated {0} playlists to account {1}", playlistsCount, targetAccountId);
            }

            // 5. Safely rename JSON file to backup file
            Path backupPath = resolveBackupPath(jsonPath);
            moveFileSafely(jsonPath, backupPath);
            LOGGER.log(System.Logger.Level.INFO, "JSON library file backed up to: {0}", backupPath);

            return MigrationResult.success(tracks.size(), playlistsCount, backupPath);

        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Data migration failed for " + jsonPath + ": " + e.getMessage(), e);
            return MigrationResult.failure("Migration failed: " + e.getMessage(), e);
        }
    }

    private void ensureAccountExists(String accountId) {
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

    private static Path resolveBackupPath(Path jsonPath) {
        Path dir = jsonPath.getParent();
        String filename = jsonPath.getFileName().toString();
        Path defaultBak = (dir != null) ? dir.resolve(filename + ".bak") : Path.of(filename + ".bak");
        if (!Files.exists(defaultBak)) {
            return defaultBak;
        }

        String ts = BACKUP_TIMESTAMP_FMT.format(Instant.now());
        Path timestampBak = (dir != null) ? dir.resolve(filename + ".bak." + ts) : Path.of(filename + ".bak." + ts);
        if (!Files.exists(timestampBak)) {
            return timestampBak;
        }

        return (dir != null) ? dir.resolve(filename + ".bak." + System.currentTimeMillis()) : Path.of(filename + ".bak." + System.currentTimeMillis());
    }

    private static void moveFileSafely(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
