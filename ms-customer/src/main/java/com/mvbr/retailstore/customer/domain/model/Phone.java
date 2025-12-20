package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;

import java.util.regex.Pattern;

// Value Object...
public record Phone(String value) {

    private static final Pattern BR_MOBILE_PATTERN =
            Pattern.compile("^(?:55)?\\d{2}9\\d{8}$");

    public Phone {
        if (value == null || value.isBlank()) {
            throw new DomainException("Phone is required");
        }
        var digits = digitsOnly(value);
        if (digits.isBlank()) {
            throw new DomainException("Phone is required");
        }
        if (!BR_MOBILE_PATTERN.matcher(digits).matches()) {
            throw new DomainException("Invalid Phone");
        }
        value = normalizeDigits(digits);
    }

    public String normalized() {
        return value.trim().toLowerCase();
    }

    @Override
    public String toString() {
        return value;
    }

    private static String digitsOnly(String value) {
        return value.replaceAll("\\D", "");
    }

    private static String normalizeDigits(String digits) {
        if (digits.length() == 13 && digits.startsWith("55")) {
            return digits.substring(2);
        }
        return digits;
    }

}
