package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;

// Value Object...
public record Document(DocumentType type, String value) {

    public Document {
        if (type == null) {
            throw new DomainException("Document type is required");
        }
        if (value == null) {
            throw new DomainException("Document is required");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new DomainException("Document is required");
        }
        var digits = digitsOnly(value);
        if (digits.isBlank()) {
            throw new DomainException("Document is required");
        }
        if (type == DocumentType.CPF) {
            if (!isValidCpf(digits)) {
                throw new DomainException("Invalid CPF");
            }
        } else if (type == DocumentType.CNPJ) {
            if (!isValidCnpj(digits)) {
                throw new DomainException("Invalid CNPJ");
            }
        } else {
            throw new DomainException("Invalid Document type");
        }
        value = digits;
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private static boolean isValidCpf(String digits) {
        if (digits.length() != 11 || hasAllSameDigits(digits)) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (digits.charAt(i) - '0') * (10 - i);
        }
        int check1 = (sum * 10) % 11;
        if (check1 == 10) {
            check1 = 0;
        }
        if (check1 != digits.charAt(9) - '0') {
            return false;
        }
        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (digits.charAt(i) - '0') * (11 - i);
        }
        int check2 = (sum * 10) % 11;
        if (check2 == 10) {
            check2 = 0;
        }
        return check2 == digits.charAt(10) - '0';
    }

    private static boolean isValidCnpj(String digits) {
        if (digits.length() != 14 || hasAllSameDigits(digits)) {
            return false;
        }
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (digits.charAt(i) - '0') * weights1[i];
        }
        int remainder = sum % 11;
        int check1 = remainder < 2 ? 0 : 11 - remainder;
        if (check1 != digits.charAt(12) - '0') {
            return false;
        }
        sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += (digits.charAt(i) - '0') * weights2[i];
        }
        remainder = sum % 11;
        int check2 = remainder < 2 ? 0 : 11 - remainder;
        return check2 == digits.charAt(13) - '0';
    }

    private static boolean hasAllSameDigits(String digits) {
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != digits.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    public String normalized() {
        return value.trim().toLowerCase();
    }

    @Override
    public String toString() {
        return value;
    }

}
