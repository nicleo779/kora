package com.nicleo.kora.core.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    public <T> T cast(ResultSet resultSet, int index, Class<T> targetType) throws SQLException {
        return cast(resultSet, index, targetType, null, null);
    }

    public <T> T cast(ResultSet resultSet, int index, Class<T> targetType, String columnName, String fieldName) throws SQLException {
        try {
            return doCast(resultSet, index, targetType);
        } catch (SqlExecutorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw conversionFailure(resultSet.getObject(index), targetType, columnName, fieldName, ex);
        }
    }

    public <T> T cast(Object value, Class<T> targetType) {
        return cast(value, targetType, null, null);
    }

    public <T> T cast(Object value, Class<T> targetType, String columnName, String fieldName) {
        try {
            return doCast(value, targetType);
        } catch (SqlExecutorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw conversionFailure(value, targetType, columnName, fieldName, ex);
        }
    }


    public Object fieldToColumn(Object value) {
        if (value == null) {
            return null;
        }
        var sourceType = value.getClass();
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(sourceType)) {
                return converter.fieldToColumn(value);
            }
        }
        return value;
    }

    private <T> T doCast(ResultSet resultSet, int index, Class<T> targetType) throws SQLException {
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(targetType)) {
                return converter.columnToField(resultSet, index, targetType);
            }
        }
        return resultSet.getObject(index, targetType);
    }

    @SuppressWarnings("unchecked")
    private <T> T doCast(Object value, Class<T> targetType) {
        if (value == null) {
            return null;
        }
        Class<?> normalizedTargetType = wrap(targetType);
        if (normalizedTargetType.isInstance(value) || normalizedTargetType == Object.class) {
            return (T) value;
        }
        if (normalizedTargetType == String.class) {
            return (T) String.valueOf(value);
        }
        if (normalizedTargetType == BigDecimal.class) {
            return (T) toBigDecimal(value);
        }
        if (normalizedTargetType == BigInteger.class) {
            return (T) toBigInteger(value);
        }
        if (normalizedTargetType == UUID.class) {
            return (T) UUID.fromString(String.valueOf(value));
        }
        if (normalizedTargetType == LocalDate.class) {
            return (T) toLocalDate(value);
        }
        if (normalizedTargetType == LocalDateTime.class) {
            return (T) toLocalDateTime(value);
        }
        if (normalizedTargetType == LocalTime.class) {
            return (T) toLocalTime(value);
        }
        if (normalizedTargetType == Instant.class) {
            return (T) toInstant(value);
        }
        if (normalizedTargetType == OffsetDateTime.class) {
            return (T) toOffsetDateTime(value);
        }
        if (normalizedTargetType == ZonedDateTime.class) {
            return (T) toZonedDateTime(value);
        }
        if (normalizedTargetType == OffsetTime.class) {
            return (T) toOffsetTime(value);
        }
        if (normalizedTargetType == Long.class) {
            return (T) Long.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Integer.class) {
            return (T) Integer.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Short.class) {
            return (T) Short.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Byte.class) {
            return (T) Byte.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Double.class) {
            return (T) Double.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Float.class) {
            return (T) Float.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Boolean.class) {
            return (T) Boolean.valueOf(String.valueOf(value));
        }
        if (normalizedTargetType == Character.class) {
            String text = String.valueOf(value);
            if (text.length() != 1) {
                throw new IllegalArgumentException("Expected single character but got: " + text);
            }
            return (T) Character.valueOf(text.charAt(0));
        }
        if (normalizedTargetType.isEnum()) {
            return (T) enumValueOf(normalizedTargetType, value);
        }
        throw new IllegalArgumentException("Unsupported conversion from " + value.getClass().getName() + " to " + targetType.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object enumValueOf(Class<?> enumType, Object value) {
        return Enum.valueOf((Class) enumType.asSubclass(Enum.class), String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof BigInteger bigInteger) {
            return new BigDecimal(bigInteger);
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigInteger toBigInteger(Object value) {
        if (value instanceof BigInteger bigInteger) {
            return bigInteger;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.toBigInteger();
        }
        if (value instanceof Number number) {
            return BigInteger.valueOf(number.longValue());
        }
        return new BigInteger(String.valueOf(value));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDate();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private LocalTime toLocalTime(Object value) {
        if (value instanceof LocalTime localTime) {
            return localTime;
        }
        if (value instanceof Time time) {
            return time.toLocalTime();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalTime();
        }
        if (value instanceof OffsetTime offsetTime) {
            return offsetTime.toLocalTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalTime();
        }
        return LocalTime.parse(String.valueOf(value));
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        return Instant.parse(String.valueOf(value));
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime.toOffsetDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private ZonedDateTime toZonedDateTime(Object value) {
        if (value instanceof ZonedDateTime zonedDateTime) {
            return zonedDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault());
        }
        if (value instanceof Instant instant) {
            return instant.atZone(ZoneId.systemDefault());
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toZonedDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault());
        }
        return ZonedDateTime.parse(String.valueOf(value));
    }

    private OffsetTime toOffsetTime(Object value) {
        if (value instanceof OffsetTime offsetTime) {
            return offsetTime;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toOffsetTime();
        }
        if (value instanceof LocalTime localTime) {
            ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
            return localTime.atOffset(offset);
        }
        return OffsetTime.parse(String.valueOf(value));
    }

    private Class<?> wrap(Class<?> targetType) {
        if (!targetType.isPrimitive()) {
            return targetType;
        }
        return switch (targetType.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> targetType;
        };
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
}
