package com.nicleo.kora.spring.boot.autoconfigure;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;

import com.nicleo.kora.core.runtime.DefaultSqlPagingSupport;
import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionFactory;
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
                                               SqlPagingSupport sqlPagingSupport,
                                               SqlGenerator sqlGenerator,
                                               DbType dbType,
                                               ObjectProvider<SqlInterceptor> interceptors) {
        return () -> {
            SpringTransactionSqlSession sqlSession = new SpringTransactionSqlSession(dataSource);
            sqlSession.setSqlPagingSupport(sqlPagingSupport);
            sqlSession.setSqlGenerator(sqlGenerator);
            sqlSession.setDbType(dbType);
            List<SqlInterceptor> interceptorList = interceptors.orderedStream().toList();
            if (!interceptorList.isEmpty()) {
                sqlSession.addInterceptor(interceptorList);
            }
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
    @ConditionalOnMissingBean(SqlSession.class)
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SqlSession sqlSession(SqlSessionFactory sqlSessionFactory) {
        return sqlSessionFactory.openSession();
    }

    public static final class SqlBinder {
    }
}
