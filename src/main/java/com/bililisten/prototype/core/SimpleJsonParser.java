package com.bililisten.prototype.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJsonParser {
    private final String input;
    private int index;

    private SimpleJsonParser(String input) {
        this.input = input;
    }

    public static Object parse(String json) {
        SimpleJsonParser parser = new SimpleJsonParser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespace();
        if (isAtEnd()) {
            throw error("Unexpected end of input");
        }

        char current = input.charAt(index);
        return switch (current) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseLiteral("true", Boolean.TRUE);
            case 'f' -> parseLiteral("false", Boolean.FALSE);
            case 'n' -> parseLiteral("null", null);
            default -> {
                if (current == '-' || Character.isDigit(current)) {
                    yield parseNumber();
                }
                throw error("Unexpected character: " + current);
            }
        };
    }

    private Map<String, Object> parseObject() {
        expect('{');
        skipWhitespace();

        Map<String, Object> object = new LinkedHashMap<>();
        if (peek('}')) {
            index++;
            return object;
        }

        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            object.put(key, value);
            skipWhitespace();

            if (peek('}')) {
                index++;
                return object;
            }

            expect(',');
        }
    }

    private List<Object> parseArray() {
        expect('[');
        skipWhitespace();

        List<Object> array = new ArrayList<>();
        if (peek(']')) {
            index++;
            return array;
        }

        while (true) {
            array.add(parseValue());
            skipWhitespace();

            if (peek(']')) {
                index++;
                return array;
            }

            expect(',');
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder builder = new StringBuilder();

        while (!isAtEnd()) {
            char current = input.charAt(index++);
            if (current == '"') {
                return builder.toString();
            }

            if (current != '\\') {
                builder.append(current);
                continue;
            }

            if (isAtEnd()) {
                throw error("Unexpected end of input inside string escape");
            }

            char escaped = input.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> builder.append(parseUnicodeEscape());
                default -> throw error("Unsupported escape sequence: \\" + escaped);
            }
        }

        throw error("Unterminated string");
    }

    private char parseUnicodeEscape() {
        if (index + 4 > input.length()) {
            throw error("Incomplete unicode escape");
        }

        int value = Integer.parseInt(input.substring(index, index + 4), 16);
        index += 4;
        return (char) value;
    }

    private Object parseNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }

        consumeDigits();
        boolean isFractional = false;

        if (peek('.')) {
            isFractional = true;
            index++;
            consumeDigits();
        }

        if (peek('e') || peek('E')) {
            isFractional = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            consumeDigits();
        }

        String literal = input.substring(start, index);
        return isFractional ? Double.parseDouble(literal) : Long.parseLong(literal);
    }

    private void consumeDigits() {
        if (isAtEnd() || !Character.isDigit(input.charAt(index))) {
            throw error("Expected digit");
        }
        while (!isAtEnd() && Character.isDigit(input.charAt(index))) {
            index++;
        }
    }

    private Object parseLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) {
            throw error("Expected literal: " + literal);
        }
        index += literal.length();
        return value;
    }

    private void expect(char expected) {
        skipWhitespace();
        if (isAtEnd() || input.charAt(index) != expected) {
            throw error("Expected '" + expected + "'");
        }
        index++;
    }

    private boolean peek(char expected) {
        return !isAtEnd() && input.charAt(index) == expected;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
    }

    private boolean isAtEnd() {
        return index >= input.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at index " + index);
    }
}

