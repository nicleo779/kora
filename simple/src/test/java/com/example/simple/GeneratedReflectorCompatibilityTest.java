package com.example.simple;

import com.example.simple.reflect.CtorOnlyUser;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedReflectorCompatibilityTest {
    @Test
    @SuppressWarnings("unchecked")
    void reflectorShouldSupportConstructorOnlyEntityWithoutSetter() throws Exception {
        GeneratedReflector<CtorOnlyUser> reflector = (GeneratedReflector<CtorOnlyUser>) Class
                .forName(GeneratedTypeNames.reflectorTypeName(CtorOnlyUser.class))
                .getConstructor()
                .newInstance();

        CtorOnlyUser user = reflector.newInstance();
        assertNotNull(user);

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> reflector.set(user, "name", "Demo"));
        UnsupportedOperationException getterException = assertThrows(UnsupportedOperationException.class,
                () -> reflector.get(user, "name"));
        IllegalArgumentException invokeException = assertThrows(IllegalArgumentException.class,
                () -> reflector.invoke(user, "getName", new Object[0]));

        assertEquals("No setter or field access for property: name", exception.getMessage());
        assertEquals("No getter or field access for property: name", getterException.getMessage());
        assertEquals("Unknown method", invokeException.getMessage());
    }
}
