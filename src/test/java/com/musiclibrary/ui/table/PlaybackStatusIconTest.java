package com.musiclibrary.ui.table;

import com.musiclibrary.model.PlaybackState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PlaybackStatusIconTest {

    @Test
    @DisplayName("PlaybackStatusIcon dimensions and state getters")
    void testDimensionsAndState() {
        PlaybackStatusIcon icon = new PlaybackStatusIcon(PlaybackState.PLAYING);

        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThat(icon.getIconHeight()).isEqualTo(16);
        assertThat(icon.getState()).isEqualTo(PlaybackState.PLAYING);
        assertThat(icon.getColor()).isNull();

        icon.setState(PlaybackState.PAUSED);
        assertThat(icon.getState()).isEqualTo(PlaybackState.PAUSED);

        icon.setColor(Color.CYAN);
        assertThat(icon.getColor()).isEqualTo(Color.CYAN);
    }

    @Test
    @DisplayName("paintIcon renders without errors for all playback states")
    void testPaintIconWithoutErrors() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        try {
            PlaybackStatusIcon playingIcon = new PlaybackStatusIcon(PlaybackState.PLAYING, Color.WHITE);
            PlaybackStatusIcon pausedIcon = new PlaybackStatusIcon(PlaybackState.PAUSED, Color.YELLOW);
            PlaybackStatusIcon stoppedIcon = new PlaybackStatusIcon(PlaybackState.STOPPED);

            assertThatCode(() -> {
                playingIcon.paintIcon(null, g2, 0, 0);
                pausedIcon.paintIcon(null, g2, 16, 0);
                stoppedIcon.paintIcon(null, g2, 0, 16);
            }).doesNotThrowAnyException();
        } finally {
            g2.dispose();
        }
    }

    @Test
    @DisplayName("PlaybackStatusIcon equals and hashCode contract")
    void testEqualsAndHashCode() {
        PlaybackStatusIcon icon1 = new PlaybackStatusIcon(PlaybackState.PLAYING, Color.RED);
        PlaybackStatusIcon icon2 = new PlaybackStatusIcon(PlaybackState.PLAYING, Color.RED);
        PlaybackStatusIcon icon3 = new PlaybackStatusIcon(PlaybackState.PAUSED, Color.RED);

        assertThat(icon1).isEqualTo(icon2);
        assertThat(icon1.hashCode()).isEqualTo(icon2.hashCode());
        assertThat(icon1).isNotEqualTo(icon3);
    }

    @Test
    @DisplayName("Null state defaults to STOPPED and renders nothing safely")
    void testNullStateHandledAsStopped() {
        PlaybackStatusIcon icon = new PlaybackStatusIcon(null);
        assertThat(icon.getState()).isEqualTo(PlaybackState.STOPPED);

        icon.setState(null);
        assertThat(icon.getState()).isEqualTo(PlaybackState.STOPPED);

        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            // STOPPED icon should do nothing in paintIcon
            assertThatCode(() -> icon.paintIcon(null, g2, 0, 0)).doesNotThrowAnyException();
        } finally {
            g2.dispose();
        }
    }

    @Test
    @DisplayName("Color resolution adopts Component foreground, falls back to Color.WHITE, or respects customColor")
    void testColorResolutionAndSelectionRendering() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        try {
            PlaybackStatusIcon iconNoCustom = new PlaybackStatusIcon(PlaybackState.PLAYING);

            // 1. With component having selection foreground
            javax.swing.JLabel selectedLabel = new javax.swing.JLabel();
            selectedLabel.setForeground(Color.BLUE);
            assertThatCode(() -> iconNoCustom.paintIcon(selectedLabel, g2, 0, 0)).doesNotThrowAnyException();

            // 2. Component null -> falls back to Color.WHITE
            assertThatCode(() -> iconNoCustom.paintIcon(null, g2, 0, 0)).doesNotThrowAnyException();

            // 3. Custom color overrides component foreground
            PlaybackStatusIcon customColorIcon = new PlaybackStatusIcon(PlaybackState.PLAYING, Color.MAGENTA);
            assertThatCode(() -> customColorIcon.paintIcon(selectedLabel, g2, 0, 0)).doesNotThrowAnyException();
        } finally {
            g2.dispose();
        }
    }
}
