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
        DATENZUGRIFF("Datenzugriff (DAO)", "READ", "WRITE"),
        KONFIGURATION("Batch-Konfiguration", "READ", "PROCESS", "WRITE");

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
        return group(JavaCatalog.scan(root)).values().stream()
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
        JavaCatalog catalog = JavaCatalog.scan(root);
        Map<String, JavaCatalog.Type> byPath = new LinkedHashMap<>();
        for (JavaCatalog.Type type : catalog.files()) {
            byPath.put(type.rel(), type);
        }
        Map<String, Group> groups = group(catalog);
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
        Set<JavaCatalog.Type> templateTypes = new LinkedHashSet<>();
        for (List<JavaFile> groupFiles : chosen.artefacts.values()) {
            for (JavaFile file : groupFiles) {
                templateTypes.add(byPath.get(file.rel()));
            }
        }
        Map<JavaCatalog.Type, Integer> dependencyUses = new LinkedHashMap<>();
        for (JavaCatalog.Type t : templateTypes) {
            for (JavaCatalog.Type ref : catalog.references(t)) {
                if (!templateTypes.contains(ref)) {
                    dependencyUses.merge(ref, 1, Integer::sum);
                }
            }
        }
        Set<JavaCatalog.Type> dependencies = new LinkedHashSet<>();
        for (Map.Entry<JavaCatalog.Type, Integer> dep : dependencyUses.entrySet()) {
            JavaCatalog.Type type = dep.getKey();
            if (type.shared() || dep.getValue() >= 2) {
                dependencies.add(type);
            }
        }
        // Pull in direct foundations of shared types as well (without importing the entire codebase).
        for (JavaCatalog.Type shared : List.copyOf(dependencies)) {
            for (JavaCatalog.Type ref : catalog.references(shared)) {
                if (!templateTypes.contains(ref) && ref.shared()) {
                    dependencies.add(ref);
                }
            }
        }
        for (JavaCatalog.Type base : dependencies) {
            baseTypes.add(new JavaFile(base.path(), base.rel(), base.name(), base.pkg(), base.code()));
        }
        if (!catalog.duplicateTypes().isEmpty()) {
            log.accept("WARNUNG: Mehrfach definierte Java-Typen: "
                    + catalog.duplicateTypes().stream().map(JavaCatalog.Type::fqn).distinct().limit(8)
                    .collect(Collectors.joining(", ")) + " – unklare Verweise nicht automatisch aufgenommen.");
        }
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
                JavaCatalog.Type owner = byPath.get(f.rel());
                JavaCatalog.Recognition recognition = owner == null ? null : JavaCatalog.recognise(owner);
                if (recognition == null && owner != null) {
                    recognition = JavaCatalog.roleBeforeVariant(owner);
                }
                String variant = recognition == null ? "" : recognition.variant();
                if (variant.equalsIgnoreCase("Bestand") || variant.equalsIgnoreCase("Schwebe")
                        || variant.equalsIgnoreCase("Stock") || variant.equalsIgnoreCase("Pending")) {
                    rows.getLast()[8] = "Variante: " + variant + " (automatisch erkannt)";
                }
            }
        }
        if (!baseTypes.isEmpty()) {
            Files.createDirectories(dir.resolve("basis"));
            Map<String, Long> counts = baseTypes.stream()
                    .collect(Collectors.groupingBy(JavaFile::name, Collectors.counting()));
            for (JavaFile base : baseTypes) {
                Path baseTarget = dir.resolve("basis");
                if (counts.getOrDefault(base.name(), 0L) > 1) {
                    baseTarget = baseTarget.resolve(base.pkg().isBlank() ? "_default" : base.pkg().replace('.', '/'));
                }
                baseTarget = baseTarget.resolve(base.name() + ".java");
                Files.createDirectories(baseTarget.getParent());
                Files.writeString(baseTarget, base.content(), StandardCharsets.UTF_8);
            }
        }
        Files.writeString(dir.resolve(INDEX), index(title, chosen.name, root, rows, baseTypes), StandardCharsets.UTF_8);
        log.accept("Standards erzeugt: %d Vorlagen, %d Vorlagen je Projekt aus %s → %s"
                .formatted(rows.size(), baseTypes.size(), chosen.name, dir));
        return new Summary(chosen.name, rows.size(), baseTypes.size(), dir, backup);
    }

    private static String index(String title, String example, Path root, List<String[]> rows, Set<JavaFile> baseTypes) {
        StringBuilder md = new StringBuilder("# Standards" + (title.isBlank() ? "" : " – " + title) + "\n\n");
        if (!example.isBlank()) {
            md.append("- Beispiel-Entität: **").append(example).append("**\n");
        }
        if (root != null) {
            md.append("- Quelle: `").append(root).append("`\n");
        }
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
        md.append("\n## Vorlagen je Projekt (einmalig)\n\n");
        if (baseTypes.isEmpty()) {
            md.append("Keine.\n");
        } else {
            md.append("Gemeinsame Klassen und Schnittstellen (Ordner `basis/`): je Projekt einmalig anlegen, falls es sie im Code noch nicht gibt, sonst nur verwenden:\n\n");
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
        if (md == null) {
            throw new IOException("STANDARDS.md konnte nicht gelesen werden: " + index);
        }
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
                Integer i = h.containsKey(name) ? h.get(name)
                        : h.entrySet().stream().filter(e -> e.getKey().startsWith(name)).map(Map.Entry::getValue)
                                .findFirst().orElse(null);
                String v = i == null || i >= cells.size() ? "" : cells.get(i);
                return v.equals("–") ? "" : v;
            };
            String template = unquote(col.apply("vorlage"));
            if (!template.isBlank()) {
                templatePath(dir, template);
            }
            if (template.isEmpty() && unquote(col.apply("klasse")).isEmpty()) {
                continue;
            }
            rows.add(new Row(col.apply("phase"), col.apply("artefakt"), template, unquote(col.apply("klasse")),
                    unquote(col.apply("package")), unquote(col.apply("pfad")), unquote(col.apply("basisklasse")),
                    col.apply("klassen-annotationen"), col.apply("hinweise")));
        }
        List<Path> base = new ArrayList<>();
        if (Files.isDirectory(dir.resolve("basis"))) {
            try (Stream<Path> list = Files.walk(dir.resolve("basis"))) {
                base.addAll(list.filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)
                        && p.getFileName().toString().endsWith(".java")).sorted().toList());
            }
        }
        return new Stored(example, dir, rows, base);
    }

    /**
     * Only direct Java templates under vorlagen/ may be loaded or deleted. In particular,
     * an edited STANDARDS.md must never reference arbitrary files via "../" or symlinks.
     */
    public static Path templatePath(Path dir, String template) {
        Path path = Path.of(template);
        Path root = dir.toAbsolutePath().normalize();
        Path allowed = root.resolve("vorlagen");
        Path file = root.resolve(path).normalize();
        if (path.isAbsolute() || !file.getParent().equals(allowed)
                || !file.getFileName().toString().endsWith(".java")
                || Files.isSymbolicLink(allowed) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("Ungültiger Vorlagenpfad in STANDARDS.md: " + template);
        }
        return file;
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

    // ---- editing (add / remove parts of a standard) ----

    /** Creates an empty standard (index without rows) unless {@code dir} already has an index. */
    public static void ensureStandard(Path dir, String title) throws IOException {
        if (!Files.isRegularFile(dir.resolve(INDEX))) {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(INDEX), index(title, "", null, List.of(), Set.of()), StandardCharsets.UTF_8);
        }
    }

    /** Phase choices for a template: "Read", "Process", "Write", "Read/Write". */
    public static List<String> phaseChoices() {
        return List.of("Read", "Process", "Write", "Read/Write");
    }

    /** Artefact kinds for a template, e.g. "Transferobjekt (TO)". */
    public static List<String> kindChoices() {
        return Stream.of(Kind.values()).map(k -> k.title).toList();
    }

    /** Suggested {phase, artefact kind} of a class, by the code collector's conventions; never null. */
    public static String[] suggestKind(String name, String content) {
        JavaFile f = new JavaFile(Path.of(name + ".java"), name + ".java", name, "", content);
        DataStructure data = Conventions.dataStructure(name, content);
        Kind kind = data != null ? Kind.of(data.kind()) : logicKind(f);
        if (kind == null) {
            kind = Kind.LESEN;
        }
        return new String[]{kind.phaseLabel(), kind.title};
    }

    /** Suggested entity name contained in a class, e.g. "Vertrag" for VertragTO; blank if unknown. */
    public static String guessEntity(String name, String content) {
        DataStructure data = Conventions.dataStructure(name, content);
        String stem = data != null ? data.stem() : Conventions.logicStem(name);
        return stem == null ? "" : Conventions.stripVariantPrefix(stem);
    }

    /**
     * Adds a whole class as template: the entity name in it is replaced by the placeholders (as when generating),
     * the file is copied to {@code vorlagen/} and a row is appended to STANDARDS.md.
     *
     * @param entity name of the entity contained in the class (blank = the standard's example entity)
     */
    public static Row addTemplate(Path dir, Path javaFile, String entity, String phase, String kind)
            throws IOException {
        ensureStandard(dir, "");
        String content = SourceReader.read(javaFile);
        if (content == null) {
            throw new IllegalArgumentException("Datei nicht lesbar: " + javaFile);
        }
        String stem = entity == null || entity.isBlank() ? load(dir).example() : entity;
        String name = javaFile.getFileName().toString().replaceFirst("\\.java$", "");
        Matcher pkgMatcher = PACKAGE.matcher(content);
        String pkg = pkgMatcher.find() ? pkgMatcher.group(1) : "";
        String rel = "src/main/java/" + (pkg.isBlank() ? "" : pkg.replace('.', '/') + "/") + name + ".java";
        JavaFile f = new JavaFile(javaFile, rel, name, pkg, content);
        String pattern = stem.isBlank() ? name : toTemplate(name, stem);
        Files.createDirectories(dir.resolve("vorlagen"));
        String fileName = pattern + ".java";
        for (int n = 2; Files.exists(dir.resolve("vorlagen").resolve(fileName)); n++) {
            fileName = pattern + "-" + n + ".java";
        }
        Files.writeString(dir.resolve("vorlagen").resolve(fileName), stem.isBlank() ? content : toTemplate(content, stem),
                StandardCharsets.UTF_8);
        Row row = new Row(phase, kind, "vorlagen/" + fileName, pattern, stem.isBlank() ? pkg : toTemplate(pkg, stem),
                stem.isBlank() ? rel : toTemplate(rel, stem),
                stem.isBlank() ? String.join(", ", supers(f)) : toTemplate(String.join(", ", supers(f)), stem),
                classAnnotations(f), "");
        List<String> lines = new ArrayList<>(Files.readAllLines(dir.resolve(INDEX), StandardCharsets.UTF_8));
        int[] section = artefactTable(lines);
        if (section == null) {
            throw new IllegalStateException("STANDARDS.md enthält keine Tabelle unter „## Artefakte“.");
        }
        lines.add(section[1] + 1, rowLine(lines.get(section[0]), row, section[1] - section[0] - 1));
        Files.write(dir.resolve(INDEX), lines, StandardCharsets.UTF_8);
        return row;
    }

    /** Copies a base class or interface unchanged to {@code basis/} and lists it in STANDARDS.md. */
    public static Path addBase(Path dir, Path javaFile) throws IOException {
        ensureStandard(dir, "");
        Files.createDirectories(dir.resolve("basis"));
        Path target = dir.resolve("basis").resolve(javaFile.getFileName());
        // Never silently replace a project-wide class previously imported by the user.
        Files.copy(javaFile, target);
        String entry = "- `basis/" + javaFile.getFileName() + "`";
        List<String> lines = new ArrayList<>(Files.readAllLines(dir.resolve(INDEX), StandardCharsets.UTF_8));
        if (lines.stream().noneMatch(l -> l.startsWith(entry))) {
            int at = lines.indexOf("Keine.");
            if (at >= 0) {
                lines.set(at, "Werden von neuen Klassen verwendet, nicht neu angelegt (Ordner `basis/`):");
                lines.add(at + 1, "");
                lines.add(at + 2, entry);
            } else {
                while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
                    lines.remove(lines.size() - 1);
                }
                lines.add(entry);
            }
            Files.write(dir.resolve(INDEX), lines, StandardCharsets.UTF_8);
        }
        return target;
    }

    /** Removes the row from STANDARDS.md and deletes its template file unless another row still uses it. */
    public static void removeRow(Path dir, Row row) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(dir.resolve(INDEX), StandardCharsets.UTF_8));
        int[] section = artefactTable(lines);
        if (section == null) {
            return;
        }
        List<String> header = cells(lines.get(section[0])).stream().map(c -> c.toLowerCase(Locale.ROOT)).toList();
        int templateCol = indexOfHeader(header, "vorlage");
        int classCol = indexOfHeader(header, "klasse");
        int removed = -1;
        int sameTemplate = 0;
        for (int i = section[0] + 2; i <= section[1]; i++) {
            List<String> c = cells(lines.get(i));
            String template = templateCol < 0 || templateCol >= c.size() ? "" : unquote(c.get(templateCol));
            String klass = classCol < 0 || classCol >= c.size() ? "" : unquote(c.get(classCol));
            if (!row.template().isBlank() && template.equals(row.template())) {
                sameTemplate++;
            }
            if (removed < 0 && template.equals(row.template()) && klass.equals(row.className())) {
                removed = i;
            }
        }
        if (removed < 0) {
            return;
        }
        Path deleteFile = sameTemplate <= 1 && !row.template().isBlank()
                ? templatePath(dir, row.template()) : null;
        lines.remove(removed);
        Files.write(dir.resolve(INDEX), lines, StandardCharsets.UTF_8);
        if (deleteFile != null) {
            Files.deleteIfExists(deleteFile);
        }
    }

    /** Deletes a base class file and its entry in STANDARDS.md. */
    public static void removeBase(Path dir, Path file) throws IOException {
        Path expected = dir.toAbsolutePath().normalize().resolve("basis");
        Path selected = file.toAbsolutePath().normalize();
        boolean safe = selected.startsWith(expected) && !selected.equals(expected)
                && selected.getFileName().toString().endsWith(".java");
        for (Path ancestor = selected; ancestor != null && ancestor.startsWith(expected);
                ancestor = ancestor.getParent()) {
            safe &= !Files.isSymbolicLink(ancestor);
        }
        if (!safe) {
            throw new IllegalArgumentException("Ungültiger Pfad für eine Projektvorlage: " + file);
        }
        Files.deleteIfExists(selected);
        List<String> lines = new ArrayList<>(Files.readAllLines(dir.resolve(INDEX), StandardCharsets.UTF_8));
        if (lines.removeIf(l -> l.startsWith("- `basis/" + file.getFileName() + "`"))) {
            Files.write(dir.resolve(INDEX), lines, StandardCharsets.UTF_8);
        }
    }

    /** {header line, last table line} of the table under "## Artefakte"; null if there is none. */
    private static int[] artefactTable(List<String> lines) {
        boolean in = false;
        int header = -1;
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i).strip();
            if (l.startsWith("## ")) {
                if (header >= 0) {
                    break;
                }
                in = l.toLowerCase(Locale.ROOT).startsWith("## artefakte");
            } else if (in && l.startsWith("|")) {
                if (header < 0) {
                    header = i;
                }
                last = i;
            }
        }
        return header < 0 || last < header + 1 ? null : new int[]{header, last};
    }

    private static int indexOfHeader(List<String> header, String name) {
        int exact = header.indexOf(name);
        if (exact >= 0) {
            return exact;
        }
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).startsWith(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String rowLine(String headerLine, Row row, int existingRows) {
        StringBuilder out = new StringBuilder("|");
        for (String h : cells(headerLine)) {
            String key = h.toLowerCase(Locale.ROOT);
            String v = switch (key) {
                case "nr" -> String.valueOf(existingRows + 1);
                case "phase" -> row.phase();
                case "artefakt" -> row.kind();
                case "klasse" -> quoted(row.className());
                case "package" -> quoted(row.pkg());
                case "pfad" -> quoted(row.path());
                case "klassen-annotationen" -> row.annotations().isEmpty() ? "–" : row.annotations();
                case "hinweise" -> row.notes();
                default -> key.startsWith("vorlage") ? quoted(row.template())
                        : key.startsWith("basisklasse") ? (row.supers().isEmpty() ? "–" : quoted(row.supers())) : "";
            };
            out.append(' ').append(v.replace("|", "\\|")).append(" |");
        }
        return out.toString();
    }

    private static String quoted(String s) {
        return s.isEmpty() ? "–" : "`" + s + "`";
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

    /** Exact entity stems (rather than substring containment) prevent Vertrag/Vertragspartner collisions. */
    private static Map<String, Group> group(JavaCatalog catalog) {
        Map<String, Group> groups = new TreeMap<>();
        List<JavaCatalog.Type> orphans = new ArrayList<>();
        for (JavaCatalog.Type type : catalog.files()) {
            JavaFile f = new JavaFile(type.path(), type.rel(), type.name(), type.pkg(), type.code());
            if (type.isAbstract() || declares(f, "enum")) {
                continue;
            }
            JavaCatalog.Recognition recognition = JavaCatalog.recognise(type);
            if (recognition == null) {
                recognition = JavaCatalog.roleBeforeVariant(type);
            }
            DataStructure structure = Conventions.dataStructure(type.name(), type.lexical());
            Kind kind = structure == null ? logicKind(f) : Kind.of(structure.kind());
            if (kind == null && type.name().matches(".*(?:JobConfig|StepConfig|BatchConfig|Configuration|Config|Tasklet|Listener)(?:Bestand|Schwebe)?")) {
                kind = Kind.KONFIGURATION;
            }
            if (kind == null) {
                continue;
            }
            if (recognition == null) {
                orphans.add(type);
                continue;
            }
            String key = recognition.entity().toLowerCase(Locale.ROOT);
            String name = recognition.entity();
            groups.computeIfAbsent(key, k -> new Group(name))
                    .artefacts.computeIfAbsent(kind, k -> new ArrayList<>()).add(f);
        }

        // A class named only Mapper/Reader in an entity-specific package belongs to that entity.
        // Exact package segment equality is required; substring matches are forbidden.
        for (JavaCatalog.Type type : orphans) {
            JavaFile f = new JavaFile(type.path(), type.rel(), type.name(), type.pkg(), type.code());
            Kind kind = logicKind(f);
            if (kind == null) {
                continue;
            }
            String[] segments = type.pkg().toLowerCase(Locale.ROOT).split("\\.");
            List<String> matches = groups.keySet().stream()
                    .filter(key -> java.util.Arrays.asList(segments).contains(key)).toList();
            if (matches.size() == 1) {
                groups.get(matches.getFirst()).artefacts.computeIfAbsent(kind, k -> new ArrayList<>()).add(f);
            }
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
