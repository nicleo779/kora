package com.nicleo.kora.core.dynamic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class ExpressionEvaluator {
    private ExpressionEvaluator() {
    }

    static Object evaluate(String expression, DynamicSqlContext context) {
        return new Parser(tokenize(expression), context).parseExpression();
    }

    static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0D;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        return true;
    }

    private static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '(' || current == ')' || current == '+') {
                tokens.add(new Token(String.valueOf(current), String.valueOf(current)));
                index++;
                continue;
            }
            if (current == '!' && peek(expression, index + 1) != '=') {
                tokens.add(new Token("!", "!"));
                index++;
                continue;
            }
            if ((current == '!' || current == '=' || current == '>' || current == '<') && peek(expression, index + 1) == '=') {
                tokens.add(new Token("op", expression.substring(index, index + 2)));
                index += 2;
                continue;
            }
            if (current == '>' || current == '<') {
                tokens.add(new Token("op", String.valueOf(current)));
                index++;
                continue;
            }
            if (current == '\'' || current == '"') {
                int end = index + 1;
                StringBuilder value = new StringBuilder();
                while (end < expression.length()) {
                    char ch = expression.charAt(end);
                    if (ch == '\\' && end + 1 < expression.length()) {
                        value.append(expression.charAt(end + 1));
                        end += 2;
                        continue;
                    }
                    if (ch == current) {
                        break;
                    }
                    value.append(ch);
                    end++;
                }
                if (end >= expression.length()) {
                    throw new IllegalArgumentException("Unclosed string in expression: " + expression);
                }
                tokens.add(new Token("string", value.toString()));
                index = end + 1;
                continue;
            }
            if (Character.isDigit(current)) {
                int end = index + 1;
                while (end < expression.length() && (Character.isDigit(expression.charAt(end)) || expression.charAt(end) == '.')) {
                    end++;
                }
                tokens.add(new Token("number", expression.substring(index, end)));
                index = end;
                continue;
            }
            int end = index + 1;
            while (end < expression.length()) {
                char ch = expression.charAt(end);
                if (Character.isWhitespace(ch) || ch == '(' || ch == ')' || ch == '+' || ch == '!' || ch == '=' || ch == '>' || ch == '<' || ch == '\'' || ch == '"') {
                    break;
                }
                end++;
            }
            String word = expression.substring(index, end);
            String lower = word.toLowerCase(Locale.ROOT);
            if ("and".equals(lower) || "or".equals(lower) || "not".equals(lower)) {
                tokens.add(new Token(lower, lower));
            } else if ("null".equals(lower) || "true".equals(lower) || "false".equals(lower)) {
                tokens.add(new Token("literal", lower));
            } else {
                tokens.add(new Token("identifier", word));
            }
            index = end;
        }
        tokens.add(new Token("eof", ""));
        return tokens;
    }

    private static char peek(String expression, int index) {
        return index >= expression.length() ? '\0' : expression.charAt(index);
    }

    private record Token(String type, String value) {
    }

    private static final class Parser {
        private final List<Token> tokens;
        private final DynamicSqlContext context;
        private int index;

        private Parser(List<Token> tokens, DynamicSqlContext context) {
            this.tokens = tokens;
            this.context = context;
        }

        private Object parseExpression() {
            Object value = parseOr();
            expect("eof");
            return value;
        }

        private Object parseOr() {
            Object left = parseAnd();
            while (match("or")) {
                left = toBoolean(left) || toBoolean(parseAnd());
            }
            return left;
        }

        private Object parseAnd() {
            Object left = parseEquality();
            while (match("and")) {
                left = toBoolean(left) && toBoolean(parseEquality());
            }
            return left;
        }

        private Object parseEquality() {
            Object left = parseComparison();
            while (peekType("op") && ("==".equals(peek().value) || "!=".equals(peek().value))) {
                String operator = advance().value;
                Object right = parseComparison();
                boolean result = equalsValue(left, right);
                left = "==".equals(operator) ? result : !result;
            }
            return left;
        }

        private Object parseComparison() {
            Object left = parseAdditive();
            while (peekType("op") && (">".equals(peek().value) || ">=".equals(peek().value) || "<".equals(peek().value) || "<=".equals(peek().value))) {
                String operator = advance().value;
                Object right = parseAdditive();
                left = compare(left, right, operator);
            }
            return left;
        }

        private Object parseAdditive() {
            Object left = parseUnary();
            while (match("+")) {
                Object right = parseUnary();
                if (left instanceof String || right instanceof String) {
                    left = String.valueOf(left) + String.valueOf(right);
                } else if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                    left = leftNumber.doubleValue() + rightNumber.doubleValue();
                } else {
                    left = String.valueOf(left) + right;
                }
            }
            return left;
        }

        private Object parseUnary() {
            if (match("!") || match("not")) {
                return !toBoolean(parseUnary());
            }
            return parsePrimary();
        }

        private Object parsePrimary() {
            if (match("(")) {
                Object value = parseOr();
                expect(")");
                return value;
            }
            Token token = advance();
            return switch (token.type) {
                case "string" -> token.value;
                case "number" -> token.value.contains(".") ? Double.parseDouble(token.value) : Long.parseLong(token.value);
                case "literal" -> switch (token.value) {
                    case "null" -> null;
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new IllegalStateException("Unexpected literal: " + token.value);
                };
                case "identifier" -> context.resolveValue(token.value);
                default -> throw new IllegalArgumentException("Unexpected token '" + token.value + "' in expression");
            };
        }

        private boolean compare(Object left, Object right, String operator) {
            if (left == null || right == null) {
                return false;
            }
            int value;
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                value = Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
            } else if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
                @SuppressWarnings("unchecked")
                Comparable<Object> cast = (Comparable<Object>) comparable;
                value = cast.compareTo(right);
            } else {
                value = String.valueOf(left).compareTo(String.valueOf(right));
            }
            return switch (operator) {
                case ">" -> value > 0;
                case ">=" -> value >= 0;
                case "<" -> value < 0;
                case "<=" -> value <= 0;
                default -> throw new IllegalStateException("Unexpected operator: " + operator);
            };
        }

        private boolean equalsValue(Object left, Object right) {
            if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
                return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
            }
            return Objects.equals(left, right);
        }

        private boolean match(String typeOrValue) {
            if (peekType(typeOrValue) || typeOrValue.equals(peek().value)) {
                index++;
                return true;
            }
            return false;
        }

        private Token expect(String typeOrValue) {
            Token token = advance();
            if (!typeOrValue.equals(token.type) && !typeOrValue.equals(token.value)) {
                throw new IllegalArgumentException("Expected " + typeOrValue + " but found " + token.value);
            }
            return token;
        }

        private Token advance() {
            return tokens.get(index++);
        }

        private Token peek() {
            return tokens.get(index);
        }

        private boolean peekType(String type) {
            return type.equals(peek().type);
        }
    }
}
