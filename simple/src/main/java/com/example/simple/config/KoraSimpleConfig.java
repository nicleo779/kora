package com.example.simple.config;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
        xml = {"/mapper"},
        entity = {"com.example.simple.entity"},
        mapper = {"com.example.simple.mapper"}
)
public class KoraSimpleConfig {
}
