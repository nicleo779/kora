package com.nicleo.kora.quarkus.runtime;

import gen.TestConfigGenerated;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KoraSupportInstallerTest {
    @Test
    void shouldInstallGeneratedSupportFromScanIndex() {
        TestConfigGenerated.reset();

        new KoraSupportInstaller().installGeneratedSupport();

        assertTrue(TestConfigGenerated.installed());
    }
}
