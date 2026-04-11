# kora

`kora` 是一个轻量级、偏 MyBatis 风格的 SQL 框架，核心目标是把：

- XML SQL 的可读性
- Wrapper DSL 的类型安全
- 编译期代码生成
- 无运行时反射

组合到一起。

当前仓库包含核心运行时、注解处理器、Spring Boot 自动配置，以及一个可直接运行的 `simple` 示例模块。

## 模块

- `kora-core`
  注解、运行时接口、JDBC 执行、Wrapper DSL、分页、Reflector、Dialect SPI。
- `kora-processor`
  编译期扫描、Mapper 实现生成、实体表元数据生成、Reflector 生成。
- `kora-spring-boot`
  Spring Boot 自动配置、事务感知 `SqlSession`、Mapper Bean 自动注册、静态 `Sql` 入口。
- `simple`
  仓库内示例，覆盖 XML Mapper、Wrapper、分页、Reflector、批量 CRUD、Spring 风格扫描配置。

## 当前能力

- `@KoraScan` 扫描 XML、实体包、Mapper 包
- `@Reflect` 生成 `XxxReflector`
- XML `select / insert / update / delete`
- XML 动态标签 `where / if / foreach`
- `#{id}`、`#{query.minAge}`、`#{filter.range.minAge}` 参数绑定
- 自动生成 `XxxMapperImpl`
- 自动生成 `XxxTable`
- `BaseMapper` 通用 CRUD、批量操作、Wrapper 查询与更新
- `@MapperCapability` 共享能力委托
- `Page<T>` 分页
- `Map<String, Object>`、基础类型、实体映射
- Spring Boot 自动注册 `SqlSessionFactory`、`SqlSession`、Mapper Bean
- 静态 `Sql.of(query)` 查询入口
- SQL Dialect SPI

## 环境

- JDK 21
- Gradle Wrapper

## 快速开始

### 1. 依赖

```kotlin
dependencies {
    implementation("com.nicleo:kora-core:1.0.0")
    annotationProcessor("com.nicleo:kora-processor:1.0.0")
}
```

在当前多模块仓库内也可以直接依赖项目模块：

```kotlin
dependencies {
    implementation(project(":kora-core"))
    annotationProcessor(project(":kora-processor"))
}
```

处理器需要读取项目资源路径时，建议增加：

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Akora.projectDir=${project.projectDir.absolutePath}")
}
```

### 2. 扫描入口

```java
package com.example.simple.config;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
        xml = {"/mapper"},
        entity = {"com.example.simple.entity"},
        mapper = {"com.example.simple.mapper"}
)
public class KoraSimpleConfig {
}
```

### 3. 实体与 Reflector

```java
package com.example.simple.entity;

import com.nicleo.kora.core.annotation.Reflect;

@Reflect
public class User {
    private Long id;
    private String name;
    private Integer age;

