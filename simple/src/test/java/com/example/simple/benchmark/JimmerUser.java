package com.example.simple.benchmark;

import org.babyfish.jimmer.sql.Column;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.Table;

@Entity
@Table(name = "users")
public interface JimmerUser {
    @Id
    long id();

    String name();

    int age();

    @Column(name = "login_name")
    String loginName();
}
