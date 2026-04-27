package com.nicleo.kora.quarkus.deployment.test.mapper;

import com.nicleo.kora.quarkus.deployment.test.entity.TestUser;

public interface TestUserMapper {
    TestUser selectById(Long id);
}
