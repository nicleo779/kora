package com.nicleo.kora.core.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public record ParameterInfo(String name, Type type, Annotation[] annotations) {
}
