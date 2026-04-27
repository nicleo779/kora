package com.nicleo.kora.quarkus.runtime;

import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DefaultSqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.TypeConverter;
import com.nicleo.kora.quarkus.QuarkusSqlExecutor;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

@Dependent
public class KoraQuarkusProducer {
    @Produces
    @Singleton
    @DefaultBean
    public SqlPagingSupport sqlPagingSupport() {
        return new DefaultSqlPagingSupport();
    }

    @Produces
    @Singleton
    @DefaultBean
    public SqlGenerator sqlGenerator() {
        return new DefaultSqlGenerator();
    }

    @Produces
    @Singleton
    @DefaultBean
    public SqlExecutor sqlExecutor(DataSource dataSource,
                                   Instance<TypeConverter> typeConverter,
                                   Instance<SqlPagingSupport> sqlPagingSupport,
                                   Instance<SqlGenerator> sqlGenerator,
                                   Instance<SqlInterceptor> interceptors) {
        QuarkusSqlExecutor sqlExecutor = new QuarkusSqlExecutor(dataSource);
        if (typeConverter.isResolvable()) {
            sqlExecutor.setTypeConverter(typeConverter.get());
        }
        if (sqlPagingSupport.isResolvable()) {
            sqlExecutor.setSqlPagingSupport(sqlPagingSupport.get());
        }
        if (sqlGenerator.isResolvable()) {
            sqlExecutor.setSqlGenerator(sqlGenerator.get());
        }
        for (SqlInterceptor interceptor : interceptors) {
            sqlExecutor.addInterceptor(interceptor);
        }
        return sqlExecutor;
    }
}
