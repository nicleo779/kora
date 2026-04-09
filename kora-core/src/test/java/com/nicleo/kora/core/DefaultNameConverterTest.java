package com.nicleo.kora.core;

import com.nicleo.kora.core.util.DefaultNameConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultNameConverterTest {
    @Test
    void fieldToColumnConvertsCamelCase() {
        assertEquals("user_name", DefaultNameConverter.INSTANCE.fieldToColumn(Object.class, "userName"));
        assertEquals("age", DefaultNameConverter.INSTANCE.fieldToColumn(Object.class, "age"));
    }

    @Test
    void columnToFieldConvertsSnakeCase() {
        assertEquals("userName", DefaultNameConverter.INSTANCE.columnToField(Object.class, "user_name"));
        assertEquals("age", DefaultNameConverter.INSTANCE.columnToField(Object.class, "age"));
    }
}
