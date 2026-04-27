# kora

`kora` 是一个轻量级、偏 MyBatis 风格的 SQL 框架，核心目标是把：

- XML SQL 的可读性
- Wrapper DSL 的类型安全
- 编译期代码生成
- 无运行时反射

组合到一起。

当前仓库包含核心运行时、注解处理器、Spring Boot / Quarkus 集成，以及一个可直接运行的 `simple` 示例模块。

## 当前能力

- `@KoraScan` 扫描实体包、Mapper 包，XML 通过 `-Akora.mapper=...` 指定
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
- `SqlExecutor` Debug 级别 SQL 与参数日志
- Spring Boot 自动注册 `SqlSessionFactory`、`SqlSession`、Mapper Bean
- Quarkus 自动注册 `SqlExecutor`、Mapper Bean
- 静态 `Sql.query()` / `Sql.from(...)` / `Sql.select(...)` 查询入口
- SQL Dialect SPI

## 环境

- JDK 21
- Gradle Wrapper

## 快速开始

### 1. 依赖

```kotlin
dependencies {
    implementation("com.nicleo:kora-core:1.1.0")
    annotationProcessor("com.nicleo:kora-processor:1.1.0")
}
```

在当前多模块仓库内也可以直接依赖项目模块：

```kotlin
dependencies {
    implementation(project(":kora-core"))
    annotationProcessor(project(":kora-processor"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Akora.mapper=${project.projectDir}/src/main/resources/mapper")
}
```

### 2. 扫描入口

```java
package com.example.simple.config;

import com.nicleo.kora.core.annotation.KoraScan;

@KoraScan(
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

### SQL Debug 日志

`DefaultSqlExecutor` 会在 `DEBUG` 级别打印执行 SQL 与参数。

Spring Boot 示例：

```yaml
logging:
  level:
    com.nicleo.kora.core.runtime.jdbc.DefaultSqlExecutor: DEBUG
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
    implementation("com.nicleo:kora-spring-boot:1.1.0")
    annotationProcessor("com.nicleo:kora-processor:1.1.0")
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
User user = Sql.query()
        .selectAll()
        .from(UserTable.TABLE)
        .orderBy(order -> order.asc(UserTable.TABLE.id))
        .limit(1)
        .one(User.class);
```

也支持更短的查询入口：

```java
User user = Sql.from(UserTable.TABLE)
        .where(UserTable.TABLE.id.eq(1L))
        .one(User.class);
```

```java
User user = Sql.select(UserTable.TABLE, UserTable.TABLE.id.eq(1L));
List<User> users = Sql.selectList(
        UserTable.TABLE,
        UserTable.TABLE.age.ge(18),
        UserTable.TABLE.name.isNotNull()
);
```

说明：

- `Sql.query()`
从空查询开始，适合复杂 DSL 组合
- `Sql.from(table)`
默认 `selectAll().from(table)`，适合从单表快速起查询
- `Sql.select(table, conditions...)`
默认单条查询语义，内部会执行 `.one(table.entityType())`
- `Sql.selectList(table, conditions...)`
默认多条查询语义，内部会执行 `.list(table.entityType())`

也支持直接静态 CRUD：

```java
Sql.insert(user);
Sql.updateById(user);
Sql.deleteById(User.class, 1L);
```

## Quarkus 集成

### 依赖

```kotlin
dependencies {
    implementation("com.nicleo:kora-quarkus:1.1.0")
    annotationProcessor("com.nicleo:kora-processor:1.1.0")
}
```

### 自动配置提供

存在 `DataSource` 时，`kora-quarkus` 会自动提供：

- `SqlExecutor`
- `SqlPagingSupport`
- `SqlGenerator`
- Mapper Bean 自动注册
- 静态 `Sql` 入口绑定
- `@KoraScan` 生成表/Reflector 的启动期安装

### 用法

和 Spring Boot 一样，Quarkus 下仍然通过 `@KoraScan` 描述实体与 Mapper 包：

```java
@KoraScan(
        entity = {"com.example.quarkus.entity"},
        mapper = {"com.example.quarkus.mapper"}
)
public class KoraQuarkusConfig {
}
```

生成的 Mapper 可以直接通过 CDI 注入：

```java
@Inject
UserMapper userMapper;
```

静态 DSL 入口同样可用：

```java
User user = Sql.from(Tables.get(User.class))
        .limit(1)
        .one(User.class);
