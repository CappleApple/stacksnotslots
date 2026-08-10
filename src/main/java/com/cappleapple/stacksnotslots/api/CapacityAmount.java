package com.cappleapple.stacksnotslots.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact rational capacity value used for fractional per-item costs without floating-point drift. */
public final class CapacityAmount implements Comparable<CapacityAmount> {
    public static final CapacityAmount ZERO = new CapacityAmount(BigInteger.ZERO, BigInteger.ONE, false);

    private final BigInteger numerator;
    private final BigInteger denominator;

    private CapacityAmount(BigInteger numerator, BigInteger denominator, boolean normalize) {
        if (denominator.signum() <= 0) throw new IllegalArgumentException("Capacity denominator must be positive");
        if (numerator.signum() == 0) {
            this.numerator = BigInteger.ZERO;
            this.denominator = BigInteger.ONE;
            return;
        }
        if (normalize) {
            BigInteger divisor = numerator.abs().gcd(denominator);
            numerator = numerator.divide(divisor);
            denominator = denominator.divide(divisor);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static CapacityAmount of(long wholeUnits) {
        return wholeUnits == 0 ? ZERO : new CapacityAmount(BigInteger.valueOf(wholeUnits), BigInteger.ONE, false);
    }

    public static CapacityAmount fraction(long numerator, long denominator) {
        return fraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    public static CapacityAmount fraction(BigInteger numerator, BigInteger denominator) {
        Objects.requireNonNull(numerator);
        Objects.requireNonNull(denominator);
        return numerator.signum() == 0 ? ZERO : new CapacityAmount(numerator, denominator, true);
    }

    public BigInteger numerator() { return numerator; }
    public BigInteger denominator() { return denominator; }
    public boolean isZero() { return numerator.signum() == 0; }
    public boolean isNegative() { return numerator.signum() < 0; }

    public CapacityAmount add(CapacityAmount other) {
        Objects.requireNonNull(other);
        if (isZero()) return other;
        if (other.isZero()) return this;
        if (denominator.equals(other.denominator)) {
            return fraction(numerator.add(other.numerator), denominator);
        }
        BigInteger divisor = denominator.gcd(other.denominator);
        BigInteger leftScale = other.denominator.divide(divisor);
        BigInteger rightScale = denominator.divide(divisor);
        return fraction(numerator.multiply(leftScale).add(other.numerator.multiply(rightScale)),
                denominator.multiply(leftScale));
    }

    public CapacityAmount subtract(CapacityAmount other) {
        return add(other.negate());
    }

    public CapacityAmount negate() {
        return isZero() ? ZERO : new CapacityAmount(numerator.negate(), denominator, false);
    }

    public CapacityAmount multiply(long factor) {
        if (factor == 0 || isZero()) return ZERO;
        return fraction(numerator.multiply(BigInteger.valueOf(factor)), denominator);
    }

    /** Floors this non-negative value divided by a positive amount, saturating at {@link Long#MAX_VALUE}. */
    public long divideFloorToLong(CapacityAmount divisor) {
        Objects.requireNonNull(divisor);
        if (isNegative() || divisor.numerator.signum() <= 0) {
            throw new IllegalArgumentException("Capacity division requires a non-negative dividend and positive divisor");
        }
        BigInteger quotient = numerator.multiply(divisor.denominator)
                .divide(denominator.multiply(divisor.numerator));
        return saturatingLong(quotient);
    }

    public long floorToLong() {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger floor = division[0];
        if (numerator.signum() < 0 && division[1].signum() != 0) floor = floor.subtract(BigInteger.ONE);
        return saturatingLong(floor);
    }

    public long ceilToLong() {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        BigInteger ceil = division[0];
        if (numerator.signum() > 0 && division[1].signum() != 0) ceil = ceil.add(BigInteger.ONE);
        return saturatingLong(ceil);
    }

    public double doubleValue() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), 16, RoundingMode.HALF_UP).doubleValue();
    }

    /** User-facing decimal with enough precision for uncommon non-power-of-two stack sizes. */
    public String decimalString() {
        if (denominator.equals(BigInteger.ONE)) return numerator.toString();
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), 6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public CapacityAmount maxZero() {
        return isNegative() ? ZERO : this;
    }

    @Override
    public int compareTo(CapacityAmount other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override public boolean equals(Object other) {
        return other instanceof CapacityAmount amount
                && numerator.equals(amount.numerator) && denominator.equals(amount.denominator);
    }

    @Override public int hashCode() {
        return 31 * numerator.hashCode() + denominator.hashCode();
    }

    @Override public String toString() {
        return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
    }

    private static long saturatingLong(BigInteger value) {
        if (value.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) return Long.MAX_VALUE;
        if (value.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0) return Long.MIN_VALUE;
        return value.longValue();
    }
}
