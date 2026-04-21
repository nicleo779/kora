package com.nicleo.kora.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnotationMetaTest {
    @Test
    void shouldExposeCommonTypedValues() {
        AnnotationMeta meta = new AnnotationMeta("demo.Tag", Map.of(
                "name", "demo",
                "order", 3,
                "enabled", true,
                "ratio", 1.5d,
                "type", "java.lang.String"
        ));

        assertEquals("demo", meta.stringValue("name"));
        assertEquals(3, meta.integerValue("order"));
        assertEquals(true, meta.booleanValue("enabled"));
        assertEquals(1.5d, meta.doubleValue("ratio"));
        assertEquals("java.lang.String", meta.classValue("type"));
    }

    @Test
    void shouldConvertCompatibleStringAndNumberValues() {
        AnnotationMeta meta = new AnnotationMeta("demo.Tag", Map.of(
                "order", "12",
                "count", 12,
                "enabled", "true",
                "ratio", "2.5"
        ));

        assertEquals(12, meta.integerValue("order"));
        assertEquals(12L, meta.longValue("count"));
        assertEquals(true, meta.booleanValue("enabled"));
        assertEquals(2.5d, meta.doubleValue("ratio"));
    }

    @Test
    void shouldReturnNullForMissingValue() {
        AnnotationMeta meta = new AnnotationMeta("demo.Tag", Map.of());

        assertNull(meta.stringValue("missing"));
        assertNull(meta.integerValue("missing"));
        assertNull(meta.longValue("missing"));
        assertNull(meta.booleanValue("missing"));
        assertNull(meta.doubleValue("missing"));
        assertNull(meta.classValue("missing"));
    }

    @Test
    void shouldRejectUnsupportedConversions() {
        AnnotationMeta meta = new AnnotationMeta("demo.Tag", Map.of("value", new Object()));

        assertThrows(IllegalArgumentException.class, () -> meta.integerValue("value"));
        assertThrows(IllegalArgumentException.class, () -> meta.booleanValue("value"));
        assertThrows(IllegalArgumentException.class, () -> meta.doubleValue("value"));
    }
}
