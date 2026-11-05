package com.musiclibrary.service.audio;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * High-fidelity AudioDevice supporting hardware mixer volume control via
 * FloatControl.Type.MASTER_GAIN, with fallback software DSP scaling and
 * graceful degradation in headless or non-audio CI environments.
 */
public class VolumeAudioDevice extends AudioDeviceBase {

    private static final System.Logger LOGGER = System.getLogger(VolumeAudioDevice.class.getName());

    private volatile SourceDataLine source;
    private AudioFormat fmt;
    private byte[] byteBuf = new byte[4096];
    private float volume; // 0.0f - 1.0f
    private boolean hardwareGainSupported = false;

    public VolumeAudioDevice() {
        this(1.0f);
    }

    public VolumeAudioDevice(float initialVolume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, initialVolume));
    }

    /**
     * Sets linear volume in range [0.0f, 1.0f] and applies it to the active line.
     * When transitioning across the mute boundary (volume <= 0.001f <-> non-zero),
     * the audio line buffer is immediately flushed so that silence or audio output
     * commences instantly without buffered latency.
     *
     * @param volume target volume
     */
    public void setVolume(float volume) {
        float clamped = Math.max(0.0f, Math.min(1.0f, volume));
        boolean wasMuted = this.volume <= 0.001f;
        boolean nowMuted = clamped <= 0.001f;
        this.volume = clamped;
        applyVolume();

        if (wasMuted != nowMuted) {
            SourceDataLine line = this.source;
            if (line != null && line.isOpen()) {
                try {
                    line.flush();
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.DEBUG, "Failed to flush audio line on mute transition", e);
                }
            }
        }
    }

    /**
     * Returns the current volume.
     *
     * @return volume in range [0.0f, 1.0f]
     */
    public float getVolume() {
        return volume;
    }

    public boolean isHardwareGainSupported() {
        return hardwareGainSupported;
    }

    /**
     * Instantly pauses audio output at the hardware/mixer level.
     */
    public void pauseAudio() {
        SourceDataLine line = this.source;
        if (line != null && line.isOpen()) {
            try {
                line.stop();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to pause audio line", e);
            }
        }
    }

    /**
     * Resumes audio output at the hardware/mixer level.
     */
    public void resumeAudio() {
        SourceDataLine line = this.source;
        if (line != null && line.isOpen()) {
            try {
                line.start();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to resume audio line", e);
            }
        }
    }

    /**
     * Instantly stops audio output and flushes buffered audio bytes from the mixer.
     */
    public void stopAudio() {
        SourceDataLine line = this.source;
        if (line != null && line.isOpen()) {
            try {
                line.stop();
                line.flush();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to stop/flush audio line", e);
            }
        }
    }

    @Override
    protected void openImpl() {
        // Line initialized on first writeImpl
    }

    protected void createSource() {
        try {
            if (fmt == null) {
                Decoder decoder = getDecoder();
                if (decoder != null) {
                    fmt = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
                } else {
                    fmt = new AudioFormat(44100.0f, 16, 2, true, false);
                }
            }
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            source = (SourceDataLine) AudioSystem.getLine(info);

            // Open with low-latency buffer (~100ms, aligned to at least 4 JLayer frames)
            int frameSize = fmt.getFrameSize() > 0 ? fmt.getFrameSize() : 4;
            float sampleRate = fmt.getSampleRate() > 0 ? fmt.getSampleRate() : 44100.0f;
            int jlayerFrameBytes = 1152 * frameSize;
            int minBuffer = jlayerFrameBytes * 4;
            int calculatedBuffer = (int) (sampleRate * frameSize * 0.100f);
            int bufferSize = Math.max(minBuffer, calculatedBuffer);
            bufferSize = (bufferSize / frameSize) * frameSize;

            try {
                source.open(fmt, bufferSize);
            } catch (Exception e) {
                source.open(fmt);
            }
            source.start();
            applyVolume();
        } catch (RuntimeException | LinkageError | LineUnavailableException e) {
            // In headless environments or missing soundcards, line remains null.
            source = null;
        }
    }

    private void applyVolume() {
        if (source != null && source.isOpen()) {
            boolean isMuted = volume <= 0.001f;
            try {
                if (source.isControlSupported(BooleanControl.Type.MUTE)) {
                    BooleanControl muteControl = (BooleanControl) source.getControl(BooleanControl.Type.MUTE);
                    muteControl.setValue(isMuted);
                }
            } catch (Exception ignored) {
            }

            try {
                if (source.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) source.getControl(FloatControl.Type.MASTER_GAIN);
                    float min = gainControl.getMinimum();
                    float max = Math.min(0.0f, gainControl.getMaximum());
                    if (isMuted) {
                        gainControl.setValue(min);
                    } else {
                        // Decibel conversion: 20 * log10(volume)
                        float dB = (float) (20.0 * Math.log10(volume));
                        dB = Math.max(min, Math.min(max, dB));
                        gainControl.setValue(dB);
                    }
                    hardwareGainSupported = true;
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        hardwareGainSupported = false;
    }

    @Override
    protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
        if (source == null) {
            createSource();
        }
        if (source == null) {
            // Null-device fallback for headless / line-unavailable systems
            return;
        }

        // If hardware MASTER_GAIN is not supported on this mixer line,
        // perform linear software sample scaling
        if (!hardwareGainSupported && volume < 0.999f) {
            short[] scaled = new short[len];
            for (int i = 0; i < len; i++) {
                scaled[i] = (short) Math.round(samples[offs + i] * volume);
            }
            byte[] bytes = toByteArray(scaled, 0, len);
            source.write(bytes, 0, len * 2);
        } else {
            byte[] bytes = toByteArray(samples, offs, len);
            source.write(bytes, 0, len * 2);
        }
    }

    private byte[] toByteArray(short[] samples, int offs, int len) {
        int byteCount = len * 2;
        if (byteBuf.length < byteCount) {
            byteBuf = new byte[byteCount + 1024];
        }
        int idx = 0;
        for (int i = 0; i < len; i++) {
            short s = samples[offs + i];
            byteBuf[idx++] = (byte) (s & 0xFF);
            byteBuf[idx++] = (byte) ((s >> 8) & 0xFF);
        }
        return byteBuf;
    }

    @Override
    protected void closeImpl() {
        if (source != null) {
            try {
                source.stop();
                source.flush();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to stop/flush source data line", e);
            }
            try {
                source.close();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to close source data line", e);
            }
            source = null;
        }
    }

    @Override
    protected void flushImpl() {
        if (source != null) {
            try {
                source.drain();
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.DEBUG, "Failed to drain source data line", e);
            }
        }
    }

    @Override
    public int getPosition() {
        if (source != null) {
            return (int) (source.getMicrosecondPosition() / 1000L);
        }
        return 0;
    }
}
