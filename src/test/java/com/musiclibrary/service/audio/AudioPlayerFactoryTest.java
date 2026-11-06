package com.musiclibrary.service.audio;

import com.musiclibrary.model.PlaybackState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AudioPlayerFactoryTest {

    @AfterEach
    void tearDown() {
        AudioPlayerFactory.setCustomPlayerSupplier(null);
        AudioPlayerFactory.setVlcAvailableOverride(null);
    }

    @Test
    @DisplayName("isVlcAvailable returns boolean safely without throwing any exceptions")
    void testIsVlcAvailableSafeExecution() {
        assertThatCode(() -> {
            boolean available = AudioPlayerFactory.isVlcAvailable();
            // Should be either true or false depending on host machine LibVLC installation
            assertThat(available).isIn(true, false);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("createDefaultPlayer produces non-null, ready-to-use AudioPlayer in STOPPED state")
    void testCreateDefaultPlayerNotNullAndUsable() {
        AudioPlayer player = AudioPlayerFactory.createDefaultPlayer();
        try {
            assertThat(player).isNotNull();
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
            assertThat(player.isPlaying()).isFalse();
            assertThat(player.getVolume()).isEqualTo(0.7f);
        } finally {
            player.close();
        }
    }

    @Test
    @DisplayName("When VLC is unavailable (override false), createDefaultPlayer safely falls back to BasicAudioPlayer")
    void testFallbackToBasicAudioPlayerWhenVlcUnavailable() {
        AudioPlayerFactory.setVlcAvailableOverride(false);

        assertThat(AudioPlayerFactory.isVlcAvailable()).isFalse();

        AudioPlayer player = AudioPlayerFactory.createDefaultPlayer();
        try {
            assertThat(player).isNotNull();
            assertThat(player).isInstanceOf(BasicAudioPlayer.class);
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        } finally {
            player.close();
        }
    }

    @Test
    @DisplayName("When VLC is reported available but native construction fails, safely falls back to BasicAudioPlayer")
    void testFallbackWhenVlcConstructionFails() {
        // Even if override is true, if native libvlc dll/so is missing or invalid, it must catch Throwable and fallback
        AudioPlayerFactory.setVlcAvailableOverride(true);

        assertThat(AudioPlayerFactory.isVlcAvailable()).isTrue();

        AudioPlayer player = AudioPlayerFactory.createDefaultPlayer();
        try {
            assertThat(player).isNotNull();
            // Either VlcjAudioPlayer if LibVLC is actually installed, or BasicAudioPlayer on fallback
            assertThat(player).satisfiesAnyOf(
                    p -> assertThat(p).isInstanceOf(VlcjAudioPlayer.class),
                    p -> assertThat(p).isInstanceOf(BasicAudioPlayer.class)
            );
            assertThat(player.getState()).isEqualTo(PlaybackState.STOPPED);
        } finally {
            player.close();
        }
    }

    @Test
    @DisplayName("setCustomPlayerSupplier overrides player creation and reset restores default logic")
    void testCustomPlayerSupplierPrecedenceAndReset() {
        AtomicBoolean customCreated = new AtomicBoolean(false);
        AudioPlayer customMockPlayer = new BasicAudioPlayer();

        try {
            AudioPlayerFactory.setCustomPlayerSupplier(() -> {
                customCreated.set(true);
                return customMockPlayer;
            });

            AudioPlayer player1 = AudioPlayerFactory.createDefaultPlayer();
            assertThat(player1).isSameAs(customMockPlayer);
            assertThat(customCreated.get()).isTrue();

            // When custom supplier returns null, falls through to default creation
            AudioPlayerFactory.setCustomPlayerSupplier(() -> null);
            AudioPlayer player2 = AudioPlayerFactory.createDefaultPlayer();
            assertThat(player2).isNotNull();
            assertThat(player2).isNotSameAs(customMockPlayer);
            player2.close();

            // When reset to null, default creation is restored
            AudioPlayerFactory.setCustomPlayerSupplier(null);
            AudioPlayer player3 = AudioPlayerFactory.createDefaultPlayer();
            assertThat(player3).isNotNull();
            player3.close();
        } finally {
            customMockPlayer.close();
        }
    }

    @Test
    @DisplayName("PlayerController integrates seamlessly with AudioPlayer created by AudioPlayerFactory")
    void testPlayerControllerIntegrationWithDefaultAudioPlayer() {
        AudioPlayer player = AudioPlayerFactory.createDefaultPlayer();
        com.musiclibrary.ui.panel.ControlBarPanel controlBar = new com.musiclibrary.ui.panel.ControlBarPanel();

        try (com.musiclibrary.controller.PlayerController controller = new com.musiclibrary.controller.PlayerController(player, controlBar)) {
            assertThat(controller.getAudioPlayer()).isSameAs(player);
            assertThat(controller.getControlBarPanel()).isSameAs(controlBar);
            assertThat(controlBar.getVolumeSlider().getValue()).isEqualTo(70);

            // Change volume on slider reflects to player
            controlBar.getVolumeSlider().setValue(50);
            assertThat(player.getVolume()).isEqualTo(0.5f);
        }
    }

    @Test
    @DisplayName("MainFrame initializes audio engine through AudioPlayerFactory and binds it to PlayerController")
    void testMainFrameAudioPlayerFactoryIntegration() {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        com.musiclibrary.ui.MainFrame frame = new com.musiclibrary.ui.MainFrame();
        try {
            AudioPlayer audioPlayer = frame.getAudioPlayer();
            assertThat(audioPlayer).isNotNull();
            assertThat(frame.getPlayerController().getAudioPlayer()).isSameAs(audioPlayer);
            assertThat(audioPlayer.getState()).isEqualTo(PlaybackState.STOPPED);
        } finally {
            frame.dispose();
        }
    }
}
