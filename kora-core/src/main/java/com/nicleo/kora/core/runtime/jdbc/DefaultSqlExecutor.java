package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DefaultSqlPagingSupport;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.IdGenerator;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.SqlExecutorException;
import com.nicleo.kora.core.runtime.TypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultSqlExecutor implements SqlExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultSqlExecutor.class);

    private final DataSource dataSource;
    private TypeConverter typeConverter;
    private SqlPagingSupport sqlPagingSupport;
    private SqlGenerator sqlGenerator;
    private DbType dbType;
    private IdGenerator idGenerator;
    private final List<SqlInterceptor> interceptors;

    public DefaultSqlExecutor(DataSource dataSource) {
        this.dataSource = dataSource;
        this.typeConverter = new TypeConverter();
        this.sqlPagingSupport = new DefaultSqlPagingSupport();
        this.sqlGenerator = new DefaultSqlGenerator();
        this.interceptors = new ArrayList<>();
    }

    public void setSqlPagingSupport(SqlPagingSupport sqlPagingSupport) {
        this.sqlPagingSupport = sqlPagingSupport;
    }

    public void setSqlGenerator(SqlGenerator sqlGenerator) {
        this.sqlGenerator = sqlGenerator;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public List<SqlInterceptor> getInterceptors() {
        return interceptors;
    }

    public void addInterceptor(SqlInterceptor... interceptors) {
        this.interceptors.addAll(Arrays.asList(interceptors));
    }

    public void addInterceptor(Collection<SqlInterceptor> interceptors) {
        this.interceptors.addAll(interceptors);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
        return selectOne(sql, args, SqlExecutionContext.select(this, resultType), resultType);
    }

    @Override
    public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        List<T> results = selectList(sql, args, context, resultType);
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            throw new SqlExecutorException("Expected one row but got " + results.size() + " for sql: " + sql);
        }
        return results.getFirst();
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
        return selectList(sql, args, SqlExecutionContext.select(this, resultType), resultType);
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, args));
        logSql(context, "query", request.getSql(), request.getArgs());
        return executeQuery(request.getSql(), request.getArgs(), createRowMapper(resultType));
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
    public SqlPagingSupport getSqlPagingSupport() {
        return sqlPagingSupport;
    }

    @Override
    public DbType getDbType() {
        if (dbType != null) {
            return dbType;
        }
        try (var connection = openConnection()) {
            dbType = inferDbType(connection);
            return dbType;
        } catch (SQLException ex) {
            throw new SqlExecutorException("Failed to open connection for DbType inference", ex);
        }
    }

    @Override
    public SqlGenerator getSqlGenerator() {
        return sqlGenerator;
    }

    @Override
    public int update(String sql, Object[] args) {
        return update(sql, args, SqlExecutionContext.update(this));
    }

    @Override
    public int update(String sql, Object[] args, SqlExecutionContext context) {
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, args));
        logSql(context, "update", request.getSql(), request.getArgs());
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(request.getSql())) {
                bindParameters(statement, request.getArgs());
                return statement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new SqlExecutorException("Failed to execute update: " + request.getSql(), ex);
        }
    }

    @Override
    public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
        return updateAndReturnGeneratedKey(sql, args, SqlExecutionContext.update(this), resultType);
    }

    public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, args));
        logSql(context, "updateAndReturnGeneratedKey", request.getSql(), request.getArgs());
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(request.getSql(), Statement.RETURN_GENERATED_KEYS)) {
                bindParameters(statement, request.getArgs());
                int updated = statement.executeUpdate();
                if (updated < 1) {
                    return null;
                }
                try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return typeConverter.cast(generatedKeys, 1, resultType);
                    }
                }
            }
            return null;
        } catch (SQLException ex) {
            throw new SqlExecutorException("Failed to execute update with generated key: " + request.getSql(), ex);
        }
    }

    @Override
    public int[] executeBatch(String sql, List<Object[]> batchArgs) {
        return executeBatch(sql, batchArgs, SqlExecutionContext.update(this));
    }

    @Override
    public int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
        if (batchArgs == null || batchArgs.isEmpty()) {
            return new int[0];
        }
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, new Object[0]));
        logBatchSql(context, request.getSql(), batchArgs);
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(request.getSql())) {
                for (Object[] args : batchArgs) {
                    bindParameters(statement, args);
                    statement.addBatch();
                }
                return statement.executeBatch();
            }
        } catch (SQLException ex) {
            throw new SqlExecutorException("Failed to execute batch: " + request.getSql(), ex);
        }
    }

    @Override
    public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindParameters(statement, args);
                try (ResultSet resultSet = statement.executeQuery()) {
                    var results = new ArrayList<T>();
                    while (resultSet.next()) {
                        results.add(rowMapper.mapRow(resultSet));
                    }
                    if (results.isEmpty()) {
                        return results;
                    }
                    return results;
                }
            }
        } catch (SQLException ex) {
            throw new SqlExecutorException("Failed to execute query: " + sql, ex);
        }
    }

    protected Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void logSql(SqlExecutionContext context, String operation, String sql, Object[] args) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        logger.debug("Executing SQL [{}] mapper={} statement={} sql={} args={}",
                operation,
                mapperClassName(context),
                statementId(context),
                sql,
                formatArgs(args));
    }

    private void logBatchSql(SqlExecutionContext context, String sql, List<Object[]> batchArgs) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        logger.debug("Executing SQL [batch] mapper={} statement={} sql={} batchSize={} args={}",
                mapperClassName(context),
                statementId(context),
                sql,
                batchArgs.size(),
                batchArgs.stream().map(this::formatArgs).collect(Collectors.toList()));
    }

    private String mapperClassName(SqlExecutionContext context) {
        return context == null || context.getMapperClassName() == null ? "-" : context.getMapperClassName();
    }

    private String statementId(SqlExecutionContext context) {
        return context == null || context.getStatementId() == null ? "-" : context.getStatementId();
    }

    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(this::formatArg)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String formatArg(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] bytes) {
            return Arrays.toString(bytes);
        }
        if (value instanceof short[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof int[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof long[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof float[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof double[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof char[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof boolean[] values) {
            return Arrays.toString(values);
        }
        if (value instanceof Object[] values) {
            return Arrays.deepToString(values);
        }
        return String.valueOf(value);
    }

    private <T> RowMapper<T> createRowMapper(Class<T> resultType) {
        if (Map.class.isAssignableFrom(resultType)) {
            return resultSet -> resultType.cast(readRowAsMap(resultSet));
        }
        if (isSimpleResultType(resultType)) {
            return resultSet -> {
                Object converted = typeConverter.cast(resultSet, 1, resultType);
                if (converted == null) {
                    return null;
                }
                @SuppressWarnings("unchecked")
                T value = (T) converted;
                return value;
            };
        }
        GeneratedReflector<T> reflector = GeneratedReflectors.get(resultType);
        return new GeneratedRowMapper<>(resultType, reflector, typeConverter);
    }

    private Map<String, Object> readRowAsMap(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
            row.put(metaData.getColumnLabel(columnIndex), resultSet.getObject(columnIndex));
        }
        return row;
    }

    private boolean isSimpleResultType(Class<?> resultType) {
        Class<?> normalized = wrap(resultType);
        return normalized.isPrimitive()
                || normalized == String.class
                || normalized == Boolean.class
                || normalized == Character.class
                || Number.class.isAssignableFrom(normalized)
                || normalized == BigDecimal.class
                || normalized == BigInteger.class
                || normalized == LocalDate.class
                || normalized == LocalDateTime.class
                || normalized == LocalTime.class
                || normalized == Instant.class
                || normalized == OffsetDateTime.class
                || normalized == OffsetTime.class
                || normalized == ZonedDateTime.class
                || normalized == Object.class
                || normalized.isEnum()
                || normalized == byte[].class;
    }

    private Class<?> wrap(Class<?> type) {
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

    private SqlRequest applyInterceptors(SqlExecutionContext context, SqlRequest originalRequest) {
        SqlExecutionContext effectiveContext = context == null ? SqlExecutionContext.update(this) : context.withSqlExecutor(this);
        if (!effectiveContext.isInterceptorEnabled()) {
            return originalRequest;
        }
        SqlRequest current = originalRequest;
        for (SqlInterceptor interceptor : interceptors) {
            SqlRequest intercepted = interceptor.intercept(effectiveContext.withoutInterceptors(), current);
            if (intercepted == null) {
                throw new SqlExecutorException("SqlInterceptor returned null request: " + interceptor.getClass().getName());
            }
            current = intercepted;
        }
        return current;
    }

    private void bindParameters(PreparedStatement statement, Object[] args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, typeConverter.fieldToColumn(args[i]));
        }
    }

    private DbType inferDbType(Connection connection) {
        try {
            String url = connection.getMetaData().getURL();
            if (url != null) {
                String normalized = url.toLowerCase(Locale.ROOT);
                if (normalized.startsWith("jdbc:h2:")) {
                    return DbType.H2;
                }
                if (normalized.startsWith("jdbc:mysql:")) {
                    return DbType.MYSQL;
                }
                if (normalized.startsWith("jdbc:postgresql:")) {
                    return DbType.POSTGRESQL;
                }
                if (normalized.startsWith("jdbc:mariadb:")) {
                    return DbType.MARIADB;
                }
                if (normalized.startsWith("jdbc:sqlite:")) {
                    return DbType.SQLITE;
                }
                if (normalized.startsWith("jdbc:oracle:")) {
                    return DbType.ORACLE;
                }
                if (normalized.startsWith("jdbc:sqlserver:")) {
                    return DbType.SQLSERVER;
                }
            }
            String productName = connection.getMetaData().getDatabaseProductName();
            if (productName != null) {
                String normalized = productName.toLowerCase(Locale.ROOT);
                if (normalized.contains("h2")) {
                    return DbType.H2;
                }
                if (normalized.contains("mysql")) {
                    return DbType.MYSQL;
                }
                if (normalized.contains("postgresql")) {
                    return DbType.POSTGRESQL;
                }
                if (normalized.contains("mariadb")) {
                    return DbType.MARIADB;
                }
                if (normalized.contains("sqlite")) {
                    return DbType.SQLITE;
                }
                if (normalized.contains("oracle")) {
                    return DbType.ORACLE;
                }
                if (normalized.contains("sql server")) {
                    return DbType.SQLSERVER;
                }
            }
            return DbType.MYSQL;
        } catch (SQLException ex) {
            throw new SqlExecutorException("Failed to infer DbType from DataSource", ex);
        }
    }
}
