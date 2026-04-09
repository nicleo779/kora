package com.example.simple.query;

import com.example.simple.entity.User;
import com.nicleo.kora.core.query.Column;
import com.nicleo.kora.core.query.EntityTable;

public final class UserTable extends EntityTable<User> {
    public static final UserTable USERS = new UserTable("users", null);

    public final Column<User, Long> ID = column("id", Long.class);
    public final Column<User, String> NAME = column("name", String.class);
    public final Column<User, Integer> AGE = column("age", Integer.class);
    public final Column<User, String> USER_NAME = column("user_name", String.class);

    private UserTable(String tableName, String alias) {
        super(User.class, tableName, alias);
    }

    @Override
    protected EntityTable<User> copy(String alias) {
        return new UserTable(tableName(), alias);
    }
}
