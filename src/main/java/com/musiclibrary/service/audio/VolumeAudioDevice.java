package com.musiclibrary.service.audio;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDeviceBase;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
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

    private SourceDataLine source;
    private AudioFormat fmt;
    private byte[] byteBuf = new byte[4096];
    private float volume = 1.0f; // 0.0f - 1.0f
    private boolean hardwareGainSupported = false;

    public VolumeAudioDevice() {
        this(1.0f);
    }

    public VolumeAudioDevice(float initialVolume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, initialVolume));
    }

    /**
     * Sets linear volume in range [0.0f, 1.0f] and applies it to the active line.
     *
     * @param volume target volume
     */
    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, Math.min(1.0f, volume));
        applyVolume();
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

    @Override
    protected void openImpl() throws JavaLayerException {
        // Line initialized on first writeImpl
    }

    protected void createSource() throws JavaLayerException {
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
            source.open(fmt);
            source.start();
            applyVolume();
        } catch (RuntimeException | LinkageError | LineUnavailableException e) {
            // In headless environments or missing soundcards, line remains null.
            source = null;
        }
    }

    private void applyVolume() {
        if (source != null && source.isOpen()) {
            try {
                if (source.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) source.getControl(FloatControl.Type.MASTER_GAIN);
                    float min = gainControl.getMinimum();
                    float max = Math.min(0.0f, gainControl.getMaximum());
                    if (volume <= 0.001f) {
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
            } catch (Exception ignored) {}
            try {
                source.close();
            } catch (Exception ignored) {}
            source = null;
        }
    }

    @Override
    protected void flushImpl() {
        if (source != null) {
            try {
                source.drain();
            } catch (Exception ignored) {}
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
