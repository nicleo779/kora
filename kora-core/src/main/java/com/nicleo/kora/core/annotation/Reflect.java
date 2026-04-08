package com.nicleo.kora.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Reflect {
    String suffix() default "GeneratedReflector";

    ReflectMetadataLevel metadata() default ReflectMetadataLevel.FIELDS;

    boolean annotationMetadata() default false;
}
