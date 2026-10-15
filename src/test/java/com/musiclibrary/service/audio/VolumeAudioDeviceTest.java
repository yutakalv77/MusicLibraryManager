package com.musiclibrary.service.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
