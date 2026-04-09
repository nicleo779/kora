# kora

A lightweight, MyBatis-style compile-time framework with two modules:

- `kora-core`: annotations, runtime interfaces, JDBC execution, reflector registry, XML model, and SQL template parsing
- `kora-processor`: unified compile-time processor for scan config, mapper generation, meta generation, and `@Reflect`

## Unified Entry

Configure scan roots with one annotation:

```java
@KoraScan(
        xml = {"/mapper", "/shared-mapper"},
        entity = {"com.example.entity", "com.example.shared.entity"}
)
public class KoraConfig {
}
```

## Reflect Entry

Use `@Reflect` on any class that needs no-reflection runtime access:

```java
@Reflect
public class User {
    private String name;
    private int age;

    public User() {}
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
```

This generates `UserGeneratedReflector`.

## Features

- `kora-processor` handles both `@KoraScan` and `@Reflect`
- XML `namespace` points to the mapper interface fully qualified name
- Automatically generates `MapperImpl`
- Generated `MapperImpl` now extends `AbstractMapper<T>`
- Supports shared mapper capabilities via `@MapperCapability`
- Shared mapper capabilities can be inherited through multiple interface levels
- Automatically generates entity field constant classes, for example `USER`
- Automatically generates `XxxGeneratedReflector` for `@Reflect` classes
- Runtime APIs now accept `Class<T>` again
- Inside runtime, `GeneratedReflectors.get(User.class)` resolves the generated reflector
- Generated support classes install a project-specific resolver automatically
- Supports XML `select/insert/update/delete`
- Supports `#{id}` and `#{user.name}` bindings
- Parameter names come directly from compile-time method parameter names

## Dependency

```kotlin
dependencies {
    implementation(project(":kora-core"))
    annotationProcessor(project(":kora-processor"))
}
```

## Publishing

Publish both modules to the local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Publish to Repsy:

```bash
./gradlew publishAllPublicationsToRepsyRepository \
  -PrepsyUsername=user30355289 \
  -PrepsyPassword=your-password
```

Or use environment variables:

- `REPSY_USERNAME`
- `REPSY_PASSWORD`

Repository:

- `https://repo.repsy.io/user30355289/public`

## Mapper Example

```java
public interface UserMapper extends WriteMapper<User> {
    User selectById(Long id);
    List<User> selectByAge(int age);
    int insert(User user);
}
```

Generated `UserMapperImpl` calls:

```java
return sqlSession.selectOne(sql, args, User.class);
```

For parameter object access:

```java
Object[] args = new Object[]{GeneratedReflectors.get(User.class).get(user, "name")};
```

## Shared Mapper Capability

Register a shared mapper capability with `@MapperCapability` on the implementation class:

```java
public interface UpdateMapper<T> {
    int updateNameById(Long id, String name);
}

@MapperCapability(UpdateMapper.class)
public class UpdateMapperImpl<T> extends AbstractMapper<T> implements UpdateMapper<T> {
    public UpdateMapperImpl(SqlSession sqlSession, Class<?> entityClass) {
        super(sqlSession, entityClass);
    }

    @Override
    public int updateNameById(Long id, String name) {
        return sqlSession.update("update users set name = ? where id = ?", new Object[]{name, id});
    }
}
```

Then let the business mapper inherit the capability, even through an intermediate parent interface:

```java
public interface WriteMapper<T> extends UpdateMapper<T> {
}

public interface UserMapper extends WriteMapper<User> {
    User selectById(Long id);
}
```

The generated `UserMapperImpl` will:
- extend `AbstractMapper<User>`
- instantiate `UpdateMapperImpl(sqlSession, User.class)`
- delegate inherited capability methods automatically

Supported capability constructors:

- `(SqlSession)`
- `(SqlSession, Class<?>)`

## Runtime Wiring

```java
JdbcDataSource dataSource = new JdbcDataSource();
SqlSession sqlSession = new DefaultSqlSession(dataSource);
sqlSession.registerTypeConverter(LocalDateTime.class, value -> ((Timestamp) value).toLocalDateTime());
UserMapper userMapper = new UserMapperImpl(sqlSession);
User user = userMapper.selectById(1L);
```

The generated `UserMapperImpl` constructor installs the generated reflector resolver automatically.
