package com.nicleo.kora.processor;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;

final class AsmUtils {
    private AsmUtils() {
    }

    static String internalName(String qualifiedName) {
        return qualifiedName.replace('.', '/');
    }

    static String descriptor(TypeMirror typeMirror, Types types) {
        if (typeMirror == null) {
            return "Ljava/lang/Object;";
        }
        return switch (typeMirror.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case CHAR -> "C";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case VOID -> "V";
            case ARRAY -> "[" + descriptor(((ArrayType) typeMirror).getComponentType(), types);
            default -> {
                TypeMirror erased = types.erasure(typeMirror);
                if (erased instanceof ArrayType arrayType) {
                    yield "[" + descriptor(arrayType.getComponentType(), types);
                }
                yield "L" + internalName(erased.toString()) + ";";
            }
        };
    }

    static String descriptor(String typeName) {
        return switch (typeName) {
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "char" -> "C";
            case "float" -> "F";
            case "double" -> "D";
            case "void" -> "V";
            default -> {
                if (typeName.endsWith("[]")) {
                    yield "[" + descriptor(typeName.substring(0, typeName.length() - 2));
                }
                String rawTypeName = typeName;
                int genericStart = rawTypeName.indexOf('<');
                if (genericStart >= 0) {
                    rawTypeName = rawTypeName.substring(0, genericStart);
                }
                yield "L" + internalName(rawTypeName) + ";";
            }
        };
    }

    static String methodDescriptor(TypeMirror returnType, List<? extends TypeMirror> parameterTypes, Types types) {
        StringBuilder builder = new StringBuilder("(");
        for (TypeMirror parameterType : parameterTypes) {
            builder.append(descriptor(parameterType, types));
        }
        builder.append(')').append(descriptor(returnType, types));
        return builder.toString();
    }

    static Type asmType(TypeMirror typeMirror, Types types) {
        return Type.getType(descriptor(typeMirror, types));
    }

    static void pushInt(MethodVisitor mv, int value) {
        switch (value) {
            case -1 -> mv.visitInsn(Opcodes.ICONST_M1);
            case 0 -> mv.visitInsn(Opcodes.ICONST_0);
            case 1 -> mv.visitInsn(Opcodes.ICONST_1);
            case 2 -> mv.visitInsn(Opcodes.ICONST_2);
            case 3 -> mv.visitInsn(Opcodes.ICONST_3);
            case 4 -> mv.visitInsn(Opcodes.ICONST_4);
            case 5 -> mv.visitInsn(Opcodes.ICONST_5);
            default -> {
                if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
                    mv.visitIntInsn(Opcodes.BIPUSH, value);
                } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
                    mv.visitIntInsn(Opcodes.SIPUSH, value);
                } else {
                    mv.visitLdcInsn(value);
                }
            }
        }
    }

    static void pushStringArray(MethodVisitor mv, List<String> values) {
        pushInt(mv, values.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int i = 0; i < values.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            mv.visitLdcInsn(values.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    static void pushClassArray(MethodVisitor mv, List<? extends TypeMirror> typesList, Types types) {
        pushInt(mv, typesList.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        for (int i = 0; i < typesList.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            pushClassLiteral(mv, typesList.get(i), types);
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    static void pushClassLiteral(MethodVisitor mv, TypeMirror typeMirror, Types types) {
        TypeMirror erased = types.erasure(typeMirror);
        if (erased.getKind().isPrimitive()) {
            switch (erased.getKind()) {
                case BOOLEAN -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
                case BYTE -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
                case SHORT -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
                case INT -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
                case LONG -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
                case CHAR -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
                case FLOAT -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
                case DOUBLE -> mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
                default -> throw new IllegalArgumentException("Unsupported primitive type: " + erased);
            }
            return;
        }
        mv.visitLdcInsn(Type.getType(descriptor(erased, types)));
    }

    static void castFromObject(MethodVisitor mv, TypeMirror typeMirror, Types types) {
        TypeMirror erased = types.erasure(typeMirror);
        switch (erased.getKind()) {
            case BOOLEAN -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            }
            case BYTE -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            }
            case SHORT -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
            }
            case INT -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            }
            case LONG -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            }
            case CHAR -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
            }
            case FLOAT -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            }
            case DOUBLE -> {
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            }
            default -> mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(descriptor(erased, types)).getInternalName());
        }
    }

    static void box(MethodVisitor mv, TypeMirror typeMirror) {
        switch (typeMirror.getKind()) {
            case BOOLEAN -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            case BYTE -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            case SHORT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            case INT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case LONG -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case CHAR -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            case FLOAT -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case DOUBLE -> mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            default -> {
            }
        }
    }

    static int loadOpcode(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, CHAR -> Opcodes.ILOAD;
            case LONG -> Opcodes.LLOAD;
            case FLOAT -> Opcodes.FLOAD;
            case DOUBLE -> Opcodes.DLOAD;
            default -> Opcodes.ALOAD;
        };
    }

    static int returnOpcode(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case VOID -> Opcodes.RETURN;
            case BOOLEAN, BYTE, SHORT, INT, CHAR -> Opcodes.IRETURN;
            case LONG -> Opcodes.LRETURN;
            case FLOAT -> Opcodes.FRETURN;
            case DOUBLE -> Opcodes.DRETURN;
            default -> Opcodes.ARETURN;
        };
    }

    static int slotSize(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case LONG, DOUBLE -> 2;
            default -> 1;
        };
    }

    static void emitStringEqualsDispatch(MethodVisitor mv, int stringLocal, List<String> cases, java.util.function.IntConsumer caseBody, Runnable defaultBody) {
        Label defaultLabel = new Label();
        Label endLabel = new Label();
        List<Label> labels = new java.util.ArrayList<>(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            labels.add(new Label());
        }
        for (int i = 0; i < cases.size(); i++) {
            mv.visitVarInsn(Opcodes.ALOAD, stringLocal);
            mv.visitLdcInsn(cases.get(i));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(Opcodes.IFNE, labels.get(i));
        }
        mv.visitJumpInsn(Opcodes.GOTO, defaultLabel);
        for (int i = 0; i < cases.size(); i++) {
            mv.visitLabel(labels.get(i));
            caseBody.accept(i);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
        }
        mv.visitLabel(defaultLabel);
        defaultBody.run();
        mv.visitLabel(endLabel);
    }
}
