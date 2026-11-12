package de.devtoolbox.tools.mappingdoc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prefills only direct properties of attribute objects belonging to the selected entity.
 * JSON formatting and all unrelated objects are kept byte-for-byte as text.
 */
final class MappingJsonPrefill {

    private static final Pattern ENTITY = Pattern.compile(
            "\"(?i:entity_?type_?name|entit(?:ae|ä)t(?:s?_?name)?|entity_?name)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\"(?i:attribute?_?name)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final String VALUE = "(?:\\[\\s*(?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*,?\\s*)*]|\"(?:[^\"\\\\]|\\\\.)*\"|null)";
    private record Span(int start, int end) { }
    private record Edit(int from, int to, String replacement) { }

    private MappingJsonPrefill() {
    }

    static String replace(String json, String entity, List<String> attributes, String key, String value) {
        List<Span> objects = objects(json);
        Span selected = null;
        boolean namesPresent = false;
        Matcher names = ENTITY.matcher(json);
        while (names.find()) {
            Span owner = owner(objects, names.start());
            if (owner == null || !direct(json, owner, names.start())) {
                continue;
            }
            namesPresent = true;
            if (names.group(1).equals(entity)) {
                if (selected != null) {
                    throw new IllegalArgumentException("Entität mehrfach im Zielmodell: " + entity);
                }
                selected = owner;
            }
        }
        if (namesPresent && selected == null) {
            throw new IllegalArgumentException("Entität nicht im Zielmodell gefunden: " + entity);
        }
        if (selected == null) {
            selected = new Span(0, json.length());
        }

        Set<Span> targets = new LinkedHashSet<>();
        Matcher attrs = ATTRIBUTE.matcher(json);
        while (attrs.find()) {
            Span owner = owner(objects, attrs.start());
            if (owner != null && owner.start() >= selected.start() && owner.end() <= selected.end()
                    && direct(json, owner, attrs.start()) && attributes.contains(attrs.group(1))) {
                targets.add(owner);
            }
        }

        String quoted = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        String newValue = "[ " + quoted + " ]";
        Pattern property = Pattern.compile("(\"" + Pattern.quote(key) + "\"\\s*:\\s*)" + VALUE);
        Pattern propertyName = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:");
        List<Edit> edits = new ArrayList<>();
        for (Span target : targets) {
            Matcher m = property.matcher(json);
            m.region(target.start(), target.end());
            boolean replaced = false;
            while (m.find()) {
                if (!direct(json, target, m.start())) {
                    continue;
                }
                String replacement = m.group().startsWith(m.group(1))
                        ? (m.group().substring(m.group(1).length()).startsWith("[") ? newValue : quoted) : quoted;
                edits.add(new Edit(m.start(1), m.end(), m.group(1) + replacement));
                replaced = true;
            }
            if (!replaced) {
                Matcher keys = propertyName.matcher(json);
                keys.region(target.start(), target.end());
                while (keys.find()) {
                    if (direct(json, target, keys.start())) {
                        throw new IllegalArgumentException("Ungültiges Feld „" + key + "“ in Zielmodell-JSON.");
                    }
                }
                int at = target.end() - 1;
                while (at > target.start() && Character.isWhitespace(json.charAt(at - 1))) {
                    at--;
                }
                boolean hasMember = json.charAt(at - 1) != '{';
                String newline = json.contains("\r\n") ? "\r\n" : "\n";
                String indent = "";
                int previousNewline = json.lastIndexOf('\n', at - 1);
                if (previousNewline >= target.start()) {
                    int i = previousNewline + 1;
                    while (i < at && (json.charAt(i) == ' ' || json.charAt(i) == '\t')) {
                        indent += json.charAt(i++);
                    }
                }
                String addition = (hasMember ? "," : "") + (previousNewline >= target.start()
                        ? newline + indent : " ") + "\"" + key + "\" : " + newValue;
                edits.add(new Edit(at, at, addition));
            }
        }
        edits.sort(Comparator.comparingInt(Edit::from).reversed());
        StringBuilder result = new StringBuilder(json);
        for (Edit edit : edits) {
            result.replace(edit.from(), edit.to(), edit.replacement());
        }
        return result.toString();
    }

    /** Find all JSON object spans, ignoring braces inside quoted strings. */
    private static List<Span> objects(String json) {
        List<Span> result = new ArrayList<>();
        ArrayDeque<Integer> starts = new ArrayDeque<>();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{') {
                starts.push(i);
            } else if (c == '}') {
                if (starts.isEmpty()) {
                    throw new IllegalArgumentException("Ungültige JSON-Klammerung.");
                }
                result.add(new Span(starts.pop(), i + 1));
            }
        }
        if (quoted || !starts.isEmpty()) {
            throw new IllegalArgumentException("Ungültige JSON-Klammerung.");
        }
        return result;
    }

    private static Span owner(List<Span> spans, int position) {
        Span nearest = null;
        for (Span span : spans) {
            if (span.start() <= position && position < span.end()
                    && (nearest == null || span.start() > nearest.start())) {
                nearest = span;
            }
        }
        return nearest;
    }

    /** True for a direct object member, not a property of a nested object or array. */
    private static boolean direct(String json, Span span, int position) {
        int curly = 0;
        int square = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = span.start(); i < position; i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == '{') {
                curly++;
            } else if (c == '}') {
                curly--;
            } else if (c == '[') {
                square++;
            } else if (c == ']') {
                square--;
            }
        }
        return !quoted && curly == 1 && square == 0;
    }
}
