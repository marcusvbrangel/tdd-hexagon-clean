package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhoneTest {

    @Test
    void acceptsValidBrMobileWithDigitsOnly() {
        assertDoesNotThrow(() -> new Phone("11987654321"));
    }

    @Test
    void acceptsValidBrMobileWithFormatting() {
        assertDoesNotThrow(() -> new Phone("(11) 9 8765-4321"));
    }

    @Test
    void acceptsValidBrMobileWithCountryCode() {
        assertDoesNotThrow(() -> new Phone("+55 (11) 9 8765-4321"));
    }

    @Test
    void rejectsInvalidMissingNine() {
        assertThrows(DomainException.class, () -> new Phone("(11) 8 7654-3210"));
    }

    @Test
    void rejectsInvalidLength() {
        assertThrows(DomainException.class, () -> new Phone("1198765432"));
    }

    @Test
    void rejectsBlank() {
        assertThrows(DomainException.class, () -> new Phone("   "));
    }
}
