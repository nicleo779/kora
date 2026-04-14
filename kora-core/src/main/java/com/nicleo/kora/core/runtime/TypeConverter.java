package com.nicleo.kora.core.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TypeConverter {
    private final List<CustomTypeConverter> customConverters = new CopyOnWriteArrayList<>();

    public TypeConverter register(CustomTypeConverter converter) {
        customConverters.add(converter);
        return this;
    }

    public void clearCustomConverters() {
        customConverters.clear();
    }

    public Object cast(Object value, Class<?> targetType) {
        return cast(value, targetType, null, null);
    }

    public Object cast(Object value, Class<?> targetType, String columnName, String fieldName) {
        try {
            return doCast(value, targetType);
        } catch (SqlExecutorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw conversionFailure(value, targetType, columnName, fieldName, ex);
        }
    }

    public Object toDbValue(Object value) {
        if (value == null) {
            return null;
        }
        Object converted = applyCustomConvertersToDb(value, wrap(value.getClass()));
        return converted != null ? converted : value;
    }

    private Object applyCustomConvertersFromDb(Object value, Class<?> targetType) {
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(targetType)) {
                return converter.fromDb(value, targetType);
            }
        }
        return null;
    }

    private Object applyCustomConvertersToDb(Object value, Class<?> sourceType) {
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(sourceType)) {
                return converter.toDb(value, sourceType);
            }
        }
        return null;
    }

    private Object doCast(Object value, Class<?> targetType) {
        Class<?> normalizedTargetType = wrap(targetType);
        if (value == null) {
            if (normalizedTargetType != targetType || targetType.isPrimitive()) {
                throw new SqlExecutorException("Cannot assign null to primitive type " + targetType.getName());
            }
            return null;
        }
        if (normalizedTargetType.isInstance(value)) {
            return value;
        }
        Object converted = applyCustomConvertersFromDb(value, normalizedTargetType);
        if (converted != null) {
            return converted;
        }
        if (normalizedTargetType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (normalizedTargetType == Long.class) {
            return ((Number) value).longValue();
        }
        if (normalizedTargetType == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (normalizedTargetType == Float.class) {
            return ((Number) value).floatValue();
        }
        if (normalizedTargetType == Short.class) {
            return ((Number) value).shortValue();
        }
        if (normalizedTargetType == Byte.class) {
            return ((Number) value).byteValue();
        }
        if (normalizedTargetType == Boolean.class) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
        }
        if (normalizedTargetType == String.class) {
            return String.valueOf(value);
        }
        if (normalizedTargetType == BigDecimal.class) {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            return new BigDecimal(String.valueOf(value));
        }
        if (normalizedTargetType == BigInteger.class) {
            if (value instanceof BigInteger integer) {
                return integer;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.toBigInteger();
            }
            if (value instanceof Number number) {
                return BigInteger.valueOf(number.longValue());
            }
            return new BigInteger(String.valueOf(value));
        }
        if (normalizedTargetType == UUID.class) {
            if (value instanceof UUID uuid) {
                return uuid;
            }
            return UUID.fromString(String.valueOf(value));
        }
        if (normalizedTargetType == LocalDate.class) {
            if (value instanceof java.sql.Date date) {
                return date.toLocalDate();
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime().toLocalDate();
            }
            if (value instanceof LocalDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (value instanceof Instant instant) {
                return instant.atZone(ZoneId.systemDefault()).toLocalDate();
            }
            return LocalDate.parse(String.valueOf(value));
        }
        if (normalizedTargetType == LocalDateTime.class) {
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
            if (value instanceof java.sql.Date date) {
                return date.toLocalDate().atStartOfDay();
            }
            if (value instanceof Instant instant) {
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            }
            return LocalDateTime.parse(String.valueOf(value));
        }
        if (normalizedTargetType == LocalTime.class) {
            if (value instanceof Time time) {
                return time.toLocalTime();
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime().toLocalTime();
            }
            return LocalTime.parse(String.valueOf(value));
        }
        if (normalizedTargetType == Instant.class) {
            if (value instanceof Timestamp timestamp) {
                return timestamp.toInstant();
            }
            if (value instanceof java.util.Date date) {
                return date.toInstant();
            }
            if (value instanceof LocalDateTime dateTime) {
                return dateTime.atZone(ZoneId.systemDefault()).toInstant();
            }
            if (value instanceof OffsetDateTime offsetDateTime) {
                return offsetDateTime.toInstant();
            }
            if (value instanceof ZonedDateTime zonedDateTime) {
                return zonedDateTime.toInstant();
            }
            return Instant.parse(String.valueOf(value));
        }
        if (normalizedTargetType == OffsetDateTime.class) {
            if (value instanceof Timestamp timestamp) {
                return timestamp.toInstant().atOffset(ZoneOffset.systemDefault().getRules().getOffset(timestamp.toInstant()));
            }
            if (value instanceof Instant instant) {
                return instant.atOffset(ZoneOffset.systemDefault().getRules().getOffset(instant));
            }
            if (value instanceof ZonedDateTime zonedDateTime) {
                return zonedDateTime.toOffsetDateTime();
            }
            return OffsetDateTime.parse(String.valueOf(value));
        }
        if (normalizedTargetType == OffsetTime.class) {
            if (value instanceof Time time) {
                return time.toLocalTime().atOffset(OffsetDateTime.now().getOffset());
            }
            if (value instanceof OffsetDateTime offsetDateTime) {
                return offsetDateTime.toOffsetTime();
            }
            return OffsetTime.parse(String.valueOf(value));
        }
        if (normalizedTargetType == ZonedDateTime.class) {
            if (value instanceof Timestamp timestamp) {
                return timestamp.toInstant().atZone(ZoneId.systemDefault());
            }
            if (value instanceof Instant instant) {
                return instant.atZone(ZoneId.systemDefault());
            }
            if (value instanceof OffsetDateTime offsetDateTime) {
                return offsetDateTime.toZonedDateTime();
            }
            return ZonedDateTime.parse(String.valueOf(value));
        }
        return normalizedTargetType.cast(value);
    }

    private SqlExecutorException conversionFailure(Object value, Class<?> targetType, String columnName, String fieldName, RuntimeException cause) {
        String sourceType = value == null ? "null" : value.getClass().getName();
        StringBuilder message = new StringBuilder("Failed to convert result");
        if (columnName != null || fieldName != null) {
            message.append(" from");
            if (columnName != null) {
                message.append(" column '").append(columnName).append("'");
            }
            if (fieldName != null) {
                message.append(" to field '").append(fieldName).append("'");
            }
        }
        message.append(" from type ").append(sourceType)
                .append(" to type ").append(targetType.getName());
        return new SqlExecutorException(message.toString(), cause);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
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

}
