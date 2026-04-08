package com.nicleo.kora.core.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public record FieldInfo(String name, Type type, int modifiers, String getter, String setter, Annotation[] annotations) {
}
