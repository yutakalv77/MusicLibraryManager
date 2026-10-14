package com.musiclibrary.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class I18nManagerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("get returns localized string for English and Japanese")
    void testGetLocalizedMessage() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        // English
        manager.setLocale(AppLocale.ENGLISH);
        assertThat(manager.get("menu.file")).isEqualTo("File");
        assertThat(manager.get("control.play")).isEqualTo("Play");
        assertThat(manager.get("table.column.title")).isEqualTo("Title");

        // Japanese
        manager.setLocale(AppLocale.JAPANESE);
        assertThat(manager.get("menu.file")).isEqualTo("ファイル");
        assertThat(manager.get("control.play")).isEqualTo("再生");
        assertThat(manager.get("table.column.title")).isEqualTo("タイトル");
    }

    @Test
    @DisplayName("format applies MessageFormat arguments with various parameter counts, types, and edge cases")
    void testFormat() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        manager.setLocale(AppLocale.ENGLISH);
        // Complete arguments
        assertThat(manager.format("status.summary", 42, "3 hr 15 min", "1.2 GB"))
                .isEqualTo("42 tracks | Total Time: 3 hr 15 min | 1.2 GB");

        // Single argument pattern
        assertThat(manager.format("toast.tracks_added", 15))
                .isEqualTo("15 tracks added");

        // Null argument array
        assertThat(manager.format("menu.file", (Object[]) null))
                .isEqualTo("File");

        // Empty argument array
        assertThat(manager.format("menu.file"))
                .isEqualTo("File");

        // Null argument within arguments
        assertThat(manager.format("status.scanning", (Object) null))
                .isEqualTo("Scanning folder: null...");

        // Insufficient arguments (only {0} supplied when pattern needs {0}, {1}, {2})
        assertThat(manager.format("status.summary", 5))
                .isEqualTo("5 tracks | Total Time: {1} | {2}");

        // Format with missing key
        assertThat(manager.format("non.existent.key", "param"))
                .isEqualTo("!non.existent.key!");

        // Japanese format
        manager.setLocale(AppLocale.JAPANESE);
        assertThat(manager.format("status.summary", 42, "3時間15分", "1.2 GB"))
                .isEqualTo("42 曲 | 総再生時間: 3時間15分 | 1.2 GB");
        assertThat(manager.format("toast.tracks_added", 10))
                .isEqualTo("10 曲を追加しました");
    }

    @Test
    @DisplayName("get with missing key returns safe fallback !key! and null returns empty string")
    void testMissingKey() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        assertThat(manager.get("missing.key")).isEqualTo("!missing.key!");
        assertThat(manager.get(null)).isEqualTo("");
    }

    @Test
    @DisplayName("Listeners are notified on locale changes and duplicate registration is prevented")
    void testLanguageChangeListeners() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        List<AppLocale> received = new ArrayList<>();
        LanguageChangeListener listener = received::add;

        // Duplicate registration check
        manager.addLanguageChangeListener(listener);
        manager.addLanguageChangeListener(listener);

        manager.setLocale(AppLocale.ENGLISH);
        manager.setLocale(AppLocale.JAPANESE);

        assertThat(received).containsExactly(AppLocale.ENGLISH, AppLocale.JAPANESE);

        // Remove listener
        manager.removeLanguageChangeListener(listener);
        manager.setLocale(AppLocale.ENGLISH);
        assertThat(received).hasSize(2); // no new events
    }

    @Test
    @DisplayName("Exception in one listener does not prevent subsequent listeners from executing")
    void testListenerExceptionIsolation() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        AtomicBoolean failingCalled = new AtomicBoolean(false);
        AtomicBoolean healthyCalled = new AtomicBoolean(false);

        LanguageChangeListener failingListener = newLocale -> {
            failingCalled.set(true);
            throw new RuntimeException("Simulated listener exception");
        };

        LanguageChangeListener healthyListener = newLocale -> healthyCalled.set(true);

        manager.addLanguageChangeListener(failingListener);
        manager.addLanguageChangeListener(healthyListener);

        assertThatCode(() -> manager.setLocale(AppLocale.JAPANESE))
                .doesNotThrowAnyException();

        assertThat(failingCalled).isTrue();
        assertThat(healthyCalled).isTrue();
    }

    @Test
    @DisplayName("null listener registration and removal is safe and does not throw exception")
    void testNullListenerSafety() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        assertThatCode(() -> {
            manager.addLanguageChangeListener(null);
            manager.removeLanguageChangeListener(null);
            manager.setLocale(AppLocale.JAPANESE);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("init with explicit locale applies locale, and null falls back to SYSTEM")
    void testInitWithExplicitLocale() {
        Path configPath = tempDir.resolve("i18n-config.json");
        I18nManager manager = new I18nManager(configPath);

        manager.init(AppLocale.JAPANESE);
        assertThat(manager.getCurrentLocale()).isEqualTo(AppLocale.JAPANESE);

        manager.init(null);
        assertThat(manager.getCurrentLocale()).isEqualTo(AppLocale.SYSTEM);
    }

    @Test
    @DisplayName("Locale configuration is persisted and restored across instances")
    void testLocalePersistence() {
        Path configPath = tempDir.resolve("sub").resolve("i18n-config.json");
        I18nManager manager1 = new I18nManager(configPath);

        manager1.setLocale(AppLocale.JAPANESE);
        assertThat(Files.exists(configPath)).isTrue();

        // Restore in a new instance
        I18nManager manager2 = new I18nManager(configPath);
        manager2.init();

        assertThat(manager2.getCurrentLocale()).isEqualTo(AppLocale.JAPANESE);
        assertThat(manager2.get("menu.file")).isEqualTo("ファイル");

        // Save and restore ENGLISH
        manager2.setLocale(AppLocale.ENGLISH);
        I18nManager manager3 = new I18nManager(configPath);
        manager3.init();

        assertThat(manager3.getCurrentLocale()).isEqualTo(AppLocale.ENGLISH);
        assertThat(manager3.get("menu.file")).isEqualTo("File");
    }

    @Test
    @DisplayName("loadLocaleConfig safely handles corrupted JSON")
    void testCorruptedConfigHandling() throws IOException {
        Path configPath = tempDir.resolve("corrupted.json");
        Files.writeString(configPath, "{ invalid json ]");

        I18nManager manager = new I18nManager(configPath);
        AppLocale loaded = manager.loadLocaleConfig();

        assertThat(loaded).isNull();

        manager.init();
        assertThat(manager.getCurrentLocale()).isEqualTo(AppLocale.SYSTEM);
    }

    @Test
    @DisplayName("loadLocaleConfig safely falls back on unknown locale strings")
    void testUnknownLocaleConfigHandling() throws IOException {
        Path configPath = tempDir.resolve("unknown.json");
        Files.writeString(configPath, "{\"locale\": \"UNKNOWN_LANG\"}");

        I18nManager manager = new I18nManager(configPath);
        AppLocale loaded = manager.loadLocaleConfig();

        // AppLocale.fromCode("UNKNOWN_LANG") maps to SYSTEM
        assertThat(loaded).isEqualTo(AppLocale.SYSTEM);

        manager.init();
        assertThat(manager.getCurrentLocale()).isEqualTo(AppLocale.SYSTEM);
    }

    @Test
    @DisplayName("loadLocaleConfig safely falls back on empty 0-byte file")
    void testEmptyConfigFileHandling() throws IOException {
        Path configPath = tempDir.resolve("empty.json");
        Files.createFile(configPath);

        I18nManager manager = new I18nManager(configPath);
        AppLocale loaded = manager.loadLocaleConfig();

        assertThat(loaded).isNull();

        manager.init();
        assertThat(manager.getCurrentLocale()).isEqualTo(AppLocale.SYSTEM);
    }

    @Test
    @DisplayName("I18nManager safely handles null config path without throwing exceptions")
    void testNullConfigPathHandling() {
        I18nManager manager = new I18nManager(null);

        assertThat(manager.loadLocaleConfig()).isNull();

        assertThatCode(() -> {
            manager.saveLocaleConfig();
            manager.setLocale(AppLocale.ENGLISH);
        }).doesNotThrowAnyException();
        assertThat(manager.getCurrentLocale()).isEqualTo(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("I18nManager automatically creates deeply nested configuration directories")
    void testDeeplyNestedConfigDirectories() {
        Path nestedPath = tempDir.resolve("lvl1").resolve("lvl2").resolve("lvl3").resolve("i18n.json");
        I18nManager manager = new I18nManager(nestedPath);

        manager.setLocale(AppLocale.JAPANESE);
        assertThat(Files.exists(nestedPath)).isTrue();

        I18nManager restored = new I18nManager(nestedPath);
        restored.init();
        assertThat(restored.getCurrentLocale()).isEqualTo(AppLocale.JAPANESE);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Concurrent multi-threaded setLocale, get, format, and listener operations are thread-safe")
    void testConcurrentOperationsThreadSafety() throws InterruptedException {
        Path configPath = tempDir.resolve("concurrent-i18n.json");
        I18nManager manager = new I18nManager(configPath);

        int threadCount = 8;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger eventCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    LanguageChangeListener listener = newLocale -> eventCount.incrementAndGet();
                    manager.addLanguageChangeListener(listener);

                    for (int j = 0; j < operationsPerThread; j++) {
                        if (j % 2 == 0) {
                            manager.setLocale(AppLocale.JAPANESE);
                        } else {
                            manager.setLocale(AppLocale.ENGLISH);
                        }

                        manager.get("menu.file");
                        manager.format("status.summary", 10, "1m", "5MB");
                        manager.getCurrentLocale();
                        manager.getLocale();
                    }

                    manager.removeLanguageChangeListener(listener);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(manager.getCurrentLocale()).isNotNull();
        assertThat(eventCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("English, Japanese, and Default message properties keys are completely synchronized and non-empty")
    void testPropertiesKeysSynchronizedAndNonEmpty() throws IOException {
        Properties enProps = new Properties();
        try (var in = getClass().getResourceAsStream("/messages_en.properties")) {
            assertThat(in).isNotNull();
            enProps.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Properties jaProps = new Properties();
        try (var in = getClass().getResourceAsStream("/messages_ja.properties")) {
            assertThat(in).isNotNull();
            jaProps.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Properties defaultProps = new Properties();
        try (var in = getClass().getResourceAsStream("/messages.properties")) {
            assertThat(in).isNotNull();
            defaultProps.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Set<String> enKeys = enProps.stringPropertyNames();
        Set<String> jaKeys = jaProps.stringPropertyNames();
        Set<String> defaultKeys = defaultProps.stringPropertyNames();

        // Exact key synchronization
        assertThat(jaKeys).as("Japanese keys match English keys").containsExactlyInAnyOrderElementsOf(enKeys);
        assertThat(defaultKeys).as("Default keys match English keys").containsExactlyInAnyOrderElementsOf(enKeys);

        // Verify non-empty values
        for (String key : enKeys) {
            String enVal = enProps.getProperty(key);
            String jaVal = jaProps.getProperty(key);
            String defVal = defaultProps.getProperty(key);

            assertThat(enVal).as("English value for key: " + key).isNotBlank();
            assertThat(jaVal).as("Japanese value for key: " + key).isNotBlank();
            assertThat(defVal).as("Default value for key: " + key).isNotBlank();
        }
    }

    @Test
    @DisplayName("Japanese messages are encoded in UTF-8 without character corruption or question marks")
    void testJapaneseUtf8IntegrityNoGarbledCharacters() throws IOException {
        Properties jaProps = new Properties();
        try (var in = getClass().getResourceAsStream("/messages_ja.properties")) {
            assertThat(in).isNotNull();
            jaProps.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        // Verify key Japanese texts
        assertThat(jaProps.getProperty("menu.file")).isEqualTo("ファイル");
        assertThat(jaProps.getProperty("menu.view.theme.system")).isEqualTo("システム設定に合わせる");
        assertThat(jaProps.getProperty("control.no_track")).isEqualTo("曲が選択されていません");
        assertThat(jaProps.getProperty("sidebar.recently_added")).isEqualTo("最近追加した項目");
        assertThat(jaProps.getProperty("dialog.import.title")).isEqualTo("楽曲の読み込み");
        assertThat(jaProps.getProperty("dialog.cancel")).isEqualTo("キャンセル");
        assertThat(jaProps.getProperty("toast.import_detail")).isEqualTo("{0} 曲追加、{1} 曲更新、{2} 曲スキップ");
        assertThat(jaProps.getProperty("table.menu.play")).isEqualTo("再生");
        assertThat(jaProps.getProperty("table.menu.play_next")).isEqualTo("次に再生");
        assertThat(jaProps.getProperty("table.menu.get_info")).isEqualTo("情報を表示");
        assertThat(jaProps.getProperty("table.menu.delete")).isEqualTo("ライブラリから削除");

        // Verify no replacement question marks in values
        for (String key : jaProps.stringPropertyNames()) {
            String value = jaProps.getProperty(key);
            assertThat(value).as("Japanese property value for: " + key).doesNotContain("???");
        }
    }

    @Test
    @DisplayName("Singleton instance is accessible and not null")
    void testSingleton() {
        I18nManager instance = I18nManager.getInstance();
        assertThat(instance).isNotNull();
        assertThat(I18nManager.getInstance()).isSameAs(instance);
    }
}
