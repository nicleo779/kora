package com.example.simple;

import com.example.simple.entity.BaseUser;
import com.example.simple.entity.User;
import com.nicleo.kora.core.runtime.ClassInfo;
import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class GeneratedReflectorGenericTypeTest {
    @Test
    @SuppressWarnings("unchecked")
    void generatedReflectorShouldPreserveGenericTypeMetadata() throws Exception {
        GeneratedReflector<User> reflector = (GeneratedReflector<User>) Class
                .forName(GeneratedTypeNames.reflectorTypeName(User.class))
                .getConstructor()
                .newInstance();

        ClassInfo classInfo = reflector.getClassInfo();
        ParameterizedType superType = assertInstanceOf(ParameterizedType.class, classInfo.superType());
        assertEquals(BaseUser.class, superType.getRawType());
        assertEquals(Long.class, superType.getActualTypeArguments()[0]);

        FieldInfo ids = reflector.getField("ids");
        ParameterizedType idsType = assertInstanceOf(ParameterizedType.class, ids.type());
        assertEquals(java.util.List.class, idsType.getRawType());
        assertEquals(String.class, idsType.getActualTypeArguments()[0]);

        assertEquals(User.class, classInfo.type());
    }
}
