package com.nicleo.kora.core.query;

import java.util.List;

public interface SelectMapper<T> {
    List<T> selectList(QueryWrapper<T> query);

    T selectOne(QueryWrapper<T> query);
}
