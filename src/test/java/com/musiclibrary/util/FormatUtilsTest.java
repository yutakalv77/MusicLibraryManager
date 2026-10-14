package com.musiclibrary.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormatUtilsTest {

    @Test
    @DisplayName("formatFileSize handles zero and negative byte lengths safely")
    void testZeroAndNegativeBytes() {
        assertThat(FormatUtils.formatFileSize(0)).isEqualTo("0 B");
        assertThat(FormatUtils.formatFileSize(-1)).isEqualTo("0 B");
        assertThat(FormatUtils.formatFileSize(-1024)).isEqualTo("0 B");
        assertThat(FormatUtils.formatFileSize(Long.MIN_VALUE)).isEqualTo("0 B");
    }

    @Test
    @DisplayName("formatFileSize formats raw bytes correctly under 1 KB")
    void testBytes() {
        assertThat(FormatUtils.formatFileSize(1)).isEqualTo("1 B");
        assertThat(FormatUtils.formatFileSize(500)).isEqualTo("500 B");
        assertThat(FormatUtils.formatFileSize(1023)).isEqualTo("1023 B");
    }

    @Test
    @DisplayName("formatFileSize formats kilobytes with one decimal precision")
    void testKilobytes() {
        assertThat(FormatUtils.formatFileSize(1024)).isEqualTo("1.0 KB");
        assertThat(FormatUtils.formatFileSize(1536)).isEqualTo("1.5 KB");
        assertThat(FormatUtils.formatFileSize(51200)).isEqualTo("50.0 KB");
        assertThat(FormatUtils.formatFileSize(1024 * 1024 - 1)).isEqualTo("1024.0 KB");
    }

    @Test
    @DisplayName("formatFileSize formats megabytes with one decimal precision")
    void testMegabytes() {
        assertThat(FormatUtils.formatFileSize(1024L * 1024L)).isEqualTo("1.0 MB");
        assertThat(FormatUtils.formatFileSize(5L * 1024L * 1024L)).isEqualTo("5.0 MB");
        assertThat(FormatUtils.formatFileSize(104857600L)).isEqualTo("100.0 MB");
    }

    @Test
    @DisplayName("formatFileSize formats gigabytes with one decimal precision")
    void testGigabytes() {
        assertThat(FormatUtils.formatFileSize(1024L * 1024L * 1024L)).isEqualTo("1.0 GB");
        assertThat(FormatUtils.formatFileSize(2147483648L)).isEqualTo("2.0 GB");
        assertThat(FormatUtils.formatFileSize(5L * 1024L * 1024L * 1024L)).isEqualTo("5.0 GB");
    }
}
