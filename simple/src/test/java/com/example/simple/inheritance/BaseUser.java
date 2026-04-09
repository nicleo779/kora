package com.example.simple.inheritance;

import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;

@Reflect(metadata = ReflectMetadataLevel.ALL, annotationMetadata = true)
@TestReflectTag("base")
public class BaseUser {
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String baseLabel(String prefix) {
        return prefix + id;
    }
}
