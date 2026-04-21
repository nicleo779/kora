package com.nicleo.kora.core.mapper;

import com.nicleo.kora.core.annotation.IdStrategy;
import com.nicleo.kora.core.query.Column;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Tables;
import com.nicleo.kora.core.runtime.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseMapperImplIdStrategyTest {
    @BeforeEach
    void setUp() {
        GeneratedReflectors.clear();
        Tables.clear();
        Tables.register(ManualIdEntity.class, ManualIdTable.MANUAL_IDS);
        Tables.register(UuidEntity.class, UuidTable.UUIDS);
        Tables.register(CustomEntity.class, CustomTable.CUSTOMS);
        Tables.register(SessionCustomEntity.class, SessionCustomTable.SESSION_CUSTOMS);
        Tables.register(AutoIdEntity.class, AutoIdTable.AUTO_IDS);
        GeneratedReflectors.install(new TestReflectorResolver());
    }

    @Test
    void noneStrategyShouldUseProvidedIdWhenPresent() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        BaseMapperImpl<ManualIdEntity> mapper = new BaseMapperImpl<>(sqlSession, ManualIdEntity.class);
        ManualIdEntity entity = new ManualIdEntity(10L, "manual");

        assertEquals(1, mapper.insert(entity));
        assertEquals("insert into manual_ids (id, name) values (?, ?)", sqlSession.lastSql);
        assertArrayEquals(new Object[]{10L, "manual"}, sqlSession.lastArgs);
        assertEquals(10L, entity.getId());
    }

    @Test
    void uuidStrategyShouldGenerateAndAssignId() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        BaseMapperImpl<UuidEntity> mapper = new BaseMapperImpl<>(sqlSession, UuidEntity.class);
        UuidEntity entity = new UuidEntity(null, "uuid");

        assertEquals(1, mapper.insert(entity));
        assertNotNull(entity.getId());
        assertTrue(UUID.fromString(entity.getId()).toString().equals(entity.getId()));
        assertEquals("insert into uuids (id, name) values (?, ?)", sqlSession.lastSql);
        assertEquals(entity.getId(), sqlSession.lastArgs[0]);
        assertEquals("uuid", sqlSession.lastArgs[1]);
    }

    @Test
    void customStrategyShouldGenerateAndAssignIdsForBatch() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        BaseMapperImpl<CustomEntity> mapper = new BaseMapperImpl<>(sqlSession, CustomEntity.class);
        CustomEntity first = new CustomEntity(null, "first");
        CustomEntity second = new CustomEntity(null, "second");

        assertEquals(2, mapper.insert(List.of(first, second)));
        assertEquals(1001L, first.getId());
        assertEquals(1002L, second.getId());
        assertEquals("insert into customs (id, name) values (?, ?)", sqlSession.lastBatchSql);
        assertEquals(2, sqlSession.lastBatchArgs.size());
        assertArrayEquals(new Object[]{1001L, "first"}, sqlSession.lastBatchArgs.get(0));
        assertArrayEquals(new Object[]{1002L, "second"}, sqlSession.lastBatchArgs.get(1));
    }

    @Test
    void customStrategyShouldFallbackToSessionGenerator() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        sqlSession.setIdGenerator(new SessionIdGenerator());
        BaseMapperImpl<SessionCustomEntity> mapper = new BaseMapperImpl<>(sqlSession, SessionCustomEntity.class);
        SessionCustomEntity entity = new SessionCustomEntity(null, "session");

        assertEquals(1, mapper.insert(entity));
        assertEquals(5001L, entity.getId());
        assertEquals("insert into session_customs (id, name) values (?, ?)", sqlSession.lastSql);
        assertArrayEquals(new Object[]{5001L, "session"}, sqlSession.lastArgs);
    }

    @Test
    void noneStrategyShouldAssignGeneratedKeyBackToEntity() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        sqlSession.generatedKey = 77L;
        BaseMapperImpl<AutoIdEntity> mapper = new BaseMapperImpl<>(sqlSession, AutoIdEntity.class);
        AutoIdEntity entity = new AutoIdEntity(null, "auto");

        assertEquals(1, mapper.insert(entity));
        assertEquals("insert into auto_ids (name) values (?)", sqlSession.lastSql);
        assertArrayEquals(new Object[]{"auto"}, sqlSession.lastArgs);
        assertEquals(77L, entity.getId());
    }

    private static final class RecordingSqlExecutor implements SqlExecutor {
        private TypeConverter typeConverter = new TypeConverter();
        private IdGenerator idGenerator;
        private String lastSql;
        private Object[] lastArgs;
        private String lastBatchSql;
        private List<Object[]> lastBatchArgs;
        private Object generatedKey;

        @Override
        public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, Object[] args) {
            this.lastSql = sql;
            this.lastArgs = args;
            return 1;
        }

        @Override
        public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
            this.lastSql = sql;
            this.lastArgs = args;
            return generatedKey == null ? null : resultType.cast(generatedKey);
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs) {
            this.lastBatchSql = sql;
            this.lastBatchArgs = batchArgs;
            int[] results = new int[batchArgs.size()];
            java.util.Arrays.fill(results, 1);
            return results;
        }

        @Override
        public TypeConverter getTypeConverter() {
            return typeConverter;
        }

        @Override
        public void setTypeConverter(TypeConverter typeConverter) {
            this.typeConverter = typeConverter;
        }

        @Override
        public IdGenerator getIdGenerator() {
            return idGenerator;
        }

        @Override
        public void setIdGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
        }

        @Override
        public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            return selectOne(sql, args, resultType);
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            return selectList(sql, args, resultType);
        }

        @Override
        public int update(String sql, Object[] args, SqlExecutionContext context) {
            return update(sql, args);
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
            return executeBatch(sql, batchArgs);
        }

        @Override
        public SqlPagingSupport getSqlPagingSupport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DbType getDbType() {
            return null;
        }

        @Override
        public SqlGenerator getSqlGenerator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class AutoIdEntity {
        private Long id;
        private String name;

        private AutoIdEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }

    private static final class ManualIdEntity {
        private Long id;
        private String name;

        private ManualIdEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }

    private static final class UuidEntity {
        private String id;
        private String name;

        private UuidEntity(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }

    private static final class CustomEntity {
        private Long id;
        private String name;

        private CustomEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }

    private static final class SessionCustomEntity {
        private Long id;
        private String name;

        private SessionCustomEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }
    }

    private static final class ManualIdTable extends EntityTable<ManualIdEntity> {
        private static final ManualIdTable MANUAL_IDS = new ManualIdTable();
        private final Column<ManualIdEntity, Long> ID = column("id", Long.class);

        private ManualIdTable() {
            super(ManualIdEntity.class, "manual_ids");
        }

        @Override
        public IdStrategy idStrategy() {
            return IdStrategy.NONE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Column<ManualIdEntity, V> idColumn() {
            return (Column<ManualIdEntity, V>) ID;
        }

        @Override
        public String fieldName(String column) {
            return column;
        }

        @Override
        public String columnName(String field) {
            return field;
        }
    }

    private static final class UuidTable extends EntityTable<UuidEntity> {
        private static final UuidTable UUIDS = new UuidTable();
        private final Column<UuidEntity, String> ID = column("id", String.class);

        private UuidTable() {
            super(UuidEntity.class, "uuids");
        }

        @Override
        public IdStrategy idStrategy() {
            return IdStrategy.UUID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Column<UuidEntity, V> idColumn() {
            return (Column<UuidEntity, V>) ID;
        }

        @Override
        public String fieldName(String column) {
            return column;
        }

        @Override
        public String columnName(String field) {
            return field;
        }
    }

    private static final class CustomTable extends EntityTable<CustomEntity> {
        private static final CustomTable CUSTOMS = new CustomTable();
        private static final SequenceIdGenerator GENERATOR = new SequenceIdGenerator();
        private final Column<CustomEntity, Long> ID = column("id", Long.class);

        private CustomTable() {
            super(CustomEntity.class, "customs");
        }

        @Override
        public IdStrategy idStrategy() {
            return IdStrategy.CUSTOM;
        }

        @Override
        public IdGenerator idGenerator() {
            return GENERATOR;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Column<CustomEntity, V> idColumn() {
            return (Column<CustomEntity, V>) ID;
        }

        @Override
        public String fieldName(String column) {
            return column;
        }

        @Override
        public String columnName(String field) {
            return field;
        }
    }

    private static final class SessionCustomTable extends EntityTable<SessionCustomEntity> {
        private static final SessionCustomTable SESSION_CUSTOMS = new SessionCustomTable();
        private final Column<SessionCustomEntity, Long> ID = column("id", Long.class);

        private SessionCustomTable() {
            super(SessionCustomEntity.class, "session_customs");
        }

        @Override
        public IdStrategy idStrategy() {
            return IdStrategy.CUSTOM;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Column<SessionCustomEntity, V> idColumn() {
            return (Column<SessionCustomEntity, V>) ID;
        }

        @Override
        public String fieldName(String column) {
            return column;
        }

        @Override
        public String columnName(String field) {
            return field;
        }
    }

    private static final class AutoIdTable extends EntityTable<AutoIdEntity> {
        private static final AutoIdTable AUTO_IDS = new AutoIdTable();
        private final Column<AutoIdEntity, Long> ID = column("id", Long.class);

        private AutoIdTable() {
            super(AutoIdEntity.class, "auto_ids");
        }

        @Override
        public IdStrategy idStrategy() {
            return IdStrategy.NONE;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Column<AutoIdEntity, V> idColumn() {
            return (Column<AutoIdEntity, V>) ID;
        }

        @Override
        public String fieldName(String column) {
            return column;
        }

        @Override
        public String columnName(String field) {
            return field;
        }
    }

    private static final class SequenceIdGenerator implements IdGenerator {
        private final AtomicLong sequence = new AtomicLong(1000);

        @Override
        public Object generate(SqlExecutor sqlExecutor, EntityTable<?> entityTable, Object entity) {
            return sequence.incrementAndGet();
        }
    }

    private static final class SessionIdGenerator implements IdGenerator {
        private final AtomicLong sequence = new AtomicLong(5000);

        @Override
        public Object generate(SqlExecutor sqlExecutor, EntityTable<?> entityTable, Object entity) {
            return sequence.incrementAndGet();
        }
    }

    private static final class TestReflectorResolver implements GeneratedReflectors.Resolver {
        @Override
        @SuppressWarnings("unchecked")
        public <T> GeneratedReflector<T> get(Class<T> type) {
            if (type == ManualIdEntity.class) {
                return (GeneratedReflector<T>) new ManualIdReflector();
            }
            if (type == UuidEntity.class) {
                return (GeneratedReflector<T>) new UuidReflector();
            }
            if (type == CustomEntity.class) {
                return (GeneratedReflector<T>) new CustomReflector();
            }
            if (type == SessionCustomEntity.class) {
                return (GeneratedReflector<T>) new SessionCustomReflector();
            }
            if (type == AutoIdEntity.class) {
                return (GeneratedReflector<T>) new AutoIdReflector();
            }
            throw new IllegalArgumentException(type.getName());
        }
    }

    private static final class ManualIdReflector implements GeneratedReflector<ManualIdEntity> {
        @Override
        public ManualIdEntity newInstance() {
            return new ManualIdEntity(null, null);
        }

        @Override
        public Object invoke(ManualIdEntity target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(ManualIdEntity target, String property, Object value) {
            switch (property) {
                case "id" -> target.setId((Long) value);
                default -> {
                }
            }
        }

        @Override
        public Object get(ManualIdEntity target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "name" -> target.getName();
                default -> null;
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "name"};
        }

        @Override
        public FieldInfo getField(String field) {
            return null;
        }
    }

    private static final class UuidReflector implements GeneratedReflector<UuidEntity> {
        @Override
        public UuidEntity newInstance() {
            return new UuidEntity(null, null);
        }

        @Override
        public Object invoke(UuidEntity target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(UuidEntity target, String property, Object value) {
            if ("id".equals(property)) {
                target.setId((String) value);
            }
        }

        @Override
        public Object get(UuidEntity target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "name" -> target.getName();
                default -> null;
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "name"};
        }

        @Override
        public FieldInfo getField(String field) {
            return null;
        }
    }

    private static final class CustomReflector implements GeneratedReflector<CustomEntity> {
        @Override
        public CustomEntity newInstance() {
            return new CustomEntity(null, null);
        }

        @Override
        public Object invoke(CustomEntity target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(CustomEntity target, String property, Object value) {
            if ("id".equals(property)) {
                target.setId((Long) value);
            }
        }

        @Override
        public Object get(CustomEntity target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "name" -> target.getName();
                default -> null;
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "name"};
        }

        @Override
        public FieldInfo getField(String field) {
            return null;
        }
    }

    private static final class SessionCustomReflector implements GeneratedReflector<SessionCustomEntity> {
        @Override
        public SessionCustomEntity newInstance() {
            return new SessionCustomEntity(null, null);
        }

        @Override
        public Object invoke(SessionCustomEntity target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(SessionCustomEntity target, String property, Object value) {
            if ("id".equals(property)) {
                target.setId((Long) value);
            }
        }

        @Override
        public Object get(SessionCustomEntity target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "name" -> target.getName();
                default -> null;
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "name"};
        }

        @Override
        public FieldInfo getField(String field) {
            return null;
        }
    }

    private static final class AutoIdReflector implements GeneratedReflector<AutoIdEntity> {
        @Override
        public AutoIdEntity newInstance() {
            return new AutoIdEntity(null, null);
        }

        @Override
        public Object invoke(AutoIdEntity target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(AutoIdEntity target, String property, Object value) {
            if ("id".equals(property)) {
                target.setId((Long) value);
            }
        }

        @Override
        public Object get(AutoIdEntity target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "name" -> target.getName();
                default -> null;
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "name"};
        }

        @Override
        public FieldInfo getField(String field) {
            return "id".equals(field) ? new FieldInfo("id", Long.class, 2, null, new com.nicleo.kora.core.runtime.AnnotationMeta[0]) : null;
        }
    }
}
