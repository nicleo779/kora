package com.nicleo.kora.core;

import com.nicleo.kora.core.util.NameUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameUtilsTest {
    @Test
    void camelToSnakeConvertsWords() {
        assertEquals("user_name", NameUtils.camelToSnake("userName"));
        assertEquals("age", NameUtils.camelToSnake("age"));
    }
}
