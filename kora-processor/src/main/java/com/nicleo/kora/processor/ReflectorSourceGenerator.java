package com.nicleo.kora.processor;

import com.nicleo.kora.core.annotation.ReflectMetadataLevel;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReflectorSourceGenerator {
    private final Context context;

    ReflectorSourceGenerator(Context context) {
        this.context = context;
    }

    String buildReflectorSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        String entityTypeName = entityType.getQualifiedName().toString();
        List<VariableElement> fields = context.collectInstanceFields(entityType);
        KoraProcessor.ReflectSpec reflectSpec = context.reflectSpec(entityType);
        ReflectMetadataLevel metadataLevel = reflectSpec == null ? ReflectMetadataLevel.BASIC : reflectSpec.metadataLevel();
        List<ExecutableElement> methods = metadataLevel.includesMethods() ? context.collectInvokableMethods(entityType) : List.of();
        ExecutableElement fullArgsConstructor = findFullArgsConstructor(entityType);
        List<String> expandedFieldNames = context.expandedFieldNames(entityType);
        boolean includeFieldsMetadata = metadataLevel.includesFields();
        boolean includeMethodsMetadata = metadataLevel.includesMethods();
        boolean includeAnnotationMetadata = reflectSpec != null && reflectSpec.annotationMetadata();
        boolean useLazyMetadata = includeAnnotationMetadata;
        Map<String, String> typeConstants = new LinkedHashMap<>();
        TypeElement declaredSuperType = context.directSuperType(entityType);
        TypeElement parentReflectType = declaredSuperType != null && !context.isJavaLangObject(declaredSuperType)
                ? context.hasReflectSpec(declaredSuperType) ? declaredSuperType : null
                : null;
        String parentReflectTypeName = parentReflectType == null ? null : parentReflectType.getQualifiedName().toString();
        Map<String, List<ExecutableElement>> methodsByName = new LinkedHashMap<>();
        for (ExecutableElement method : methods) {
            methodsByName.computeIfAbsent(method.getSimpleName().toString(), ignored -> new ArrayList<>()).add(method);
        }
        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("import com.nicleo.kora.core.runtime.ClassInfo;\n");
        source.append("import com.nicleo.kora.core.runtime.FieldInfo;\n");
        source.append("import com.nicleo.kora.core.runtime.GeneratedReflector;\n");
        source.append("import com.nicleo.kora.core.runtime.GeneratedReflectors;\n");
        source.append("import com.nicleo.kora.core.runtime.MethodInfo;\n");
        source.append("import com.nicleo.kora.core.runtime.ParameterInfo;\n");
        source.append("import com.nicleo.kora.core.runtime.RuntimeTypes;\n");
        source.append("import java.lang.annotation.Annotation;\n");
        source.append("import java.lang.reflect.Type;\n");
        if (useLazyMetadata) {
            source.append("import java.util.concurrent.atomic.AtomicReferenceArray;\n");
        }
        if (includeMethodsMetadata) {
            source.append("import java.util.Collections;\n");
            source.append("import java.util.LinkedHashSet;\n");
        }
        source.append('\n');
        source.append("public final class ").append(generatedSimpleName)
                .append(" implements GeneratedReflector<")
                .append(entityTypeName).append("> {\n");
        source.append("    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];\n");
        source.append("    private static final ParameterInfo[] NO_PARAMS = new ParameterInfo[0];\n");
        source.append("    private static final MethodInfo[] NO_METHODS = new MethodInfo[0];\n");
        source.append("    private static volatile ClassInfo classInfo;\n");
        if (parentReflectType != null) {
            source.append("    private static volatile GeneratedReflector<")
                    .append(parentReflectTypeName).append("> parentReflector;\n");
        }
        if (includeFieldsMetadata || includeMethodsMetadata || parentReflectType != null) {
            source.append('\n');
        }
        context.collectTypeConstants(typeConstants, entityType.getSuperclass());
        for (VariableElement field : fields) {
            context.collectTypeConstants(typeConstants, field.asType());
        }
        if (fullArgsConstructor != null) {
            for (VariableElement parameter : fullArgsConstructor.getParameters()) {
                context.collectTypeConstants(typeConstants, parameter.asType());
            }
        }
        for (ExecutableElement method : methods) {
            context.collectTypeConstants(typeConstants, method.getReturnType());
            for (VariableElement parameter : method.getParameters()) {
                context.collectTypeConstants(typeConstants, parameter.asType());
            }
        }
        int typeIndex = 0;
        for (String initializer : typeConstants.keySet()) {
            source.append("    private static final Type TYPE_").append(typeIndex).append(" = ").append(initializer).append(";\n");
            typeConstants.put(initializer, "TYPE_" + typeIndex);
            typeIndex++;
        }
        context.setActiveTypeConstants(Map.copyOf(typeConstants));
        if (!typeConstants.isEmpty()) {
            source.append('\n');
        }
        if (includeFieldsMetadata) {
            source.append("    private static final String[] FIELD_NAMES = new String[]{");
            for (int i = 0; i < expandedFieldNames.size(); i++) {
                if (i > 0) {
                    source.append(", ");
                }
                source.append('"').append(context.escapeJava(expandedFieldNames.get(i))).append('"');
            }
            source.append("};\n");
            if (useLazyMetadata) {
                source.append("    private static final AtomicReferenceArray<FieldInfo> FIELD_CACHE = new AtomicReferenceArray<>(")
                        .append(fields.size()).append(");\n");
            } else {
                source.append("    private static final FieldInfo[] FIELDS = new FieldInfo[]{\n");
                for (int i = 0; i < fields.size(); i++) {
                    source.append(context.buildFieldInfoEntry(entityType, fields.get(i), false));
                    source.append(i + 1 == fields.size() ? '\n' : ",\n");
                }
                source.append("    };\n");
            }
            source.append('\n');
        }
        if (includeMethodsMetadata) {
            source.append("    private static final String[] METHOD_NAMES = new String[]{");
            int methodNameIndex = 0;
            for (String methodName : methodsByName.keySet()) {
                if (methodNameIndex++ > 0) {
                    source.append(", ");
                }
                source.append('"').append(context.escapeJava(methodName)).append('"');
            }
            source.append("};\n");
            if (useLazyMetadata) {
                source.append("    private static final AtomicReferenceArray<MethodInfo[]> METHOD_CACHE = new AtomicReferenceArray<>(")
                        .append(methodsByName.size()).append(");\n");
            } else {
                source.append("    private static final MethodInfo[][] METHODS = new MethodInfo[][]{\n");
                int methodGroupIndex = 0;
                for (Map.Entry<String, List<ExecutableElement>> entry : methodsByName.entrySet()) {
                    List<ExecutableElement> groupedMethods = entry.getValue();
                    source.append("        ");
                    if (groupedMethods.size() == 1) {
                        source.append("methodArray(").append(context.buildMethodInfoEntry(entityType, groupedMethods.getFirst(), false).trim()).append(")");
                    } else {
                        source.append("new MethodInfo[]{\n");
                        for (int i = 0; i < groupedMethods.size(); i++) {
                            source.append(context.buildMethodInfoEntry(entityType, groupedMethods.get(i), false));
                            source.append(i + 1 == groupedMethods.size() ? '\n' : ",\n");
                        }
                        source.append("        }");
                    }
                    source.append(++methodGroupIndex == methodsByName.size() ? '\n' : ",\n");
                }
                source.append("    };\n");
            }
            source.append('\n');
        }
        if (includeFieldsMetadata && useLazyMetadata) {
            source.append("    private static FieldInfo initField(int index) {\n")
                    .append("        FieldInfo local = FIELD_CACHE.get(index);\n")
                    .append("        if (local == null) {\n")
                    .append("            synchronized (").append(generatedSimpleName).append(".class) {\n")
                    .append("                local = FIELD_CACHE.get(index);\n")
                    .append("                if (local == null) {\n")
                    .append("                    local = switch (index) {\n");
            for (int i = 0; i < fields.size(); i++) {
                source.append("                        case ").append(i).append(" -> ")
                        .append(context.buildFieldInfoEntry(entityType, fields.get(i), includeAnnotationMetadata).trim())
                        .append(";\n");
            }
            source.append("                        default -> throw new IllegalArgumentException(\"Unknown field index: \" + index);\n")
                    .append("                    };\n")
                    .append("                    FIELD_CACHE.set(index, local);\n")
                    .append("                }\n")
                    .append("            }\n")
                    .append("        }\n")
                    .append("        return local;\n")
                    .append("    }\n\n");
        }
        if (includeMethodsMetadata && useLazyMetadata) {
            source.append("    private static MethodInfo[] initMethods(int index) {\n")
                    .append("        MethodInfo[] local = METHOD_CACHE.get(index);\n")
                    .append("        if (local == null) {\n")
                    .append("            synchronized (").append(generatedSimpleName).append(".class) {\n")
                    .append("                local = METHOD_CACHE.get(index);\n")
                    .append("                if (local == null) {\n")
                    .append("                    local = switch (index) {\n");
            int methodIndex = 0;
            for (Map.Entry<String, List<ExecutableElement>> entry : methodsByName.entrySet()) {
                List<ExecutableElement> groupedMethods = entry.getValue();
                source.append("                        case ").append(methodIndex++).append(" -> ");
                if (groupedMethods.size() == 1) {
                    source.append("methodArray(").append(context.buildMethodInfoEntry(entityType, groupedMethods.getFirst(), includeAnnotationMetadata).trim()).append(");\n");
                } else {
                    source.append("new MethodInfo[]{\n");
                    for (int i = 0; i < groupedMethods.size(); i++) {
                        source.append(context.buildMethodInfoEntry(entityType, groupedMethods.get(i), includeAnnotationMetadata));
                        source.append(i + 1 == groupedMethods.size() ? '\n' : ",\n");
                    }
                    source.append("                        };\n");
                }
            }
            source.append("                        default -> throw new IllegalArgumentException(\"Unknown method index: \" + index);\n")
                    .append("                    };\n")
                    .append("                    METHOD_CACHE.set(index, local);\n")
                    .append("                }\n")
                    .append("            }\n")
                    .append("        }\n")
                    .append("        return local;\n")
                    .append("    }\n\n");
        }
        if (includeMethodsMetadata) {
            source.append("    private static MethodInfo[] methodArray(MethodInfo methodInfo) {\n")
                    .append("        return new MethodInfo[]{methodInfo};\n")
                    .append("    }\n\n");
        }
        source.append("    private static ParameterInfo[] parameterArray(ParameterInfo parameterInfo) {\n")
                .append("        return new ParameterInfo[]{parameterInfo};\n")
                .append("    }\n\n");
        source.append("    private static ParameterInfo parameter(String name, Type type) {\n")
                .append("        return new ParameterInfo(name, type, NO_ANNOTATIONS);\n")
                .append("    }\n\n");
        source.append("    private static ClassInfo initClassInfo() {\n")
                .append("        ClassInfo local = classInfo;\n")
                .append("        if (local == null) {\n")
                .append("            synchronized (").append(generatedSimpleName).append(".class) {\n")
                .append("                local = classInfo;\n")
                .append("                if (local == null) {\n")
                .append("                    local = new ClassInfo(")
                .append(entityTypeName).append(".class, ")
                .append(context.renderNullableTypeLiteral(entityType.getSuperclass())).append(", ")
                .append(context.modifierMask(entityType.getModifiers())).append(", ")
                .append(context.renderAnnotationArray(entityType.getAnnotationMirrors(), includeAnnotationMetadata)).append(", ")
                .append(renderConstructorParams(fullArgsConstructor, includeAnnotationMetadata)).append(");\n")
                .append("                    classInfo = local;\n")
                .append("                }\n")
                .append("            }\n")
                .append("        }\n")
                .append("        return local;\n")
                .append("    }\n\n");
        if (parentReflectType != null) {
            source.append("    private static GeneratedReflector<").append(parentReflectTypeName).append("> parentReflector() {\n")
                    .append("        GeneratedReflector<").append(parentReflectTypeName).append("> local = parentReflector;\n")
                    .append("        if (local == null) {\n")
                    .append("            synchronized (").append(generatedSimpleName).append(".class) {\n")
                    .append("                local = parentReflector;\n")
                    .append("                if (local == null) {\n")
                    .append("                    local = GeneratedReflectors.get(").append(parentReflectTypeName).append(".class);\n")
                    .append("                    parentReflector = local;\n")
                    .append("                }\n")
                    .append("            }\n")
                    .append("        }\n")
                    .append("        return local;\n")
                    .append("    }\n\n");
        }
        if (parentReflectType != null && includeMethodsMetadata) {
            source.append("    private static volatile String[] mergedMethodNames;\n");
        }
        if (parentReflectType != null && includeMethodsMetadata) {
            source.append('\n');
            source.append("    private static MethodInfo[] mergeMethodInfoArrays(MethodInfo[] currentMethods, MethodInfo[] inheritedMethods) {\n")
                    .append("        if (currentMethods.length == 0) {\n")
                    .append("            return inheritedMethods;\n")
                    .append("        }\n")
                    .append("        if (inheritedMethods.length == 0) {\n")
                    .append("            return currentMethods;\n")
                    .append("        }\n")
                    .append("        MethodInfo[] merged = new MethodInfo[currentMethods.length + inheritedMethods.length];\n")
                    .append("        System.arraycopy(currentMethods, 0, merged, 0, currentMethods.length);\n")
                    .append("        System.arraycopy(inheritedMethods, 0, merged, currentMethods.length, inheritedMethods.length);\n")
                    .append("        return merged;\n")
                    .append("    }\n\n")
                    .append("    private static String[] mergedMethodNames() {\n")
                    .append("        String[] local = mergedMethodNames;\n")
                    .append("        if (local == null) {\n")
                    .append("            synchronized (").append(generatedSimpleName).append(".class) {\n")
                    .append("                local = mergedMethodNames;\n")
                    .append("                if (local == null) {\n")
                    .append("                    local = mergeMethodNames(METHOD_NAMES, parentReflector().getMethods());\n")
                    .append("                    mergedMethodNames = local;\n")
                    .append("                }\n")
                    .append("            }\n")
                    .append("        }\n")
                    .append("        return local.clone();\n")
                    .append("    }\n\n")
                    .append("    private static String[] mergeMethodNames(String[] currentNames, String[] inheritedNames) {\n")
                    .append("        if (currentNames.length == 0) {\n")
                    .append("            return inheritedNames.clone();\n")
                    .append("        }\n")
                    .append("        if (inheritedNames.length == 0) {\n")
                    .append("            return currentNames.clone();\n")
                    .append("        }\n")
                    .append("        LinkedHashSet<String> names = new LinkedHashSet<>();\n")
                    .append("        Collections.addAll(names, currentNames);\n")
                    .append("        Collections.addAll(names, inheritedNames);\n")
                    .append("        return names.toArray(new String[0]);\n")
                    .append("    }\n\n");
        }
        source.append("    @Override\n    public ").append(entityTypeName).append(" newInstance() {\n");
        String newInstanceExpression = context.renderNewInstanceExpression(entityType);
        if (newInstanceExpression != null) {
            source.append("        return ").append(newInstanceExpression).append(";\n");
        } else {
            source.append("        throw new UnsupportedOperationException(\"Type cannot be instantiated without accessible constructor: ")
                    .append(entityTypeName)
                    .append("\");\n");
        }
        source.append("    }\n\n");
        source.append("    @Override\n    public ").append(entityTypeName).append(" newInstance(Object[] args) {\n");
        if (fullArgsConstructor != null) {
            source.append("        if (args == null) {\n")
                    .append("            args = new Object[0];\n")
                    .append("        }\n")
                    .append("        if (args.length != ").append(fullArgsConstructor.getParameters().size()).append(") {\n")
                    .append("            throw new IllegalArgumentException(\"Expected ").append(fullArgsConstructor.getParameters().size()).append(" constructor arguments but got \" + args.length);\n")
                    .append("        }\n")
                    .append("        return ").append(renderConstructorInvocation(entityType, fullArgsConstructor, "args")).append(";\n");
        } else {
            source.append("        if (args != null && args.length > 0) {\n")
                    .append("            throw new IllegalArgumentException(\"Expected 0 constructor arguments but got \" + args.length);\n")
                    .append("        }\n")
                    .append("        return newInstance();\n");
        }
        source.append("    }\n\n");
        source.append("    @Override\n    public ClassInfo getClassInfo() {\n")
                .append("        return initClassInfo();\n")
                .append("    }\n\n");
        source.append("    @Override\n    public Object invoke(").append(entityTypeName).append(" target, String method, Object[] args) {\n")
                .append("        if (method == null) {\n")
                .append("            throw new java.lang.IllegalArgumentException(\"method must not be null\");\n")
                .append("        }\n")
                .append("        switch (method) {\n");
        for (ExecutableElement method : methods) {
            source.append(context.buildInvokeCase(method));
        }
        source.append("            default:\n")
                .append(parentReflectType != null
                        ? "                return parentReflector().invoke((" + parentReflectTypeName + ") target, method, args);\n"
                        : "                throw new java.lang.IllegalArgumentException(\"Unknown method: \" + method);\n")
                .append("        }\n    }\n\n");
        source.append("    @Override\n    public void set(").append(entityTypeName).append(" target, String property, Object value) {\n")
                .append("        switch (property) {\n");
        for (VariableElement field : fields) {
            source.append(context.buildSetCase(entityType, field));
        }
        source.append("            default:\n")
                .append(parentReflectType != null
                        ? "                parentReflector().set((" + parentReflectTypeName + ") target, property, value);\n                return;\n"
                        : "                return;\n")
                .append("        }\n    }\n\n");
        source.append("    @Override\n    public Object get(").append(entityTypeName).append(" target, String property) {\n")
                .append("        switch (property) {\n");
        for (VariableElement field : fields) {
            source.append(context.buildGetCase(entityType, field));
        }
        source.append("            default:\n")
                .append(parentReflectType != null
                        ? "                return parentReflector().get((" + parentReflectTypeName + ") target, property);\n"
                        : "                throw new java.lang.IllegalArgumentException(\"Unknown property: \" + property);\n")
                .append("        }\n    }\n\n");
        source.append("    @Override\n    public String[] fieldNamesView() {\n")
                .append(includeFieldsMetadata
                        ? "        return FIELD_NAMES;\n"
                        : parentReflectType != null
                        ? "        return parentReflector().fieldNamesView();\n"
                        : "        return new String[0];\n")
                .append("    }\n\n");
        source.append("    @Override\n    public String[] getFields() {\n")
                .append(includeFieldsMetadata
                        ? "        return FIELD_NAMES.clone();\n"
                        : parentReflectType != null
                        ? "        return parentReflector().getFields();\n"
                        : "        return new String[0];\n")
                .append("    }\n\n");
        source.append("    @Override\n    public boolean hasField(String field) {\n")
                .append(includeFieldsMetadata
                        ? "        if (field == null) {\n            return false;\n        }\n        return switch (field) {\n"
                        : parentReflectType != null
                        ? "        return field != null && parentReflector().hasField(field);\n"
                        : "        return false;\n")
                .append(includeFieldsMetadata
                        ? context.buildExpandedFieldPresenceSwitch(entityType, expandedFieldNames, "            ", parentReflectType != null ? "parentReflector().hasField(field)" : "false")
                        : "")
                .append(includeFieldsMetadata ? "        };\n" : "")
                .append("    }\n\n");
        source.append("    @Override\n    public FieldInfo getField(String field) {\n")
                .append(includeFieldsMetadata
                        ? "        if (field == null) {\n            return null;\n        }\n        return switch (field) {\n"
                        : parentReflectType != null
                        ? "        return field == null ? null : parentReflector().getField(field);\n"
                        : "        return null;\n")
                .append(includeFieldsMetadata
                        ? context.buildExpandedFieldInfoSwitch(entityType, fields, expandedFieldNames, "            ", parentReflectType != null ? "parentReflector().getField(field)" : "null", useLazyMetadata)
                        : "")
                .append(includeFieldsMetadata ? "        };\n" : "")
                .append("    }\n\n");
        source.append("    @Override\n    public String[] getMethods() {\n")
                .append(includeMethodsMetadata
                        ? parentReflectType != null
                        ? "        return mergedMethodNames();\n"
                        : "        return METHOD_NAMES.clone();\n"
                        : parentReflectType != null
                        ? "        return parentReflector().getMethods();\n"
                        : "        return new String[0];\n")
                .append("    }\n\n");
        source.append("    @Override\n    public boolean hasMethod(String name) {\n")
                .append(includeMethodsMetadata
                        ? "        if (name == null) {\n            return false;\n        }\n        return switch (name) {\n"
                        : parentReflectType != null
                        ? "        return name != null && parentReflector().hasMethod(name);\n"
                        : "        return false;\n")
                .append(includeMethodsMetadata
                        ? context.buildMethodPresenceSwitch(methodsByName, "            ", parentReflectType != null ? "parentReflector().hasMethod(name)" : "false")
                        : "")
                .append(includeMethodsMetadata ? "        };\n" : "")
                .append("    }\n\n");
        source.append("    @Override\n    public MethodInfo[] getMethod(String name) {\n")
                .append(includeMethodsMetadata
                        ? "        if (name == null) {\n            return NO_METHODS;\n        }\n        return switch (name) {\n"
                        : parentReflectType != null
                        ? "        return name == null ? NO_METHODS : parentReflector().getMethod(name);\n"
                        : "        return NO_METHODS;\n")
                .append(includeMethodsMetadata
                        ? context.buildMethodInfoArraySwitch(methodsByName, "            ", parentReflectType != null ? "parentReflector().getMethod(name)" : "NO_METHODS", useLazyMetadata, parentReflectType != null)
                        : "")
                .append(includeMethodsMetadata ? "        };\n" : "")
                .append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private ExecutableElement findFullArgsConstructor(TypeElement entityType) {
        ExecutableElement preferred = null;
        for (var enclosedElement : entityType.getEnclosedElements()) {
            if (!(enclosedElement instanceof ExecutableElement constructor) || !constructor.getKind().name().equals("CONSTRUCTOR")) {
                continue;
            }
            if (constructor.getModifiers().contains(javax.lang.model.element.Modifier.PRIVATE)) {
                continue;
            }
            if (preferred == null || constructor.getParameters().size() > preferred.getParameters().size()) {
                preferred = constructor;
            }
        }
        return preferred;
    }

    private String renderConstructorParams(ExecutableElement constructor, boolean includeAnnotationMetadata) {
        return constructor == null
                ? "NO_PARAMS"
                : context.renderParameterInfoArray(constructor.getParameters(), includeAnnotationMetadata);
    }

    private String renderConstructorInvocation(TypeElement entityType, ExecutableElement constructor, String argsExpression) {
        StringBuilder builder = new StringBuilder("new ")
                .append(entityType.getQualifiedName())
                .append("(");
        List<? extends VariableElement> parameters = constructor.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("(")
                    .append(context.renderRuntimeCastType(parameters.get(i).asType()))
                    .append(") ")
                    .append(argsExpression)
                    .append("[")
                    .append(i)
                    .append("]");
        }
        builder.append(")");
        return builder.toString();
    }

    interface Context {
        List<VariableElement> collectInstanceFields(TypeElement entityType);
        List<ExecutableElement> collectInvokableMethods(TypeElement entityType);
        List<String> expandedFieldNames(TypeElement entityType);
        KoraProcessor.ReflectSpec reflectSpec(TypeElement entityType);
        boolean hasReflectSpec(TypeElement typeElement);
        TypeElement directSuperType(TypeElement entityType);
        boolean isJavaLangObject(TypeElement typeElement);
        void collectTypeConstants(Map<String, String> typeConstants, javax.lang.model.type.TypeMirror typeMirror);
        void setActiveTypeConstants(Map<String, String> typeConstants);
        String escapeJava(String text);
        String buildFieldInfoEntry(TypeElement entityType, VariableElement field, boolean includeAnnotationMetadata);
        String buildMethodInfoEntry(TypeElement entityType, ExecutableElement method, boolean includeAnnotationMetadata);
        String renderNullableTypeLiteral(javax.lang.model.type.TypeMirror typeMirror);
        int modifierMask(java.util.Set<javax.lang.model.element.Modifier> modifiers);
        String renderAnnotationArray(List<? extends javax.lang.model.element.AnnotationMirror> annotations, boolean includeAnnotationMetadata);
        String renderParameterInfoArray(List<? extends VariableElement> parameters, boolean includeAnnotationMetadata);
        String renderNewInstanceExpression(TypeElement typeElement);
        String renderRuntimeCastType(javax.lang.model.type.TypeMirror typeMirror);
        String buildInvokeCase(ExecutableElement method);
        String buildSetCase(TypeElement entityType, VariableElement field);
        String buildGetCase(TypeElement entityType, VariableElement field);
        String buildFieldPresenceSwitch(List<VariableElement> fields, String indent, String defaultExpression);
        String buildFieldInfoSwitch(List<VariableElement> fields, String indent, String defaultExpression, boolean useLazyMetadata);
        String buildExpandedFieldPresenceSwitch(TypeElement entityType, List<String> fieldNames, String indent, String defaultExpression);
        String buildExpandedFieldInfoSwitch(TypeElement entityType, List<VariableElement> localFields, List<String> fieldNames, String indent, String defaultExpression, boolean useLazyMetadata);
        String buildMethodPresenceSwitch(Map<String, List<ExecutableElement>> methodsByName, String indent, String defaultExpression);
        String buildMethodInfoArraySwitch(Map<String, List<ExecutableElement>> methodsByName, String indent, String defaultExpression, boolean useLazyMetadata, boolean mergeInheritedMethods);
    }
}
