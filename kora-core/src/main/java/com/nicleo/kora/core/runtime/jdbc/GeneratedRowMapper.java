package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.ParameterInfo;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlExecutorException;
import com.nicleo.kora.core.runtime.TypeConverter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective {@link RowMapper} backed by a {@link GeneratedReflector}.
 *
 * <p>The first row establishes a {@link ColumnPlan column plan} that resolves each ResultSet column
 * to an entity field binding (constructor arg or setter slot). Subsequent rows reuse the plan and
 * a pair of preallocated buffers, so mapping cost is dominated by JDBC reads and field writes.
 *
 * <p>Both plain POJOs (no-arg constructor + setters), Java records (all-args constructor), and
 * hybrid entities (partial constructor + remaining setters) are supported.
 *
 * <p><b>Thread-safety:</b> instances are <i>not</i> thread-safe. Reuse within a single thread for
 * the duration of one result-set iteration.
 */
public final class GeneratedRowMapper<T> implements RowMapper<T> {

    private enum Strategy { SETTERS_ONLY, CONSTRUCTOR_ONLY, CONSTRUCTOR_WITH_SETTERS }

    private static final int[] NO_SLOTS = new int[0];
    private static final Map<MappingCacheKey, EntityMappingPlan> ENTITY_PLANS = new ConcurrentHashMap<>();

    private final GeneratedReflector<T> reflector;
    private final TypeConverter typeConverter;
    private final EntityMappingPlan mapping;
    private final Object[] constructorBuffer; // reused across rows (null if no constructor)
    private final Object[] setterBuffer;      // reused across rows (null if no mixed setters)

