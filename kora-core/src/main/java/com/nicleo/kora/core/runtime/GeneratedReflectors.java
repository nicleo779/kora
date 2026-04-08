package com.nicleo.kora.core.runtime;

public final class GeneratedReflectors {
    private static volatile Resolver resolver;

    private GeneratedReflectors() {
    }

    public static void install(Resolver resolver) {
        GeneratedReflectors.resolver = resolver;
    }

    public static <T> GeneratedReflector<T> get(Class<T> type) {
        Resolver current = resolver;
        if (current == null) {
            throw new SqlSessionException("No GeneratedReflector resolver installed for type: " + type.getName());
        }
        return current.get(type);
    }

    public interface Resolver {
        <T> GeneratedReflector<T> get(Class<T> type);
    }
}
