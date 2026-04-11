package com.nicleo.kora.processor;

import com.nicleo.kora.core.annotation.KoraScan;
import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;
import com.nicleo.kora.core.dynamic.BindSqlNode;
import com.nicleo.kora.core.dynamic.ChooseSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.ForEachSqlNode;
import com.nicleo.kora.core.dynamic.IfSqlNode;
import com.nicleo.kora.core.dynamic.MixedSqlNode;
import com.nicleo.kora.core.dynamic.TextSqlNode;
import com.nicleo.kora.core.dynamic.TrimSqlNode;
import com.nicleo.kora.core.dynamic.WhenSqlNode;
import com.nicleo.kora.core.xml.MapperXmlDefinition;
import com.nicleo.kora.core.xml.SqlCommandType;
import com.nicleo.kora.core.xml.SqlNodeDefinition;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SupportedAnnotationTypes({
        "com.nicleo.kora.core.annotation.KoraScan",
        "com.nicleo.kora.core.annotation.MapperCapability",
        "com.nicleo.kora.core.annotation.Reflect"
})
@SupportedOptions("kora.projectDir")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class KoraProcessor extends AbstractProcessor {
    static final String SQL_SESSION = "com.nicleo.kora.core.runtime.SqlSession";
    private static final String LIST_TYPE = "java.util.List";
    private static final String PAGE_TYPE = "com.nicleo.kora.core.query.Page";
    private static final String PAGING_TYPE = "com.nicleo.kora.core.query.Paging";
    private static final String ID_ANNOTATION = "com.nicleo.kora.core.annotation.ID";
    static final String ID_STRATEGY_TYPE = "com.nicleo.kora.core.annotation.IdStrategy";
    static final String ID_GENERATOR_TYPE = "com.nicleo.kora.core.runtime.IdGenerator";

    private Filer filer;
    private Messager messager;
    private Elements elements;
    private Types types;
    private String projectDir;
    private MapperXmlLoader xmlLoader;
    private GeneratedJavaSourceWriter sourceWriter;
    private MapperSourceGenerator mapperSourceGenerator;
    private EntityTableSourceGenerator entityTableSourceGenerator;
    private ReflectorSourceGenerator reflectorSourceGenerator;
    private Map<String, String> activeTypeConstants = Map.of();

    private final Map<String, ReflectSpec> reflectSpecs = new LinkedHashMap<>();
    private final Map<String, MapperCapabilitySpec> mapperCapabilitySpecs = new LinkedHashMap<>();
    private final List<ScanSpec> scanSpecs = new ArrayList<>();
    private final Set<String> generatedReflectors = new HashSet<>();
    private final Set<String> generatedMeta = new HashSet<>();
    private final Set<String> generatedMappers = new HashSet<>();
    private final Set<String> generatedSupports = new HashSet<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.projectDir = processingEnv.getOptions().getOrDefault("kora.projectDir", System.getProperty("user.dir", "."));
        this.xmlLoader = new MapperXmlLoader(projectDir);
        this.sourceWriter = new GeneratedJavaSourceWriter(filer);
        this.mapperSourceGenerator = new MapperSourceGenerator(new MapperSourceGenerator.Context() {
            @Override
            public String packageNameOf(TypeElement typeElement) {
                return KoraProcessor.this.packageNameOf(typeElement);
            }

            @Override
            public String supportSimpleName(ScanSpec scanSpec) {
                return KoraProcessor.this.supportSimpleName(scanSpec);
            }

            @Override
            public String mapperEntityTypeLiteral(TypeElement mapperType) {
                return KoraProcessor.this.mapperEntityTypeLiteral(mapperType);
            }

            @Override
            public TypeElement mapperEntityType(TypeElement mapperType) {
                return KoraProcessor.this.mapperEntityType(mapperType);
            }

            @Override
            public String statementFieldName(String statementId) {
                return KoraProcessor.this.statementFieldName(statementId);
            }

            @Override
            public String renderSqlNode(DynamicSqlNode node) {
                return KoraProcessor.this.renderSqlNode(node);
            }

            @Override
            public String tableConstantReference(TypeElement entityType) {
                return KoraProcessor.this.tableConstantReference(entityType);
            }

            @Override
            public String renderPagingArgument(ExecutableElement method) {
                return KoraProcessor.this.renderPagingArgument(method);
            }

            @Override
            public String renderResultTypeLiteral(ExecutableElement method, SqlNodeDefinition statement) {
                return KoraProcessor.this.renderResultTypeLiteral(method, statement);
            }

            @Override
            public String renderExecution(TypeElement mapperType, ExecutableElement method, SqlNodeDefinition statement) {
                return KoraProcessor.this.renderExecution(mapperType, method, statement);
            }
        });
        this.entityTableSourceGenerator = new EntityTableSourceGenerator(new EntityTableSourceGenerator.Context() {
            @Override
            public String resolveTableName(TypeElement entityType) {
                return KoraProcessor.this.resolveTableName(entityType);
            }

            @Override
            public List<VariableElement> collectTableFields(TypeElement entityType) {
                return KoraProcessor.this.collectTableFields(entityType);
            }

            @Override
            public VariableElement resolveIdField(TypeElement entityType, List<VariableElement> fields) {
                return KoraProcessor.this.resolveIdField(entityType, fields);
            }

            @Override
            public IdGenerationSpec resolveIdGeneration(TypeElement entityType, VariableElement idField) {
                return KoraProcessor.this.resolveIdGeneration(entityType, idField);
            }

            @Override
            public String escapeJava(String text) {
                return KoraProcessor.this.escapeJava(text);
            }

            @Override
            public String renderRuntimeCastType(TypeMirror typeMirror) {
                return KoraProcessor.this.renderRuntimeCastType(typeMirror);
            }

            @Override
            public String resolveColumnName(VariableElement field) {
                return KoraProcessor.this.resolveColumnName(field);
            }
        });
        this.reflectorSourceGenerator = new ReflectorSourceGenerator(new ReflectorSourceGenerator.Context() {
            @Override
            public List<VariableElement> collectInstanceFields(TypeElement entityType) {
                return KoraProcessor.this.collectInstanceFields(entityType);
            }

            @Override
            public List<ExecutableElement> collectInvokableMethods(TypeElement entityType) {
                return KoraProcessor.this.collectInvokableMethods(entityType);
            }

            @Override
            public List<String> expandedFieldNames(TypeElement entityType) {
                return KoraProcessor.this.expandedFieldNames(entityType);
            }

            @Override
            public ReflectSpec reflectSpec(TypeElement entityType) {
                return reflectSpecs.get(entityType.getQualifiedName().toString());
            }

            @Override
            public boolean hasReflectSpec(TypeElement typeElement) {
                return reflectSpecs.containsKey(typeElement.getQualifiedName().toString());
            }

            @Override
            public TypeElement directSuperType(TypeElement entityType) {
                return KoraProcessor.this.directSuperType(entityType);
            }

            @Override
            public boolean isJavaLangObject(TypeElement typeElement) {
                return KoraProcessor.this.isJavaLangObject(typeElement);
            }

            @Override
            public void collectTypeConstants(Map<String, String> typeConstants, TypeMirror typeMirror) {
                KoraProcessor.this.collectTypeConstants(typeConstants, typeMirror);
            }

            @Override
            public void setActiveTypeConstants(Map<String, String> typeConstants) {
                activeTypeConstants = typeConstants;
            }

            @Override
            public String escapeJava(String text) {
                return KoraProcessor.this.escapeJava(text);
            }

            @Override
            public String buildFieldInfoEntry(TypeElement entityType, VariableElement field, boolean includeAnnotationMetadata) {
                return KoraProcessor.this.buildFieldInfoEntry(entityType, field, includeAnnotationMetadata);
            }

            @Override
            public String buildMethodInfoEntry(TypeElement entityType, ExecutableElement method, boolean includeAnnotationMetadata) {
                return KoraProcessor.this.buildMethodInfoEntry(entityType, method, includeAnnotationMetadata);
            }

            @Override
            public String renderNullableTypeLiteral(TypeMirror typeMirror) {
                return KoraProcessor.this.renderNullableTypeLiteral(typeMirror);
            }

            @Override
            public int modifierMask(Set<Modifier> modifiers) {
                return KoraProcessor.this.modifierMask(modifiers);
            }

            @Override
            public String renderAnnotationArray(List<? extends AnnotationMirror> annotations, boolean includeAnnotationMetadata) {
                return KoraProcessor.this.renderAnnotationArray(annotations, includeAnnotationMetadata);
            }

            @Override
            public String renderNewInstanceExpression(TypeElement typeElement) {
                return KoraProcessor.this.renderNewInstanceExpression(typeElement);
            }

            @Override
            public String buildInvokeCase(ExecutableElement method) {
                return KoraProcessor.this.buildInvokeCase(method);
            }

            @Override
            public String buildSetCase(TypeElement entityType, VariableElement field) {
                return KoraProcessor.this.buildSetCase(entityType, field);
            }

            @Override
            public String buildGetCase(TypeElement entityType, VariableElement field) {
                return KoraProcessor.this.buildGetCase(entityType, field);
            }

            @Override
            public String buildFieldPresenceSwitch(List<VariableElement> fields, String indent, String defaultExpression) {
                return KoraProcessor.this.buildFieldPresenceSwitch(fields, indent, defaultExpression);
            }

            @Override
            public String buildFieldInfoSwitch(List<VariableElement> fields, String indent, String defaultExpression, boolean useLazyMetadata) {
                return KoraProcessor.this.buildFieldInfoSwitch(fields, indent, defaultExpression, useLazyMetadata);
            }

            @Override
            public String buildExpandedFieldPresenceSwitch(TypeElement entityType, List<String> fieldNames, String indent, String defaultExpression) {
                return KoraProcessor.this.buildExpandedFieldPresenceSwitch(entityType, fieldNames, indent, defaultExpression);
            }

            @Override
            public String buildExpandedFieldInfoSwitch(TypeElement entityType, List<VariableElement> localFields, List<String> fieldNames, String indent, String defaultExpression, boolean useLazyMetadata) {
                return KoraProcessor.this.buildExpandedFieldInfoSwitch(entityType, localFields, fieldNames, indent, defaultExpression, useLazyMetadata);
            }

            @Override
            public String buildMethodPresenceSwitch(Map<String, List<ExecutableElement>> methodsByName, String indent, String defaultExpression) {
                return KoraProcessor.this.buildMethodPresenceSwitch(methodsByName, indent, defaultExpression);
            }

            @Override
            public String buildMethodInfoArraySwitch(Map<String, List<ExecutableElement>> methodsByName, String indent, String defaultExpression, boolean useLazyMetadata, boolean mergeInheritedMethods) {
                return KoraProcessor.this.buildMethodInfoArraySwitch(methodsByName, indent, defaultExpression, useLazyMetadata, mergeInheritedMethods);
            }
        });
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        collectReflectSpecs(roundEnv);
        collectMapperCapabilitySpecs(roundEnv);
        collectScanSpecs(roundEnv);
        collectMapperReflectSpecs(roundEnv);
        generateReflectors();
        generateMeta(roundEnv);
        if (!roundEnv.processingOver() && annotations.isEmpty()) {
            generateSupportsAndMappers(roundEnv);
        }
        return false;
    }

    private void collectReflectSpecs(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Reflect.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Reflect can only be used on classes", element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            Reflect reflect = typeElement.getAnnotation(Reflect.class);
            reflectSpecs.put(typeElement.getQualifiedName().toString(), new ReflectSpec(typeElement, reflect.suffix(), reflect.metadata(), reflect.annotationMetadata()));
        }
    }

    private void collectMapperCapabilitySpecs(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(MapperCapability.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@MapperCapability can only be used on classes", element);
                continue;
            }
            TypeElement implType = (TypeElement) element;
            TypeElement contractType = mapperCapabilityContractType(implType);
            if (contractType == null || contractType.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@MapperCapability value must be an interface", implType);
                continue;
            }
            mapperCapabilitySpecs.put(contractType.getQualifiedName().toString(), new MapperCapabilitySpec(contractType, implType));
        }
        registerBuiltinMapperCapability("com.nicleo.kora.core.mapper.BaseMapper", "com.nicleo.kora.core.mapper.BaseMapperImpl");
    }

    private void registerBuiltinMapperCapability(String contractTypeName, String implTypeName) {
        if (mapperCapabilitySpecs.containsKey(contractTypeName)) {
            return;
        }
        TypeElement contractType = elements.getTypeElement(contractTypeName);
        TypeElement implType = elements.getTypeElement(implTypeName);
        if (contractType == null || implType == null) {
            return;
        }
        mapperCapabilitySpecs.put(contractTypeName, new MapperCapabilitySpec(contractType, implType));
    }

    private void collectScanSpecs(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(KoraScan.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@KoraScan can only be used on classes", element);
                continue;
            }
            TypeElement configType = (TypeElement) element;
            String configQualifiedName = configType.getQualifiedName().toString();
            boolean exists = scanSpecs.stream().anyMatch(spec -> spec.configQualifiedName.equals(configQualifiedName));
            if (exists) {
                continue;
            }
            KoraScan scan = configType.getAnnotation(KoraScan.class);
            scanSpecs.add(new ScanSpec(configType, List.of(scan.xml()), List.of(scan.entity()), List.of(scan.mapper())));
        }
        for (ScanSpec scanSpec : scanSpecs) {
            for (Element rootElement : roundEnv.getRootElements()) {
                collectMapperTypes(rootElement, scanSpec.mapperPackages, scanSpec.mapperTypeNames);
            }
        }
    }

    private void generateReflectors() {
        for (ReflectSpec spec : reflectSpecs.values()) {
            if (!generatedReflectors.add(spec.typeElement.getQualifiedName().toString())) {
                continue;
            }
            try {
                writeReflectorClass(spec.typeElement, spec.suffix);
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate reflector: " + ex.getMessage(), spec.typeElement);
            }
        }
    }

    private void generateMeta(RoundEnvironment roundEnv) {
        for (ScanSpec scanSpec : scanSpecs) {
            for (Element rootElement : roundEnv.getRootElements()) {
                collectAndGenerateMeta(rootElement, scanSpec.entityPackages);
            }
        }
    }

    private void collectMapperReflectSpecs(RoundEnvironment roundEnv) {
        for (ScanSpec scanSpec : scanSpecs) {
            try {
                Map<String, MapperXmlDefinition> xmlDefinitions = loadMapperXmlDefinitions(scanSpec);
                for (MapperXmlDefinition xmlDefinition : xmlDefinitions.values()) {
                    TypeElement mapperType = elements.getTypeElement(xmlDefinition.getNamespace());
                    if (mapperType == null) {
                        throw new ProcessorException("Mapper interface not found for namespace: " + xmlDefinition.getNamespace());
                    }
                    collectMapperReflectSpecs(mapperType, xmlDefinition);
                }
                for (TypeElement mapperType : findMapperTypes(scanSpec)) {
                    registerMapperCapabilityReflectSpecs(mapperType);
                }
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to collect mapper reflect specs: " + ex.getMessage(), scanSpec.configType);
            } catch (ProcessorException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), scanSpec.configType);
            }
        }
    }

    private void collectMapperReflectSpecs(TypeElement mapperType, MapperXmlDefinition xmlDefinition) {
        for (MapperMethodSpec mapperMethod : mapperMethods(mapperType, xmlDefinition)) {
            registerMapperMethodReflectTypes(mapperMethod.method(), mapperMethod.statement());
        }
    }

    private void registerMapperCapabilityReflectSpecs(TypeElement mapperType) {
        if (!hasMapperCapabilityEntity(mapperType)) {
            return;
        }
        registerReflectType(mapperEntityType(mapperType), new HashSet<>());
    }

    private void collectAndGenerateMeta(Element element, List<String> entityPackages) {
        if (element.getKind().isClass() && element instanceof TypeElement typeElement) {
            if (isGeneratedType(typeElement)) {
                return;
            }
            String packageName = packageNameOf(typeElement);
            boolean matched = entityPackages.stream().anyMatch(pkg -> packageName.equals(pkg) || packageName.startsWith(pkg + "."));
            if (matched && generatedMeta.add(typeElement.getQualifiedName().toString())) {
                try {
                    writeMetaClass(typeElement);
                } catch (IOException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate meta class: " + ex.getMessage(), typeElement);
                }
            }
        }
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind().isClass()) {
                collectAndGenerateMeta(enclosed, entityPackages);
            }
        }
    }

    private List<TypeElement> findMapperTypes(ScanSpec scanSpec) {
        if (scanSpec.mapperTypeNames.isEmpty()) {
            return List.of();
        }
        List<TypeElement> mapperTypes = new ArrayList<>();
        for (String mapperTypeName : scanSpec.mapperTypeNames) {
            TypeElement mapperType = elements.getTypeElement(mapperTypeName);
            if (mapperType != null) {
                mapperTypes.add(mapperType);
            }
        }
        return mapperTypes;
    }

    private List<TypeElement> findEntityTypes(ScanSpec scanSpec, RoundEnvironment roundEnv) {
        List<TypeElement> entityTypes = new ArrayList<>();
        for (String qualifiedName : generatedMeta) {
            TypeElement typeElement = elements.getTypeElement(qualifiedName);
            if (typeElement != null && matchesPackage(typeElement, scanSpec.entityPackages)) {
                entityTypes.add(typeElement);
            }
        }
        return entityTypes;
    }

    private void collectMapperTypes(Element element, List<String> mapperPackages, Set<String> mapperTypeNames) {
        if (element instanceof TypeElement typeElement) {
            if (!isGeneratedType(typeElement)
                    && typeElement.getKind() == ElementKind.INTERFACE
                    && matchesPackage(typeElement, mapperPackages)) {
                mapperTypeNames.add(typeElement.getQualifiedName().toString());
            }
        }
        for (Element enclosed : element.getEnclosedElements()) {
            if (enclosed.getKind().isClass() || enclosed.getKind().isInterface()) {
                collectMapperTypes(enclosed, mapperPackages, mapperTypeNames);
            }
        }
    }

    private boolean matchesPackage(TypeElement typeElement, List<String> packages) {
        String packageName = packageNameOf(typeElement);
        return packages.stream().anyMatch(pkg -> packageName.equals(pkg) || packageName.startsWith(pkg + "."));
    }

    private boolean isGeneratedType(TypeElement typeElement) {
        String simpleName = typeElement.getSimpleName().toString();
        return simpleName.endsWith("Reflector")
                || simpleName.endsWith("Generated")
                || simpleName.endsWith("Impl")
                || simpleName.endsWith("Table");
    }

    private void generateSupportsAndMappers(RoundEnvironment roundEnv) {
        for (ScanSpec scanSpec : scanSpecs) {
            try {
                writeSupportClass(scanSpec, findEntityTypes(scanSpec, roundEnv));
                Map<String, MapperXmlDefinition> xmlDefinitions = loadMapperXmlDefinitions(scanSpec);
                for (MapperXmlDefinition xmlDefinition : xmlDefinitions.values()) {
                    TypeElement mapperType = elements.getTypeElement(xmlDefinition.getNamespace());
                    if (mapperType == null) {
                        throw new ProcessorException("Mapper interface not found for namespace: " + xmlDefinition.getNamespace());
                    }
                    if (!generatedMappers.add(xmlDefinition.getNamespace())) {
                        continue;
                    }
                    writeMapperImpl(mapperType, xmlDefinition, supportClassName(scanSpec));
                }
                for (TypeElement mapperType : findMapperTypes(scanSpec)) {
                    String mapperQualifiedName = mapperType.getQualifiedName().toString();
                    if (generatedMappers.contains(mapperQualifiedName) || xmlDefinitions.containsKey(mapperQualifiedName)) {
                        continue;
                    }
                    if (!hasMapperCapabilityEntity(mapperType)) {
                        continue;
                    }
                    if (hasDeclaredAbstractMethods(mapperType)) {
                        throw new ProcessorException("Mapper declares abstract methods but no xml found: " + mapperQualifiedName);
                    }
                    generatedMappers.add(mapperQualifiedName);
                    writeMapperImpl(mapperType, new MapperXmlDefinition(mapperQualifiedName, Map.of()), supportClassName(scanSpec));
                }
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process scan config: " + ex.getMessage(), scanSpec.configType);
            } catch (ProcessorException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), scanSpec.configType);
            }
        }
    }

    private Map<String, MapperXmlDefinition> loadMapperXmlDefinitions(ScanSpec scanSpec) throws IOException {
        return xmlLoader.load(scanSpec.xmlRoots);
    }

    private void writeMetaClass(TypeElement entityType) throws IOException {
        String packageName = queryPackageNameOf(entityType);
        String generatedSimpleName = entityType.getSimpleName() + "Table";
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        sourceWriter.write(qualifiedName, entityType, buildMetaSource(packageName, generatedSimpleName, entityType));
    }

    private void writeReflectorClass(TypeElement entityType, String suffix) throws IOException {
        String packageName = packageNameOf(entityType);
        String generatedSimpleName = entityType.getSimpleName() + suffix;
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        sourceWriter.write(qualifiedName, entityType, buildReflectorSource(packageName, generatedSimpleName, entityType));
    }

    private void writeSupportClass(ScanSpec scanSpec, List<TypeElement> entityTypes) throws IOException {
        String qualifiedName = supportClassName(scanSpec);
        if (!generatedSupports.add(qualifiedName)) {
            return;
        }
        sourceWriter.write(qualifiedName, scanSpec.configType, buildSupportSource(scanSpec, entityTypes));
    }

    private void writeMapperImpl(TypeElement mapperType, MapperXmlDefinition xmlDefinition, String supportClassName) throws IOException {
        String packageName = packageNameOf(mapperType);
        String generatedSimpleName = mapperType.getSimpleName() + "Impl";
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        sourceWriter.write(qualifiedName, mapperType, buildMapperSource(packageName, generatedSimpleName, mapperType, xmlDefinition, supportClassName));
    }

    private String buildMetaSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        return entityTableSourceGenerator.buildMetaSource(packageName, generatedSimpleName, entityType);
    }

    private String buildReflectorSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        String generated = reflectorSourceGenerator.buildReflectorSource(packageName, generatedSimpleName, entityType);
        this.activeTypeConstants = Map.of();
        return generated;
    }

    private String buildFieldPresenceSwitch(List<VariableElement> fields, String indent, String defaultExpression) {
        StringBuilder source = new StringBuilder();
        for (VariableElement field : fields) {
            source.append(indent).append("case \"").append(field.getSimpleName()).append("\" -> true;\n");
        }
        source.append(indent).append("default -> ").append(defaultExpression).append(";\n");
        return source.toString();
    }

    private String buildExpandedFieldPresenceSwitch(TypeElement entityType, List<String> fieldNames, String indent, String defaultExpression) {
        StringBuilder source = new StringBuilder();
        for (String fieldName : fieldNames) {
            source.append(indent).append("case \"").append(fieldName).append("\" -> true;\n");
        }
        source.append(indent).append("default -> ").append(defaultExpression).append(";\n");
        return source.toString();
    }

    private String buildFieldInfoSwitch(List<VariableElement> fields, String indent, String defaultExpression, boolean useLazyMetadata) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            source.append(indent).append("case \"").append(fields.get(i).getSimpleName()).append("\" -> ")
                    .append(useLazyMetadata ? "initField(" + i + ")" : "FIELDS[" + i + "]")
                    .append(";\n");
        }
        source.append(indent).append("default -> ").append(defaultExpression).append(";\n");
        return source.toString();
    }

    private String buildExpandedFieldInfoSwitch(TypeElement entityType, List<VariableElement> localFields, List<String> fieldNames, String indent, String defaultExpression, boolean useLazyMetadata) {
        Map<String, Integer> localIndexes = new LinkedHashMap<>();
        for (int i = 0; i < localFields.size(); i++) {
            localIndexes.put(localFields.get(i).getSimpleName().toString(), i);
        }
        StringBuilder source = new StringBuilder();
        for (String fieldName : fieldNames) {
            Integer localIndex = localIndexes.get(fieldName);
            if (localIndex != null) {
                source.append(indent).append("case \"").append(fieldName).append("\" -> ")
                        .append(useLazyMetadata ? "initField(" + localIndex + ")" : "FIELDS[" + localIndex + "]")
                        .append(";\n");
            } else {
                source.append(indent).append("case \"").append(fieldName).append("\" -> parentReflector().getField(\"")
                        .append(fieldName).append("\");\n");
            }
        }
        source.append(indent).append("default -> ").append(defaultExpression).append(";\n");
        return source.toString();
    }

    private List<String> expandedFieldNames(TypeElement entityType) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (VariableElement field : collectInstanceFields(entityType)) {
            names.add(field.getSimpleName().toString());
        }
        TypeElement superType = directSuperType(entityType);
        if (superType != null && !isJavaLangObject(superType) && reflectSpecs.containsKey(superType.getQualifiedName().toString())) {
            names.addAll(expandedFieldNames(superType));
        }
        return new ArrayList<>(names);
    }

    private String buildMethodPresenceSwitch(Map<String, List<ExecutableElement>> methodsByName, String indent, String defaultExpression) {
        StringBuilder source = new StringBuilder();
        for (String methodName : methodsByName.keySet()) {
            source.append(indent).append("case \"").append(methodName).append("\" -> true;\n");
        }
        source.append(indent).append("default -> ").append(defaultExpression).append(";\n");
        return source.toString();
    }

    private String buildMethodInfoArraySwitch(Map<String, List<ExecutableElement>> methodsByName, String indent, String defaultExpression, boolean useLazyMetadata, boolean mergeInheritedMethods) {
        StringBuilder source = new StringBuilder();
        int index = 0;
        for (String methodName : methodsByName.keySet()) {
            int currentIndex = index++;
            String localExpression = useLazyMetadata ? "initMethods(" + currentIndex + ")" : "METHODS[" + currentIndex + "]";
            source.append(indent).append("case \"").append(methodName).append("\" -> ")
                    .append(mergeInheritedMethods
                            ? "mergeMethodInfoArrays(" + localExpression + ", parentReflector().getMethod(name))"
                            : localExpression)
                    .append(";\n");
        }
        source.append(indent).append("default -> ").append(defaultExpression).append(";\n");
        return source.toString();
    }

    private String buildFieldInfoEntry(TypeElement entityType, VariableElement field, boolean includeAnnotationMetadata) {
        String fieldName = field.getSimpleName().toString();
        return "        new FieldInfo(\"" + fieldName + "\", "
                + renderTypeLiteral(field.asType()) + ", "
                + modifierMask(field.getModifiers()) + ", "
                + renderFieldAlias(field) + ", "
                + renderFieldAnnotationArray(field, includeAnnotationMetadata) + ")";
    }

    private String buildMethodInfoEntry(TypeElement entityType, ExecutableElement method, boolean includeAnnotationMetadata) {
        String methodName = method.getSimpleName().toString();
        return "new MethodInfo(\"" + methodName + "\", "
                + renderTypeLiteral(method.getReturnType()) + ", "
                + modifierMask(method.getModifiers()) + ", "
                + renderParameterInfoArray(method, includeAnnotationMetadata) + ", "
                + renderAnnotationArray(method.getAnnotationMirrors(), includeAnnotationMetadata) + ")";
    }

    private String renderParameterInfoArray(ExecutableElement method, boolean includeAnnotationMetadata) {
        List<? extends VariableElement> parameters = method.getParameters();
        if (parameters.isEmpty()) {
            return "NO_PARAMS";
        }
        if (parameters.size() == 1) {
            VariableElement parameter = parameters.getFirst();
            String annotations = renderAnnotationArray(parameter.getAnnotationMirrors(), includeAnnotationMetadata);
            if ("NO_ANNOTATIONS".equals(annotations)) {
                return "parameterArray(parameter(\"" + parameter.getSimpleName() + "\", " + renderTypeLiteral(parameter.asType()) + "))";
            }
        }
        StringBuilder builder = new StringBuilder("new ParameterInfo[]{");
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            VariableElement parameter = parameters.get(i);
            builder.append("new ParameterInfo(\"")
                    .append(parameter.getSimpleName()).append("\", ")
                    .append(renderTypeLiteral(parameter.asType())).append(", ")
                    .append(renderAnnotationArray(parameter.getAnnotationMirrors(), includeAnnotationMetadata)).append(")");
        }
        builder.append('}');
        return builder.toString();
    }

    private String renderTypeLiteral(TypeMirror typeMirror) {
        if (typeMirror == null) {
            return "null";
        }
        return switch (typeMirror.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> classLiteral(typeMirror);
            case ARRAY -> renderArrayTypeLiteral((ArrayType) typeMirror);
            case TYPEVAR -> renderTypeVariableLiteral((javax.lang.model.type.TypeVariable) typeMirror);
            case WILDCARD -> renderWildcardTypeLiteral((WildcardType) typeMirror);
            case DECLARED -> renderDeclaredTypeLiteral((DeclaredType) typeMirror);
            default -> classLiteral(typeMirror);
        };
    }

    private String classLiteral(TypeMirror typeMirror) {
        TypeMirror erasedType = types.erasure(typeMirror);
        return switch (erasedType.getKind()) {
            case BOOLEAN -> "boolean.class";
            case BYTE -> "byte.class";
            case SHORT -> "short.class";
            case INT -> "int.class";
            case LONG -> "long.class";
            case CHAR -> "char.class";
            case FLOAT -> "float.class";
            case DOUBLE -> "double.class";
            case ARRAY -> erasedType + ".class";
            case TYPEVAR -> "java.lang.Object.class";
            default -> erasedType + ".class";
        };
    }

    private String renderArrayTypeLiteral(ArrayType arrayType) {
        TypeMirror componentType = arrayType.getComponentType();
        if (isReifiable(componentType)) {
            return classLiteral(arrayType);
        }
        return internTypeLiteral("RuntimeTypes.array(" + renderTypeLiteral(componentType) + ")");
    }

    private String renderDeclaredTypeLiteral(DeclaredType declaredType) {
        if (declaredType.getTypeArguments().isEmpty()) {
            return classLiteral(declaredType);
        }
        String ownerType = declaredType.getEnclosingType() == null || declaredType.getEnclosingType().getKind() == TypeKind.NONE
                ? "null"
                : renderTypeLiteral(declaredType.getEnclosingType());
        String actualTypes = declaredType.getTypeArguments().stream()
                .map(this::renderTypeLiteral)
                .collect(Collectors.joining(", "));
        return internTypeLiteral("RuntimeTypes.parameterized(" + classLiteral(declaredType) + ", " + ownerType
                + (actualTypes.isEmpty() ? "" : ", " + actualTypes) + ")");
    }

    private String renderTypeVariableLiteral(javax.lang.model.type.TypeVariable typeVariable) {
        TypeMirror upperBound = typeVariable.getUpperBound();
        if (upperBound != null && upperBound.getKind() != TypeKind.NULL && upperBound.getKind() != TypeKind.NONE) {
            return internTypeLiteral("RuntimeTypes.typeVariable(\"" + escapeJava(typeVariable.asElement().getSimpleName().toString()) + "\", "
                    + renderTypeLiteral(upperBound) + ")");
        }
        return internTypeLiteral("RuntimeTypes.typeVariable(\"" + escapeJava(typeVariable.asElement().getSimpleName().toString()) + "\", Object.class)");
    }

    private String renderWildcardTypeLiteral(WildcardType wildcardType) {
        String upperBounds = wildcardType.getExtendsBound() == null
                ? "new Type[]{Object.class}"
                : "new Type[]{" + renderTypeLiteral(wildcardType.getExtendsBound()) + "}";
        String lowerBounds = wildcardType.getSuperBound() == null
                ? "new Type[0]"
                : "new Type[]{" + renderTypeLiteral(wildcardType.getSuperBound()) + "}";
        return internTypeLiteral("RuntimeTypes.wildcard(" + upperBounds + ", " + lowerBounds + ")");
    }

    private void collectTypeConstants(Map<String, String> typeConstants, TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind() == TypeKind.NONE) {
            return;
        }
        switch (typeMirror.getKind()) {
            case ARRAY -> {
                ArrayType arrayType = (ArrayType) typeMirror;
                if (!isReifiable(arrayType.getComponentType())) {
                    typeConstants.putIfAbsent("RuntimeTypes.array(" + renderTypeLiteral(arrayType.getComponentType()) + ")", "");
                }
                collectTypeConstants(typeConstants, arrayType.getComponentType());
            }
            case DECLARED -> {
                DeclaredType declaredType = (DeclaredType) typeMirror;
                for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                    collectTypeConstants(typeConstants, typeArgument);
                }
                if (!declaredType.getTypeArguments().isEmpty()) {
                    String ownerType = declaredType.getEnclosingType() == null || declaredType.getEnclosingType().getKind() == TypeKind.NONE
                            ? "null"
                            : renderTypeLiteral(declaredType.getEnclosingType());
                    String actualTypes = declaredType.getTypeArguments().stream()
                            .map(this::renderTypeLiteral)
                            .collect(Collectors.joining(", "));
                    typeConstants.putIfAbsent("RuntimeTypes.parameterized(" + classLiteral(declaredType) + ", " + ownerType
                            + (actualTypes.isEmpty() ? "" : ", " + actualTypes) + ")", "");
                }
            }
            case TYPEVAR -> {
                javax.lang.model.type.TypeVariable typeVariable = (javax.lang.model.type.TypeVariable) typeMirror;
                TypeMirror upperBound = typeVariable.getUpperBound();
                if (upperBound != null && upperBound.getKind() != TypeKind.NULL && upperBound.getKind() != TypeKind.NONE) {
                    collectTypeConstants(typeConstants, upperBound);
                    typeConstants.putIfAbsent("RuntimeTypes.typeVariable(\"" + escapeJava(typeVariable.asElement().getSimpleName().toString()) + "\", "
                            + renderTypeLiteral(upperBound) + ")", "");
                } else {
                    typeConstants.putIfAbsent("RuntimeTypes.typeVariable(\"" + escapeJava(typeVariable.asElement().getSimpleName().toString()) + "\", Object.class)", "");
                }
            }
            case WILDCARD -> {
                WildcardType wildcardType = (WildcardType) typeMirror;
                if (wildcardType.getExtendsBound() != null) {
                    collectTypeConstants(typeConstants, wildcardType.getExtendsBound());
                }
                if (wildcardType.getSuperBound() != null) {
                    collectTypeConstants(typeConstants, wildcardType.getSuperBound());
                }
                String upperBounds = wildcardType.getExtendsBound() == null
                        ? "new Type[]{Object.class}"
                        : "new Type[]{" + renderTypeLiteral(wildcardType.getExtendsBound()) + "}";
                String lowerBounds = wildcardType.getSuperBound() == null
                        ? "new Type[0]"
                        : "new Type[]{" + renderTypeLiteral(wildcardType.getSuperBound()) + "}";
                typeConstants.putIfAbsent("RuntimeTypes.wildcard(" + upperBounds + ", " + lowerBounds + ")", "");
            }
            default -> {
            }
        }
    }

    private String internTypeLiteral(String initializer) {
        return this.activeTypeConstants.getOrDefault(initializer, initializer);
    }

    private boolean isReifiable(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> true;
            case ARRAY -> isReifiable(((ArrayType) typeMirror).getComponentType());
            case DECLARED -> typeMirror instanceof DeclaredType declaredType && declaredType.getTypeArguments().isEmpty();
            default -> false;
        };
    }

    private int modifierMask(Set<Modifier> modifiers) {
        int value = 0;
        if (modifiers.contains(Modifier.PUBLIC)) value |= java.lang.reflect.Modifier.PUBLIC;
        if (modifiers.contains(Modifier.PROTECTED)) value |= java.lang.reflect.Modifier.PROTECTED;
        if (modifiers.contains(Modifier.PRIVATE)) value |= java.lang.reflect.Modifier.PRIVATE;
        if (modifiers.contains(Modifier.ABSTRACT)) value |= java.lang.reflect.Modifier.ABSTRACT;
        if (modifiers.contains(Modifier.STATIC)) value |= java.lang.reflect.Modifier.STATIC;
        if (modifiers.contains(Modifier.FINAL)) value |= java.lang.reflect.Modifier.FINAL;
        if (modifiers.contains(Modifier.TRANSIENT)) value |= java.lang.reflect.Modifier.TRANSIENT;
        if (modifiers.contains(Modifier.VOLATILE)) value |= java.lang.reflect.Modifier.VOLATILE;
        if (modifiers.contains(Modifier.SYNCHRONIZED)) value |= java.lang.reflect.Modifier.SYNCHRONIZED;
        if (modifiers.contains(Modifier.NATIVE)) value |= java.lang.reflect.Modifier.NATIVE;
        if (modifiers.contains(Modifier.STRICTFP)) value |= java.lang.reflect.Modifier.STRICT;
        return value;
    }

    private String renderAnnotationArray(List<? extends AnnotationMirror> annotations, boolean includeAnnotationMetadata) {
        List<? extends AnnotationMirror> includedAnnotations = annotations.stream()
                .filter(annotation -> includeAnnotationMetadata && isRuntimeVisibleAnnotation(annotation))
                .toList();
        if (includedAnnotations.isEmpty()) {
            return "NO_ANNOTATIONS";
        }
        StringBuilder builder = new StringBuilder("new java.lang.annotation.Annotation[]{");
        for (int i = 0; i < includedAnnotations.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(renderAnnotationLiteral(includedAnnotations.get(i)));
        }
        builder.append('}');
        return builder.toString();
    }

    private String renderFieldAnnotationArray(VariableElement field, boolean includeAnnotationMetadata) {
        List<? extends AnnotationMirror> includedAnnotations = field.getAnnotationMirrors().stream()
                .filter(annotation -> includeAnnotationMetadata && isRuntimeVisibleAnnotation(annotation))
                .toList();
        if (includedAnnotations.isEmpty()) {
            return "NO_ANNOTATIONS";
        }
        return renderAnnotationArray(includedAnnotations, true);
    }

    private String renderFieldAlias(VariableElement field) {
        String alias = aliasValue(field);
        return alias == null ? "null" : "\"" + escapeJava(alias) + "\"";
    }

    private boolean isRuntimeVisibleAnnotation(AnnotationMirror annotationMirror) {
        Element element = annotationMirror.getAnnotationType().asElement();
        if (!(element instanceof TypeElement annotationType)) {
            return false;
        }
        for (AnnotationMirror meta : annotationType.getAnnotationMirrors()) {
            if (!meta.getAnnotationType().toString().equals("java.lang.annotation.Retention")) {
                continue;
            }
            for (AnnotationValue value : meta.getElementValues().values()) {
                Object raw = value.getValue();
                if (raw instanceof VariableElement retentionValue) {
                    return retentionValue.getSimpleName().contentEquals(RetentionPolicy.RUNTIME.name());
                }
            }
        }
        return false;
    }

    private String renderAnnotationLiteral(AnnotationMirror annotationMirror) {
        TypeElement annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
        String annotationTypeName = annotationType.getQualifiedName().toString();
        StringBuilder builder = new StringBuilder("new ").append(annotationTypeName).append("() {\n");
        builder.append("            @Override\n")
                .append("            public Class<? extends java.lang.annotation.Annotation> annotationType() {\n")
                .append("                return ").append(annotationTypeName).append(".class;\n")
                .append("            }\n");
        for (Element enclosed : annotationType.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosed;
            AnnotationValue value = annotationValue(annotationMirror, method);
            builder.append("            @Override\n")
                    .append("            public ").append(method.getReturnType()).append(' ')
                    .append(method.getSimpleName()).append("() {\n")
                    .append("                return ").append(renderAnnotationValue(value, method.getReturnType())).append(";\n")
                    .append("            }\n");
        }
        builder.append("        }");
        return builder.toString();
    }

    private AnnotationValue annotationValue(AnnotationMirror annotationMirror, ExecutableElement method) {
        AnnotationValue value = annotationMirror.getElementValues().get(method);
        if (value != null) {
            return value;
        }
        AnnotationValue defaultValue = method.getDefaultValue();
        if (defaultValue == null) {
            throw new ProcessorException("Missing annotation value for " + method.getSimpleName() + " on " + annotationMirror);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private String renderAnnotationValue(AnnotationValue value, TypeMirror expectedType) {
        Object raw = value.getValue();
        return switch (expectedType.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> String.valueOf(raw);
            case ARRAY -> renderAnnotationArrayValue((List<? extends AnnotationValue>) raw, (ArrayType) expectedType);
            case DECLARED -> renderDeclaredAnnotationValue(raw, expectedType);
            default -> raw == null ? "null" : String.valueOf(raw);
        };
    }

    private String renderAnnotationArrayValue(List<? extends AnnotationValue> values, ArrayType arrayType) {
        StringBuilder builder = new StringBuilder("new ").append(classLiteral(arrayType.getComponentType()).replace(".class", "")).append("[]{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(renderAnnotationValue(values.get(i), arrayType.getComponentType()));
        }
        builder.append('}');
        return builder.toString();
    }

    private String renderDeclaredAnnotationValue(Object raw, TypeMirror expectedType) {
        if (raw instanceof String stringValue) {
            return "\"" + escapeJava(stringValue) + "\"";
        }
        if (raw instanceof TypeMirror typeMirror) {
            return classLiteral(typeMirror);
        }
        if (raw instanceof VariableElement enumConstant) {
            TypeElement owner = (TypeElement) enumConstant.getEnclosingElement();
            return owner.getQualifiedName() + "." + enumConstant.getSimpleName();
        }
        if (raw instanceof AnnotationMirror annotationMirror) {
            return renderAnnotationLiteral(annotationMirror);
        }
        return types.erasure(expectedType) + ".class.cast(" + renderObjectLiteral(raw) + ")";
    }

    private String renderObjectLiteral(Object raw) {
        if (raw instanceof String stringValue) {
            return "\"" + escapeJava(stringValue) + "\"";
        }
        if (raw instanceof Character charValue) {
            return "'" + escapeJava(String.valueOf(charValue)) + "'";
        }
        if (raw instanceof Long) {
            return raw + "L";
        }
        if (raw instanceof Float) {
            return raw + "F";
        }
        if (raw instanceof Double) {
            return raw + "D";
        }
        return String.valueOf(raw);
    }

    private String buildSupportSource(ScanSpec scanSpec, List<TypeElement> entityTypes) {
        return mapperSourceGenerator.buildSupportSource(scanSpec, new ArrayList<>(reflectSpecs.values()), entityTypes);
    }

    private String buildMapperSource(String packageName, String generatedSimpleName, TypeElement mapperType, MapperXmlDefinition xmlDefinition, String supportClassName) {
        List<MapperMethodSpec> mapperMethods = mapperMethods(mapperType, xmlDefinition);
        return mapperSourceGenerator.buildMapperSource(
                packageName,
                generatedSimpleName,
                mapperType,
                mapperMethods,
                mapperCapabilityDelegates(mapperType, mapperMethods),
                supportClassName
        );
    }

    private String renderExecution(TypeElement mapperType, ExecutableElement method, SqlNodeDefinition statement) {
        SqlCommandType commandType = statement.commandType();
        TypeMirror returnType = method.getReturnType();
        if (commandType == SqlCommandType.SELECT) {
            if (isPageReturn(returnType)) {
                TypeMirror elementType = extractPageElementType(returnType);
                ensureReflectorIfNeeded(elementType);
                String pagingName = pagingParameterName(method);
                if (pagingName == null) {
                    throw new ProcessorException("Page<T> select method must declare a Paging parameter: " + mapperType.getQualifiedName() + "." + method.getSimpleName());
                }
                return "        return " + renderTypeCast(returnType,
                        "sqlSession.getSqlPagingSupport().page(sqlSession, context, sql, args, " + pagingName + ", " + classLiteral(elementType) + ")") + ";\n";
            }
            if (isListReturn(returnType)) {
                TypeMirror elementType = extractListElementType(returnType);
                ensureReflectorIfNeeded(elementType);
                return "        return " + renderTypeCast(returnType,
                        "sqlSession.selectList(sql, args, context, " + classLiteral(elementType) + ")") + ";\n";
            }
            if (returnType.getKind() == TypeKind.VOID) {
                throw new ProcessorException("Select method cannot return void: " + mapperType.getQualifiedName() + "." + method.getSimpleName());
            }
            ensureReflectorIfNeeded(returnType);
            return "        return " + renderTypeCast(returnType,
                    "sqlSession.selectOne(sql, args, context, " + classLiteral(returnType) + ")") + ";\n";
        }
        if (returnType.getKind() == TypeKind.INT) {
            return "        return sqlSession.update(sql, args, context);\n";
        }
        if (returnType.getKind() == TypeKind.VOID) {
            return "        sqlSession.update(sql, args, context);\n        return;\n";
        }
        throw new ProcessorException("Non-select method must return int or void: " + mapperType.getQualifiedName() + "." + method.getSimpleName());
    }

    private String renderResultTypeLiteral(ExecutableElement method, SqlNodeDefinition statement) {
        if (statement.commandType() != SqlCommandType.SELECT) {
            return "null";
        }
        TypeMirror returnType = method.getReturnType();
        if (isPageReturn(returnType)) {
            return classLiteral(extractPageElementType(returnType));
        }
        if (isListReturn(returnType)) {
            return classLiteral(extractListElementType(returnType));
        }
        return classLiteral(returnType);
    }

    private boolean isListReturn(TypeMirror returnType) {
        if (!(returnType instanceof DeclaredType declaredType)) {
            return false;
        }
        Element element = declaredType.asElement();
        return element instanceof TypeElement typeElement
                && typeElement.getQualifiedName().contentEquals(LIST_TYPE)
                && declaredType.getTypeArguments().size() == 1;
    }

    private boolean isPageReturn(TypeMirror returnType) {
        if (!(returnType instanceof DeclaredType declaredType)) {
            return false;
        }
        Element element = declaredType.asElement();
        return element instanceof TypeElement typeElement
                && typeElement.getQualifiedName().contentEquals(PAGE_TYPE)
                && declaredType.getTypeArguments().size() == 1;
    }

    private TypeMirror extractListElementType(TypeMirror returnType) {
        DeclaredType declaredType = (DeclaredType) returnType;
        return declaredType.getTypeArguments().get(0);
    }

    private TypeMirror extractPageElementType(TypeMirror returnType) {
        DeclaredType declaredType = (DeclaredType) returnType;
        return declaredType.getTypeArguments().get(0);
    }

    private void registerMapperMethodReflectTypes(ExecutableElement method, SqlNodeDefinition statement) {
        if (statement.commandType() == SqlCommandType.SELECT) {
            TypeMirror returnType = method.getReturnType();
            if (isPageReturn(returnType)) {
                TypeMirror elementType = ((DeclaredType) returnType).getTypeArguments().getFirst();
                validateMapperTypeSupport(elementType, mapperTypeContext(method, "return"));
                registerReflectTypes(elementType);
            } else if (isListReturn(returnType)) {
                TypeMirror elementType = ((DeclaredType) returnType).getTypeArguments().getFirst();
                validateMapperTypeSupport(elementType, mapperTypeContext(method, "return"));
                registerReflectTypes(elementType);
            } else if (returnType.getKind() != TypeKind.VOID && !returnType.getKind().isPrimitive()) {
                validateMapperTypeSupport(returnType, mapperTypeContext(method, "return"));
                registerReflectTypes(returnType);
            }
        }
        for (VariableElement parameter : method.getParameters()) {
            validateMapperTypeSupport(parameter.asType(), mapperTypeContext(method, "parameter '" + parameter.getSimpleName() + "'"));
            registerReflectTypes(parameter.asType());
        }
    }

    private void registerReflectTypes(TypeMirror typeMirror) {
        registerReflectTypes(typeMirror, new HashSet<>());
    }

    private void registerReflectTypes(TypeMirror typeMirror, Set<String> visiting) {
        if (typeMirror == null) {
            return;
        }
        if (typeMirror instanceof DeclaredType declaredType) {
            TypeElement typeElement = asTypeElement(typeMirror);
            if (typeElement != null && shouldAutoReflect(typeElement)) {
                registerReflectType(typeElement, visiting);
            }
            for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                registerReflectTypes(typeArgument, visiting);
            }
            return;
        }
        if (typeMirror instanceof ArrayType arrayType) {
            registerReflectTypes(arrayType.getComponentType(), visiting);
            return;
        }
        if (typeMirror instanceof WildcardType wildcardType) {
            registerReflectTypes(wildcardType.getExtendsBound(), visiting);
            registerReflectTypes(wildcardType.getSuperBound(), visiting);
        }
    }

    private void registerReflectType(TypeElement typeElement, Set<String> visiting) {
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (!visiting.add(qualifiedName)) {
            return;
        }
        reflectSpecs.putIfAbsent(qualifiedName, new ReflectSpec(typeElement, "Reflector", ReflectMetadataLevel.BASIC, false));
        TypeElement superType = directSuperType(typeElement);
        if (superType != null && !isJavaLangObject(superType) && shouldAutoReflect(superType)) {
            registerReflectType(superType, visiting);
        }
        for (VariableElement field : collectInstanceFields(typeElement)) {
            registerReflectTypes(field.asType(), visiting);
        }
        visiting.remove(qualifiedName);
    }

    private boolean shouldAutoReflect(TypeElement typeElement) {
        if (typeElement == null || typeElement.getKind() != ElementKind.CLASS || isGeneratedType(typeElement)) {
            return false;
        }
        String packageName = packageNameOf(typeElement);
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jakarta.");
    }

    private void ensureReflectorIfNeeded(TypeMirror typeMirror) {
        TypeElement typeElement = asTypeElement(typeMirror);
        if (typeElement != null && shouldAutoReflect(typeElement)) {
            reflectSpecFor(typeElement);
        }
    }

    private void validateMapperTypeSupport(TypeMirror typeMirror, String usage) {
        if (typeMirror == null || typeMirror.getKind() == TypeKind.VOID || typeMirror.getKind().isPrimitive()) {
            return;
        }
        if (typeMirror instanceof ArrayType arrayType) {
            validateMapperTypeSupport(arrayType.getComponentType(), usage);
            return;
        }
        if (typeMirror instanceof WildcardType wildcardType) {
            validateMapperTypeSupport(wildcardType.getExtendsBound(), usage);
            validateMapperTypeSupport(wildcardType.getSuperBound(), usage);
            return;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return;
        }
        TypeElement typeElement = asTypeElement(typeMirror);
        if (typeElement == null) {
            return;
        }
        if (isSupportedMapperJavaType(typeElement, declaredType)) {
            for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                validateMapperTypeSupport(typeArgument, usage);
            }
            return;
        }
        String packageName = packageNameOf(typeElement);
        if (packageName.startsWith("java.") || packageName.startsWith("javax.") || packageName.startsWith("jakarta.")) {
            throw new ProcessorException("Unsupported mapper " + usage + " type: " + typeElement.getQualifiedName());
        }
    }

    private boolean isSupportedMapperJavaType(TypeElement typeElement, DeclaredType declaredType) {
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (qualifiedName.equals("java.lang.String")
                || qualifiedName.equals("java.lang.Boolean")
                || qualifiedName.equals("java.lang.Byte")
                || qualifiedName.equals("java.lang.Short")
                || qualifiedName.equals("java.lang.Integer")
                || qualifiedName.equals("java.lang.Long")
                || qualifiedName.equals("java.lang.Float")
                || qualifiedName.equals("java.lang.Double")
                || qualifiedName.equals("java.lang.Character")
                || qualifiedName.equals("java.lang.Object")
                || qualifiedName.equals("java.math.BigDecimal")
                || qualifiedName.equals("java.math.BigInteger")
                || qualifiedName.equals("java.util.UUID")
                || qualifiedName.equals("java.time.LocalDate")
                || qualifiedName.equals("java.time.LocalDateTime")
                || qualifiedName.equals("java.time.LocalTime")
                || qualifiedName.equals("java.time.Instant")
                || qualifiedName.equals("java.time.OffsetDateTime")
                || qualifiedName.equals("java.time.OffsetTime")
                || qualifiedName.equals("java.time.ZonedDateTime")) {
            return true;
        }
        TypeElement mapType = elements.getTypeElement("java.util.Map");
        if (mapType != null && types.isAssignable(types.erasure(declaredType), types.erasure(mapType.asType()))) {
            return true;
        }
        TypeElement collectionType = elements.getTypeElement("java.util.Collection");
        if (collectionType != null && types.isAssignable(types.erasure(declaredType), types.erasure(collectionType.asType()))) {
            return true;
        }
        return typeElement.getKind() == ElementKind.ENUM;
    }

    private String mapperTypeContext(ExecutableElement method, String position) {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        return position + " type for mapper method " + owner.getQualifiedName() + "." + method.getSimpleName();
    }

    private String renderTypeCast(TypeMirror targetType, String expression) {
        if (targetType instanceof DeclaredType declaredType && !declaredType.getTypeArguments().isEmpty()) {
            return "(" + targetType + ") (" + types.erasure(targetType) + ") " + expression;
        }
        return expression;
    }

    private TypeElement asTypeElement(TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind().isPrimitive()) {
            return null;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        Element element = declaredType.asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private List<MapperMethodSpec> mapperMethods(TypeElement mapperType, MapperXmlDefinition xmlDefinition) {
        List<MapperMethodSpec> methods = new ArrayList<>();
        for (Element enclosedElement : mapperType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            SqlNodeDefinition statement = xmlDefinition.getStatements().get(method.getSimpleName().toString());
            if (statement == null) {
                throw new ProcessorException("No xml statement found for method: " + method.getSimpleName());
            }
            methods.add(new MapperMethodSpec(method, statement));
        }
        return methods;
    }

    private List<MapperCapabilityDelegateSpec> mapperCapabilityDelegates(TypeElement mapperType, List<MapperMethodSpec> mapperMethods) {
        Map<String, MapperMethodSpec> mapperMethodSignatures = new LinkedHashMap<>();
        for (MapperMethodSpec mapperMethod : mapperMethods) {
            mapperMethodSignatures.put(methodSignature(mapperMethod.method()), mapperMethod);
        }
        List<MapperCapabilityDelegateSpec> delegates = new ArrayList<>();
        Set<String> delegatedContracts = new HashSet<>();
        Set<String> delegatedMethodSignatures = new HashSet<>(mapperMethodSignatures.keySet());
        Set<String> usedFieldNames = new HashSet<>();
        collectMapperCapabilityDelegates(mapperType.asType(), delegates, delegatedContracts, delegatedMethodSignatures, usedFieldNames);
        return delegates;
    }

    private void collectMapperCapabilityDelegates(TypeMirror typeMirror,
                                                  List<MapperCapabilityDelegateSpec> delegates,
                                                  Set<String> delegatedContracts,
                                                  Set<String> delegatedMethodSignatures,
                                                  Set<String> usedFieldNames) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return;
        }
        for (TypeMirror interfaceTypeMirror : types.directSupertypes(declaredType)) {
            if (!(interfaceTypeMirror instanceof DeclaredType interfaceType)) {
                continue;
            }
            TypeElement interfaceElement = asTypeElement(interfaceType);
            if (interfaceElement == null || interfaceElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            MapperCapabilitySpec capabilitySpec = mapperCapabilitySpecs.get(interfaceElement.getQualifiedName().toString());
            if (capabilitySpec != null && delegatedContracts.add(interfaceElement.getQualifiedName().toString())) {
                List<MapperCapabilityMethodSpec> methods = new ArrayList<>();
                for (Element enclosedElement : interfaceElement.getEnclosedElements()) {
                    if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                        continue;
                    }
                    ExecutableElement method = (ExecutableElement) enclosedElement;
                    if (method.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    }
                    String signature = methodSignature(method);
                    if (!delegatedMethodSignatures.add(signature)) {
                        continue;
                    }
                    ExecutableType resolvedMethodType = (ExecutableType) types.asMemberOf(interfaceType, method);
                    methods.add(new MapperCapabilityMethodSpec(
                            method.getSimpleName().toString(),
                            resolvedMethodType.getReturnType().toString(),
                            resolvedMethodType.getParameterTypes().stream().map(TypeMirror::toString).toList(),
                            method.getParameters().stream().map(parameter -> parameter.getSimpleName().toString()).toList()
                    ));
                }
                if (!methods.isEmpty()) {
                    String fieldName = uniqueCapabilityFieldName(interfaceElement.getSimpleName().toString(), usedFieldNames);
                    delegates.add(new MapperCapabilityDelegateSpec(
                            fieldName,
                            interfaceType.toString(),
                            renderCapabilityInstantiation(capabilitySpec.implType(), interfaceType),
                            methods
                    ));
                }
            }
            collectMapperCapabilityDelegates(interfaceType, delegates, delegatedContracts, delegatedMethodSignatures, usedFieldNames);
        }
    }

    private TypeElement mapperEntityType(TypeElement mapperType) {
        Set<String> entityTypes = new LinkedHashSet<>();
        collectMapperEntityTypes(mapperType.asType(), entityTypes);
        if (entityTypes.isEmpty()) {
            TypeElement objectType = elements.getTypeElement(Object.class.getCanonicalName());
            if (objectType == null) {
                throw new ProcessorException("java.lang.Object type is unavailable");
            }
            return objectType;
        }
        if (entityTypes.size() > 1) {
            throw new ProcessorException("Mapper capability entity type is ambiguous for mapper: " + mapperType.getQualifiedName() + " -> " + entityTypes);
        }
        TypeElement entityType = elements.getTypeElement(entityTypes.iterator().next());
        if (entityType == null) {
            throw new ProcessorException("Mapper entity type not found for mapper: " + mapperType.getQualifiedName());
        }
        return entityType;
    }

    private String mapperEntityTypeLiteral(TypeElement mapperType) {
        return mapperEntityType(mapperType).getQualifiedName().toString();
    }

    private boolean hasMapperCapabilityEntity(TypeElement mapperType) {
        Set<String> entityTypes = new LinkedHashSet<>();
        collectMapperEntityTypes(mapperType.asType(), entityTypes);
        return !entityTypes.isEmpty();
    }

    private void collectMapperEntityTypes(TypeMirror typeMirror, Set<String> entityTypes) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return;
        }
        for (TypeMirror interfaceTypeMirror : types.directSupertypes(declaredType)) {
            if (!(interfaceTypeMirror instanceof DeclaredType interfaceType)) {
                continue;
            }
            TypeElement interfaceElement = asTypeElement(interfaceType);
            if (interfaceElement == null || interfaceElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            if (mapperCapabilitySpecs.containsKey(interfaceElement.getQualifiedName().toString()) && !interfaceType.getTypeArguments().isEmpty()) {
                entityTypes.add(types.erasure(interfaceType.getTypeArguments().getFirst()).toString());
            }
            collectMapperEntityTypes(interfaceType, entityTypes);
        }
    }

    private boolean hasDeclaredAbstractMethods(TypeElement mapperType) {
        for (Element enclosedElement : mapperType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            if (!method.getModifiers().contains(Modifier.STATIC)) {
                return true;
            }
        }
        return false;
    }

    private String renderCapabilityInstantiation(TypeElement implType, DeclaredType interfaceType) {
        CapabilityConstructorMode constructorMode = capabilityConstructorMode(implType);
        String implTypeName = implType.getQualifiedName().toString();
        if (constructorMode == CapabilityConstructorMode.SQL_SESSION) {
            return "new " + implTypeName + "(sqlSession)";
        }
        if (interfaceType.getTypeArguments().isEmpty()) {
            throw new ProcessorException("Mapper capability " + interfaceType + " must declare an entity type argument");
        }
        TypeMirror entityType = interfaceType.getTypeArguments().getFirst();
        TypeElement entityTypeElement = asTypeElement(entityType);
        if (entityTypeElement == null) {
            throw new ProcessorException("Mapper capability entity type must be a class: " + interfaceType);
        }
        return "new " + implTypeName + "(sqlSession, " + tableConstantReference(entityTypeElement) + ")";
    }

    private CapabilityConstructorMode capabilityConstructorMode(TypeElement implType) {
        for (Element enclosedElement : implType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            List<? extends VariableElement> parameters = constructor.getParameters();
            if (parameters.size() == 1 && parameters.getFirst().asType().toString().equals(SQL_SESSION)) {
                return CapabilityConstructorMode.SQL_SESSION;
            }
            if (parameters.size() == 2
                    && parameters.getFirst().asType().toString().equals(SQL_SESSION)
                    && types.erasure(parameters.get(1).asType()).toString().equals("com.nicleo.kora.core.query.EntityTable")) {
                return CapabilityConstructorMode.SQL_SESSION_AND_ENTITY_TABLE;
            }
        }
        throw new ProcessorException("Mapper capability impl must declare constructor (SqlSession) or (SqlSession, EntityTable<?>): " + implType.getQualifiedName());
    }

    private String tableConstantReference(TypeElement entityType) {
        String queryPackage = queryPackageNameOf(entityType);
        String tableSimpleName = entityType.getSimpleName() + "Table";
        return queryPackage + "." + tableSimpleName + ".TABLE";
    }

    private TypeElement mapperCapabilityContractType(TypeElement implType) {
        for (AnnotationMirror annotationMirror : implType.getAnnotationMirrors()) {
            if (!annotationMirror.getAnnotationType().toString().equals(MapperCapability.class.getCanonicalName())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals("value") && entry.getValue().getValue() instanceof TypeMirror typeMirror) {
                    return asTypeElement(typeMirror);
                }
            }
        }
        return null;
    }

    private String methodSignature(ExecutableElement method) {
        StringBuilder signature = new StringBuilder(method.getSimpleName()).append('(');
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                signature.append(',');
            }
            signature.append(types.erasure(parameters.get(i).asType()));
        }
        signature.append(')');
        return signature.toString();
    }

    private String uniqueCapabilityFieldName(String simpleName, Set<String> usedFieldNames) {
        String baseName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        String candidate = baseName;
        int index = 2;
        while (!usedFieldNames.add(candidate)) {
            candidate = baseName + index++;
        }
        return candidate;
    }

    private boolean canInstantiate(TypeElement typeElement) {
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return false;
        }
        List<ExecutableElement> constructors = new ArrayList<>();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                constructors.add((ExecutableElement) enclosedElement);
            }
        }
        if (constructors.isEmpty()) {
            return true;
        }
        for (ExecutableElement constructor : constructors) {
            if (constructor.getParameters().isEmpty() && !constructor.getModifiers().contains(Modifier.PRIVATE)) {
                return true;
            }
        }
        return false;
    }

    private String renderNewInstanceExpression(TypeElement typeElement) {
        if (typeElement.getModifiers().contains(Modifier.ABSTRACT)) {
            return null;
        }
        List<ExecutableElement> constructors = new ArrayList<>();
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                constructors.add((ExecutableElement) enclosedElement);
            }
        }
        if (constructors.isEmpty()) {
            return "new " + typeElement.getQualifiedName() + "()";
        }
        ExecutableElement preferred = null;
        for (ExecutableElement constructor : constructors) {
            if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
                continue;
            }
            if (preferred == null || constructor.getParameters().size() < preferred.getParameters().size()) {
                preferred = constructor;
            }
            if (constructor.getParameters().isEmpty()) {
                preferred = constructor;
                break;
            }
        }
        if (preferred == null) {
            return null;
        }
        String arguments = preferred.getParameters().stream()
                .map(parameter -> renderDefaultValue(parameter.asType()))
                .collect(Collectors.joining(", "));
        return "new " + typeElement.getQualifiedName() + "(" + arguments + ")";
    }

    private String renderDefaultValue(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case BOOLEAN -> "false";
            case BYTE -> "(byte) 0";
            case SHORT -> "(short) 0";
            case INT -> "0";
            case LONG -> "0L";
            case CHAR -> "'\\0'";
            case FLOAT -> "0F";
            case DOUBLE -> "0D";
            default -> "null";
        };
    }

    private ReflectSpec reflectSpecFor(TypeElement typeElement) {
        if (typeElement == null) {
            throw new ProcessorException("Missing @Reflect type information");
        }
        ReflectSpec spec = reflectSpecs.get(typeElement.getQualifiedName().toString());
        if (spec == null) {
            throw new ProcessorException("No GeneratedReflector registered for type: " + typeElement.getQualifiedName());
        }
        return spec;
    }

    private ExecutableElement findMethod(TypeElement ownerElement, String methodName, int parameterCount) {
        for (Element enclosedElement : ownerElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getSimpleName().contentEquals(methodName)) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                if (method.getParameters().size() == parameterCount) {
                    return method;
                }
            }
        }
        return null;
    }

    private List<VariableElement> collectInstanceFields(TypeElement entityType) {
        List<VariableElement> fields = new ArrayList<>();
        for (Element enclosedElement : entityType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.FIELD && !enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                VariableElement field = (VariableElement) enclosedElement;
                if (!isIgnoredGeneratedField(field)) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private List<VariableElement> collectTableFields(TypeElement entityType) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        collectTableFields(entityType, fields);
        return new ArrayList<>(fields.values());
    }

    private VariableElement resolveIdField(TypeElement entityType, List<VariableElement> fields) {
        List<VariableElement> annotatedFields = fields.stream()
                .filter(field -> hasAnnotation(field, ID_ANNOTATION))
                .toList();
        if (annotatedFields.size() > 1) {
            throw new ProcessorException("Multiple @ID fields found on entity: " + entityType.getQualifiedName());
        }
        if (!annotatedFields.isEmpty()) {
            return annotatedFields.getFirst();
        }
        return fields.stream()
                .filter(field -> field.getSimpleName().contentEquals("id"))
                .findFirst()
                .orElse(null);
    }

    private IdGenerationSpec resolveIdGeneration(TypeElement entityType, VariableElement idField) {
        if (idField == null) {
            return new IdGenerationSpec("NONE", null);
        }
        AnnotationMirror idAnnotation = annotationMirror(idField, ID_ANNOTATION);
        if (idAnnotation == null) {
            return new IdGenerationSpec("NONE", null);
        }
        String strategy = annotationEnumValue(idAnnotation, "strategy");
        return switch (strategy) {
            case "NONE" -> new IdGenerationSpec("NONE", null);
            case "UUID" -> {
                validateUuidIdType(entityType, idField);
                yield new IdGenerationSpec("UUID", null);
            }
            case "CUSTOM" -> new IdGenerationSpec("CUSTOM", customIdGeneratorInstantiation(entityType, idField, idAnnotation));
            default -> throw new ProcessorException("Unsupported @ID strategy " + strategy + " on entity: " + entityType.getQualifiedName());
        };
    }

    private void validateUuidIdType(TypeElement entityType, VariableElement idField) {
        String typeName = renderRuntimeCastType(idField.asType());
        if (!typeName.equals(String.class.getCanonicalName()) && !typeName.equals("java.util.UUID")) {
            throw new ProcessorException("@ID(strategy = UUID) only supports String or UUID fields: "
                    + entityType.getQualifiedName() + "." + idField.getSimpleName());
        }
    }

    private String customIdGeneratorInstantiation(TypeElement entityType, VariableElement idField, AnnotationMirror idAnnotation) {
        TypeMirror generatorType = annotationClassValue(idAnnotation, "generator");
        if (generatorType == null || generatorType.toString().equals(ID_GENERATOR_TYPE)) {
            return null;
        }
        TypeElement generatorElement = (TypeElement) types.asElement(generatorType);
        if (generatorElement == null) {
            throw new ProcessorException("Invalid id generator type on entity: "
                    + entityType.getQualifiedName() + "." + idField.getSimpleName());
        }
        TypeElement idGeneratorType = elements.getTypeElement(ID_GENERATOR_TYPE);
        if (idGeneratorType == null || !types.isAssignable(types.erasure(generatorType), types.erasure(idGeneratorType.asType()))) {
            throw new ProcessorException("Id generator must implement " + ID_GENERATOR_TYPE + ": " + generatorType);
        }
        if (generatorElement.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new ProcessorException("Id generator must be concrete: " + generatorType);
        }
        List<ExecutableElement> constructors = generatorElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .toList();
        boolean hasAccessibleNoArgsConstructor = (constructors.isEmpty() && generatorElement.getModifiers().contains(Modifier.PUBLIC)) || constructors.stream()
                .anyMatch(constructor -> constructor.getParameters().isEmpty() && constructor.getModifiers().contains(Modifier.PUBLIC));
        if (!hasAccessibleNoArgsConstructor) {
            throw new ProcessorException("Id generator must declare a public no-arg constructor: " + generatorType);
        }
        return "new " + generatorType + "()";
    }

    private void collectTableFields(TypeElement entityType, Map<String, VariableElement> fields) {
        TypeElement superType = directSuperType(entityType);
        if (superType != null && !isJavaLangObject(superType)) {
            collectTableFields(superType, fields);
        }
        for (VariableElement field : collectInstanceFields(entityType)) {
            fields.putIfAbsent(field.getSimpleName().toString(), field);
        }
    }

    private String renderPagingArgument(ExecutableElement method) {
        String pagingName = pagingParameterName(method);
        return pagingName == null ? "null" : pagingName;
    }

    private String pagingParameterName(ExecutableElement method) {
        for (VariableElement parameter : method.getParameters()) {
            if (types.erasure(parameter.asType()).toString().equals(PAGING_TYPE)) {
                return parameter.getSimpleName().toString();
            }
        }
        return null;
    }

    private List<ExecutableElement> collectInvokableMethods(TypeElement entityType) {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element enclosedElement : entityType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD
                    && !enclosedElement.getModifiers().contains(Modifier.STATIC)
                    && !enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                if (!isIgnoredGeneratedMethod(method)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private boolean isIgnoredGeneratedField(VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        return fieldName.startsWith("$") || hasAnnotation(field, "lombok.Generated");
    }

    private boolean isIgnoredGeneratedMethod(ExecutableElement method) {
        if (isAccessorMethod(method)) {
            return false;
        }
        String methodName = method.getSimpleName().toString();
        if (methodName.startsWith("$")) {
            return true;
        }
        if (methodName.equals("canEqual") && method.getParameters().size() == 1) {
            return true;
        }
        if (methodName.equals("builder") && method.getParameters().isEmpty()) {
            return true;
        }
        if (methodName.equals("toBuilder") && method.getParameters().isEmpty()) {
            return true;
        }
        return hasAnnotation(method, "lombok.Generated") || isObjectDerivedMethod(method);
    }

    private boolean isAccessorMethod(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        int parameterCount = method.getParameters().size();
        if (parameterCount == 0) {
            return methodName.startsWith("get") && methodName.length() > 3
                    || methodName.startsWith("is") && methodName.length() > 2;
        }
        return parameterCount == 1 && methodName.startsWith("set") && methodName.length() > 3;
    }

    private boolean hasAnnotation(Element element, String annotationType) {
        return annotationMirror(element, annotationType) != null;
    }

    private AnnotationMirror annotationMirror(Element element, String annotationType) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotationType)) {
                return annotationMirror;
            }
        }
        return null;
    }

    private String annotationEnumValue(AnnotationMirror annotationMirror, String attribute) {
        ExecutableElement method = annotationMethod(annotationMirror, attribute);
        String value = annotationValue(annotationMirror, method).getValue().toString();
        int separator = value.lastIndexOf('.');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private TypeMirror annotationClassValue(AnnotationMirror annotationMirror, String attribute) {
        ExecutableElement method = annotationMethod(annotationMirror, attribute);
        Object value = annotationValue(annotationMirror, method).getValue();
        return value instanceof TypeMirror typeMirror ? typeMirror : null;
    }

    private ExecutableElement annotationMethod(AnnotationMirror annotationMirror, String attribute) {
        TypeElement annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
        for (Element enclosedElement : annotationType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getSimpleName().contentEquals(attribute)) {
                return (ExecutableElement) enclosedElement;
            }
        }
        throw new ProcessorException("Missing annotation attribute " + attribute + " on " + annotationMirror.getAnnotationType());
    }

    private boolean isObjectDerivedMethod(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        List<? extends VariableElement> parameters = method.getParameters();
        if (methodName.equals("toString") && parameters.isEmpty()) {
            return true;
        }
        if (methodName.equals("hashCode") && parameters.isEmpty()) {
            return true;
        }
        return methodName.equals("equals")
                && parameters.size() == 1
                && parameters.getFirst().asType().toString().equals(Object.class.getCanonicalName());
    }

    private TypeElement directSuperType(TypeElement entityType) {
        TypeMirror superType = entityType.getSuperclass();
        return superType == null || superType.getKind() == TypeKind.NONE ? null : asTypeElement(superType);
    }

    private boolean isJavaLangObject(TypeElement typeElement) {
        return typeElement != null && typeElement.getQualifiedName().contentEquals(Object.class.getCanonicalName());
    }

    private String buildInvokeCase(ExecutableElement method) {
        StringBuilder source = new StringBuilder();
        source.append("            case \"").append(method.getSimpleName()).append("\":\n");
        String call = "target." + method.getSimpleName() + "(" + renderInvokeArguments(method) + ")";
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            source.append("                ").append(call).append(";\n")
                    .append("                return null;\n");
        } else {
            source.append("                return ").append(call).append(";\n");
        }
        return source.toString();
    }

    private String renderInvokeArguments(ExecutableElement method) {
        StringBuilder source = new StringBuilder();
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                source.append(", ");
            }
            VariableElement parameter = parameters.get(i);
            source.append("(").append(renderRuntimeCastType(parameter.asType())).append(") ")
                    .append("args[").append(i).append("]");
        }
        return source.toString();
    }

    private String buildSetCase(TypeElement entityType, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        ExecutableElement setter = findMethod(entityType, "set" + capitalize(fieldName), 1);
        String fieldType = renderRuntimeCastType(field.asType());
        StringBuilder source = new StringBuilder();
        source.append("            case \"").append(fieldName).append("\":\n");
        if (setter != null) {
            source.append("                target.").append(setter.getSimpleName()).append("((").append(fieldType).append(") value);\n");
            source.append("                return;\n");
        } else if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            source.append("                target.").append(fieldName).append(" = (")
                    .append(fieldType).append(") value;\n");
            source.append("                return;\n");
        } else {
            source.append("                throw new UnsupportedOperationException(\"No setter or field access for property: ").append(fieldName).append("\");\n");
            return source.toString();
        }
        return source.toString();
    }

    private String buildGetCase(TypeElement entityType, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        ExecutableElement getter = findMethod(entityType, "get" + capitalize(fieldName), 0);
        ExecutableElement booleanGetter = findMethod(entityType, "is" + capitalize(fieldName), 0);
        StringBuilder source = new StringBuilder();
        source.append("            case \"").append(fieldName).append("\":\n");
        if (getter != null) {
            source.append("                return target.").append(getter.getSimpleName()).append("();\n");
        } else if (booleanGetter != null) {
            source.append("                return target.").append(booleanGetter.getSimpleName()).append("();\n");
        } else if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            source.append("                return target.").append(fieldName).append(";\n");
        } else {
            source.append("                throw new UnsupportedOperationException(\"No getter or field access for property: ").append(fieldName).append("\");\n");
        }
        return source.toString();
    }

    private String renderSqlNode(DynamicSqlNode node) {
        if (node instanceof TextSqlNode textSqlNode) {
            return "com.nicleo.kora.core.dynamic.SqlNodes.text(\"" + escapeJava(textSqlNode.getText()) + "\")";
        }
        if (node instanceof MixedSqlNode mixedSqlNode) {
            String children = mixedSqlNode.getChildren().stream().map(this::renderSqlNode).collect(Collectors.joining(", "));
            return "com.nicleo.kora.core.dynamic.SqlNodes.mixed(" + children + ")";
        }
        if (node instanceof IfSqlNode ifSqlNode) {
            return "com.nicleo.kora.core.dynamic.SqlNodes.ifNode(\"" + escapeJava(ifSqlNode.getTest()) + "\", " + renderSqlNode(ifSqlNode.getContents()) + ")";
        }
        if (node instanceof TrimSqlNode trimSqlNode) {
            return "com.nicleo.kora.core.dynamic.SqlNodes.trim(" + renderNullableString(trimSqlNode.getPrefix()) + ", "
                    + renderNullableString(trimSqlNode.getSuffix()) + ", "
                    + renderStringArray(trimSqlNode.getPrefixOverrides()) + ", "
                    + renderStringArray(trimSqlNode.getSuffixOverrides()) + ", "
                    + renderSqlNode(trimSqlNode.getContents()) + ")";
        }
        if (node instanceof ForEachSqlNode forEachSqlNode) {
            return "com.nicleo.kora.core.dynamic.SqlNodes.foreach(\"" + escapeJava(forEachSqlNode.getCollection()) + "\", "
                    + renderNullableString(forEachSqlNode.getItem()) + ", "
                    + renderNullableString(forEachSqlNode.getIndex()) + ", "
                    + renderNullableString(forEachSqlNode.getOpen()) + ", "
                    + renderNullableString(forEachSqlNode.getClose()) + ", "
                    + renderNullableString(forEachSqlNode.getSeparator()) + ", "
                    + renderSqlNode(forEachSqlNode.getContents()) + ")";
        }
        if (node instanceof ChooseSqlNode chooseSqlNode) {
            String whenNodes = chooseSqlNode.getWhenNodes().stream().map(this::renderWhenNode).collect(Collectors.joining(", "));
            return "com.nicleo.kora.core.dynamic.SqlNodes.choose(new com.nicleo.kora.core.dynamic.WhenSqlNode[]{" + whenNodes + "}, "
                    + (chooseSqlNode.getOtherwiseNode() == null ? "null" : renderSqlNode(chooseSqlNode.getOtherwiseNode())) + ")";
        }
        if (node instanceof BindSqlNode bindSqlNode) {
            return "com.nicleo.kora.core.dynamic.SqlNodes.bind(\"" + escapeJava(bindSqlNode.getName()) + "\", \"" + escapeJava(bindSqlNode.getValueExpression()) + "\")";
        }
        throw new ProcessorException("Unsupported sql node type: " + node.getClass().getName());
    }

    private String renderWhenNode(WhenSqlNode whenSqlNode) {
        return "com.nicleo.kora.core.dynamic.SqlNodes.when(\"" + escapeJava(whenSqlNode.getTest()) + "\", " + renderSqlNode(whenSqlNode.getContents()) + ")";
    }

    private String renderStringArray(List<String> values) {
        if (values.isEmpty()) {
            return "new String[0]";
        }
        return "new String[]{" + values.stream().map(value -> "\"" + escapeJava(value) + "\"").collect(Collectors.joining(", ")) + "}";
    }

    private String renderNullableString(String value) {
        return value == null ? "null" : "\"" + escapeJava(value) + "\"";
    }

    private String renderNullableTypeLiteral(TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind() == TypeKind.NONE) {
            return "null";
        }
        return renderTypeLiteral(typeMirror);
    }

    private String statementFieldName(String statementId) {
        StringBuilder builder = new StringBuilder("SQL_");
        for (int i = 0; i < statementId.length(); i++) {
            char ch = statementId.charAt(i);
            if (Character.isUpperCase(ch) && i > 0 && Character.isLowerCase(statementId.charAt(i - 1))) {
                builder.append('_');
            }
            builder.append(Character.isLetterOrDigit(ch) ? Character.toUpperCase(ch) : '_');
        }
        return builder.toString();
    }

    private String capitalize(String fieldName) {
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private String boxedType(String typeName) {
        return switch (typeName) {
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            case "boolean" -> "java.lang.Boolean";
            case "char" -> "java.lang.Character";
            default -> typeName;
        };
    }

    private String renderRuntimeCastType(TypeMirror typeMirror) {
        if (typeMirror == null) {
            return Object.class.getCanonicalName();
        }
        TypeMirror erasedType = types.erasure(typeMirror);
        return boxedType(erasedType.toString());
    }

    private String packageNameOf(TypeElement typeElement) {
        PackageElement packageElement = elements.getPackageOf(typeElement);
        return packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    }

    private String queryPackageNameOf(TypeElement typeElement) {
        String packageName = packageNameOf(typeElement);
        if (packageName.isEmpty()) {
            return "query";
        }
        return packageName.endsWith(".entity")
                ? packageName.substring(0, packageName.length() - ".entity".length()) + ".query"
                : packageName + ".query";
    }

    private String resolveTableName(TypeElement entityType) {
        String alias = aliasValue(entityType);
        if (alias != null) {
            return alias;
        }
        return toSnakeCase(entityType.getSimpleName().toString());
    }

    private String resolveColumnName(VariableElement field) {
        String alias = aliasValue(field);
        if (alias != null) {
            return alias;
        }
        return toSnakeCase(field.getSimpleName().toString());
    }

    private String aliasValue(Element element) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (!annotationMirror.getAnnotationType().toString().equals("com.nicleo.kora.core.annotation.Alias")) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals("value")) {
                    Object value = entry.getValue().getValue();
                    if (value instanceof String alias && !alias.isBlank()) {
                        return alias;
                    }
                }
            }
        }
        return null;
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private String constantName(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && i > 0 && Character.isLowerCase(value.charAt(i - 1))) {
                builder.append('_');
            } else if (!Character.isLetterOrDigit(current) && builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
                continue;
            } else if (!Character.isLetterOrDigit(current)) {
                continue;
            }
            builder.append(Character.toUpperCase(current));
        }
        return builder.toString();
    }

    private String supportSimpleName(ScanSpec scanSpec) {
        return scanSpec.configType.getSimpleName() + "Generated";
    }

    private String supportClassName(ScanSpec scanSpec) {
        String packageName = packageNameOf(scanSpec.configType);
        String simpleName = supportSimpleName(scanSpec);
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    private String escapeJava(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    static final class ReflectSpec {
        private final TypeElement typeElement;
        private final String suffix;
        private final ReflectMetadataLevel metadataLevel;
        private final boolean annotationMetadata;

        private ReflectSpec(TypeElement typeElement, String suffix, ReflectMetadataLevel metadataLevel, boolean annotationMetadata) {
            this.typeElement = typeElement;
            this.suffix = suffix;
            this.metadataLevel = metadataLevel;
            this.annotationMetadata = annotationMetadata;
        }

        TypeElement typeElement() {
            return typeElement;
        }

        String suffix() {
            return suffix;
        }

        ReflectMetadataLevel metadataLevel() {
            return metadataLevel;
        }

        boolean annotationMetadata() {
            return annotationMetadata;
        }
    }

    record MapperMethodSpec(ExecutableElement method, SqlNodeDefinition statement) {
    }

    record IdGenerationSpec(String strategy, String generatorInstantiation) {
    }

    private record MapperCapabilitySpec(TypeElement contractType, TypeElement implType) {
    }

    record MapperCapabilityDelegateSpec(
            String fieldName,
            String interfaceTypeLiteral,
            String instantiation,
            List<MapperCapabilityMethodSpec> methods
    ) {
    }

    record MapperCapabilityMethodSpec(
            String methodName,
            String returnType,
            List<String> parameterTypes,
            List<String> parameterNames
    ) {
    }

    private enum CapabilityConstructorMode {
        SQL_SESSION,
        SQL_SESSION_AND_ENTITY_TABLE
    }

    static final class ScanSpec {
        private final TypeElement configType;
        private final String configQualifiedName;
        private final List<String> xmlRoots;
        private final List<String> entityPackages;
        private final List<String> mapperPackages;
        private final Set<String> mapperTypeNames = new LinkedHashSet<>();

        private ScanSpec(TypeElement configType, List<String> xmlRoots, List<String> entityPackages, List<String> mapperPackages) {
            this.configType = configType;
            this.configQualifiedName = configType.getQualifiedName().toString();
            this.xmlRoots = xmlRoots.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
            this.entityPackages = entityPackages.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
            this.mapperPackages = mapperPackages.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
        }

        TypeElement configType() {
            return configType;
        }
    }

    static final class ProcessorException extends RuntimeException {
        ProcessorException(String message) {
            super(message);
        }

        ProcessorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
