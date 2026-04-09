package com.example.simple.entity;

import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class User extends BaseUser<Long> {
    private String name;
    private Integer age;
    private String userName;
    private List<String> ids;
    public User() {
    }

    public User(Long id, String name, Integer age, String userName) {
        this.setId(id);
        this.name = name;
        this.age = age;
        this.userName = userName;
    }

}
