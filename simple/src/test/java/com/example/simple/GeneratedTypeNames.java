package com.example.simple;

final class GeneratedTypeNames {
    private GeneratedTypeNames() {
    }

    static String reflectorTypeName(Class<?> entityType) {
        return "gen." + packageHash(packageName(entityType)) + "." + entityType.getSimpleName() + "Reflector";
    }

    static String tableTypeName(Class<?> entityType) {
        return "gen." + packageHash(packageName(entityType)) + "." + entityType.getSimpleName() + "Table";
    }

    private static String packageName(Class<?> type) {
        Package classPackage = type.getPackage();
        return classPackage == null ? "" : classPackage.getName();
    }

    private static String packageHash(String packageName) {
        long unsignedHash = Integer.toUnsignedLong(packageName.hashCode());
        if (unsignedHash == 0L) {
            return "a";
        }
        StringBuilder builder = new StringBuilder();
        while (unsignedHash > 0L) {
            int digit = (int) (unsignedHash % 26L);
            builder.append((char) ('a' + digit));
            unsignedHash /= 26L;
        }
        return builder.reverse().toString();
    }
}
