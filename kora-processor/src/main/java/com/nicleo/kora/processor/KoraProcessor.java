package com.nicleo.kora.processor;

import com.nicleo.kora.core.annotation.KoraScan;
import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.dynamic.BindSqlNode;
import com.nicleo.kora.core.dynamic.ChooseSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.ForEachSqlNode;
import com.nicleo.kora.core.dynamic.IfSqlNode;
import com.nicleo.kora.core.dynamic.MixedSqlNode;
import com.nicleo.kora.core.dynamic.TextSqlNode;
import com.nicleo.kora.core.dynamic.TrimSqlNode;
import com.nicleo.kora.core.dynamic.WhenSqlNode;
import com.nicleo.kora.core.util.NameUtils;
import com.nicleo.kora.core.xml.MapperXmlDefinition;
import com.nicleo.kora.core.xml.MapperXmlParser;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        "com.nicleo.kora.core.annotation.Reflect"
})
@SupportedOptions("kora.projectDir")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class KoraProcessor extends AbstractProcessor {
    private static final String SQL_SESSION = "com.nicleo.kora.core.runtime.SqlSession";
    private static final String LIST_TYPE = "java.util.List";

    private Filer filer;
    private Messager messager;
    private Elements elements;
    private String projectDir;

    private final Map<String, ReflectSpec> reflectSpecs = new LinkedHashMap<>();
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
        this.projectDir = processingEnv.getOptions().getOrDefault("kora.projectDir", System.getProperty("user.dir", "."));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        collectReflectSpecs(roundEnv);
        collectScanSpecs(roundEnv);
        generateReflectors();
        generateMeta(roundEnv);
        if (!roundEnv.processingOver() && annotations.isEmpty()) {
            generateSupportsAndMappers();
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
            reflectSpecs.put(typeElement.getQualifiedName().toString(), new ReflectSpec(typeElement, reflect.suffix()));
        }
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
            scanSpecs.add(new ScanSpec(configType, List.of(scan.xml()), List.of(scan.entity())));
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

    private boolean isGeneratedType(TypeElement typeElement) {
        String simpleName = typeElement.getSimpleName().toString();
        return simpleName.equals(simpleName.toUpperCase())
                || simpleName.endsWith("GeneratedReflector")
                || simpleName.endsWith("Generated")
                || simpleName.endsWith("Impl");
    }

    private void generateSupportsAndMappers() {
        for (ScanSpec scanSpec : scanSpecs) {
            try {
                writeSupportClass(scanSpec);
                for (Path xmlFile : findXmlFiles(scanSpec.xmlRoots)) {
                    MapperXmlDefinition xmlDefinition = parseXml(xmlFile);
                    TypeElement mapperType = elements.getTypeElement(xmlDefinition.getNamespace());
                    if (mapperType == null) {
                        throw new ProcessorException("Mapper interface not found for namespace: " + xmlDefinition.getNamespace());
                    }
                    if (!generatedMappers.add(xmlDefinition.getNamespace())) {
                        continue;
                    }
                    writeMapperImpl(mapperType, xmlDefinition, supportClassName(scanSpec));
                }
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to process scan config: " + ex.getMessage(), scanSpec.configType);
            } catch (ProcessorException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), scanSpec.configType);
            }
        }
    }

    private MapperXmlDefinition parseXml(Path xmlFile) {
        try (Reader reader = Files.newBufferedReader(xmlFile)) {
            return MapperXmlParser.parse(reader);
        } catch (IOException ex) {
            throw new ProcessorException("Failed to read xml: " + xmlFile, ex);
        }
    }

    private List<Path> findXmlFiles(List<String> xmlRoots) throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        for (String xmlRoot : xmlRoots) {
            for (Path root : candidatePaths(xmlRoot)) {
                if (!Files.exists(root)) {
                    continue;
                }
                if (Files.isRegularFile(root) && root.toString().endsWith(".xml")) {
                    files.add(root);
                    continue;
                }
                try (Stream<Path> stream = Files.walk(root)) {
                    files.addAll(stream.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".xml"))
                            .collect(Collectors.toList()));
                }
            }
        }
        return new ArrayList<>(files);
    }

    private List<Path> candidatePaths(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path direct = Paths.get(normalized);
        String userDir = projectDir;
        List<Path> paths = new ArrayList<>();
        if (direct.isAbsolute()) {
            paths.add(direct);
            return paths;
        }
        paths.add(Paths.get(userDir, normalized));
        paths.add(Paths.get(userDir, "src/main/resources", normalized));
        paths.add(Paths.get(userDir, "src/test/resources", normalized));
        return paths;
    }

    private void writeMetaClass(TypeElement entityType) throws IOException {
        String packageName = metaPackageNameOf(entityType);
        String generatedSimpleName = entityType.getSimpleName().toString().toUpperCase();
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName, entityType);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(buildMetaSource(packageName, generatedSimpleName, entityType));
        }
    }

    private void writeReflectorClass(TypeElement entityType, String suffix) throws IOException {
        String packageName = packageNameOf(entityType);
        String generatedSimpleName = entityType.getSimpleName() + suffix;
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName, entityType);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(buildReflectorSource(packageName, generatedSimpleName, entityType));
        }
    }

    private void writeSupportClass(ScanSpec scanSpec) throws IOException {
        String qualifiedName = supportClassName(scanSpec);
        if (!generatedSupports.add(qualifiedName)) {
            return;
        }
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName, scanSpec.configType);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(buildSupportSource(scanSpec));
        }
    }

    private void writeMapperImpl(TypeElement mapperType, MapperXmlDefinition xmlDefinition, String supportClassName) throws IOException {
        String packageName = packageNameOf(mapperType);
        String generatedSimpleName = mapperType.getSimpleName() + "Impl";
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, mapperType);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(buildMapperSource(packageName, generatedSimpleName, mapperType, xmlDefinition, supportClassName));
        }
    }

    private String buildMetaSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("public final class ").append(generatedSimpleName).append(" {\n");
        source.append("    private ").append(generatedSimpleName).append("() {\n    }\n\n");
        for (VariableElement field : collectInstanceFields(entityType)) {
            String fieldName = field.getSimpleName().toString();
            source.append("    public static final String ").append(fieldName)
                    .append(" = \"").append(NameUtils.camelToSnake(fieldName)).append("\";\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private String buildReflectorSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        String entityTypeName = entityType.getQualifiedName().toString();
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("public final class ").append(generatedSimpleName)
                .append(" implements com.nicleo.kora.core.runtime.GeneratedReflector<")
                .append(entityTypeName).append("> {\n");
        source.append("    @Override\n    public ").append(entityTypeName).append(" newInstance() {\n")
                .append("        return new ").append(entityType.getSimpleName()).append("();\n    }\n\n");
        source.append("    @Override\n    public Object invoke(").append(entityTypeName).append(" target, String method, Object[] args) {\n")
                .append("        if (method == null) {\n")
                .append("            throw new java.lang.IllegalArgumentException(\"method must not be null\");\n")
                .append("        }\n")
                .append("        switch (method) {\n");
        for (ExecutableElement method : collectInvokableMethods(entityType)) {
            source.append(buildInvokeCase(method));
        }
        source.append("            default:\n")
                .append("                throw new java.lang.IllegalArgumentException(\"Unknown method: \" + method);\n")
                .append("        }\n    }\n\n");
        source.append("    @Override\n    public void set(").append(entityTypeName).append(" target, String property, Object value) {\n")
                .append("        switch (com.nicleo.kora.core.runtime.TypeConverter.normalize(property)) {\n");
        for (VariableElement field : collectInstanceFields(entityType)) {
            source.append(buildSetCase(entityType, field));
        }
        source.append("            default:\n                return;\n        }\n    }\n\n");
        source.append("    @Override\n    public Object get(").append(entityTypeName).append(" target, String property) {\n")
                .append("        switch (com.nicleo.kora.core.runtime.TypeConverter.normalize(property)) {\n");
        for (VariableElement field : collectInstanceFields(entityType)) {
            source.append(buildGetCase(entityType, field));
        }
        source.append("            default:\n                throw new java.lang.IllegalArgumentException(\"Unknown property: \" + property);\n")
                .append("        }\n    }\n}\n");
        return source.toString();
    }

    private String buildSupportSource(ScanSpec scanSpec) {
        String packageName = packageNameOf(scanSpec.configType);
        String simpleName = supportSimpleName(scanSpec);
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("public final class ").append(simpleName).append(" {\n")
                .append("    private static volatile boolean installed;\n\n")
                .append("    private ").append(simpleName).append("() {\n    }\n\n")
                .append("    public static void install() {\n")
                .append("        if (installed) {\n            return;\n        }\n")
                .append("        synchronized (").append(simpleName).append(".class) {\n")
                .append("            if (installed) {\n                return;\n            }\n")
                .append("            com.nicleo.kora.core.runtime.GeneratedReflectors.install(new Resolver());\n")
                .append("            installed = true;\n")
                .append("        }\n")
                .append("    }\n\n")
                .append("    private static final class Resolver implements com.nicleo.kora.core.runtime.GeneratedReflectors.Resolver {\n")
                .append("        @Override\n")
                .append("        @SuppressWarnings(\"unchecked\")\n")
                .append("        public <T> com.nicleo.kora.core.runtime.GeneratedReflector<T> get(Class<T> type) {\n");
        for (ReflectSpec spec : reflectSpecs.values()) {
            source.append("            if (type == ").append(spec.typeElement.getQualifiedName()).append(".class) {\n")
                    .append("                return (com.nicleo.kora.core.runtime.GeneratedReflector<T>) new ")
                    .append(spec.typeElement.getQualifiedName()).append(spec.suffix).append("();\n")
                    .append("            }\n");
        }
        source.append("            throw new com.nicleo.kora.core.runtime.SqlSessionException(\"No GeneratedReflector for type: \" + type.getName());\n")
                .append("        }\n")
                .append("    }\n")
                .append("}\n");
        return source.toString();
    }

    private String buildMapperSource(String packageName, String generatedSimpleName, TypeElement mapperType, MapperXmlDefinition xmlDefinition, String supportClassName) {
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("public final class ").append(generatedSimpleName)
                .append(" implements ").append(mapperType.getQualifiedName()).append(" {\n")
                .append("    private final ").append(SQL_SESSION).append(" sqlSession;\n\n");
        for (Element enclosedElement : mapperType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && !enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                SqlNodeDefinition statement = xmlDefinition.getStatements().get(method.getSimpleName().toString());
                if (statement == null) {
                    throw new ProcessorException("No xml statement found for method: " + method.getSimpleName());
                }
                source.append("    private static final com.nicleo.kora.core.dynamic.DynamicSqlNode ")
                        .append(statementFieldName(statement.id()))
                        .append(" = ")
                        .append(renderSqlNode(statement.rootSqlNode()))
                        .append(";\n\n");
            }
        }
        source.append("    public ").append(generatedSimpleName).append('(').append(SQL_SESSION).append(" sqlSession) {\n")
                .append("        ").append(supportClassName).append(".install();\n")
                .append("        this.sqlSession = sqlSession;\n")
                .append("    }\n\n");
        for (Element enclosedElement : mapperType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && !enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                SqlNodeDefinition statement = xmlDefinition.getStatements().get(method.getSimpleName().toString());
                if (statement == null) {
                    throw new ProcessorException("No xml statement found for method: " + method.getSimpleName());
                }
                source.append(buildMethod(mapperType, method, statement));
            }
        }
        source.append("}\n");
        return source.toString();
    }

    private String buildMethod(TypeElement mapperType, ExecutableElement method, SqlNodeDefinition statement) {
        StringBuilder source = new StringBuilder();
        source.append("    @Override\n")
                .append("    public ").append(method.getReturnType()).append(' ').append(method.getSimpleName()).append('(')
                .append(renderParameters(method)).append(") {\n")
                .append("        java.util.Map<String, Object> params = com.nicleo.kora.core.dynamic.MapperParameters.build(")
                .append(renderParameterNames(method)).append(", ").append(renderParameterValues(method)).append(");\n")
                .append("        com.nicleo.kora.core.runtime.BoundSql boundSql = com.nicleo.kora.core.dynamic.DynamicSqlRenderer.render(")
                .append(statementFieldName(statement.id())).append(", params);\n")
                .append("        String sql = boundSql.getSql();\n")
                .append("        Object[] args = com.nicleo.kora.core.dynamic.DynamicSqlArgumentResolver.resolve(boundSql);\n")
                .append(renderExecution(mapperType, method, statement))
                .append("    }\n\n");
        return source.toString();
    }

    private String renderParameters(ExecutableElement method) {
        List<? extends VariableElement> parameters = method.getParameters();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            VariableElement parameter = parameters.get(i);
            builder.append(parameter.asType()).append(' ').append(parameter.getSimpleName());
        }
        return builder.toString();
    }

    private String renderParameterNames(ExecutableElement method) {
        StringBuilder builder = new StringBuilder("new String[]{");
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append('"').append(parameters.get(i).getSimpleName()).append('"');
        }
        builder.append('}');
        return builder.toString();
    }

    private String renderParameterValues(ExecutableElement method) {
        StringBuilder builder = new StringBuilder("new Object[]{");
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameters.get(i).getSimpleName());
        }
        builder.append('}');
        return builder.toString();
    }

    private String renderExecution(TypeElement mapperType, ExecutableElement method, SqlNodeDefinition statement) {
        SqlCommandType commandType = statement.commandType();
        TypeMirror returnType = method.getReturnType();
        if (commandType == SqlCommandType.SELECT) {
            if (isListReturn(returnType)) {
                String elementType = extractListElementType(returnType);
                reflectSpecFor(typeElementOf(elementType));
                return "        return sqlSession.selectList(sql, args, " + elementType + ".class);\n";
            }
            if (returnType.getKind() == TypeKind.VOID) {
                throw new ProcessorException("Select method cannot return void: " + mapperType.getQualifiedName() + "." + method.getSimpleName());
            }
            if (returnType.getKind().isPrimitive()) {
                throw new ProcessorException("Select method must return an entity or List<entity>, not primitive: " + mapperType.getQualifiedName() + "." + method.getSimpleName());
            }
            reflectSpecFor(asTypeElement(returnType));
            return "        return sqlSession.selectOne(sql, args, " + returnType + ".class);\n";
        }
        if (returnType.getKind() == TypeKind.INT) {
            return "        return sqlSession.update(sql, args);\n";
        }
        if (returnType.getKind() == TypeKind.VOID) {
            return "        sqlSession.update(sql, args);\n        return;\n";
        }
        throw new ProcessorException("Non-select method must return int or void: " + mapperType.getQualifiedName() + "." + method.getSimpleName());
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

    private String extractListElementType(TypeMirror returnType) {
        DeclaredType declaredType = (DeclaredType) returnType;
        return declaredType.getTypeArguments().get(0).toString();
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

    private TypeElement typeElementOf(String qualifiedName) {
        TypeElement typeElement = elements.getTypeElement(qualifiedName);
        if (typeElement == null) {
            throw new ProcessorException("Unknown type: " + qualifiedName);
        }
        return typeElement;
    }

    private ReflectSpec reflectSpecFor(TypeElement typeElement) {
        if (typeElement == null) {
            throw new ProcessorException("Missing @Reflect type information");
        }
        ReflectSpec spec = reflectSpecs.get(typeElement.getQualifiedName().toString());
        if (spec == null) {
            throw new ProcessorException("Type must be annotated with @Reflect: " + typeElement.getQualifiedName());
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
                fields.add((VariableElement) enclosedElement);
            }
        }
        return fields;
    }

    private List<ExecutableElement> collectInvokableMethods(TypeElement entityType) {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element enclosedElement : entityType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD
                    && !enclosedElement.getModifiers().contains(Modifier.STATIC)
                    && !enclosedElement.getModifiers().contains(Modifier.PRIVATE)) {
                methods.add((ExecutableElement) enclosedElement);
            }
        }
        return methods;
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
            source.append("(").append(boxedType(parameter.asType().toString())).append(") ")
                    .append("com.nicleo.kora.core.runtime.TypeConverter.cast(args[").append(i).append("], ")
                    .append(boxedType(parameter.asType().toString())).append(".class)");
        }
        return source.toString();
    }

    private String buildSetCase(TypeElement entityType, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        String normalized = NameUtils.camelToSnake(fieldName).replace("_", "").toLowerCase();
        ExecutableElement setter = findMethod(entityType, "set" + capitalize(fieldName), 1);
        String fieldType = boxedType(field.asType().toString());
        StringBuilder source = new StringBuilder();
        source.append("            case \"").append(normalized).append("\":\n");
        if (setter != null) {
            source.append("                target.").append(setter.getSimpleName()).append("((")
                    .append(fieldType).append(") com.nicleo.kora.core.runtime.TypeConverter.cast(value, ")
                    .append(fieldType).append(".class));\n");
        } else if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            source.append("                target.").append(fieldName).append(" = (")
                    .append(fieldType).append(") com.nicleo.kora.core.runtime.TypeConverter.cast(value, ")
                    .append(fieldType).append(".class);\n");
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Reflect field requires setter or non-private access: " + entityType.getQualifiedName() + "." + fieldName, field);
            source.append("                return;\n");
            return source.toString();
        }
        source.append("                return;\n");
        return source.toString();
    }

    private String buildGetCase(TypeElement entityType, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        String normalized = NameUtils.camelToSnake(fieldName).replace("_", "").toLowerCase();
        ExecutableElement getter = findMethod(entityType, "get" + capitalize(fieldName), 0);
        ExecutableElement booleanGetter = findMethod(entityType, "is" + capitalize(fieldName), 0);
        StringBuilder source = new StringBuilder();
        source.append("            case \"").append(normalized).append("\":\n");
        if (getter != null) {
            source.append("                return target.").append(getter.getSimpleName()).append("();\n");
        } else if (booleanGetter != null) {
            source.append("                return target.").append(booleanGetter.getSimpleName()).append("();\n");
        } else if (!field.getModifiers().contains(Modifier.PRIVATE)) {
            source.append("                return target.").append(fieldName).append(";\n");
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, "@Reflect field requires getter or non-private access: " + entityType.getQualifiedName() + "." + fieldName, field);
            source.append("                throw new java.lang.IllegalArgumentException(\"Unknown property: \" + property);\n");
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

    private String packageNameOf(TypeElement typeElement) {
        PackageElement packageElement = elements.getPackageOf(typeElement);
        return packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    }

    private String metaPackageNameOf(TypeElement typeElement) {
        String packageName = packageNameOf(typeElement);
        return packageName.isEmpty() ? "meta" : packageName + ".meta";
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

    private static final class ReflectSpec {
        private final TypeElement typeElement;
        private final String suffix;

        private ReflectSpec(TypeElement typeElement, String suffix) {
            this.typeElement = typeElement;
            this.suffix = suffix;
        }
    }

    private static final class ScanSpec {
        private final TypeElement configType;
        private final String configQualifiedName;
        private final List<String> xmlRoots;
        private final List<String> entityPackages;

        private ScanSpec(TypeElement configType, List<String> xmlRoots, List<String> entityPackages) {
            this.configType = configType;
            this.configQualifiedName = configType.getQualifiedName().toString();
            this.xmlRoots = xmlRoots.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
            this.entityPackages = entityPackages.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
        }
    }

    private static final class ProcessorException extends RuntimeException {
        private ProcessorException(String message) {
            super(message);
        }

        private ProcessorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
