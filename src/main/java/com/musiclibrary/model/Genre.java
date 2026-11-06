package com.musiclibrary.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a musical genre.
 */
@SuppressWarnings("this-escape")
public class Genre {

    private String id;
    private String name;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Default constructor creating a genre with a generated UUID and empty name.
     */
    public Genre() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Creates a genre with a generated UUID and current timestamps.
     *
     * @param name genre name
     */
    public Genre(String name) {
        this(UUID.randomUUID().toString(), name, Instant.now(), Instant.now());
    }

    /**
     * Creates a genre with specified ID and name, with current timestamps.
     *
     * @param id   genre identifier
     * @param name genre name
     */
    public Genre(String id, String name) {
        this(id, name, Instant.now(), Instant.now());
    }

    /**
     * Creates a genre with all fields specified.
     *
     * @param id        genre identifier
     * @param name      genre name
     * @param createdAt creation timestamp
     * @param updatedAt last update timestamp
     */
    public Genre(String id, String name, Instant createdAt, Instant updatedAt) {
        setId(id);
        setName(name);
        this.createdAt = (createdAt != null) ? createdAt : Instant.now();
        this.updatedAt = (updatedAt != null) ? updatedAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Genre id cannot be null or blank");
        }
        this.id = id.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Genre name cannot be null or blank");
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
        Genre genre = (Genre) o;
        return Objects.equals(id, genre.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return name != null ? name : "";
    }
}
