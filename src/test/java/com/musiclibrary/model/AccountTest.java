package com.musiclibrary.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    @DisplayName("Default constructor initializes default account")
    void testDefaultConstructor() {
        Account account = new Account();
        assertThat(account.getId()).isEqualTo(Account.DEFAULT_ACCOUNT_ID);
        assertThat(account.getName()).isEqualTo(Account.DEFAULT_ACCOUNT_NAME);
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Constructor with id and name")
    void testConstructorWithIdAndName() {
        Account account = new Account("u-1", "Alice");
        assertThat(account.getId()).isEqualTo("u-1");
        assertThat(account.getName()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Validation prevents null or blank id and name")
    void testValidation() {
        assertThatThrownBy(() -> new Account(null, "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Account("  ", "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Account("u-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Account("u-1", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder and toBuilder work correctly")
    void testBuilder() {
        Instant now = Instant.now();
        Account account = Account.builder()
                .id("acc-123")
                .name("Bob")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(account.getId()).isEqualTo("acc-123");
        assertThat(account.getName()).isEqualTo("Bob");
        assertThat(account.getCreatedAt()).isEqualTo(now);
        assertThat(account.getUpdatedAt()).isEqualTo(now);

        Account cloned = account.toBuilder()
                .name("Bob Updated")
                .build();

        assertThat(cloned.getId()).isEqualTo("acc-123");
        assertThat(cloned.getName()).isEqualTo("Bob Updated");
        assertThat(cloned).isEqualTo(account); // equals is id-based
    }

    @Test
    @DisplayName("equals and hashCode contracts are satisfied based strictly on id")
    void testEqualsAndHashCodeContract() {
        Instant now = Instant.now();
        Account acc1 = new Account("acc-same", "Alice", now, now);
        Account acc2 = new Account("acc-same", "Different Name", now.plusSeconds(10), now.plusSeconds(20));
        Account acc3 = new Account("acc-different", "Alice", now, now);

        // Reflexivity
        assertThat(acc1).isEqualTo(acc1);

        // Symmetry & consistency
        assertThat(acc1).isEqualTo(acc2);
        assertThat(acc2).isEqualTo(acc1);
        assertThat(acc1.hashCode()).isEqualTo(acc2.hashCode());

        // Inequality
        assertThat(acc1).isNotEqualTo(acc3);
        assertThat(acc1).isNotEqualTo(null);
        assertThat(acc1).isNotEqualTo("some string");
    }

    @Test
    @DisplayName("Setters validate null and blank arguments")
    void testSettersValidation() {
        Account account = new Account("id-1", "Original Name");

        // Valid setters
        account.setId("id-updated");
        assertThat(account.getId()).isEqualTo("id-updated");
        account.setName("Updated Name");
        assertThat(account.getName()).isEqualTo("Updated Name");

        Instant custom = Instant.parse("2026-10-15T00:00:00Z");
        account.setCreatedAt(custom);
        assertThat(account.getCreatedAt()).isEqualTo(custom);
        account.setUpdatedAt(custom);
        assertThat(account.getUpdatedAt()).isEqualTo(custom);

        // Invalid setters
        assertThatThrownBy(() -> account.setId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.setId("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> account.setName(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.setName("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> account.setCreatedAt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.setUpdatedAt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Builder gracefully falls back for null or blank fields")
    void testBuilderFallbacks() {
        Account account = Account.builder()
                .id(null)
                .name("   ")
                .createdAt(null)
                .updatedAt(null)
                .build();

        assertThat(account.getId()).isNotBlank();
        assertThat(account.getName()).isEqualTo(Account.DEFAULT_ACCOUNT_NAME);
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("toString includes id, name, createdAt, and updatedAt")
    void testToString() {
        Account account = new Account("u-xyz", "Charlie");
        assertThat(account.toString())
                .contains("id='u-xyz'")
                .contains("name='Charlie'")
                .contains("createdAt=")
                .contains("updatedAt=");
    }
}
