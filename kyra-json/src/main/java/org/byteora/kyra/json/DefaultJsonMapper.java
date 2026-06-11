package org.byteora.kyra.json;

import org.byteora.kyra.core.EnumSupport;
import org.byteora.kyra.core.IEnum;
import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.core.runtime.ClassInfo;
import org.byteora.kyra.core.runtime.FieldInfo;
import org.byteora.kyra.core.runtime.ParameterInfo;
import org.byteora.kyra.core.runtime.Reflector;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.SerializableString;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.io.NumberOutput;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.ByteArrayBuilder;
import tools.jackson.core.util.JsonRecyclerPools;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class DefaultJsonMapper implements JsonMapper {
    private static final String FLATTEN_ANNOTATION = "org.byteora.kyra.json.Flatten";

    // Per-field write strategies precomputed once per class. Anything not statically decidable
    // (generics, collections, maps, nested objects, custom handlers, IEnum) maps to KIND_DELEGATE
    // and falls back to the generic engine, preserving exact behaviour.
    private static final int KIND_DELEGATE = 0;
    private static final int KIND_STRING = 1;
    private static final int KIND_BOOLEAN = 2;
    private static final int KIND_INT = 3;
    private static final int KIND_LONG = 4;
    private static final int KIND_SHORT = 5;
    private static final int KIND_BYTE = 6;
    private static final int KIND_DOUBLE = 7;
    private static final int KIND_FLOAT = 8;
    private static final int KIND_BIG_INTEGER = 9;
    private static final int KIND_BIG_DECIMAL = 10;
    private static final int KIND_ENUM = 11;
    private static final int KIND_STRINGIFY = 12;
    private static final int KIND_DATE = 13;

    private final JsonFactory jsonFactory;
    private final List<JsonTypeHandler<?>> handlers;
    private final boolean failOnUnknownProperties;
    private final boolean includeNulls;
    private final Map<Class<?>, ClassPlan> classPlans = new ConcurrentHashMap<>();
    private final Map<Class<?>, JsonTypeHandler<Object>> handlerCache = new ConcurrentHashMap<>();
    // Reused per thread so the output buffer is grown once and recycled across calls (steady-state
    // buffer allocation ~0). Reentrant toJson (e.g. a handler that calls back) falls back to a fresh
    // writer because the pooled one is marked in-use.
    private final ThreadLocal<StringBuilderWriter> pooledWriter = ThreadLocal.withInitial(() -> new StringBuilderWriter(512));
    // Same per-thread reuse strategy as pooledWriter, but for the UTF-8 byte sink (toBytes).
    private final ThreadLocal<BytesHolder> pooledBytes = ThreadLocal.withInitial(BytesHolder::new);
    private static final ThreadLocal<char[]> NUMBER_CHARS = ThreadLocal.withInitial(() -> new char[24]);
    private static final Map<TypeVariable<?>, Type> EMPTY_VARIABLES = Map.of();
    private static final ConcurrentHashMap<Class<?>, EnumLookup> ENUM_LOOKUPS = new ConcurrentHashMap<>();

    private static final JsonTypeHandler<Object> NO_HANDLER = new JsonTypeHandler<>() {
        @Override
        public boolean supports(Type type) {
            return false;
        }

        @Override
        public void write(JsonWriterContext context, Object value) {
        }

        @Override
        public Object read(JsonReaderContext context, Type type) {
            return null;
        }
    };

    private DefaultJsonMapper(List<JsonTypeHandler<?>> handlers, boolean failOnUnknownProperties, boolean includeNulls) {
        // A thread-local recycler pool avoids the concurrent-deque pool's per-call acquire/release
        // contention (visible in profiling) when a mapper is reused across many calls on a thread.
        this.jsonFactory = JsonFactory.builder()
                .recyclerPool(JsonRecyclerPools.threadLocalPool())
                // writeTo(OutputStream) must not close the caller's stream when the generator closes.
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER)
                .build();
        this.handlers = List.copyOf(handlers);
        this.failOnUnknownProperties = failOnUnknownProperties;
        this.includeNulls = includeNulls;
    }

    @Override
    public String toJson(Object value) {
        // String output always uses the pooled char writer: it produces exactly one String (what the
        // caller wants) and allocates ~40% less than routing through the UTF-8 byte sink, which would
        // add a byte[] materialization plus a byte[]->String decode. Byte-oriented callers that want
        // the fastest large-payload path should use toBytes()/writeTo(OutputStream) instead.
        StringBuilderWriter pooled = pooledWriter.get();
        boolean reuse = !pooled.inUse();
        StringBuilderWriter writer = reuse ? pooled : new StringBuilderWriter(256);
        if (reuse) {
            writer.acquire();
        }
        try {
            try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), writer)) {
                writeRoot(generator, value);
            }
            return writer.toString();
        } catch (IOException ex) {
            throw new JsonException("Failed to write JSON", ex);
        } finally {
            if (reuse) {
                writer.release();
            }
        }
    }

    @Override
    public byte[] toBytes(Object value) {
        // Byte path: the UTF8JsonGenerator writes property names via SerializedString.asQuotedUTF8()
        // (pre-escaped UTF-8, pure arraycopy) and skips the char->String->UTF-8 round trip of toJson.
        BytesHolder pooled = pooledBytes.get();
        boolean reuse = !pooled.inUse;
        ByteArrayBuilder builder = reuse ? pooled.builder : new ByteArrayBuilder(256);
        if (reuse) {
            pooled.inUse = true;
            builder.reset();
        }
        try {
            try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), builder)) {
                writeRoot(generator, value);
            }
            return builder.toByteArray();
        } catch (IOException ex) {
            throw new JsonException("Failed to write JSON", ex);
        } finally {
            if (reuse) {
                pooled.inUse = false;
            } else {
                builder.release();
            }
        }
    }

    @Override
    public void writeTo(OutputStream out, Object value) {
        // AUTO_CLOSE_TARGET is disabled on the factory, so generator.close() flushes and returns its
        // recycler buffers without closing the caller's stream.
        try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), out)) {
            writeRoot(generator, value);
        } catch (IOException ex) {
            throw new JsonException("Failed to write JSON", ex);
        }
    }

    private void writeRoot(JsonGenerator generator, Object value) throws IOException {
        writeValue(generator, value, value == null ? Object.class : value.getClass(), EMPTY_VARIABLES);
    }

    private void writeIntNumber(JsonGenerator generator, int value) throws IOException {
        char[] buffer = NUMBER_CHARS.get();
        int length = NumberOutput.outputInt(value, buffer, 0);
        generator.writeNumber(buffer, 0, length);
    }

    private void writeLongNumber(JsonGenerator generator, long value) throws IOException {
        char[] buffer = NUMBER_CHARS.get();
        int length = NumberOutput.outputLong(value, buffer, 0);
        generator.writeNumber(buffer, 0, length);
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return fromJson(json, (Type) type);
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        // String input already lives in the heap as UTF-16; re-encoding to UTF-8 for the byte parser
        // costs more than ReaderBasedJsonParser saves on large payloads. Use the byte parser only on
        // {@link #fromBytes(byte[], Type)} where the bytes are already available.
        try (JsonParser parser = jsonFactory.createParser(ObjectReadContext.empty(), new StringReader(json))) {
            return readRoot(parser, type);
        } catch (JsonReadException ex) {
            throw ex.withRoot(typeName(Types.rawType(type)));
        } catch (IOException ex) {
            throw new JsonException("Failed to read JSON", ex);
        }
    }

    @Override
    public <T> T fromBytes(byte[] json, Class<T> type) {
        return fromBytes(json, (Type) type);
    }

    @Override
    public <T> T fromBytes(byte[] json, Type type) {
        // The UTF-8 byte parser is jackson's fastest read path and avoids decoding the input into
        // an intermediate String first.
        try (JsonParser parser = jsonFactory.createParser(ObjectReadContext.empty(), json)) {
            return readRoot(parser, type);
        } catch (JsonReadException ex) {
            throw ex.withRoot(typeName(Types.rawType(type)));
        } catch (IOException ex) {
            throw new JsonException("Failed to read JSON", ex);
        }
    }

    private <T> T readRoot(JsonParser parser, Type type) throws IOException {
        if (parser.nextToken() == null) {
            throw new JsonException("JSON input is empty");
        }
        Object value = readValue(parser, type, EMPTY_VARIABLES);
        if (parser.nextToken() != null) {
            throw new JsonException("Unexpected trailing JSON content");
        }
        return (T) value;
    }

    private void writeValue(JsonGenerator generator, Object value, Type type, Map<TypeVariable<?>, Type> variables) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        Type resolvedType = Types.resolve(type, variables);
        JsonTypeHandler<Object> handler = handler(resolvedType);
        if (handler == null && resolvedType == Object.class) {
            handler = handler(value.getClass());
        }
        if (handler != null) {
            handler.write(new WriterContext(generator, variables), value);
            return;
        }
        if (value instanceof IEnum<?> iEnum) {
            writeValue(generator, iEnum.getValue(), Object.class, EMPTY_VARIABLES);
            return;
        }
        Class<?> rawType = Types.rawType(resolvedType);
        if (rawType == Object.class) {
            rawType = value.getClass();
            resolvedType = rawType;
        }
        if (writeSimpleValue(generator, value, rawType)) {
            return;
        }
        if (rawType.isArray()) {
            writeArray(generator, value, Types.arrayElementType(resolvedType));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            writeIterable(generator, iterable, Types.collectionElementType(resolvedType));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeMap(generator, map, Types.mapValueType(resolvedType));
            return;
        }
        writeObject(generator, value, resolvedType, rawType);
    }

    private boolean writeSimpleValue(JsonGenerator generator, Object value, Class<?> rawType) throws IOException {
        if (rawType == String.class || rawType == Character.class || rawType == char.class) {
            generator.writeString(String.valueOf(value));
        } else if (rawType == boolean.class || rawType == Boolean.class) {
            generator.writeBoolean((Boolean) value);
        } else if (value instanceof Integer intValue) {
            writeIntNumber(generator, intValue);
        } else if (value instanceof Long longValue) {
            writeLongNumber(generator, longValue);
        } else if (value instanceof Double doubleValue) {
            generator.writeNumber(doubleValue);
        } else if (value instanceof Float floatValue) {
            generator.writeNumber(floatValue);
        } else if (value instanceof Short shortValue) {
            writeIntNumber(generator, shortValue);
        } else if (value instanceof Byte byteValue) {
            writeIntNumber(generator, byteValue);
        } else if (value instanceof BigInteger bigInteger) {
            generator.writeNumber(bigInteger);
        } else if (value instanceof BigDecimal bigDecimal) {
            generator.writeNumber(bigDecimal);
        } else if (value instanceof Number number) {
            generator.writeNumber(number.toString());
        } else if (rawType.isEnum()) {
            generator.writeString(((Enum<?>) value).name());
        } else if (value instanceof Date date) {
            // Render legacy Date as ISO-8601 instant (locale-independent, round-trippable) rather
            // than the non-standard Date.toString() form.
            generator.writeString(date.toInstant().toString());
        } else if (value instanceof UUID || value instanceof LocalDate || value instanceof LocalDateTime
                || value instanceof OffsetDateTime || value instanceof Instant || value instanceof ZonedDateTime
                || value instanceof LocalTime || value instanceof OffsetTime) {
            // These types' toString() is already the canonical/ISO-8601 form.
            generator.writeString(value.toString());
        } else {
            return false;
        }
        return true;
    }

    private void writeArray(JsonGenerator generator, Object value, Type elementType) throws IOException {
        Class<?> component = value.getClass().getComponentType();
        if (component != null && component.isPrimitive()) {
            writePrimitiveArray(generator, value);
            return;
        }
        // Reference arrays: cast to Object[] and index directly instead of going through the
        // reflective Array.get, which is markedly slower on the hot per-element loop.
        generator.writeStartArray();
        for (Object element : (Object[]) value) {
            writeValue(generator, element, elementType, EMPTY_VARIABLES);
        }
        generator.writeEndArray();
    }

    /**
     * Serializes a primitive array without boxing each element. {@code int[]}/{@code long[]}/
     * {@code double[]} delegate to Jackson's bulk {@code writeArray} (brackets included); other
     * primitive component types keep per-element loops. {@code byte[]}/{@code char[]} retain their
     * existing semantics (array of numbers / array of single-char strings), not base64.
     */
    private void writePrimitiveArray(JsonGenerator generator, Object value) throws IOException {
        if (value instanceof int[] array) {
            generator.writeArray(array, 0, array.length);
            return;
        }
        if (value instanceof long[] array) {
            generator.writeArray(array, 0, array.length);
            return;
        }
        if (value instanceof double[] array) {
            generator.writeArray(array, 0, array.length);
            return;
        }
        generator.writeStartArray();
        if (value instanceof boolean[] array) {
            for (boolean element : array) {
                generator.writeBoolean(element);
            }
        } else if (value instanceof byte[] array) {
            for (byte element : array) {
                writeIntNumber(generator, element);
            }
        } else if (value instanceof short[] array) {
            for (short element : array) {
                writeIntNumber(generator, element);
            }
        } else if (value instanceof float[] array) {
            for (float element : array) {
                generator.writeNumber(element);
            }
        } else if (value instanceof char[] array) {
            for (char element : array) {
                generator.writeString(String.valueOf(element));
            }
        }
        generator.writeEndArray();
    }

    private void writeIterable(JsonGenerator generator, Iterable<?> iterable, Type elementType) throws IOException {
        // The declared element type is constant across the collection, so classify it once instead of
        // re-resolving the type and rescanning handlers on every element inside writeValue.
        Type resolved = Types.resolve(elementType, EMPTY_VARIABLES);
        int kind = scalarKind(resolved, Types.rawType(resolved));
        // toJson(list) passes the runtime class (e.g. ArrayList) with no generic args, so the declared
        // element type is Object; infer a scalar kind from the actual elements when homogeneous.
        if (kind == KIND_DELEGATE && iterable instanceof List<?> list) {
            kind = inferListScalarKind(list);
        }
        if (kind == KIND_INT && iterable instanceof List<?> list && writeIntListArray(generator, list)) {
            return;
        }
        if (kind == KIND_LONG && iterable instanceof List<?> list && writeLongListArray(generator, list)) {
            return;
        }
        generator.writeStartArray();
        if (kind == KIND_DELEGATE || kind == KIND_ENUM) {
            for (Object element : iterable) {
                writeValue(generator, element, elementType, EMPTY_VARIABLES);
            }
        } else if (iterable instanceof List<?> list) {
            writeListScalars(generator, list, kind);
        } else {
            for (Object element : iterable) {
                if (element == null) {
                    generator.writeNull();
                } else {
                    writeScalarByKind(generator, element, kind);
                }
            }
        }
        generator.writeEndArray();
    }

    /**
     * When a {@link List} of non-null integers is serialized, copy into a primitive buffer and emit
     * via Jackson's bulk {@code writeArray} (brackets included). Returns {@code false} if any element
     * is null so the caller can fall back to the generic scalar path.
     */
    private boolean writeIntListArray(JsonGenerator generator, List<?> list) throws IOException {
        int size = list.size();
        int[] buffer = new int[size];
        for (int i = 0; i < size; i++) {
            Object element = list.get(i);
            if (element == null) {
                return false;
            }
            buffer[i] = ((Number) element).intValue();
        }
        generator.writeArray(buffer, 0, size);
        return true;
    }

    private boolean writeLongListArray(JsonGenerator generator, List<?> list) throws IOException {
        int size = list.size();
        long[] buffer = new long[size];
        for (int i = 0; i < size; i++) {
            Object element = list.get(i);
            if (element == null) {
                return false;
            }
            buffer[i] = ((Number) element).longValue();
        }
        generator.writeArray(buffer, 0, size);
        return true;
    }

    /**
     * When {@code toJson(list)} is called without a {@link TypeRef}, the declared element type is
     * {@link Object}; inspect the list contents and return a scalar kind only when every non-null
     * element shares the same wrapper type.
     */
    private int inferListScalarKind(List<?> list) {
        if (list.isEmpty()) {
            return KIND_DELEGATE;
        }
        Object first = list.get(0);
        if (first == null) {
            return KIND_DELEGATE;
        }
        if (first instanceof Integer) {
            return homogeneousListKind(list, Integer.class) ? KIND_INT : KIND_DELEGATE;
        }
        if (first instanceof Long) {
            return homogeneousListKind(list, Long.class) ? KIND_LONG : KIND_DELEGATE;
        }
        if (first instanceof String) {
            return homogeneousListKind(list, String.class) ? KIND_STRING : KIND_DELEGATE;
        }
        if (first instanceof Boolean) {
            return homogeneousListKind(list, Boolean.class) ? KIND_BOOLEAN : KIND_DELEGATE;
        }
        if (first instanceof Double) {
            return homogeneousListKind(list, Double.class) ? KIND_DOUBLE : KIND_DELEGATE;
        }
        return KIND_DELEGATE;
    }

    private static boolean homogeneousListKind(List<?> list, Class<?> elementType) {
        int size = list.size();
        for (int i = 1; i < size; i++) {
            Object element = list.get(i);
            if (element != null && !elementType.isInstance(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Indexed {@link List} access avoids iterator allocation and lets integral kinds write primitive
     * values directly instead of routing through the boxed {@link #writeScalarByKind} switch.
     */
    private void writeListScalars(JsonGenerator generator, List<?> list, int kind) throws IOException {
        int size = list.size();
        switch (kind) {
            case KIND_INT -> {
                for (int i = 0; i < size; i++) {
                    Object element = list.get(i);
                    if (element == null) {
                        generator.writeNull();
                    } else {
                        writeIntNumber(generator, ((Number) element).intValue());
                    }
                }
            }
            case KIND_LONG -> {
                for (int i = 0; i < size; i++) {
                    Object element = list.get(i);
                    if (element == null) {
                        generator.writeNull();
                    } else {
                        writeLongNumber(generator, ((Number) element).longValue());
                    }
                }
            }
            case KIND_BOOLEAN -> {
                for (int i = 0; i < size; i++) {
                    Object element = list.get(i);
                    if (element == null) {
                        generator.writeNull();
                    } else {
                        generator.writeBoolean((Boolean) element);
                    }
                }
            }
            default -> {
                for (int i = 0; i < size; i++) {
                    Object element = list.get(i);
                    if (element == null) {
                        generator.writeNull();
                    } else {
                        writeScalarByKind(generator, element, kind);
                    }
                }
            }
        }
    }

    private void writeMap(JsonGenerator generator, Map<?, ?> map, Type valueType) throws IOException {
        generator.writeStartObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new JsonException("JSON object keys cannot be null");
            }
            generator.writeName(String.valueOf(entry.getKey()));
            writeValue(generator, entry.getValue(), valueType, EMPTY_VARIABLES);
        }
        generator.writeEndObject();
    }

    private void writeObject(JsonGenerator generator, Object value, Type type, Class<?> rawType) throws IOException {
        generator.writeStartObject();
        writeObjectFields(generator, value, type, rawType, "");
        generator.writeEndObject();
    }

    private void writeObjectFields(JsonGenerator generator, Object value, Type type, Class<?> rawType, String prefix) throws IOException {
        ClassPlan plan = classPlan(rawType);
        Reflector<Object> reflector = plan.reflector;
        Map<TypeVariable<?>, Type> variables = type == rawType ? plan.rawTypeVariables : Types.typeVariables(type);
        boolean noPrefix = prefix.isEmpty();
        int fieldCount = plan.jsonNames.length;
        for (int i = 0; i < fieldCount; i++) {
            String flattenPrefix = plan.flattenPrefixes[i];
            if (flattenPrefix != null) {
                Object fieldValue = reflector.get(value, i);
                if (fieldValue == null) {
                    continue;
                }
                Type fieldType = Types.resolve(plan.fieldTypes[i], variables);
                writeFlattened(generator, fieldValue, fieldType, noPrefix ? flattenPrefix : prefix + flattenPrefix);
                continue;
            }
            writeJsonField(generator, reflector, plan, value, i, variables, noPrefix, prefix);
        }
    }

    private void writeJsonField(JsonGenerator generator, Reflector<Object> reflector, ClassPlan plan, Object value,
                                int index, Map<TypeVariable<?>, Type> variables, boolean noPrefix, String prefix) throws IOException {
        int kind = plan.scalarKinds[index];
        Class<?> rawType = plan.fieldRawTypes[index];
        if (isPrimitiveScalar(kind, rawType)) {
            if (noPrefix) {
                generator.writeName(plan.encodedNames[index]);
            } else {
                generator.writeName(prefix + plan.jsonNames[index]);
            }
            writePrimitiveScalar(generator, reflector, value, index, kind);
            return;
        }
        Object fieldValue = reflector.get(value, index);
        if (fieldValue == null && !includeNulls) {
            return;
        }
        if (noPrefix) {
            generator.writeName(plan.encodedNames[index]);
        } else {
            generator.writeName(prefix + plan.jsonNames[index]);
        }
        if (fieldValue == null) {
            generator.writeNull();
        } else {
            writeScalarOrDelegate(generator, fieldValue, plan, index, variables);
        }
    }

    private static boolean isPrimitiveScalar(int kind, Class<?> rawType) {
        return rawType.isPrimitive()
                && kind >= KIND_BOOLEAN
                && kind <= KIND_FLOAT;
    }

    private void writePrimitiveScalar(JsonGenerator generator, Reflector<Object> reflector, Object value, int index, int kind)
            throws IOException {
        switch (kind) {
            case KIND_BOOLEAN -> generator.writeBoolean(reflector.getBoolean(value, index));
            case KIND_BYTE -> writeIntNumber(generator, reflector.getByte(value, index));
            case KIND_SHORT -> writeIntNumber(generator, reflector.getShort(value, index));
            case KIND_INT -> writeIntNumber(generator, reflector.getInt(value, index));
            case KIND_LONG -> writeLongNumber(generator, reflector.getLong(value, index));
            case KIND_FLOAT -> generator.writeNumber(reflector.getFloat(value, index));
            case KIND_DOUBLE -> generator.writeNumber(reflector.getDouble(value, index));
            default -> throw new IllegalStateException("Unsupported primitive scalar kind: " + kind);
        }
    }

    private void writeScalarOrDelegate(JsonGenerator generator, Object fieldValue, ClassPlan plan, int index,
                                       Map<TypeVariable<?>, Type> variables) throws IOException {
        int kind = plan.scalarKinds[index];
        if (kind == KIND_ENUM) {
            // Field path can use the per-class pre-encoded constant names, indexed by ordinal.
            generator.writeString(plan.enumConstantNames[index][((Enum<?>) fieldValue).ordinal()]);
            return;
        }
        if (kind == KIND_DELEGATE) {
            writeValue(generator, fieldValue, plan.fieldTypes[index], variables);
            return;
        }
        writeScalarByKind(generator, fieldValue, kind);
    }

    /**
     * Writes a boxed scalar value by its precomputed {@code KIND_*}. Excludes {@code KIND_ENUM}
     * (handled via per-class pre-encoded names) and {@code KIND_DELEGATE} (generic engine).
     */
    private void writeScalarByKind(JsonGenerator generator, Object value, int kind) throws IOException {
        switch (kind) {
            case KIND_STRING -> generator.writeString(value instanceof String s ? s : String.valueOf(value));
            case KIND_BOOLEAN -> generator.writeBoolean((Boolean) value);
            case KIND_INT -> writeIntNumber(generator, ((Number) value).intValue());
            case KIND_LONG -> writeLongNumber(generator, ((Number) value).longValue());
            case KIND_SHORT -> writeIntNumber(generator, ((Number) value).shortValue());
            case KIND_BYTE -> writeIntNumber(generator, ((Number) value).byteValue());
            case KIND_DOUBLE -> generator.writeNumber(((Number) value).doubleValue());
            case KIND_FLOAT -> generator.writeNumber(((Number) value).floatValue());
            case KIND_BIG_INTEGER -> generator.writeNumber((BigInteger) value);
            case KIND_BIG_DECIMAL -> generator.writeNumber((BigDecimal) value);
            case KIND_STRINGIFY -> generator.writeString(value.toString());
            case KIND_DATE -> generator.writeString(((Date) value).toInstant().toString());
            default -> throw new IllegalStateException("Unsupported scalar kind: " + kind);
        }
    }

    private void writeFlattened(JsonGenerator generator, Object value, Type type, String prefix) throws IOException {
        if (value instanceof Map<?, ?> map) {
            Type valueType = Types.mapValueType(type);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw new JsonException("JSON object keys cannot be null");
                }
                Object entryValue = entry.getValue();
                if (entryValue == null && !includeNulls) {
                    continue;
                }
                generator.writeName(prefix + entry.getKey());
                writeValue(generator, entryValue, valueType, EMPTY_VARIABLES);
            }
            return;
        }
        writeObjectFields(generator, value, type, value.getClass(), prefix);
    }

    private Object readValue(JsonParser parser, Type type, Map<TypeVariable<?>, Type> variables) throws IOException {
        Type resolvedType = Types.resolve(type, variables);
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return defaultValue(Types.rawType(resolvedType));
        }
        JsonTypeHandler<Object> handler = handler(resolvedType);
        if (handler != null) {
            return handler.read(new ReaderContext(parser, variables), resolvedType);
        }
        Class<?> rawType = Types.rawType(resolvedType);
        if (rawType == Object.class) {
            return readUntyped(parser);
        }
        if (EnumSupport.isIEnum(rawType)) {
            return readIEnum(parser, rawType, variables);
        }
        if (isScalarType(rawType)) {
            return readSimpleValue(parser, rawType);
        }
        if (rawType.isArray()) {
            return readArray(parser, rawType, Types.arrayElementType(resolvedType));
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            return readCollection(parser, rawType, Types.collectionElementType(resolvedType));
        }
        if (Map.class.isAssignableFrom(rawType)) {
            Type keyType = Types.mapKeyType(resolvedType);
            if (Types.rawType(keyType) != String.class) {
                throw new JsonException("Only Map<String, V> is supported for JSON objects");
            }
            return readMap(parser, Types.mapValueType(resolvedType));
        }
        return readObject(parser, resolvedType, rawType);
    }

    private Object readSimpleValue(JsonParser parser, Class<?> rawType) {
        try {
            return readSimpleValue0(parser, rawType);
        } catch (JsonException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw coercionError(parser, rawType, ex);
        }
    }

    private Object readSimpleValue0(JsonParser parser, Class<?> rawType) {
        if (rawType == String.class) {
            // getValueAsString coerces numbers and booleans to their textual form (123, true).
            return parser.getValueAsString();
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return readBoolean(parser, rawType);
        }
        if (isNumeric(rawType)) {
            return readNumeric(parser, rawType);
        }
        if (rawType == char.class || rawType == Character.class) {
            String value = parser.getValueAsString();
            return value == null || value.isEmpty() ? defaultValue(rawType) : value.charAt(0);
        }
        if (rawType.isEnum()) {
            return resolveEnum(rawType, parser.getValueAsString());
        }
        String value = parser.getValueAsString();
        if (rawType == UUID.class) {
            return UUID.fromString(value);
        }
        if (rawType == LocalDate.class) {
            return LocalDate.parse(value);
        }
        if (rawType == LocalDateTime.class) {
            return LocalDateTime.parse(value);
        }
        if (rawType == OffsetDateTime.class) {
            return OffsetDateTime.parse(value);
        }
        if (rawType == ZonedDateTime.class) {
            return ZonedDateTime.parse(value);
        }
        if (rawType == Instant.class) {
            return Instant.parse(value);
        }
        if (rawType == LocalTime.class) {
            return LocalTime.parse(value);
        }
        if (rawType == OffsetTime.class) {
            return OffsetTime.parse(value);
        }
        if (rawType == Date.class) {
            // Accept an epoch-millis number or an ISO-8601 instant string; mirrors the ISO write form
            // and tolerates the numeric-timestamp form other libraries emit.
            if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                return new Date(parser.getLongValue());
            }
            return Date.from(Instant.parse(value));
        }
        return null;
    }

    /**
     * Coerces the current token to a boolean the way Jackson databind does: native JSON booleans
     * pass through, numbers are true when non-zero, and the strings {@code true}/{@code false}/{@code 1}/{@code 0}
     * (case-insensitive, trimmed) are accepted. A blank string yields the type's default.
     */
    private Object readBoolean(JsonParser parser, Class<?> rawType) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        }
        if (token == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue() != 0d;
        }
        String text = parser.getValueAsString();
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                return defaultValue(rawType);
            }
            if (text.equalsIgnoreCase("true") || text.equals("1")) {
                return Boolean.TRUE;
            }
            if (text.equalsIgnoreCase("false") || text.equals("0")) {
                return Boolean.FALSE;
            }
        }
        throw new IllegalArgumentException("not a boolean: " + text);
    }

    /**
     * Coerces the current token to the requested numeric type, accepting both JSON numbers and
     * numeric strings (e.g. {@code "123"}). Fractional inputs are truncated toward zero when the
     * target is integral, mirroring Jackson databind; out-of-range values fail loudly.
     */
    private Object readNumeric(JsonParser parser, Class<?> rawType) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            if (rawType == BigInteger.class) {
                return parser.getBigIntegerValue();
            }
            if (rawType == BigDecimal.class) {
                return parser.getDecimalValue();
            }
            if (rawType == double.class || rawType == Double.class) {
                return parser.getDoubleValue();
            }
            if (rawType == float.class || rawType == Float.class) {
                return parser.getFloatValue();
            }
            return narrowIntegral(parser.getLongValue(), rawType);
        }
        if (token == JsonToken.VALUE_STRING) {
            String text = parser.getValueAsString();
            text = text == null ? "" : text.trim();
            if (text.isEmpty()) {
                return defaultValue(rawType);
            }
            return fromBigDecimal(new BigDecimal(text), rawType);
        }
        // VALUE_NUMBER_FLOAT, or any other token that getDecimalValue can coerce.
        return fromBigDecimal(parser.getDecimalValue(), rawType);
    }

    private Object fromBigDecimal(BigDecimal number, Class<?> rawType) {
        if (rawType == BigDecimal.class) {
            return number;
        }
        if (rawType == BigInteger.class) {
            return number.toBigInteger();
        }
        if (rawType == double.class || rawType == Double.class) {
            return number.doubleValue();
        }
        if (rawType == float.class || rawType == Float.class) {
            return number.floatValue();
        }
        // toBigInteger truncates any fraction toward zero; longValueExact then guards the range.
        return narrowIntegral(number.toBigInteger().longValueExact(), rawType);
    }

    private Object narrowIntegral(long value, Class<?> rawType) {
        if (rawType == long.class || rawType == Long.class) {
            return value;
        }
        if (rawType == int.class || rawType == Integer.class) {
            return (int) requireRange(value, Integer.MIN_VALUE, Integer.MAX_VALUE, rawType);
        }
        if (rawType == short.class || rawType == Short.class) {
            return (short) requireRange(value, Short.MIN_VALUE, Short.MAX_VALUE, rawType);
        }
        return (byte) requireRange(value, Byte.MIN_VALUE, Byte.MAX_VALUE, rawType);
    }

    private long requireRange(long value, long min, long max, Class<?> rawType) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(value + " out of range for " + typeName(rawType));
        }
        return value;
    }

    private static boolean isNumeric(Class<?> rawType) {
        return rawType == byte.class || rawType == Byte.class
                || rawType == short.class || rawType == Short.class
                || rawType == int.class || rawType == Integer.class
                || rawType == long.class || rawType == Long.class
                || rawType == float.class || rawType == Float.class
                || rawType == double.class || rawType == Double.class
                || rawType == BigInteger.class || rawType == BigDecimal.class;
    }

    private static boolean isScalarType(Class<?> rawType) {
        return rawType == String.class
                || rawType == char.class || rawType == Character.class
                || rawType == boolean.class || rawType == Boolean.class
                || isNumeric(rawType)
                || rawType.isEnum()
                || rawType == UUID.class
                || rawType == LocalDate.class || rawType == LocalDateTime.class
                || rawType == OffsetDateTime.class || rawType == ZonedDateTime.class
                || rawType == Instant.class
                || rawType == LocalTime.class || rawType == OffsetTime.class
                || rawType == Date.class;
    }

    private Object readIEnum(JsonParser parser, Class<?> rawType, Map<TypeVariable<?>, Type> variables) throws IOException {
        Object encoded = readValue(parser, EnumSupport.valueType(rawType), variables);
        try {
            return EnumSupport.parse(rawType.asSubclass(Enum.class), encoded);
        } catch (IllegalArgumentException ex) {
            throw new JsonReadException(null, "", "no " + typeName(rawType)
                    + " constant matches value " + renderValue(encoded), ex);
        }
    }

    private Object readUntyped(JsonParser parser) {
        return switch (parser.currentToken()) {
            case START_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    map.put(fieldName, readUntyped(parser));
                }
                yield map;
            }
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readUntyped(parser));
                }
                yield list;
            }
            case VALUE_STRING -> parser.getValueAsString();
            case VALUE_NUMBER_INT -> parser.getLongValue();
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> null;
            default -> throw new JsonException("Unsupported JSON token: " + parser.currentToken());
        };
    }

    private Object readArray(JsonParser parser, Class<?> rawType, Type elementType) throws IOException {
        expect(parser, JsonToken.START_ARRAY);
        Class<?> componentType = rawType.getComponentType();
        if (componentType.isPrimitive()) {
            return readPrimitiveArray(parser, componentType);
        }
        // Build directly into a result list (no shared pooled scratch, which would corrupt nested
        // arrays via reentrancy), then materialize the exact-size typed array.
        ArrayList<Object> values = new ArrayList<>();
        readScalarElementsInto(parser, values, elementType);
        Object[] array = (Object[]) Array.newInstance(componentType, values.size());
        return values.toArray(array);
    }

    /**
     * Reads a primitive array straight into the typed array, growing a primitive buffer instead of
     * collecting boxed values into a list and unboxing them via {@code Array.set}. Element coercion
     * and range checks match the per-field primitive path.
     */
    private Object readPrimitiveArray(JsonParser parser, Class<?> componentType) throws IOException {
        if (componentType == int.class) {
            return readIntArrayBuffer(parser);
        }
        if (componentType == long.class) {
            return readLongArrayBuffer(parser);
        }
        if (componentType == double.class) {
            return readDoubleArrayBuffer(parser);
        }
        // Less common primitive component types (byte/short/float/char/boolean) reuse the boxed path.
        ArrayList<Object> values = new ArrayList<>();
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(readValueIndex(parser, componentType, EMPTY_VARIABLES, index++));
        }
        Object array = Array.newInstance(componentType, values.size());
        for (int i = 0; i < values.size(); i++) {
            Array.set(array, i, values.get(i));
        }
        return array;
    }

    private int[] readIntArrayBuffer(JsonParser parser) throws IOException {
        int[] buffer = new int[16];
        int size = 0;
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (size == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length << 1);
            }
            try {
                buffer[size++] = (int) requireRange(readIntegralValue(parser, int.class),
                        Integer.MIN_VALUE, Integer.MAX_VALUE, int.class);
            } catch (JsonReadException ex) {
                throw ex.prepend("[" + index + "]");
            }
            index++;
        }
        return Arrays.copyOf(buffer, size);
    }

    private long[] readLongArrayBuffer(JsonParser parser) throws IOException {
        long[] buffer = new long[16];
        int size = 0;
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (size == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length << 1);
            }
            try {
                buffer[size++] = readIntegralValue(parser, long.class);
            } catch (JsonReadException ex) {
                throw ex.prepend("[" + index + "]");
            }
            index++;
        }
        return Arrays.copyOf(buffer, size);
    }

    private double[] readDoubleArrayBuffer(JsonParser parser) throws IOException {
        double[] buffer = new double[16];
        int size = 0;
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (size == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length << 1);
            }
            try {
                buffer[size++] = readFloatingValue(parser, double.class);
            } catch (JsonReadException ex) {
                throw ex.prepend("[" + index + "]");
            }
            index++;
        }
        return Arrays.copyOf(buffer, size);
    }

    private Collection<?> readCollection(JsonParser parser, Class<?> rawType, Type elementType) throws IOException {
        expect(parser, JsonToken.START_ARRAY);
        Type resolved = Types.resolve(elementType, EMPTY_VARIABLES);
        Class<?> elementRaw = Types.rawType(resolved);
        int kind = scalarKind(resolved, elementRaw);
        // List<Integer>/List<Long>: collect into a primitive buffer first, then box once for the API.
        if (!Set.class.isAssignableFrom(rawType)) {
            if (kind == KIND_INT) {
                return boxedIntList(readIntArrayBuffer(parser));
            }
            if (kind == KIND_LONG) {
                return boxedLongList(readLongArrayBuffer(parser));
            }
        }
        Collection<Object> result = Set.class.isAssignableFrom(rawType) ? new LinkedHashSet<>() : new ArrayList<>();
        readScalarElementsInto(parser, result, resolved, elementRaw, kind);
        return result;
    }

    private static List<Integer> boxedIntList(int[] values) {
        ArrayList<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private static List<Long> boxedLongList(long[] values) {
        ArrayList<Long> result = new ArrayList<>(values.length);
        for (long value : values) {
            result.add(value);
        }
        return result;
    }

    private void readScalarElementsInto(JsonParser parser, Collection<Object> target, Type elementType) throws IOException {
        Type resolved = Types.resolve(elementType, EMPTY_VARIABLES);
        readScalarElementsInto(parser, target, resolved, Types.rawType(resolved), scalarKind(resolved, Types.rawType(resolved)));
    }

    /**
     * Reads array/collection elements into {@code target}. The declared element type is constant, so
     * classify it once and stream scalar elements straight through the matching coercion instead of
     * re-resolving the type and rescanning handlers per element via the generic {@code readValue}.
     */
    private void readScalarElementsInto(JsonParser parser, Collection<Object> target, Type resolved,
                                        Class<?> elementRaw, int kind) throws IOException {
        // Homogeneous object lists (e.g. List<User>): resolve the element ClassPlan and its generic
        // bindings once, then read each element straight via readObject. This skips the per-element
        // readValue dispatch ladder and the classPlan ConcurrentHashMap lookup that dominate the cost
        // of large POJO lists.
        ObjectElementPlan objectPlan = kind == KIND_DELEGATE ? objectElementPlan(resolved, elementRaw) : null;
        int index = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (objectPlan != null) {
                try {
                    if (parser.currentToken() == JsonToken.VALUE_NULL) {
                        target.add(null);
                    } else {
                        target.add(readObject(parser, objectPlan.plan(), objectPlan.variables(), elementRaw));
                    }
                } catch (JsonReadException ex) {
                    throw ex.prepend("[" + index + "]");
                }
            } else if (kind == KIND_DELEGATE) {
                target.add(readValueIndex(parser, resolved, EMPTY_VARIABLES, index));
            } else if (kind == KIND_INT) {
                try {
                    target.add((int) requireRange(readIntegralValue(parser, int.class),
                            Integer.MIN_VALUE, Integer.MAX_VALUE, int.class));
                } catch (JsonReadException ex) {
                    throw ex.prepend("[" + index + "]");
                }
            } else if (kind == KIND_LONG) {
                try {
                    target.add(readIntegralValue(parser, long.class));
                } catch (JsonReadException ex) {
                    throw ex.prepend("[" + index + "]");
                }
            } else {
                try {
                    target.add(readScalar(parser, elementRaw, kind));
                } catch (JsonReadException ex) {
                    throw ex.prepend("[" + index + "]");
                }
            }
            index++;
        }
    }

    /**
     * Returns a reusable plan when {@code resolved} is a plain reflectable object type (the
     * {@code readObject} case), or {@code null} when the element must go through the generic
     * {@code readValue} dispatch (handler-backed, {@code Object}, {@code IEnum}, scalar, array,
     * collection, or map element types).
     */
    private ObjectElementPlan objectElementPlan(Type resolved, Class<?> elementRaw) {
        if (elementRaw == Object.class
                || handler(resolved) != null
                || EnumSupport.isIEnum(elementRaw)
                || isScalarType(elementRaw)
                || elementRaw.isArray()
                || Collection.class.isAssignableFrom(elementRaw)
                || Map.class.isAssignableFrom(elementRaw)) {
            return null;
        }
        Reflector<?> reflector = ReflectorRegistry.get(elementRaw);
        if (reflector == null) {
            return null;
        }
        ClassPlan plan = classPlan(elementRaw);
        Map<TypeVariable<?>, Type> variables = resolved == elementRaw
                ? plan.rawTypeVariables : Types.typeVariables(resolved);
        return new ObjectElementPlan(plan, variables);
    }

    /**
     * Reads a single non-delegate scalar element by its precomputed {@code KIND_*}: a JSON null maps
     * to the type's default, enums resolve by name, everything else goes through {@code readSimpleValue}.
     */
    private Object readScalar(JsonParser parser, Class<?> rawType, int kind) {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return defaultValue(rawType);
        }
        if (kind == KIND_ENUM) {
            return resolveEnum(rawType, parser.getValueAsString());
        }
        return readSimpleValue(parser, rawType);
    }

    private Map<String, Object> readMap(JsonParser parser, Type valueType) throws IOException {
        expect(parser, JsonToken.START_OBJECT);
        Map<String, Object> map = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            map.put(fieldName, readValueField(parser, valueType, EMPTY_VARIABLES, fieldName));
        }
        return map;
    }

    private Object readObject(JsonParser parser, Type type, Class<?> rawType) throws IOException {
        expect(parser, JsonToken.START_OBJECT);
        ClassPlan plan = classPlan(rawType);
        Map<TypeVariable<?>, Type> variables = type == rawType ? plan.rawTypeVariables : Types.typeVariables(type);
        return readObject(parser, plan, variables, rawType);
    }

    private Object readObject(JsonParser parser, ClassPlan plan, Map<TypeVariable<?>, Type> variables, Class<?> rawType)
            throws IOException {
        if (!plan.hasFlatten) {
            return readPlainObject(parser, plan, variables, rawType);
        }
        return readFlattenedObject(parser, plan, variables, rawType);
    }

    private Object readPlainObject(JsonParser parser, ClassPlan plan, Map<TypeVariable<?>, Type> variables, Class<?> rawType) throws IOException {
        // Common case: no-arg POJO. Stream each value straight into the instance instead of
        // collecting into Object[]/boolean[] then setting (saves two array allocations per object).
        // Absent fields keep their fresh-instance defaults, so no presence tracking is needed.
        if (plan.params.length == 0) {
            Object instance = plan.reflector.newInstance();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                Integer fieldIndex = plan.plainByName.get(fieldName);
                if (fieldIndex != null) {
                    setFieldFromParser(parser, plan.reflector, plan, instance, fieldIndex, variables, fieldName);
                    continue;
                }
                if (failOnUnknownProperties) {
                    throw new JsonException("Unknown JSON property '" + fieldName + "' for " + rawType.getName());
                }
                parser.skipChildren();
            }
            return instance;
        }
        Object[] fieldValues = new Object[plan.jsonNames.length];
        boolean[] presentFields = new boolean[fieldValues.length];
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            Integer fieldIndex = plan.plainByName.get(fieldName);
            if (fieldIndex != null) {
                fieldValues[fieldIndex] = readScalarOrDelegate(parser, plan, fieldIndex, variables, fieldName);
                presentFields[fieldIndex] = true;
                continue;
            }
            if (failOnUnknownProperties) {
                throw new JsonException("Unknown JSON property '" + fieldName + "' for " + rawType.getName());
            }
            parser.skipChildren();
        }
        return instantiate(plan, variables, fieldValues, presentFields);
    }

    private void setFieldFromParser(JsonParser parser, Reflector<Object> reflector, ClassPlan plan, Object instance,
                                    int index, Map<TypeVariable<?>, Type> variables, String fieldName) throws IOException {
        int kind = plan.scalarKinds[index];
        Class<?> rawType = plan.fieldRawTypes[index];
        if (isPrimitiveScalar(kind, rawType)) {
            try {
                setPrimitiveField(parser, reflector, instance, index, kind, rawType);
            } catch (JsonReadException ex) {
                throw ex.prepend("." + fieldName);
            }
            return;
        }
        reflector.set(instance, index, readScalarOrDelegate(parser, plan, index, variables, fieldName));
    }

    private void setPrimitiveField(JsonParser parser, Reflector<Object> reflector, Object instance, int index,
                                   int kind, Class<?> rawType) {
        try {
            if (parser.currentToken() == JsonToken.VALUE_NULL) {
                switch (kind) {
                    case KIND_BOOLEAN -> reflector.setBoolean(instance, index, false);
                    case KIND_BYTE -> reflector.setByte(instance, index, (byte) 0);
                    case KIND_SHORT -> reflector.setShort(instance, index, (short) 0);
                    case KIND_INT -> reflector.setInt(instance, index, 0);
                    case KIND_LONG -> reflector.setLong(instance, index, 0L);
                    case KIND_FLOAT -> reflector.setFloat(instance, index, 0f);
                    case KIND_DOUBLE -> reflector.setDouble(instance, index, 0d);
                    default -> reflector.set(instance, index, defaultValue(rawType));
                }
                return;
            }
            switch (kind) {
                case KIND_BOOLEAN -> reflector.setBoolean(instance, index, readBooleanValue(parser, rawType));
                case KIND_BYTE -> reflector.setByte(instance, index,
                        (byte) requireRange(readIntegralValue(parser, rawType), Byte.MIN_VALUE, Byte.MAX_VALUE, rawType));
                case KIND_SHORT -> reflector.setShort(instance, index,
                        (short) requireRange(readIntegralValue(parser, rawType), Short.MIN_VALUE, Short.MAX_VALUE, rawType));
                case KIND_INT -> reflector.setInt(instance, index,
                        (int) requireRange(readIntegralValue(parser, rawType), Integer.MIN_VALUE, Integer.MAX_VALUE, rawType));
                case KIND_LONG -> reflector.setLong(instance, index, readIntegralValue(parser, rawType));
                case KIND_FLOAT -> reflector.setFloat(instance, index, (float) readFloatingValue(parser, rawType));
                case KIND_DOUBLE -> reflector.setDouble(instance, index, readFloatingValue(parser, rawType));
                default -> throw new IllegalStateException("Unsupported primitive scalar kind: " + kind);
            }
        } catch (JsonReadException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw coercionError(parser, rawType, ex);
        }
    }

    private boolean readBooleanValue(JsonParser parser, Class<?> rawType) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_TRUE) {
            return true;
        }
        if (token == JsonToken.VALUE_FALSE) {
            return false;
        }
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue() != 0d;
        }
        Object value = readBoolean(parser, rawType);
        return (Boolean) value;
    }

    private long readIntegralValue(JsonParser parser, Class<?> rawType) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return parser.getLongValue();
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT) {
            return (long) parser.getDoubleValue();
        }
        return ((Number) readNumeric(parser, rawType)).longValue();
    }

    private double readFloatingValue(JsonParser parser, Class<?> rawType) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
        return ((Number) readNumeric(parser, rawType)).doubleValue();
    }

    /**
     * Reads a single field value. Scalar kinds bypass the generic {@code readValue} dispatch (type
     * resolution + handler scan + type-ladder) and go straight to the matching coercion, while a
     * JSON null yields the field's default. Everything else delegates to the generic engine.
     */
    private Object readScalarOrDelegate(JsonParser parser, ClassPlan plan, int index,
                                        Map<TypeVariable<?>, Type> variables, String fieldName) throws IOException {
        int kind = plan.scalarKinds[index];
        if (kind == KIND_DELEGATE) {
            return readValueField(parser, plan.fieldTypes[index], variables, fieldName);
        }
        try {
            return readScalar(parser, plan.fieldRawTypes[index], kind);
        } catch (JsonReadException ex) {
            throw ex.prepend("." + fieldName);
        }
    }

    private Object readFlattenedObject(JsonParser parser, ClassPlan classPlan, Map<TypeVariable<?>, Type> variables, Class<?> rawType) throws IOException {
        Reflector<Object> reflector = classPlan.reflector;
        Object[] fieldValues = new Object[classPlan.jsonNames.length];
        boolean[] presentFields = new boolean[fieldValues.length];

        FlattenPlan plan = flattenPlan(reflector, variables, rawType);

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            Integer fieldIndex = plan.plainByName().get(fieldName);
            if (fieldIndex != null) {
                FieldInfo fieldInfo = reflector.getField(fieldIndex);
                fieldValues[fieldIndex] = readValueField(parser,
                        fieldInfo == null ? Object.class : fieldInfo.type(), variables, fieldName);
                presentFields[fieldIndex] = true;
                continue;
            }
            UnwrapRoute route = plan.unwrapRoutes().get(fieldName);
            if (route != null) {
                ChildModel model = plan.unwrapModels().get(route.parentIndex());
                FieldInfo childField = model.reflector().getField(route.childIndex());
                model.values()[route.childIndex()] = readValueField(parser,
                        childField == null ? Object.class : childField.type(), model.variables(), fieldName);
                model.present()[route.childIndex()] = true;
                continue;
            }
            if (plan.anyMapIndex() >= 0 && (plan.anyMapPrefix().isEmpty() || fieldName.startsWith(plan.anyMapPrefix()))) {
                String key = plan.anyMapPrefix().isEmpty() ? fieldName : fieldName.substring(plan.anyMapPrefix().length());
                plan.anyMap().put(key, readValueField(parser, plan.anyMapValueType(), variables, fieldName));
                continue;
            }
            if (failOnUnknownProperties) {
                throw new JsonException("Unknown JSON property '" + fieldName + "' for " + rawType.getName());
            }
            parser.skipChildren();
        }

        for (Map.Entry<Integer, ChildModel> entry : plan.unwrapModels().entrySet()) {
            ChildModel model = entry.getValue();
            if (!anyPresent(model.present())) {
                continue;
            }
            fieldValues[entry.getKey()] = instantiate(model.reflector(), model.variables(), model.values(), model.present());
            presentFields[entry.getKey()] = true;
        }
        if (plan.anyMapIndex() >= 0) {
            fieldValues[plan.anyMapIndex()] = plan.anyMap();
            presentFields[plan.anyMapIndex()] = true;
        }

        return instantiate(classPlan, variables, fieldValues, presentFields);
    }

    private Object instantiate(ClassPlan plan, Map<TypeVariable<?>, Type> variables, Object[] fieldValues, boolean[] presentFields) {
        // Plain no-arg POJOs (the common case) skip constructor binding entirely: no args/flags
        // arrays, no parameter scan. Fields are set directly after a no-arg instantiation.
        if (plan.params.length == 0) {
            Object instance = plan.reflector.newInstance();
            for (int i = 0; i < fieldValues.length; i++) {
                if (presentFields[i]) {
                    plan.reflector.set(instance, i, fieldValues[i]);
                }
            }
            return instance;
        }
        return instantiate(plan.reflector, variables, plan.allByName, fieldValues, presentFields);
    }

    private Object instantiate(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables, Object[] fieldValues, boolean[] presentFields) {
        return instantiate(reflector, variables, fieldsByName(reflector), fieldValues, presentFields);
    }

    private Object instantiate(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables,
                               Map<String, Integer> fieldsByName, Object[] fieldValues, boolean[] presentFields) {
        ConstructorBinding constructorBinding = constructorBinding(reflector, variables, fieldsByName, fieldValues, presentFields);
        Object instance = constructorBinding.args().length == 0
                ? reflector.newInstance()
                : reflector.newInstance(constructorBinding.args());
        for (int i = 0; i < fieldValues.length; i++) {
            if (presentFields[i] && !constructorBinding.constructorFields()[i]) {
                reflector.set(instance, i, fieldValues[i]);
            }
        }
        return instance;
    }

    private FlattenPlan flattenPlan(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables, Class<?> rawType) {
        Map<String, Integer> plainByName = new HashMap<>();
        Map<String, UnwrapRoute> unwrapRoutes = new HashMap<>();
        Map<Integer, ChildModel> unwrapModels = new LinkedHashMap<>();
        int anyMapIndex = -1;
        Type anyMapValueType = Object.class;
        String anyMapPrefix = "";

        String[] fields = reflector.getFields();
        for (int i = 0; i < fields.length; i++) {
            FieldInfo fieldInfo = reflector.getField(i);
            String flattenPrefix = flattenPrefix(fieldInfo);
            if (flattenPrefix == null) {
                plainByName.put(fields[i], i);
                if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                    plainByName.put(fieldInfo.alias(), i);
                }
                continue;
            }
            Type fieldType = Types.resolve(fieldInfo.type(), variables);
            Class<?> fieldRaw = Types.rawType(fieldType);
            if (Map.class.isAssignableFrom(fieldRaw)) {
                anyMapIndex = i;
                anyMapValueType = Types.mapValueType(fieldType);
                anyMapPrefix = flattenPrefix;
                continue;
            }
            if (fieldRaw == Object.class) {
                throw new JsonException("Cannot flatten field '" + fields[i] + "' of " + rawType.getName()
                        + ": its concrete type is unknown at runtime. Provide a TypeRef so the generic type can be resolved.");
            }
            Reflector<Object> childReflector = reflector(fieldRaw);
            Map<TypeVariable<?>, Type> childVariables = Types.typeVariables(fieldType);
            String[] childFields = childReflector.getFields();
            ChildModel model = new ChildModel(childReflector, childVariables,
                    new Object[childFields.length], new boolean[childFields.length]);
            unwrapModels.put(i, model);
            for (int j = 0; j < childFields.length; j++) {
                FieldInfo childField = childReflector.getField(j);
                unwrapRoutes.put(flattenPrefix + childFields[j], new UnwrapRoute(i, j));
                if (childField != null && childField.alias() != null && !childField.alias().isBlank()) {
                    unwrapRoutes.put(flattenPrefix + childField.alias(), new UnwrapRoute(i, j));
                }
            }
        }
        Map<String, Object> anyMap = anyMapIndex >= 0 ? new LinkedHashMap<>() : Map.of();
        return new FlattenPlan(plainByName, unwrapRoutes, unwrapModels, anyMapIndex, anyMapValueType, anyMapPrefix, anyMap);
    }

    private boolean anyPresent(boolean[] present) {
        for (boolean value : present) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private ConstructorBinding constructorBinding(Reflector<Object> reflector,
                                                  Map<TypeVariable<?>, Type> variables,
                                                  Map<String, Integer> fieldsByName,
                                                  Object[] fieldValues,
                                                  boolean[] presentFields) {
        ClassInfo classInfo = reflector.getClassInfo();
        ParameterInfo[] parameters = classInfo == null || classInfo.params() == null
                ? new ParameterInfo[0]
                : classInfo.params();
        Object[] args = new Object[parameters.length];
        boolean[] constructorFields = new boolean[fieldValues.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterInfo parameter = parameters[i];
            Type parameterType = Types.resolve(parameter.type(), variables);
            Integer fieldIndex = fieldsByName.get(parameter.name());
            if (fieldIndex == null) {
                args[i] = defaultValue(Types.rawType(parameterType));
                continue;
            }
            constructorFields[fieldIndex] = true;
            args[i] = presentFields[fieldIndex]
                    ? fieldValues[fieldIndex]
                    : defaultValue(Types.rawType(parameterType));
        }
        return new ConstructorBinding(args, constructorFields);
    }

    private Map<String, Integer> fieldsByName(Reflector<Object> reflector) {
        Map<String, Integer> fieldsByName = new HashMap<>();
        String[] fields = reflector.getFields();
        for (int i = 0; i < fields.length; i++) {
            fieldsByName.put(fields[i], i);
            FieldInfo fieldInfo = reflector.getField(i);
            if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                fieldsByName.put(fieldInfo.alias(), i);
            }
        }
        return fieldsByName;
    }

    private String jsonName(FieldInfo fieldInfo) {
        if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
            return fieldInfo.alias();
        }
        return fieldInfo == null ? "" : fieldInfo.name();
    }

    private String flattenPrefix(FieldInfo fieldInfo) {
        if (fieldInfo == null) {
            return null;
        }
        for (AnnotationMeta annotation : fieldInfo.annotations()) {
            if (FLATTEN_ANNOTATION.equals(annotation.type())) {
                String prefix = annotation.stringValue("prefix");
                return prefix == null ? "" : prefix;
            }
        }
        return null;
    }

    private Object defaultValue(Class<?> rawType) {
        if (!rawType.isPrimitive()) {
            return null;
        }
        if (rawType == boolean.class) {
            return false;
        }
        if (rawType == char.class) {
            return '\0';
        }
        if (rawType == byte.class) {
            return (byte) 0;
        }
        if (rawType == short.class) {
            return (short) 0;
        }
        if (rawType == int.class) {
            return 0;
        }
        if (rawType == long.class) {
            return 0L;
        }
        if (rawType == float.class) {
            return 0F;
        }
        if (rawType == double.class) {
            return 0D;
        }
        return null;
    }

    private void expect(JsonParser parser, JsonToken expectedToken) {
        if (parser.currentToken() != expectedToken) {
            throw new JsonReadException(null, "", "expected JSON " + expectedToken
                    + " but found " + parser.currentToken());
        }
    }

    private Object readValueField(JsonParser parser, Type type, Map<TypeVariable<?>, Type> variables, String fieldName) throws IOException {
        try {
            return readValue(parser, type, variables);
        } catch (JsonReadException ex) {
            // Build the path segment only when reporting an error, keeping the happy path allocation-free.
            throw ex.prepend("." + fieldName);
        }
    }

    private Object readValueIndex(JsonParser parser, Type type, Map<TypeVariable<?>, Type> variables, int index) throws IOException {
        try {
            return readValue(parser, type, variables);
        } catch (JsonReadException ex) {
            throw ex.prepend("[" + index + "]");
        }
    }

    private JsonReadException coercionError(JsonParser parser, Class<?> rawType, Throwable cause) {
        String detail = "expected " + typeName(rawType) + " but found JSON "
                + parser.currentToken() + renderToken(parser);
        return new JsonReadException(null, "", detail, cause);
    }

    private static String renderToken(JsonParser parser) {
        String text = tokenText(parser);
        return text == null ? "" : " (" + text + ")";
    }

    private static String tokenText(JsonParser parser) {
        try {
            return parser.getValueAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String renderValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence) {
            return "\"" + value + "\"";
        }
        return String.valueOf(value);
    }

    private static String typeName(Class<?> rawType) {
        if (rawType == null) {
            return "Object";
        }
        return rawType.getSimpleName();
    }

    private ClassPlan classPlan(Class<?> rawType) {
        ClassPlan cached = classPlans.get(rawType);
        return cached != null ? cached : classPlans.computeIfAbsent(rawType, this::buildClassPlan);
    }

    /**
     * Pre-computes the type-argument-independent metadata for a class once and caches it: the
     * alias-resolved JSON name, declared type and {@code @Flatten} prefix per field, plus the
     * name/alias -> index maps used for property matching and constructor binding. This removes the
     * per-call {@code getField}, annotation scanning, name concatenation, and map rebuilds that
     * otherwise ran on every serialize/deserialize.
     */
    private ClassPlan buildClassPlan(Class<?> rawType) {
        Reflector<Object> reflector = reflector(rawType);
        String[] fields = reflector.getFields();
        int count = fields.length;
        Type[] fieldTypes = new Type[count];
        Class<?>[] fieldRawTypes = new Class<?>[count];
        String[] jsonNames = new String[count];
        SerializableString[] encodedNames = new SerializableString[count];
        SerializableString[][] enumConstantNames = new SerializableString[count][];
        String[] flattenPrefixes = new String[count];
        int[] scalarKinds = new int[count];
        Map<String, Integer> plainByName = new HashMap<>();
        Map<String, Integer> allByName = new HashMap<>();
        boolean hasFlatten = false;
        ClassInfo classInfo = reflector.getClassInfo();
        ParameterInfo[] params = classInfo == null || classInfo.params() == null
                ? Reflector.NO_PARAMS
                : classInfo.params();
        for (int i = 0; i < count; i++) {
            FieldInfo fieldInfo = reflector.getField(i);
            Type fieldType = fieldInfo == null ? Object.class : fieldInfo.type();
            fieldTypes[i] = fieldType;
            fieldRawTypes[i] = Types.rawType(fieldType);
            jsonNames[i] = jsonName(fieldInfo);
            encodedNames[i] = new SerializedString(jsonNames[i]);
            String flattenPrefix = flattenPrefix(fieldInfo);
            flattenPrefixes[i] = flattenPrefix;
            scalarKinds[i] = scalarKind(fieldType, fieldRawTypes[i]);
            if (scalarKinds[i] == KIND_ENUM) {
                enumConstantNames[i] = encodeEnumConstants(fieldRawTypes[i]);
            }
            allByName.put(fields[i], i);
            if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                allByName.put(fieldInfo.alias(), i);
            }
            if (flattenPrefix == null) {
                plainByName.put(fields[i], i);
                if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                    plainByName.put(fieldInfo.alias(), i);
                }
            } else {
                hasFlatten = true;
            }
        }
        // typeVariables(rawType) walks the generic supertype chain via reflection; for the common
        // non-generic case (type == rawType) the binding never changes, so cache it once here instead
        // of recomputing per element when (de)serializing a homogeneous collection/array.
        Map<TypeVariable<?>, Type> rawTypeVariables = Types.typeVariables(rawType);
        return new ClassPlan(reflector, fieldTypes, fieldRawTypes, jsonNames, encodedNames, enumConstantNames,
                flattenPrefixes, scalarKinds, hasFlatten, Map.copyOf(plainByName), Map.copyOf(allByName), params,
                rawTypeVariables);
    }

    /**
     * Pre-encodes an enum's constant names as {@link SerializedString}, indexed by ordinal, so the
     * write path never re-escapes them. Returns {@code null} for non-enum types.
     */
    private static SerializableString[] encodeEnumConstants(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null) {
            return null;
        }
        SerializableString[] names = new SerializableString[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = new SerializedString(((Enum<?>) constants[i]).name());
        }
        return names;
    }

    private static Enum<?> resolveEnum(Class<?> rawType, String name) {
        return enumLookup(rawType).resolve(name);
    }

    private static EnumLookup enumLookup(Class<?> enumType) {
        return ENUM_LOOKUPS.computeIfAbsent(enumType, EnumLookup::of);
    }

    /**
     * Classifies a field's declared type into a fast-path scalar kind, or {@link #KIND_DELEGATE}
     * when the value must go through the generic engine. A registered handler, an {@code IEnum},
     * or any non-concrete/generic type all delegate so behaviour is identical to the slow path.
     */
    private int scalarKind(Type fieldType, Class<?> rawType) {
        if (rawType == null || handler(fieldType) != null) {
            return KIND_DELEGATE;
        }
        if (rawType == String.class || rawType == char.class || rawType == Character.class) {
            return KIND_STRING;
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return KIND_BOOLEAN;
        }
        if (rawType == int.class || rawType == Integer.class) {
            return KIND_INT;
        }
        if (rawType == long.class || rawType == Long.class) {
            return KIND_LONG;
        }
        if (rawType == short.class || rawType == Short.class) {
            return KIND_SHORT;
        }
        if (rawType == byte.class || rawType == Byte.class) {
            return KIND_BYTE;
        }
        if (rawType == double.class || rawType == Double.class) {
            return KIND_DOUBLE;
        }
        if (rawType == float.class || rawType == Float.class) {
            return KIND_FLOAT;
        }
        if (rawType == BigInteger.class) {
            return KIND_BIG_INTEGER;
        }
        if (rawType == BigDecimal.class) {
            return KIND_BIG_DECIMAL;
        }
        if (rawType.isEnum()) {
            return EnumSupport.isIEnum(rawType) ? KIND_DELEGATE : KIND_ENUM;
        }
        if (rawType == Date.class) {
            return KIND_DATE;
        }
        if (rawType == UUID.class
                || rawType == LocalDate.class || rawType == LocalDateTime.class
                || rawType == OffsetDateTime.class || rawType == ZonedDateTime.class
                || rawType == Instant.class
                || rawType == LocalTime.class || rawType == OffsetTime.class) {
            return KIND_STRINGIFY;
        }
        return KIND_DELEGATE;
    }

    @SuppressWarnings("unchecked")
    private Reflector<Object> reflector(Class<?> rawType) {
        Reflector<?> reflector = ReflectorRegistry.get(rawType);
        if (reflector == null) {
            throw new JsonException("No Reflector registered for " + rawType.getName()
                    + ". Add @Reflect and run kyra-processor so the ServiceLoader installer is generated.");
        }
        return (Reflector<Object>) reflector;
    }

    private JsonTypeHandler<Object> handler(Type type) {
        if (handlers.isEmpty()) {
            return null;
        }
        // Cache the scan result for raw Class keys (the common handler target); these are bounded by
        // the number of classes seen, while arbitrary parameterized types still fall back to a scan.
        if (type instanceof Class<?> clazz) {
            JsonTypeHandler<Object> cached = handlerCache.get(clazz);
            if (cached != null) {
                return cached == NO_HANDLER ? null : cached;
            }
            JsonTypeHandler<Object> found = scanHandlers(type);
            handlerCache.put(clazz, found == null ? NO_HANDLER : found);
            return found;
        }
        return scanHandlers(type);
    }

    @SuppressWarnings("unchecked")
    private JsonTypeHandler<Object> scanHandlers(Type type) {
        for (JsonTypeHandler<?> handler : handlers) {
            if (handler.supports(type)) {
                return (JsonTypeHandler<Object>) handler;
            }
        }
        return null;
    }

    /**
     * Carries the location (root type plus a dotted/indexed path) of a failed read so the final
     * message can point at the exact field, e.g. {@code Order.items[2].active}. Each enclosing
     * structural reader prepends its own segment as the exception propagates outward.
     */
    private static final class JsonReadException extends JsonException {
        private final String root;
        private final String path;
        private final String detail;

        private JsonReadException(String root, String path, String detail, Throwable cause) {
            super(render(root, path, detail), cause);
            this.root = root;
            this.path = path;
            this.detail = detail;
        }

        private JsonReadException(String root, String path, String detail) {
            this(root, path, detail, null);
        }

        private JsonReadException prepend(String segment) {
            return new JsonReadException(root, segment + path, detail, getCause());
        }

        private JsonReadException withRoot(String newRoot) {
            return new JsonReadException(newRoot, path, detail, getCause());
        }

        private static String render(String root, String path, String detail) {
            String location = (root == null ? "" : root) + path;
            if (location.isEmpty()) {
                location = "<root>";
            }
            return "Failed to read JSON at " + location + ": " + detail;
        }
    }

    /**
     * Cached, type-argument-independent view of a reflectable class. Generic bindings still vary per
     * call and are resolved on top of this via the per-call {@code variables} map.
     */
    private record ClassPlan(Reflector<Object> reflector,
                             Type[] fieldTypes,
                             Class<?>[] fieldRawTypes,
                             String[] jsonNames,
                             SerializableString[] encodedNames,
                             SerializableString[][] enumConstantNames,
                             String[] flattenPrefixes,
                             int[] scalarKinds,
                             boolean hasFlatten,
                             Map<String, Integer> plainByName,
                             Map<String, Integer> allByName,
                             ParameterInfo[] params,
                             Map<TypeVariable<?>, Type> rawTypeVariables) {
    }

    private record ObjectElementPlan(ClassPlan plan, Map<TypeVariable<?>, Type> variables) {
    }

    private record ConstructorBinding(Object[] args, boolean[] constructorFields) {
    }

    private record UnwrapRoute(int parentIndex, int childIndex) {
    }

    private record ChildModel(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables,
                              Object[] values, boolean[] present) {
    }

    private record FlattenPlan(Map<String, Integer> plainByName,
                               Map<String, UnwrapRoute> unwrapRoutes,
                               Map<Integer, ChildModel> unwrapModels,
                               int anyMapIndex,
                               Type anyMapValueType,
                               String anyMapPrefix,
                               Map<String, Object> anyMap) {
    }

    /**
     * Per-thread reusable UTF-8 byte sink for {@link #toBytes}. {@code inUse} guards re-entrant
     * serialization (e.g. a handler that calls back into the mapper) by forcing a fresh builder.
     */
    private static final class BytesHolder {
        private final ByteArrayBuilder builder = new ByteArrayBuilder(512);
        private boolean inUse;
    }

    /**
     * Precomputed enum constant names for linear scan lookup. Small enums (the common case) avoid
     * {@link Enum#valueOf}'s internal HashMap on every field read.
     */
    private static final class EnumLookup {
        private final Class<?> enumType;
        private final String[] names;
        private final Enum<?>[] constants;

        private EnumLookup(Class<?> enumType, String[] names, Enum<?>[] constants) {
            this.enumType = enumType;
            this.names = names;
            this.constants = constants;
        }

        private static EnumLookup of(Class<?> enumType) {
            Object[] values = enumType.getEnumConstants();
            if (values == null || values.length == 0) {
                return new EnumLookup(enumType, new String[0], new Enum<?>[0]);
            }
            String[] names = new String[values.length];
            Enum<?>[] constants = new Enum<?>[values.length];
            for (int i = 0; i < values.length; i++) {
                Enum<?> constant = (Enum<?>) values[i];
                names[i] = constant.name();
                constants[i] = constant;
            }
            return new EnumLookup(enumType, names, constants);
        }

        @SuppressWarnings("unchecked")
        private <E extends Enum<E>> E resolve(String name) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) {
                    return (E) constants[i];
                }
            }
            throw new IllegalArgumentException("No enum constant " + enumType.getName() + "." + name);
        }
    }

    /**
     * Unsynchronized, pre-sized output target. {@link java.io.StringWriter} is backed by a
     * synchronized {@link StringBuffer} (its locking and growth reallocations showed up in
     * profiling); a {@link StringBuilder} avoids both.
     */
    private static final class StringBuilderWriter extends Writer {
        private static final int MAX_RETAINED_CHARS = 1 << 18; // ~256K chars; trim larger buffers.

        private StringBuilder builder;
        private boolean inUse;

        private StringBuilderWriter(int initialCapacity) {
            this.builder = new StringBuilder(initialCapacity);
        }

        private boolean inUse() {
            return inUse;
        }

        private void acquire() {
            inUse = true;
            builder.setLength(0);
        }

        private void release() {
            inUse = false;
            // Avoid permanently retaining a huge buffer after an outsized document.
            if (builder.capacity() > MAX_RETAINED_CHARS) {
                builder = new StringBuilder(512);
            }
        }

        @Override
        public void write(int c) {
            builder.append((char) c);
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            builder.append(buffer, offset, length);
        }

        @Override
        public void write(String str) {
            builder.append(str);
        }

        @Override
        public void write(String str, int offset, int length) {
            builder.append(str, offset, offset + length);
        }

        @Override
        public Writer append(CharSequence sequence) {
            builder.append(sequence);
            return this;
        }

        @Override
        public Writer append(char c) {
            builder.append(c);
            return this;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }

    static final class Builder implements JsonMapper.Builder {
        private final List<JsonTypeHandler<?>> handlers = new ArrayList<>();
        private boolean failOnUnknownProperties;
        private boolean includeNulls = false;

        @Override
        public Builder register(JsonTypeHandler<?> handler) {
            handlers.add(handler);
            return this;
        }

        @Override
        public Builder failOnUnknownProperties(boolean failOnUnknownProperties) {
            this.failOnUnknownProperties = failOnUnknownProperties;
            return this;
        }

        @Override
        public Builder includeNulls(boolean includeNulls) {
            this.includeNulls = includeNulls;
            return this;
        }

        @Override
        public JsonMapper build() {
            return new DefaultJsonMapper(handlers, failOnUnknownProperties, includeNulls);
        }
    }

    private final class WriterContext implements JsonWriterContext {
        private final JsonGenerator generator;
        private final Map<TypeVariable<?>, Type> variables;

        private WriterContext(JsonGenerator generator, Map<TypeVariable<?>, Type> variables) {
            this.generator = generator;
            this.variables = variables;
        }

        @Override
        public JsonGenerator generator() {
            return generator;
        }

        @Override
        public void write(Object value) {
            write(value, value == null ? Object.class : value.getClass());
        }

        @Override
        public void write(Object value, Type type) {
            try {
                writeValue(generator, value, type, variables);
            } catch (IOException ex) {
                throw new JsonException("Failed to write JSON value", ex);
            }
        }
    }

    private final class ReaderContext implements JsonReaderContext {
        private final JsonParser parser;
        private final Map<TypeVariable<?>, Type> variables;

        private ReaderContext(JsonParser parser, Map<TypeVariable<?>, Type> variables) {
            this.parser = parser;
            this.variables = variables;
        }

        @Override
        public JsonParser parser() {
            return parser;
        }

        @Override
        public <T> T read(Class<T> type) {
            return read((Type) type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T read(Type type) {
            try {
                return (T) readValue(parser, type, variables);
            } catch (IOException ex) {
                throw new JsonException("Failed to read JSON value", ex);
            }
        }
    }
}
