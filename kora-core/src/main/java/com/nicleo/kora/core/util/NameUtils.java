package com.nicleo.kora.core.util;

public final class NameUtils {
    private NameUtils() {
    }

    public static String camelToSnake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (Character.isUpperCase(current)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    public static String snakeToCamel(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        StringBuilder builder = new StringBuilder(input.length());
        boolean upperNext = false;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '_') {
                upperNext = true;
                continue;
            }
            char normalized = Character.toLowerCase(current);
            if (upperNext) {
                builder.append(Character.toUpperCase(normalized));
                upperNext = false;
            } else {
                builder.append(normalized);
            }
        }
        return builder.toString();
    }
}
