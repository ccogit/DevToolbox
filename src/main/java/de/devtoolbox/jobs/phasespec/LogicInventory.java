package de.devtoolbox.jobs.phasespec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The target list "Soll Logik": every method of the code file that decides, calculates or fails, with the number of
 * its branches and the values it names, plus the constants of the classes. It is read from the code file the AI
 * gets, so it lists exactly what the AI can see. With it the depth of a specification can be counted, the same way
 * the attribute lists make its breadth countable.
 * <p>
 * The Java is read with a light scanner (comments and strings removed, braces counted), not with a parser: a method
 * that is missed or miscounted costs a line in a checklist, nothing more.
 */
public final class LogicInventory {

    /** Values listed per method and characters shown per constant, so one line stays readable. */
    private static final int MAX_VALUES = 14;
    private static final int MAX_CONSTANT = 400;

    private static final Pattern BEGIN = Pattern.compile("(?m)^===== BEGIN FILE: (.+?) =====$");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern METHOD = Pattern.compile(
            "(\\w+)\\s*\\((?:[^()]|\\([^()]*\\))*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?$");
    private static final Set<String> NO_METHOD = Set.of("if", "for", "while", "switch", "catch", "synchronized",
            "try", "do", "else", "return", "new", "throw");
    private static final Pattern BRANCH = Pattern.compile(
            "\\bif\\s*\\(|\\bcase\\b|\\bdefault\\s*(?::|->)|\\s\\?\\s|\\.(?:filter|anyMatch|allMatch|noneMatch|orElse\\w*)\\s*\\(");
    private static final Pattern THROW = Pattern.compile("\\bthrow\\b");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.$§])\\d[\\d_]*(?:\\.\\d+)?[LlDdFf]?(?![\\w$§])");
    private static final Pattern CONSTANT_NAME = Pattern.compile("\\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\\b");
    private static final Pattern CONSTANT_FIELD = Pattern.compile("\\bstatic\\b[^=;]*?\\b(\\w+)\\s*=\\s*([^;]+)$");

    /** A method with logic. */
    public record Method(String type, String name, int branches, int errors, List<String> values) {
        public String key() {
            return type + "." + name;
        }

        /** What has to be described: every branch, every error and the method itself. */
        int weight() {
            return 1 + branches + errors;
        }
    }

    /** A constant of a class with its value as written in the code. */
    public record Constant(String type, String name, String value) {
    }

    private final int files;
    private final List<Method> methods;
    private final List<Constant> constants;
    private final int plainMethods;

    private LogicInventory(int files, List<Method> methods, List<Constant> constants, int plainMethods) {
        this.files = files;
        this.methods = methods;
        this.constants = constants;
        this.plainMethods = plainMethods;
    }

    /** Reads the code file written by the code collector ({@code ===== BEGIN FILE: … =====} blocks). */
    public static LogicInventory of(String codeFile) {
        return of(codeFile, List.of());
    }

    /**
     * As {@link #of(String)}, but only classes that mention one of the {@code terms} (the entity and its synonyms,
     * case-insensitive, in the path or the source) are counted: referenced helpers without the term – converters,
     * pool providers – are no logic of the entity. No terms = every class.
     */
    public static LogicInventory of(String codeFile, List<String> terms) {
        List<String> wanted = terms.stream().map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty()).toList();
        List<Method> methods = new ArrayList<>();
        List<Constant> constants = new ArrayList<>();
        int[] plain = {0};
        int files = 0;
        Matcher begin = BEGIN.matcher(codeFile);
        while (begin.find()) {
            String path = begin.group(1);
            int end = codeFile.indexOf("===== END FILE: " + path + " =====", begin.end());
            if (end < 0) {
                continue;
            }
            files++;
            if (path.endsWith(".java")) {
                String source = codeFile.substring(begin.end(), end);
                String haystack = (path + "\n" + source).toLowerCase(Locale.ROOT);
                if (wanted.isEmpty() || wanted.stream().anyMatch(haystack::contains)) {
                    scan(source, methods, constants, plain);
                }
            }
        }
        return new LogicInventory(files, List.copyOf(methods), List.copyOf(constants), plain[0]);
    }

    /** Number of source files in the code file. */
    public int files() {
        return files;
    }

    public List<Method> methods() {
        return methods;
    }

    public List<Constant> constants() {
        return constants;
    }

    public boolean isEmpty() {
        return methods.isEmpty();
    }

    /** "L1" … for the n-th method (1-based). */
    public static String number(int n) {
        return "L" + n;
    }

    /**
     * The methods in groups for one answer each, of about the same weight: as few groups as possible with at most
     * {@code maxWeight} branches and errors each (a single heavier method is a group of its own). Returns the
     * 1-based first and last number per group.
     */
    public List<int[]> stages(int maxWeight) {
        List<int[]> stages = new ArrayList<>();
        int total = methods.stream().mapToInt(Method::weight).sum();
        int groups = Math.max(1, (total + maxWeight - 1) / maxWeight);
        double target = (double) total / groups;
        int first = 1;
        int weight = 0;
        for (int i = 0; i < methods.size(); i++) {
            weight += methods.get(i).weight();
            boolean last = i == methods.size() - 1;
            if (last || weight >= target && stages.size() < groups - 1) {
                stages.add(new int[]{first, i + 1});
                first = i + 2;
                weight = 0;
            }
        }
        return stages;
    }

    /** The section "Soll Logik" of the task. */
    public String markdown() {
        int branches = methods.stream().mapToInt(Method::branches).sum();
        int errors = methods.stream().mapToInt(Method::errors).sum();
        StringBuilder md = new StringBuilder();
        md.append("### Soll Logik – ").append(methods.size()).append(" Methoden, ").append(branches)
                .append(" Verzweigungen, ").append(errors).append(" Fehlerfälle\n\n");
        md.append("Aus dem Code-Kontext ausgezählt: jede Methode, die verzweigt, rechnet, Werte nennt oder einen "
                + "Fehler wirft. „Verzweigungen“ zählt `if`, `case`, `default`, `? :` und Filter in Streams, "
                + "„Fehler“ zählt `throw`. Die Zahlen sind Mindestzahlen für die Regeln der Methode; die „Werte“ "
                + "müssen im Ergebnis wörtlich vorkommen.\n\n");
        md.append("| Nr | Klasse.Methode | Verzweigungen | Fehler | Werte im Code |\n|---|---|---|---|---|\n");
        for (int i = 0; i < methods.size(); i++) {
            Method m = methods.get(i);
            md.append("| ").append(number(i + 1)).append(" | `").append(m.key()).append("` | ").append(m.branches())
                    .append(" | ").append(m.errors()).append(" | ")
                    .append(m.values().isEmpty() ? "–" : cell(String.join(", ", m.values()))).append(" |\n");
        }
        if (plainMethods > 0) {
            md.append("\nWeitere ").append(plainMethods).append(" Methoden des Code-Kontexts verzweigen nicht "
                    + "(Übernahme, Delegation, Getter/Setter). Sie brauchen keinen eigenen Abschnitt; was sie tun, "
                    + "steht in der Zeile des Attributs.\n");
        }
        if (!constants.isEmpty()) {
            md.append("\n### Soll Konstanten – ").append(constants.size()).append("\n\n");
            md.append("Jede Konstante steht im Ergebnis dort, wo sie verwendet wird, mit ihrem Wert und ihrer "
                    + "fachlichen Bedeutung.\n\n| Nr | Klasse.Konstante | Wert im Code |\n|---|---|---|\n");
            for (int i = 0; i < constants.size(); i++) {
                Constant c = constants.get(i);
                md.append("| K").append(i + 1).append(" | `").append(c.type()).append('.').append(c.name())
                        .append("` | ").append(cell(c.value())).append(" |\n");
            }
        }
        return md.toString().strip();
    }

    private static String cell(String text) {
        return "`" + text.replace("|", "\\|").replace("`", "'") + "`";
    }

    // --- scanner ---

    /** One source file: finds types, methods (only directly in a type), static fields and enum values. */
    private static void scan(String source, List<Method> methods, List<Constant> constants, int[] plain) {
        List<String> strings = new ArrayList<>();
        String code = strip(source, strings);
        List<String> types = new ArrayList<>();
        List<Integer> typeDepths = new ArrayList<>();
        List<Boolean> enumValuesPending = new ArrayList<>();
        int depth = 0;
        int statement = 0; // start of the text since the last ';', '{' or '}'
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            boolean inType = !types.isEmpty() && depth == typeDepths.getLast() + 1;
            if (c == ';') {
                if (inType && enumValuesPending.getLast()) {
                    enumValues(code.substring(statement, i), types.getLast(), strings, constants);
                    enumValuesPending.set(enumValuesPending.size() - 1, false);
                } else if (inType) {
                    constant(code.substring(statement, i), types.getLast(), strings, constants);
                }
                statement = i + 1;
            } else if (c == '{') {
                String header = code.substring(statement, i).strip();
                Matcher type = TYPE.matcher(header);
                Matcher method = METHOD.matcher(header);
                if (type.find()) {
                    types.add(type.group(1));
                    typeDepths.add(depth);
                    enumValuesPending.add(type.group().startsWith("enum"));
                    depth++;
                    statement = i + 1;
                } else if (inType && method.find() && isMethod(header, method)) {
                    int close = closing(code, i);
                    method(types.getLast(), method.group(1), code.substring(i, close), strings, methods, plain);
                    i = close;
                    statement = i + 1;
                } else if (inType && header.contains("=")) {
                    i = closing(code, i); // array or anonymous class of a field: stays part of its statement
                } else {
                    depth++;
                    statement = i + 1;
                }
            } else if (c == '}') {
                if (inType && enumValuesPending.getLast()) {
                    enumValues(code.substring(statement, i), types.getLast(), strings, constants);
                }
                depth--;
                if (!typeDepths.isEmpty() && typeDepths.getLast() == depth) {
                    types.removeLast();
                    typeDepths.removeLast();
                    enumValuesPending.removeLast();
                }
                statement = i + 1;
            }
        }
    }

    /** The values of an enum (with their arguments, e.g. codes) as one constant. */
    private static void enumValues(String statement, String type, List<String> strings, List<Constant> constants) {
        String value = restore(statement.strip().replaceAll("\\s+", " "), strings);
        if (!value.isEmpty()) {
            constants.add(new Constant(type, "(Enum-Werte)", shorten(value)));
        }
    }

    private static String shorten(String value) {
        return value.length() > MAX_CONSTANT ? value.substring(0, MAX_CONSTANT) + " …" : value;
    }

    private static boolean isMethod(String header, Matcher method) {
        if (NO_METHOD.contains(method.group(1))) {
            return false;
        }
        String before = header.substring(0, method.start()).stripTrailing();
        return !before.endsWith("new") && !before.endsWith(".") && !before.endsWith("=") && !before.contains("->");
    }

    /** Index of the brace closing the one at {@code open}; the end of the text if it never closes. */
    private static int closing(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return code.length() - 1;
    }

    private static void method(String type, String name, String body, List<String> strings, List<Method> methods,
                               int[] plain) {
        int branches = count(BRANCH, body);
        int errors = count(THROW, body);
        Set<String> values = new LinkedHashSet<>();
        Matcher number = NUMBER.matcher(body);
        while (number.find()) {
            String n = number.group();
            if (!n.matches("[01][LlDdFf]?")) {
                values.add(n);
            }
        }
        boolean numbers = !values.isEmpty();
        Matcher string = STRING_REF.matcher(body);
        while (string.find()) {
            String s = strings.get(Integer.parseInt(string.group(1)));
            // codes and keys, not messages: a text with blanks is a log or error message
            if (!s.isBlank() && s.length() <= 40 && !s.contains(" ")) {
                values.add("\"" + s + "\"");
            }
        }
        Matcher constant = CONSTANT_NAME.matcher(body);
        while (constant.find()) {
            values.add(constant.group());
        }
        // names alone (bean names, column names) are no logic
        if (branches + errors == 0 && !numbers) {
            plain[0]++;
            return;
        }
        List<String> listed = new ArrayList<>(values);
        if (listed.size() > MAX_VALUES) {
            int more = listed.size() - MAX_VALUES;
            listed = new ArrayList<>(listed.subList(0, MAX_VALUES));
            listed.add("… " + more + " weitere");
        }
        methods.add(new Method(type, name, branches, errors, List.copyOf(listed)));
    }

    private static void constant(String statement, String type, List<String> strings, List<Constant> constants) {
        String text = statement.strip().replaceAll("\\s+", " ");
        if (!text.contains("final")) {
            return;
        }
        Matcher m = CONSTANT_FIELD.matcher(text);
        if (!m.find() || m.group(1).equals("serialVersionUID") || m.group(1).matches("(?i)log(ger)?")) {
            return;
        }
        constants.add(new Constant(type, m.group(1), shorten(restore(m.group(2).strip(), strings))));
    }

    private static int count(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** A removed string literal: {@code "§<index>§"}. */
    private static final Pattern STRING_REF = Pattern.compile("\"§(\\d+)§\"");

    private static String restore(String text, List<String> strings) {
        Matcher m = STRING_REF.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement("\"" + strings.get(Integer.parseInt(m.group(1))) + "\""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * The source without comments; string and char literals are replaced by {@code "§<index>§"} and collected in
     * {@code strings}, so braces and keywords inside them do not count.
     */
    private static String strip(String source, List<String> strings) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end;
                out.append("\"§").append(strings.size()).append("§\"");
                strings.add(source.substring(i + 3, end).strip());
                i = Math.min(n, end + 3);
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && source.charAt(end) != c && source.charAt(end) != '\n') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end, n);
                if (c == '"') {
                    out.append("\"§").append(strings.size()).append("§\"");
                    strings.add(source.substring(i + 1, end));
                } else {
                    out.append("' '");
                }
                i = Math.min(n, end + 1);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
