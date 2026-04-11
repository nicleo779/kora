package com.nicleo.kora.processor;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.List;

final class EntityTableSourceGenerator {
    private final Context context;

    EntityTableSourceGenerator(Context context) {
        this.context = context;
    }

    String buildMetaSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        String entityTypeName = entityType.getQualifiedName().toString();
        String tableName = context.resolveTableName(entityType);
        String tableConstantName = "TABLE";
        List<VariableElement> tableFields = context.collectTableFields(entityType);
        VariableElement idField = context.resolveIdField(entityType, tableFields);
        KoraProcessor.IdGenerationSpec idGeneration = context.resolveIdGeneration(entityType, idField);

        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("import com.nicleo.kora.core.query.Column;\n");
        source.append("import com.nicleo.kora.core.query.EntityTable;\n\n");
        source.append("public final class ").append(generatedSimpleName)
                .append(" extends EntityTable<").append(entityTypeName).append("> {\n");
        source.append("    public static final ").append(generatedSimpleName).append(' ')
                .append(tableConstantName).append(" = new ").append(generatedSimpleName)
                .append("(\"").append(context.escapeJava(tableName)).append("\", null);\n\n");
        if (idGeneration.generatorInstantiation() != null) {
            source.append("    private static final ").append(KoraProcessor.ID_GENERATOR_TYPE).append(" ID_GENERATOR = ")
                    .append(idGeneration.generatorInstantiation()).append(";\n\n");
        }
        source.append("    public static ").append(generatedSimpleName).append(" as(String alias) {\n");
        source.append("        return new ").append(generatedSimpleName).append("(")
                .append(tableConstantName).append(".tableName(), alias);\n");
        source.append("    }\n\n");
        source.append("    public ").append(generatedSimpleName).append(" alias(String alias) {\n");
        source.append("        return new ").append(generatedSimpleName).append("(this.tableName(), alias);\n");
        source.append("    }\n\n");
        for (VariableElement field : tableFields) {
            String fieldName = field.getSimpleName().toString();
            source.append("    public final Column<").append(entityTypeName).append(", ")
                    .append(context.renderRuntimeCastType(field.asType())).append("> ")
                    .append(fieldName).append(" = column(\"")
                    .append(context.escapeJava(context.resolveColumnName(field))).append("\", ")
                    .append(context.renderRuntimeCastType(field.asType())).append(".class);\n");
        }
        source.append("\n    @Override\n");
        source.append("    @SuppressWarnings(\"unchecked\")\n");
        source.append("    public <V> Column<").append(entityTypeName).append(", V> idColumn() {\n");
        if (idField != null) {
            source.append("        return (Column<").append(entityTypeName).append(", V>) ")
                    .append(idField.getSimpleName().toString()).append(";\n");
        } else {
            source.append("        throw new IllegalStateException(\"No id field configured for table: \" + tableName());\n");
        }
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public ").append(KoraProcessor.ID_STRATEGY_TYPE).append(" idStrategy() {\n");
        source.append("        return ").append(KoraProcessor.ID_STRATEGY_TYPE).append('.').append(idGeneration.strategy()).append(";\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public ").append(KoraProcessor.ID_GENERATOR_TYPE).append(" idGenerator() {\n");
        source.append("        return ").append(idGeneration.generatorInstantiation() == null ? "null" : "ID_GENERATOR").append(";\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public String fieldName(String column) {\n");
        source.append("        return switch (column) {\n");
        for (VariableElement field : tableFields) {
            source.append("            case \"").append(context.escapeJava(context.resolveColumnName(field))).append("\" -> \"")
                    .append(field.getSimpleName()).append("\";\n");
        }
        source.append("            default -> column;\n");
        source.append("        };\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public String columnName(String field) {\n");
        source.append("        return switch (field) {\n");
        for (VariableElement field : tableFields) {
            source.append("            case \"").append(field.getSimpleName()).append("\" -> \"")
                    .append(context.escapeJava(context.resolveColumnName(field))).append("\";\n");
        }
        source.append("            default -> field;\n");
        source.append("        };\n");
        source.append("    }\n");
        source.append("\n    private ").append(generatedSimpleName).append("(String tableName, String alias) {\n");
        source.append("        super(").append(entityTypeName).append(".class, tableName, alias);\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    interface Context {
        String resolveTableName(TypeElement entityType);

        List<VariableElement> collectTableFields(TypeElement entityType);

        VariableElement resolveIdField(TypeElement entityType, List<VariableElement> fields);

        KoraProcessor.IdGenerationSpec resolveIdGeneration(TypeElement entityType, VariableElement idField);

        String escapeJava(String text);

        String renderRuntimeCastType(javax.lang.model.type.TypeMirror typeMirror);

        String resolveColumnName(VariableElement field);
    }
}
