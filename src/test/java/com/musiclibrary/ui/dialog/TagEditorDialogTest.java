package com.musiclibrary.ui.dialog;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Track;
import com.musiclibrary.model.TrackMetadataUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TagEditorDialogTest {

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    private Track createTrack(String id, String title, String artist, String album, String genre, int year, int trackNumber) {
        return Track.builder()
                .id(id)
                .filePath("C:/music/" + id + ".mp3")
                .title(title)
                .artist(artist)
                .album(album)
                .genre(genre)
                .year(year)
                .trackNumber(trackNumber)
                .durationSeconds(180)
                .fileSizeBytes(1024 * 1024 * 5)
                .build();
    }

    @Test
    @DisplayName("Single track initialization populates fields and enables title and track number")
    void testSingleTrackInitialization() {
        Track track = createTrack("t1", "Bohemian Rhapsody", "Queen", "A Night at the Opera", "Rock", 1975, 4);
        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), null);

        try {
            assertThat(dialog.getTitle()).contains("Bohemian Rhapsody");
            assertThat(dialog.getTitleField().getText()).isEqualTo("Bohemian Rhapsody");
            assertThat(dialog.getTitleField().isEnabled()).isTrue();
            assertThat(dialog.getArtistField().getText()).isEqualTo("Queen");
            assertThat(dialog.getAlbumField().getText()).isEqualTo("A Night at the Opera");
            assertThat(dialog.getGenreCombo().getSelectedItem()).isEqualTo("Rock");
            assertThat(dialog.getYearField().getText()).isEqualTo("1975");
            assertThat(dialog.getTrackNumberField().getText()).isEqualTo("4");
            assertThat(dialog.getTrackNumberField().isEnabled()).isTrue();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Multi-track initialization populates common fields, disables title and track number")
    void testMultiTrackInitializationCommonFields() {
        Track track1 = createTrack("t1", "Song A", "The Beatles", "Abbey Road", "Rock", 1969, 1);
        Track track2 = createTrack("t2", "Song B", "The Beatles", "Abbey Road", "Rock", 1969, 2);

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track1, track2), null);

        try {
            assertThat(dialog.getTitle()).contains("2");
            assertThat(dialog.getTitleField().isEnabled()).isFalse();
            assertThat(dialog.getArtistField().getText()).isEqualTo("The Beatles");
            assertThat(dialog.getAlbumField().getText()).isEqualTo("Abbey Road");
            assertThat(dialog.getGenreCombo().getSelectedItem()).isEqualTo("Rock");
            assertThat(dialog.getYearField().getText()).isEqualTo("1969");
            assertThat(dialog.getTrackNumberField().isEnabled()).isFalse();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Multi-track initialization leaves differing fields empty")
    void testMultiTrackInitializationDifferingFields() {
        Track track1 = createTrack("t1", "Song A", "Artist 1", "Album A", "Rock", 1990, 1);
        Track track2 = createTrack("t2", "Song B", "Artist 2", "Album B", "Jazz", 2000, 2);

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track1, track2), null);

        try {
            assertThat(dialog.getTitleField().isEnabled()).isFalse();
            assertThat(dialog.getArtistField().getText()).isEmpty();
            assertThat(dialog.getAlbumField().getText()).isEmpty();
            assertThat(dialog.getGenreCombo().getSelectedItem()).isEqualTo("");
            assertThat(dialog.getYearField().getText()).isEmpty();
            assertThat(dialog.getTrackNumberField().isEnabled()).isFalse();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Saving single track invokes callback with full TrackMetadataUpdate")
    void testSaveSingleTrack() {
        Track track = createTrack("t1", "Old Title", "Old Artist", "Old Album", "Pop", 2010, 1);
        AtomicReference<TrackMetadataUpdate> updateRef = new AtomicReference<>();

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), updateRef::set);

        try {
            dialog.getTitleField().setText("New Title");
            dialog.getArtistField().setText("New Artist");
            dialog.getAlbumField().setText("New Album");
            dialog.getGenreCombo().setSelectedItem("Rock");
            dialog.getYearField().setText("2024");
            dialog.getTrackNumberField().setText("5");

            dialog.getSaveButton().doClick();

            assertThat(dialog.isSaved()).isTrue();
            TrackMetadataUpdate update = updateRef.get();
            assertThat(update).isNotNull();
            assertThat(update.isUpdateTitle()).isTrue();
            assertThat(update.getTitle()).isEqualTo("New Title");
            assertThat(update.isUpdateArtist()).isTrue();
            assertThat(update.getArtist()).isEqualTo("New Artist");
            assertThat(update.isUpdateAlbum()).isTrue();
            assertThat(update.getAlbum()).isEqualTo("New Album");
            assertThat(update.isUpdateGenre()).isTrue();
            assertThat(update.getGenre()).isEqualTo("Rock");
            assertThat(update.isUpdateYear()).isTrue();
            assertThat(update.getYear()).isEqualTo(2024);
            assertThat(update.isUpdateTrackNumber()).isTrue();
            assertThat(update.getTrackNumber()).isEqualTo(5);
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Cancel button closes dialog without invoking callback")
    void testCancelButton() {
        Track track = createTrack("t1", "Song", "Artist", "Album", "Rock", 2020, 1);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), u -> callbackCalled.set(true));

        try {
            dialog.getCancelButton().doClick();

            assertThat(dialog.isSaved()).isFalse();
            assertThat(callbackCalled.get()).isFalse();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Clear artwork marks updateArtwork as true and artworkData as null")
    void testClearArtwork() {
        Track track = Track.builder()
                .id("t1")
                .filePath("C:/music/t1.mp3")
                .title("Title")
                .artist("Artist")
                .album("Album")
                .artworkData(new byte[]{1, 2, 3})
                .build();

        AtomicReference<TrackMetadataUpdate> updateRef = new AtomicReference<>();
        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), updateRef::set);

        try {
            dialog.getClearArtworkButton().doClick();
            dialog.getSaveButton().doClick();

            assertThat(dialog.isSaved()).isTrue();
            TrackMetadataUpdate update = updateRef.get();
            assertThat(update).isNotNull();
            assertThat(update.isUpdateArtwork()).isTrue();
            assertThat(update.getArtworkData()).isNull();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Validation fails for invalid year, dialog remains open")
    void testValidationInvalidYear() {
        Track track = createTrack("t1", "Title", "Artist", "Album", "Rock", 2020, 1);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        AtomicReference<String> errorRef = new AtomicReference<>();

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), u -> callbackCalled.set(true)) {
            @Override
            protected void showValidationError(String message) {
                errorRef.set(message);
            }
        };

        try {
            dialog.getYearField().setText("invalid");
            dialog.getSaveButton().doClick();

            assertThat(dialog.isSaved()).isFalse();
            assertThat(callbackCalled.get()).isFalse();
            assertThat(errorRef.get()).isNotNull();

            // Also test out of range year
            errorRef.set(null);
            dialog.getYearField().setText("999");
            dialog.getSaveButton().doClick();

            assertThat(dialog.isSaved()).isFalse();
            assertThat(callbackCalled.get()).isFalse();
            assertThat(errorRef.get()).isNotNull();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Validation fails for negative track number, dialog remains open")
    void testValidationInvalidTrackNumber() {
        Track track = createTrack("t1", "Title", "Artist", "Album", "Rock", 2020, 1);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        AtomicReference<String> errorRef = new AtomicReference<>();

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), u -> callbackCalled.set(true)) {
            @Override
            protected void showValidationError(String message) {
                errorRef.set(message);
            }
        };

        try {
            dialog.getTrackNumberField().setText("-1");
            dialog.getSaveButton().doClick();

            assertThat(dialog.isSaved()).isFalse();
            assertThat(callbackCalled.get()).isFalse();
            assertThat(errorRef.get()).isNotNull();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Multi-track save only updates explicitly filled fields")
    void testMultiTrackSavePartialFields() {
        Track track1 = createTrack("t1", "Song 1", "Artist A", "Album A", "Rock", 2010, 1);
        Track track2 = createTrack("t2", "Song 2", "Artist B", "Album B", "Jazz", 2012, 2);

        AtomicReference<TrackMetadataUpdate> updateRef = new AtomicReference<>();
        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track1, track2), updateRef::set);

        try {
            // Only update artist and year; album and genre remain empty
            dialog.getArtistField().setText("Shared Artist");
            dialog.getAlbumField().setText("");
            dialog.getGenreCombo().setSelectedItem("");
            dialog.getYearField().setText("2025");

            dialog.getSaveButton().doClick();

            assertThat(dialog.isSaved()).isTrue();
            TrackMetadataUpdate update = updateRef.get();
            assertThat(update).isNotNull();
            assertThat(update.isUpdateArtist()).isTrue();
            assertThat(update.getArtist()).isEqualTo("Shared Artist");
            assertThat(update.isUpdateYear()).isTrue();
            assertThat(update.getYear()).isEqualTo(2025);
            assertThat(update.isUpdateAlbum()).isFalse();
            assertThat(update.isUpdateGenre()).isFalse();
            assertThat(update.isUpdateTitle()).isFalse();
            assertThat(update.isUpdateTrackNumber()).isFalse();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Multi-track common value extraction accurately distinguishes identical, partially identical, and distinct values")
    void testMultiTrackCommonValueExtractionAllCombinations() {
        // Track 1: Artist="Queen", Album="The Game", Genre="Rock", Year=1980
        Track t1 = createTrack("t1", "Song 1", "Queen", "The Game", "Rock", 1980, 1);
        // Track 2: Artist="Queen", Album="The Game", Genre="Pop", Year=1980
        Track t2 = createTrack("t2", "Song 2", "Queen", "The Game", "Pop", 1980, 2);
        // Track 3: Artist="Queen", Album="News of the World", Genre="Disco", Year=1980
        Track t3 = createTrack("t3", "Song 3", "Queen", "News of the World", "Disco", 1980, 3);

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(t1, t2, t3), null);
        try {
            // Artist: all 3 share "Queen" -> populated
            assertThat(dialog.getArtistField().getText()).isEqualTo("Queen");

            // Year: all 3 share 1980 -> populated
            assertThat(dialog.getYearField().getText()).isEqualTo("1980");

            // Album: 2 share "The Game", 1 has "News of the World" -> empty
            assertThat(dialog.getAlbumField().getText()).isEmpty();

            // Genre: all 3 different -> empty
            assertThat(dialog.getGenreCombo().getSelectedItem()).isEqualTo("");

            // Title & Track Number are disabled in multi-track mode
            assertThat(dialog.getTitleField().isEnabled()).isFalse();
            assertThat(dialog.getTrackNumberField().isEnabled()).isFalse();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("validateInputs enforces exact boundaries for year (1000..2999) and track number (>= 0)")
    void testYearAndTrackNumberValidationComprehensiveBoundaries() {
        Track track = createTrack("t1", "Title", "Artist", "Album", "Rock", 2020, 1);
        AtomicReference<String> errorRef = new AtomicReference<>();

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), null) {
            @Override
            protected void showValidationError(String message) {
                errorRef.set(message);
            }
        };

        try {
            // --- Year Boundaries (1000..2999) ---
            // 999: Out of lower bound
            dialog.getYearField().setText("999");
            assertThat(dialog.validateInputs()).isFalse();
            assertThat(errorRef.get()).isNotNull();

            // 1000: Exact lower bound -> Valid
            errorRef.set(null);
            dialog.getYearField().setText("1000");
            assertThat(dialog.validateInputs()).isTrue();
            assertThat(errorRef.get()).isNull();

            // 2026: Mid range -> Valid
            dialog.getYearField().setText("2026");
            assertThat(dialog.validateInputs()).isTrue();

            // 2999: Exact upper bound -> Valid
            dialog.getYearField().setText("2999");
            assertThat(dialog.validateInputs()).isTrue();

            // 3000: Out of upper bound -> Invalid
            errorRef.set(null);
            dialog.getYearField().setText("3000");
            assertThat(dialog.validateInputs()).isFalse();
            assertThat(errorRef.get()).isNotNull();

            // Non-numeric year
            errorRef.set(null);
            dialog.getYearField().setText("twenty-twenty");
            assertThat(dialog.validateInputs()).isFalse();
            assertThat(errorRef.get()).isNotNull();

            // Blank year -> Valid (skip year)
            errorRef.set(null);
            dialog.getYearField().setText("");
            assertThat(dialog.validateInputs()).isTrue();

            // --- Track Number Boundaries (>= 0) ---
            // Reset valid year first
            dialog.getYearField().setText("2024");

            // -1: Negative -> Invalid
            errorRef.set(null);
            dialog.getTrackNumberField().setText("-1");
            assertThat(dialog.validateInputs()).isFalse();
            assertThat(errorRef.get()).isNotNull();

            // 0: Valid
            errorRef.set(null);
            dialog.getTrackNumberField().setText("0");
            assertThat(dialog.validateInputs()).isTrue();

            // 1: Valid
            dialog.getTrackNumberField().setText("1");
            assertThat(dialog.validateInputs()).isTrue();

            // 999: Valid
            dialog.getTrackNumberField().setText("999");
            assertThat(dialog.validateInputs()).isTrue();

            // Non-numeric track number -> Invalid
            errorRef.set(null);
            dialog.getTrackNumberField().setText("one");
            assertThat(dialog.validateInputs()).isFalse();
            assertThat(errorRef.get()).isNotNull();

            // Blank track number -> Valid (0)
            dialog.getTrackNumberField().setText("");
            assertThat(dialog.validateInputs()).isTrue();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("ESC key binding triggers dialog disposal without invoking onSaveCallback")
    void testEscKeyClosesDialogWithoutSaving() {
        Track track = createTrack("t1", "Title", "Artist", "Album", "Rock", 2020, 1);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        TagEditorDialog dialog = new TagEditorDialog(null, List.of(track), u -> callbackCalled.set(true));

        try {
            // Simulate ESC key stroke action registered on root pane
            javax.swing.KeyStroke escStroke = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
            java.awt.event.ActionListener escAction = dialog.getRootPane().getActionForKeyStroke(escStroke);
            assertThat(escAction).isNotNull();

            escAction.actionPerformed(new java.awt.event.ActionEvent(dialog, java.awt.event.ActionEvent.ACTION_PERFORMED, "escape"));

            assertThat(dialog.isSaved()).isFalse();
            assertThat(callbackCalled.get()).isFalse();
        } finally {
            dialog.dispose();
        }
    }
}
