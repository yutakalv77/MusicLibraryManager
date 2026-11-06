package com.musiclibrary.repository;

import com.musiclibrary.model.Genre;
import com.musiclibrary.repository.db.DatabaseManager;
import com.musiclibrary.repository.db.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqliteGenreRepositoryTest {

    private DatabaseManager dbManager;
    private SqliteGenreRepository genreRepository;

    @BeforeEach
    void setUp() throws SQLException {
        dbManager = new DatabaseManager(":memory:");
        SchemaInitializer.initialize(dbManager);
        genreRepository = new SqliteGenreRepository(dbManager);
    }

    @AfterEach
    void tearDown() throws SQLException {
        dbManager.close();
    }

    @Test
    @DisplayName("Seed data is populated on schema initialization")
    void testInitialSeedDataPopulated() {
        List<String> names = genreRepository.findAllNames();
        assertThat(names).hasSize(194);
        assertThat(names).contains("Rock", "Pop", "Jazz", "Classical", "Hip Hop", "Hip-Hop",
                "Electronic", "Metal", "R&B", "Blues", "Folk", "Country",
                "Soundtrack", "Anime", "JPop", "J-Pop", "Other", "Post-Punk", "Shoegaze", "Dubstep");
        assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    @DisplayName("findAll returns Genre domain entities ordered by name case-insensitively")
    void testFindAll() {
        List<Genre> genres = genreRepository.findAll();
        assertThat(genres).isNotEmpty();
        for (Genre g : genres) {
            assertThat(g.getId()).isNotBlank();
            assertThat(g.getName()).isNotBlank();
            assertThat(g.getCreatedAt()).isNotNull();
            assertThat(g.getUpdatedAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("findByName performs case-insensitive search")
    void testFindByNameCaseInsensitive() {
        Optional<Genre> rock = genreRepository.findByName("rock");
        assertThat(rock).isPresent();
        assertThat(rock.get().getName()).isEqualTo("Rock");

        Optional<Genre> hipHop = genreRepository.findByName("HIP HOP");
        assertThat(hipHop).isPresent();
        assertThat(hipHop.get().getName()).isEqualTo("Hip Hop");

        Optional<Genre> missing = genreRepository.findByName("NonExistentGenre");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("existsByName performs case-insensitive check")
    void testExistsByName() {
        assertThat(genreRepository.existsByName("jazz")).isTrue();
        assertThat(genreRepository.existsByName("JAZZ")).isTrue();
        assertThat(genreRepository.existsByName("Jazz")).isTrue();
        assertThat(genreRepository.existsByName("CyberpunkSynth80s")).isFalse();
    }

    @Test
    @DisplayName("save inserts new genre or updates existing one")
    void testSaveInsertAndUpdate() {
        Genre genre = new Genre("g-new-1", "Vaporwave");
        Genre saved = genreRepository.save(genre);
        assertThat(saved).isNotNull();

        Optional<Genre> found = genreRepository.findByName("Vaporwave");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("g-new-1");

        // Update name
        genre.setName("Future Funk");
        genreRepository.save(genre);

        Optional<Genre> updated = genreRepository.findByName("Future Funk");
        assertThat(updated).isPresent();
        assertThat(updated.get().getId()).isEqualTo("g-new-1");
    }

    @Test
    @DisplayName("findOrCreate returns existing genre when match found case-insensitively")
    void testFindOrCreateExisting() {
        int initialCount = genreRepository.findAll().size();

        Genre rock = genreRepository.findOrCreate("rock");
        assertThat(rock).isNotNull();
        assertThat(rock.getName()).isEqualTo("Rock");

        // Verify count did not increase
        assertThat(genreRepository.findAll()).hasSize(initialCount);
    }

    @Test
    @DisplayName("findOrCreate creates, saves and returns new genre when not found")
    void testFindOrCreateNew() {
        int initialCount = genreRepository.findAll().size();

        Genre synthwave = genreRepository.findOrCreate("Synthwave");
        assertThat(synthwave).isNotNull();
        assertThat(synthwave.getName()).isEqualTo("Synthwave");
        assertThat(synthwave.getId()).isNotBlank();

        // Verify count increased by 1
        assertThat(genreRepository.findAll()).hasSize(initialCount + 1);

        // Subsequent call returns the created genre
        Genre again = genreRepository.findOrCreate("synthwave");
        assertThat(again.getId()).isEqualTo(synthwave.getId());
    }

    @Test
    @DisplayName("Saving duplicate genre name with different id throws RepositoryException")
    void testDuplicateGenreNameThrowsException() {
        Genre g1 = new Genre("id-1", "Indie Pop");
        genreRepository.save(g1);

        Genre g2 = new Genre("id-2", "indie pop");
        assertThatThrownBy(() -> genreRepository.save(g2))
                .isInstanceOf(RepositoryException.class);
    }

    @Test
    @DisplayName("Edge cases and null arguments handling")
    void testEdgeCasesAndNullHandling() {
        assertThat(genreRepository.findByName(null)).isEmpty();
        assertThat(genreRepository.findByName("")).isEmpty();
        assertThat(genreRepository.findByName("   ")).isEmpty();

        assertThat(genreRepository.existsByName(null)).isFalse();
        assertThat(genreRepository.existsByName("")).isFalse();
        assertThat(genreRepository.existsByName("   ")).isFalse();

        assertThatThrownBy(() -> genreRepository.save(null)).isInstanceOf(NullPointerException.class);
        Genre emptyGenre = new Genre();
        assertThatThrownBy(() -> genreRepository.save(emptyGenre)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genreRepository.findOrCreate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genreRepository.findOrCreate("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> genreRepository.findOrCreate("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
