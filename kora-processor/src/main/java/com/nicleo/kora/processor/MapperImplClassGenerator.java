package com.nicleo.kora.processor;

import com.nicleo.kora.core.dynamic.BindSqlNode;
import com.nicleo.kora.core.dynamic.ChooseSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.ForEachSqlNode;
import com.nicleo.kora.core.dynamic.IfSqlNode;
import com.nicleo.kora.core.dynamic.MixedSqlNode;
import com.nicleo.kora.core.dynamic.TextSqlNode;
import com.nicleo.kora.core.dynamic.TrimSqlNode;
import com.nicleo.kora.core.dynamic.WhenSqlNode;
import com.nicleo.kora.core.xml.SqlCommandType;
import com.nicleo.kora.core.xml.SqlNodeDefinition;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;

final class MapperImplClassGenerator {
    private static final String SQL_EXECUTOR_DESC = "Lcom/nicleo/kora/core/runtime/SqlExecutor;";
    private static final String DYNAMIC_SQL_NODE_DESC = "Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;";
    private static final String GENERATED_MAPPER_SUPPORT = "com/nicleo/kora/core/runtime/GeneratedMapperSupport";
    private static final String SQL_NODES = "com/nicleo/kora/core/dynamic/SqlNodes";

    private final Context context;

    MapperImplClassGenerator(Context context) {
        this.context = context;
    }

