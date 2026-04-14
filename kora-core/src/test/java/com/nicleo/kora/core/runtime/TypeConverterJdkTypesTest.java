package com.nicleo.kora.core.runtime;

import org.junit.jupiter.api.Test;

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
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeConverterJdkTypesTest {
    private final TypeConverter converter = new TypeConverter();

    @Test
    void shouldConvertCommonJdkTypes() {
        assertEquals(new BigDecimal("12.5"), converter.cast("12.5", BigDecimal.class));
        assertEquals(new BigInteger("42"), converter.cast(42L, BigInteger.class));
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                converter.cast("123e4567-e89b-12d3-a456-426614174000", UUID.class));

        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.of(2024, 1, 2, 3, 4, 5));
        assertEquals(LocalDate.of(2024, 1, 2), converter.cast(timestamp, LocalDate.class));
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), converter.cast(timestamp, LocalDateTime.class));
        assertEquals(LocalTime.of(3, 4, 5), converter.cast(Time.valueOf(LocalTime.of(3, 4, 5)), LocalTime.class));

        Instant instant = timestamp.toInstant();
        assertEquals(instant, converter.cast(timestamp, Instant.class));
        assertEquals(instant.atZone(ZoneId.systemDefault()).toOffsetDateTime(), converter.cast(timestamp, OffsetDateTime.class));
        assertEquals(instant.atZone(ZoneId.systemDefault()), converter.cast(timestamp, ZonedDateTime.class));

        OffsetDateTime offsetDateTime = instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        assertEquals(offsetDateTime.toOffsetTime(), converter.cast(offsetDateTime, OffsetTime.class));
    }

    @Test
    void shouldReportColumnFieldAndTypeWhenConversionFails() {
        SqlExecutorException ex = assertThrows(SqlExecutorException.class,
                () -> converter.cast("abc", Long.class, "user_id", "userId"));

        assertEquals(
                "Failed to convert result from column 'user_id' to field 'userId' from type java.lang.String to type java.lang.Long",
                ex.getMessage()
        );
    }
}
