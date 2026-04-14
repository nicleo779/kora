package com.example.simple.mapper;

import com.example.simple.common.Pair;

import java.time.LocalDateTime;
import java.util.List;

public interface PairUserMapper {
    List<Pair<String, Long>> memberNumOfDay(LocalDateTime start, LocalDateTime end);
}
