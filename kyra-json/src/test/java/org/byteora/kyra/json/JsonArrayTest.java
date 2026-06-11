package org.byteora.kyra.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonArrayTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void roundTripsIntArray() {
        int[] value = {1, -2, 3, Integer.MAX_VALUE, Integer.MIN_VALUE};
        String json = mapper.toJson(value);
        assertEquals("[1,-2,3,2147483647,-2147483648]", json);
        assertArrayEquals(value, mapper.fromJson(json, int[].class));
    }

    @Test
    void roundTripsLongArray() {
        long[] value = {1L, -2L, 9_000_000_000L, Long.MAX_VALUE};
        String json = mapper.toJson(value);
        assertArrayEquals(value, mapper.fromJson(json, long[].class));
    }

    @Test
    void roundTripsDoubleArray() {
        double[] value = {1.5, -2.25, 0.0, 100.125};
        String json = mapper.toJson(value);
        assertArrayEquals(value, mapper.fromJson(json, double[].class));
    }

    @Test
    void roundTripsFloatArray() {
        float[] value = {1.5f, -2.25f, 3.0f};
        String json = mapper.toJson(value);
        assertArrayEquals(value, mapper.fromJson(json, float[].class));
    }

    @Test
    void roundTripsShortAndByteArrays() {
        short[] shorts = {1, -2, 300};
        byte[] bytes = {1, -2, 127};
        assertArrayEquals(shorts, mapper.fromJson(mapper.toJson(shorts), short[].class));
        assertArrayEquals(bytes, mapper.fromJson(mapper.toJson(bytes), byte[].class));
    }

    @Test
    void byteArraySerializesAsNumbersNotBase64() {
        byte[] bytes = {1, 2, 3};
        assertEquals("[1,2,3]", mapper.toJson(bytes));
    }

    @Test
    void roundTripsBooleanArray() {
        boolean[] value = {true, false, true};
        String json = mapper.toJson(value);
        assertEquals("[true,false,true]", json);
        assertArrayEquals(value, mapper.fromJson(json, boolean[].class));
    }

    @Test
    void roundTripsCharArray() {
        char[] value = {'a', 'b', 'c'};
        String json = mapper.toJson(value);
        assertEquals("[\"a\",\"b\",\"c\"]", json);
        assertArrayEquals(value, mapper.fromJson(json, char[].class));
    }

    @Test
    void roundTripsReferenceArray() {
        String[] value = {"a", "b", "c"};
        String json = mapper.toJson(value);
        assertEquals("[\"a\",\"b\",\"c\"]", json);
        assertArrayEquals(value, mapper.fromJson(json, String[].class));
    }

    @Test
    void coercesStringsAndFloatsInIntArray() {
        assertArrayEquals(new int[]{1, 2, 3}, mapper.fromJson("[\"1\",2,3.9]", int[].class));
    }

    @Test
    void handlesEmptyArray() {
        assertEquals("[]", mapper.toJson(new int[0]));
        assertArrayEquals(new int[0], mapper.fromJson("[]", int[].class));
    }
}
