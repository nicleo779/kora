# Project Memory

## Runtime Mapping Rules

- `GeneratedReflector` only accepts real Java field names.
- Column-name to field-name adaptation happens in `GeneratedRowMapper`.
- `GeneratedRowMapper` mapping rule:
  - try column label as-is
  - if no field matches, convert `snake_case` column name to `camelCase`
- Type conversion happens in `GeneratedRowMapper` before calling `reflector.set(...)`.
- `GeneratedReflector` does not use `TypeConverter` for field assignment anymore.

## Type Conversion

- Custom type converters are registered by target field type only.
- Converter shape:
  - `CustomTypeConverter<T> { T convert(Object value); }`
- Registration shape:
  - `sqlSession.registerTypeConverter(TargetType.class, value -> ...)`
- `TypeConverter` is now scoped to the `SqlSession` instance, not global static state.
- Different `SqlSession` instances do not share custom converters.

## SQL Session And Cache

- `DefaultSqlSession` has first-level cache scoped to the `SqlSession` instance.
- Cache key uses SQL + args + result type.
- `update(...)` clears first-level cache.
- `clearCache()` is available for manual invalidation.
- Do not assume a global singleton `SqlSession` is safe when first-level cache is enabled.

## SQL Interceptor Rules

- SQL interceptor works on `SqlExecutionContext + SqlRequest`.
- Interceptor can rewrite SQL and args.
- `SqlExecutionContext` includes current `SqlSession`.
- Interceptor context passed into interceptors is `withoutInterceptors()` by default.
- Purpose: internal queries such as pagination `count(*)` should not recursively re-enter interceptor chain unless explicitly requested.

## Generated Reflector Metadata

- `@Reflect` supports metadata level control:
  - `NONE`
  - `FIELDS`
  - `METHODS`
  - `ALL`
- `@Reflect` also supports `annotationMetadata = true/false`.
- Default behavior is lightweight:
  - `metadata = FIELDS`
  - `annotationMetadata = false`
- `GeneratedReflector#getFields()` and `getMethods()` return lightweight name arrays.
- Detailed `FieldInfo` / `MethodInfo[]` are lazily initialized by `getField(name)` / `getMethod(name)`.
- `hasField(name)` / `hasMethod(name)` are the cheap existence checks and should be preferred on hot paths.
- `GeneratedReflector#getClassInfo()` exposes class-level metadata including type, direct superclass, modifiers, and class annotations.
- Generated reflectors filter out `Object`-derived methods such as `equals`, `hashCode`, and `toString` from method metadata.
- Inherited field/method access is delegated through the parent reflector when the direct superclass also has `@Reflect`.
- Runtime annotation metadata is generated only when explicitly enabled.
- Only runtime-visible annotations should be emitted into metadata.
- Mapper auto-reflect defaults to `FIELDS`.
- Auto-reflected child/parent types do not expose method metadata unless explicitly enabled.

## Generated Reflector Implementation Rules

- `get/set` should directly call accessor methods when available.
- Direct field access is only fallback when accessor method is unavailable and field access is legal.
- Generated reflector metadata is produced at compile time, not fetched with runtime reflection APIs such as `getDeclaredField` or `getDeclaredMethod`.

## Mapper Capability Rules

- Shared mapper abilities can be registered with `@MapperCapability(ContractInterface.class)`.
- Capability implementations may extend `AbstractMapper<T>`.
- Generated mapper implementations also extend `AbstractMapper<T>`.
- Business mappers can inherit shared capability interfaces directly or through multiple parent-interface levels.
- Generated mapper implementations create capability delegates automatically and forward inherited methods.
- Supported capability constructors are:
  - `(SqlSession)`
  - `(SqlSession, Class<?>)`

## Example Project Convention

- `simple` now includes a shared mapper capability chain:
  - `WriteMapper<T> extends UpdateMapper<T>`
  - `UserMapper extends WriteMapper<User>`
- `simple` also covers nested DTO auto-reflect and generated capability delegation.

## Non-Persistent Machine-Specific Notes

- Do not store local JDK paths or machine-specific environment assumptions in project memory.
