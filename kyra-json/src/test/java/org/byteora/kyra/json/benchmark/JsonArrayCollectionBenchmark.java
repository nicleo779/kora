package org.byteora.kyra.json.benchmark;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.byteora.kyra.json.TypeRef;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Focused benchmarks for primitive-array and scalar-collection (de)serialization, the paths
 * targeted by the primitive-array fast path and per-element plan reuse optimizations.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonArrayCollectionBenchmark {
    static final int SIZE = 1000;

    @Benchmark
    public String kyraSerializeIntArray(BenchmarkState state) {
        return state.kyraMapper.toJson(state.ints);
    }

    @Benchmark
    public int[] kyraDeserializeIntArray(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.intArrayJson, int[].class);
    }

    @Benchmark
    public String kyraSerializeDoubleArray(BenchmarkState state) {
        return state.kyraMapper.toJson(state.doubles);
    }

    @Benchmark
    public double[] kyraDeserializeDoubleArray(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.doubleArrayJson, double[].class);
    }

    @Benchmark
    public String kyraSerializeLongArray(BenchmarkState state) {
        return state.kyraMapper.toJson(state.longs);
    }

    @Benchmark
    public long[] kyraDeserializeLongArray(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.longArrayJson, long[].class);
    }

    @Benchmark
    public String kyraSerializeIntList(BenchmarkState state) {
        return state.kyraMapper.toJson(state.intList);
    }

    @Benchmark
    public List<Integer> kyraDeserializeIntList(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.intListJson, new TypeRef<List<Integer>>() {
        });
    }

    @Benchmark
    public String kyraSerializeStringList(BenchmarkState state) {
        return state.kyraMapper.toJson(state.strList);
    }

    @Benchmark
    public List<String> kyraDeserializeStringList(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.strListJson, new TypeRef<List<String>>() {
        });
    }

    @Benchmark
    public String jacksonSerializeIntArray(BenchmarkState state) {
        try {
            return state.jacksonMapper.writeValueAsString(state.ints);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public int[] jacksonDeserializeIntArray(BenchmarkState state) {
        try {
            return state.jacksonMapper.readValue(state.intArrayJson, int[].class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public String jacksonSerializeIntList(BenchmarkState state) {
        try {
            return state.jacksonMapper.writeValueAsString(state.intList);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public List<Integer> jacksonDeserializeIntList(BenchmarkState state) {
        try {
            return state.jacksonMapper.readValue(state.intListJson, new TypeReference<List<Integer>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        org.byteora.kyra.json.JsonMapper kyraMapper;
        JsonMapper jacksonMapper;
        int[] ints;
        double[] doubles;
        long[] longs;
        List<Integer> intList;
        List<String> strList;
        String intArrayJson;
        String doubleArrayJson;
        String longArrayJson;
        String intListJson;
        String strListJson;

        @Setup(Level.Trial)
        public void setUp() {
            kyraMapper = org.byteora.kyra.json.JsonMapper.builder().build();
            jacksonMapper = JsonMapper.builder()
                    .changeDefaultVisibility(vc -> vc
                            .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                            .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                            .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                            .withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
                    .build();

            ints = new int[SIZE];
            doubles = new double[SIZE];
            longs = new long[SIZE];
            intList = new ArrayList<>(SIZE);
            strList = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) {
                ints[i] = i * 31 - 7;
                doubles[i] = i * 1.5;
                longs[i] = (long) i * 1_000_003L;
                intList.add(i * 31 - 7);
                strList.add("item-" + i);
            }
            intArrayJson = kyraMapper.toJson(ints);
            doubleArrayJson = kyraMapper.toJson(doubles);
            longArrayJson = kyraMapper.toJson(longs);
            intListJson = kyraMapper.toJson(intList);
            strListJson = kyraMapper.toJson(strList);
        }
    }
}
