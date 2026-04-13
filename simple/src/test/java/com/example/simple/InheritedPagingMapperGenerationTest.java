package com.example.simple;

import com.example.simple.mapper.InheritedPagingUserMapperImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class InheritedPagingMapperGenerationTest {
    @Test
    void generatedMapperShouldExistForPagingSubclassParameter() {
        assertNotNull(InheritedPagingUserMapperImpl.class);
    }
}
