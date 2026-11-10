package de.devtoolbox.tools.standards;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Tool;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;
import de.devtoolbox.tools.codecollector.PhaseClassifier;
import de.devtoolbox.tools.codecollector.SourceReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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

/**
 * Building block: finds existing implementations in the target project - per selected phase: data objects (TOs, POs,
 * entities, DTOs) and reading logic (Read), mapping logic (Process), writing logic (Write), data access - picks the
 * most complete entity as example and renders it as structural templates
 * ("Standards") for implementing a new entity the same way.
 */
public final class StandardsTool implements Tool {

    public static final String ID = "standards";
    public static final String TARGET = "target";
    public static final String ENTITY = "entity";
    public static final String EXAMPLE = "example";

    private static final Set<String> EXCLUDED_DIRS =
            Set.of("target", "build", "out", "bin", ".git", ".idea", ".gradle", ".mvn", "node_modules");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern LOGIC_NAME = Pattern.compile("(\\w+?)(ItemReader|Reader|RowMapper|Anreicherer|Anreicherung"
            + "|Leser|Loader|ItemProcessor|Processor|Mapper|Mapping|Converter|Konverter|Zuordnung|Umsetzung"
            + "|ItemWriter|Writer|Exporter|Repository|Dao|DAO)$");
    private static final Pattern ANNOTATION = Pattern.compile("@(\\w+)(\\([^)]*\\))?");
    private static final Pattern PREFIX = Pattern.compile("^(?:Alt|Neu|Old|New)(?=[A-Z])");

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

        boolean in(Set<String> selected) {
            return phases.stream().anyMatch(selected::contains);
        }

