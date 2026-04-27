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
import java.util.Arrays;
import java.util.UUID;

public final class TypeConverter {

    private CustomTypeConverter[] customConverters = null;

    public TypeConverter register(CustomTypeConverter converter) {
        if (converter == null) {
            return this;
        }
        if (customConverters == null) {
            customConverters = new CustomTypeConverter[]{converter};
            return this;
        }
        customConverters = Arrays.copyOf(customConverters, customConverters.length + 1);
        customConverters[customConverters.length - 1] = converter;
        return this;
    }

    public void clearCustomConverters() {
        customConverters = null;
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

    public Object fieldToColumn(Object value) {
        if (value == null) {
            return null;
        }
        if (customConverters == null || customConverters.length == 0) {
            return value;
        }
        Class<?> sourceType = value.getClass();
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(sourceType)) {
                return converter.fieldToColumn(value);
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T doCast(ResultSet resultSet, int index, Class<T> targetType) throws SQLException {
        if (customConverters != null) {
            for (CustomTypeConverter converter : customConverters) {
                if (converter.supports(targetType)) {
                    return converter.columnToField(resultSet, index, targetType);
                }
            }
        }
        return resultSet.getObject(index, targetType);
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