    byte[] buildMapperImplClass(String generatedQualifiedName,
                                TypeElement mapperType,
                                List<KoraProcessor.MapperMethodSpec> mapperMethods,
                                List<KoraProcessor.MapperCapabilityDelegateSpec> capabilityDelegates,
                                String supportClassName) {
        String classInternalName = AsmUtils.internalName(generatedQualifiedName);
        String mapperInternalName = AsmUtils.internalName(mapperType.getQualifiedName().toString());
        String supportInternalName = AsmUtils.internalName(supportClassName);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, classInternalName, null, "java/lang/Object", new String[]{mapperInternalName});

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "sqlExecutor", SQL_EXECUTOR_DESC, null, null).visitEnd();
        for (KoraProcessor.MapperCapabilityDelegateSpec delegate : capabilityDelegates) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, delegate.fieldName(), AsmUtils.descriptor(delegate.interfaceErasedTypeLiteral()), null, null).visitEnd();
        }
        for (KoraProcessor.MapperMethodSpec mapperMethod : mapperMethods) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    context.statementFieldName(mapperMethod.statement().id()), DYNAMIC_SQL_NODE_DESC, null, null).visitEnd();
        }

        writeClinit(cw, classInternalName, mapperMethods);
        writeConstructor(cw, classInternalName, supportInternalName, capabilityDelegates);
        for (KoraProcessor.MapperMethodSpec mapperMethod : mapperMethods) {
            writeMapperMethod(cw, classInternalName, mapperType, mapperMethod);
        }
        for (KoraProcessor.MapperCapabilityDelegateSpec delegate : capabilityDelegates) {
            for (KoraProcessor.MapperCapabilityMethodSpec method : delegate.methods()) {
                writeCapabilityMethod(cw, classInternalName, delegate, method);
                writeCapabilityBridgeMethodIfNeeded(cw, classInternalName, delegate, method);
            }
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void writeClinit(ClassWriter cw, String classInternalName, List<KoraProcessor.MapperMethodSpec> mapperMethods) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        for (KoraProcessor.MapperMethodSpec mapperMethod : mapperMethods) {
            emitDynamicSqlNode(mv, mapperMethod.statement().rootSqlNode());
            mv.visitFieldInsn(Opcodes.PUTSTATIC, classInternalName, context.statementFieldName(mapperMethod.statement().id()), DYNAMIC_SQL_NODE_DESC);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeConstructor(ClassWriter cw,
                                  String classInternalName,
                                  String supportInternalName,
                                  List<KoraProcessor.MapperCapabilityDelegateSpec> capabilityDelegates) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(" + SQL_EXECUTOR_DESC + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, classInternalName, "sqlExecutor", SQL_EXECUTOR_DESC);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, supportInternalName, "install", "()V", false);
        for (KoraProcessor.MapperCapabilityDelegateSpec delegate : capabilityDelegates) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitTypeInsn(Opcodes.NEW, AsmUtils.internalName(delegate.implTypeName()));
            mv.visitInsn(Opcodes.DUP);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            switch (delegate.constructorMode()) {
                case SQL_EXECUTOR -> mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AsmUtils.internalName(delegate.implTypeName()), "<init>",
                        "(" + SQL_EXECUTOR_DESC + ")V", false);
                case SQL_EXECUTOR_AND_ENTITY_CLASS -> {
                    if (delegate.entityTypeName() == null) {
                        mv.visitInsn(Opcodes.ACONST_NULL);
                    } else {
                        mv.visitLdcInsn(org.objectweb.asm.Type.getType(AsmUtils.descriptor(delegate.entityTypeName())));
                    }
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AsmUtils.internalName(delegate.implTypeName()), "<init>",
                            "(" + SQL_EXECUTOR_DESC + "Ljava/lang/Class;)V", false);
                }
                case SQL_EXECUTOR_AND_ENTITY_TABLE -> {
                    if (delegate.tableTypeName() == null) {
                        mv.visitInsn(Opcodes.ACONST_NULL);
                    } else {
                        mv.visitFieldInsn(Opcodes.GETSTATIC, AsmUtils.internalName(delegate.tableTypeName()), "TABLE",
                                "L" + AsmUtils.internalName(delegate.tableTypeName()) + ";");
                    }
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, AsmUtils.internalName(delegate.implTypeName()), "<init>",
                            "(" + SQL_EXECUTOR_DESC + "Lcom/nicleo/kora/core/query/EntityTable;)V", false);
                }
            }
            mv.visitFieldInsn(Opcodes.PUTFIELD, classInternalName, delegate.fieldName(), AsmUtils.descriptor(delegate.interfaceErasedTypeLiteral()));
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeMapperMethod(ClassWriter cw,
                                   String classInternalName,
                                   TypeElement mapperType,
                                   KoraProcessor.MapperMethodSpec mapperMethod) {
        ExecutableElement method = mapperMethod.method();
        SqlNodeDefinition statement = mapperMethod.statement();
        List<? extends VariableElement> parameters = method.getParameters();
        String methodDescriptor = AsmUtils.methodDescriptor(context.types().erasure(method.getReturnType()),
                parameters.stream().map(parameter -> context.types().erasure(parameter.asType())).toList(), context.types());
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.getSimpleName().toString(), methodDescriptor, null, null);
        mv.visitCode();

        pushStringArray(mv, parameters.stream().map(parameter -> parameter.getSimpleName().toString()).toList());
        pushParameterValuesArray(mv, parameters);

        int valuesLocal = nextLocalIndex(parameters);
        mv.visitVarInsn(Opcodes.ASTORE, valuesLocal);
        int namesLocal = valuesLocal + 1;
        mv.visitVarInsn(Opcodes.ASTORE, namesLocal);

        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, classInternalName, "sqlExecutor", SQL_EXECUTOR_DESC);
        mv.visitLdcInsn(mapperType.getQualifiedName().toString());
        mv.visitLdcInsn(statement.id());
        mv.visitFieldInsn(Opcodes.GETSTATIC, classInternalName, context.statementFieldName(statement.id()), DYNAMIC_SQL_NODE_DESC);
        mv.visitVarInsn(Opcodes.ALOAD, namesLocal);
        mv.visitVarInsn(Opcodes.ALOAD, valuesLocal);

        if (statement.commandType() == SqlCommandType.SELECT) {
            if (context.isPageReturn(method.getReturnType())) {
                int pagingIndex = pagingParameterIndex(parameters);
                mv.visitVarInsn(Opcodes.ALOAD, pagingIndex);
                AsmUtils.pushClassLiteral(mv, context.extractPageElementType(method.getReturnType()), context.types());
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, GENERATED_MAPPER_SUPPORT, "selectPage",
                        "(" + SQL_EXECUTOR_DESC + "Ljava/lang/String;Ljava/lang/String;" + DYNAMIC_SQL_NODE_DESC + "[Ljava/lang/String;[Ljava/lang/Object;Lcom/nicleo/kora/core/query/Paging;Ljava/lang/Class;)Lcom/nicleo/kora/core/query/Page;", false);
                mv.visitInsn(Opcodes.ARETURN);
            } else if (context.isListReturn(method.getReturnType())) {
                AsmUtils.pushClassLiteral(mv, context.extractListElementType(method.getReturnType()), context.types());
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, GENERATED_MAPPER_SUPPORT, "selectList",
                        "(" + SQL_EXECUTOR_DESC + "Ljava/lang/String;Ljava/lang/String;" + DYNAMIC_SQL_NODE_DESC + "[Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/Class;)Ljava/util/List;", false);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                AsmUtils.pushClassLiteral(mv, method.getReturnType(), context.types());
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, GENERATED_MAPPER_SUPPORT, "selectOne",
                        "(" + SQL_EXECUTOR_DESC + "Ljava/lang/String;Ljava/lang/String;" + DYNAMIC_SQL_NODE_DESC + "[Ljava/lang/String;[Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false);
                if (context.types().erasure(method.getReturnType()).getKind() != TypeKind.VOID) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getType(AsmUtils.descriptor(method.getReturnType(), context.types())).getInternalName());
                }
                mv.visitInsn(AsmUtils.returnOpcode(method.getReturnType()));
            }
        } else {
            mv.visitFieldInsn(Opcodes.GETSTATIC, "com/nicleo/kora/core/xml/SqlCommandType", statement.commandType().name(), "Lcom/nicleo/kora/core/xml/SqlCommandType;");
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GENERATED_MAPPER_SUPPORT, "update",
                    "(" + SQL_EXECUTOR_DESC + "Ljava/lang/String;Ljava/lang/String;Lcom/nicleo/kora/core/xml/SqlCommandType;" + DYNAMIC_SQL_NODE_DESC + "[Ljava/lang/String;[Ljava/lang/Object;)I", false);
            if (method.getReturnType().getKind() == TypeKind.INT) {
                mv.visitInsn(Opcodes.IRETURN);
            } else {
                mv.visitInsn(Opcodes.POP);
                mv.visitInsn(Opcodes.RETURN);
            }
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeCapabilityMethod(ClassWriter cw,
                                       String classInternalName,
                                       KoraProcessor.MapperCapabilityDelegateSpec delegate,
                                       KoraProcessor.MapperCapabilityMethodSpec method) {
        String descriptor = methodDescriptor(method.returnType(), method.parameterTypes());
        String bridgeDescriptor = methodDescriptor(method.bridgeReturnType(), method.bridgeParameterTypes());
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method.methodName(), descriptor, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, classInternalName, delegate.fieldName(), AsmUtils.descriptor(delegate.interfaceErasedTypeLiteral()));
        int localIndex = 1;
        for (String parameterType : method.parameterTypes()) {
            mv.visitVarInsn(loadOpcode(parameterType), localIndex);
            localIndex += slotSize(parameterType);
        }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, AsmUtils.internalName(delegate.interfaceErasedTypeLiteral()), method.methodName(), bridgeDescriptor, true);
        castCapabilityReturn(mv, method.bridgeReturnType(), method.returnType());
        mv.visitInsn(returnOpcode(method.returnType()));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeCapabilityBridgeMethodIfNeeded(ClassWriter cw,
                                                     String classInternalName,
                                                     KoraProcessor.MapperCapabilityDelegateSpec delegate,
                                                     KoraProcessor.MapperCapabilityMethodSpec method) {
        String typedDescriptor = methodDescriptor(method.returnType(), method.parameterTypes());
        String bridgeDescriptor = methodDescriptor(method.bridgeReturnType(), method.bridgeParameterTypes());
        if (typedDescriptor.equals(bridgeDescriptor)) {
            return;
        }

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                method.methodName(),
                bridgeDescriptor,
                null,
                null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        int bridgeLocal = 1;
        for (int i = 0; i < method.parameterTypes().size(); i++) {
            String bridgeParamType = method.bridgeParameterTypes().get(i);
            String typedParamType = method.parameterTypes().get(i);
            mv.visitVarInsn(loadOpcode(bridgeParamType), bridgeLocal);
            castBetweenTypes(mv, bridgeParamType, typedParamType);
            bridgeLocal += slotSize(bridgeParamType);
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classInternalName, method.methodName(), typedDescriptor, false);
        castReturnToBridge(mv, method.returnType(), method.bridgeReturnType());
        mv.visitInsn(returnOpcode(method.bridgeReturnType()));
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private int pagingParameterIndex(List<? extends VariableElement> parameters) {
        int localIndex = 1;
        for (VariableElement parameter : parameters) {
            if (context.isPagingType(parameter.asType())) {
                return localIndex;
            }
            localIndex += AsmUtils.slotSize(parameter.asType());
        }
        return -1;
    }

    private int nextLocalIndex(List<? extends VariableElement> parameters) {
        int localIndex = 1;
        for (VariableElement parameter : parameters) {
            localIndex += AsmUtils.slotSize(parameter.asType());
        }
        return localIndex;
    }

    private void pushStringArray(MethodVisitor mv, List<String> values) {
        AsmUtils.pushStringArray(mv, values);
    }

    private void pushParameterValuesArray(MethodVisitor mv, List<? extends VariableElement> parameters) {
        AsmUtils.pushInt(mv, parameters.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        int localIndex = 1;
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            mv.visitVarInsn(AsmUtils.loadOpcode(parameter.asType()), localIndex);
            if (parameter.asType().getKind().isPrimitive()) {
                AsmUtils.box(mv, parameter.asType());
            }
            mv.visitInsn(Opcodes.AASTORE);
            localIndex += AsmUtils.slotSize(parameter.asType());
        }
    }

    private void emitDynamicSqlNode(MethodVisitor mv, DynamicSqlNode node) {
        if (node instanceof TextSqlNode textSqlNode) {
            mv.visitLdcInsn(textSqlNode.getText());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "text", "(Ljava/lang/String;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        if (node instanceof MixedSqlNode mixedSqlNode) {
            AsmUtils.pushInt(mv, mixedSqlNode.getChildren().size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "com/nicleo/kora/core/dynamic/DynamicSqlNode");
            for (int i = 0; i < mixedSqlNode.getChildren().size(); i++) {
                mv.visitInsn(Opcodes.DUP);
                AsmUtils.pushInt(mv, i);
                emitDynamicSqlNode(mv, mixedSqlNode.getChildren().get(i));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "mixed", "([Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        if (node instanceof IfSqlNode ifSqlNode) {
            mv.visitLdcInsn(ifSqlNode.getTest());
            emitDynamicSqlNode(mv, ifSqlNode.getContents());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "ifNode",
                    "(Ljava/lang/String;Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        if (node instanceof TrimSqlNode trimSqlNode) {
            pushNullableString(mv, trimSqlNode.getPrefix());
            pushNullableString(mv, trimSqlNode.getSuffix());
            pushStringArray(mv, trimSqlNode.getPrefixOverrides());
            pushStringArray(mv, trimSqlNode.getSuffixOverrides());
            emitDynamicSqlNode(mv, trimSqlNode.getContents());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "trim",
                    "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        if (node instanceof ForEachSqlNode forEachSqlNode) {
            pushNullableString(mv, forEachSqlNode.getCollection());
            pushNullableString(mv, forEachSqlNode.getItem());
            pushNullableString(mv, forEachSqlNode.getIndex());
            pushNullableString(mv, forEachSqlNode.getOpen());
            pushNullableString(mv, forEachSqlNode.getClose());
            pushNullableString(mv, forEachSqlNode.getSeparator());
            emitDynamicSqlNode(mv, forEachSqlNode.getContents());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "foreach",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        if (node instanceof ChooseSqlNode chooseSqlNode) {
            AsmUtils.pushInt(mv, chooseSqlNode.getWhenNodes().size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "com/nicleo/kora/core/dynamic/WhenSqlNode");
            for (int i = 0; i < chooseSqlNode.getWhenNodes().size(); i++) {
                mv.visitInsn(Opcodes.DUP);
                AsmUtils.pushInt(mv, i);
                emitWhenNode(mv, chooseSqlNode.getWhenNodes().get(i));
                mv.visitInsn(Opcodes.AASTORE);
            }
            if (chooseSqlNode.getOtherwiseNode() == null) {
                mv.visitInsn(Opcodes.ACONST_NULL);
            } else {
                emitDynamicSqlNode(mv, chooseSqlNode.getOtherwiseNode());
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "choose",
                    "([Lcom/nicleo/kora/core/dynamic/WhenSqlNode;Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        if (node instanceof BindSqlNode bindSqlNode) {
            mv.visitLdcInsn(bindSqlNode.getName());
            mv.visitLdcInsn(bindSqlNode.getValueExpression());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "bind",
                    "(Ljava/lang/String;Ljava/lang/String;)Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;", false);
            return;
        }
        throw new IllegalArgumentException("Unsupported sql node type: " + node.getClass().getName());
    }

    private void emitWhenNode(MethodVisitor mv, WhenSqlNode whenSqlNode) {
        mv.visitLdcInsn(whenSqlNode.getTest());
        emitDynamicSqlNode(mv, whenSqlNode.getContents());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, SQL_NODES, "when",
                "(Ljava/lang/String;Lcom/nicleo/kora/core/dynamic/DynamicSqlNode;)Lcom/nicleo/kora/core/dynamic/WhenSqlNode;", false);
    }

    private void pushNullableString(MethodVisitor mv, String value) {
        if (value == null) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private String methodDescriptor(String returnType, List<String> parameterTypes) {
        StringBuilder builder = new StringBuilder("(");
        for (String parameterType : parameterTypes) {
            builder.append(AsmUtils.descriptor(parameterType));
        }
        builder.append(')').append(AsmUtils.descriptor(returnType));
        return builder.toString();
    }

    private int loadOpcode(String typeName) {
        return switch (typeName) {
            case "boolean", "byte", "short", "int", "char" -> Opcodes.ILOAD;
            case "long" -> Opcodes.LLOAD;
            case "float" -> Opcodes.FLOAD;
            case "double" -> Opcodes.DLOAD;
            default -> Opcodes.ALOAD;
        };
    }

    private int returnOpcode(String typeName) {
        return switch (typeName) {
            case "void" -> Opcodes.RETURN;
            case "boolean", "byte", "short", "int", "char" -> Opcodes.IRETURN;
            case "long" -> Opcodes.LRETURN;
            case "float" -> Opcodes.FRETURN;
            case "double" -> Opcodes.DRETURN;
            default -> Opcodes.ARETURN;
        };
    }

    private void castBetweenTypes(MethodVisitor mv, String sourceType, String targetType) {
        if (sourceType.equals(targetType)) {
            return;
        }
        if (isPrimitive(targetType)) {
            unboxToPrimitive(mv, targetType);
            return;
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getType(AsmUtils.descriptor(targetType)).getInternalName());
    }

    private void castReturnToBridge(MethodVisitor mv, String typedReturnType, String bridgeReturnType) {
        if (typedReturnType.equals(bridgeReturnType) || "void".equals(bridgeReturnType)) {
            return;
        }
        if (isPrimitive(typedReturnType) && !isPrimitive(bridgeReturnType)) {
            boxPrimitive(mv, typedReturnType);
        } else if (!isPrimitive(typedReturnType) && isPrimitive(bridgeReturnType)) {
            unboxToPrimitive(mv, bridgeReturnType);
        } else if (!isPrimitive(typedReturnType)) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getType(AsmUtils.descriptor(bridgeReturnType)).getInternalName());
        }
    }

    private void castCapabilityReturn(MethodVisitor mv, String sourceReturnType, String targetReturnType) {
        if (sourceReturnType.equals(targetReturnType) || "void".equals(targetReturnType)) {
            return;
        }
        if (isPrimitive(sourceReturnType) && !isPrimitive(targetReturnType)) {
            boxPrimitive(mv, sourceReturnType);
            mv.visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getType(AsmUtils.descriptor(targetReturnType)).getInternalName());
        } else if (!isPrimitive(sourceReturnType) && isPrimitive(targetReturnType)) {
            unboxToPrimitive(mv, targetReturnType);
        } else if (!isPrimitive(targetReturnType)) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getType(AsmUtils.descriptor(targetReturnType)).getInternalName());
        }
    }

    private boolean isPrimitive(String typeName) {
        return switch (typeName) {
            case "boolean", "byte", "short", "int", "long", "char", "float", "double" -> true;
            default -> false;
        };
    }

    private void boxPrimitive(MethodVisitor mv, String typeName) {
        switch (typeName) {
            case "boolean" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            case "byte" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            case "short" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            case "int" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case "long" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case "char" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            case "float" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case "double" -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            default -> {
            }
        }
    }

    private void unboxToPrimitive(MethodVisitor mv, String typeName) {
        switch (typeName) {
            case "boolean" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            }
            case "byte" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            }
            case "short" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
            }
            case "int" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            }
            case "long" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            }
            case "char" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
            }
            case "float" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            }
            case "double" -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            }
            default -> {
            }
        }
    }

    private int slotSize(String typeName) {
        return switch (typeName) {
            case "long", "double" -> 2;
            default -> 1;
        };
    }

    interface Context {
        Types types();
        boolean isListReturn(TypeMirror returnType);
        boolean isPageReturn(TypeMirror returnType);
        TypeMirror extractListElementType(TypeMirror returnType);
        TypeMirror extractPageElementType(TypeMirror returnType);
        boolean isPagingType(TypeMirror typeMirror);
        String statementFieldName(String statementId);
    }
}