    public User() {
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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
```

编译后会生成：

- `UserMapperImpl`
- `UserTable`
- `UserReflector`

### 4. Reflect 分级

`@Reflect` 当前支持两档 metadata：

- `ReflectMetadataLevel.BASIC`
  生成字段访问与基础元数据
- `ReflectMetadataLevel.METHOD`
  额外生成方法相关元数据与方法调用分发

例如：

```java
@Reflect(metadata = ReflectMetadataLevel.METHOD, annotationMetadata = true)
public class User {
}
```

运行期访问：

```java
GeneratedReflector<User> reflector = GeneratedReflectors.get(User.class);
User user = reflector.newInstance();
reflector.set(user, "name", "Alice");
Object value = reflector.get(user, "name");
```

### 5. Mapper 接口

```java
package com.example.simple.mapper;

import com.example.simple.entity.User;
import java.util.List;

public interface UserMapper {
    User selectById(Long id);

    List<User> selectByAgeRange(Integer minAge, Integer maxAge);
}
```

### 6. XML Mapper

`namespace` 必须对应 Mapper 接口全限定名：

```xml
<mapper namespace="com.example.simple.mapper.UserMapper">
    <select id="selectById">
        select id, name, age
        from users
        where id = #{id}
    </select>

    <select id="selectByAgeRange">
        select id, name, age
        from users
        <where>
            <if test="minAge != null">
                age <![CDATA[ >= ]]> #{minAge}
            </if>
            <if test="maxAge != null">
                and age <![CDATA[ <= ]]> #{maxAge}
            </if>
        </where>
        order by id
    </select>
</mapper>
```

## 运行时使用

### 直接使用 `SqlSession`

```java
JdbcDataSource dataSource = new JdbcDataSource();
dataSource.setURL("jdbc:h2:mem:test;MODE=MySQL;DB_CLOSE_DELAY=-1");
dataSource.setUser("sa");
dataSource.setPassword("");

SqlSession sqlSession = new DefaultSqlSession(dataSource);
UserMapper userMapper = new UserMapperImpl(sqlSession);

User user = userMapper.selectById(1L);
```

## Wrapper DSL

除了 XML，也可以直接使用 Wrapper DSL 构造查询。

### 条件查询

```java
List<User> users = userMapper.selectList(
        Wrapper.where()
                .where(
                        UserTable.TABLE.age.ge(18),
                        UserTable.TABLE.name.isNotNull()
                )
                .orderBy(order -> order.asc(UserTable.TABLE.id))
);
```

### 分页

```java
Paging paging = Paging.of(1, 10);
Page<User> page = userMapper.selectPage(paging, 18, 30);
```

### alias-aware DSL

```java
var total = Functions.count().as("total");
var ageGroup = Functions.ifElse(UserTable.TABLE.age.ge(18), "adult", "minor").as("age_group");

var query = Wrapper.query()
        .select(ageGroup, total)
        .from(UserTable.TABLE)
        .groupBy(ageGroup)
        .having(total.ge(2))
        .orderBy(order -> order.desc(total));
```

支持：

- `orderBy(order -> order.desc(total))`
- `orderBy(order -> order.descAlias("total"))`
- `groupBy(ageGroup)`
- `groupByAlias("age_group")`
- `having(total.ge(2))`
- `having(h -> h.geAlias("total", 2))`

### join 快捷写法

```java
var users = UserTable.TABLE;
var manager = UserTable.TABLE.alias("manager");

var query = Wrapper.query()
        .select(users.name, manager.name)
        .from(users)
        .leftJoin(manager, on -> on.eq(users.id, manager.id));
```

现在列对列比较可以直接写：

```java
users.id.eq(manager.id)
users.age.ge(manager.age)
```

## `BaseMapper` 通用能力

当前示例已经覆盖：

- `selectOne`
- `selectList`
- `count`
- `insert(T)`
- `insert(List<T>)`
- `updateById(T)`
- `updateById(List<T>)`
- `update(UpdateWrapper<T>)`
- `deleteById`
- `delete(WhereWrapper<T>)`
- `page(Paging, WhereWrapper<T>)`

## `@MapperCapability`

可以把公共 SQL 能力实现成共享组件，再让业务 Mapper 通过接口继承得到这些方法。

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
        return sqlSession.update(
                "update users set name = ? where id = ?",
                new Object[]{name, id}
        );
    }
}
```

支持的能力构造器：

- `(SqlSession)`
- `(SqlSession, Class<?>)`
- `(SqlSession, EntityTable<?>)`

## SQL Dialect SPI

`kora` 现在已经提供方言 SPI，用于承载：

- 标识符引用规则
- 分页渲染
- query/update/delete/insert renderer
- count rewrite

核心接口：

- `SqlDialect`
- `SqlDialectRegistry`
- `IdentifierPolicy`
- `PagingRenderer`
- `QueryRenderer`
- `UpdateRenderer`
- `DeleteRenderer`
- `InsertRenderer`
- `CountQueryRewriter`

当前默认方言注册器：

- MySQL
- MariaDB
- PostgreSQL
- SQLite
- Oracle
- SQL Server
- H2

## Spring Boot 集成

### 依赖

```kotlin
dependencies {
    implementation("com.nicleo:kora-spring-boot:1.0.0")
    annotationProcessor("com.nicleo:kora-processor:1.0.0")
}
```

### 自动配置提供

存在 `DataSource` 时，`kora-spring-boot` 会自动提供：

- `SqlSessionFactory`
- 原型作用域 `SqlSession`
- `SqlPagingSupport`
- `SqlGenerator`
- Mapper Bean 注册器
- 静态 `Sql` 入口绑定

### 静态查询入口

```java
var query = Wrapper.query()
        .selectAll()
        .from(UserTable.TABLE)
        .orderBy(order -> order.asc(UserTable.TABLE.id))
        .limit(1);

User user = Sql.of(query).one(User.class);
```

## 构建与测试

运行全部测试：

```bash
./gradlew test
```

发布到本地 Maven：

```bash
./gradlew publishToMavenLocal
```

发布到 Repsy：

```bash
./gradlew publishAllPublicationsToRepsyRepository \
  -PrepsyUsername=user30355289 \
  -PrepsyPassword=your-password
```

也可以使用环境变量：

- `REPSY_USERNAME`
- `REPSY_PASSWORD`

仓库地址：

- `https://repo.repsy.io/user30355289/public`

## 项目结构

```text
.
|-- kora-core
|-- kora-processor
|-- kora-spring-boot
`-- simple
```

## 参考文件

- `simple/src/main/java/com/example/simple/config/KoraSimpleConfig.java`
- `simple/src/main/java/com/example/simple/mapper/UserMapper.java`
- `simple/src/main/resources/mapper/UserMapper.xml`
- `simple/src/test/java/com/example/simple/SimpleIntegrationTest.java`
- `kora-spring-boot/src/test/java/com/nicleo/kora/spring/boot/autoconfigure/KoraAutoConfigurationTest.java`
