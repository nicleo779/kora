package com.example.simple.entity;

import com.nicleo.kora.core.annotation.Reflect;
import com.nicleo.kora.core.annotation.ReflectMetadataLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Reflect(metadata = ReflectMetadataLevel.ALL)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private Long id;
    private String name;
    private Integer age;
    private String userName;
}
