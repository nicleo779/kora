package com.example.simple.inheritance;

import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;

@Reflect(metadata = ReflectMetadataLevel.ALL, annotationMetadata = true)
@TestReflectTag("admin")
public class AdminUser extends BaseUser {
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
