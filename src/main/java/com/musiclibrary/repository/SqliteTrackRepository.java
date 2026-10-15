package com.musiclibrary.repository;

import com.musiclibrary.model.Track;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.DbUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of TrackRepository.
 */
public class SqliteTrackRepository implements TrackRepository {

    private final DatabaseManager dbManager;

    public SqliteTrackRepository(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
    }

    @Override
    public Optional<Track> findById(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, file_path, title, artist, album_artist, album, genre, " +
                     "year, track_number, disc_number, bpm, bitrate, duration_seconds, " +
                     "file_size_bytes, file_last_modified, has_artwork, created_at, updated_at " +
                     "FROM tracks WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMasterRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find track by id: " + trackId, e);
        }
    }

    @Override
    public Optional<Track> findByFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, file_path, title, artist, album_artist, album, genre, " +
                     "year, track_number, disc_number, bpm, bitrate, duration_seconds, " +
                     "file_size_bytes, file_last_modified, has_artwork, created_at, updated_at " +
                     "FROM tracks WHERE file_path = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, filePath.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapMasterRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find track by file_path: " + filePath, e);
        }
    }

    @Override
    public Optional<Track> findByAccountAndTrackId(String accountId, String trackId) {
        if (accountId == null || accountId.isBlank() || trackId == null || trackId.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT " +
                     "at.id AS account_track_id, at.account_id, at.rating, at.play_count, " +
                     "at.date_added, at.last_played_at, " +
                     "t.id AS track_id, t.file_path, t.title, t.artist, t.album_artist, t.album, t.genre, " +
                     "t.year, t.track_number, t.disc_number, t.bpm, t.bitrate, t.duration_seconds, " +
                     "t.file_size_bytes, t.file_last_modified, t.has_artwork, t.created_at, t.updated_at " +
                     "FROM account_tracks at " +
                     "INNER JOIN tracks t ON at.track_id = t.id " +
                     "WHERE at.account_id = ? AND at.track_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.trim());
            stmt.setString(2, trackId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapJoinedRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find track by accountId: " + accountId + " and trackId: " + trackId, e);
        }
    }

    @Override
    public List<Track> findByAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return List.of();
        }
        String sql = "SELECT " +
                     "at.id AS account_track_id, at.account_id, at.rating, at.play_count, " +
                     "at.date_added, at.last_played_at, " +
                     "t.id AS track_id, t.file_path, t.title, t.artist, t.album_artist, t.album, t.genre, " +
                     "t.year, t.track_number, t.disc_number, t.bpm, t.bitrate, t.duration_seconds, " +
                     "t.file_size_bytes, t.file_last_modified, t.has_artwork, t.created_at, t.updated_at " +
                     "FROM account_tracks at " +
                     "INNER JOIN tracks t ON at.track_id = t.id " +
                     "WHERE at.account_id = ? " +
                     "ORDER BY t.artist COLLATE NOCASE ASC, t.album COLLATE NOCASE ASC, t.disc_number ASC, t.track_number ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                List<Track> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapJoinedRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to load tracks for accountId: " + accountId, e);
        }
    }

    @Override
    public Track importTrack(String accountId, Track track) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(track, "track must not be null");

        try (Connection conn = dbManager.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                Track saved = importTrackInternal(conn, accountId, track);
                conn.commit();
                return saved;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to import track: " + track.getFilePath(), e);
        }
    }

    @Override
    public void importTracksBatch(String accountId, List<Track> tracks) {
        if (accountId == null || accountId.isBlank() || tracks == null || tracks.isEmpty()) {
            return;
        }

        try (Connection conn = dbManager.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                for (Track track : tracks) {
                    importTrackInternal(conn, accountId, track);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to import batch tracks for accountId: " + accountId, e);
        }
    }

    private static Track importTrackInternal(Connection conn, String accountId, Track track) throws SQLException {
        Instant now = Instant.now();
        String trackMasterId = (track.getId() != null && !track.getId().isBlank()) ? track.getId() : UUID.randomUUID().toString();

        String upsertTrackSql = "INSERT INTO tracks (" +
                "id, file_path, title, artist, album_artist, album, genre, " +
                "year, track_number, disc_number, bpm, bitrate, duration_seconds, " +
                "file_size_bytes, file_last_modified, has_artwork, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(file_path) DO UPDATE SET " +
                "title = excluded.title, " +
                "artist = excluded.artist, " +
                "album_artist = excluded.album_artist, " +
                "album = excluded.album, " +
                "genre = excluded.genre, " +
                "year = excluded.year, " +
                "track_number = excluded.track_number, " +
                "disc_number = excluded.disc_number, " +
                "bpm = excluded.bpm, " +
                "bitrate = excluded.bitrate, " +
                "duration_seconds = excluded.duration_seconds, " +
                "file_size_bytes = excluded.file_size_bytes, " +
                "file_last_modified = excluded.file_last_modified, " +
                "has_artwork = excluded.has_artwork, " +
                "updated_at = excluded.updated_at " +
                "RETURNING id";

        String resolvedTrackId;
        try (PreparedStatement stmt = conn.prepareStatement(upsertTrackSql)) {
            stmt.setString(1, trackMasterId);
            stmt.setString(2, track.getFilePath());
            stmt.setString(3, track.getTitle());
            stmt.setString(4, track.getArtist());
            stmt.setString(5, track.getAlbumArtist());
            stmt.setString(6, track.getAlbum());
            stmt.setString(7, track.getGenre());
            stmt.setInt(8, track.getYear());
            stmt.setInt(9, track.getTrackNumber());
            stmt.setInt(10, track.getDiscNumber());
            stmt.setInt(11, track.getBpm());
            stmt.setInt(12, track.getBitrate());
            stmt.setLong(13, track.getDurationSeconds());
            stmt.setLong(14, track.getFileSizeBytes());
            stmt.setString(15, track.getLastModified() != null ? track.getLastModified().toString() : Instant.EPOCH.toString());
            stmt.setInt(16, track.hasArtwork() ? 1 : 0);
            stmt.setString(17, now.toString());
            stmt.setString(18, now.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    resolvedTrackId = rs.getString(1);
                } else {
                    resolvedTrackId = trackMasterId;
                }
            }
        }

        // Upsert account_tracks with unique candidate ID (RETURNING will return existing id on conflict)
        String accountTrackIdCandidate = UUID.randomUUID().toString();

        String upsertAccountTrackSql = "INSERT INTO account_tracks (" +
                "id, account_id, track_id, rating, play_count, last_played_at, date_added, created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(account_id, track_id) DO UPDATE SET " +
                "rating = CASE WHEN excluded.rating > 0 THEN excluded.rating ELSE account_tracks.rating END, " +
                "play_count = CASE WHEN excluded.play_count > 0 THEN excluded.play_count ELSE account_tracks.play_count END, " +
                "last_played_at = CASE WHEN excluded.last_played_at IS NOT NULL THEN excluded.last_played_at ELSE account_tracks.last_played_at END, " +
                "updated_at = excluded.updated_at " +
                "RETURNING id, rating, play_count, date_added, last_played_at";

        String resolvedAccountTrackId = accountTrackIdCandidate;
        int resolvedRating = track.getRating();
        int resolvedPlayCount = track.getPlayCount();
        Instant resolvedDateAdded = track.getDateAdded() != null ? track.getDateAdded() : now;
        Instant resolvedLastPlayed = track.getLastPlayed();

        try (PreparedStatement stmt = conn.prepareStatement(upsertAccountTrackSql)) {
            stmt.setString(1, accountTrackIdCandidate);
            stmt.setString(2, accountId);
            stmt.setString(3, resolvedTrackId);
            stmt.setInt(4, track.getRating());
            stmt.setInt(5, track.getPlayCount());
            if (track.getLastPlayed() != null) {
                stmt.setString(6, track.getLastPlayed().toString());
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }
            stmt.setString(7, resolvedDateAdded.toString());
            stmt.setString(8, now.toString());
            stmt.setString(9, now.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    resolvedAccountTrackId = rs.getString("id");
                    resolvedRating = rs.getInt("rating");
                    resolvedPlayCount = rs.getInt("play_count");
                    resolvedDateAdded = DbUtils.parseInstant(rs.getString("date_added"));
                    resolvedLastPlayed = DbUtils.parseNullableInstant(rs.getString("last_played_at"));
                }
            }
        }

        track.setId(resolvedTrackId);
        track.setAccountTrackId(resolvedAccountTrackId);
        track.setRating(resolvedRating);
        track.setPlayCount(resolvedPlayCount);
        track.setDateAdded(resolvedDateAdded);
        track.setLastPlayed(resolvedLastPlayed);
        return track;
    }

    @Override
    public void updateUserData(String accountTrackId, int rating, int playCount, Instant lastPlayedAt) {
        if (accountTrackId == null || accountTrackId.isBlank()) {
            return;
        }
        String sql = "UPDATE account_tracks SET " +
                     "rating = ?, " +
                     "play_count = ?, " +
                     "last_played_at = ?, " +
                     "updated_at = ? " +
                     "WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.clamp(rating, 0, 5));
            stmt.setInt(2, Math.max(0, playCount));
            if (lastPlayedAt != null) {
                stmt.setString(3, lastPlayedAt.toString());
            } else {
                stmt.setNull(3, Types.VARCHAR);
            }
            stmt.setString(4, Instant.now().toString());
            stmt.setString(5, accountTrackId.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to update user data for accountTrackId: " + accountTrackId, e);
        }
    }

    @Override
    public void deleteFromAccount(String accountId, String trackId) {
        if (accountId == null || accountId.isBlank() || trackId == null || trackId.isBlank()) {
            return;
        }
        String sql = "DELETE FROM account_tracks WHERE account_id = ? AND track_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.trim());
            stmt.setString(2, trackId.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete track link for accountId: " + accountId + ", trackId: " + trackId, e);
        }
    }

    @Override
    public void deleteTrackMaster(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            return;
        }
        String sql = "DELETE FROM tracks WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, trackId.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete track master by id: " + trackId, e);
        }
    }

    private static Track mapJoinedRow(ResultSet rs) throws SQLException {
        return Track.builder()
                .id(rs.getString("track_id"))
                .accountTrackId(rs.getString("account_track_id"))
                .filePath(rs.getString("file_path"))
                .title(rs.getString("title"))
                .artist(rs.getString("artist"))
                .albumArtist(rs.getString("album_artist"))
                .album(rs.getString("album"))
                .genre(rs.getString("genre"))
                .year(rs.getInt("year"))
                .trackNumber(rs.getInt("track_number"))
                .discNumber(rs.getInt("disc_number"))
                .bpm(rs.getInt("bpm"))
                .bitrate(rs.getInt("bitrate"))
                .durationSeconds(rs.getLong("duration_seconds"))
                .fileSizeBytes(rs.getLong("file_size_bytes"))
                .lastModified(DbUtils.parseInstant(rs.getString("file_last_modified")))
                .hasArtwork(rs.getInt("has_artwork") == 1)
                .rating(rs.getInt("rating"))
                .playCount(rs.getInt("play_count"))
                .dateAdded(DbUtils.parseInstant(rs.getString("date_added")))
                .lastPlayed(DbUtils.parseNullableInstant(rs.getString("last_played_at")))
                .build();
    }

    private static Track mapMasterRow(ResultSet rs) throws SQLException {
        return Track.builder()
                .id(rs.getString("id"))
                .filePath(rs.getString("file_path"))
                .title(rs.getString("title"))
                .artist(rs.getString("artist"))
                .albumArtist(rs.getString("album_artist"))
                .album(rs.getString("album"))
                .genre(rs.getString("genre"))
                .year(rs.getInt("year"))
                .trackNumber(rs.getInt("track_number"))
                .discNumber(rs.getInt("disc_number"))
                .bpm(rs.getInt("bpm"))
                .bitrate(rs.getInt("bitrate"))
                .durationSeconds(rs.getLong("duration_seconds"))
                .fileSizeBytes(rs.getLong("file_size_bytes"))
                .lastModified(DbUtils.parseInstant(rs.getString("file_last_modified")))
                .hasArtwork(rs.getInt("has_artwork") == 1)
                .build();
    }
}
