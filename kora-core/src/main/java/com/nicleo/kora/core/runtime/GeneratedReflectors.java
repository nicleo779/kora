package com.nicleo.kora.core.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GeneratedReflectors {
    private static final Map<Class<?>, GeneratedReflector<?>> REGISTRY = new ConcurrentHashMap<>();
    private static final List<Resolver> RESOLVERS = new CopyOnWriteArrayList<>();

    private GeneratedReflectors() {
    }

    public static void install(Resolver resolver) {
        RESOLVERS.add(Objects.requireNonNull(resolver, "resolver"));
    }

    public static <T> void register(Class<T> type, GeneratedReflector<? extends T> reflector) {
        REGISTRY.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(reflector, "reflector"));
    }

    public static void clear() {
        REGISTRY.clear();
        RESOLVERS.clear();
    }

    public static <T> GeneratedReflector<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        GeneratedReflector<T> cached = cached(type);
        if (cached != null) {
            return cached;
        }
        for (int i = RESOLVERS.size() - 1; i >= 0; i--) {
            GeneratedReflector<T> reflector = RESOLVERS.get(i).resolveOrNull(type);
            if (reflector != null) {
                REGISTRY.putIfAbsent(type, reflector);
                return reflector;
            }
        }
        throw new SqlSessionException("No GeneratedReflector resolver installed for type: " + type.getName());
    }

    @SuppressWarnings("unchecked")
    private static <T> GeneratedReflector<T> cached(Class<T> type) {
        return (GeneratedReflector<T>) REGISTRY.get(type);
    }

    public interface Resolver {
        <T> GeneratedReflector<T> get(Class<T> type);

        default <T> GeneratedReflector<T> resolveOrNull(Class<T> type) {
            try {
                return get(type);
            } catch (IllegalArgumentException | SqlSessionException ex) {
                return null;
            }
        }
    }
}
