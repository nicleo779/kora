package com.nicleo.kora.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedSupportIndexStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldDropInvalidEntriesWhenLoadingAndRewriteOnlyValidOnes() throws Exception {
        GeneratedSupportIndexStore store = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        store.upsertReflector("com.example.User", "gen.demo.UserReflector");
        store.upsertTable("com.example.User", "gen.demo.UserTable");
        store.upsertReflector("missing.User", "gen.demo.MissingReflector");
        store.write();

        GeneratedSupportIndexStore reloaded = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        reloaded.load((entityTypeName, generatedTypeName) -> !entityTypeName.startsWith("missing."));

        assertEquals(1, reloaded.reflectors().size());
        assertEquals("com.example.User", reloaded.reflectors().getFirst().entityTypeName());
        assertEquals(1, reloaded.tables().size());
        assertTrue(reloaded.isDirty());

        reloaded.write();
        GeneratedSupportIndexStore rewritten = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        rewritten.load((entityTypeName, generatedTypeName) -> true);

        assertEquals(1, rewritten.reflectors().size());
        assertEquals("gen.demo.UserReflector", rewritten.reflectors().getFirst().reflectorTypeName());
        assertEquals(1, rewritten.tables().size());
        assertEquals("gen.demo.UserTable", rewritten.tables().getFirst().tableTypeName());
    }
}
