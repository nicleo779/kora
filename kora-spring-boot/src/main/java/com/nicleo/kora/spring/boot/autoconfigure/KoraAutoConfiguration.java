package com.nicleo.kora.spring.boot.autoconfigure;

import java.util.List;

import javax.sql.DataSource;

import com.nicleo.kora.core.runtime.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

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

    @Bean
    @ConditionalOnMissingBean
    public DbType dbType() {
        return DbType.MYSQL;
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
    public SqlBinder sqlBinder(SqlSessionFactory sqlSessionFactory) {
        Sql.bind(sqlSessionFactory);
        return new SqlBinder();
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

    public static final class SqlBinder {
    }
}
