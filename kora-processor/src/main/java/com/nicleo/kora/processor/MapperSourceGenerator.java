package com.nicleo.kora.processor;

import com.nicleo.kora.core.xml.SqlNodeDefinition;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;
import java.util.stream.Collectors;

final class MapperSourceGenerator {
    private final Context context;

    MapperSourceGenerator(Context context) {
        this.context = context;
    }

    String buildSupportSource(KoraProcessor.ScanSpec scanSpec,
                              List<KoraProcessor.ReflectSpec> reflectSpecs,
                              List<TypeElement> entityTypes) {
        String packageName = context.packageNameOf(scanSpec.configType());
        String simpleName = context.supportSimpleName(scanSpec);
        String packageBlock = packageName.isEmpty() ? "" : "package %s;%n%n".formatted(packageName);
        String registrations = reflectSpecs.stream()
                .map(spec -> "            com.nicleo.kora.core.runtime.GeneratedReflectors.register(%s.class, new %s%s());%n"
                        .formatted(spec.typeElement().getQualifiedName(), spec.typeElement().getQualifiedName(), spec.suffix()))
                .collect(Collectors.joining());
        String tableRegistrations = entityTypes.stream()
                .map(entityType -> "            com.nicleo.kora.core.query.Tables.register(%s.class, %s);%n"
                        .formatted(entityType.getQualifiedName(), context.tableConstantReference(entityType)))
                .collect(Collectors.joining());
        return """
                %spublic final class %s {
                    private static volatile boolean installed;

                    private %s() {
                    }

                    public static void install() {
                        if (installed) {
                            return;
                        }
                        synchronized (%s.class) {
                            if (installed) {
                                return;
                            }
                %s%s            installed = true;
                        }
                    }
                }
                """.formatted(packageBlock, simpleName, simpleName, simpleName, registrations, tableRegistrations);
    }

    String buildMapperSource(String packageName,
                             String generatedSimpleName,
                             TypeElement mapperType,
                             List<KoraProcessor.MapperMethodSpec> mapperMethods,
                             List<KoraProcessor.MapperCapabilityDelegateSpec> capabilityDelegates,
                             String supportClassName) {
        String packageBlock = packageName.isEmpty() ? "" : "package %s;%n%n".formatted(packageName);
        String sqlExecutorField = "    private final %s sqlExecutor;%n%n".formatted(KoraProcessor.SQL_EXECUTOR);
        String delegateFields = capabilityDelegates.stream()
                .map(delegate -> "    private final %s %s;%n%n".formatted(delegate.interfaceTypeLiteral(), delegate.fieldName()))
                .collect(Collectors.joining());
        String statementFields = mapperMethods.stream()
                .map(mapperMethod -> "    private static final com.nicleo.kora.core.dynamic.DynamicSqlNode %s = %s;%n%n"
                        .formatted(context.statementFieldName(mapperMethod.statement().id()), context.renderSqlNode(mapperMethod.statement().rootSqlNode())))
                .collect(Collectors.joining());
        String delegateInitializers = capabilityDelegates.stream()
                .map(delegate -> "        this.%s = %s;%n".formatted(delegate.fieldName(), delegate.instantiation()))
                .collect(Collectors.joining());
        String mapperMethodsSource = mapperMethods.stream()
                .map(mapperMethod -> buildMethod(mapperType, mapperMethod.method(), mapperMethod.statement()))
                .collect(Collectors.joining());
        String capabilityMethods = capabilityDelegates.stream()
                .flatMap(delegate -> delegate.methods().stream().map(method -> buildCapabilityMethod(method, delegate.fieldName())))
                .collect(Collectors.joining());
        return """
                %spublic final class %s implements %s {

                %s%s%s    public %s(%s sqlExecutor) {
                        this.sqlExecutor = sqlExecutor;
                        %s.install();
                %s    }

                %s%s}
                """.formatted(
                packageBlock,
                generatedSimpleName,
                mapperType.getQualifiedName(),
                sqlExecutorField,
                delegateFields,
                statementFields,
                generatedSimpleName,
                KoraProcessor.SQL_EXECUTOR,
                supportClassName,
                delegateInitializers,
                mapperMethodsSource,
                capabilityMethods
        );
    }

    private String buildMethod(TypeElement mapperType, ExecutableElement method, SqlNodeDefinition statement) {
        String pagingArgument = context.renderPagingArgument(method);
        return """
                    @Override
                    public %s %s(%s) {
                        java.util.Map<String, Object> params = com.nicleo.kora.core.dynamic.MapperParameters.build(%s, %s);
                        com.nicleo.kora.core.runtime.BoundSql boundSql = com.nicleo.kora.core.dynamic.DynamicSqlRenderer.render(%s, params);
                        com.nicleo.kora.core.runtime.SqlExecutionContext context = new com.nicleo.kora.core.runtime.SqlExecutionContext(
                                sqlExecutor,
                                "%s",
                                "%s",
                                com.nicleo.kora.core.xml.SqlCommandType.%s,
                                %s,
                                %s,
                                true
                        );
                        String sql = boundSql.getSql();
                        Object[] args = com.nicleo.kora.core.dynamic.DynamicSqlArgumentResolver.resolve(boundSql);
                %s    }

                """.formatted(
                method.getReturnType(),
                method.getSimpleName(),
                renderParameters(method),
                renderParameterNames(method),
                renderParameterValues(method),
                context.statementFieldName(statement.id()),
                mapperType.getQualifiedName(),
                statement.id(),
                statement.commandType().name(),
                context.renderResultTypeLiteral(method, statement),
                pagingArgument,
                context.renderExecution(mapperType, method, statement)
        );
    }

    private String buildCapabilityMethod(KoraProcessor.MapperCapabilityMethodSpec method, String fieldName) {
        String invocation = "%s.%s(%s);".formatted(fieldName, method.methodName(), renderParameterValues(method.parameterNames()));
        String body = "void".equals(method.returnType()) ? "        %s%n        return;%n".formatted(invocation) : "        return %s%n".formatted(invocation);
        return """
                    @Override
                    public %s %s(%s) {
                %s    }

                """.formatted(
                method.returnType(),
                method.methodName(),
                renderParameters(method.parameterTypes(), method.parameterNames()),
                body
        );
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

    private String renderParameters(List<String> parameterTypes, List<String> parameterNames) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameterTypes.get(i)).append(' ').append(parameterNames.get(i));
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

    private String renderParameterValues(List<String> parameterNames) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameterNames.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameterNames.get(i));
        }
        return builder.toString();
    }

    interface Context {
        String packageNameOf(TypeElement typeElement);

        String supportSimpleName(KoraProcessor.ScanSpec scanSpec);

        String statementFieldName(String statementId);

        String renderSqlNode(com.nicleo.kora.core.dynamic.DynamicSqlNode node);

        String tableConstantReference(TypeElement entityType);

        String renderPagingArgument(ExecutableElement method);

        String renderResultTypeLiteral(ExecutableElement method, SqlNodeDefinition statement);

        String renderExecution(TypeElement mapperType, ExecutableElement method, SqlNodeDefinition statement);
    }
}
