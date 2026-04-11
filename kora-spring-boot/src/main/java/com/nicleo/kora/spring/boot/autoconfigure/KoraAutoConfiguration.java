package com.nicleo.kora.spring.boot.autoconfigure;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DefaultSqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionFactory;
import com.nicleo.kora.core.runtime.TypeConverter;
import com.nicleo.kora.spring.boot.CurrentSqlSessionProvider;
import com.nicleo.kora.spring.boot.SpringTransactionSqlSession;
import com.nicleo.kora.spring.boot.Sql;

@AutoConfiguration
@ConditionalOnClass({DataSource.class, SpringTransactionSqlSession.class})
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
    public SqlBinding sqlBinding(CurrentSqlSessionProvider currentSqlSessionProvider) {
        Sql.bind(currentSqlSessionProvider);
        return new SqlBinding();
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentSqlSessionProvider currentSqlSessionProvider(ObjectProvider<SqlSession> sqlSessionProvider, SqlSessionFactory sqlSessionFactory) {
        return () -> {
            SqlSession current = sqlSessionProvider.getIfAvailable();
            return current != null
                    ? new CurrentSqlSessionProvider.SessionHandle(current, false)
                    : new CurrentSqlSessionProvider.SessionHandle(sqlSessionFactory.openSession(), true);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                               ObjectProvider<TypeConverter> typeConverter,
                                               ObjectProvider<SqlPagingSupport> sqlPagingSupport,
                                               ObjectProvider<SqlGenerator> sqlGenerator,
                                               ObjectProvider<List<SqlInterceptor>> interceptors) {
        return () -> {
            SpringTransactionSqlSession sqlSession = new SpringTransactionSqlSession(dataSource);
            sqlPagingSupport.ifAvailable(sqlSession::setSqlPagingSupport);
            sqlGenerator.ifAvailable(sqlSession::setSqlGenerator);
            typeConverter.ifAvailable(sqlSession::setTypeConverter);
            interceptors.ifAvailable(sqlSession::addInterceptor);
            return sqlSession;
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public static KoraMapperBeanDefinitionRegistryPostProcessor koraMapperBeanDefinitionRegistryPostProcessor() {
        return new KoraMapperBeanDefinitionRegistryPostProcessor();
    }

    @Bean
    @ConditionalOnMissingBean(SqlSession.class)
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SqlSession sqlSession(SqlSessionFactory sqlSessionFactory) {
        return sqlSessionFactory.openSession();
    }

    public static final class SqlBinding implements AutoCloseable {
        @Override
        public void close() {
            Sql.clear();
        }
    }
}
