package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.dialect.IdentifierPolicy;

import java.util.*;

public final class NativeIdentifierPolicy implements IdentifierPolicy {
    private static final String[] COMMON_RESERVED = new String[]{"DISTINCT", "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "TABLE", "WHERE", "GROUP", "HAVING", "ORDER", "NUMBER", "ADD", "ALL", "AND", "AS", "ASC", "DESC", "BY", "FROM", "JOIN", "LEFT", "RIGHT", "UNION", "IS", "OR", "SET", "WHEN", "CASE", "ELSE", "LIKE", "NULL", "NOT", "IN", "ON", "DROP", "KEY", "EXIT", "INNER", "END", "VIEW"};
    private static final String[] MYSQL_RESERVED = new String[]{"BLOB", "BOTH", "CALL", "CHAR", "DEC", "DIV", "DUAL", "EACH", "FOR", "GET", "IF", "INT", "INT1", "INT2", "INT3", "INT4", "INT8", "INTO", "KEYS", "KILL", "LAG", "LEAD", "LOAD", "LOCK", "LONG", "LOOP", "MOD", "OF", "OUT", "OVER", "RANK", "READ", "REAL", "ROW", "ROWS", "SHOW", "SQL", "SSL", "THEN", "TO", "TRUE", "UNDO", "USE", "WITH", "XOR", "LIMIT"};
    private static final String[] SQLITE_RESERVED = new String[]{"CAST", "EACH", "FAIL", "FOR", "FULL", "GLOB", "IF", "INTO", "NO", "OF", "PLAN", "ROW", "TEMP", "THEN", "TO", "WITH"};
    private static final String[] H2_RESERVED = new String[]{"INTO", "THEN", "TOP"};
    private static final String[] POSTGRESQL_RESERVED = new String[]{"ALSO", "ANY", "AT", "BIT", "BOTH", "CALL", "CAST", "CHAR", "COPY", "COST", "CSV", "CUBE", "DATA", "DAY", "DEC", "DO", "EACH", "ENUM", "FOR", "FULL", "HOLD", "HOUR", "IF", "INT", "INTO", "JSON", "KEEP", "KEYS", "LAST", "LOAD", "LOCK", "MODE", "MOVE", "NAME", "NEW", "NEXT", "NFC", "NFD", "NFKC", "NFKD", "NO", "NONE", "OF", "OFF", "OIDS", "OLD", "OMIT", "ONLY", "OUT", "OVER", "PATH", "PLAN", "READ", "REAL", "REF", "ROLE", "ROW", "ROWS", "RULE", "SETS", "SHOW", "SKIP", "SOME", "SQL", "TEMP", "TEXT", "THEN", "TIES", "TIME", "TO", "TRIM", "TRUE", "TYPE", "USER", "WITH", "WORK", "XML", "YEAR", "YES", "ZONE"};
    private static final String[] ORACLE_RESERVED = new String[]{"FETCH", "INTO", "LIMIT", "OFFSET", "OUTER", "THEN", "TOP", "VALUES", "UNIQUE", "CONNECT", "LEVEL", "MINUS", "PRIOR"};
    private static final String[] SQLSERVER_RESERVED = new String[]{"FETCH", "INTO", "LIMIT", "OFFSET", "OUTER", "THEN", "TOP", "VALUES", "UNIQUE", "IDENTITY", "MERGE", "NVARCHAR", "ROWGUIDCOL", "TRY_CONVERT"};
    private final String prefix;
    private final String suffix;
    private final Set<String> reservedWords;

    private NativeIdentifierPolicy(String prefix, String suffix, Set<String> reservedWords) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.reservedWords = reservedWords;
    }

    public static NativeIdentifierPolicy mysql() {
        return new NativeIdentifierPolicy("`", "`", merge(MYSQL_RESERVED));
    }

    public static NativeIdentifierPolicy mariaDb() {
        return new NativeIdentifierPolicy("`", "`", merge(MYSQL_RESERVED));
    }

    public static NativeIdentifierPolicy sqlite() {
        return new NativeIdentifierPolicy("\"", "\"", merge(SQLITE_RESERVED));
    }

    public static NativeIdentifierPolicy h2() {
        return new NativeIdentifierPolicy("\"", "\"", merge(H2_RESERVED));
    }

    public static NativeIdentifierPolicy postgreSql() {
        return new NativeIdentifierPolicy("\"", "\"", merge(POSTGRESQL_RESERVED));
    }

    public static NativeIdentifierPolicy oracle() {
        return new NativeIdentifierPolicy("\"", "\"", merge(ORACLE_RESERVED));
    }

    public static NativeIdentifierPolicy sqlServer() {
        return new NativeIdentifierPolicy("[", "]", merge(SQLSERVER_RESERVED));
    }

    private static Set<String> merge(String[] b) {
        var str = Arrays.copyOf(COMMON_RESERVED, COMMON_RESERVED.length + b.length);
        System.arraycopy(b, 0, str, COMMON_RESERVED.length, b.length);
        return Set.of(str);
    }

    @Override
    public String quote(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return identifier;
        }
        if (!shouldQuote(identifier)) {
            return identifier;
        }
        return prefix + escape(identifier) + suffix;
    }

    private boolean shouldQuote(String identifier) {
        return reservedWords.contains(identifier.toUpperCase(Locale.ROOT));
    }


    private String escape(String identifier) {
        if ("[".equals(prefix) && "]".equals(suffix)) {
            return identifier.replace("]", "]]");
        }
        if (prefix.equals(suffix)) {
            return identifier.replace(suffix, suffix + suffix);
        }
        return identifier;
    }
}
