# FastSQL Memory

## Overview

This repository was refactored into three modules:

- `fastsql-core`
- `fastsql-compiler`
- `fastsql-idea-plugin`

The goal is to support SQL template syntax directly inside Java string text blocks, for example:

```java
var sql = """
select * from user
where id in (
  @for (var i = 0; i < list.size(); i++) {
    ${list.get(i)}
    @if (i != list.size() - 1) {,}
  }
)
""";
```

## Current module responsibilities

### `fastsql-core`

Provides shared template parsing and syntax utilities.

Implemented:

- `TemplateParser`
- `TemplateNode`
- `TemplateParseException`
- `TemplateSupport`
- `FastSql`

Template syntax currently recognized:

- `@if (...) { ... }`
- `@for (...) { ... }`
- `${...}`

### `fastsql-compiler`

Implements compile-time rewriting using a `javac` plugin.

Current behavior:

- Supports bare Java string literals / text blocks, not only `FastSql.sql(...)`
- Detects templates by syntax markers:
  - `@if`
  - `@for`
  - `${`
- Rewrites matching string literals into `Supplier<String> + StringBuilder` code

Important implementation:

- `FastSqlCompilerPlugin`
- `TemplateLowering`

Publishing support:

- local publish: `publishToMavenLocal`
- remote Maven publish via `maven-publish`
- `fastsql-core` and `fastsql-compiler` both publishable

Notes:

- `fastsql-compiler` intentionally does not publish `javadocJar`, because `com.sun.tools.javac.*` causes unstable Javadoc generation under module exports

### `fastsql-idea-plugin`

Implements IntelliJ support for template editing inside Java string text blocks.

Important architecture change:

- initial approach tried to work directly on Java `PsiLiteralExpression`
- this was not enough for string-internal completion/highlighting
- plugin was migrated to **Language Injection**

Current injected language pieces:

- `FastSqlTemplateLanguage`
- `FastSqlTemplateFileType`
- `FastSqlTemplateLexer`
- `FastSqlTemplateParserDefinition`
- `FastSqlTemplateInjector`
- `FastSqlSyntaxHighlighter`
- `FastSqlSyntaxHighlighterFactory`
- `FastSqlBraceMatcher`

## Current IntelliJ capabilities

### Recognition

Supports bare form:

```java
var sql = """ ... """;
```

Recognition strategy:

- if template text contains `@if`, `@for`, `${`
- or looks like an editing candidate while typing
- also keeps a weak fallback for local variables named like `sql`

### Syntax checking

Implemented in `FastSqlAnnotator`.

Checks:

- template structure errors from `TemplateParser`
- Java fragment syntax errors inside:
  - `${...}`
  - `@if(...)`
  - `@for(...)`

Important bug fixes already done:

- annotation ranges now use correct injected-file-relative offsets
- text block source text now uses `literal.getText()` plus `valueRange`, not `literal.getValue()`
- Java fragment validation no longer validates incomplete prefix slices, only complete fragments

This fixed false errors such as:

```java
@for(var it : list) { ... }
```

where earlier `var` incorrectly reported `expected ';'`.

### Completion

Implemented in `FastSqlCompletionContributor`.

Current completion behavior:

- string-internal completion now triggers through injected language
- `@` can suggest:
  - `if`
  - `for`
- Java variable suggestions in `${...}` / `@if(...)` / `@for(...)`
- field and method suggestions for expressions like:
  - `list.`
  - `list.get(0).`
- method items display:
  - method name
  - parameter list
  - return type
- field items display:
  - field name
  - field type
- variable items display:
  - variable name
  - variable type

### Java PSI bridging

Completion is now partially bridged to Java PSI:

- uses `PsiExpressionCodeFragment`
- uses Java reference variants where possible
- uses Java expression type resolution for chained expressions

This means completion is now much closer to native IDEA Java completion than the original hand-built member listing approach.

### Template local variable support

Implemented support for template loop variables from enhanced-for syntax:

```java
@for(var it : list) {
  ${it}
  ${it.}
}
```

Current support:

- `it` becomes visible inside the loop body
- `it` is suggested inside `${...}` and `@if(...)`
- `it.` member completion is supported
- element type inference works for:
  - arrays
  - `Iterable<T>`
- explicit types also work:

```java
@for(User it : list) { ... }
```

Key utility for this:

- `collectActiveTemplateVariables`
- `resolveEnhancedForVariable`
- `inferEnhancedForVariableType`

## Current highlighting / editor support

Implemented:

- directive highlighting for `@if` / `@for`
- interpolation marker highlighting for `${`
- brace highlighting for `{}` 
- parenthesis highlighting for `()`
- paired brace matching for:
  - `{ }`
  - `( )`

## Build and local install notes

### JDK

Working JDK used during this session:

`C:\Users\cui19\.jdks\graalvm-ce-17.0.9`

The machine default `JAVA_HOME` had previously been invalid, so builds were run by explicitly setting:

```powershell
$env:JAVA_HOME='C:\Users\cui19\.jdks\graalvm-ce-17.0.9'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### Plugin local build

```powershell
.\gradlew :fastsql-idea-plugin:buildPlugin
```

ZIP output:

- `fastsql-idea-plugin/build/distributions`

### Plugin local install

Install in IntelliJ via:

- Settings / Plugins / gear icon / Install Plugin from Disk

### Plugin sandbox run

```powershell
.\gradlew :fastsql-idea-plugin:runIde
```

If sandbox packaging fails because plugin JAR is locked:

- close running IDEA / sandbox instance
- delete `.intellijPlatform/sandbox/.../plugins/fastsql-idea-plugin`

## Publishing notes

### `fastsql-idea-plugin`

Marketplace publish support added in `fastsql-idea-plugin/build.gradle.kts`.

Token lookup:

- Gradle property `intellijPublishToken`
- env `PUBLISH_TOKEN`
- env `JETBRAINS_MARKETPLACE_TOKEN`

For local use, online publish is not required.

### `fastsql-core` and `fastsql-compiler`

Maven publish support added.

Local publish:

```powershell
.\gradlew :fastsql-core:publishToMavenLocal :fastsql-compiler:publishToMavenLocal
```

Remote publish supported via:

- `mavenSnapshotsRepoUrl` / `MAVEN_SNAPSHOTS_URL`
- `mavenReleasesRepoUrl` / `MAVEN_RELEASES_URL`
- `mavenRepoUsername` / `MAVEN_USERNAME`
- `mavenRepoPassword` / `MAVEN_PASSWORD`

## Known limitations / next recommended work

These areas are still not fully native-IDE-grade:

1. Java fragment completion is closer to native IDEA, but not fully delegated to the IntelliJ Java completion engine.
2. Template local variables are supported mainly for enhanced-for loop variables; more advanced scope rules are still limited.
3. Java fragment error ranges are still fairly coarse and often highlight a single character.
4. Highlighting inside `${...}` is still shallow; only delimiters and control markers are highlighted, not the full embedded Java syntax.
5. `@else` / `@elseif` are not implemented.
6. Formatting / indentation support is not implemented.

## Good next steps

Recommended next improvements, in order:

1. Make template local variables available more deeply inside Java fragment parsing and validation.
2. Improve `${...}` internal Java syntax highlighting.
3. Add `@else` / `@elseif`.
4. Improve Java fragment error ranges.
5. Add formatter / indentation support.

## Recovery prompt

Next time, you can ask:

- "Read `MEMORY.md` and continue FastSQL work"
- "Continue from FastSQL memory"
- "Restore previous FastSQL context from `MEMORY.md`"

