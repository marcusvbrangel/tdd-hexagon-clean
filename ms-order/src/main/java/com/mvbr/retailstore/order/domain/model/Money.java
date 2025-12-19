package com.mvbr.retailstore.order.domain.model;

import com.mvbr.retailstore.order.domain.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal value) {

    public static final int SCALE = 2;

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money(BigDecimal value) {
        if (value == null) {
            throw new InvalidOrderException("Money amount cannot be null");
        }
        if (value.signum() < 0) {
            throw new InvalidOrderException("Money amount cannot be negative");
        }
        this.value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal amount() {
        return value;
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return new Money(this.value.add(other.value));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        if (this.value.compareTo(other.value) < 0) {
            throw new InvalidOrderException("Money operation would result in negative value");
        }
        return new Money(this.value.subtract(other.value));
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new InvalidOrderException("Multiplier cannot be negative");
        }
        return new Money(this.value.multiply(BigDecimal.valueOf(factor)));
    }

    public boolean isZero() {
        return this.value.signum() == 0;
    }

    public boolean isPositive() {
        return this.value.signum() > 0;
    }

    public boolean isGreaterThan(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return this.value.compareTo(other.value) > 0;
    }

    public boolean isLessThan(Money other) {
        Objects.requireNonNull(other, "Other money cannot be null");
        return this.value.compareTo(other.value) < 0;
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }

    // Igualdade numérica (10.0 == 10.00)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return value.compareTo(money.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.stripTrailingZeros());
    }
}
