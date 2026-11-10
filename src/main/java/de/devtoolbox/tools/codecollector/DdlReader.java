package de.devtoolbox.tools.codecollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads table structures from DDL files (DB2 for z/OS dialect, works for most others):
 * CREATE TABLE, ALTER TABLE … ADD PRIMARY/FOREIGN KEY, CREATE UNIQUE INDEX, COMMENT ON and LABEL ON.
 * Keys are optional: legacy DB2 schemas often declare no foreign keys and express uniqueness by a unique index.
 * Tolerant by design – statements it does not understand are skipped.
 */
public final class DdlReader {

    public record Column(String name, String type, boolean nullable, String defaultValue, String label, String comment) {
    }

    public record Table(String qualifiedName, String name, List<Column> columns, List<String> primaryKey,
                 List<String> foreignKeys, List<List<String>> uniqueIndexes, String label, String comment,
                 Path source) {
    }

    private static final Pattern UNIQUE_INDEX =
            Pattern.compile("(?is)^CREATE\\s+UNIQUE\\s+(?:WHERE\\s+NOT\\s+NULL\\s+)?INDEX\\s+[\\w$#@.\"]+\\s+ON\\s+([\\w$#@.\"]+)\\s*\\(([^)]*)\\)");
    private static final Pattern CREATE_TABLE =
            Pattern.compile("(?is)^CREATE\\s+(?:GLOBAL\\s+TEMPORARY\\s+)?TABLE\\s+([\\w$#@.\"]+)\\s*\\(");
    private static final Pattern ALTER_ADD =
            Pattern.compile("(?is)^ALTER\\s+TABLE\\s+([\\w$#@.\"]+)\\s+ADD\\s+(.*)$");
    private static final Pattern REMARK_SINGLE =
            Pattern.compile("(?is)^(COMMENT|LABEL)\\s+ON\\s+(TABLE|COLUMN)\\s+([\\w$#@.\"]+)\\s+IS\\s+'((?:[^']|'')*)'");
    private static final Pattern REMARK_MULTI =
            Pattern.compile("(?is)^(COMMENT|LABEL)\\s+ON\\s+([\\w$#@.\"]+)\\s*\\((.*)\\)$");
    private static final Pattern REMARK_PAIR = Pattern.compile("(?is)([\\w$#@\"]+)\\s+IS\\s+'((?:[^']|'')*)'");
    private static final Pattern KEY_COLUMNS = Pattern.compile("(?is)\\(([^)]*)\\)");
    private static final Pattern TYPE_END = Pattern.compile(
            "(?i)\\s+(?:NOT\\s+NULL|NULL|WITH\\s+DEFAULT|DEFAULT|PRIMARY\\s+KEY|UNIQUE|REFERENCES|CONSTRAINT|CHECK|GENERATED)\\b");
    private static final Pattern DEFAULT_VALUE = Pattern.compile("(?i)\\bDEFAULT\\s+('(?:[^']|'')*'|[^\\s,]+)");

    /** Unique indexes seen before their table, per {@link #read} call. */
    private static final ThreadLocal<Map<String, List<List<String>>>> PENDING = new ThreadLocal<>();

    private DdlReader() {
    }

