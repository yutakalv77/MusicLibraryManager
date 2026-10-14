package com.musiclibrary.ui.panel;

import com.musiclibrary.i18n.AppLocale;
import com.musiclibrary.i18n.I18nManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusBarPanelTest {

    @BeforeEach
    void setUp() {
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
    }

    @Test
    @DisplayName("StatusBarPanel initializes summary label and hidden progress indicator")
    void testInitialization() {
        StatusBarPanel panel = new StatusBarPanel();

        assertThat(panel.getSummaryLabel()).isNotNull();
        assertThat(panel.getStatusLabel()).isNotNull();
        assertThat(panel.getProgressBar()).isNotNull();
        assertThat(panel.getProgressBar().isVisible()).isFalse();
    }

    @Test
    @DisplayName("updateSummary formats duration (days, hours, minutes, seconds) and size (B, KB, MB, GB) accurately")
    void testUpdateSummaryComprehensive() {
        StatusBarPanel panel = new StatusBarPanel();

        // 1. Zero / empty state
        panel.updateSummary(0, 0, 0);
        assertThat(panel.getSummaryLabel().getText()).isEqualTo("0 tracks | Total Time: 0:00 | 0 B");

        // 2. Negative safety
        panel.updateSummary(0, -50, -100);
        assertThat(panel.getSummaryLabel().getText()).isEqualTo("0 tracks | Total Time: 0:00 | 0 B");

        // 3. Minutes & Seconds + Bytes
        panel.updateSummary(1, 150, 500);
        assertThat(panel.getSummaryLabel().getText()).contains("1 tracks");
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 2 min 30 sec");
        assertThat(panel.getSummaryLabel().getText()).contains("500 B");

        // 4. Hours & Minutes + KB
        panel.updateSummary(25, 3661, 51200);
        assertThat(panel.getSummaryLabel().getText()).contains("25 tracks");
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 1 hr 1 min");
        assertThat(panel.getSummaryLabel().getText()).contains("50.0 KB");

        // 5. Days & Hours + MB
        panel.updateSummary(500, 90000, 104857600);
        assertThat(panel.getSummaryLabel().getText()).contains("500 tracks");
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 1 days 1 hr");
        assertThat(panel.getSummaryLabel().getText()).contains("100.0 MB");

        // 6. Large dataset (GB) with comma formatted count
        panel.updateSummary(5000, 180000, 2147483648L);
        assertThat(panel.getSummaryLabel().getText()).contains("5,000 tracks");
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 2 days 2 hr");
        assertThat(panel.getSummaryLabel().getText()).contains("2.0 GB");
    }

    @Test
    @DisplayName("updateSummary formats correctly under Japanese locale to prevent duration confusion")
    void testUpdateSummaryJapaneseLocale() {
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        StatusBarPanel panel = new StatusBarPanel();

        // 0 tracks
        panel.updateSummary(0, 0, 0);
        assertThat(panel.getSummaryLabel().getText()).isEqualTo("0 曲 | 総再生時間: 0分 0秒 | 0 B");

        // Large dataset (e.g. 58,000 tracks, 160 days)
        panel.updateSummary(58000, 13842000L, 129397096448L); // 160 d 5 hr
        assertThat(panel.getSummaryLabel().getText()).contains("58,000 曲");
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 160日 5時間");
        assertThat(panel.getSummaryLabel().getText()).contains("120.5 GB");
    }

    @Test
    @DisplayName("setStatus updates custom text and restores Ready on null")
    void testSetStatus() {
        StatusBarPanel panel = new StatusBarPanel();

        panel.setStatus("Scanning library: 50/100 files...");
        assertThat(panel.getStatusLabel().getText()).isEqualTo("Scanning library: 50/100 files...");

        panel.setStatus(null);
        assertThat(panel.getStatusLabel().getText()).isEqualTo("Ready");
    }

    @Test
    @DisplayName("showProgress and hideProgress configure indeterminate and determinate modes")
    void testProgressVisibilityAndModes() {
        StatusBarPanel panel = new StatusBarPanel();

        // Indeterminate mode
        panel.showProgress(true, 0, 100);
        assertThat(panel.getProgressBar().isVisible()).isTrue();
        assertThat(panel.getProgressBar().isIndeterminate()).isTrue();

        // Determinate mode
        panel.showProgress(false, 45, 100);
        assertThat(panel.getProgressBar().isVisible()).isTrue();
        assertThat(panel.getProgressBar().isIndeterminate()).isFalse();
        assertThat(panel.getProgressBar().getValue()).isEqualTo(45);
        assertThat(panel.getProgressBar().getMaximum()).isEqualTo(100);

        // Hide
        panel.hideProgress();
        assertThat(panel.getProgressBar().isVisible()).isFalse();
    }

    @Test
    @DisplayName("updateLanguage updates summary and status labels in Japanese and English")
    void testUpdateLanguage() {
        StatusBarPanel panel = new StatusBarPanel();
        panel.updateSummary(5, 300, 20000000);

        // Japanese update
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();

        assertThat(panel.getSummaryLabel().getText()).contains("5 曲");
        assertThat(panel.getStatusLabel().getText()).isEqualTo("準備完了");

        // English update
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateLanguage();

        assertThat(panel.getSummaryLabel().getText()).contains("5 tracks");
        assertThat(panel.getStatusLabel().getText()).isEqualTo("Ready");
    }

    @Test
    @DisplayName("Track count formatting accurately applies three-digit commas across various scales")
    void testTrackCountCommaFormattingPrecision() {
        StatusBarPanel panel = new StatusBarPanel();

        // 0
        panel.updateSummary(0, 100, 1000);
        assertThat(panel.getSummaryLabel().getText()).startsWith("0 tracks");

        // 999
        panel.updateSummary(999, 100, 1000);
        assertThat(panel.getSummaryLabel().getText()).startsWith("999 tracks");

        // 1,000
        panel.updateSummary(1000, 100, 1000);
        assertThat(panel.getSummaryLabel().getText()).startsWith("1,000 tracks");

        // 58,000 (Target scale)
        panel.updateSummary(58000, 100, 1000);
        assertThat(panel.getSummaryLabel().getText()).startsWith("58,000 tracks");

        // 1,234,567
        panel.updateSummary(1234567, 100, 1000);
        assertThat(panel.getSummaryLabel().getText()).startsWith("1,234,567 tracks");

        // Japanese locale check
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateSummary(58000, 100, 1000);
        assertThat(panel.getSummaryLabel().getText()).startsWith("58,000 曲");
    }

    @Test
    @DisplayName("Duration formatting covers seconds, minutes, hours, and days tiers in English and Japanese")
    void testDurationTierFormattingBilingual() {
        StatusBarPanel panel = new StatusBarPanel();

        // Tier 1: 0 seconds
        panel.updateSummary(1, 0, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 0:00");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 0分 0秒");

        // Tier 2: Minutes & seconds only (e.g. 45 seconds -> 0 min 45 sec, 90 seconds -> 1 min 30 sec)
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateSummary(1, 45, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 0 min 45 sec");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 0分 45秒");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateSummary(1, 90, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 1 min 30 sec");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 1分 30秒");

        // Tier 3: Hours & minutes (e.g. 3600 sec -> 1 hr 0 min, 8100 sec -> 2 hr 15 min)
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateSummary(1, 3600, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 1 hr 0 min");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 1時間 0分");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateSummary(1, 8100, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 2 hr 15 min");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 2時間 15分");

        // Tier 4: Days & hours (e.g. 86400 sec -> 1 days 0 hr, 13842000 sec -> 160 days 5 hr)
        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateSummary(1, 86400, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 1 days 0 hr");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 1日 0時間");

        I18nManager.getInstance().setLocale(AppLocale.ENGLISH);
        panel.updateSummary(1, 13842000L, 1000);
        assertThat(panel.getSummaryLabel().getText()).contains("Total Time: 160 days 5 hr");
        I18nManager.getInstance().setLocale(AppLocale.JAPANESE);
        panel.updateLanguage();
        assertThat(panel.getSummaryLabel().getText()).contains("総再生時間: 160日 5時間");
    }
}
