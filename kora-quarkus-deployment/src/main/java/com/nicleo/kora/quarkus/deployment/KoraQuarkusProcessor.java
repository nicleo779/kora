package com.nicleo.kora.quarkus.deployment;

import com.nicleo.kora.core.annotation.KoraScan;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import jakarta.enterprise.context.Dependent;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

class KoraQuarkusProcessor {
    private static final DotName KORA_SCAN = DotName.createSimple(KoraScan.class.getName());
    private static final DotName DEPENDENT_SCOPE = DotName.createSimple(Dependent.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("kora-quarkus");
    }

    @BuildStep
    void registerGeneratedMappersAndSupport(CombinedIndexBuildItem combinedIndex,
                                            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
                                            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        IndexView index = combinedIndex.getIndex();
        Set<String> mapperPackages = new LinkedHashSet<>();
        Set<String> supportClasses = new LinkedHashSet<>();
        collectKoraScanMetadata(index, mapperPackages, supportClasses);

        if (!supportClasses.isEmpty()) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(supportClasses.toArray(String[]::new))
                    .methods()
                    .build());
        }

        Set<String> mapperImplementations = findMapperImplementations(index, mapperPackages);
        if (mapperImplementations.isEmpty()) {
            return;
        }

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .setDefaultScope(DEPENDENT_SCOPE)
                .setUnremovable();
        for (String mapperImplementation : mapperImplementations) {
            builder.addBeanClass(mapperImplementation);
        }
        additionalBeans.produce(builder.build());
    }

    void collectKoraScanMetadata(IndexView index, Set<String> mapperPackages, Set<String> supportClasses) {
        for (AnnotationInstance annotation : index.getAnnotations(KORA_SCAN)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo configClass = annotation.target().asClass();
            supportClasses.add("gen." + configClass.name().withoutPackagePrefix() + "Generated");

            AnnotationValue mapperValue = annotation.value("mapper");
            if (mapperValue == null) {
                continue;
            }
            for (String mapperPackage : mapperValue.asStringArray()) {
                if (!mapperPackage.isBlank()) {
                    mapperPackages.add(mapperPackage);
                }
            }
        }
    }

    Set<String> findMapperImplementations(IndexView index, Set<String> mapperPackages) {
        LinkedHashSet<String> mapperImplementations = new LinkedHashSet<>();
        if (mapperPackages.isEmpty()) {
            return mapperImplementations;
        }
        for (ClassInfo classInfo : index.getKnownClasses()) {
            if (!Modifier.isInterface(classInfo.flags())) {
                continue;
            }
            String interfaceName = classInfo.name().toString();
            if (!matchesMapperPackage(interfaceName, mapperPackages)) {
                continue;
            }
            String implClassName = interfaceName + "Impl";
            if (index.getClassByName(DotName.createSimple(implClassName)) != null) {
                mapperImplementations.add(implClassName);
            }
        }
        return mapperImplementations;
    }

    private boolean matchesMapperPackage(String className, Set<String> mapperPackages) {
        for (String mapperPackage : mapperPackages) {
            if (className.startsWith(mapperPackage + ".")) {
                return true;
            }
        }
        return false;
    }
}
