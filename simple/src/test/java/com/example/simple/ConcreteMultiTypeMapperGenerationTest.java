package com.example.simple;

import com.example.simple.dto.UserSummary;
import com.example.simple.mapper.ConcreteMultiTypeUserMapper;
import com.example.simple.mapper.ConcreteMultiTypeUserMapperImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConcreteMultiTypeMapperGenerationTest {
    @Test
    void multiGenericCapabilityShouldUseFirstGenericType() {
        ConcreteMultiTypeUserMapper mapper = new ConcreteMultiTypeUserMapperImpl(new MixedCapabilityMapperGenerationTest.NoopSqlExecutor());

        assertNotNull(mapper);
        assertEquals(UserSummary.class.getName(), mapper.firstTypeName());
    }
}
