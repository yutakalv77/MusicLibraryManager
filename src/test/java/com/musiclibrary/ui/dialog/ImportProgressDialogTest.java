package com.musiclibrary.ui.dialog;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ImportProgressDialogTest {

    @BeforeEach
    void setUp() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("ImportProgressDialog initializes non-modal with progress bar and buttons (null owner)")
    void testInitializationNullOwner() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            assertThat(dialog.isModal()).isFalse();
            assertThat(dialog.getProgressBar()).isNotNull();
            assertThat(dialog.getProgressBar().isIndeterminate()).isTrue();
            assertThat(dialog.getBackgroundButton()).isNotNull();
            assertThat(dialog.getCancelButton()).isNotNull();
            assertThat(dialog.getTitle()).isEqualTo("Import Music");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("ImportProgressDialog initializes with Frame owner correctly")
    void testInitializationWithOwner() {
        Frame owner = new Frame("Owner Frame");
        try {
            ImportProgressDialog dialog = new ImportProgressDialog(owner);
            try {
                assertThat(dialog.getOwner()).isSameAs(owner);
                assertThat(dialog.isModal()).isFalse();
            } finally {
                dialog.dispose();
            }
        } finally {
            owner.dispose();
        }
    }

    @Test
    @DisplayName("updateProgress reflects current count and updates filename")
    void testUpdateProgress() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            dialog.updateProgress(25, 100, "favorite_song.mp3");

            assertThat(dialog.getProgressBar().isIndeterminate()).isFalse();
            assertThat(dialog.getProgressBar().getValue()).isEqualTo(25);
            assertThat(dialog.getProgressBar().getMaximum()).isEqualTo(100);
            assertThat(dialog.getFileNameLabel().getText()).isEqualTo("favorite_song.mp3");
            assertThat(dialog.getStatusMessageLabel().getText()).contains("25 / 100");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("updateProgress truncates file names longer than 50 characters with ellipsis")
    void testFileNameTruncation() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            String longFileName = "C_Music_Super_Long_Directory_Path_With_Many_Subfolders_song_title_01_remastered_final_version.mp3";
            assertThat(longFileName.length()).isGreaterThan(50);

            dialog.updateProgress(1, 10, longFileName);

            String labelText = dialog.getFileNameLabel().getText();
            assertThat(labelText).hasSize(50);
            assertThat(labelText).startsWith("...");
            assertThat(labelText).endsWith("_final_version.mp3");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("updateProgress handles null and short file names safely")
    void testNullAndShortFileNames() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            dialog.updateProgress(1, 5, null);
            assertThat(dialog.getFileNameLabel().getText()).isEqualTo("");

            dialog.updateProgress(2, 5, "short.mp3");
            assertThat(dialog.getFileNameLabel().getText()).isEqualTo("short.mp3");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("updateProgress maintains indeterminate mode when total <= 0")
    void testZeroOrNegativeTotalProgress() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            dialog.updateProgress(0, 0, "discovering_files.mp3");

            assertThat(dialog.getProgressBar().isIndeterminate()).isTrue();
            assertThat(dialog.getStatusMessageLabel().getText()).isEqualTo("Scanning audio files...");
            assertThat(dialog.getStatusMessageLabel().getText()).doesNotContain("0 / 0");

            dialog.updateProgress(0, -1, "discovering_files.mp3");
            assertThat(dialog.getProgressBar().isIndeterminate()).isTrue();
            assertThat(dialog.getStatusMessageLabel().getText()).isEqualTo("Scanning audio files...");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Action callbacks are invoked when buttons are clicked")
    void testCallbacks() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            AtomicBoolean backgroundCalled = new AtomicBoolean(false);
            AtomicBoolean cancelCalled = new AtomicBoolean(false);

            dialog.setOnBackground(() -> backgroundCalled.set(true));
            dialog.setOnCancel(() -> cancelCalled.set(true));

            dialog.setVisible(true);
            assertThat(dialog.isVisible()).isTrue();

            dialog.getBackgroundButton().doClick();
            assertThat(backgroundCalled.get()).isTrue();
            assertThat(dialog.isVisible()).isFalse();

            dialog.getCancelButton().doClick();
            assertThat(cancelCalled.get()).isTrue();
            assertThat(dialog.getCancelButton().isEnabled()).isFalse();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("updateLanguage updates dialog title, status, and button texts in Japanese and English")
    void testUpdateLanguageBilingual() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            // Update to Japanese
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            dialog.updateLanguage();

            assertThat(dialog.getTitle()).isEqualTo("楽曲の読み込み");
            assertThat(dialog.getBackgroundButton().getText()).isEqualTo("バックグラウンドで実行");
            assertThat(dialog.getCancelButton().getText()).isEqualTo("キャンセル");
            assertThat(dialog.getStatusMessageLabel().getText()).contains("音楽ファイルをスキャン中...");

            // Switch back to English
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            dialog.updateLanguage();

            assertThat(dialog.getTitle()).isEqualTo("Import Music");
            assertThat(dialog.getBackgroundButton().getText()).isEqualTo("Run in Background");
            assertThat(dialog.getCancelButton().getText()).isEqualTo("Cancel");
            assertThat(dialog.getStatusMessageLabel().getText()).contains("Scanning audio files...");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("updateProgress displays calculating initially, then speed and remaining ETA accurately")
    void testSpeedAndEtaMetricsCalculation() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            // 1. Initial phase (< 1s or < 5 tracks) displays calculating
            dialog.updateProgress(2, 500, "track_2.mp3");
            assertThat(dialog.getMetricsLabel().getText()).isEqualTo("Calculating speed & remaining time...");

            // 2. Simulated 10 seconds elapsed with 100 tracks processed (Speed = 10.0 tracks/sec, Remaining 400 tracks = 40 sec)
            long simulatedStart = System.currentTimeMillis() - 10_000L;
            dialog.setStartTimeMs(simulatedStart);
            dialog.updateProgress(100, 500, "track_100.mp3");

            assertThat(dialog.getMetricsLabel().getText()).contains("Speed: 10.0 tracks/sec");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining: ~40 sec");

            // 3. Simulated longer remaining time (e.g. 5 minutes 30 seconds)
            // 10s elapsed, 20 tracks processed -> speed = 2.0 tracks/sec. Remaining = 660 tracks -> 330 sec = 5 min 30 sec
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(20, 680, "track_20.mp3");
            assertThat(dialog.getMetricsLabel().getText()).contains("Speed: 2.0 tracks/sec");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining: ~5 min 30 sec");

            // 4. Completed state clears metrics
            dialog.updateProgress(680, 680, "track_final.mp3");
            assertThat(dialog.getMetricsLabel().getText().trim()).isEmpty();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Speed and ETA metrics are accurately localized under Japanese locale")
    void testSpeedAndEtaJapanese() {
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            // Initial calculating state
            dialog.updateProgress(3, 1000, "track_3.mp3");
            assertThat(dialog.getMetricsLabel().getText()).isEqualTo("処理速度・残り時間を計算中...");

            // 10 seconds elapsed, 100 tracks processed -> speed = 10.0 曲/秒. Remaining 100 tracks = 10 秒
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(100, 200, "track_100.mp3");

            assertThat(dialog.getMetricsLabel().getText()).contains("速度: 10.0 曲/秒");
            assertThat(dialog.getMetricsLabel().getText()).contains("残り: 約 10 秒");

            // Longer remaining (1分 40秒)
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(100, 1100, "track_100.mp3"); // remaining 1000 tracks -> 100 sec = 1分 40秒
            assertThat(dialog.getMetricsLabel().getText()).contains("速度: 10.0 曲/秒");
            assertThat(dialog.getMetricsLabel().getText()).contains("残り: 約 1分 40秒");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Speed and ETA handle edge boundary values: just started, 1 track left, and completed")
    void testSpeedAndEtaBoundaryValuesAndCompletion() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            // 1. Elapsed time >= 1s but processed < 5 -> still calculating
            dialog.setStartTimeMs(System.currentTimeMillis() - 2_000L);
            dialog.updateProgress(4, 100, "track_4.mp3");
            assertThat(dialog.getMetricsLabel().getText()).isEqualTo("Calculating speed & remaining time...");

            // 2. Processed >= 5 but elapsed < 1000ms -> still calculating
            dialog.setStartTimeMs(System.currentTimeMillis() - 500L);
            dialog.updateProgress(5, 100, "track_5.mp3");
            assertThat(dialog.getMetricsLabel().getText()).isEqualTo("Calculating speed & remaining time...");

            // 3. Exactly 1 track remaining -> formatEta returns seconds
            // 10s elapsed, 99 tracks processed -> speed = 9.9 tracks/sec. 1 track remaining -> round(1 / 9.9) = 0 -> clamped to 1 sec
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(99, 100, "track_99.mp3");
            assertThat(dialog.getMetricsLabel().getText()).contains("Speed: 9.9 tracks/sec");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining: ~1 sec");

            // Japanese boundary for 1 track remaining
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            dialog.updateLanguage();
            assertThat(dialog.getMetricsLabel().getText()).contains("速度: 9.9 曲/秒");
            assertThat(dialog.getMetricsLabel().getText()).contains("残り: 約 1 秒");

            // 4. Fully completed (100 / 100) -> metrics cleared
            dialog.updateProgress(100, 100, "track_100.mp3");
            assertThat(dialog.getMetricsLabel().getText().trim()).isEmpty();
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Large-scale 58,000 tracks simulation verifies minutes, hours, and days tiers for ETA")
    void testLargeScaleImportSimulationSpeedAndEta() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            // Simulation 1: 58,000 scale at 500 tracks/sec
            // 20s elapsed, 10,000 processed -> speed = 500.0 tracks/sec. Remaining 48,000 tracks -> 96 sec = 1 min 36 sec
            dialog.setStartTimeMs(System.currentTimeMillis() - 20_000L);
            dialog.updateProgress(10000, 58000, "bulk_track_10000.mp3");

            assertThat(dialog.getMetricsLabel().getText()).contains("Speed: 500.0 tracks/sec");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining: ~1 min 36 sec");

            // Simulation 2: Hours tier (e.g. 5,400 sec = 1 hr 30 min)
            // 10s elapsed, 10 tracks processed -> speed = 1.0 track/sec. Remaining 5,400 tracks -> 5400 sec = 1 hr 30 min
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(10, 5410, "slow_track_10.mp3");
            assertThat(dialog.getMetricsLabel().getText()).contains("Speed: 1.0 tracks/sec");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining: ~1 hr 30 min");

            // Simulation 3: Days tier (e.g. 183,600 sec = 2 days 3 hr)
            // 10s elapsed, 10 tracks processed -> speed = 1.0 track/sec. Remaining 183,600 tracks
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(10, 183610, "huge_track_10.mp3");
            assertThat(dialog.getMetricsLabel().getText()).contains("Speed: 1.0 tracks/sec");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining: ~2 days 3 hr");

            // Verify Japanese translation for tiers
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            dialog.updateLanguage();
            assertThat(dialog.getMetricsLabel().getText()).contains("速度: 1.0 曲/秒");
            assertThat(dialog.getMetricsLabel().getText()).contains("残り: 約 2日 3時間");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("Dynamic language switching seamlessly updates title, buttons, and metrics labels")
    void testDynamicLanguageSwitchingUpdatesMetrics() {
        ImportProgressDialog dialog = new ImportProgressDialog(null);

        try {
            dialog.setStartTimeMs(System.currentTimeMillis() - 10_000L);
            dialog.updateProgress(50, 200, "sample.mp3");

            // Initially English
            assertThat(dialog.getTitle()).isEqualTo("Import Music");
            assertThat(dialog.getMetricsLabel().getText()).contains("Speed:");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining:");

            // Dynamic switch to Japanese
            I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
            dialog.updateLanguage();

            assertThat(dialog.getTitle()).isEqualTo("楽曲の読み込み");
            assertThat(dialog.getMetricsLabel().getText()).contains("速度:");
            assertThat(dialog.getMetricsLabel().getText()).contains("残り:");

            // Dynamic switch back to English
            I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
            dialog.updateLanguage();

            assertThat(dialog.getTitle()).isEqualTo("Import Music");
            assertThat(dialog.getMetricsLabel().getText()).contains("Speed:");
            assertThat(dialog.getMetricsLabel().getText()).contains("Remaining:");
        } finally {
            dialog.dispose();
        }
    }
}
