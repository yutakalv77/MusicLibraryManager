package com.musiclibrary.service.audio;

import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

import java.util.function.Supplier;

/**
 * Factory for creating AudioPlayer instances with automatic fallback to BasicAudioPlayer
 * when LibVLC native libraries are not detected on the host system.
 */
public final class AudioPlayerFactory {

    private static final System.Logger LOGGER = System.getLogger(AudioPlayerFactory.class.getName());

    private static volatile Supplier<AudioPlayer> customPlayerSupplier = null;
    private static volatile Boolean vlcAvailableOverride = null;

    private AudioPlayerFactory() {
        // Utility class
    }

    /**
     * Creates and returns the default AudioPlayer.
     * If a custom player supplier has been registered (e.g. for testing), it takes precedence.
     * Otherwise, checks whether LibVLC is available:
     * - If available, instantiates VlcjAudioPlayer (falling back to BasicAudioPlayer if instantiation fails).
     * - If not available, logs an informational message and returns BasicAudioPlayer.
     *
     * @return an AudioPlayer instance ready for playback
     */
    public static AudioPlayer createDefaultPlayer() {
        Supplier<AudioPlayer> supplier = customPlayerSupplier;
        if (supplier != null) {
            AudioPlayer player = supplier.get();
            if (player != null) {
                return player;
            }
        }

        if (isVlcAvailable()) {
            try {
                LOGGER.log(System.Logger.Level.INFO, "LibVLC native library detected. Initializing VlcjAudioPlayer.");
                return new VlcjAudioPlayer();
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Failed to initialize VlcjAudioPlayer despite LibVLC detection. Falling back to BasicAudioPlayer.", t);
                return new BasicAudioPlayer();
            }
        } else {
            LOGGER.log(System.Logger.Level.INFO,
                    "LibVLC native library not detected. Using fallback BasicAudioPlayer.");
            return new BasicAudioPlayer();
        }
    }

    /**
     * Checks whether LibVLC is available on the current system by invoking NativeDiscovery.
     * Safely catches UnsatisfiedLinkError, NoClassDefFoundError, and any other exceptions.
     *
     * @return true if LibVLC was discovered and can be linked, false otherwise
     */
    public static boolean isVlcAvailable() {
        Boolean override = vlcAvailableOverride;
        if (override != null) {
            return override;
        }
        try {
            return new NativeDiscovery().discover();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | Exception e) {
            LOGGER.log(System.Logger.Level.DEBUG, "LibVLC discovery failed: " + e.getMessage(), e);
            return false;
        } catch (Throwable t) {
            LOGGER.log(System.Logger.Level.WARNING, "Unexpected error during LibVLC discovery", t);
            return false;
        }
    }

    /**
     * Sets a custom supplier for AudioPlayer creation, primarily for unit and integration testing.
     * Pass null to reset to standard behavior.
     *
     * @param supplier custom player supplier or null
     */
    public static void setCustomPlayerSupplier(Supplier<AudioPlayer> supplier) {
        customPlayerSupplier = supplier;
    }

    /**
     * Overrides the result of isVlcAvailable for testing purposes.
     * Pass null to restore automatic discovery.
     *
     * @param override Boolean override value or null
     */
    static void setVlcAvailableOverride(Boolean override) {
        vlcAvailableOverride = override;
    }
}
