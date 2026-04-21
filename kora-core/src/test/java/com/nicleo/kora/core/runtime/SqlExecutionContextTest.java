package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.xml.SqlCommandType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlExecutionContextTest {
    @Test
    void shouldExposeMapperMethodAnnotationsWithoutReflection() {
        AnnotationMeta[] annotations = {
                new AnnotationMeta(TestTag.class.getName(), java.util.Map.of("value", "direct", "order", 1))
        };
        SqlExecutionContext context = new SqlExecutionContext(
                null,
                "com.example.Mapper",
                "selectById",
                SqlCommandType.SELECT,
                String.class,
                null,
                null,
                annotations,
                true
        );

        AnnotationMeta annotation = context.getMapperMethodAnnotation(TestTag.class.getName());
        assertEquals("direct", annotation.value("value"));
        assertEquals(1, annotation.value("order"));
        assertEquals("direct", context.getMapperMethodAnnotation(TestTag.class).value("value"));
    }

    @Test
    void shouldReturnNullWhenAnnotationIsMissing() {
        SqlExecutionContext context = new SqlExecutionContext(
                null,
                "com.example.Mapper",
                "selectById",
                SqlCommandType.SELECT,
                String.class,
                null,
                null,
                new AnnotationMeta[0],
                true
        );

        assertNull(context.getMapperMethodAnnotation("com.example.TestTag"));
        assertNull(context.getMapperMethodAnnotation(TestTag.class));
        assertEquals(0, context.getMapperMethodAnnotations().length);
    }

    @interface TestTag {
    }
}
