package com.musiclibrary.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a user account / profile.
 */
public class Account {

    public static final String DEFAULT_ACCOUNT_ID = "default";
    public static final String DEFAULT_ACCOUNT_NAME = "Default User";

    private String id;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Default constructor creating the default account.
     */
    public Account() {
        this(DEFAULT_ACCOUNT_ID, DEFAULT_ACCOUNT_NAME, Instant.now(), Instant.now());
    }

    /**
     * Creates an account with specified ID and name.
     *
     * @param id   account identifier
     * @param name account display name
     */
    public Account(String id, String name) {
        this(id, name, Instant.now(), Instant.now());
    }

    /**
     * Creates an account with all fields specified.
     *
     * @param id        account identifier
     * @param name      account display name
     * @param createdAt creation timestamp
     * @param updatedAt last update timestamp
     */
    public Account(String id, String name, Instant createdAt, Instant updatedAt) {
        setId(id);
        setName(name);
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
        this.updatedAt = (updatedAt != null) ? updatedAt : Instant.now();
    }

    private Account(Builder builder) {
        String idVal = builder.id != null && !builder.id.isBlank() ? builder.id : UUID.randomUUID().toString();
        setId(idVal);
        setName(builder.name != null && !builder.name.isBlank() ? builder.name : DEFAULT_ACCOUNT_NAME);
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id cannot be null or blank");
        }
        this.id = id.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Account name cannot be null or blank");
        }
        this.name = name.trim();
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
        Account account = (Account) o;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Account{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    public static class Builder {
        private String id;
        private String name = DEFAULT_ACCOUNT_NAME;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
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

        public Account build() {
            return new Account(this);
        }
    }
}
