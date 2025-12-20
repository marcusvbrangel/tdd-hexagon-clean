package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTest {

    @Test
    void acceptsValidCpfWithDigitsOnly() {
        assertDoesNotThrow(() -> new Document(DocumentType.CPF, "63977319957"));
    }

    @Test
    void acceptsValidCpfWithFormatting() {
        assertDoesNotThrow(() -> new Document(DocumentType.CPF, "639.773.199-57"));
    }

    @Test
    void rejectsInvalidCpfCheckDigit() {
        assertThrows(DomainException.class, () -> new Document(DocumentType.CPF, "63977319958"));
    }

    @Test
    void rejectsInvalidCpfRepeatedDigits() {
        assertThrows(DomainException.class, () -> new Document(DocumentType.CPF, "11111111111"));
    }

    @Test
    void acceptsValidCnpjWithDigitsOnly() {
        assertDoesNotThrow(() -> new Document(DocumentType.CNPJ, "90383305204893"));
    }

    @Test
    void acceptsValidCnpjWithFormatting() {
        assertDoesNotThrow(() -> new Document(DocumentType.CNPJ, "90.383.305/2048-93"));
    }

    @Test
    void rejectsInvalidCnpjCheckDigit() {
        assertThrows(DomainException.class, () -> new Document(DocumentType.CNPJ, "90383305204894"));
    }

    @Test
    void rejectsInvalidCnpjRepeatedDigits() {
        assertThrows(DomainException.class, () -> new Document(DocumentType.CNPJ, "00000000000000"));
    }
}
