package com.nicleo.kora.spring.boot;

import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.QueryWrapper;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionException;
import com.nicleo.kora.core.runtime.SqlSessionFactory;
import com.nicleo.kora.core.xml.SqlCommandType;

public final class Sql {
    private static volatile SqlSessionFactory sqlSessionFactory;

    private Sql() {
    }

    public static void bind(SqlSessionFactory sqlSessionFactory) {
        Sql.sqlSessionFactory = sqlSessionFactory;
    }

    public static SqlExecutor of(QueryWrapper queryWrapper) {
        if (sqlSessionFactory == null) {
            throw new SqlSessionException("SqlSessionFactory is not bound. Did you enable kora-spring-boot auto-configuration?");
        }
        return new SqlExecutor(queryWrapper, sqlSessionFactory);
    }

    public static final class SqlExecutor {
        private final QueryWrapper queryWrapper;
        private final SqlSessionFactory sqlSessionFactory;

        private SqlExecutor(QueryWrapper queryWrapper, SqlSessionFactory sqlSessionFactory) {
            this.queryWrapper = queryWrapper;
            this.sqlSessionFactory = sqlSessionFactory;
        }

        public <R> R one(Class<R> resultType) {
            try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
                SqlRequest request = sqlSession.getSqlGenerator().renderQuery(queryWrapper.toDefinition(), sqlSession.getDbType());
                return sqlSession.selectOne(request.getSql(), request.getArgs(), resultType);
            }
        }

        public <R> java.util.List<R> list(Class<R> resultType) {
            try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
                SqlRequest request = sqlSession.getSqlGenerator().renderQuery(queryWrapper.toDefinition(), sqlSession.getDbType());
                return sqlSession.selectList(request.getSql(), request.getArgs(), resultType);
            }
        }

        public <R> Page<R> page(Paging paging, Class<R> resultType) {
            try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
                var definition = queryWrapper.toDefinition();
                SqlRequest request = sqlSession.getSqlGenerator().renderQuery(definition, sqlSession.getDbType());
                SqlRequest countRequest = sqlSession.getSqlGenerator().rewriteCount(definition, sqlSession.getDbType());
                SqlExecutionContext context = new SqlExecutionContext(
                        sqlSession,
                        Sql.class.getName(),
                        "page",
                        SqlCommandType.SELECT,
                        resultType,
                        paging,
                        countRequest,
                        true
                );
                return sqlSession.getSqlPagingSupport().page(sqlSession, context, request.getSql(), request.getArgs(), paging, resultType);
            }
        }
    }
}
