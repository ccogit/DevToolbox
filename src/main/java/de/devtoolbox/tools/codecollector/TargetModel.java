package de.devtoolbox.tools.codecollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The target model: JSON files in the project's target model folder. An entity is an object with a name key
 * ({@code entityTypeName}, {@code entitaet}, {@code entityName}; any case, any nesting level – one file may hold
 * several entities). Files without such a key are one entity named like the file. Attributes are the values of
 * {@code attributeName} / {@code attributname} below the entity; the other simple values of an attribute object
 * (e.g. {@code mapping}, {@code schwebeMapping}, {@code specification}, {@code status}) are kept as its details.
 * Confluence status macros like {@code {status:colour=Red|title=TBD}} are reduced to their title.
 */
public final class TargetModel {

    /**
     * {@code file} is null if nothing matches the entity. {@code entity} is the matched entity name,
     * {@code details} maps an attribute to its other values ("mapping: TBD · status: TBD"), empty if there are none.
     */
    public record Result(Path file, String entity, List<String> attributes, Map<String, String> details,
                         List<String> otherCandidates) {

        public static Result none() {
            return new Result(null, null, List.of(), Map.of(), List.of());
        }

        public boolean hasDetails() {
            return details.values().stream().anyMatch(d -> !d.isEmpty());
        }
    }

    /** One entity of the target model. {@code name} is null if the file names none (then the file name counts). */
    record Entity(Path file, String name, List<String> attributes, Map<String, String> details) {

        String displayName() {
            return name != null ? name : file.getFileName().toString().replaceFirst("(?i)\\.json$", "");
        }
    }

