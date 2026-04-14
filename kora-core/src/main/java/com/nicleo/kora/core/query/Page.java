package com.nicleo.kora.core.query;

import java.util.List;
import java.util.function.Function;

public record Page<T>(Integer current, Integer size, Long total, List<T> records) {
    public <R> Page<R> convert(Function<T, R> consumer) {
        var list = records.stream().map(consumer).toList();
        return new Page<>(current, size, total, list);
    }
}