```

## 本地验证

运行全部测试：

```bash
./gradlew test
```

## 性能测试

`simple` 模块内置了基于 `JMH` 的性能测试，当前覆盖这些场景：

- 单条查询 `selectById`
- 范围查询 `selectByAgeRange`
- 单条写入 `insertOne`
- 单条更新 `updateById`
- 单条删除 `deleteById`
- 分页查询 `page`
- 批量写入 `batchInsert(100 rows/batch)`

性能测试代码：

- `simple/src/test/java/com/example/simple/benchmark/SimpleMapperPerformanceBenchmark.java`

运行方式：

```bash
./gradlew :simple:jmh
```

只看 `Kora` / `MyBatis-Plus` / `jOOQ` / `Jimmer` 对比：

```bash
./gradlew :simple:jmh --args "SimpleMapperPerformanceBenchmark.(kora|myBatisPlus|jooq|jimmer).* -wi 1 -i 3 -w 1s -r 1s -f 1"
```

说明：

- 默认基准模式为 `Throughput`
- 输出单位为 `ops/s`
- 数据库使用内存 `H2`
- 当前主对比维度是 `Kora`、`MyBatis-Plus`、`jOOQ`、`Jimmer`
- `MyBatis` 查询基线代码仍保留在基准类中，便于后续单独补跑
- `updateById` 与 `deleteById` 场景会在基准过程中保证命中存在数据，避免退化为大量空操作

### 本地测试数据

测试环境：

- JDK `21`
- GraalVM CE `21.0.2`
- `1` 线程
- `1` 次预热 / `3` 次测量 / 每次 `1s`

#### 四框架结果


| 场景                 | Kora (ops/s) | MyBatis-Plus (ops/s) | jOOQ (ops/s) | Jimmer (ops/s) |
| ------------------ | ------------ | -------------------- | ------------ | -------------- |
| `selectById`       | 134,580.343  | 64,546.749           | 86,021.502   | 89,088.124     |
| `selectByAgeRange` | 14,783.318   | 1,015.885            | 9,038.434    | 14,553.187     |
| `insertOne`        | 148,416.712  | 48,575.187           | 86,434.914   | 66,320.762     |
| `updateById`       | 146,268.050  | 33,746.205           | 83,948.316   | 55,267.412     |
| `deleteById`       | 115,751.885  | 30,104.550           | 69,977.608   | 115,694.329    |
| `page`             | 5,852.455    | 4,127.696            | 5,093.807    | 5,804.739      |
| `batchInsert`      | 3,981.074    | 1,411.412            | 1,921.091    | 1,754.450      |


批量写入按 `100 rows/batch` 折算吞吐：

- `Kora`: `398,107 rows/s`
- `MyBatis-Plus`: `141,141 rows/s`
- `jOOQ`: `192,109 rows/s`
- `Jimmer`: `175,445 rows/s`

当前这组本地结果下，`Kora` 在 `selectById`、`selectByAgeRange`、`insertOne`、`updateById`、`deleteById`、`page`、`batchInsert` 上最高。

#### 图表

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#0f172a","primaryColor":"#2563eb","secondaryColor":"#f97316","tertiaryColor":"#22c55e","primaryTextColor":"#e5e7eb","secondaryTextColor":"#e5e7eb","tertiaryTextColor":"#e5e7eb","lineColor":"#94a3b8","textColor":"#e5e7eb","mainBkg":"#0f172a","fontFamily":"ui-sans-serif"}}}%%
xychart-beta
    title "CRUD Throughput (ops/s)"
    x-axis ["selectById", "insertOne", "updateById", "deleteById"]
    y-axis "ops/s" 0 --> 160000
    bar [134580, 148417, 146268, 115752]
    bar [64547, 48575, 33746, 30105]
    bar [86022, 86435, 83948, 69978]
    bar [89088, 66321, 55267, 115694]
```



```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#0f172a","primaryColor":"#2563eb","secondaryColor":"#f97316","tertiaryColor":"#22c55e","primaryTextColor":"#e5e7eb","secondaryTextColor":"#e5e7eb","tertiaryTextColor":"#e5e7eb","lineColor":"#94a3b8","textColor":"#e5e7eb","mainBkg":"#0f172a","fontFamily":"ui-sans-serif"}}}%%
xychart-beta
    title "Range / Page / Batch Throughput (ops/s)"
    x-axis ["selectByAgeRange", "page", "batchInsert"]
    y-axis "ops/s" 0 --> 16000
    bar [14783, 5852, 3981]
    bar [1016, 4128, 1411]
    bar [9038, 5094, 1921]
    bar [14553, 5805, 1754]
```



图例：

- 第一组柱状为 `Kora`
- 第二组柱状为 `MyBatis-Plus`
- 第三组柱状为 `jOOQ`
- 第四组柱状为 `Jimmer`

