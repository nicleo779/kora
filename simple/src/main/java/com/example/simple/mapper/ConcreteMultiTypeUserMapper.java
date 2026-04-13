package com.example.simple.mapper;

import com.example.simple.common.MultiTypeMapper;
import com.example.simple.dto.UserSummary;
import com.example.simple.entity.User;
import com.nicleo.kora.core.mapper.BaseMapper;

public interface ConcreteMultiTypeUserMapper extends BaseMapper<User>, MultiTypeMapper<UserSummary, User> {
}
