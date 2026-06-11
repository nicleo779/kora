package org.byteora.kyra.orm.dynamic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class ExpressionEvaluator {
    private ExpressionEvaluator() {
    }

    static CompiledExpression compile(String expression) {
        Parser parser = new Parser(tokenize(expression));
        return parser.parseExpression();
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

    private static boolean equalsValue(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private static boolean compare(Object left, Object right, String operator) {
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

    private static Object additive(Object left, Object right) {
        if (left instanceof String || right instanceof String) {
            return String.valueOf(left) + String.valueOf(right);
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return leftNumber.doubleValue() + rightNumber.doubleValue();
        }
        return String.valueOf(left) + right;
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
        private int index;

        private Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private CompiledExpression parseExpression() {
            CompiledExpression value = parseOr();
            expect("eof");
            return value;
        }

        private CompiledExpression parseOr() {
            CompiledExpression left = parseAnd();
            while (match("or")) {
                CompiledExpression right = parseAnd();
                left = new OrExpr(left, right);
            }
            return left;
        }

        private CompiledExpression parseAnd() {
            CompiledExpression left = parseEquality();
            while (match("and")) {
                CompiledExpression right = parseEquality();
                left = new AndExpr(left, right);
            }
            return left;
        }

        private CompiledExpression parseEquality() {
            CompiledExpression left = parseComparison();
            while (peekType("op") && ("==".equals(peek().value) || "!=".equals(peek().value))) {
                String operator = advance().value;
                CompiledExpression right = parseComparison();
                left = new EqualityExpr(left, right, "!=".equals(operator));
            }
            return left;
        }

        private CompiledExpression parseComparison() {
            CompiledExpression left = parseAdditive();
            while (peekType("op") && (">".equals(peek().value) || ">=".equals(peek().value) || "<".equals(peek().value) || "<=".equals(peek().value))) {
                String operator = advance().value;
                CompiledExpression right = parseAdditive();
                left = new ComparisonExpr(left, right, operator);
            }
            return left;
        }

        private CompiledExpression parseAdditive() {
            CompiledExpression left = parseUnary();
            while (match("+")) {
                CompiledExpression right = parseUnary();
                left = new AdditiveExpr(left, right);
            }
            return left;
        }

        private CompiledExpression parseUnary() {
            if (match("!") || match("not")) {
                return new NotExpr(parseUnary());
            }
            return parsePrimary();
        }

        private CompiledExpression parsePrimary() {
            if (match("(")) {
                CompiledExpression value = parseOr();
                expect(")");
                return value;
            }
            Token token = advance();
            return switch (token.type) {
                case "string" -> new LiteralExpr(token.value);
                case "number" -> new LiteralExpr(token.value.contains(".")
                        ? Double.parseDouble(token.value)
                        : Long.parseLong(token.value));
                case "literal" -> switch (token.value) {
                    case "null" -> new LiteralExpr(null);
                    case "true" -> new LiteralExpr(Boolean.TRUE);
                    case "false" -> new LiteralExpr(Boolean.FALSE);
                    default -> throw new IllegalStateException("Unexpected literal: " + token.value);
                };
                case "identifier" -> {
                    if (match("(")) {
                        if (match(")")) {
                            yield new ZeroArgFunctionExpr(token.value);
                        }
                        CompiledExpression argument = parseOr();
                        expect(")");
                        yield new OneArgFunctionExpr(token.value, argument);
                    }
                    yield new IdentifierExpr(token.value);
                }
                default -> throw new IllegalArgumentException("Unexpected token '" + token.value + "' in expression");
            };
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

    private record LiteralExpr(Object value) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return value;
        }
    }

    private record IdentifierExpr(String name) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return context.resolveValue(name);
        }
    }

    private record ZeroArgFunctionExpr(String name) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return context.resolveValue(name + "()");
        }
    }

    private record OneArgFunctionExpr(String name, CompiledExpression argument) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return context.resolveFunction(name, argument.evaluate(context));
        }
    }

    private record NotExpr(CompiledExpression operand) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return !toBoolean(operand.evaluate(context));
        }
    }

    private record AndExpr(CompiledExpression left, CompiledExpression right) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return toBoolean(left.evaluate(context)) && toBoolean(right.evaluate(context));
        }
    }

    private record OrExpr(CompiledExpression left, CompiledExpression right) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return toBoolean(left.evaluate(context)) || toBoolean(right.evaluate(context));
        }
    }

    private record EqualityExpr(CompiledExpression left, CompiledExpression right, boolean negate) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            boolean result = equalsValue(left.evaluate(context), right.evaluate(context));
            return negate ? !result : result;
        }
    }

    private record ComparisonExpr(CompiledExpression left, CompiledExpression right, String operator) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return compare(left.evaluate(context), right.evaluate(context), operator);
        }
    }

    private record AdditiveExpr(CompiledExpression left, CompiledExpression right) implements CompiledExpression {
        @Override
        public Object evaluate(DynamicSqlContext context) {
            return additive(left.evaluate(context), right.evaluate(context));
        }
    }
}
