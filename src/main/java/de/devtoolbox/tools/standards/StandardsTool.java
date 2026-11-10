package de.devtoolbox.tools.standards;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Tool;
import de.devtoolbox.tools.codecollector.Conventions;
import de.devtoolbox.tools.codecollector.Conventions.DataKind;
import de.devtoolbox.tools.codecollector.Conventions.DataStructure;
import de.devtoolbox.tools.codecollector.PhaseClassifier;
import de.devtoolbox.tools.codecollector.SourceReader;
import de.devtoolbox.tools.codecollector.SourceTree;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standards of a migration project: structural templates derived once from an entity that is already implemented
 * (TOs, POs, entities, DTOs, repository and the logic of each phase), stored in the project as editable files and
 * applied by the implementation job.
 *
 * <pre>
 * standards/
 *   STANDARDS.md         index table: phase, artefact, template, class/package/path pattern, base type, notes
 *   vorlagen/*.java      templates; the example entity is replaced by {{Entitaet}} / {{entitaet}} / {{ENTITAET}}
 *   basis/*.java         base classes and interfaces used by the templates (as of generation)
 * </pre>
 */
public final class StandardsTool implements Tool {

    public static final String ID = "standards";
    public static final String SOURCE = "source";
    public static final String EXAMPLE = "example";

    public static final String CLASS = "{{Entitaet}}";
    public static final String LOWER = "{{entitaet}}";
    public static final String UPPER = "{{ENTITAET}}";
    public static final String INDEX = "STANDARDS.md";

    private static final Set<String> ALL_PHASES = Set.of("READ", "PROCESS", "WRITE");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern ANNOTATION = Pattern.compile("@(\\w+)(\\([^)]*\\))?");
    private static final Pattern EXAMPLE_LINE = Pattern.compile("(?m)^- Beispiel-Entität:\\s*\\**([^*\\n]+?)\\**\\s*$");

    /** Kinds of artefacts, in the order they are presented, with the batch phases they belong to. */
    enum Kind {
        TO("Transferobjekt (TO)", "READ"), PO("Persistenzobjekt (PO)", "READ"), ENTITY("Entity", "READ"),
        DTO("DTO", "READ"), REPOSITORY("Repository (Einlesen)", "READ"), LESEN("Einleselogik", "READ"),
        VERARBEITUNG("Verarbeitungslogik (Mapping)", "PROCESS"), SCHREIBEN("Schreiblogik", "WRITE"),
        DATENZUGRIFF("Datenzugriff (DAO)", "READ", "WRITE");

        final String title;
        final Set<String> phases;

        Kind(String title, String... phases) {
            this.title = title;
            this.phases = Set.of(phases);
        }

        /** The artefact kind of a data structure found by the {@link Conventions}. */
        static Kind of(DataKind kind) {
            return switch (kind) {
                case TO -> TO;
                case PO -> PO;
                case ENTITY -> ENTITY;
                case DTO -> DTO;
            };
        }

        /** e.g. "Read" or "Read/Write". */
        String phaseLabel() {
            return Stream.of("READ", "PROCESS", "WRITE").filter(phases::contains)
                    .map(p -> p.charAt(0) + p.substring(1).toLowerCase(Locale.ROOT)).collect(Collectors.joining("/"));
        }
    }

    record JavaFile(Path path, String rel, String name, String pkg, String content) {
    }

    /** An entity found in the code and what it offers as templates, e.g. "Vertrag" with 8 artefacts. */
    public record Candidate(String name, String kinds, int artefacts) {
        public String label() {
            return name + " – " + artefacts + " Artefakte (" + kinds + ")";
        }
    }

    public record Summary(String example, int templates, int baseTypes, Path dir, Path backup) {
    }

    /** One row of STANDARDS.md. Patterns contain the placeholders. */
    public record Row(String phase, String kind, String template, String className, String pkg, String path,
                      String supers, String annotations, String notes) {

        /** Path of the new file relative to the project root; derived from the package for older standards. */
        public String pathOrDefault() {
            return !path.isBlank() ? path
                    : "src/main/java/" + (pkg.isBlank() ? "" : pkg.replace('.', '/') + "/") + className + ".java";
        }
    }

    /** The stored standards as read from STANDARDS.md (incl. the user's edits). */
    public record Stored(String example, Path dir, List<Row> rows, List<Path> baseFiles) {
    }

