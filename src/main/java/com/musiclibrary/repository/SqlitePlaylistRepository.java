package com.musiclibrary.repository;

import com.musiclibrary.model.Account;
import com.musiclibrary.model.Playlist;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.DbUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite implementation of PlaylistRepository.
 */
public class SqlitePlaylistRepository implements PlaylistRepository {

    private final DatabaseManager dbManager;

    public SqlitePlaylistRepository(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
    }

    @Override
    public Optional<Playlist> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String playlistSql = "SELECT id, account_id, folder_id, name, position, created_at, updated_at " +
                             "FROM playlists WHERE id = ?";
        String tracksSql = "SELECT at.track_id " +
                           "FROM playlist_tracks pt " +
                           "INNER JOIN account_tracks at ON pt.account_track_id = at.id " +
                           "WHERE pt.playlist_id = ? " +
                           "ORDER BY pt.position ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement plStmt = conn.prepareStatement(playlistSql)) {
            plStmt.setString(1, id.trim());
            Playlist playlist = null;
            try (ResultSet rs = plStmt.executeQuery()) {
                if (rs.next()) {
                    playlist = mapPlaylistRow(rs);
                }
            }

            if (playlist == null) {
                return Optional.empty();
            }

            try (PreparedStatement trStmt = conn.prepareStatement(tracksSql)) {
                trStmt.setString(1, id.trim());
                try (ResultSet rs = trStmt.executeQuery()) {
                    while (rs.next()) {
                        String trackId = rs.getString("track_id");
                        if (trackId != null && !trackId.isBlank()) {
                            playlist.addTrack(trackId);
                        }
                    }
                }
            }

            return Optional.of(playlist);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find playlist by id: " + id, e);
        }
    }

    @Override
    public List<Playlist> findByAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return List.of();
        }
        String sql = "SELECT " +
                     "p.id AS playlist_id, p.account_id, p.folder_id, p.name AS playlist_name, " +
                     "p.position AS playlist_position, p.created_at AS playlist_created_at, " +
                     "p.updated_at AS playlist_updated_at, " +
                     "at.track_id, pt.position AS track_position " +
                     "FROM playlists p " +
                     "LEFT JOIN playlist_tracks pt ON p.id = pt.playlist_id " +
                     "LEFT JOIN account_tracks at ON pt.account_track_id = at.id " +
                     "WHERE p.account_id = ? " +
                     "ORDER BY p.position ASC, p.id ASC, pt.position ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, Playlist> playlistMap = new LinkedHashMap<>();
                while (rs.next()) {
                    String playlistId = rs.getString("playlist_id");
                    Playlist playlist = playlistMap.computeIfAbsent(playlistId, k -> {
                        try {
                            String name = rs.getString("playlist_name");
                            String accId = rs.getString("account_id");
                            String folderId = rs.getString("folder_id");
                            int position = rs.getInt("playlist_position");
                            Instant createdAt = DbUtils.parseInstant(rs.getString("playlist_created_at"));
                            Instant updatedAt = DbUtils.parseInstant(rs.getString("playlist_updated_at"));
                            return new Playlist(playlistId, name, accId, folderId, position, new ArrayList<>(), createdAt, updatedAt);
                        } catch (SQLException ex) {
                            throw new RepositoryException("Error mapping playlist: " + playlistId, ex);
                        }
                    });

                    String trackId = rs.getString("track_id");
                    if (trackId != null && !trackId.isBlank()) {
                        playlist.addTrack(trackId);
                    }
                }
                return new ArrayList<>(playlistMap.values());
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find playlists by accountId: " + accountId, e);
        }
    }

    @Override
    public void save(Playlist playlist) {
        Objects.requireNonNull(playlist, "playlist must not be null");

        String upsertPlaylistSql = "INSERT INTO playlists (" +
                "id, account_id, folder_id, name, position, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "account_id = excluded.account_id, " +
                "folder_id = excluded.folder_id, " +
                "name = excluded.name, " +
                "position = excluded.position, " +
                "updated_at = excluded.updated_at";

        String deleteTracksSql = "DELETE FROM playlist_tracks WHERE playlist_id = ?";

        String insertTrackSql = "INSERT INTO playlist_tracks (playlist_id, account_track_id, position) " +
                "SELECT ?, at.id, ? " +
                "FROM account_tracks at " +
                "WHERE at.account_id = ? AND at.track_id = ?";

        String effectiveAccountId = (playlist.getAccountId() != null && !playlist.getAccountId().isBlank())
                ? playlist.getAccountId()
                : Account.DEFAULT_ACCOUNT_ID;

        try (Connection conn = dbManager.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // 1. Upsert playlist master record
                try (PreparedStatement stmt = conn.prepareStatement(upsertPlaylistSql)) {
                    stmt.setString(1, playlist.getId());
                    stmt.setString(2, effectiveAccountId);
                    if (playlist.getFolderId() != null && !playlist.getFolderId().isBlank()) {
                        stmt.setString(3, playlist.getFolderId().trim());
                    } else {
                        stmt.setNull(3, Types.VARCHAR);
                    }
                    stmt.setString(4, playlist.getName());
                    stmt.setInt(5, playlist.getPosition());
                    stmt.setString(6, playlist.getCreatedAt().toString());
                    stmt.setString(7, playlist.getUpdatedAt().toString());
                    stmt.executeUpdate();
                }

                // 2. Delete existing playlist tracks
                try (PreparedStatement stmt = conn.prepareStatement(deleteTracksSql)) {
                    stmt.setString(1, playlist.getId());
                    stmt.executeUpdate();
                }

                // 3. Batch insert new playlist tracks linking to account_tracks
                List<String> trackIds = playlist.getTrackIds();
                if (trackIds != null && !trackIds.isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement(insertTrackSql)) {
                        for (int pos = 0; pos < trackIds.size(); pos++) {
                            String trackId = trackIds.get(pos);
                            stmt.setString(1, playlist.getId());
                            stmt.setInt(2, pos);
                            stmt.setString(3, effectiveAccountId);
                            stmt.setString(4, trackId);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save playlist: " + playlist.getId(), e);
        }
    }

    @Override
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String sql = "DELETE FROM playlists WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete playlist by id: " + id, e);
        }
    }

    private static Playlist mapPlaylistRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String accountId = rs.getString("account_id");
        String folderId = rs.getString("folder_id");
        String name = rs.getString("name");
        int position = rs.getInt("position");
        Instant createdAt = DbUtils.parseInstant(rs.getString("created_at"));
        Instant updatedAt = DbUtils.parseInstant(rs.getString("updated_at"));
        return new Playlist(id, name, accountId, folderId, position, new ArrayList<>(), createdAt, updatedAt);
    }
}
