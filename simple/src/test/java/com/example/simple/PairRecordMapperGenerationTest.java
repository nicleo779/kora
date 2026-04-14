package com.example.simple;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PairRecordMapperGenerationTest {
    @Test
    void generatedMapperShouldAlsoGenerateRecordReflector() throws ClassNotFoundException {
        assertNotNull(Class.forName("com.example.simple.mapper.PairUserMapperImpl"));
        assertNotNull(Class.forName("com.example.simple.common.PairReflector"));
    }
}
