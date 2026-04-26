package com.nicleo.kora.core.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class GeneratedReflectorsTest {
    @BeforeEach
    void setUp() {
        GeneratedReflectors.clear();
    }

    @Test
    void shouldResolveRegisteredReflectors() {
        AlphaReflector alphaReflector = new AlphaReflector();
        BetaReflector betaReflector = new BetaReflector();
        GeneratedReflectors.register(Alpha.class, alphaReflector);
        GeneratedReflectors.register(Beta.class, betaReflector);

        assertSame(alphaReflector, GeneratedReflectors.get(Alpha.class));
        assertSame(betaReflector, GeneratedReflectors.get(Beta.class));
        assertSame(alphaReflector, GeneratedReflectors.get(Alpha.class));
    }

    @Test
    void shouldReplaceRegistryEntries() {
        AlphaReflector directReflector = new AlphaReflector();
        AlphaReflector replacementReflector = new AlphaReflector();
        GeneratedReflectors.register(Alpha.class, directReflector);
        GeneratedReflectors.register(Alpha.class, replacementReflector);

        assertSame(replacementReflector, GeneratedReflectors.get(Alpha.class));
    }

    @Test
    void shouldReturnNullWhenTypeIsUnknown() {
        assertNull(GeneratedReflectors.get(Alpha.class));
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
        public ClassInfo getClassInfo() {
            return null;
        }

        @Override
        public Object invoke(Alpha target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Alpha target, int index, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Alpha target, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getFields() {
            return new String[0];
        }

        @Override
        public FieldInfo getField(int index) {
            return null;
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public int getMethod(int index) {
            return -1;
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }

    static final class BetaReflector implements GeneratedReflector<Beta> {
        @Override
        public Beta newInstance() {
            return new Beta();
        }

        @Override
        public ClassInfo getClassInfo() {
            return null;
        }

        @Override
        public Object invoke(Beta target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Beta target, int index, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Beta target, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getFields() {
            return new  String[0];
        }

        @Override
        public FieldInfo getField(int index) {
            return null;
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public int getMethod(int index) {
            return -1;
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }
}
