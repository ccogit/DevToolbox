package de.devtoolbox.tools.codecollector;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;
import de.devtoolbox.core.Tool;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Entry;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Meta;
import de.devtoolbox.tools.codecollector.ProjectScanner.Hit;
import de.devtoolbox.tools.codecollector.ProjectScanner.Result;
import de.devtoolbox.tools.codecollector.ProjectScanner.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Building block: searches a project for business terms and merges all matching files into Markdown. */
public final class CodeCollectorTool implements Tool {

    public static final String ID = "code-collector";

    /** Keys other jobs (skills) may read from the shared configuration. */
    public static final String TERMS = "terms";
    public static final String PHASES = "phases";
    public static final String DDL = "ddl";

    /** How deep nested data structures are followed. */
    private static final int MAX_MODEL_DEPTH = 5;
    private static final String DEFAULT_EXTENSIONS = "java,xml,sql,properties,yml,yaml";
    private static final String DEFAULT_EXCLUDES = "target,build,out,bin,.git,.idea,.gradle,.mvn,node_modules";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Code-Sammler (fachliche Entität → Markdown)";
    }

    @Override
    public List<Param> params() {
        return List.of(
                Param.directory("base", "Projektverzeichnis", true, "Wurzel, ab der rekursiv gesucht wird"),
                Param.text(TERMS, "Fachliche Entität", "", true,
                        "Name zuerst, dann Synonyme als Suchbegriffe, kommagetrennt, z. B. Risiko, Risk, Wagnis"),
                Param.directory(DDL, "DDL der Altdatenbank", false,
                        "Optional: Datei oder Ordner mit .ddl/.sql – primäre Quelle der Attribute (sonst TOs/POs/Entities)"),
                Param.choice("scope", "Umfang", "references",
                        "Referenzen: Projektklassen, die Treffer direkt verwenden (z. B. DTOs ohne den Begriff)",
                        new Option("hits", "Nur Klassen mit dem Begriff"),
                        new Option("references", "Klassen mit dem Begriff + referenzierte Klassen")),
                Param.multi(PHASES, "Betrachtete Phasen", "READ,PROCESS,WRITE",
                        "Treffer anderer Phasen entfallen; Modell, Datenzugriff, Konfiguration bleiben immer",
                        new Option("READ", "Read"), new Option("PROCESS", "Process"), new Option("WRITE", "Write")),
                Param.flag("ignoreDeclarations", "Package/Import ignorieren", true,
                        "Begriff nur im Package-Namen/Ordnerpfad (z. B. de.vers.risk) ist kein Treffer"),
                Param.text("maxTokens", "Max. Tokens/Datei", "", false,
                        "Leer = eine Datei (für Copilot nötig: Skill + Aufgabe + 1 Code-Datei). Sonst Aufteilung an Dateigrenzen"),
                Param.flag("compact", "Kompakt", false, "Imports und Lizenzköpfe weglassen (spart Tokens)"),
                Param.flag("tests", "Tests einbeziehen", false, "src/test mit durchsuchen"),
                Param.text("extensions", "Dateiendungen", DEFAULT_EXTENSIONS, false, "Kommagetrennt"),
                Param.text("exclude", "Ordner ausschließen", DEFAULT_EXCLUDES, false, "Ordnernamen, kommagetrennt"),
                Param.directory("output", "Ausgabeordner", false, "Leer = Downloads"));
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Path base = config.path("base");
        if (base == null || !Files.isDirectory(base)) {
            throw new IllegalArgumentException("Projektverzeichnis existiert nicht: " + config.text("base"));
        }
        List<String> terms = config.list(TERMS);
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Die fachliche Entität fehlt.");
        }
        Set<Phase> phases = phases(config);
        boolean withReferences = config.text("scope").equals("references");
        int maxTokens = config.number("maxTokens");
        Path outDir = config.path("output") != null ? config.path("output") : defaultDir();
        Settings settings = new Settings(terms, lowerSet(config.list("extensions")), lowerSet(config.list("exclude")),
                config.flag("tests"), config.flag("ignoreDeclarations"), outDir);

        log.accept("Durchsuche " + base + " nach: " + String.join(", ", terms));
        Result scan = ProjectScanner.scan(base, settings);
        log.accept("%,d Dateien durchsucht, %d Treffer.".formatted(scan.scannedFiles(), scan.hits().size()));

        List<Entry> entries = new ArrayList<>();
        List<Hit> keptHits = new ArrayList<>();
        int dropped = 0;
        for (Hit hit : scan.hits()) {
            int n = hit.lines().size();
            String reason = n == 0 ? "Treffer im Dateinamen" : n + (n == 1 ? " Trefferzeile" : " Trefferzeilen");
            Entry entry = entry(base, hit.path(), hit.content(), hit.lines(), reason);
            if (isWanted(entry, phases)) {
                entries.add(entry);
                keptHits.add(hit);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            log.accept(dropped + " Treffer aus nicht gewählten Phasen weggelassen.");
        }
        if (entries.isEmpty()) {
            log.accept("Keine Treffer – keine Datei geschrieben.");
            return List.of();
        }
        if (withReferences) {
            log.accept(addReferences(base, keptHits, scan.typesByFqn(), phases, entries) + " referenzierte Klassen ergänzt.");
        }
        entries.sort(Comparator.comparing(Entry::phase).thenComparing(Entry::relPath));

        AttributeIndex.Result attributes = attributes(config.path(DDL), terms, entries);
        log.accept("Attributverzeichnis: " + attributes.summary());

        String phaseNames = phases.stream().map(p -> p.shortName).collect(Collectors.joining(", "));
        Meta meta = new Meta(base, terms, phaseNames, withReferences, scan.scannedFiles(), config.flag("compact"),
                attributes.markdown());
        List<String> documents = MarkdownWriter.render(meta, entries, maxTokens * 4);

        String stem = "code-" + slug(terms.getFirst());
        Files.createDirectories(outDir);
        deleteOldOutput(outDir, stem);
        List<Path> written = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            String name = documents.size() == 1 ? stem + ".md" : stem + "-teil" + (i + 1) + ".md";
            Path file = outDir.resolve(name);
            Files.writeString(file, documents.get(i), StandardCharsets.UTF_8);
            written.add(file);
            log.accept("Geschrieben: %s (%,d Zeichen, ca. %,d Tokens)"
                    .formatted(file, documents.get(i).length(), documents.get(i).length() / 4));
        }
        Map<Phase, Long> perPhase = entries.stream()
                .collect(Collectors.groupingBy(Entry::phase, () -> new EnumMap<>(Phase.class), Collectors.counting()));
        log.accept("%d Dateien – %s".formatted(entries.size(), perPhase.entrySet().stream()
                .map(e -> e.getKey().shortName + " " + e.getValue()).collect(Collectors.joining(", "))));
        return written;
    }

    /**
     * Adds the project types used by the hits. Hits' direct references of any kind are added once; data
     * structures (TOs, POs, DTOs, entities …) are followed further, so nested structures such as
     * RisikoTO → DeckungTO → SelbstbeteiligungTO arrive complete.
     */
    private static int addReferences(Path base, List<Hit> hits, Map<String, Path> types, Set<Phase> phases,
                                     List<Entry> entries) {
        Set<Path> known = new HashSet<>();
        hits.forEach(h -> known.add(h.path()));
        List<Hit> frontier = hits;
        int added = 0;
        for (int depth = 0; depth < MAX_MODEL_DEPTH && !frontier.isEmpty(); depth++) {
            List<Hit> next = new ArrayList<>();
            for (Map.Entry<Path, Path> ref : ReferenceResolver.resolve(frontier, types).entrySet()) {
                Path path = ref.getKey();
                String content = known.add(path) ? SourceReader.read(path) : null;
                if (content == null) {
                    continue;
                }
                Entry entry = entry(base, path, content, List.of(), "Referenz von " + ref.getValue().getFileName());
                // only types recognised as data structures are followed further
                boolean model = entry.phase() == Phase.MODEL;
                // direct references without a clearer role are almost always DTOs/value objects
                entry = entry.phase() == Phase.OTHER ? entry.withPhase(Phase.MODEL) : entry;
                if ((depth == 0 || model) && isWanted(entry, phases)) {
                    entries.add(entry);
                    added++;
                    if (model) {
                        next.add(new Hit(path, content, List.of()));
                    }
                }
            }
            frontier = next;
        }
        return added;
    }

    /** DDL first (if given and it has matching tables), otherwise the fields of the collected data structures. */
    private static AttributeIndex.Result attributes(Path ddl, List<String> terms, List<Entry> entries) throws IOException {
        if (ddl != null) {
            if (!Files.exists(ddl)) {
                throw new IllegalArgumentException("DDL nicht gefunden: " + ddl);
            }
            Map<DdlReader.Table, String> foundIn = new HashMap<>();
            List<DdlReader.Table> relevant = AttributeIndex.relevantTables(DdlReader.read(ddl), terms, entries, foundIn);
            if (!relevant.isEmpty()) {
                return AttributeIndex.fromDdl(relevant, foundIn);
            }
        }
        return AttributeIndex.fromCode(entries);
    }

    /** Hits of deselected batch phases are dropped; supporting categories are always kept. */
    private static boolean isWanted(Entry entry, Set<Phase> phases) {
        return !entry.phase().batchPhase || phases.contains(entry.phase());
    }

    private static Set<Phase> phases(Config config) {
        Set<Phase> phases = EnumSet.noneOf(Phase.class);
        for (String value : config.list(PHASES)) {
            try {
                Phase phase = Phase.valueOf(value.toUpperCase(Locale.ROOT));
                if (phase.batchPhase) {
                    phases.add(phase);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unbekannte Phase: " + value + " (erlaubt: READ, PROCESS, WRITE)");
            }
        }
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Phase auswählen.");
        }
        return phases;
    }

    /** Removes the files of an earlier run, so no stale part (e.g. teil3 of a former 3-part run) is left behind. */
    private static void deleteOldOutput(Path dir, String stem) throws IOException {
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, stem + "{.md,-teil*.md}")) {
            for (Path file : old) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static Entry entry(Path base, Path file, String content, List<Integer> lines, String reason) {
        String rel = base.relativize(file).toString().replace('\\', '/');
        return new Entry(rel, content, PhaseClassifier.classify(rel, content), lines, reason);
    }

    private static Set<String> lowerSet(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT).replaceFirst("^\\*?\\.", ""))
                .collect(Collectors.toSet());
    }

    public static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9äöüß]+", "-").replaceAll("^-|-$", "");
    }

    private static Path defaultDir() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }
}
