package com.musiclibrary.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaylistTest {

    @Test
    @DisplayName("Default constructor assigns valid UUID and default title")
    void testDefaultConstructor() {
        Playlist playlist = new Playlist();

        assertThat(playlist.getId()).isNotBlank();
        assertThat(playlist.getName()).isEqualTo("Untitled Playlist");
        assertThat(playlist.getTrackIds()).isEmpty();
        assertThat(playlist.getTrackCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Constructor with name trims name, handles null and blank with default fallback")
    void testNameConstructor() {
        Playlist playlist = new Playlist("  My Favorites  ");
        assertThat(playlist.getId()).isNotBlank();
        assertThat(playlist.getName()).isEqualTo("My Favorites");
        assertThat(playlist.getTrackIds()).isEmpty();

        Playlist nullNamePlaylist = new Playlist(null);
        assertThat(nullNamePlaylist.getName()).isEqualTo("Untitled Playlist");

        Playlist blankNamePlaylist = new Playlist("   ");
        assertThat(blankNamePlaylist.getName()).isEqualTo("Untitled Playlist");
    }

    @Test
    @DisplayName("Constructor with ID and Name falls back to UUID and default name if null or blank")
    void testIdAndNameConstructor() {
        Playlist playlist = new Playlist("custom-id", "Custom Name");
        assertThat(playlist.getId()).isEqualTo("custom-id");
        assertThat(playlist.getName()).isEqualTo("Custom Name");

        Playlist nullFallback = new Playlist(null, null);
        assertThat(nullFallback.getId()).isNotBlank();
        assertThat(nullFallback.getName()).isEqualTo("Untitled Playlist");

        Playlist blankFallback = new Playlist("   ", "   ");
        assertThat(blankFallback.getId()).isNotBlank();
        assertThat(blankFallback.getName()).isEqualTo("Untitled Playlist");
    }

    @Test
    @DisplayName("Constructor with initial track IDs filters blanks and nulls and handles null list")
    void testConstructorWithInitialTracks() {
        List<String> initial = List.of("t1", "t2");
        Playlist playlist = new Playlist("custom-id", "Rock Playlist", initial);

        assertThat(playlist.getId()).isEqualTo("custom-id");
        assertThat(playlist.getName()).isEqualTo("Rock Playlist");
        assertThat(playlist.getTrackIds()).containsExactly("t1", "t2");
        assertThat(playlist.getTrackCount()).isEqualTo(2);

        // Null list falls back to empty list
        Playlist nullListPlaylist = new Playlist("id", "Name", null);
        assertThat(nullListPlaylist.getTrackIds()).isEmpty();

        // Mixed list with null and blanks
        List<String> mixedList = Arrays.asList("valid-1", null, "", "   ", "valid-2");
        Playlist filteredPlaylist = new Playlist("id", "Name", mixedList);
        assertThat(filteredPlaylist.getTrackIds()).containsExactly("valid-1", "valid-2");
    }

    @Test
    @DisplayName("setId validates and rejects null and blank IDs")
    void testSetIdValidation() {
        Playlist playlist = new Playlist("Favorites");

        assertThatThrownBy(() -> playlist.setId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> playlist.setId(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> playlist.setId("   "))
                .isInstanceOf(IllegalArgumentException.class);

        playlist.setId("new-id-123");
        assertThat(playlist.getId()).isEqualTo("new-id-123");
    }

    @Test
    @DisplayName("setName trims whitespace and falls back to Untitled Playlist for null or blank")
    void testSetNameValidation() {
        Playlist playlist = new Playlist("Favorites");

        playlist.setName("  Workout Mix  ");
        assertThat(playlist.getName()).isEqualTo("Workout Mix");

        playlist.setName(null);
        assertThat(playlist.getName()).isEqualTo("Untitled Playlist");

        playlist.setName("   ");
        assertThat(playlist.getName()).isEqualTo("Untitled Playlist");
    }

    @Test
    @DisplayName("getTrackIds returns unmodifiable list preventing external modification")
    void testGetTrackIdsUnmodifiable() {
        Playlist playlist = new Playlist("Favorites");
        playlist.addTrack("t1");

        List<String> trackIds = playlist.getTrackIds();
        assertThatThrownBy(() -> trackIds.add("t2"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> trackIds.remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(trackIds::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("setTrackIds makes defensive copy and filters null/blank elements")
    void testSetTrackIds() {
        Playlist playlist = new Playlist("Favorites");

        List<String> externalList = new ArrayList<>(Arrays.asList("t1", null, "   ", "t2"));
        playlist.setTrackIds(externalList);

        assertThat(playlist.getTrackIds()).containsExactly("t1", "t2");

        // External mutation should not affect playlist
        externalList.add("t3");
        assertThat(playlist.getTrackIds()).containsExactly("t1", "t2");

        // Null list clears playlist
        playlist.setTrackIds(null);
        assertThat(playlist.getTrackIds()).isEmpty();
    }

    @Test
    @DisplayName("Duplicate track IDs can be added and first occurrence is removed on removeTrack")
    void testDuplicateTrackIds() {
        Playlist playlist = new Playlist("Looping Mix");
        playlist.addTrack("t1");
        playlist.addTrack("t2");
        playlist.addTrack("t1"); // duplicate track ID

        assertThat(playlist.getTrackCount()).isEqualTo(3);
        assertThat(playlist.getTrackIds()).containsExactly("t1", "t2", "t1");
        assertThat(playlist.containsTrack("t1")).isTrue();

        // removeTrack should remove only first occurrence
        boolean removed = playlist.removeTrack("t1");
        assertThat(removed).isTrue();
        assertThat(playlist.getTrackCount()).isEqualTo(2);
        assertThat(playlist.getTrackIds()).containsExactly("t2", "t1");
    }

    @Test
    @DisplayName("addTrack and removeTrack work correctly with valid and absent values")
    void testAddAndRemoveTrack() {
        Playlist playlist = new Playlist("Chill");
        playlist.addTrack("t1");
        playlist.addTrack("t2");
        playlist.addTrack("t3");

        assertThat(playlist.getTrackCount()).isEqualTo(3);
        assertThat(playlist.containsTrack("t2")).isTrue();

        boolean removed = playlist.removeTrack("t2");
        assertThat(removed).isTrue();
        assertThat(playlist.getTrackIds()).containsExactly("t1", "t3");

        boolean removedNotFound = playlist.removeTrack("unknown");
        assertThat(removedNotFound).isFalse();

        // removeTrack with null and blank returns false without throwing
        assertThat(playlist.removeTrack(null)).isFalse();
        assertThat(playlist.removeTrack("   ")).isFalse();

        // removeTrackAt
        String removedAt0 = playlist.removeTrackAt(0);
        assertThat(removedAt0).isEqualTo("t1");
        assertThat(playlist.getTrackIds()).containsExactly("t3");
    }

    @Test
    @DisplayName("addTrack at index inserts at front, middle, and end boundary positions")
    void testAddTrackAtIndex() {
        Playlist playlist = new Playlist("Mix");
        playlist.addTrack("t1");
        playlist.addTrack("t3");

        // Insert at middle (index 1)
        playlist.addTrack(1, "t2");
        assertThat(playlist.getTrackIds()).containsExactly("t1", "t2", "t3");

        // Insert at front (index 0)
        playlist.addTrack(0, "t0");
        assertThat(playlist.getTrackIds()).containsExactly("t0", "t1", "t2", "t3");

        // Insert at end (index == size)
        playlist.addTrack(4, "t4");
        assertThat(playlist.getTrackIds()).containsExactly("t0", "t1", "t2", "t3", "t4");

        // Boundary violations
        assertThatThrownBy(() -> playlist.addTrack(-1, "invalid"))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> playlist.addTrack(6, "invalid"))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("addTrack rejects null or blank track IDs")
    void testAddTrackValidation() {
        Playlist playlist = new Playlist("Mix");

        assertThatThrownBy(() -> playlist.addTrack(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> playlist.addTrack(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> playlist.addTrack("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> playlist.addTrack(0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> playlist.addTrack(0, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("removeTrackAt rejects out-of-bounds indices")
    void testRemoveTrackAtOutOfBounds() {
        Playlist playlist = new Playlist("Test", "Test", List.of("A", "B"));

        assertThatThrownBy(() -> playlist.removeTrackAt(-1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> playlist.removeTrackAt(2))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("reorder moves track to target index")
    void testReorder() {
        Playlist playlist = new Playlist("Reorder Test", "Reorder Test", List.of("A", "B", "C", "D"));

        // Move B (index 1) to end (index 3)
        playlist.reorder(1, 3);
        assertThat(playlist.getTrackIds()).containsExactly("A", "C", "D", "B");

        // Move B (index 3) to front (index 0)
        playlist.reorder(3, 0);
        assertThat(playlist.getTrackIds()).containsExactly("B", "A", "C", "D");

        // Same index does nothing
        playlist.reorder(2, 2);
        assertThat(playlist.getTrackIds()).containsExactly("B", "A", "C", "D");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 5})
    @DisplayName("reorder with invalid fromIndex throws IndexOutOfBoundsException")
    void testReorderInvalidFromIndex(int invalidFrom) {
        Playlist playlist = new Playlist("Test", "Test", List.of("A", "B"));
        assertThatThrownBy(() -> playlist.reorder(invalidFrom, 0))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2, 5})
    @DisplayName("reorder with invalid toIndex throws IndexOutOfBoundsException")
    void testReorderInvalidToIndex(int invalidTo) {
        Playlist playlist = new Playlist("Test", "Test", List.of("A", "B"));
        assertThatThrownBy(() -> playlist.reorder(0, invalidTo))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("Large-scale reorder integrity test with 1,000 tracks")
    void testLargeScaleReorderIntegrity() {
        int trackCount = 1000;
        List<String> largeTrackList = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            largeTrackList.add("track-" + i);
        }

        Playlist playlist = new Playlist("Large Playlist", "Large Playlist", largeTrackList);
        assertThat(playlist.getTrackCount()).isEqualTo(trackCount);

        // 1. Move first element (index 0: "track-0") to the very end (index 999)
        playlist.reorder(0, 999);
        assertThat(playlist.getTrackIds().get(0)).isEqualTo("track-1");
        assertThat(playlist.getTrackIds().get(999)).isEqualTo("track-0");
        assertThat(playlist.getTrackCount()).isEqualTo(trackCount);

        // 2. Move element at 999 back to front (index 0)
        playlist.reorder(999, 0);
        assertThat(playlist.getTrackIds().get(0)).isEqualTo("track-0");
        assertThat(playlist.getTrackIds().get(1)).isEqualTo("track-1");

        // 3. Move middle element (index 500) to index 100
        String movingItem = playlist.getTrackIds().get(500);
        playlist.reorder(500, 100);
        assertThat(playlist.getTrackIds().get(100)).isEqualTo(movingItem);

        // 4. Move element from index 200 to index 800
        String movingItem2 = playlist.getTrackIds().get(200);
        playlist.reorder(200, 800);
        assertThat(playlist.getTrackIds().get(800)).isEqualTo(movingItem2);

        // Verify total integrity: no tracks lost or duplicated
        assertThat(playlist.getTrackCount()).isEqualTo(trackCount);
        assertThat(new HashSet<>(playlist.getTrackIds())).hasSize(trackCount);
    }

    @Test
    @DisplayName("clear empties the playlist")
    void testClear() {
        Playlist playlist = new Playlist("Test", "Test", List.of("A", "B"));
        playlist.clear();
        assertThat(playlist.getTrackIds()).isEmpty();
        assertThat(playlist.getTrackCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("containsTrack correctly identifies presence and absence")
    void testContainsTrack() {
        Playlist playlist = new Playlist("Test", "Test", List.of("A", "B"));
        assertThat(playlist.containsTrack("A")).isTrue();
        assertThat(playlist.containsTrack("B")).isTrue();
        assertThat(playlist.containsTrack("C")).isFalse();
        assertThat(playlist.containsTrack(null)).isFalse();
    }

    @Test
    @DisplayName("equals and hashCode contracts are satisfied based on id")
    void testEqualsAndHashCode() {
        String sameId = UUID.randomUUID().toString();
        Playlist p1 = new Playlist(sameId, "Playlist A", List.of("t1"));
        Playlist p2 = new Playlist(sameId, "Playlist B", List.of("t2"));
        Playlist p3 = new Playlist("Playlist A");

        // Reflexivity
        assertThat(p1).isEqualTo(p1);

        // Symmetry & consistency
        assertThat(p1).isEqualTo(p2);
        assertThat(p2).isEqualTo(p1);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());

        // Inequality
        assertThat(p1).isNotEqualTo(p3);
        assertThat(p1).isNotEqualTo(null);
        assertThat(p1).isNotEqualTo("some string");
    }

    @Test
    @DisplayName("toString contains playlist id, name, and trackCount")
    void testToString() {
        Playlist playlist = new Playlist("custom-id", "Rock Classics", List.of("t1", "t2"));
        assertThat(playlist.toString())
                .contains("id='custom-id'")
                .contains("name='Rock Classics'")
                .contains("trackCount=2");
    }

    @Test
    @DisplayName("Gson serialization and deserialization works seamlessly")
    void testGsonSerialization() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(java.time.Instant.class, new com.google.gson.TypeAdapter<java.time.Instant>() {
                    @Override
                    public void write(com.google.gson.stream.JsonWriter out, java.time.Instant value) throws java.io.IOException {
                        if (value == null) {
                            out.nullValue();
                        } else {
                            out.value(value.toString());
                        }
                    }

                    @Override
                    public java.time.Instant read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                        return java.time.Instant.parse(in.nextString());
                    }
                })
                .create();
        Playlist playlist = new Playlist("p-id-1", "Workout Hits", List.of("t10", "t20"));

        String json = gson.toJson(playlist);
        Playlist deserialized = gson.fromJson(json, Playlist.class);

        assertThat(deserialized.getId()).isEqualTo(playlist.getId());
        assertThat(deserialized.getName()).isEqualTo(playlist.getName());
        assertThat(deserialized.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
        assertThat(deserialized.getFolderId()).isNull();
        assertThat(deserialized.getPosition()).isEqualTo(0);
        assertThat(deserialized.getTrackIds()).containsExactly("t10", "t20");
        assertThat(deserialized.getTrackCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Builder and full arguments constructor support accountId, folderId, position, and timestamps")
    void testBuilderAndFullArgsConstructor() {
        java.time.Instant now = java.time.Instant.now();
        Playlist playlist = Playlist.builder()
                .id("custom-pl-1")
                .name("Driving Songs")
                .accountId("acc-42")
                .folderId("folder-7")
                .position(5)
                .trackIds(List.of("tr-1", "tr-2"))
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(playlist.getId()).isEqualTo("custom-pl-1");
        assertThat(playlist.getName()).isEqualTo("Driving Songs");
        assertThat(playlist.getAccountId()).isEqualTo("acc-42");
        assertThat(playlist.getFolderId()).isEqualTo("folder-7");
        assertThat(playlist.getPosition()).isEqualTo(5);
        assertThat(playlist.getTrackIds()).containsExactly("tr-1", "tr-2");
        assertThat(playlist.getCreatedAt()).isEqualTo(now);
        assertThat(playlist.getUpdatedAt()).isEqualTo(now);

        Playlist modified = playlist.toBuilder()
                .name("Chill Driving")
                .position(6)
                .build();

        assertThat(modified.getName()).isEqualTo("Chill Driving");
        assertThat(modified.getPosition()).isEqualTo(6);
        assertThat(modified).isEqualTo(playlist);
    }

    @Test
    @DisplayName("Default constructors initialize accountId to default, folderId to null, and position to 0")
    void testNewPropertiesDefaultValues() {
        Playlist defaultPlaylist = new Playlist();
        assertThat(defaultPlaylist.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
        assertThat(defaultPlaylist.getFolderId()).isNull();
        assertThat(defaultPlaylist.getPosition()).isEqualTo(0);
        assertThat(defaultPlaylist.getCreatedAt()).isNotNull();
        assertThat(defaultPlaylist.getUpdatedAt()).isNotNull();

        Playlist namedPlaylist = new Playlist("My Jams");
        assertThat(namedPlaylist.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
        assertThat(namedPlaylist.getFolderId()).isNull();
        assertThat(namedPlaylist.getPosition()).isEqualTo(0);
    }

    @Test
    @DisplayName("Setters for new properties safely handle null, blanks, and negative clamping")
    void testNewPropertiesSettersValidationAndClamping() {
        Playlist playlist = new Playlist("Test Playlist");

        // accountId null/blank fallback
        playlist.setAccountId("acc-custom");
        assertThat(playlist.getAccountId()).isEqualTo("acc-custom");
        playlist.setAccountId(null);
        assertThat(playlist.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
        playlist.setAccountId("   ");
        assertThat(playlist.getAccountId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);

        // folderId null/blank normalization
        playlist.setFolderId("folder-1");
        assertThat(playlist.getFolderId()).isEqualTo("folder-1");
        playlist.setFolderId(null);
        assertThat(playlist.getFolderId()).isNull();
        playlist.setFolderId("   ");
        assertThat(playlist.getFolderId()).isNull();

        // position negative clamping
        playlist.setPosition(5);
        assertThat(playlist.getPosition()).isEqualTo(5);
        playlist.setPosition(-1);
        assertThat(playlist.getPosition()).isEqualTo(0);
        playlist.setPosition(-99);
        assertThat(playlist.getPosition()).isEqualTo(0);

        // timestamps null fallback
        playlist.setCreatedAt(null);
        assertThat(playlist.getCreatedAt()).isNotNull();
        playlist.setUpdatedAt(null);
        assertThat(playlist.getUpdatedAt()).isNotNull();

        // name null/blank fallback
        playlist.setName(null);
        assertThat(playlist.getName()).isEqualTo("Untitled Playlist");
        playlist.setName("   ");
        assertThat(playlist.getName()).isEqualTo("Untitled Playlist");
    }

    @Test
    @DisplayName("Track mutation operations (add, remove, reorder, clear) preserve new properties")
    void testTrackOperationsPreserveNewProperties() {
        Instant created = Instant.parse("2026-10-15T01:00:00Z");
        Instant updated = Instant.parse("2026-10-15T02:00:00Z");

        Playlist playlist = Playlist.builder()
                .name("Rock List")
                .accountId("acc-rock")
                .folderId("folder-genres")
                .position(4)
                .trackIds(List.of("t1", "t2", "t3"))
                .createdAt(created)
                .updatedAt(updated)
                .build();

        // 1. addTrack
        playlist.addTrack("t4");
        assertThat(playlist.getTrackCount()).isEqualTo(4);
        assertThat(playlist.getAccountId()).isEqualTo("acc-rock");
        assertThat(playlist.getFolderId()).isEqualTo("folder-genres");
        assertThat(playlist.getPosition()).isEqualTo(4);

        // 2. reorder
        playlist.reorder(0, 3);
        assertThat(playlist.getTrackIds()).containsExactly("t2", "t3", "t4", "t1");
        assertThat(playlist.getAccountId()).isEqualTo("acc-rock");
        assertThat(playlist.getFolderId()).isEqualTo("folder-genres");
        assertThat(playlist.getPosition()).isEqualTo(4);

        // 3. removeTrack
        playlist.removeTrack("t2");
        assertThat(playlist.getTrackCount()).isEqualTo(3);
        assertThat(playlist.getAccountId()).isEqualTo("acc-rock");
        assertThat(playlist.getFolderId()).isEqualTo("folder-genres");

        // 4. clear
        playlist.clear();
        assertThat(playlist.getTrackCount()).isEqualTo(0);
        assertThat(playlist.getAccountId()).isEqualTo("acc-rock");
        assertThat(playlist.getFolderId()).isEqualTo("folder-genres");
        assertThat(playlist.getPosition()).isEqualTo(4);
    }
}
