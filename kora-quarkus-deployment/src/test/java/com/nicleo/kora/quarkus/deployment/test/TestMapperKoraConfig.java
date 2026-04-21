package com.nicleo.kora.quarkus.deployment.test;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
        entity = {"com.nicleo.kora.quarkus.deployment.test"},
        mapper = {"com.nicleo.kora.quarkus.deployment.test"}
)
public class TestMapperKoraConfig {
}
