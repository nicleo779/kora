package com.example.simple.mapper;

import com.example.simple.common.ViewTypeMapper;
import com.example.simple.dto.UserSummary;
import com.example.simple.entity.User;
import com.nicleo.kora.core.mapper.BaseMapper;

public interface MixedUserMapper extends BaseMapper<User>, ViewTypeMapper<UserSummary> {
}
