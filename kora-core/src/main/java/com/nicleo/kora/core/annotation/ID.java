package com.nicleo.kora.core.annotation;

import com.nicleo.kora.core.runtime.IdGenerator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface ID {
    IdStrategy strategy() default IdStrategy.NONE;

    Class<? extends IdGenerator> generator() default IdGenerator.class;
}
