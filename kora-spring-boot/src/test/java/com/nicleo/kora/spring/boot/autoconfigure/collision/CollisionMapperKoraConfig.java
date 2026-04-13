package com.nicleo.kora.spring.boot.autoconfigure.collision;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
        xml = {},
        entity = {
                "com.nicleo.kora.spring.boot.autoconfigure.collision.left",
                "com.nicleo.kora.spring.boot.autoconfigure.collision.right"
        },
        mapper = {
                "com.nicleo.kora.spring.boot.autoconfigure.collision.left",
                "com.nicleo.kora.spring.boot.autoconfigure.collision.right"
        }
)
public class CollisionMapperKoraConfig {
}
