package com.nicleo.kora.core.runtime;

@FunctionalInterface
public interface SqlSessionFactory {
    SqlSession openSession();
}
