package com.musiclibrary.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a playlist folder for hierarchical organization.
 */
public class PlaylistFolder {

    private String id;
    private String accountId;
    private String parentFolderId;
    private String name;
    private int position;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Creates a folder with the specified name in the root level of default account.
     *
     * @param name folder display name
     */
    public PlaylistFolder(String name) {
        this(UUID.randomUUID().toString(), Account.DEFAULT_ACCOUNT_ID, null, name, 0, Instant.now(), Instant.now());
    }

    /**
     * Creates a folder with all fields specified.
     *
     * @param id             folder unique identifier
     * @param accountId      owner account ID
     * @param parentFolderId parent folder ID (or null if root level)
     * @param name           folder display name
     * @param position       display order index
     * @param createdAt      creation timestamp
     * @param updatedAt      last update timestamp
     */
    public PlaylistFolder(String id, String accountId, String parentFolderId, String name, int position, Instant createdAt, Instant updatedAt) {
        setId(id);
        setAccountId(accountId);
        setParentFolderId(parentFolderId);
        setName(name);
        setPosition(position);
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
        this.updatedAt = (updatedAt != null) ? updatedAt : Instant.now();
    }

    private PlaylistFolder(Builder builder) {
        String idVal = builder.id != null && !builder.id.isBlank() ? builder.id : UUID.randomUUID().toString();
        setId(idVal);
        setAccountId(builder.accountId != null && !builder.accountId.isBlank() ? builder.accountId : Account.DEFAULT_ACCOUNT_ID);
        setParentFolderId(builder.parentFolderId);
        setName(builder.name != null && !builder.name.isBlank() ? builder.name : "New Folder");
        setPosition(Math.max(0, builder.position));
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .accountId(this.accountId)
                .parentFolderId(this.parentFolderId)
                .name(this.name)
                .position(this.position)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    /**
     * Checks if this folder is at the root level (no parent folder).
     *
     * @return true if root level, false if nested
     */
    public boolean isRootFolder() {
        return parentFolderId == null || parentFolderId.isBlank();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("PlaylistFolder id cannot be null or blank");
        }
        this.id = id.trim();
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("PlaylistFolder accountId cannot be null or blank");
        }
        this.accountId = accountId.trim();
    }

    public String getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(String parentFolderId) {
        this.parentFolderId = (parentFolderId != null && !parentFolderId.isBlank()) ? parentFolderId.trim() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("PlaylistFolder name cannot be null or blank");
        }
        this.name = name.trim();
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("PlaylistFolder position must be non-negative: " + position);
        }
        this.position = position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt cannot be null");
        }
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt cannot be null");
        }
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistFolder that = (PlaylistFolder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PlaylistFolder{" +
                "id='" + id + '\'' +
                ", accountId='" + accountId + '\'' +
                ", parentFolderId='" + parentFolderId + '\'' +
                ", name='" + name + '\'' +
                ", position=" + position +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    public static class Builder {
        private String id;
        private String accountId = Account.DEFAULT_ACCOUNT_ID;
        private String parentFolderId;
        private String name;
        private int position = 0;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder parentFolderId(String parentFolderId) {
            this.parentFolderId = parentFolderId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder position(int position) {
            this.position = position;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public PlaylistFolder build() {
            return new PlaylistFolder(this);
        }
    }
}
