package com.example.simple.entity;

import com.example.simple.TestAnnotation;
import com.nicleo.kora.core.annotation.ID;
import com.nicleo.kora.core.annotation.IdStrategy;
import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;
import lombok.Data;

@Data
//@Reflect(annotationMetadata = true)
public class BaseUser<T> {
    @TestAnnotation(value = "userId",size = 3)
    @ID(strategy = IdStrategy.CUSTOM)
    private Long id;

    private T tag;

    public String describeId(String prefix) {
        return prefix + this.id;
    }
}
