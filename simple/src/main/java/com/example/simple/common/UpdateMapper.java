package com.example.simple.common;

public interface UpdateMapper<T> {
    int updateNameById(Long id, String name);
}
