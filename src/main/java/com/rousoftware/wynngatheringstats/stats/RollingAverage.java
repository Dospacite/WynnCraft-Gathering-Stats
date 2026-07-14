package com.rousoftware.wynngatheringstats.stats;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalDouble;

final class RollingAverage {
    private final int capacity;
    private final Deque<Double> values = new ArrayDeque<>();
    private double sum;

    RollingAverage(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    void add(double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("value must be finite and non-negative");
        }

        values.addLast(value);
        sum += value;
        if (values.size() > capacity) {
            sum -= values.removeFirst();
        }
    }

    OptionalDouble average() {
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sum / values.size());
    }

    int size() {
        return values.size();
    }

    void clear() {
        values.clear();
        sum = 0;
    }
}
