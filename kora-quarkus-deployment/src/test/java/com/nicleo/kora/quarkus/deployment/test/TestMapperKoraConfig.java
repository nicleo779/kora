package com.nicleo.kora.quarkus.deployment.test.config;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
        entity = {"com.nicleo.kora.quarkus.deployment.test.entity"},
        mapper = {"com.nicleo.kora.quarkus.deployment.test.mapper"}
)
public class TestMapperKoraConfig {
}
