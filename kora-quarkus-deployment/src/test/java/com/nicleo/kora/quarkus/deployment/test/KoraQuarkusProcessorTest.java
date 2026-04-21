package com.nicleo.kora.quarkus.deployment;

import com.nicleo.kora.quarkus.deployment.test.config.TestMapperKoraConfig;
import com.nicleo.kora.quarkus.deployment.test.mapper.TestUserMapper;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoraQuarkusProcessorTest {
    @Test
    void shouldDiscoverSupportClassAndGeneratedMapperImplementation() throws Exception {
        KoraQuarkusProcessor processor = new KoraQuarkusProcessor();
        IndexView index = indexOf(
                TestMapperKoraConfig.class,
                TestUserMapper.class,
                Class.forName("com.nicleo.kora.quarkus.deployment.test.mapper.TestUserMapperImpl")
        );

        Set<String> mapperPackages = new LinkedHashSet<>();
        Set<String> supportClasses = new LinkedHashSet<>();
        processor.collectKoraScanMetadata(index, mapperPackages, supportClasses);

        assertEquals(Set.of("com.nicleo.kora.quarkus.deployment.test.mapper"), mapperPackages);
        assertEquals(Set.of("gen.TestMapperKoraConfigGenerated"), supportClasses);

        Set<String> mapperImplementations = processor.findMapperImplementations(index, mapperPackages);
        assertEquals(Set.of("com.nicleo.kora.quarkus.deployment.test.mapper.TestUserMapperImpl"), mapperImplementations);
    }

    @Test
    void shouldOnlyMatchConfiguredMapperPackages() throws Exception {
        KoraQuarkusProcessor processor = new KoraQuarkusProcessor();
        IndexView index = indexOf(
                TestMapperKoraConfig.class,
                TestUserMapper.class,
                Class.forName("com.nicleo.kora.quarkus.deployment.test.mapper.TestUserMapperImpl")
        );

        Set<String> mapperImplementations = processor.findMapperImplementations(
                index,
                Set.of("com.nicleo.kora.quarkus.deployment.test.other")
        );

        assertTrue(mapperImplementations.isEmpty());
    }

    private IndexView indexOf(Class<?>... types) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : types) {
            String resourceName = type.getName().replace('.', '/') + ".class";
            try (InputStream inputStream = type.getClassLoader().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing class resource: " + resourceName);
                }
                indexer.index(inputStream);
            }
        }
        return indexer.complete();
    }
}
