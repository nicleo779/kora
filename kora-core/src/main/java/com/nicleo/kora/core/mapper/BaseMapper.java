package com.nicleo.kora.core.mapper;

import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.UpdateWrapper;
import com.nicleo.kora.core.query.WhereWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface BaseMapper<T> {
    T selectById(Serializable id);

    List<T> selectByIds(Collection<? extends Serializable> ids);

    List<T> selectList(WhereWrapper query);

    T selectOne(WhereWrapper query);

    long count(WhereWrapper query);

    Page<T> page(Paging paging, WhereWrapper query);

    int insert(T entity);

    int insert(Collection<T> entities);

    int updateById(T entity);

    int updateById(Collection<T> entities);

    int delete(WhereWrapper query);

    int update(UpdateWrapper updateWrapper);

    int deleteById(Serializable id);

    int deleteByIds(Collection<? extends Serializable> ids);
}
