package com.example.simple.mapper;

import com.example.simple.common.MultiTypeMapper;
import com.example.simple.entity.User;
import com.nicleo.kora.core.mapper.BaseMapper;

@SuppressWarnings({"rawtypes"})
public interface RawMultiTypeUserMapper extends BaseMapper<User>, MultiTypeMapper {
}
