package com.example.simple.mapper;

import com.example.simple.TestMapperTag;
import com.example.simple.common.ReadMapper;
import com.example.simple.common.WriteMapper;
import com.example.simple.dto.UserFilter;
import com.example.simple.dto.UserQuery;
import com.example.simple.dto.UserSummary;
import com.example.simple.entity.User;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserMapper extends ReadMapper<User>, WriteMapper<User> {
    @TestMapperTag("selectById")
    User selectById(Long id);

    List<User> selectByAgeRange(Integer minAge, Integer maxAge);

    List<User> selectByIds(List<Long> ids);

    List<UserSummary> selectSummaries(UserQuery query);

    List<User> selectByNestedFilter(UserFilter filter);

    long selectUserCount();

    BigDecimal selectMaxAgeDecimal();

    List<BigDecimal> selectAgeDecimals();

    Map<String, Object> selectUserAsMap(Long id);

    List<Map<String, Object>> selectUserMaps();

    Page<User> selectPage(Paging paging, Integer minAge, Integer maxAge);

    int expireUsers(LocalDateTime now);
}
