package com.example.simple.dto;

import com.nicleo.kora.core.annotation.Alias;

public class UserSummary {
    private Long id;
    private String name;
    @Alias("login_name")
    private String userName;

    public UserSummary() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
