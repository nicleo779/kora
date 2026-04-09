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

    List<T> selectByIds(Collection<Serializable> ids);

    List<T> selectList(WhereWrapper<T> query);

    T selectOne(WhereWrapper<T> query);

    Page<T> page(Paging paging, WhereWrapper<T> query);

    int insert(T entity);

    int insert(Collection<T> entities);

    int updateById(T entity);

    int updateById(Collection<T> entities);

    int delete(WhereWrapper<T> query);

    int update(UpdateWrapper<T> updateWrapper);

    int deleteById(Serializable id);

    int deleteByIds(Collection<Serializable> ids);
}
