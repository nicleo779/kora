package com.example.simple;

import com.example.simple.mapper.RawMultiTypeUserMapper;
import com.example.simple.mapper.RawMultiTypeUserMapperImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RawMultiTypeMapperGenerationTest {
    @Test
    void rawMultiGenericCapabilityShouldFallbackToNullEntityClass() {
        RawMultiTypeUserMapper mapper = new RawMultiTypeUserMapperImpl(new MixedCapabilityMapperGenerationTest.NoopSqlExecutor());

        assertNotNull(mapper);
        assertEquals("null", mapper.firstTypeName());
    }
}
