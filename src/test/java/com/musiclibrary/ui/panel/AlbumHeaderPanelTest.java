package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Album;
import com.musiclibrary.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AlbumHeaderPanelTest {

    private AlbumHeaderPanel headerPanel;
    private Album sampleAlbum;

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);

        Track t1 = Track.builder().id("1").filePath("tracks/1.mp3").title("Song 1").artist("Queen").album("A Night at the Opera").year(1975).durationSeconds(354).build();
        Track t2 = Track.builder().id("2").filePath("tracks/2.mp3").title("Song 2").artist("Queen").album("A Night at the Opera").year(1975).durationSeconds(219).build();
        sampleAlbum = Album.fromTracks(List.of(t1, t2)).get(0);

        headerPanel = new AlbumHeaderPanel();
    }

    @Test
    @DisplayName("setAlbum populates labels with title, artist, year, track count, and duration")
    void testSetAlbum() {
        headerPanel.setAlbum(sampleAlbum);

        assertThat(headerPanel.getAlbum()).isEqualTo(sampleAlbum);

        // Access internal components through container hierarchy
        JPanel contentPanel = (JPanel) headerPanel.getComponent(1);
        JPanel detailsPanel = (JPanel) contentPanel.getComponent(1);

        JLabel titleLabel = (JLabel) detailsPanel.getComponent(0);
        JLabel artistLabel = (JLabel) detailsPanel.getComponent(2);
        JLabel metaLabel = (JLabel) detailsPanel.getComponent(4);

        assertThat(titleLabel.getText()).isEqualTo("A Night at the Opera");
        assertThat(artistLabel.getText()).isEqualTo("Queen");
        assertThat(metaLabel.getText())
                .contains("1975")
                .contains("2 songs")
                .contains("9:33"); // 354 + 219 = 573s -> 9:33
    }

    @Test
    @DisplayName("setAlbum(null) gracefully clears labels and resets artwork to default")
    void testSetAlbumNullClearsSafely() {
        headerPanel.setAlbum(sampleAlbum);
        headerPanel.setAlbum(null);

        assertThat(headerPanel.getAlbum()).isNull();

        JPanel contentPanel = (JPanel) headerPanel.getComponent(1);
        JPanel detailsPanel = (JPanel) contentPanel.getComponent(1);

        JLabel titleLabel = (JLabel) detailsPanel.getComponent(0);
        JLabel artistLabel = (JLabel) detailsPanel.getComponent(2);
        JLabel metaLabel = (JLabel) detailsPanel.getComponent(4);

        assertThat(titleLabel.getText()).isEmpty();
        assertThat(artistLabel.getText()).isEmpty();
        assertThat(metaLabel.getText()).isEmpty();
    }

    @Test
    @DisplayName("Clicking back button invokes onBackAction callback")
    void testBackButtonAction() {
        AtomicBoolean backClicked = new AtomicBoolean(false);
        headerPanel.setOnBackAction(() -> backClicked.set(true));

        JPanel topBar = (JPanel) headerPanel.getComponent(0);
        JButton backButton = (JButton) topBar.getComponent(0);

        backButton.doClick();

        assertThat(backClicked.get()).isTrue();
    }

    @Test
    @DisplayName("updateLanguage refreshes back button and meta text between English and Japanese")
    void testUpdateLanguage() {
        headerPanel.setAlbum(sampleAlbum);

        JPanel topBar = (JPanel) headerPanel.getComponent(0);
        JButton backButton = (JButton) topBar.getComponent(0);
        JPanel contentPanel = (JPanel) headerPanel.getComponent(1);
        JPanel detailsPanel = (JPanel) contentPanel.getComponent(1);
        JLabel metaLabel = (JLabel) detailsPanel.getComponent(4);

        assertThat(backButton.getText()).contains("Back to Albums");
        assertThat(metaLabel.getText()).contains("songs");

        // Switch to Japanese
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        headerPanel.updateLanguage();

        assertThat(backButton.getText()).contains("アルバム一覧に戻る");
        assertThat(metaLabel.getText()).contains("曲");

        // Switch back to English
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        headerPanel.updateLanguage();

        assertThat(backButton.getText()).contains("Back to Albums");
        assertThat(metaLabel.getText()).contains("songs");
    }
}
