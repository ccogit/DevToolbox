package de.devtoolbox.tools.standards;

import de.devtoolbox.tools.codecollector.Conventions;
import de.devtoolbox.tools.codecollector.SourceReader;
import de.devtoolbox.tools.codecollector.SourceTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, source-only inventory for migration conventions. It is not a full Java compiler;
 * comments and string literals are masked before inspecting declarations and references.
 * Unresolved or ambiguous links are never silently substituted by a same-named class.
 */
public final class JavaCatalog {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)(\\.\\*)?\\s*;");
    private static final Pattern TOKEN = Pattern.compile("\\b[A-Z][\\w$]*\\b");
    private static final Pattern ROLE = Pattern.compile("^(.*?)(ItemReader|Reader|RowMapper|Extractor|Anreicherer|Anreicherung|Leser|Loader"
            + "|ItemProcessor|Processor|Mapper|Mapping|Converter|Konverter|Transformer|Translator"
            + "|ItemWriter|Writer|Exporter|Persister|Repository|Repo|DAO|Dao|JobConfiguration|BatchConfiguration|Configuration|Config|Tasklet|Listener)$");
    private static final Pattern VARIANT_PREFIX = Pattern.compile("^(Bestand|Schwebe|Stock|Pending|Alt|Neu|Old|New)(?=[A-Z])");
    private static final Pattern VARIANT_SUFFIX = Pattern.compile("(Bestand|Schwebe|Stock|Pending|Alt|Neu|Old|New)$");
    private static final Pattern BUSINESS_VARIANT = Pattern.compile("(?i)bestand|schwebe|stock|pending");

    /** Variant of a class that belongs to neither stock nor suspense (nor another named variant). */
    public static final String COMMON = "Gemeinsam";

    /** The declared primary source type; a missing primary declaration is not guessed. */
    public record Type(Path path, String rel, String name, String pkg, String code, String lexical,
                       String declaration, boolean isAbstract, boolean isInterface, List<String> imports) {
        public String fqn() {
            return pkg.isEmpty() ? name : pkg + "." + name;
        }
        public boolean shared() {
            String relLower = rel.toLowerCase(Locale.ROOT);
            return isAbstract || isInterface || name.startsWith("Abstract") || name.endsWith("Base")
                    || relLower.matches(".*(?:/|^)(?:common|shared|basis|base|util|utils|infrastructure)(?:/).*");
        }
        public boolean uses(String simpleName) {
            return Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b").matcher(lexical).find();
        }
    }

    /** Evidence used to display a proposed class, including its stock/suspense variant. */
    public record Recognition(String entity, String variant, String reason) { }

    private final List<Type> files;
    private final Map<String, List<Type>> byFqn;
    private final Map<String, List<Type>> bySimple;

    private JavaCatalog(List<Type> files) {
        this.files = List.copyOf(files);
        Map<String, List<Type>> fqn = new LinkedHashMap<>();
        Map<String, List<Type>> simple = new LinkedHashMap<>();
        for (Type t : files) {
            fqn.computeIfAbsent(t.fqn(), k -> new ArrayList<>()).add(t);
            simple.computeIfAbsent(t.name(), k -> new ArrayList<>()).add(t);
        }
        this.byFqn = fqn;
        this.bySimple = simple;
    }

    public static JavaCatalog scan(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Quellprojekt nicht gefunden: " + root);
        }
        List<Type> result = new ArrayList<>();
        for (Path file : SourceTree.files(root, (p, a) -> p.getFileName().toString().endsWith(".java")
                && a.isRegularFile() && a.size() <= 2_000_000)) {
            String code = SourceReader.read(file);
            if (code == null) {
                continue;
            }
            String lexical = mask(code);
            Matcher pkg = PACKAGE.matcher(lexical);
            String packageName = pkg.find() ? pkg.group(1) : "";
            String name = file.getFileName().toString().replaceFirst("\\.java$", "");
            Pattern declarationPattern = Pattern.compile("\\b(class|interface|record|enum)\\s+"
                    + Pattern.quote(name) + "\\b([^\\{;]*)", Pattern.DOTALL);
            Matcher m = declarationPattern.matcher(lexical);
            if (!m.find()) {
                continue;
            }
            String head = lexical.substring(Math.max(0, m.start() - 60), m.start());
            boolean abstractType = Pattern.compile("\\babstract\\s*$").matcher(head).find();
            boolean interfaceType = m.group(1).equals("interface");
            List<String> imports = new ArrayList<>();
            Matcher im = IMPORT.matcher(lexical);
            while (im.find()) {
                imports.add(im.group(1) + (im.group(2) == null ? "" : ".*"));
            }
            result.add(new Type(file, root.relativize(file).toString().replace('\\', '/'), name,
                    packageName, code, lexical, m.group(2), abstractType, interfaceType, List.copyOf(imports)));
        }
        result.sort(Comparator.comparing(Type::rel));
        return new JavaCatalog(result);
    }

    public List<Type> files() {
        return files;
    }

    public List<Type> duplicateTypes() {
        return byFqn.values().stream().filter(group -> group.size() > 1)
                .flatMap(List::stream).sorted(Comparator.comparing(Type::rel)).toList();
    }

    /** Resolve a referenced type in this order: explicit import, local package, wildcard, global unique. */
    public List<Type> resolve(Type from, String simpleName) {
        if (!bySimple.containsKey(simpleName)) {
            return List.of();
        }
        List<Type> result = new ArrayList<>();
        for (String imp : from.imports()) {
            if (imp.endsWith("." + simpleName)) {
                result.addAll(byFqn.getOrDefault(imp, List.of()));
            }
        }
        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        result.addAll(byFqn.getOrDefault(from.pkg().isEmpty() ? simpleName : from.pkg() + "." + simpleName, List.of()));
        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        for (String imp : from.imports()) {
            if (imp.endsWith(".*")) {
                result.addAll(byFqn.getOrDefault(imp.substring(0, imp.length() - 2) + "." + simpleName, List.of()));
            }
        }
        if (!result.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(result));
        }
        // No import or same-package declaration: guessing across packages would silently
        // pick the wrong class in a multi-module project.
        return List.of();
    }

    /** Project-local references, including generic type arguments; unresolved types are ignored. */
    public List<Type> references(Type from) {
        Set<Type> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(from.lexical());
        Set<String> visited = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (!visited.add(token) || token.equals(from.name())) {
                continue;
            }
            List<Type> found = resolve(from, token);
            if (found.size() == 1) {
                result.add(found.getFirst());
            }
        }
        return List.copyOf(result);
    }

    /** Class/role convention, without guessing entity names from arbitrary substring matches. */
    public static Recognition recognise(Type type) {
        String name = type.name();
        String stem;
        Conventions.DataStructure data = type.isInterface() ? null
                : Conventions.dataStructure(name, type.lexical());
        if (data != null) {
            stem = data.stem();
        } else {
            Matcher role = ROLE.matcher(name);
            if (!role.matches() || role.group(1).isEmpty()) {
                return null;
            }
            stem = role.group(1).replaceFirst("(?:PO|Po|TO|To|DTO|Dto|Entity)$", "");
        }
        String variant = COMMON;
        Matcher before = VARIANT_PREFIX.matcher(stem);
        if (before.find()) {
            variant = before.group(1);
            stem = stem.substring(before.end());
        }
        Matcher after = VARIANT_SUFFIX.matcher(stem);
        if (after.find()) {
            variant = after.group(1);
            stem = stem.substring(0, after.start());
        }
        // Also handle VertragMapperSchwebe / VertragWriterBestand.
        Matcher roleLast = ROLE.matcher(name);
        if (roleLast.matches() && stem.isEmpty()) {
            return null;
        }
        if (stem.isEmpty() || !Character.isUpperCase(stem.codePointAt(0))) {
            return null;
        }
        if (stem.equals("Abstract") || stem.equals("Base") || stem.equals("Generic")
                || stem.equals("Common") || stem.equals("Standard")) {
            return null;
        }
        return new Recognition(stem, variant,
                data == null ? "Logik " + name : "Datenstruktur " + data.kind());
    }

    /** Resolve a class named EntityRoleSchwebe when the role is before the variant. */
    public static Recognition roleBeforeVariant(Type type) {
        String name = type.name();
        Matcher suffix = VARIANT_SUFFIX.matcher(name);
        if (!suffix.find()) {
            return null;
        }
        String variant = suffix.group(1);
        String without = name.substring(0, suffix.start());
        Matcher role = ROLE.matcher(without);
        if (!role.matches() || role.group(1).isEmpty()) {
            return null;
        }
        return new Recognition(role.group(1), variant, "Spezialvariante " + name);
    }

    /** Variant evidence from class names, exact package segments, and explicit Spring qualifiers. */
    public static String variant(Type type) {
        Recognition recognition = recognise(type);
        if (recognition == null) recognition = roleBeforeVariant(type);
        if (recognition != null && !recognition.variant().equals(COMMON)) {
            return recognition.variant();
        }
        Matcher prefix = VARIANT_PREFIX.matcher(type.name());
        if (prefix.find() && BUSINESS_VARIANT.matcher(prefix.group(1)).matches()) {
            return prefix.group(1);
        }
        Matcher suffix = VARIANT_SUFFIX.matcher(type.name());
        if (suffix.find() && BUSINESS_VARIANT.matcher(suffix.group(1)).matches()) {
            return suffix.group(1);
        }
        for (String segment : (type.pkg() + "." + type.rel().replace('/', '.')).split("\\.")) {
            if (segment.equalsIgnoreCase("schwebe") || segment.equalsIgnoreCase("pending")) {
                return "Schwebe";
            }
            if (segment.equalsIgnoreCase("bestand") || segment.equalsIgnoreCase("stock")) {
                return "Bestand";
            }
        }
        Matcher qualifier = Pattern.compile(
                "(?i)@(?:Qualifier|Component|Profile)\\s*\\(\\s*\"(?:[^\"\\\\]|\\\\.)*?(bestand|schwebe)(?:[^\"\\\\]|\\\\.)*?\"")
                .matcher(type.code());
        if (!qualifier.find()) {
            return COMMON;
        }
        return qualifier.group(1).equalsIgnoreCase("bestand") ? "Bestand" : "Schwebe";
    }

    /** Whether the variant separates stock from suspense data (Bestand/Schwebe, Stock/Pending). */
    public static boolean isBusinessVariant(String variant) {
        return BUSINESS_VARIANT.matcher(variant).matches();
    }

    /** Candidates with at least one entity-specific artifact; stable ordering by coverage then name. */
    public Map<String, List<Type>> entities() {
        Map<String, List<Type>> groups = new LinkedHashMap<>();
        for (Type type : files) {
            if (type.isAbstract() || type.isInterface()) {
                continue;
            }
            Recognition rec = recognise(type);
            if (rec == null) {
                rec = roleBeforeVariant(type);
            }
            if (rec != null) {
                groups.computeIfAbsent(rec.entity().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(type);
            }
        }
        return groups;
    }

    /**
     * Replace comments and string/char contents by spaces, preserving newline positions.
     * This prevents false type tokens caused by comments or SQL strings.
     */
    public static String mask(String code) {
        StringBuilder result = new StringBuilder(code.length());
        int state = 0; // 0 code, 1 line comment, 2 block comment, 3 string, 4 character, 5 text block
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            if (state == 0) {
                if (c == '/' && next == '/') { result.append("  "); i++; state = 1; }
                else if (c == '/' && next == '*') { result.append("  "); i++; state = 2; }
                else if (c == '"' && i + 2 < code.length() && code.charAt(i + 1) == '"'
                        && code.charAt(i + 2) == '"') { result.append("   "); i += 2; state = 5; }
                else if (c == '"') { result.append(' '); state = 3; }
                else if (c == '\'') { result.append(' '); state = 4; }
                else result.append(c);
            } else if (state == 1) {
                result.append(c == '\n' ? '\n' : ' ');
                if (c == '\n') state = 0;
            } else if (state == 2) {
                if (c == '*' && next == '/') { result.append("  "); i++; state = 0; }
                else result.append(c == '\n' ? '\n' : ' ');
            } else if (state == 5 && c == '"' && i + 2 < code.length()
                    && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                result.append("   "); i += 2; state = 0;
            } else if ((state == 3 || state == 4) && c == '\\' && i + 1 < code.length()) {
                result.append(' ').append(code.charAt(++i) == '\n' ? '\n' : ' ');
            } else if ((state == 3 && c == '"') || (state == 4 && c == '\'')) {
                result.append(' '); state = 0;
            } else result.append(c == '\n' ? '\n' : ' ');
        }
        return result.toString();
    }
}
