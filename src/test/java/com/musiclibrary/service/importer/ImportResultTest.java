package com.musiclibrary.service.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportResultTest {

    @Test
    @DisplayName("ImportResult record stores counts and computes totals correctly")
    void testRecordCountsAndTotals() {
        ImportResult result = new ImportResult(10, 5, 3);

        assertThat(result.addedCount()).isEqualTo(10);
        assertThat(result.updatedCount()).isEqualTo(5);
        assertThat(result.skippedCount()).isEqualTo(3);

        // getTotalImportedCount = added + updated
        assertThat(result.getTotalImportedCount()).isEqualTo(15);

        // getTotalProcessedCount = added + updated + skipped
        assertThat(result.getTotalProcessedCount()).isEqualTo(18);
    }

    @Test
    @DisplayName("ImportResult.EMPTY constant has all zero counts")
    void testEmptyConstant() {
        ImportResult empty = ImportResult.EMPTY;

        assertThat(empty.addedCount()).isEqualTo(0);
        assertThat(empty.updatedCount()).isEqualTo(0);
        assertThat(empty.skippedCount()).isEqualTo(0);
        assertThat(empty.getTotalImportedCount()).isEqualTo(0);
        assertThat(empty.getTotalProcessedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("ImportResult supports equality and toString correctly")
    void testEqualityAndToString() {
        ImportResult r1 = new ImportResult(3, 2, 1);
        ImportResult r2 = new ImportResult(3, 2, 1);
        ImportResult r3 = new ImportResult(0, 0, 0);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
        assertThat(r1.toString()).contains("addedCount=3", "updatedCount=2", "skippedCount=1");
    }

    @Test
    @DisplayName("DuplicatePolicy enum contains expected policy constants")
    void testDuplicatePolicyValues() {
        assertThat(DuplicatePolicy.values()).containsExactly(
                DuplicatePolicy.SKIP_SAME_CONTENT,
                DuplicatePolicy.ALLOW_SAME_CONTENT
        );
        assertThat(DuplicatePolicy.valueOf("SKIP_SAME_CONTENT")).isEqualTo(DuplicatePolicy.SKIP_SAME_CONTENT);
        assertThat(DuplicatePolicy.valueOf("ALLOW_SAME_CONTENT")).isEqualTo(DuplicatePolicy.ALLOW_SAME_CONTENT);
    }
}
