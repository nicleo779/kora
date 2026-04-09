package com.example.simple.entity;

import com.example.simple.TestAnnotation;
import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;
import lombok.Data;

@Data
public class BaseUser<T> {
    @TestAnnotation("userId")
    private Long id;

    private T tag;

    public String describeId(String prefix) {
        return prefix + this.id;
    }
}
