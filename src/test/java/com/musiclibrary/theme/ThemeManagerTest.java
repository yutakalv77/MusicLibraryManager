package com.musiclibrary.theme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ThemeManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Default theme is DARK and isDarkMode returns true")
    void testDefaultTheme() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
        assertThat(manager.isDarkMode()).isTrue();
    }

    @Test
    @DisplayName("applyTheme updates currentTheme and dark mode status, and handles null with fallback")
    void testApplyTheme() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        // Apply LIGHT
        manager.applyTheme(AppTheme.LIGHT);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
        assertThat(manager.isDarkMode()).isFalse();

        // Apply DARK
        manager.applyTheme(AppTheme.DARK);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
        assertThat(manager.isDarkMode()).isTrue();

        // Apply SYSTEM
        manager.applyTheme(AppTheme.SYSTEM);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.SYSTEM);
        assertThat(manager.isDarkMode()).isEqualTo(manager.isSystemInDarkMode());

        // Apply null falls back to DARK
        manager.applyTheme(null);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
        assertThat(manager.isDarkMode()).isTrue();
    }

    @Test
    @DisplayName("toggleTheme alternates between DARK and LIGHT")
    void testToggleTheme() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        manager.applyTheme(AppTheme.DARK);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);

        manager.toggleTheme();
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
        assertThat(manager.isDarkMode()).isFalse();

        manager.toggleTheme();
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
        assertThat(manager.isDarkMode()).isTrue();
    }

    @Test
    @DisplayName("toggleTheme from SYSTEM theme switches to opposite of effective system theme")
    void testToggleThemeFromSystem() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        manager.applyTheme(AppTheme.SYSTEM);
        boolean initialDarkMode = manager.isDarkMode();

        manager.toggleTheme();
        if (initialDarkMode) {
            assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
            assertThat(manager.isDarkMode()).isFalse();
        } else {
            assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
            assertThat(manager.isDarkMode()).isTrue();
        }
    }

    @Test
    @DisplayName("Listeners are notified on theme changes and duplicate registration is prevented")
    void testThemeChangeListeners() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        List<AppTheme> received = new ArrayList<>();
        ThemeChangeListener listener = received::add;

        // Register listener (multiple times to test deduplication)
        manager.addThemeChangeListener(listener);
        manager.addThemeChangeListener(listener);

        manager.applyTheme(AppTheme.LIGHT);
        manager.applyTheme(AppTheme.DARK);

        // Deduplication ensures events received only once per theme change
        assertThat(received).containsExactly(AppTheme.LIGHT, AppTheme.DARK);

        // Remove listener
        manager.removeThemeChangeListener(listener);
        manager.applyTheme(AppTheme.LIGHT);
        assertThat(received).hasSize(2); // no new events
    }

    @Test
    @DisplayName("Exception thrown in one listener does not block subsequent listeners")
    void testListenerExceptionIsolation() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        AtomicBoolean failingListenerCalled = new AtomicBoolean(false);
        AtomicBoolean healthyListenerCalled = new AtomicBoolean(false);

        ThemeChangeListener failingListener = newTheme -> {
            failingListenerCalled.set(true);
            throw new RuntimeException("Simulated listener failure");
        };

        ThemeChangeListener healthyListener = newTheme -> healthyListenerCalled.set(true);

        manager.addThemeChangeListener(failingListener);
        manager.addThemeChangeListener(healthyListener);

        assertThatCode(() -> manager.applyTheme(AppTheme.LIGHT))
                .doesNotThrowAnyException();

        assertThat(failingListenerCalled).isTrue();
        assertThat(healthyListenerCalled).isTrue();
    }

    @Test
    @DisplayName("null listener registration and removal is safe and does not throw exception")
    void testNullListenerSafety() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        assertThatCode(() -> {
            manager.addThemeChangeListener(null);
            manager.removeThemeChangeListener(null);
            manager.applyTheme(AppTheme.LIGHT);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("init with explicit theme applies theme and handles null with default DARK")
    void testInitWithExplicitTheme() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        manager.init(AppTheme.LIGHT);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);

        manager.init(null);
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
    }

    @Test
    @DisplayName("Theme configuration is persisted and restored across instances")
    void testThemePersistence() {
        Path configPath = tempDir.resolve("sub").resolve("theme-config.json");
        ThemeManager manager1 = new ThemeManager(configPath);

        manager1.applyTheme(AppTheme.LIGHT);
        assertThat(Files.exists(configPath)).isTrue();

        // Restore in a new instance
        ThemeManager manager2 = new ThemeManager(configPath);
        manager2.init();

        assertThat(manager2.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
        assertThat(manager2.isDarkMode()).isFalse();

        // Save and restore SYSTEM theme
        manager2.applyTheme(AppTheme.SYSTEM);
        ThemeManager manager3 = new ThemeManager(configPath);
        manager3.init();

        assertThat(manager3.getCurrentTheme()).isEqualTo(AppTheme.SYSTEM);
    }

    @Test
    @DisplayName("loadThemeConfig safely falls back on corrupted or missing files")
    void testCorruptedConfigHandling() throws IOException {
        Path configPath = tempDir.resolve("corrupted.json");
        Files.writeString(configPath, "{ invalid json content ]");

        ThemeManager manager = new ThemeManager(configPath);
        AppTheme loaded = manager.loadThemeConfig();

        assertThat(loaded).isNull();

        // Calling init() should fall back to default DARK theme
        manager.init();
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
    }

    @Test
    @DisplayName("loadThemeConfig safely falls back on unknown theme strings")
    void testUnknownThemeConfigHandling() throws IOException {
        Path configPath = tempDir.resolve("unknown.json");
        Files.writeString(configPath, "{\"theme\": \"UNKNOWN_CUSTOM_THEME\"}");

        ThemeManager manager = new ThemeManager(configPath);
        AppTheme loaded = manager.loadThemeConfig();

        assertThat(loaded).isNull();

        manager.init();
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
    }

    @Test
    @DisplayName("loadThemeConfig safely falls back on empty 0-byte file")
    void testEmptyConfigFileHandling() throws IOException {
        Path configPath = tempDir.resolve("empty.json");
        Files.createFile(configPath);

        ThemeManager manager = new ThemeManager(configPath);
        AppTheme loaded = manager.loadThemeConfig();

        assertThat(loaded).isNull();

        manager.init();
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.DARK);
    }

    @Test
    @DisplayName("ThemeManager safely handles null config path without throwing exceptions")
    void testNullConfigPathHandling() {
        ThemeManager manager = new ThemeManager(null);

        assertThat(manager.loadThemeConfig()).isNull();

        assertThatCode(() -> {
            manager.saveThemeConfig();
            manager.applyTheme(AppTheme.LIGHT);
        }).doesNotThrowAnyException();
        assertThat(manager.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
    }

    @Test
    @DisplayName("ThemeManager automatically creates deeply nested configuration directories")
    void testDeeplyNestedConfigDirectories() {
        Path nestedPath = tempDir.resolve("lvl1").resolve("lvl2").resolve("lvl3").resolve("theme.json");
        ThemeManager manager = new ThemeManager(nestedPath);

        manager.applyTheme(AppTheme.LIGHT);
        assertThat(Files.exists(nestedPath)).isTrue();

        ThemeManager restored = new ThemeManager(nestedPath);
        restored.init();
        assertThat(restored.getCurrentTheme()).isEqualTo(AppTheme.LIGHT);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent multi-threaded theme switching, toggling, and listener operations are thread-safe")
    void testConcurrentOperationsThreadSafety() throws InterruptedException {
        Path configPath = tempDir.resolve("concurrent-theme.json");
        ThemeManager manager = new ThemeManager(configPath);

        int threadCount = 4;
        int operationsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger eventCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThemeChangeListener listener = newTheme -> eventCount.incrementAndGet();
                    manager.addThemeChangeListener(listener);

                    for (int j = 0; j < operationsPerThread; j++) {
                        if (j % 3 == 0) {
                            manager.applyTheme(AppTheme.LIGHT);
                        } else if (j % 3 == 1) {
                            manager.applyTheme(AppTheme.DARK);
                        } else {
                            manager.toggleTheme();
                        }

                        manager.getCurrentTheme();
                        manager.isDarkMode();
                    }

                    manager.removeThemeChangeListener(listener);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(manager.getCurrentTheme()).isNotNull();
        assertThat(eventCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Theme application succeeds without exceptions in any environment including headless")
    void testHeadlessEnvironmentSafety() {
        Path configPath = tempDir.resolve("theme-config.json");
        ThemeManager manager = new ThemeManager(configPath);

        assertThatCode(() -> {
            manager.applyTheme(AppTheme.DARK);
            manager.applyTheme(AppTheme.LIGHT);
            manager.applyTheme(AppTheme.SYSTEM);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Singleton instance is accessible, non-null, and maintains reference equality")
    void testSingleton() {
        ThemeManager instance = ThemeManager.getInstance();
        assertThat(instance).isNotNull();
        assertThat(ThemeManager.getInstance()).isSameAs(instance);
    }
}