    private volatile ColumnPlan plan;

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector, TypeConverter typeConverter) {
        this(entityType, entityType, reflector, typeConverter);
    }

    public GeneratedRowMapper(Class<T> entityType, Type genericEntityType, GeneratedReflector<T> reflector, TypeConverter typeConverter) {
        Objects.requireNonNull(entityType, "entityType");
        Type effectiveEntityType = genericEntityType == null ? entityType : genericEntityType;
        this.reflector = Objects.requireNonNull(reflector, "reflector");
        this.typeConverter = Objects.requireNonNull(typeConverter, "typeConverter");

        MappingCacheKey cacheKey = new MappingCacheKey(effectiveEntityType, reflector.getClass());
        this.mapping = ENTITY_PLANS.computeIfAbsent(cacheKey, ignored -> buildEntityMappingPlan(entityType, effectiveEntityType, reflector));
        this.constructorBuffer = mapping.usesConstructor() ? new Object[mapping.constructorDefaults.length] : null;
        this.setterBuffer = mapping.strategy == Strategy.CONSTRUCTOR_WITH_SETTERS ? new Object[mapping.setterFieldIndex.length] : null;
    }

    private static EntityMappingPlan buildEntityMappingPlan(Class<?> entityType, Type genericEntityType, GeneratedReflector<?> reflector) {
        ParameterInfo[] constructorParams = resolveConstructorParams(reflector, entityType);
        Map<String, Type> typeVariables = resolveTypeVariables(entityType, genericEntityType);
        Object[] constructorDefaults = buildConstructorDefaults(constructorParams, typeVariables);

        FieldBinding[] bindings = buildFieldBindings(reflector, constructorParams, entityType.isRecord(), typeVariables);
        Map<String, FieldBinding> fieldByKey = buildFieldIndex(bindings, reflector);

        int setterCount = countSetterSlots(bindings);
        int[] setterFieldIndex = buildSetterFieldIndex(bindings, setterCount);

        boolean useConstructor = constructorParams.length > 0;
        boolean hasSetters = setterCount > 0;
        Strategy strategy = selectStrategy(useConstructor, hasSetters);
        return new EntityMappingPlan(strategy, fieldByKey, setterFieldIndex, constructorDefaults);
    }

    @Override
    public T mapRow(ResultSet resultSet) throws SQLException {
        ColumnPlan p = ensurePlan(resultSet);
        return switch (mapping.strategy) {
            case SETTERS_ONLY -> mapWithSetters(resultSet, p);
            case CONSTRUCTOR_ONLY -> mapWithConstructor(resultSet, p);
            case CONSTRUCTOR_WITH_SETTERS -> mapWithConstructorAndSetters(resultSet, p);
        };
    }

    // ==================== hot path ====================

    private T mapWithSetters(ResultSet rs, ColumnPlan p) throws SQLException {
        T instance = reflector.newInstance();
        for (ColumnBinding column : p.columns) {
            reflector.set(instance, column.field.fieldIndex, readColumn(rs, column));
        }
        return instance;
    }

    private T mapWithConstructor(ResultSet rs, ColumnPlan p) throws SQLException {
        Object[] args = freshConstructorArgs();
        for (ColumnBinding column : p.columns) {
            int argIndex = column.field.constructorArgIndex;
            if (argIndex >= 0) {
                args[argIndex] = readColumn(rs, column);
            }
        }
        return reflector.newInstance(args);
    }

    private T mapWithConstructorAndSetters(ResultSet rs, ColumnPlan p) throws SQLException {
        Object[] args = freshConstructorArgs();
        Object[] setters = setterBuffer;
        for (ColumnBinding column : p.columns) {
            Object value = readColumn(rs, column);
            FieldBinding field = column.field;
            int argIndex = field.constructorArgIndex;
            if (argIndex >= 0) {
                args[argIndex] = value;
            } else {
                setters[field.setterSlot] = value;
            }
        }
        T instance = reflector.newInstance(args);
        for (int slot : p.populatedSetterSlots) {
            reflector.set(instance, mapping.setterFieldIndex[slot], setters[slot]);
        }
        return instance;
    }

    private Object[] freshConstructorArgs() {
        Object[] buffer = constructorBuffer;
        System.arraycopy(mapping.constructorDefaults, 0, buffer, 0, mapping.constructorDefaults.length);
        return buffer;
    }

    private Object readColumn(ResultSet rs, ColumnBinding column) throws SQLException {
        return typeConverter.cast(rs, column.columnIndex, column.field.targetType, column.columnLabel, column.field.fieldName);
    }

    // ==================== column plan ====================

    private ColumnPlan ensurePlan(ResultSet resultSet) throws SQLException {
        ColumnPlan cached = plan;
        if (cached != null) {
            return cached;
        }
        ColumnPlan built = buildPlan(resultSet.getMetaData());
        plan = built;
        return built;
    }

    private ColumnPlan buildPlan(ResultSetMetaData metaData) throws SQLException {
        int columnCount = metaData.getColumnCount();
        ColumnBinding[] mapped = new ColumnBinding[columnCount];
        int[] setterSlots = new int[columnCount];
        int mappedCount = 0;
        int setterCount = 0;

        for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
            String columnLabel = metaData.getColumnLabel(columnIndex);
            FieldBinding binding = resolveField(columnLabel);
            if (binding == null) {
                continue;
            }
            mapped[mappedCount++] = new ColumnBinding(columnIndex, columnLabel, binding);
            if (binding.setterSlot >= 0) {
                setterSlots[setterCount++] = binding.setterSlot;
            }
        }
        ColumnBinding[] columns = mappedCount == mapped.length ? mapped : Arrays.copyOf(mapped, mappedCount);
        int[] populatedSetterSlots = setterCount == 0 ? NO_SLOTS : Arrays.copyOf(setterSlots, setterCount);
        return new ColumnPlan(columns, populatedSetterSlots);
    }

    private FieldBinding resolveField(String columnLabel) {
        if (columnLabel == null) {
            return null;
        }
        FieldBinding direct = mapping.fieldByKey.get(columnLabel);
        if (direct != null) {
            return direct;
        }
        String lower = columnLabel.toLowerCase(Locale.ROOT);
        return lower.equals(columnLabel) ? null : mapping.fieldByKey.get(lower);
    }

    // ==================== construction ====================

    private static Strategy selectStrategy(boolean useConstructor, boolean hasSetters) {
        if (!useConstructor) {
            return Strategy.SETTERS_ONLY;
        }
        return hasSetters ? Strategy.CONSTRUCTOR_WITH_SETTERS : Strategy.CONSTRUCTOR_ONLY;
    }

    private static ParameterInfo[] resolveConstructorParams(GeneratedReflector<?> reflector, Class<?> entityType) {
        ParameterInfo[] params = reflector.getClassInfo() == null ? null : reflector.getClassInfo().params();
        if (entityType.isRecord() && (params == null || params.length == 0)) {
            throw new SqlExecutorException("No constructor metadata available for record type: " + entityType.getName());
        }
        return params == null ? new ParameterInfo[0] : params;
    }

    private static Object[] buildConstructorDefaults(ParameterInfo[] params, Map<String, Type> typeVariables) {
        Object[] defaults = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            defaults[i] = primitiveDefault(resolveRawType(resolveType(params[i].type(), typeVariables)));
        }
        return defaults;
    }

    private static FieldBinding[] buildFieldBindings(GeneratedReflector<?> reflector,
                                                     ParameterInfo[] constructorParams,
                                                     boolean isRecord,
                                                     Map<String, Type> typeVariables) {
        String[] fieldNames = reflector.getFields();
        FieldBinding[] bindings = new FieldBinding[fieldNames.length];
        int setterSlot = 0;
        for (int i = 0; i < fieldNames.length; i++) {
            String fieldName = fieldNames[i];
            FieldInfo fieldInfo = reflector.getField(i);
            if (fieldInfo == null) {
                throw new SqlExecutorException("Missing field metadata for property: " + fieldName);
            }
            Class<?> targetType = resolveTargetType(fieldInfo.type(), typeVariables);
            int argIndex = indexOfParam(constructorParams, fieldName);
            int slot = (argIndex < 0 && !isRecord) ? setterSlot++ : -1;
            bindings[i] = new FieldBinding(i, fieldName, targetType, argIndex, slot);
        }
        return bindings;
    }

    /**
     * Build a single lookup map keyed by every supported column-label form for each field:
     * exact field name, lowercase field name, lowercase snake_case(field name), and both exact and
     * lowercase alias (when present). This lets {@link #resolveField(String)} succeed with at most
     * two map lookups and no per-column string transformations at runtime.
     */
    private static Map<String, FieldBinding> buildFieldIndex(FieldBinding[] bindings, GeneratedReflector<?> reflector) {
        Map<String, FieldBinding> index = new HashMap<>(bindings.length * 4);
        for (FieldBinding binding : bindings) {
            String name = binding.fieldName;
            index.put(name, binding);
            index.putIfAbsent(name.toLowerCase(Locale.ROOT), binding);
            index.putIfAbsent(camelToSnake(name), binding);

            FieldInfo fieldInfo = reflector.getField(binding.fieldIndex);
            String alias = fieldInfo == null ? null : fieldInfo.alias();
            if (alias != null && !alias.isBlank()) {
                index.put(alias, binding);
                index.putIfAbsent(alias.toLowerCase(Locale.ROOT), binding);
            }
        }
        return Map.copyOf(index);
    }

    private static int countSetterSlots(FieldBinding[] bindings) {
        int count = 0;
        for (FieldBinding binding : bindings) {
            if (binding.setterSlot >= 0) {
                count++;
            }
        }
        return count;
    }

    private static int[] buildSetterFieldIndex(FieldBinding[] bindings, int setterCount) {
        int[] index = new int[setterCount];
        for (FieldBinding binding : bindings) {
            int slot = binding.setterSlot;
            if (slot >= 0) {
                index[slot] = binding.fieldIndex;
            }
        }
        return index;
    }

    private static int indexOfParam(ParameterInfo[] params, String name) {
        for (int i = 0; i < params.length; i++) {
            if (params[i].name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // ==================== type helpers ====================

    private static Class<?> resolveTargetType(Type type, Map<String, Type> typeVariables) {
        Class<?> rawType = resolveRawType(resolveType(type, typeVariables));
        return rawType == null ? null : wrapPrimitive(rawType);
    }

    private static Type resolveType(Type type, Map<String, Type> typeVariables) {
        if (type instanceof TypeVariable<?> typeVariable) {
            Type resolved = typeVariables.get(typeVariable.getName());
            if (resolved != null) {
                return resolved;
            }
            Type[] bounds = typeVariable.getBounds();
            return bounds.length == 0 ? Object.class : resolveType(bounds[0], typeVariables);
        }
        return type;
    }

    private static Map<String, Type> resolveTypeVariables(Class<?> rawType, Type genericType) {
        if (!(genericType instanceof ParameterizedType parameterized)) {
            return Map.of();
        }
        if (parameterized.getRawType() != rawType) {
            return Map.of();
        }
        TypeVariable<?>[] variables = rawType.getTypeParameters();
        Type[] arguments = parameterized.getActualTypeArguments();
        Map<String, Type> mappings = new HashMap<>(variables.length);
        for (int i = 0; i < variables.length && i < arguments.length; i++) {
            mappings.put(variables[i].getName(), arguments[i]);
        }
        return Map.copyOf(mappings);
    }

    private static Class<?> resolveRawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        return null;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.FALSE;
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0F;
            case "double" -> 0D;
            case "char" -> '\0';
            default -> null;
        };
    }

    /** Converts {@code displayName} → {@code display_name}. Returns a lower-case result. */
    private static String camelToSnake(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder builder = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
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

    // ==================== value records ====================

    private record MappingCacheKey(Type entityType,
                                   Class<?> reflectorType) {
    }

    private record EntityMappingPlan(Strategy strategy,
                                     Map<String, FieldBinding> fieldByKey,
                                     int[] setterFieldIndex,
                                     Object[] constructorDefaults) {
        private boolean usesConstructor() {
            return strategy != Strategy.SETTERS_ONLY;
        }
    }

    private record FieldBinding(int fieldIndex,
                                String fieldName,
                                Class<?> targetType,
                                int constructorArgIndex,
                                int setterSlot) {
    }

    private record ColumnBinding(int columnIndex,
                                 String columnLabel,
                                 FieldBinding field) {
    }

    private record ColumnPlan(ColumnBinding[] columns,
                              int[] populatedSetterSlots) {
    }
}
