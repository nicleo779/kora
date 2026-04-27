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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
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
        return executeQuery(request.getSql(), request.getArgs(), context, createRowMapper(resultType));
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
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(request.getSql())) {
                Object[] boundArgs = bindParameters(statement, request.getArgs());
                int updated = statement.executeUpdate();
                logSqlSuccess(context, request.getSql(), boundArgs, null, updated);
                return updated;
            }
        } catch (SQLException ex) {
            logSqlError(context, request.getSql(), request.getArgs(), null, ex);
            throw new SqlExecutorException("Failed to execute update: " + request.getSql(), ex);
        }
    }

    @Override
    public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
        return updateAndReturnGeneratedKey(sql, args, SqlExecutionContext.update(this), resultType);
    }

    public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, args));
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(request.getSql(), Statement.RETURN_GENERATED_KEYS)) {
                Object[] boundArgs = bindParameters(statement, request.getArgs());
                int updated = statement.executeUpdate();
                logSqlSuccess(context, request.getSql(), boundArgs, null, updated);
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
            logSqlError(context, request.getSql(), request.getArgs(), null, ex);
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
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(request.getSql())) {
                for (Object[] args : batchArgs) {
                    bindParameters(statement, args);
                    statement.addBatch();
                }
                int[] rows = statement.executeBatch();
                logSqlSuccess(context, request.getSql(), null, batchArgs, batchRows(rows));
                return rows;
            }
        } catch (SQLException ex) {
            logSqlError(context, request.getSql(), null, batchArgs, ex);
            throw new SqlExecutorException("Failed to execute batch: " + request.getSql(), ex);
        }
    }

    @Override
    public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
        return executeQuery(sql, args, null, rowMapper);
    }

    private <T> List<T> executeQuery(String sql, Object[] args, SqlExecutionContext context, RowMapper<T> rowMapper) {
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                Object[] boundArgs = bindParameters(statement, args);
                try (ResultSet resultSet = statement.executeQuery()) {
                    var results = new ArrayList<T>();
                    while (resultSet.next()) {
                        results.add(rowMapper.mapRow(resultSet));
                    }
                    logSqlSuccess(context, sql, boundArgs, null, results.size());
                    return results;
                }
            }
        } catch (SQLException ex) {
            logSqlError(context, sql, args, null, ex);
            throw new SqlExecutorException("Failed to execute query: " + sql, ex);
        }
    }

    protected Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void logSqlSuccess(SqlExecutionContext context, String sql, Object[] args, List<Object[]> batchArgs, Object rows) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        if (batchArgs != null) {
            logger.debug("Executing SQL mapper={}.{} sql={} batchSize={} rows={}",
                    mapperClassName(context),
                    statementId(context),
                    formatExecutableBatchSql(sql, batchArgs),
                    batchArgs.size(),
                    rows);
            return;
        }
        logger.debug("Executing SQL mapper={}.{} sql={} rows={}",
                mapperClassName(context),
                statementId(context),
                formatExecutableSql(sql, args),
                rows);
    }

    private void logSqlError(SqlExecutionContext context, String sql, Object[] args, List<Object[]> batchArgs, SQLException ex) {
        if (!logger.isErrorEnabled()) {
            return;
        }
        if (batchArgs != null) {
            logger.error("Executing mapper={}.{} sql={} batchSize={}",
                    mapperClassName(context),
                    statementId(context),
                    formatExecutableBatchSql(sql, batchArgs),
                    batchArgs.size(),
                    ex);
            return;
        }
        logger.error("Executing mapper={}.{} sql={}",
                mapperClassName(context),
                statementId(context),
                formatExecutableSql(sql, args),
                ex);
    }

    private String mapperClassName(SqlExecutionContext context) {
        return context == null || context.getMapperClassName() == null ? "-" : context.getMapperClassName();
    }

    private String statementId(SqlExecutionContext context) {
        return context == null || context.getStatementId() == null ? "-" : context.getStatementId();
    }

    private String formatExecutableBatchSql(String sql, List<Object[]> batchArgs) {
        return batchArgs.stream()
                .map(args -> formatExecutableSql(sql, args))
                .collect(Collectors.joining("; "));
    }

    private String formatExecutableSql(String sql, Object[] args) {
        if (sql == null || args == null || args.length == 0) {
            return sql;
        }
        StringBuilder builder = new StringBuilder(sql.length() + args.length * 16);
        int argIndex = 0;
        boolean inSingleQuotedString = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '\'') {
                builder.append(ch);
                if (inSingleQuotedString && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    builder.append(sql.charAt(++i));
                    continue;
                }
                inSingleQuotedString = !inSingleQuotedString;
                continue;
            }
            if (ch == '?' && !inSingleQuotedString && argIndex < args.length) {
                builder.append(formatSqlLiteral(args[argIndex++]));
                continue;
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private Object batchRows(int[] rows) {
        if (rows == null || rows.length == 0) {
            return 0;
        }
        for (int row : rows) {
            if (row < 0) {
                return Arrays.toString(rows);
            }
        }
        return Arrays.stream(rows).sum();
    }

    private String formatSqlLiteral(Object value) {
        Object jdbcValue = typeConverter.fieldToColumn(value);
        if (jdbcValue == null) {
            return "null";
        }
        if (jdbcValue instanceof Number || jdbcValue instanceof Boolean) {
            return String.valueOf(jdbcValue);
        }
        if (jdbcValue instanceof Character character) {
            return '\'' + escapeSqlLiteral(String.valueOf(character)) + '\'';
        }
        return '\'' + escapeSqlLiteral(formatArg(jdbcValue)) + '\'';
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
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
        GeneratedReflector<T> reflector = GeneratedReflectors.get(resultType);
        if(reflector == null)
            return resultSet -> typeConverter.cast(resultSet, 1, resultType);
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

    private Object[] bindParameters(PreparedStatement statement, Object[] args) throws SQLException {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        Object[] boundArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object boundValue = typeConverter.fieldToColumn(args[i]);
            boundArgs[i] = boundValue;
            statement.setObject(i + 1, boundValue);
        }
        return boundArgs;
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