    private static final Pattern ENTITY_KEY = Pattern.compile("(?i)entity_?type_?name|entit(?:ae|ä)t(?:s?_?name)?|entity_?name");
    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("(?i)attribute?_?name");
    private static final Pattern ATTRIBUTE_NAME =
            Pattern.compile("\"(?i:attribute?_?name)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern STATUS_MACRO = Pattern.compile("\\{status:[^}]*?\\btitle=([^|}]*)[^}]*}");

    private TargetModel() {
    }

    /**
     * The phase Process needs the target model: fails with a message for the user if {@code folder} is not given or
     * is no existing folder.
     */
    public static void requireFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException(folder == null
                    ? "Für die Phase Process ist das Zielmodell (Ordner mit den JSON-Dateien der Entitäten) nötig."
                    : "Zielmodell-Ordner nicht gefunden: " + folder);
        }
    }

    /** All JSON files of the folder. */
    public static List<Path> files(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(folder)) {
            return list.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted().toList();
        }
    }

    /**
     * All entities of the folder, in file order. If any file names its entity, files without a name are other
     * documents (e.g. properties) and are left out.
     */
    static List<Entity> entities(Path folder) throws IOException {
        List<Entity> result = new ArrayList<>();
        for (Path file : files(folder)) {
            result.addAll(entities(file, Files.readString(file)));
        }
        if (result.stream().anyMatch(e -> e.name() != null)) {
            result.removeIf(e -> e.name() == null);
        }
        return result;
    }

    /** Names of the entities in the folder, sorted, without duplicates (empty if the folder is missing). */
    public static List<String> entityNames(Path folder) throws IOException {
        return entities(folder).stream().map(Entity::displayName).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Number of entities in the folder. */
    public static int entityCount(Path folder) throws IOException {
        return entities(folder).size();
    }

    /**
     * The entity whose name (or, without a name, whose file name) is the entity or one of its search terms –
     * exact match first, then "contains" – with its attributes in file order, without duplicates.
     */
    public static Result find(Path folder, List<String> terms) throws IOException {
        List<Entity> exact = new ArrayList<>();
        List<Entity> partial = new ArrayList<>();
        for (Entity e : entities(folder)) {
            String name = e.displayName().toLowerCase(Locale.ROOT);
            String fileName = e.file().getFileName().toString().replaceFirst("(?i)\\.json$", "").toLowerCase(Locale.ROOT);
            if (terms.stream().map(t -> t.toLowerCase(Locale.ROOT)).anyMatch(name::equals)) {
                exact.add(e);
            } else if (terms.stream().map(t -> t.toLowerCase(Locale.ROOT))
                    .anyMatch(t -> name.contains(t) || fileName.equals(t) || fileName.contains(t))) {
                partial.add(e);
            }
        }
        List<Entity> candidates = new ArrayList<>(exact);
        candidates.addAll(partial);
        if (candidates.isEmpty()) {
            return Result.none();
        }
        Entity hit = candidates.getFirst();
        List<String> others = candidates.subList(1, candidates.size()).stream()
                .map(e -> e.name() == null ? e.file().getFileName().toString()
                        : e.name() + " (" + e.file().getFileName() + ")")
                .toList();
        return new Result(hit.file(), hit.displayName(), hit.attributes(), hit.details(), others);
    }

    /** The entities of one file; a file that is no valid JSON is read tolerantly as one unnamed entity. */
    static List<Entity> entities(Path file, String json) {
        Object root;
        try {
            root = new Json(json).parse();
        } catch (IllegalArgumentException e) {
            return List.of(new Entity(file, null, attributes(json), Map.of()));
        }
        List<Map<String, Object>> named = new ArrayList<>();
        collectEntities(root, named);
        if (named.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            collectAttributes(root, details);
            return List.of(new Entity(file, null, List.copyOf(details.keySet()), details));
        }
        List<Entity> result = new ArrayList<>();
        for (Map<String, Object> object : named) {
            Map<String, String> details = new LinkedHashMap<>();
            collectAttributes(object, details);
            result.add(new Entity(file, entityName(object), List.copyOf(details.keySet()), details));
        }
        return result;
    }

    /** Attribute names found by pattern – fallback for files that are no valid JSON. */
    static List<String> attributes(String json) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = ATTRIBUTE_NAME.matcher(json);
        while (m.find()) {
            String name = m.group(1).replace("\\\"", "\"").strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static String entityName(Map<String, Object> object) {
        return object.entrySet().stream()
                .filter(e -> ENTITY_KEY.matcher(e.getKey()).matches() && e.getValue() instanceof String s && !s.isBlank())
                .map(e -> ((String) e.getValue()).strip()).findFirst().orElse(null);
    }

    /** Objects that carry an entity name; entities are not searched for nested entities. */
    @SuppressWarnings("unchecked")
    private static void collectEntities(Object node, List<Map<String, Object>> result) {
        if (node instanceof Map<?, ?> map) {
            if (entityName((Map<String, Object>) map) != null) {
                result.add((Map<String, Object>) map);
                return;
            }
            map.values().forEach(v -> collectEntities(v, result));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collectEntities(v, result));
        }
    }

    /** attribute name → details, for every object below {@code node} with an attribute name key. */
    private static void collectAttributes(Object node, Map<String, String> result) {
        if (node instanceof Map<?, ?> map) {
            String name = null;
            List<String> details = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = (String) e.getKey();
                if (name == null && ATTRIBUTE_KEY.matcher(key).matches() && e.getValue() instanceof String s) {
                    name = s.strip();
                } else {
                    String value = simple(e.getValue());
                    if (value != null && !value.isEmpty()) {
                        details.add(key + ": " + value);
                    }
                }
            }
            if (name != null && !name.isEmpty()) {
                String d = String.join(" · ", details);
                result.merge(name, d, (a, b) -> a.isEmpty() ? b : a);
            }
            map.values().forEach(v -> collectAttributes(v, result));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collectAttributes(v, result));
        }
    }

    /** A scalar or a list of scalars as text; null for objects and nested lists. */
    private static String simple(Object value) {
        if (value == null || value instanceof Map) {
            return null;
        }
        if (value instanceof List<?> list) {
            if (list.stream().anyMatch(v -> v instanceof Map || v instanceof List)) {
                return null;
            }
            return list.stream().map(TargetModel::simple).filter(s -> s != null && !s.isEmpty())
                    .collect(Collectors.joining(", "));
        }
        return readable(String.valueOf(value));
    }

    /** "{status:colour=Red|title=TBD|subtle=true}" → "TBD". */
    static String readable(String value) {
        return STATUS_MACRO.matcher(value).replaceAll(m -> Matcher.quoteReplacement(m.group(1).strip()))
                .replaceAll("\\s+", " ").strip();
    }

    /** Minimal JSON parser: objects become insertion-ordered maps, arrays lists, numbers/booleans their text. */
    private static final class Json {
        private final String s;
        private int i;

        Json(String s) {
            this.s = s.startsWith("﻿") ? s.substring(1) : s;
        }

        Object parse() {
            Object value = value();
            ws();
            if (i != s.length()) {
                throw error();
            }
            return value;
        }

        private Object value() {
            ws();
            if (i >= s.length()) {
                throw error();
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> map = new LinkedHashMap<>();
                ws();
                if (peek('}')) {
                    return map;
                }
                do {
                    ws();
                    String key = string();
                    ws();
                    expect(':');
                    map.put(key, value());
                    ws();
                } while (peek(','));
                expect('}');
                return map;
            }
            if (c == '[') {
                i++;
                List<Object> list = new ArrayList<>();
                ws();
                if (peek(']')) {
                    return list;
                }
                do {
                    list.add(value());
                    ws();
                } while (peek(','));
                expect(']');
                return list;
            }
            if (c == '"') {
                return string();
            }
            int start = i;
            while (i < s.length() && "{}[],: \t\r\n".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String literal = s.substring(start, i);
            if (literal.isEmpty()) {
                throw error();
            }
            return literal.equals("null") ? null : literal;
        }

        private String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw error();
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw error();
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private boolean peek(char c) {
            if (i < s.length() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error();
            }
        }

        private IllegalArgumentException error() {
            return new IllegalArgumentException("Ungültiges JSON an Position " + i);
        }
    }
}
