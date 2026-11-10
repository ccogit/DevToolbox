package de.devtoolbox.tools.codecollector;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.OutputDirectory;
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
    public static final String TERMS = Keys.TERMS;
    public static final String PHASES = Keys.PHASES;
    public static final String DDL = Keys.DDL;
    public static final String TARGET_MODEL = Keys.TARGET_MODEL;

    /** How deep nested data structures are followed. */
    private static final int MAX_MODEL_DEPTH = 5;
    private static final String DEFAULT_EXTENSIONS = "java,xml,sql,properties,yml,yaml";
    private static final String DEFAULT_EXCLUDES = String.join(",", SourceTree.DEFAULT_EXCLUDES);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Code-Sammler (Referenzprojekt → Markdown)";
    }

    @Override
    public List<Param> params() {
        return List.of(
                Param.directory(Keys.BASE, "Referenzprojekt (Code)", true,
                        "Code der bereits umgesetzten Migration, die als Vorlage dient – wird rekursiv durchsucht"),
                Param.text(TERMS, "Fachliche Entität", "", true,
                        "Name zuerst, dann Synonyme als Suchbegriffe, kommagetrennt, z. B. Risiko, Risk, Wagnis"),
                Param.directory(DDL, "DDL der Altdatenbank", false,
                        "Optional: Datei oder Ordner mit .ddl/.sql – primäre Quelle der Attribute (sonst TOs/POs/Entities)"),
                Param.directory(TARGET_MODEL, "Zielmodell (JSON je Entität)", false,
                        "Pflicht bei Phase Process: Ordner mit einer JSON-Datei je Entität – liefert die Zielattribute"),
                Param.choice(Keys.SCOPE, "Umfang", "references",
                        "Referenzen: Projektklassen, die Treffer direkt verwenden (z. B. DTOs ohne den Begriff)",
                        new Option("hits", "Nur Klassen mit dem Begriff"),
                        new Option("references", "Klassen mit dem Begriff + referenzierte Klassen")).advanced(),
                Param.multi(PHASES, "Betrachtete Phasen", "READ,PROCESS,WRITE",
                        "Treffer anderer Phasen entfallen; Modell, Datenzugriff, Konfiguration bleiben immer",
                        new Option("READ", "Read"), new Option("PROCESS", "Process"), new Option("WRITE", "Write")),
                Param.flag(Keys.IGNORE_DECLARATIONS, "Package/Import ignorieren", true,
                        "Begriff nur im Package-Namen/Ordnerpfad (z. B. de.vers.risk) ist kein Treffer").advanced(),
                Param.flag(Keys.COMPACT, "Kompakt", false, "Imports und Lizenzköpfe weglassen (spart Tokens)").advanced(),
                Param.flag(Keys.TESTS, "Tests einbeziehen", false, "src/test mit durchsuchen").advanced(),
                Param.text(Keys.EXTENSIONS, "Dateiendungen", DEFAULT_EXTENSIONS, false, "Kommagetrennt").advanced(),
                Param.text(Keys.EXCLUDE, "Ordner ausschließen", DEFAULT_EXCLUDES, false, "Ordnernamen, kommagetrennt").advanced(),
                Param.directory(Keys.OUTPUT, "Ausgabeordner", false, "Leer = Downloads").advanced());
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Path base = config.path(Keys.BASE);
        if (base == null || !Files.isDirectory(base)) {
            throw new IllegalArgumentException("Referenzprojekt existiert nicht: " + config.text(Keys.BASE));
        }
        List<String> terms = config.list(TERMS);
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Die fachliche Entität fehlt.");
        }
        Set<Phase> phases = phases(config);
        Path targetModel = config.path(TARGET_MODEL);
        if (phases.contains(Phase.PROCESS)) {
            TargetModel.requireFolder(targetModel);
        }
        boolean withReferences = config.text(Keys.SCOPE).equals("references");
        Path outDir = OutputDirectory.of(config);
        Settings settings = new Settings(terms, lowerSet(config.list(Keys.EXTENSIONS)), lowerSet(config.list(Keys.EXCLUDE)),
                config.flag(Keys.TESTS), config.flag(Keys.IGNORE_DECLARATIONS), outDir);

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
        String markdown = attributes.markdown();
        if (phases.contains(Phase.PROCESS)) {
            TargetModel.Result target = TargetModel.find(targetModel, terms);
            if (target.file() == null) {
                log.accept("WARNUNG: Keine Zielmodell-Datei für " + terms.getFirst() + " in " + targetModel);
            } else {
                log.accept("Zielattribute: " + target.attributes().size() + " aus " + target.file().getFileName()
                        + (target.otherCandidates().isEmpty() ? "" : " (weitere passende Dateien ignoriert: "
                        + target.otherCandidates().stream().map(p -> p.getFileName().toString())
                        .collect(Collectors.joining(", ")) + ")"));
            }
            markdown = markdown.strip() + "\n\n" + AttributeIndex.targetSection(target, targetModel);
        }

        String phaseNames = phases.stream().map(p -> p.shortName).collect(Collectors.joining(", "));
        Meta meta = new Meta(base, terms, phaseNames, withReferences, scan.scannedFiles(), config.flag(Keys.COMPACT),
                markdown);
        String document = MarkdownWriter.render(meta, entries);

        Files.createDirectories(outDir);
        Path file = outDir.resolve("code-" + slug(terms.getFirst()) + ".md");
        Files.writeString(file, document, StandardCharsets.UTF_8);
        log.accept("Geschrieben: %s (%,d Zeichen, ca. %,d Tokens)".formatted(file, document.length(), document.length() / 4));
        Map<Phase, Long> perPhase = entries.stream()
                .collect(Collectors.groupingBy(Entry::phase, () -> new EnumMap<>(Phase.class), Collectors.counting()));
        log.accept("%d Dateien – %s".formatted(entries.size(), perPhase.entrySet().stream()
                .map(e -> e.getKey().shortName + " " + e.getValue()).collect(Collectors.joining(", "))));
        return List.of(file);
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

    private static Entry entry(Path base, Path file, String content, List<Integer> lines, String reason) {
        String rel = base.relativize(file).toString().replace('\\', '/');
        return new Entry(rel, content, PhaseClassifier.classify(rel, content), lines, reason);
    }

    private static Set<String> lowerSet(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT).replaceFirst("^\\*?\\.", ""))
                .collect(Collectors.toSet());
    }

    public static String slug(String text) {
        return OutputDirectory.slug(text);
    }
}
