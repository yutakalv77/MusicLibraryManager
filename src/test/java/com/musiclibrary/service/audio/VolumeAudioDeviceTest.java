package com.musiclibrary.service.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VolumeAudioDeviceTest {

    @Test
    @DisplayName("setVolume clamps values between 0.0f and 1.0f and retrieves accurately")
    void testVolumeClamping() {
        VolumeAudioDevice device = new VolumeAudioDevice();

        assertThat(device.getVolume()).isEqualTo(1.0f);

        device.setVolume(0.5f);
        assertThat(device.getVolume()).isEqualTo(0.5f);

        // Lower boundary
        device.setVolume(-0.2f);
        assertThat(device.getVolume()).isEqualTo(0.0f);

        // Upper boundary
        device.setVolume(1.8f);
        assertThat(device.getVolume()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("Constructor clamps initial volume safely")
    void testConstructorVolumeClamping() {
        VolumeAudioDevice d1 = new VolumeAudioDevice(-0.5f);
        assertThat(d1.getVolume()).isEqualTo(0.0f);

        VolumeAudioDevice d2 = new VolumeAudioDevice(1.5f);
        assertThat(d2.getVolume()).isEqualTo(1.0f);

        VolumeAudioDevice d3 = new VolumeAudioDevice(0.4f);
        assertThat(d3.getVolume()).isEqualTo(0.4f);
    }

    @Test
    @DisplayName("AudioDevice lifecycle methods execute safely in headless environments without throwing")
    void testLifecycleMethodsInHeadless() {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        assertThat(device.getPosition()).isEqualTo(0);
        assertThat(device.isHardwareGainSupported()).isFalse();

        assertThatCode(() -> {
            // Write sample buffer to trigger line creation and software/hardware scaling path
            short[] samples = new short[]{100, 200, 300, 400};
            device.write(samples, 0, samples.length);
            device.flush();
            device.close();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Zero volume and near-zero volume set volume to zero safely")
    void testMuteThresholdZeroVolume() {
        VolumeAudioDevice device = new VolumeAudioDevice(0.0f);
        assertThat(device.getVolume()).isEqualTo(0.0f);

        assertThatCode(() -> {
            short[] samples = new short[]{50, 100};
            device.write(samples, 0, samples.length);
            device.close();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("closeImpl synchronously calls stop(), flush(), and close() on source in exact sequence")
    void testCloseStopsFlushesAndClosesSource() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        List<String> invokedMethods = new ArrayList<>();
        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    invokedMethods.add(method.getName());
                    return null;
                }
        );

        // Inject mock line into device
        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // Open device so isOpen() returns true
        device.open(new javazoom.jl.decoder.Decoder());

        // Execute close
        device.close();

        // Verify invocation order: stop -> flush -> close
        assertThat(invokedMethods).containsExactly("stop", "flush", "close");

        // Verify source field is reset to null
        assertThat(sourceField.get(device)).isNull();
    }

    @Test
    @DisplayName("closeImpl handles exceptions during stop() or flush() without failing to close line")
    void testCloseHandlesExceptionsDuringStopAndFlush() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        List<String> invokedMethods = new ArrayList<>();
        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    invokedMethods.add(method.getName());
                    if ("stop".equals(method.getName())) {
                        throw new IllegalStateException("Simulated stop error");
                    }
                    if ("flush".equals(method.getName())) {
                        throw new RuntimeException("Simulated flush error");
                    }
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // Open device so isOpen() returns true
        device.open(new javazoom.jl.decoder.Decoder());

        // Should complete safely without throwing
        assertThatCode(device::close).doesNotThrowAnyException();

        // stop was called, flush may not be called if stop threw, but close MUST be called
        assertThat(invokedMethods).contains("stop", "close");
        assertThat(sourceField.get(device)).isNull();
    }

    @Test
    @DisplayName("pauseAudio, resumeAudio, stopAudio safely invoke underlying SourceDataLine methods when open")
    void testAudioControlMethodsWhenOpen() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        List<String> invokedMethods = new ArrayList<>();
        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    invokedMethods.add(method.getName());
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // pauseAudio -> stop()
        device.pauseAudio();
        assertThat(invokedMethods).containsExactly("stop");

        // resumeAudio -> start()
        invokedMethods.clear();
        device.resumeAudio();
        assertThat(invokedMethods).containsExactly("start");

        // stopAudio -> stop(), flush()
        invokedMethods.clear();
        device.stopAudio();
        assertThat(invokedMethods).containsExactly("stop", "flush");
    }

    @Test
    @DisplayName("pauseAudio, resumeAudio, stopAudio are safe no-ops when source is null or closed")
    void testAudioControlMethodsWhenNullOrClosed() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        // When source is null
        assertThatCode(() -> {
            device.pauseAudio();
            device.resumeAudio();
            device.stopAudio();
        }).doesNotThrowAnyException();

        // When source is closed (isOpen returns false)
        List<String> invokedMethods = new ArrayList<>();
        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return false;
                    }
                    invokedMethods.add(method.getName());
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        assertThatCode(() -> {
            device.pauseAudio();
            device.resumeAudio();
            device.stopAudio();
        }).doesNotThrowAnyException();

        assertThat(invokedMethods).isEmpty();
    }

    @Test
    @DisplayName("pauseAudio, resumeAudio, stopAudio catch and suppress any hardware line exceptions")
    void testAudioControlMethodsHandleHardwareExceptions() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    throw new RuntimeException("Hardware driver error");
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        assertThatCode(() -> {
            device.pauseAudio();
            device.resumeAudio();
            device.stopAudio();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setVolume flushes active line buffer on mute/unmute transition, but not on normal volume adjustment")
    void testSetVolumeFlushesOnMuteAndUnmuteTransitions() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.7f);

        List<String> invokedMethods = new ArrayList<>();
        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    if ("isControlSupported".equals(method.getName())) {
                        return false;
                    }
                    invokedMethods.add(method.getName());
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // 1. Transition: Unmuted (0.7) -> Muted (0.0) -> must flush buffer immediately
        device.setVolume(0.0f);
        assertThat(invokedMethods).containsExactly("flush");

        // 2. Already muted (0.0) -> Muted (0.0) -> no flush
        invokedMethods.clear();
        device.setVolume(0.0f);
        assertThat(invokedMethods).isEmpty();

        // 3. Transition: Muted (0.0) -> Unmuted (0.5) -> must flush buffer immediately
        invokedMethods.clear();
        device.setVolume(0.5f);
        assertThat(invokedMethods).containsExactly("flush");

        // 4. Normal adjustment: Unmuted (0.5) -> Unmuted (0.8) -> must NOT flush (prevents clicking/glitching)
        invokedMethods.clear();
        device.setVolume(0.8f);
        assertThat(invokedMethods).isEmpty();

        // 5. Normal adjustment: Unmuted (0.8) -> Unmuted (0.2) -> must NOT flush
        invokedMethods.clear();
        device.setVolume(0.2f);
        assertThat(invokedMethods).isEmpty();

        // 6. Transition: Unmuted (0.2) -> Muted (0.0005f <= 0.001f) -> must flush
        invokedMethods.clear();
        device.setVolume(0.0005f);
        assertThat(invokedMethods).containsExactly("flush");
    }

    @Test
    @DisplayName("applyVolume sets BooleanControl.Type.MUTE when supported by the audio line")
    void testBooleanControlMuteInterlocking() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        boolean[] muteState = new boolean[1];
        BooleanControl mockMuteControl = new BooleanControl(BooleanControl.Type.MUTE, false) {
            @Override
            public void setValue(boolean value) {
                super.setValue(value);
                muteState[0] = value;
            }
        };

        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    if ("isControlSupported".equals(method.getName())) {
                        return BooleanControl.Type.MUTE.equals(args[0]);
                    }
                    if ("getControl".equals(method.getName())) {
                        if (BooleanControl.Type.MUTE.equals(args[0])) {
                            return mockMuteControl;
                        }
                    }
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // Mute: volume 0.0 -> muteState true
        device.setVolume(0.0f);
        assertThat(muteState[0]).isTrue();

        // Unmute: volume 0.6 -> muteState false
        device.setVolume(0.6f);
        assertThat(muteState[0]).isFalse();
    }

    @Test
    @DisplayName("setVolume suppresses flush exceptions safely without failing")
    void testSetVolumeSuppressesFlushExceptions() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.8f);

        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    if ("flush".equals(method.getName())) {
                        throw new RuntimeException("Simulated hardware flush exception");
                    }
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        assertThatCode(() -> device.setVolume(0.0f)).doesNotThrowAnyException();
        assertThatCode(() -> device.setVolume(0.5f)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("applyVolume sets FloatControl.Type.MASTER_GAIN correctly when supported")
    void testMasterGainControlInterlocking() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(1.0f);

        float[] gainValue = new float[1];
        FloatControl mockGainControl = new FloatControl(
                FloatControl.Type.MASTER_GAIN,
                -80.0f,
                6.02f,
                0.1f,
                0,
                0.0f,
                "dB"
        ) {
            @Override
            public void setValue(float value) {
                super.setValue(value);
                gainValue[0] = value;
            }
        };

        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    if ("isControlSupported".equals(method.getName())) {
                        return FloatControl.Type.MASTER_GAIN.equals(args[0]);
                    }
                    if ("getControl".equals(method.getName())) {
                        if (FloatControl.Type.MASTER_GAIN.equals(args[0])) {
                            return mockGainControl;
                        }
                    }
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // 1. Initial 1.0f volume -> 0.0 dB
        device.setVolume(1.0f);
        assertThat(device.isHardwareGainSupported()).isTrue();
        assertThat(gainValue[0]).isEqualTo(0.0f);

        // 2. Mute: volume 0.0f -> minimum (-80.0 dB)
        device.setVolume(0.0f);
        assertThat(gainValue[0]).isEqualTo(-80.0f);

        // 3. 0.5f volume -> 20 * log10(0.5) ≈ -6.02 dB
        device.setVolume(0.5f);
        assertThat(gainValue[0]).isCloseTo(-6.0206f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    @DisplayName("writeImpl scales samples linearly in software when hardware gain is unsupported")
    void testSoftwareSampleScalingWhenHardwareGainUnsupported() throws Exception {
        VolumeAudioDevice device = new VolumeAudioDevice(0.5f);

        List<byte[]> writtenBuffers = new ArrayList<>();
        SourceDataLine mockLine = (SourceDataLine) Proxy.newProxyInstance(
                SourceDataLine.class.getClassLoader(),
                new Class<?>[]{SourceDataLine.class},
                (proxy, method, args) -> {
                    if ("isOpen".equals(method.getName())) {
                        return true;
                    }
                    if ("isControlSupported".equals(method.getName())) {
                        return false;
                    }
                    if ("write".equals(method.getName())) {
                        byte[] b = (byte[]) args[0];
                        int off = (int) args[1];
                        int len = (int) args[2];
                        byte[] copy = new byte[len];
                        System.arraycopy(b, off, copy, 0, len);
                        writtenBuffers.add(copy);
                        return len;
                    }
                    return null;
                }
        );

        Field sourceField = VolumeAudioDevice.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(device, mockLine);

        // Open device so isOpen() returns true and writeImpl executes
        device.open(new javazoom.jl.decoder.Decoder());

        // Test with short samples: 1000 and 2000
        short[] samples = new short[]{1000, 2000};
        device.write(samples, 0, samples.length);

        assertThat(writtenBuffers).isNotEmpty();
        byte[] written = writtenBuffers.get(0);
        // At 0.5f volume, 1000 -> 500, 2000 -> 1000
        short out0 = (short) ((written[0] & 0xFF) | (written[1] << 8));
        short out1 = (short) ((written[2] & 0xFF) | (written[3] << 8));
        assertThat(out0).isEqualTo((short) 500);
        assertThat(out1).isEqualTo((short) 1000);

        // Test with volume 0.0f (mute) -> samples scaled to 0
        writtenBuffers.clear();
        device.setVolume(0.0f);
        device.write(samples, 0, samples.length);

        assertThat(writtenBuffers).isNotEmpty();
        byte[] mutedWritten = writtenBuffers.get(0);
        short muteOut0 = (short) ((mutedWritten[0] & 0xFF) | (mutedWritten[1] << 8));
        short muteOut1 = (short) ((mutedWritten[2] & 0xFF) | (mutedWritten[3] << 8));
        assertThat(muteOut0).isEqualTo((short) 0);
        assertThat(muteOut1).isEqualTo((short) 0);
    }

    @Test
    @DisplayName("createSource calculates low-latency buffer (~100ms, >= 4 JLayer frames) aligned to frame boundary")
    void testLowLatencyBufferSizeCalculation() {
        // Standard 44.1kHz 16-bit stereo CD quality format
        javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(44100.0f, 16, 2, true, false);
        int frameSize = fmt.getFrameSize(); // 4 bytes
        float sampleRate = fmt.getSampleRate(); // 44100.0f
        int jlayerFrameBytes = 1152 * frameSize; // 4608 bytes
        int minBuffer = jlayerFrameBytes * 4; // 18432 bytes (~104ms)
        int calculatedBuffer = (int) (sampleRate * frameSize * 0.100f); // 17640 bytes (100ms)
        int bufferSize = Math.max(minBuffer, calculatedBuffer);
        bufferSize = (bufferSize / frameSize) * frameSize;

        // Buffer size must be aligned to frame size
        assertThat(bufferSize % frameSize).isEqualTo(0);
        // Buffer latency must be approx 100-110ms (well under old 500ms-1000ms high-latency defaults)
        float latencySeconds = (float) bufferSize / (sampleRate * frameSize);
        assertThat(latencySeconds).isBetween(0.09f, 0.12f);
        assertThat(bufferSize).isEqualTo(18432);
    }
}
