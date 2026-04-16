package com.example.simple;

import com.example.simple.common.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PairRecordMapperGenerationTest {
    @Test
    void generatedMapperShouldAlsoGenerateRecordReflector() throws ClassNotFoundException {
        assertNotNull(Class.forName("com.example.simple.mapper.PairUserMapperImpl"));
        assertNotNull(Class.forName(GeneratedTypeNames.reflectorTypeName(Pair.class)));
    }
}
