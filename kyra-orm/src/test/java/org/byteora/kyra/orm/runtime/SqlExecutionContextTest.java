package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.orm.xml.SqlCommandType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlExecutionContextTest {
    @Test
    void shouldExposeMapperMethodAnnotationsWithoutReflection() {
        AnnotationMeta[] annotations = {
                new AnnotationMeta(TestTag.class.getName(), java.util.Map.of("value", "direct", "order", 1))
        };
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .mapper(TestMapper.class, "selectById")
                .resultType(String.class)
                .annotations(annotations)
                .build();

        AnnotationMeta annotation = context.getMapperMethodAnnotation(TestTag.class.getName());
        assertEquals("direct", annotation.value("value"));
        assertEquals(1, annotation.value("order"));
        assertEquals("direct", context.getMapperMethodAnnotation(TestTag.class).value("value"));
    }

    @Test
    void shouldReturnNullWhenAnnotationIsMissing() {
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .mapper(TestMapper.class, "selectById")
                .resultType(String.class)
                .build();

        assertNull(context.getMapperMethodAnnotation("com.example.TestTag"));
        assertNull(context.getMapperMethodAnnotation(TestTag.class));
        assertEquals(0, context.getMapperMethodAnnotations().length);
    }

    @interface TestTag {
    }

    interface TestMapper {
    }
}
