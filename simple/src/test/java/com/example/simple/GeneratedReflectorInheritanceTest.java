package com.example.simple;

import com.example.simple.inheritance.AdminUser;
import com.example.simple.inheritance.BaseUser;
import com.example.simple.inheritance.TestReflectTag;
import com.nicleo.kora.core.runtime.AnnotationMeta;
import com.nicleo.kora.core.runtime.ClassInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedReflectorInheritanceTest {
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void installReflectors() {
        GeneratedReflectors.clear();
        GeneratedReflectors.register(BaseUser.class, newReflector(BaseUser.class));
        GeneratedReflectors.register(AdminUser.class, newReflector(AdminUser.class));
    }

    @SuppressWarnings("unchecked")
    private static <T> GeneratedReflector<T> newReflector(Class<T> type) {
        try {
            return (GeneratedReflector<T>) Class
                    .forName(GeneratedTypeNames.reflectorTypeName(type))
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Unknown type: " + type.getName(), ex);
        }
    }

    @Test
    void generatedReflectorShouldExposeClassMetadataAndInheritedMembers() {
        GeneratedReflector<AdminUser> reflector = GeneratedReflectors.get(AdminUser.class);
        AdminUser user = new AdminUser();

        reflector.set(user, "id", 7L);
        reflector.set(user, "role", "admin");

        ClassInfo classInfo = reflector.getClassInfo();
        assertNotNull(classInfo);
        assertEquals(AdminUser.class, classInfo.type());
        assertEquals(BaseUser.class, classInfo.superType());

        AnnotationMeta tag = Arrays.stream(classInfo.annotations())
                .filter(annotation -> annotation.type().equals(TestReflectTag.class.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals("admin", tag.value("value"));

        assertEquals(7L, reflector.get(user, "id"));
        assertEquals("admin", reflector.get(user, "role"));
        assertEquals("prefix-7", reflector.invoke(user, "baseLabel", new Object[]{"prefix-"}));

        assertNotNull(reflector.getField("id"));
        assertNotNull(reflector.getField("role"));
        assertTrue(Arrays.asList(reflector.getFields()).containsAll(List.of("id", "role")));
        assertTrue(Arrays.asList(reflector.getMethods()).contains("baseLabel"));
        assertFalse(Arrays.asList(reflector.getMethods()).contains("equals"));
        assertEquals(0, reflector.getMethod("equals").length);
    }
}
