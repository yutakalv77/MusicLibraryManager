package com.musiclibrary.repository;

import com.musiclibrary.model.Genre;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.DbUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite implementation of {@link GenreRepository}.
 */
public class SqliteGenreRepository implements GenreRepository {

    private final DatabaseManager dbManager;

    public SqliteGenreRepository(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
    }

    @Override
    public List<Genre> findAll() {
        String sql = "SELECT id, name, created_at, updated_at FROM genres ORDER BY name COLLATE NOCASE ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Genre> genres = new ArrayList<>();
            while (rs.next()) {
                genres.add(mapRow(rs));
            }
            return genres;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all genres", e);
        }
    }

    @Override
    public List<String> findAllNames() {
        String sql = "SELECT name FROM genres ORDER BY name COLLATE NOCASE ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            return names;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all genre names", e);
        }
    }

    @Override
    public Optional<Genre> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, created_at, updated_at FROM genres WHERE name = ? COLLATE NOCASE";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find genre by name: " + name, e);
        }
    }

    @Override
    public boolean existsByName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String sql = "SELECT 1 FROM genres WHERE name = ? COLLATE NOCASE LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to check if genre exists by name: " + name, e);
        }
    }

    @Override
    public Genre save(Genre genre) {
        Objects.requireNonNull(genre, "genre must not be null");
        if (genre.getName() == null || genre.getName().isBlank()) {
            throw new IllegalArgumentException("Genre name cannot be null or blank");
        }
        if (genre.getId() == null || genre.getId().isBlank()) {
            genre.setId(UUID.randomUUID().toString());
        }
        if (genre.getCreatedAt() == null) {
            genre.setCreatedAt(Instant.now());
        }
        if (genre.getUpdatedAt() == null) {
            genre.setUpdatedAt(Instant.now());
        }

        String sql = "INSERT INTO genres (id, name, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name = excluded.name, " +
                     "updated_at = excluded.updated_at";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, genre.getId());
            stmt.setString(2, genre.getName());
            stmt.setString(3, genre.getCreatedAt().toString());
            stmt.setString(4, genre.getUpdatedAt().toString());
            stmt.executeUpdate();
            return genre;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save genre: " + genre.getName(), e);
        }
    }

    @Override
    public Genre findOrCreate(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Genre name cannot be null or blank");
        }
        String trimmed = name.trim();
        Optional<Genre> existing = findByName(trimmed);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        Genre newGenre = new Genre(UUID.randomUUID().toString(), trimmed, now, now);
        try {
            return save(newGenre);
        } catch (RepositoryException e) {
            // Check if inserted concurrently
            Optional<Genre> retry = findByName(trimmed);
            if (retry.isPresent()) {
                return retry.get();
            }
            throw e;
        }
    }

    private static Genre mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        Instant createdAt = DbUtils.parseInstant(rs.getString("created_at"));
        Instant updatedAt = DbUtils.parseInstant(rs.getString("updated_at"));
        return new Genre(id, name, createdAt, updatedAt);
    }
}
