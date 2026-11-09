package io.coredge.health.claims.annotations.impl;

public class GlobalPropertyExpressionParser {

    public static PropertyKey parse(String expression) {
        if (!expression.startsWith("${") || !expression.endsWith("}")) {
            throw new IllegalArgumentException("Invalid format: " + expression);
        }

        String content = expression.substring(2, expression.length() - 1);
        String[] parts = content.split(":", 2);

        String key = parts[0];
        String defaultValue = parts.length > 1 ? parts[1] : null;

        return new PropertyKey(key, defaultValue);
    }

    public record PropertyKey(String key, String defaultValue) {}
}