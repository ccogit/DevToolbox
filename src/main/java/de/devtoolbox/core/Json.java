package de.devtoolbox.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal JSON for flat configuration files: one object with string, number, boolean or array-of-scalar values.
 * Arrays are read as comma-separated text, so they fit {@link Config#list(String)}.
 */
public final class Json {

    private Json() {
    }

    public static Map<String, String> parse(String json) {
        Parser parser = new Parser(json);
        Map<String, String> result = parser.object();
        parser.ws();
        if (parser.i < parser.s.length()) {
            throw parser.error("Unerwarteter Inhalt nach dem Objekt");
        }
        return result;
    }

    /** Values may be String, Boolean, Number or List of those. */
    public static String write(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(e -> "  " + quote(e.getKey()) + ": " + value(e.getValue()))
                .collect(Collectors.joining(",\n", "{\n", "\n}\n"));
    }

    private static String value(Object value) {
        return switch (value) {
            case Boolean b -> b.toString();
            case Number n -> n.toString();
            case List<?> list -> list.stream().map(Json::value).collect(Collectors.joining(", ", "[", "]"));
            default -> quote(String.valueOf(value));
        };
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append("\\u%04x".formatted((int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s.startsWith("\uFEFF") ? s.substring(1) : s;
        }

        Map<String, String> object() {
            ws();
            expect('{');
            Map<String, String> map = new LinkedHashMap<>();
            ws();
            if (peek() == '}') {
                i++;
                return map;
            }
            do {
                ws();
                String key = string();
                ws();
                expect(':');
                ws();
                map.put(key, value());
                ws();
            } while (consume(','));
            expect('}');
            return map;
        }

        private String value() {
            char c = peek();
            if (c == '"') {
                return string();
            }
            if (c == '[') {
                i++;
                List<String> items = new ArrayList<>();
                ws();
                if (peek() != ']') {
                    do {
                        ws();
                        items.add(value());
                        ws();
                    } while (consume(','));
                }
                expect(']');
                return String.join(", ", items);
            }
            if (c == '{') {
                throw error("Verschachtelte Objekte werden nicht unterstützt");
            }
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String literal = s.substring(start, i);
            if (literal.isEmpty()) {
                throw error("Wert erwartet");
            }
            return literal.equals("null") ? "" : literal;
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw error("String nicht abgeschlossen");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'u' -> {
                        b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> b.append(e);
                }
            }
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private char peek() {
            if (i >= s.length()) {
                throw error("Unerwartetes Dateiende");
            }
            return s.charAt(i);
        }

        private boolean consume(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!consume(c)) {
                throw error("'" + c + "' erwartet");
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Ungültiges JSON (Zeichen " + i + "): " + message);
        }
    }
}
