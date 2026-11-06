package com.musiclibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenreTest {

    @Test
    @DisplayName("Default constructor creates genre with generated id, empty name, and timestamps")
    void testDefaultConstructor() {
        Genre genre = new Genre();
        assertThat(genre.getId()).isNotBlank();
        assertThat(genre.getName()).isEmpty();
        assertThat(genre.getCreatedAt()).isNotNull();
        assertThat(genre.getUpdatedAt()).isNotNull();
        assertThat(genre.toString()).isEmpty();
    }

    @Test
    @DisplayName("Constructor with name creates genre with generated id and current timestamps")
    void testNameConstructor() {
        Genre genre = new Genre("Synthwave");
        assertThat(genre.getId()).isNotBlank();
        assertThat(genre.getName()).isEqualTo("Synthwave");
        assertThat(genre.getCreatedAt()).isNotNull();
        assertThat(genre.getUpdatedAt()).isNotNull();
        assertThat(genre.toString()).isEqualTo("Synthwave");
    }

    @Test
    @DisplayName("Constructor with id and name sets fields correctly")
    void testIdAndNameConstructor() {
        String id = UUID.randomUUID().toString();
        Genre genre = new Genre(id, "Lo-Fi");
        assertThat(genre.getId()).isEqualTo(id);
        assertThat(genre.getName()).isEqualTo("Lo-Fi");
        assertThat(genre.getCreatedAt()).isNotNull();
        assertThat(genre.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Full constructor sets all fields correctly")
    void testFullConstructor() {
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        Instant updated = Instant.parse("2026-01-02T12:00:00Z");
        Genre genre = new Genre("g-1", "Chiptune", created, updated);

        assertThat(genre.getId()).isEqualTo("g-1");
        assertThat(genre.getName()).isEqualTo("Chiptune");
        assertThat(genre.getCreatedAt()).isEqualTo(created);
        assertThat(genre.getUpdatedAt()).isEqualTo(updated);
        assertThat(genre.toString()).isEqualTo("Chiptune");
    }

    @Test
    @DisplayName("Setters validate null and blank arguments")
    void testSettersValidation() {
        Genre genre = new Genre("Pop");

        assertThatThrownBy(() -> genre.setId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genre.setId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genre.setId("   ")).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> genre.setName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genre.setName("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genre.setName("   ")).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> genre.setCreatedAt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genre.setUpdatedAt(null)).isInstanceOf(IllegalArgumentException.class);

        genre.setId("new-id");
        assertThat(genre.getId()).isEqualTo("new-id");

        genre.setName("New Name");
        assertThat(genre.getName()).isEqualTo("New Name");

        Instant now = Instant.now();
        genre.setCreatedAt(now);
        genre.setUpdatedAt(now);
        assertThat(genre.getCreatedAt()).isEqualTo(now);
        assertThat(genre.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("equals and hashCode based on genre id")
    void testEqualsAndHashCode() {
        Genre g1 = new Genre("id-123", "Rock");
        Genre g2 = new Genre("id-123", "Classic Rock");
        Genre g3 = new Genre("id-999", "Rock");

        assertThat(g1).isEqualTo(g2);
        assertThat(g1.hashCode()).isEqualTo(g2.hashCode());
        assertThat(g1).isNotEqualTo(g3);
        assertThat(g1).isNotEqualTo(null);
        assertThat(g1).isNotEqualTo("some string");
    }
}
