package com.musiclibrary.repository;

import com.musiclibrary.model.Account;
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

/**
 * SQLite implementation of AccountRepository.
 */
public class SqliteAccountRepository implements AccountRepository {

    private final DatabaseManager dbManager;

    public SqliteAccountRepository(DatabaseManager dbManager) {
        this.dbManager = Objects.requireNonNull(dbManager, "dbManager must not be null");
    }

    @Override
    public Optional<Account> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, created_at, updated_at FROM accounts WHERE id = ?";
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
            throw new RepositoryException("Failed to find account by id: " + id, e);
        }
    }

    @Override
    public Optional<Account> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, created_at, updated_at FROM accounts WHERE name = ?";
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
            throw new RepositoryException("Failed to find account by name: " + name, e);
        }
    }

    @Override
    public List<Account> findAll() {
        String sql = "SELECT id, name, created_at, updated_at FROM accounts ORDER BY created_at ASC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Account> accounts = new ArrayList<>();
            while (rs.next()) {
                accounts.add(mapRow(rs));
            }
            return accounts;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all accounts", e);
        }
    }

    @Override
    public void save(Account account) {
        Objects.requireNonNull(account, "account must not be null");
        String sql = "INSERT INTO accounts (id, name, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT(id) DO UPDATE SET " +
                     "name = excluded.name, " +
                     "updated_at = excluded.updated_at";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, account.getId());
            stmt.setString(2, account.getName());
            stmt.setString(3, account.getCreatedAt().toString());
            stmt.setString(4, account.getUpdatedAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save account: " + account.getId(), e);
        }
    }

    @Override
    public void deleteById(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String sql = "DELETE FROM accounts WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id.trim());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete account by id: " + id, e);
        }
    }

    @Override
    public Account getOrCreateDefaultAccount() {
        return findById(Account.DEFAULT_ACCOUNT_ID).orElseGet(() -> {
            Account def = new Account(Account.DEFAULT_ACCOUNT_ID, Account.DEFAULT_ACCOUNT_NAME);
            save(def);
            return def;
        });
    }

    private static Account mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        Instant createdAt = DbUtils.parseInstant(rs.getString("created_at"));
        Instant updatedAt = DbUtils.parseInstant(rs.getString("updated_at"));
        return new Account(id, name, createdAt, updatedAt);
    }
}
