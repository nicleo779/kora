package com.nicleo.kora.core.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public record ClassInfo(Class<?> type, Type superType, int modifiers, Annotation[] annotations, ParameterInfo[] params) {
}
