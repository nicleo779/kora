package com.example.simple;

import com.example.simple.reflect.CtorOnlyUser;
import com.example.simple.reflect.CtorOnlyUserReflector;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedReflectorCompatibilityTest {
    @Test
    void reflectorShouldSupportConstructorOnlyEntityWithoutSetter() {
        GeneratedReflector<CtorOnlyUser> reflector = new CtorOnlyUserReflector();

        CtorOnlyUser user = reflector.newInstance();
        assertNotNull(user);
        assertEquals(null, reflector.get(user, "name"));

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> reflector.set(user, "name", "Demo"));

        assertEquals("No setter or field access for property: name", exception.getMessage());
        assertEquals(null, reflector.get(user, "name"));
        assertEquals(null, reflector.invoke(user, "getName", new Object[0]));
    }
}
