package com.nicleo.kora.core.runtime;

import java.lang.reflect.Type;

public record MethodInfo(String name, Type returnType, int modifiers, ParameterInfo[] params, AnnotationMeta[] annotations) {
}