    // ---- Tool (generation without the UI) ----

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Standards erzeugen (Vorlagen aus umgesetzten Entitäten)";
    }

    @Override
    public List<Param> params() {
        return List.of(
                Param.directory(SOURCE, "Quelle (Code)", true, "Code, in dem bereits umgesetzte Entitäten gesucht werden"),
                Param.text(EXAMPLE, "Beispiel-Entität", "", false, "Leer = die vollständigste Entität"),
                Param.directory(Keys.OUTPUT, "Standards-Ordner", true, "z. B. migrationsprojekte/<id>/standards"));
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Summary s = generate(config.path(SOURCE), config.text(EXAMPLE), config.path(Keys.OUTPUT), "", log);
        return List.of(s.dir().resolve(INDEX));
    }

    // ---- detection ----

    /** Entities with templates in {@code root}, most complete first. */
    public static List<Candidate> candidates(Path root) throws IOException {
        return group(javaFiles(root)).values().stream()
                .sorted(Comparator.<Group>comparingInt(g -> g.kinds(ALL_PHASES)).reversed()
                        .thenComparing(Comparator.comparingInt(Group::count).reversed()).thenComparing(g -> g.name))
                .map(g -> new Candidate(g.name, g.artefacts.keySet().stream().map(k -> k.title)
                        .collect(Collectors.joining(", ")), g.count()))
                .toList();
    }

    /**
     * Derives the standards from the example entity in {@code root} and writes them to {@code dir}. Existing
     * standards (possibly edited) are moved to a sibling backup folder first.
     *
     * @param example name of the example entity; blank = the most complete one
     * @param title   shown in STANDARDS.md, e.g. the project label
     */
    public static Summary generate(Path root, String example, Path dir, String title, Consumer<String> log)
            throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Quelle existiert nicht: " + root);
        }
        List<JavaFile> files = javaFiles(root);
        Map<String, JavaFile> bySimpleName = new HashMap<>();
        files.forEach(f -> bySimpleName.putIfAbsent(f.name(), f));
        Map<String, Group> groups = group(files);
        Group chosen = choose(groups, example == null ? "" : example);
        if (chosen == null) {
            throw new IllegalArgumentException("In " + root + " wurden keine umgesetzten Entitäten gefunden.");
        }
        Path backup = null;
        if (Files.isDirectory(dir)) {
            try (Stream<Path> content = Files.list(dir)) {
                if (content.findAny().isPresent()) {
                    backup = dir.resolveSibling(dir.getFileName() + "-"
                            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                    Files.move(dir, backup);
                    log.accept("Bisherige Standards gesichert: " + backup);
                }
            }
        }
        Files.createDirectories(dir.resolve("vorlagen"));

        List<String[]> rows = new ArrayList<>();
        Set<JavaFile> baseTypes = new LinkedHashSet<>();
        Set<String> templateNames = new LinkedHashSet<>();
        for (Map.Entry<Kind, List<JavaFile>> e : chosen.artefacts.entrySet()) {
            for (JavaFile f : e.getValue()) {
                String pattern = toTemplate(f.name(), chosen.name);
                String fileName = pattern + ".java";
                for (int n = 2; !templateNames.add(fileName); n++) {
                    fileName = pattern + "-" + n + ".java";
                }
                Files.writeString(dir.resolve("vorlagen").resolve(fileName), toTemplate(f.content(), chosen.name),
                        StandardCharsets.UTF_8);
                String[] supers = supers(f);
                rows.add(new String[]{e.getKey().phaseLabel(), e.getKey().title, "vorlagen/" + fileName, pattern,
                        toTemplate(f.pkg(), chosen.name), toTemplate(f.rel(), chosen.name),
                        toTemplate(String.join(", ", supers), chosen.name), classAnnotations(f), ""});
                for (String type : referencedTypes(supers)) {
                    JavaFile base = bySimpleName.get(type);
                    if (base != null && chosen.artefacts.values().stream().noneMatch(l -> l.contains(base))) {
                        baseTypes.add(base);
                    }
                }
            }
        }
        if (!baseTypes.isEmpty()) {
            Files.createDirectories(dir.resolve("basis"));
            for (JavaFile base : baseTypes) {
                Files.writeString(dir.resolve("basis").resolve(base.name() + ".java"), base.content(), StandardCharsets.UTF_8);
            }
        }
        Files.writeString(dir.resolve(INDEX), index(title, chosen.name, root, rows, baseTypes), StandardCharsets.UTF_8);
        log.accept("Standards erzeugt: %d Vorlagen, %d Basisklassen aus %s → %s"
                .formatted(rows.size(), baseTypes.size(), chosen.name, dir));
        return new Summary(chosen.name, rows.size(), baseTypes.size(), dir, backup);
    }

    private static String index(String title, String example, Path root, List<String[]> rows, Set<JavaFile> baseTypes) {
        StringBuilder md = new StringBuilder("# Standards" + (title.isBlank() ? "" : " – " + title) + "\n\n");
        md.append("- Beispiel-Entität: **").append(example).append("**\n");
        md.append("- Quelle: `").append(root).append("`\n");
        md.append("- Erzeugt: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n\n");
        md.append("Diese Datei und die Vorlagen sind die **Standards** für die Implementierung weiterer Entitäten und dürfen ")
                .append("bearbeitet werden: Zeilen ändern, löschen oder ergänzen, Vorlagen bereinigen, in „Hinweise“ Vorgaben ")
                .append("für Copilot notieren. Platzhalter: `").append(CLASS).append("` (z. B. Risiko), `").append(LOWER)
                .append("` (risiko), `").append(UPPER).append("` (RISIKO). Felder und fachliche Logik in den Vorlagen ")
                .append("stammen von der Beispiel-Entität und werden bei der Implementierung durch die Attribute der ")
                .append("Spezifikation ersetzt.\n\n");
        md.append("„Pfad“ ist der Ablageort relativ zum Root des Projekts; die Implementierung legt neue Dateien genau dort ab ")
                .append("(bei mehrmoduligen Projekten inkl. Modulordner).\n\n");
        md.append("## Artefakte\n\n| Nr | Phase | Artefakt | Vorlage | Klasse | Package | Pfad | Basisklasse / Schnittstellen | ")
                .append("Klassen-Annotationen | Hinweise |\n|---|---|---|---|---|---|---|---|---|---|\n");
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            md.append("| ").append(i + 1).append(" | ").append(r[0]).append(" | ").append(r[1]).append(" | `").append(r[2])
                    .append("` | `").append(r[3]).append("` | `").append(r[4]).append("` | `").append(r[5]).append("` | ")
                    .append(r[6].isEmpty() ? "–" : "`" + r[6] + "`").append(" | ").append(r[7].isEmpty() ? "–" : r[7])
                    .append(" | ").append(r[8]).append(" |\n");
        }
        md.append("\n## Basisklassen und Schnittstellen\n\n");
        if (baseTypes.isEmpty()) {
            md.append("Keine.\n");
        } else {
            md.append("Werden von neuen Klassen verwendet, nicht neu angelegt (Stand bei der Erzeugung, Ordner `basis/`):\n\n");
            baseTypes.forEach(b -> md.append("- `basis/").append(b.name()).append(".java` (").append(b.rel()).append(")\n"));
        }
        return md.toString();
    }

    // ---- loading ----

    /** Reads STANDARDS.md; columns are found by their header names, so the user may reorder or add columns. */
    public static Stored load(Path dir) throws IOException {
        Path index = dir.resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            throw new IllegalArgumentException("Keine Standards in " + dir
                    + " – im Projekt-Editor über „Standards erzeugen…“ anlegen.");
        }
        String md = SourceReader.read(index);
        Matcher ex = EXAMPLE_LINE.matcher(md);
        String example = ex.find() ? ex.group(1).strip() : "";
        List<Row> rows = new ArrayList<>();
        Map<String, Integer> header = null;
        boolean inArtefacts = false;
        for (String line : md.split("\n")) {
            String l = line.strip();
            if (l.startsWith("## ")) {
                inArtefacts = l.toLowerCase(Locale.ROOT).startsWith("## artefakte");
                header = null;
                continue;
            }
            if (!inArtefacts || !l.startsWith("|")) {
                continue;
            }
            List<String> cells = cells(l);
            if (header == null) {
                header = new HashMap<>();
                for (int i = 0; i < cells.size(); i++) {
                    header.put(cells.get(i).toLowerCase(Locale.ROOT), i);
                }
                continue;
            }
            if (cells.stream().allMatch(c -> c.matches(":?-+:?"))) {
                continue;
            }
            Map<String, Integer> h = header;
            java.util.function.Function<String, String> col = name -> {
                Integer i = h.entrySet().stream().filter(e -> e.getKey().startsWith(name)).map(Map.Entry::getValue)
                        .findFirst().orElse(null);
                String v = i == null || i >= cells.size() ? "" : cells.get(i);
                return v.equals("–") ? "" : v;
            };
            String template = unquote(col.apply("vorlage"));
            if (template.isEmpty() && unquote(col.apply("klasse")).isEmpty()) {
                continue;
            }
            rows.add(new Row(col.apply("phase"), col.apply("artefakt"), template, unquote(col.apply("klasse")),
                    unquote(col.apply("package")), unquote(col.apply("pfad")), unquote(col.apply("basisklasse")),
                    col.apply("klassen-annotationen"), col.apply("hinweise")));
        }
        List<Path> base = new ArrayList<>();
        if (Files.isDirectory(dir.resolve("basis"))) {
            try (Stream<Path> list = Files.list(dir.resolve("basis"))) {
                base.addAll(list.filter(p -> p.toString().endsWith(".java")).sorted().toList());
            }
        }
        return new Stored(example, dir, rows, base);
    }

    /** Replaces the placeholders with the new entity: Risiko / risiko / RISIKO. */
    public static String apply(String text, String entity) {
        String name = className(entity);
        return text.replace(CLASS, name).replace(LOWER, name.toLowerCase(Locale.ROOT))
                .replace(UPPER, name.toUpperCase(Locale.ROOT));
    }

    /**
     * Turns code of the example entity into a template: "VertragTO" → "{{Entitaet}}TO", package segment "vertrag" →
     * "{{entitaet}}", "ALT_VERTRAG" → "ALT_{{ENTITAET}}". Business names like "vertragsnummer" stay unchanged.
     */
    static String toTemplate(String text, String stem) {
        String cap = className(stem);
        String lower = cap.toLowerCase(Locale.ROOT);
        String upper = cap.toUpperCase(Locale.ROOT);
        String t = text.replaceAll(Pattern.quote(cap) + "(?![a-z])", Matcher.quoteReplacement(CLASS));
        t = t.replaceAll("(?<![A-Za-z0-9])" + Pattern.quote(lower) + "(?![a-z0-9])", Matcher.quoteReplacement(LOWER));
        return t.replaceAll("(?<![A-Z0-9])" + Pattern.quote(upper) + "(?![A-Z0-9])", Matcher.quoteReplacement(UPPER));
    }

    /** "risiko" / "Risiko-Deckung" → "Risiko" / "RisikoDeckung". */
    public static String className(String text) {
        StringBuilder out = new StringBuilder();
        for (String part : text.strip().split("[^\\p{L}\\p{N}]+")) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }

    private static List<String> cells(String line) {
        String l = line.strip();
        l = l.substring(1, l.endsWith("|") ? l.length() - 1 : l.length());
        List<String> cells = new ArrayList<>();
        for (String c : l.split("(?<!\\\\)\\|", -1)) {
            cells.add(c.strip().replace("\\|", "|"));
        }
        return cells;
    }

    private static String unquote(String s) {
        return s.replace("`", "").strip();
    }

    // ---- scanning and grouping ----

    private static List<JavaFile> javaFiles(Path root) throws IOException {
        List<JavaFile> files = new ArrayList<>();
        for (Path file : SourceTree.files(root, (f, attrs) -> f.getFileName().toString().endsWith(".java"))) {
            String content = SourceReader.read(file);
            if (content != null) {
                Matcher pkg = PACKAGE.matcher(content);
                files.add(new JavaFile(file, root.relativize(file).toString().replace('\\', '/'),
                        file.getFileName().toString().replaceFirst("\\.java$", ""),
                        pkg.find() ? pkg.group(1) : "", content));
            }
        }
        files.sort(Comparator.comparing(JavaFile::rel));
        return files;
    }

    /** Entity name → its artefacts. Data structures define the entities; logic classes are attached to them. */
    private static final class Group {
        final String name;
        final Map<Kind, List<JavaFile>> artefacts = new EnumMap<>(Kind.class);

        Group(String name) {
            this.name = name;
        }

        int kinds(Set<String> phases) {
            return (int) artefacts.keySet().stream().filter(k -> k.phases.stream().anyMatch(phases::contains)).count();
        }

        int count() {
            return artefacts.values().stream().mapToInt(List::size).sum();
        }

        int size() {
            return artefacts.values().stream().flatMap(List::stream).mapToInt(f -> f.content().length()).sum();
        }
    }

    private static Map<String, Group> group(List<JavaFile> files) {
        Map<String, Group> groups = new TreeMap<>();
        Map<JavaFile, Kind> logic = new LinkedHashMap<>();
        for (JavaFile f : files) {
            if (declares(f, "abstract\\s+class|enum")) {
                continue; // base types become basis/, not templates
            }
            DataStructure data = declares(f, "interface") ? null : Conventions.dataStructure(f.name(), f.content());
            if (data != null) {
                String stem = Conventions.stripVariantPrefix(data.stem());
                groups.computeIfAbsent(stem.toLowerCase(Locale.ROOT), k -> new Group(stem))
                        .artefacts.computeIfAbsent(Kind.of(data.kind()), k -> new ArrayList<>()).add(f);
            } else {
                Kind kind = logicKind(f);
                if (kind != null) {
                    logic.put(f, kind);
                }
            }
        }
        for (Map.Entry<JavaFile, Kind> entry : logic.entrySet()) {
            JavaFile f = entry.getKey();
            String lower = f.name().toLowerCase(Locale.ROOT);
            String key = groups.keySet().stream().filter(lower::contains)
                    .max(Comparator.comparingInt(String::length)).orElse(null);
            if (key == null) {
                if (declares(f, "interface")) {
                    continue; // a general interface (e.g. Anreicherer<T>) is a base type, not a template
                }
                String stem = Conventions.stripVariantPrefix(Conventions.logicStem(f.name()));
                key = stem.toLowerCase(Locale.ROOT);
                groups.computeIfAbsent(key, k -> new Group(stem));
            }
            groups.get(key).artefacts.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(f);
        }
        groups.values().removeIf(g -> g.artefacts.isEmpty());
        return groups;
    }

    /** Logic classes by phase – same rules as the code collector, incl. the fixed conventions. */
    private static Kind logicKind(JavaFile f) {
        if (Conventions.isRepository(f.name())) {
            return Kind.REPOSITORY; // one repository per entity, used for reading
        }
        return switch (PhaseClassifier.classify(f.rel(), f.content())) {
            case READ -> Kind.LESEN;
            case PROCESS -> Kind.VERARBEITUNG;
            case WRITE -> Kind.SCHREIBEN;
            case PERSISTENCE -> Kind.DATENZUGRIFF;
            default -> null;
        };
    }

    /** Whether the file's own type is declared with one of the given keywords (regex alternatives). */
    private static boolean declares(JavaFile f, String keywords) {
        return Pattern.compile("\\b(?:" + keywords + ")\\s+" + Pattern.quote(f.name()) + "\\b").matcher(f.content()).find();
    }

    private static Group choose(Map<String, Group> groups, String wanted) {
        if (!wanted.isBlank()) {
            Group g = groups.get(className(wanted).toLowerCase(Locale.ROOT));
            if (g == null) {
                throw new IllegalArgumentException("Beispiel-Entität '" + wanted + "' nicht gefunden. Vorhanden: "
                        + groups.values().stream().map(x -> x.name).collect(Collectors.joining(", ")));
            }
            return g;
        }
        return groups.values().stream()
                .max(Comparator.<Group>comparingInt(g -> g.kinds(ALL_PHASES))
                        .thenComparing(Comparator.comparingInt(Group::size).reversed()))
                .orElse(null);
    }

    /** Superclass and interfaces of the type, e.g. {"AbstractTO", "Anreicherer<VertragTO>"}. */
    private static String[] supers(JavaFile f) {
        String ext = match(f.content(), "\\bclass\\s+" + f.name() + "\\b[^{]*?\\bextends\\s+([\\w.<>, ]+?)(?:\\s+implements\\b|\\s*\\{)");
        String impl = match(f.content(), "\\b(?:class|record)\\s+" + f.name() + "\\b[^{]*?\\bimplements\\s+([^{]+)");
        if (impl == null) {
            impl = match(f.content(), "\\binterface\\s+" + f.name() + "\\b[^{]*?\\bextends\\s+([^{]+)");
        }
        List<String> out = new ArrayList<>();
        if (ext != null) {
            out.add(ext.replaceAll("\\s+", " "));
        }
        if (impl != null) {
            out.add(impl.replaceAll("\\s+", " ").strip());
        }
        return out.toArray(String[]::new);
    }

    private static List<String> referencedTypes(String[] supers) {
        List<String> types = new ArrayList<>();
        for (String s : supers) {
            for (String t : s.replaceAll("<[^>]*>", "").split(",")) {
                String simple = t.strip().replaceFirst(".*\\.", "");
                if (!simple.isEmpty()) {
                    types.add(simple);
                }
            }
        }
        return types;
    }

    /** Annotations before the type declaration. */
    private static String classAnnotations(JavaFile f) {
        Matcher decl = Pattern.compile("\\b(?:class|record|interface)\\s+" + f.name() + "\\b").matcher(f.content());
        if (!decl.find()) {
            return "";
        }
        String head = f.content().substring(0, decl.start());
        int lastImport = Math.max(head.lastIndexOf("import "), head.lastIndexOf("package "));
        head = head.substring(Math.max(0, head.indexOf(';', Math.max(0, lastImport)) + 1));
        Set<String> found = new LinkedHashSet<>();
        Matcher m = ANNOTATION.matcher(head.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", ""));
        while (m.find()) {
            found.add("`@" + m.group(1) + "`");
        }
        return String.join(", ", found);
    }

    private static String match(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }
}
