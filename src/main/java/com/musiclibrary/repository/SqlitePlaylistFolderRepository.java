package com.musiclibrary.repository;

import com.musiclibrary.model.PlaylistFolder;
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

/**
 * SQLite implementation of PlaylistFolderRepository.
 */
public class SqlitePlaylistFolderRepository implements PlaylistFolderRepository {

    private final DatabaseManager dbManager;

    public SqlitePlaylistFolderRepository(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
    }

    @Override
    public Optional<PlaylistFolder> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, account_id, parent_folder_id, name, position, created_at, updated_at " +
                     "FROM playlist_folders WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find playlist folder by id: " + id, e);
        }
    }

    @Override
    public List<PlaylistFolder> findByAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return List.of();
        }
        String sql = "SELECT id, account_id, parent_folder_id, name, position, created_at, updated_at " +
                     "FROM playlist_folders WHERE account_id = ? ORDER BY position ASC, created_at ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                List<PlaylistFolder> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find playlist folders by accountId: " + accountId, e);
        }
    }

    @Override
    public List<PlaylistFolder> findByParentFolderId(String accountId, String parentFolderId) {
        if (accountId == null || accountId.isBlank()) {
            return List.of();
        }
        boolean isRoot = (parentFolderId == null || parentFolderId.isBlank());
        String sql = isRoot
                ? "SELECT id, account_id, parent_folder_id, name, position, created_at, updated_at " +
                  "FROM playlist_folders WHERE account_id = ? AND parent_folder_id IS NULL ORDER BY position ASC, created_at ASC"
                : "SELECT id, account_id, parent_folder_id, name, position, created_at, updated_at " +
                  "FROM playlist_folders WHERE account_id = ? AND parent_folder_id = ? ORDER BY position ASC, created_at ASC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, accountId.trim());
            if (!isRoot) {
                stmt.setString(2, parentFolderId.trim());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                List<PlaylistFolder> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find playlist folders by parentFolderId: " + parentFolderId, e);
        }
    }

    @Override
    public void save(PlaylistFolder folder) {
        Objects.requireNonNull(folder, "folder must not be null");
        String sql = "INSERT INTO playlist_folders (id, account_id, parent_folder_id, name, position, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "account_id = excluded.account_id, " +
                     "parent_folder_id = excluded.parent_folder_id, " +
                     "name = excluded.name, " +
                     "position = excluded.position, " +
                     "updated_at = excluded.updated_at";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindFolder(stmt, folder);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save playlist folder: " + folder.getId(), e);
        }
    }

    @Override
    public void saveAll(List<PlaylistFolder> folders) {
        if (folders == null || folders.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO playlist_folders (id, account_id, parent_folder_id, name, position, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "account_id = excluded.account_id, " +
                     "parent_folder_id = excluded.parent_folder_id, " +
                     "name = excluded.name, " +
                     "position = excluded.position, " +
                     "updated_at = excluded.updated_at";

        try (Connection conn = dbManager.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (PlaylistFolder folder : folders) {
                    bindFolder(stmt, folder);
                    stmt.addBatch();
                }
                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save playlist folders in batch", e);
        }
    }

    @Override
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String sql = "DELETE FROM playlist_folders WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete playlist folder by id: " + id, e);
        }
    }

    private static void bindFolder(PreparedStatement stmt, PlaylistFolder folder) throws SQLException {
        stmt.setString(1, folder.getId());
        stmt.setString(2, folder.getAccountId());
        if (folder.getParentFolderId() != null && !folder.getParentFolderId().isBlank()) {
            stmt.setString(3, folder.getParentFolderId().trim());
        } else {
            stmt.setNull(3, Types.VARCHAR);
        }
        stmt.setString(4, folder.getName());
        stmt.setInt(5, folder.getPosition());
        stmt.setString(6, folder.getCreatedAt().toString());
        stmt.setString(7, folder.getUpdatedAt().toString());
    }

    private static PlaylistFolder mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String accountId = rs.getString("account_id");
        String parentFolderId = rs.getString("parent_folder_id");
        String name = rs.getString("name");
        int position = rs.getInt("position");
        Instant createdAt = DbUtils.parseInstant(rs.getString("created_at"));
        Instant updatedAt = DbUtils.parseInstant(rs.getString("updated_at"));
        return new PlaylistFolder(id, accountId, parentFolderId, name, position, createdAt, updatedAt);
    }
}
