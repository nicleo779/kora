package com.nicleo.kora.core.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class GeneratedReflectors {
    private static final Map<Class<?>, GeneratedReflector<?>> REGISTRY = new ConcurrentHashMap<>();

    private GeneratedReflectors() {
    }

    public static <T> void register(Class<T> type, GeneratedReflector<? extends T> reflector) {
        REGISTRY.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(reflector, "reflector"));
    }

    public static void clear() {
        REGISTRY.clear();
    }

    @SuppressWarnings("unchecked")
    public static <T> GeneratedReflector<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return (GeneratedReflector<T>) REGISTRY.get(type);
    }

}
