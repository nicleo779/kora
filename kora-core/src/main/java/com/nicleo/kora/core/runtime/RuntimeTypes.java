package com.nicleo.kora.core.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Objects;

public final class RuntimeTypes {
    private static final Type[] NO_TYPES = new Type[0];

    private RuntimeTypes() {
    }

    public static Type parameterized(Class<?> rawType, Type ownerType, Type... actualTypeArguments) {
        return new SimpleParameterizedType(rawType, ownerType, actualTypeArguments.clone());
    }

    public static Type array(Type componentType) {
        return new SimpleGenericArrayType(componentType);
    }

    public static Type wildcard(Type[] upperBounds, Type[] lowerBounds) {
        Type[] uppers = upperBounds == null || upperBounds.length == 0 ? new Type[]{Object.class} : upperBounds.clone();
        Type[] lowers = lowerBounds == null ? NO_TYPES : lowerBounds.clone();
        return new SimpleWildcardType(uppers, lowers);
    }

    public static Type typeVariable(String name, Type... bounds) {
        Type[] resolvedBounds = bounds == null || bounds.length == 0 ? new Type[]{Object.class} : bounds.clone();
        return new SimpleTypeVariable(name, resolvedBounds);
    }

    private record SimpleParameterizedType(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ParameterizedType parameterizedType)) {
                return false;
            }
            return Objects.equals(rawType, parameterizedType.getRawType())
                    && Objects.equals(ownerType, parameterizedType.getOwnerType())
                    && Arrays.equals(actualTypeArguments, parameterizedType.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments)
                    ^ Objects.hashCode(ownerType)
                    ^ Objects.hashCode(rawType);
        }
    }

    private record SimpleGenericArrayType(Type genericComponentType) implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GenericArrayType arrayType
                    && Objects.equals(genericComponentType, arrayType.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(genericComponentType);
        }
    }

    private record SimpleWildcardType(Type[] upperBounds, Type[] lowerBounds) implements WildcardType {
        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public String toString() {
            if (lowerBounds.length > 0) {
                return "? super " + Arrays.toString(lowerBounds);
            }
            if (upperBounds.length == 0 || (upperBounds.length == 1 && upperBounds[0] == Object.class)) {
                return "?";
            }
            return "? extends " + Arrays.toString(upperBounds);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof WildcardType wildcardType)) {
                return false;
            }
            return Arrays.equals(upperBounds, wildcardType.getUpperBounds())
                    && Arrays.equals(lowerBounds, wildcardType.getLowerBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(upperBounds) ^ Arrays.hashCode(lowerBounds);
        }
    }

    private static final class SimpleTypeVariable implements TypeVariable<Class<RuntimeTypes>> {
        private final String name;
        private final Type[] bounds;

        private SimpleTypeVariable(String name, Type[] bounds) {
            this.name = name;
            this.bounds = bounds;
        }

        @Override
        public Type[] getBounds() {
            return bounds.clone();
        }

        @Override
        public Class<RuntimeTypes> getGenericDeclaration() {
            return RuntimeTypes.class;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public java.lang.reflect.AnnotatedType[] getAnnotatedBounds() {
            return Arrays.stream(bounds)
                    .map(bound -> new java.lang.reflect.AnnotatedType() {
                        @Override
                        public Type getType() {
                            return bound;
                        }

                        @Override
                        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                            return null;
                        }

                        @Override
                        public Annotation[] getAnnotations() {
                            return new Annotation[0];
                        }

                        @Override
                        public Annotation[] getDeclaredAnnotations() {
                            return new Annotation[0];
                        }
                    })
                    .toArray(java.lang.reflect.AnnotatedType[]::new);
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof TypeVariable<?> typeVariable)) {
                return false;
            }
            return Objects.equals(name, typeVariable.getName())
                    && Objects.equals(getGenericDeclaration(), typeVariable.getGenericDeclaration())
                    && Arrays.equals(bounds, typeVariable.getBounds());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getGenericDeclaration()) ^ Objects.hashCode(name) ^ Arrays.hashCode(bounds);
        }
    }
}
