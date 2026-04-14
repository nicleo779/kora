package com.example.simple.reflect;

import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;

@Reflect(metadata = ReflectMetadataLevel.METHOD)
public class CtorOnlyUser {
    private String name;

    public CtorOnlyUser(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
