package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal value) {

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }

    public Money(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new InvalidOrderException("Money cannot be negative");
        }
        this.value = value.setScale(2, RoundingMode.HALF_UP);
    }

    public Money add(Money other) {
        return new Money(this.value.add(other.value));
    }

    public Money subtract(Money other) {
        if (this.value.compareTo(other.value) < 0) {
            throw new InvalidOrderException("Result cannot be negative");
        }
        return new Money(this.value.subtract(other.value));
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new InvalidOrderException("Multiplier cannot be negative");
        }
        return new Money(this.value.multiply(BigDecimal.valueOf(factor)));
    }

    public boolean isZeroOrNegative() {
        return this.value.signum() <= 0;
    }

    public boolean isNegative() {
        return this.value.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        return this.value.compareTo(other.value) > 0;
    }

    public boolean isLessThan(Money other) {
        return this.value.compareTo(other.value) < 0;
    }

    public BigDecimal toBigDecimal() {
        return value;
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }

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
