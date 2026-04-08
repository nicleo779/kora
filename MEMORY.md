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
  - `TypeConverter.register(TargetType.class, value -> ...)`

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
- Field and method metadata are lazily initialized in generated reflector classes.
- Runtime annotation metadata is generated only when explicitly enabled.
- Only runtime-visible annotations should be emitted into metadata.

## Generated Reflector Implementation Rules

- `get/set` should reuse `invoke(...)` when getter/setter methods exist.
- Direct field access is only fallback when accessor method is unavailable and field access is legal.
- Generated reflector metadata is produced at compile time, not fetched with runtime reflection APIs such as `getDeclaredField` or `getDeclaredMethod`.

## Example Project Convention

- Example entity `simple/.../User.java` currently opts into full metadata generation with:
  - `@Reflect(metadata = ReflectMetadataLevel.ALL)`
- This is mainly to keep integration tests covering method metadata behavior.

## Non-Persistent Machine-Specific Notes

- Do not store local JDK paths or machine-specific environment assumptions in project memory.