        /** e.g. "Read" or "Read/Write". */
        String phaseLabel() {
            return java.util.stream.Stream.of("READ", "PROCESS", "WRITE").filter(phases::contains)
                    .map(p -> p.charAt(0) + p.substring(1).toLowerCase(Locale.ROOT)).collect(Collectors.joining("/"));
        }
    }

    record JavaFile(Path path, String rel, String name, String pkg, String content) {
    }

    /** One template class of the example entity and the new class to derive from it. */
    record Template(Kind kind, JavaFile file, String newName, String newPackage) {
    }

    /**
     * @param markdown       the "Standards" section
     * @param example        name of the example entity ({@code null} if none was found)
     * @param artifactsTable Markdown table of the artefacts to create
     */
    public record Result(String markdown, String example, String artifactsTable, int templates) {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Standards (Vorlagen aus dem Zielprojekt)";
    }

    @Override
    public List<Param> params() {
        return List.of(
                Param.directory(TARGET, "Zielprojekt (Code)", true,
                        "Code der Migration, in der implementiert wird – wird nach Beispielen (Standards) durchsucht"),
                Param.text(ENTITY, "Fachliche Entität", "", true, "Die neu zu implementierende Entität, z. B. Risiko"),
                Param.multi(CodeCollectorTool.PHASES, "Zu implementierende Phasen", "READ,PROCESS,WRITE",
                        "Je Phase werden passende Vorlagen gesucht (Read: Datenobjekte + Einleselogik, "
                                + "Process: Mapping, Write: Schreiblogik)",
                        new Param.Option("READ", "Read"), new Param.Option("PROCESS", "Process"),
                        new Param.Option("WRITE", "Write")),
                Param.text(EXAMPLE, "Beispiel-Entität", "", false,
                        "Leer = automatisch: die bereits umgesetzte Entität mit den vollständigsten Vorlagen"),
                Param.directory("output", "Ausgabeordner", false, "Leer = Downloads"));
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Result result = collect(config, log);
        Path dir = outputDir(config);
        Files.createDirectories(dir);
        Path file = dir.resolve("standards-" + CodeCollectorTool.slug(config.text(ENTITY)) + ".md");
        Files.writeString(file, result.markdown(), StandardCharsets.UTF_8);
        log.accept("Geschrieben: " + file);
        return List.of(file);
    }

    public static Path outputDir(Config config) {
        if (config.path("output") != null) {
            return config.path("output");
        }
        Path home = Path.of(System.getProperty("user.home"));
        return Files.isDirectory(home.resolve("Downloads")) ? home.resolve("Downloads") : home;
    }

    public Result collect(Config config, Consumer<String> log) throws IOException {
        Path root = config.path(TARGET);
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalArgumentException("Zielprojekt existiert nicht: " + config.text(TARGET));
        }
        String entity = className(config.text(ENTITY));
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Die fachliche Entität fehlt.");
        }
        Set<String> phases = config.list(CodeCollectorTool.PHASES).stream()
                .map(v -> v.toUpperCase(Locale.ROOT)).filter(Set.of("READ", "PROCESS", "WRITE")::contains)
                .collect(Collectors.toSet());
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Phase auswählen.");
        }
        log.accept("Suche Standards in " + root);
        List<JavaFile> files = javaFiles(root);
        Map<String, JavaFile> bySimpleName = new HashMap<>();
        files.forEach(f -> bySimpleName.putIfAbsent(f.name(), f));

        Map<String, Group> groups = group(files);
        log.accept("%d Java-Dateien, %d Entitäten mit Vorlagen gefunden.".formatted(files.size(), groups.size()));
        Group example = choose(groups, config.text(EXAMPLE), entity, phases);
        if (example == null) {
            log.accept("WARNUNG: Keine Vorlagen gefunden – die Aufgabe enthält keine Standards.");
            String md = "## Standards\n\nIm Zielprojekt wurden keine umgesetzten Entitäten (TO, PO, Entity, Einleselogik) "
                    + "gefunden. Orientiere dich an den Konventionen des Projekts, soweit erkennbar, und markiere "
                    + "Annahmen als [OFFEN].\n";
            return new Result(md, null, "| – | keine Vorlagen gefunden | | | |\n", 0);
        }
        List<Template> templates = templates(example, entity, phases);
        log.accept("Beispiel-Entität: " + example.name + " (" + templates.size() + " Vorlagen: "
                + templates.stream().map(t -> t.file().name()).collect(Collectors.joining(", ")) + ")");
        return render(groups, example, templates, entity, bySimpleName, config.text(EXAMPLE).isBlank(), phases);
    }

    // ---- scanning and grouping ----

    private static List<JavaFile> javaFiles(Path root) throws IOException {
        List<JavaFile> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
                boolean test = name.equals("test") && dir.getParent().getFileName() != null
                        && dir.getParent().getFileName().toString().equals("src");
                return EXCLUDED_DIRS.contains(name) || test ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".java")) {
                    String content = SourceReader.read(file);
                    if (content != null) {
                        Matcher pkg = PACKAGE.matcher(content);
                        files.add(new JavaFile(file, root.relativize(file).toString().replace('\\', '/'),
                                file.getFileName().toString().replaceFirst("\\.java$", ""),
                                pkg.find() ? pkg.group(1) : "", content));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(JavaFile::rel));
        return files;
    }

    /** Entity name → its artefacts. Data structures define the entities; reading logic is attached to them. */
    private static final class Group {
        final String name;
        final Map<Kind, List<JavaFile>> artefacts = new EnumMap<>(Kind.class);

        Group(String name) {
            this.name = name;
        }

        int kinds(Set<String> phases) {
            return (int) artefacts.keySet().stream().filter(k -> k.in(phases)).count();
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
                continue; // base types are shown separately, not as templates
            }
            String[] dataStem = declares(f, "interface") ? null : dataStem(f);
            if (dataStem != null) {
                String stem = PREFIX.matcher(dataStem[1]).replaceFirst("");
                groups.computeIfAbsent(stem.toLowerCase(Locale.ROOT), k -> new Group(stem))
                        .artefacts.computeIfAbsent(Kind.valueOf(dataStem[0]), k -> new ArrayList<>()).add(f);
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
                Matcher m = LOGIC_NAME.matcher(f.name());
                String stem = PREFIX.matcher(m.matches() ? m.group(1) : f.name()).replaceFirst("");
                key = stem.toLowerCase(Locale.ROOT);
                groups.computeIfAbsent(key, k -> new Group(stem));
            }
            groups.get(key).artefacts.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(f);
        }
        groups.values().removeIf(g -> g.artefacts.isEmpty());
        return groups;
    }

    /** {kind, stem} for data structures, else null. */
    private static String[] dataStem(JavaFile f) {
        String n = f.name();
        if (n.matches("\\w+(?:Dto|DTO)")) {
            return new String[]{"DTO", n.replaceFirst("(?:Dto|DTO)$", "")};
        }
        if (n.matches("\\w+(?:TO|To)")) {
            return new String[]{"TO", n.replaceFirst("(?:TO|To)$", "")};
        }
        if (n.matches("\\w+(?:PO|Po)")) {
            return new String[]{"PO", n.replaceFirst("(?:PO|Po)$", "")};
        }
        if (n.matches("\\w+Entity") || f.content().contains("@Entity")) {
            return new String[]{"ENTITY", n.replaceFirst("Entity$", "")};
        }
        return null;
    }

    /** Logic classes by phase - same rules as the code collector, incl. the fixed directory conventions. */
    private static Kind logicKind(JavaFile f) {
        if (PhaseClassifier.isRepository(f.name())) {
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

    private static Group choose(Map<String, Group> groups, String wanted, String entity, Set<String> phases) {
        if (!wanted.isBlank()) {
            Group g = groups.get(className(wanted).toLowerCase(Locale.ROOT));
            if (g == null) {
                throw new IllegalArgumentException("Beispiel-Entität '" + wanted + "' nicht gefunden. Vorhanden: "
                        + groups.values().stream().map(x -> x.name).collect(Collectors.joining(", ")));
            }
            return g;
        }
        return groups.values().stream()
                .filter(g -> !g.name.equalsIgnoreCase(entity) && g.kinds(phases) > 0)
                .max(Comparator.<Group>comparingInt(g -> g.kinds(phases))
                        .thenComparing(Comparator.comparingInt(Group::size).reversed()))
                .orElse(null);
    }

    private static List<Template> templates(Group example, String entity, Set<String> phases) {
        List<Template> templates = new ArrayList<>();
        for (Map.Entry<Kind, List<JavaFile>> e : example.artefacts.entrySet()) {
            if (!e.getKey().in(phases)) {
                continue;
            }
            for (JavaFile f : e.getValue()) {
                templates.add(new Template(e.getKey(), f, replaceStem(f.name(), example.name, entity),
                        newPackage(f.pkg(), example.name, entity)));
            }
        }
        return templates;
    }

    // ---- rendering ----

    private static Result render(Map<String, Group> groups, Group example, List<Template> templates, String entity,
                                 Map<String, JavaFile> bySimpleName, boolean automatic, Set<String> phases) {
        StringBuilder md = new StringBuilder();
        md.append("## Standards (Vorlagen aus dem Zielprojekt)\n\n");
        md.append("Die Vorlagen sind Klassen der bereits umgesetzten Entität **").append(example.name).append("** – ")
                .append(automatic ? "gewählt, weil sie in den gewählten Phasen die vollständigsten Vorlagen hat."
                        : "vorgegeben in der Konfiguration.");
        String others = groups.values().stream().filter(g -> g != example && g.kinds(phases) > 0)
                .sorted(Comparator.<Group>comparingInt(g -> g.kinds(phases)).reversed().thenComparing(g -> g.name))
                .limit(10).map(g -> g.name + " (" + kindsOf(g, phases) + ")").collect(Collectors.joining("; "));
        if (!others.isEmpty()) {
            md.append(" Weitere umgesetzte Entitäten: ").append(others).append('.');
        }
        md.append(" Welche Vorlage für welche neue Klasse gilt, steht in der Tabelle „Zu erstellende Artefakte“.\n");

        StringBuilder table = new StringBuilder("| Nr | Phase | Artefakt | Neue Klasse | Package | Vorlage | "
                + "Basisklasse / Schnittstellen | Klassen-Annotationen |\n|---|---|---|---|---|---|---|---|\n");
        Set<JavaFile> baseTypes = new LinkedHashSet<>();
        for (int i = 0; i < templates.size(); i++) {
            Template t = templates.get(i);
            JavaFile f = t.file();
            String ext = match(f.content(), "\\bclass\\s+" + f.name() + "\\b[^{]*?\\bextends\\s+([\\w.]+)");
            String impl = match(f.content(), "\\b(?:class|record)\\s+" + f.name() + "\\b[^{]*?\\bimplements\\s+([^{]+)");
            if (impl == null) {
                impl = match(f.content(), "\\binterface\\s+" + f.name() + "\\b[^{]*?\\bextends\\s+([^{]+)");
            }
            List<String> supers = new ArrayList<>();
            if (ext != null) {
                supers.add("`" + ext + "`");
            }
            if (impl != null) {
                supers.add("`" + impl.strip().replaceAll("\\s+", " ") + "`");
            }
            table.append("| ").append(i + 1).append(" | ").append(t.kind().phaseLabel()).append(" | ")
                    .append(t.kind().title).append(" | `").append(t.newName()).append("` | `").append(t.newPackage())
                    .append("` | Vorlage ").append(i + 1).append(": `").append(f.name()).append("` | ")
                    // shown for the new class: VertragTO → RisikoTO inside generics as well
                    .append(supers.isEmpty() ? "–" : String.join(", ", supers).replace(example.name, entity)).append(" | ")
                    .append(orDash(classAnnotations(f))).append(" |\n");

            md.append("\n### Vorlage ").append(i + 1).append(": ").append(f.name()).append("\n\n");
            appendCode(md, f);
            for (String type : referencedBaseTypes(f, ext, impl)) {
                JavaFile base = bySimpleName.get(type);
                if (base != null && templates.stream().noneMatch(x -> x.file() == base)) {
                    baseTypes.add(base);
                }
            }
        }
        if (!baseTypes.isEmpty()) {
            md.append("\n### Basisklassen und Schnittstellen\n\n")
                    .append("Von den Vorlagen verwendet; neue Klassen verwenden sie genauso, sie werden nicht neu angelegt.\n");
            for (JavaFile base : baseTypes) {
                md.append("\n#### ").append(base.name()).append("\n\n");
                appendCode(md, base);
            }
        }
        return new Result(md.toString(), example.name, table.toString(), templates.size());
    }

    private static void appendCode(StringBuilder md, JavaFile f) {
        String code = f.content().strip();
        int longest = 0;
        int run = 0;
        for (int i = 0; i < code.length(); i++) {
            run = code.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        String fence = "`".repeat(Math.max(3, longest + 1));
        md.append("===== BEGIN FILE: ").append(f.rel()).append(" =====\n")
                .append(fence).append("java\n").append(code).append('\n').append(fence).append('\n')
                .append("===== END FILE: ").append(f.rel()).append(" =====\n");
    }

    private static String kindsOf(Group g, Set<String> phases) {
        return g.artefacts.keySet().stream().filter(k -> k.in(phases)).map(k -> k.title).collect(Collectors.joining(", "));
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
        return annotations(head);
    }

    private static String annotations(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = ANNOTATION.matcher(text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", ""));
        while (m.find()) {
            found.add("`@" + m.group(1) + "`");
        }
        return String.join(", ", found);
    }

    private static List<String> referencedBaseTypes(JavaFile f, String ext, String impl) {
        List<String> types = new ArrayList<>();
        for (String s : new String[]{ext, impl}) {
            if (s != null) {
                for (String t : s.replaceAll("<[^>]*>", "").split(",")) {
                    String simple = t.strip().replaceFirst(".*\\.", "");
                    if (!simple.isEmpty()) {
                        types.add(simple);
                    }
                }
            }
        }
        return types;
    }

    private static String match(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1).strip() : null;
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "–" : s;
    }

    /** "RisikoTO" from "VertragTO": the example's stem is replaced by the new entity (also keeps Alt/Neu prefixes). */
    static String replaceStem(String name, String stem, String entity) {
        int i = name.indexOf(stem);
        return i < 0 ? name : name.substring(0, i) + entity + name.substring(i + stem.length());
    }

    /** "de.x.vertrag.to" → "de.x.risiko.to"; unchanged if the package has no segment named like the example. */
    static String newPackage(String pkg, String stem, String entity) {
        String lower = stem.toLowerCase(Locale.ROOT);
        String[] parts = pkg.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(lower)) {
                parts[i] = entity.toLowerCase(Locale.ROOT);
            }
        }
        return String.join(".", parts);
    }

    /** "risiko" / "Risiko-Deckung" → "Risiko" / "RisikoDeckung". */
    static String className(String text) {
        StringBuilder out = new StringBuilder();
        for (String part : text.strip().split("[^\\p{L}\\p{N}]+")) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }
}
