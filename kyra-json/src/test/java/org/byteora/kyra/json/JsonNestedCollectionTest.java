package org.byteora.kyra.json;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonNestedCollectionTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void roundTripsNestedIntLists() {
        String json = "[[1,2],[3,4]]";
        List<List<Integer>> value = mapper.fromJson(json, new TypeRef<List<List<Integer>>>() {
        });
        assertEquals(List.of(List.of(1, 2), List.of(3, 4)), value);
    }

    @Test
    void roundTripsNestedUntypedArrays() {
        String json = "[[1,2],[3,4]]";
        Object value = mapper.fromJson(json, Object.class);
        assertEquals(List.of(List.of(1L, 2L), List.of(3L, 4L)), value);
    }

    @Test
    void roundTripsArrayOfIntArrays() {
        int[][] value = {{1, 2}, {3, 4}};
        String json = mapper.toJson(value);
        assertEquals("[[1,2],[3,4]]", json);
        int[][] decoded = mapper.fromJson(json, int[][].class);
        assertEquals(2, decoded.length);
        assertEquals(2, decoded[0].length);
        assertEquals(4, decoded[1][1]);
    }
}
