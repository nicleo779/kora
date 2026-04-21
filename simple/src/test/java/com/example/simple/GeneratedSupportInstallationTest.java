package com.example.simple;

import com.example.simple.entity.User;
import com.nicleo.kora.core.query.Tables;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedSupportInstallationTest {
    @Test
    void supportClassShouldInstallGeneratedTablesAndReflectorsFromGenSubdirectories() throws Exception {
        GeneratedReflectors.clear();
        Tables.clear();
        Class<?> supportClass = Class.forName("gen.KoraSimpleConfigGenerated");
        java.lang.reflect.Field installedField = supportClass.getDeclaredField("installed");
        installedField.setAccessible(true);
        try {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("gen.com.example.simple.config.KoraSimpleConfigGenerated"));

            installedField.setBoolean(null, false);
            supportClass.getMethod("install").invoke(null);

            assertNotNull(Tables.get(User.class));
            assertNotNull(GeneratedReflectors.get(User.class));
            assertEquals(GeneratedTypeNames.tableTypeName(User.class), Tables.get(User.class).getClass().getName());
            assertEquals(GeneratedTypeNames.reflectorTypeName(User.class), GeneratedReflectors.get(User.class).getClass().getName());
        } finally {
            GeneratedReflectors.clear();
            Tables.clear();
            installedField.setBoolean(null, false);
        }
    }
}
