package com.example.simple.mapper;

import com.example.simple.entity.User;

import java.util.List;

public interface UserMapper {
    User selectById(Long id);

    List<User> selectByAgeRange(Integer minAge, Integer maxAge);

    List<User> selectByIds(List<Long> ids);

    int insert(User user);
}
