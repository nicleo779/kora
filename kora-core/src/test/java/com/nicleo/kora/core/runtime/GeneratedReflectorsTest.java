package com.nicleo.kora.core.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedReflectorsTest {
    @BeforeEach
    void setUp() {
        GeneratedReflectors.clear();
    }

    @Test
    void shouldResolveAcrossMultipleInstalledResolvers() {
        AlphaReflector alphaReflector = new AlphaReflector();
        BetaReflector betaReflector = new BetaReflector();
        GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
            @Override
            public <T> GeneratedReflector<T> get(Class<T> type) {
                if (type == Alpha.class) {
                    return cast(alphaReflector);
                }
                throw new IllegalArgumentException(type.getName());
            }
        });
        GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
            @Override
            public <T> GeneratedReflector<T> get(Class<T> type) {
                if (type == Beta.class) {
                    return cast(betaReflector);
                }
                throw new IllegalArgumentException(type.getName());
            }
        });

        assertSame(alphaReflector, GeneratedReflectors.get(Alpha.class));
        assertSame(betaReflector, GeneratedReflectors.get(Beta.class));
        assertSame(alphaReflector, GeneratedReflectors.get(Alpha.class));
    }

    @Test
    void shouldPreferDirectRegistryEntries() {
        AlphaReflector directReflector = new AlphaReflector();
        GeneratedReflectors.register(Alpha.class, directReflector);
        GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
            @Override
            public <T> GeneratedReflector<T> get(Class<T> type) {
                throw new IllegalArgumentException(type.getName());
            }
        });

        assertSame(directReflector, GeneratedReflectors.get(Alpha.class));
    }

    @Test
    void shouldThrowWhenTypeIsUnknown() {
        assertThrows(SqlSessionException.class, () -> GeneratedReflectors.get(Alpha.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> GeneratedReflector<T> cast(GeneratedReflector<?> reflector) {
        return (GeneratedReflector<T>) reflector;
    }

    static final class Alpha {
    }

    static final class Beta {
    }

    static final class AlphaReflector implements GeneratedReflector<Alpha> {
        @Override
        public Alpha newInstance() {
            return new Alpha();
        }

        @Override
        public Object invoke(Alpha target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Alpha target, String property, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Alpha target, String property) {
            throw new UnsupportedOperationException();
        }
    }

    static final class BetaReflector implements GeneratedReflector<Beta> {
        @Override
        public Beta newInstance() {
            return new Beta();
        }

        @Override
        public Object invoke(Beta target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Beta target, String property, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Beta target, String property) {
            throw new UnsupportedOperationException();
        }
    }
}
