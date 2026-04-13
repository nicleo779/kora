package com.nicleo.kora.spring.boot.autoconfigure.mapper;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
        xml = {},
        entity = {"com.nicleo.kora.spring.boot.autoconfigure.mapper"},
        mapper = {"com.nicleo.kora.spring.boot.autoconfigure.mapper"}
)
public class TestMapperKoraConfig {
}