    /** @param location a DDL file or a folder searched recursively for *.ddl and *.sql */
    public static List<Table> read(Path location) throws IOException {
        List<Path> files;
        if (Files.isDirectory(location)) {
            try (Stream<Path> walk = Files.walk(location)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).matches(".*\\.(ddl|sql)$"))
                        .sorted().toList();
            }
        } else {
            files = List.of(location);
        }
        Map<String, Builder> tables = new LinkedHashMap<>();
        PENDING.set(new LinkedHashMap<>());
        for (Path file : files) {
            String text = SourceReader.read(file);
            if (text != null) {
                for (String statement : statements(text)) {
                    parse(statement, file, tables);
                }
            }
        }
        PENDING.get().forEach((name, indexes) -> {
            Builder table = tables.get(name);
            if (table != null) {
                table.uniqueIndexes.addAll(indexes);
            }
        });
        PENDING.remove();
        return tables.values().stream().map(Builder::build).toList();
    }

    private static void parse(String statement, Path file, Map<String, Builder> tables) {
        Matcher create = CREATE_TABLE.matcher(statement);
        if (create.find()) {
            Builder table = tables.computeIfAbsent(key(create.group(1)), k -> new Builder(clean(create.group(1)), file));
            String body = parenthesised(statement, create.end() - 1);
            for (String element : splitTopLevel(body)) {
                element(table, element.strip());
            }
            return;
        }
        Matcher unique = UNIQUE_INDEX.matcher(statement);
        if (unique.find()) {
            List<String> columns = names(unique.group(2).replaceAll("(?i)\\s+(?:ASC|DESC|RANDOM)\\b", ""));
            Builder table = tables.get(key(unique.group(1)));
            if (table != null) {
                table.uniqueIndexes.add(columns);
            } else {
                PENDING.get().computeIfAbsent(key(unique.group(1)), k -> new ArrayList<>()).add(columns);
            }
            return;
        }
        Matcher alter = ALTER_ADD.matcher(statement);
        if (alter.find()) {
            Builder table = tables.get(key(alter.group(1)));
            if (table != null) {
                element(table, alter.group(2).strip());
            }
            return;
        }
        Matcher single = REMARK_SINGLE.matcher(statement);
        if (single.find()) {
            boolean label = single.group(1).equalsIgnoreCase("LABEL");
            String text = single.group(4).replace("''", "'");
            String target = clean(single.group(3));
            if (single.group(2).equalsIgnoreCase("TABLE")) {
                Builder table = tables.get(key(target));
                if (table != null) {
                    table.remark(null, label, text);
                }
            } else {
                int dot = target.lastIndexOf('.');
                Builder table = dot < 0 ? null : tables.get(key(target.substring(0, dot)));
                if (table != null) {
                    table.remark(target.substring(dot + 1), label, text);
                }
            }
            return;
        }
        Matcher multi = REMARK_MULTI.matcher(statement);
        if (multi.find()) {
            Builder table = tables.get(key(multi.group(2)));
            if (table != null) {
                boolean label = multi.group(1).equalsIgnoreCase("LABEL");
                Matcher pair = REMARK_PAIR.matcher(multi.group(3));
                while (pair.find()) {
                    table.remark(clean(pair.group(1)), label, pair.group(2).replace("''", "'"));
                }
            }
        }
    }

    /** One element of a CREATE TABLE body: a column or a table constraint. */
    private static void element(Builder table, String element) {
        String upper = element.toUpperCase(Locale.ROOT).replaceFirst("^CONSTRAINT\\s+\\S+\\s+", "");
        if (upper.startsWith("PRIMARY KEY")) {
            Matcher cols = KEY_COLUMNS.matcher(upper);
            if (cols.find()) {
                table.primaryKey.addAll(names(cols.group(1)));
            }
        } else if (upper.startsWith("FOREIGN KEY")) {
            table.foreignKeys.add(element.replaceFirst("(?is)^CONSTRAINT\\s+\\S+\\s+", "")
                    .replaceFirst("(?i)^FOREIGN\\s+KEY\\s*", "").replaceAll("\\s+", " ").strip());
        } else if (!upper.startsWith("UNIQUE") && !upper.startsWith("CHECK") && !upper.isEmpty()) {
            column(table, element);
        }
    }

    private static void column(Builder table, String definition) {
        String[] parts = definition.split("\\s+", 2);
        if (parts.length < 2) {
            return;
        }
        String rest = parts[1];
        Matcher end = TYPE_END.matcher(rest);
        String type = (end.find() ? rest.substring(0, end.start()) : rest).replaceAll("\\s+", " ").strip();
        String upper = rest.toUpperCase(Locale.ROOT);
        boolean nullable = !upper.contains("NOT NULL") && !upper.contains("PRIMARY KEY");
        String defaultValue = "";
        Matcher def = DEFAULT_VALUE.matcher(rest);
        if (def.find()) {
            defaultValue = def.group(1);
        } else if (upper.contains("WITH DEFAULT")) {
            defaultValue = "(Standardwert des Typs)";
        }
        String name = clean(parts[0]);
        if (upper.contains("PRIMARY KEY")) {
            table.primaryKey.add(name);
        }
        table.columns.put(name, new String[]{type, String.valueOf(nullable), defaultValue, "", ""});
    }

    /** Splits the script into statements at ';' outside string literals, after removing -- comments. */
    static List<String> statements(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (String line : text.split("\n")) {
            String l = quoted ? line : stripLineComment(line);
            for (int i = 0; i < l.length(); i++) {
                char c = l.charAt(i);
                if (c == '\'') {
                    quoted = !quoted;
                }
                if (c == ';' && !quoted) {
                    add(result, current);
                } else {
                    current.append(c);
                }
            }
            current.append('\n');
        }
        add(result, current);
        return result;
    }

    private static String stripLineComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '-' && line.charAt(i + 1) == '-') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static void add(List<String> result, StringBuilder current) {
        String statement = current.toString().strip();
        if (!statement.isEmpty()) {
            result.add(statement);
        }
        current.setLength(0);
    }

    /** Content between the '(' at {@code open} and its matching ')'. */
    private static String parenthesised(String s, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')' && --depth == 0) {
                return s.substring(open + 1, i);
            }
        }
        return s.substring(open + 1);
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
            } else if (!quoted && c == ',' && depth == 0) {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private static List<String> names(String list) {
        return java.util.Arrays.stream(list.split(",")).map(DdlReader::clean).filter(s -> !s.isEmpty()).toList();
    }

    private static String clean(String identifier) {
        return identifier.replace("\"", "").strip().toUpperCase(Locale.ROOT);
    }

    /** Tables are matched by their unqualified name, so COMMENT ON without schema still finds them. */
    private static String key(String identifier) {
        String name = clean(identifier);
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static final class Builder {
        final String qualifiedName;
        final Path source;
        /** column → {type, nullable, default, label, comment} */
        final Map<String, String[]> columns = new LinkedHashMap<>();
        final List<String> primaryKey = new ArrayList<>();
        final List<String> foreignKeys = new ArrayList<>();
        final List<List<String>> uniqueIndexes = new ArrayList<>();
        String label = "";
        String comment = "";

        Builder(String qualifiedName, Path source) {
            this.qualifiedName = qualifiedName;
            this.source = source;
        }

        void remark(String column, boolean isLabel, String text) {
            if (column == null) {
                if (isLabel) {
                    label = text;
                } else {
                    comment = text;
                }
                return;
            }
            String[] col = columns.get(column);
            if (col != null) {
                col[isLabel ? 3 : 4] = text;
            }
        }

        Table build() {
            List<Column> cols = columns.entrySet().stream().map(e -> new Column(e.getKey(), e.getValue()[0],
                    Boolean.parseBoolean(e.getValue()[1]), e.getValue()[2], e.getValue()[3], e.getValue()[4])).toList();
            String name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
            return new Table(qualifiedName, name, cols, List.copyOf(primaryKey), List.copyOf(foreignKeys),
                    List.copyOf(uniqueIndexes),
                    label, comment, source);
        }
    }
}
