package com.nicleo.kora.spring.boot.autoconfigure;

import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DefaultSqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.TypeConverter;
import com.nicleo.kora.spring.boot.SpringTransactionSqlExecutor;
import com.nicleo.kora.spring.boot.Sql;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass({DataSource.class, SpringTransactionSqlExecutor.class})
public class KoraAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SqlPagingSupport sqlPagingSupport() {
        return new DefaultSqlPagingSupport();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlGenerator sqlGenerator() {
        return new DefaultSqlGenerator();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public SqlBinding sqlBinding(SqlExecutor sqlExecutor) {
        Sql.bind(sqlExecutor);
        return new SqlBinding();
    }

    @Bean("sqlExecutor")
    @ConditionalOnMissingBean(SqlExecutor.class)
    public SqlExecutor sqlExecutor(DataSource dataSource,
                                   ObjectProvider<TypeConverter> typeConverter,
                                   ObjectProvider<SqlPagingSupport> sqlPagingSupport,
                                   ObjectProvider<SqlGenerator> sqlGenerator,
                                   ObjectProvider<List<SqlInterceptor>> interceptors) {
        SpringTransactionSqlExecutor sqlExecutor = new SpringTransactionSqlExecutor(dataSource);
        sqlPagingSupport.ifAvailable(sqlExecutor::setSqlPagingSupport);
        sqlGenerator.ifAvailable(sqlExecutor::setSqlGenerator);
        typeConverter.ifAvailable(sqlExecutor::setTypeConverter);
        interceptors.ifAvailable(sqlExecutor::addInterceptor);
        return sqlExecutor;
    }

    @Bean
    @ConditionalOnMissingBean
    public static KoraMapperBeanDefinitionRegistryPostProcessor koraMapperBeanDefinitionRegistryPostProcessor() {
        return new KoraMapperBeanDefinitionRegistryPostProcessor();
    }

    public static final class SqlBinding implements AutoCloseable {
        @Override
        public void close() {
            Sql.clear();
        }
    }
}
