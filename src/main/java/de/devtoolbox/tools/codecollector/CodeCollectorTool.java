package de.devtoolbox.tools.codecollector;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.OutputDirectory;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;
import de.devtoolbox.core.Tool;
import de.devtoolbox.core.Tool.Output;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Entry;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Meta;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Omitted;
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
    /** Share of the Copilot limit for the code file (Startwert, zu kalibrieren). */
    static final double CODE_SHARE = 0.8;
    /** How many file names a log line lists at most. */
    private static final int LISTED_FILES = 10;
    /** How many of the largest files the size report and the suggestions name. */
    private static final int LARGEST = 5;

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
                Param.directory(DDL, "Struktur der Altdaten (DDL)", false,
                        "Optional: Datei oder Ordner mit .ddl/.sql – primäre Quelle der Attribute (sonst TOs/POs/Entities)"),
                Param.text(Keys.TABLES, "Tabellen der Entität (DDL)", "", false,
                        "Leer = Tabellen, deren Name einen Suchbegriff enthält. Bei mehreren Teiltabellen alle nennen."),
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
                Param.flag(Keys.IGNORE_COMMENTS, "Kommentare ignorieren", true,
                        "Begriff nur in Kommentarzeilen (//, /*, *, in .sql --) ist kein Treffer; Dateiname zählt weiter").advanced(),
                Param.text(Keys.EXCLUDE_FILES, "Dateien ausschließen", "", false,
                        "Glob-Muster, kommagetrennt: ohne / gegen den Dateinamen (*Export*), mit / gegen den Pfad (**/framework/**)").advanced(),
                Param.flag(Keys.COMPACT, "Kompakt", true, "Imports und Lizenzköpfe weglassen (spart Tokens)").advanced(),
                Param.flag(Keys.TESTS, "Tests einbeziehen", false, "src/test mit durchsuchen").advanced(),
                Param.text(Keys.EXTENSIONS, "Dateiendungen", DEFAULT_EXTENSIONS, false, "Kommagetrennt").advanced(),
                Param.text(Keys.EXCLUDE, "Ordner ausschließen", DEFAULT_EXCLUDES, false, "Ordnernamen, kommagetrennt").advanced(),
                Param.directory(Keys.OUTPUT, "Ausgabeordner", false, "Leer = Downloads").advanced());
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        return produce(config, log).files();
    }

    /**
     * Writes the code file and returns, for the task, the target lists (Soll-Listen) and their row counts. The code
     * file itself contains no attribute directory.
     */
    @Override
    public Output produce(Config config, Consumer<String> log) throws Exception {
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
        Set<Phase> keep = EnumSet.copyOf(phases);
        for (Phase p : Phase.values()) {
            if (!p.batchPhase) {
                keep.add(p);
            }
        }
        Gathered gathered = gather(base, terms, keep, config, outDir, log);
        List<Entry> entries = gathered.entries();
        if (entries.isEmpty()) {
            log.accept("Keine Treffer – keine Datei geschrieben.");
            return Output.of(List.of());
        }
        Result scan = gathered.scan();

        AttributeIndex.Sources sources = sources(config, terms, entries, log);
        log.accept("Attributverzeichnis: " + sources.summary());
        sources.warnings().forEach(log);
        TargetModel.Result target = null;
        if (phases.contains(Phase.PROCESS)) {
            target = TargetModel.find(targetModel, terms);
            if (target.file() == null) {
                log.accept("WARNUNG: Keine Zielmodell-Datei für " + terms.getFirst() + " in " + targetModel);
            } else {
                log.accept("Zielattribute: " + target.attributes().size() + " von " + target.entity() + " aus "
                        + target.file().getFileName()
                        + (target.otherCandidates().isEmpty() ? "" : " (weitere passende Entitäten ignoriert: "
                        + String.join(", ", target.otherCandidates()) + ")"));
            }
        }
        AttributeIndex.Inventory inventory = AttributeIndex.inventory(sources, phases.contains(Phase.READ),
                phases.contains(Phase.PROCESS), phases.contains(Phase.WRITE), target, targetModel);
        log.accept(inventory.summary());

        String phaseNames = phases.stream().map(p -> p.shortName).collect(Collectors.joining(", "));
        int limit = config.number(Keys.COPILOT_LIMIT);
        int budget = limit <= 0 ? 0 : (int) (limit * CODE_SHARE);
        List<Entry> kept = new ArrayList<>(entries);
        List<Omitted> omitted = new ArrayList<>();
        Meta meta = new Meta("Referenzprojekt", base, terms, phaseNames, withReferences, scan.scannedFiles(),
                config.flag(Keys.COMPACT), true, omitted);
        String document = MarkdownWriter.render(meta, kept);
        if (budget > 0 && document.length() / 4 > budget) {
            // B4: leave out unprotected files in a fixed order until the code file fits its budget
            for (Omission o : omissionOrder(entries)) {
                if (document.length() / 4 <= budget) {
                    break;
                }
                kept.remove(o.entry());
                omitted.add(new Omitted(o.entry().relPath(), o.entry().phase(), o.reason()));
                document = MarkdownWriter.render(meta, kept);
            }
            log.accept("Budget der Code-Datei: ca. %,d Tokens (%d %% des Copilot-Limits) – %d Dateien nicht enthalten:"
                    .formatted(budget, Math.round(CODE_SHARE * 100), omitted.size()));
            omitted.forEach(o -> log.accept("- nicht enthalten: " + o.relPath() + " (" + o.phase().shortName + ") – "
                    + o.reason()));
            if (document.length() / 4 > budget) {
                log.accept("WARNUNG: Die Code-Datei bleibt mit ca. %,d Tokens über ihrem Budget von %,d – übrig sind nur "
                        .formatted(document.length() / 4, budget) + "geschützte Dateien (gewählte Phasen, Datenzugriff, "
                        + "Modell-Referenzen der ersten Ebene).");
            }
        }

        Files.createDirectories(outDir);
        Path file = outDir.resolve("code-" + slug(terms.getFirst()) + ".md");
        Files.writeString(file, document, StandardCharsets.UTF_8);
        log.accept("Geschrieben: %s (%,d Zeichen, ca. %,d Tokens)".formatted(file, document.length(), document.length() / 4));
        Map<Phase, Long> perPhase = kept.stream()
                .collect(Collectors.groupingBy(Entry::phase, () -> new EnumMap<>(Phase.class), Collectors.counting()));
        log.accept("%d Dateien – %s".formatted(kept.size(), perPhase.entrySet().stream()
                .map(e -> e.getKey().shortName + " " + e.getValue()).collect(Collectors.joining(", "))));
        List<String> largest = kept.stream().sorted(Comparator.comparingInt((Entry e) -> e.content().length()).reversed())
                .limit(LARGEST).map(e -> "%s (%,d Zeichen)".formatted(fileName(e.relPath()), e.content().length()))
                .toList();
        List<String> candidates = omissionOrder(entries).stream().map(Omission::entry)
                .sorted(Comparator.comparingInt((Entry e) -> e.content().length()).reversed())
                .limit(LARGEST).map(e -> fileName(e.relPath())).toList();
        return new Output(List.of(file), inventory.markdown(), inventory.targetRows(), sources.core().size(),
                largest, candidates);
    }

    /** A file that may be left out for the budget, with the reason shown in the list "Nicht enthaltene Dateien". */
    private record Omission(Entry entry, String reason) {
    }

    /**
     * The files that may be left out, in the order they are left out (B4): references taken as data structures
     * only for lack of a clearer role, configuration references (both largest first), data structure references
     * from the second level on (deepest first), configuration hits, then data structure and other hits (both fewest
     * hit lines first). Protected – never left out – are the files of the selected phases, data access and the
     * data structures referenced directly by a hit.
     */
    private static List<Omission> omissionOrder(List<Entry> entries) {
        Comparator<Entry> largestFirst = Comparator.comparingInt((Entry e) -> e.content().length()).reversed();
        Comparator<Entry> fewestHitLines = Comparator.comparingInt((Entry e) -> e.hitLines().size())
                .thenComparing(largestFirst);
        List<Omission> order = new ArrayList<>();
        add(order, entries, e -> e.isReference() && e.phase() == Phase.MODEL && e.origin() == Phase.OTHER, largestFirst,
                e -> "Referenz ohne erkennbare Rolle (als Modell eingestuft)");
        add(order, entries, e -> e.isReference() && e.phase() == Phase.CONFIG, largestFirst,
                e -> "Referenz der Kategorie Konfiguration");
        add(order, entries, e -> e.isReference() && e.phase() == Phase.MODEL && e.origin() == Phase.MODEL
                        && e.depth() >= 2, Comparator.comparingInt(Entry::depth).reversed().thenComparing(largestFirst),
                e -> "Modell-Referenz der Ebene " + e.depth());
        add(order, entries, e -> !e.isReference() && e.phase() == Phase.CONFIG, fewestHitLines,
                e -> "Treffer der Kategorie Konfiguration mit " + hitLines(e));
        add(order, entries, e -> !e.isReference() && (e.phase() == Phase.MODEL || e.phase() == Phase.OTHER),
                fewestHitLines,
                e -> "Treffer der Kategorie " + e.phase().shortName + " mit " + hitLines(e));
        return order;
    }

    private static void add(List<Omission> order, List<Entry> entries, java.util.function.Predicate<Entry> stage,
                            Comparator<Entry> sort, java.util.function.Function<Entry, String> reason) {
        entries.stream().filter(stage).sorted(sort)
                .forEach(e -> order.add(new Omission(e, "Budget: " + reason.apply(e) + ", ca. %,d Tokens"
                        .formatted(e.content().length() / 4))));
    }

    private static String hitLines(Entry e) {
        int n = e.hitLines().size();
        return n + (n == 1 ? " Trefferzeile" : " Trefferzeilen");
    }

    private static String fileName(String relPath) {
        return relPath.substring(relPath.lastIndexOf('/') + 1);
    }

    /** A collected file, for tools that analyse the code further. */
    public record SourceFile(String relPath, String content, Phase phase) {
    }

    /** Result of {@link #collect}: the files (empty if there was no hit) and the rendered Markdown. */
    public record Collected(List<SourceFile> files, String markdown) {
    }

    private record Gathered(List<Entry> entries, Result scan) {
    }

    /**
     * For other tools: searches {@code base} for {@code terms} and keeps the files of the categories in {@code keep}
     * (plus referenced classes, if the param "Umfang" says so). Uses the search params of this tool from
     * {@code config} (extensions, excludes, tests, package/import, compact).
     *
     * @param sourceLabel what {@code base} is, for the header, e.g. "Migrationsprojekt"
     */
    public static Collected collect(Path base, List<String> terms, Set<Phase> keep, Config config, String sourceLabel,
                                    Consumer<String> log) throws IOException {
        if (base == null || !Files.isDirectory(base)) {
            throw new IllegalArgumentException(sourceLabel + " (Code) existiert nicht: " + base);
        }
        Gathered gathered = gather(base, terms, keep, config, OutputDirectory.of(config), log);
        if (gathered.entries().isEmpty()) {
            return new Collected(List.of(), "");
        }
        String kept = keep.stream().sorted().map(p -> p.shortName).collect(Collectors.joining(", "));
        Meta meta = new Meta(sourceLabel, base, terms, kept, config.text(Keys.SCOPE).equals("references"),
                gathered.scan().scannedFiles(), config.flag(Keys.COMPACT), false, List.of());
        List<SourceFile> files = gathered.entries().stream()
                .map(e -> new SourceFile(e.relPath(), e.content(), e.phase())).toList();
        return new Collected(files, MarkdownWriter.render(meta, gathered.entries()));
    }

    private static Gathered gather(Path base, List<String> terms, Set<Phase> keep, Config config, Path outDir,
                                   Consumer<String> log) throws IOException {
        Settings settings = new Settings(terms, lowerSet(config.list(Keys.EXTENSIONS)), lowerSet(config.list(Keys.EXCLUDE)),
                config.flag(Keys.TESTS), config.flag(Keys.IGNORE_DECLARATIONS), outDir, config.flag(Keys.IGNORE_COMMENTS),
                config.list(Keys.EXCLUDE_FILES));
        log.accept("Durchsuche " + base + " nach: " + String.join(", ", terms));
        Result scan = ProjectScanner.scan(base, settings);
        log.accept("%,d Dateien durchsucht, %d Treffer.".formatted(scan.scannedFiles(), scan.hits().size()));
        if (scan.excludedFiles() > 0) {
            log.accept(scan.excludedFiles() + " Dateien über „Dateien ausschließen“ ausgeschlossen (weder Treffer noch Referenz).");
        }
        if (!scan.commentOnly().isEmpty()) {
            log.accept("%d Dateien enthalten den Begriff nur in Kommentaren und sind kein Treffer: %s".formatted(
                    scan.commentOnly().size(), scan.commentOnly().stream().limit(LISTED_FILES)
                            .map(f -> f.getFileName().toString()).collect(Collectors.joining(", "))
                            + (scan.commentOnly().size() > LISTED_FILES ? ", …" : "")));
        }
        if (!scan.ambiguousTypes().isEmpty()) {
            log.accept("WARNUNG: " + scan.ambiguousTypes().size()
                    + " mehrdeutige Java-Typen (mehrere Module) – automatische Referenzauflösung übersprungen: "
                    + String.join(", ", scan.ambiguousTypes().stream().limit(5).toList()));
        }

        List<Entry> entries = new ArrayList<>();
        List<Hit> keptHits = new ArrayList<>();
        int dropped = 0;
        for (Hit hit : scan.hits()) {
            int n = hit.lines().size();
            String reason = n == 0 ? "Treffer im Dateinamen" : n + (n == 1 ? " Trefferzeile" : " Trefferzeilen");
            Entry entry = entry(base, hit.path(), hit.content(), hit.lines(), reason);
            if (keep.contains(entry.phase())) {
                entries.add(entry);
                keptHits.add(hit);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            log.accept(dropped + " Treffer aus nicht gewählten Phasen weggelassen.");
        }
        if (!entries.isEmpty() && config.text(Keys.SCOPE).equals("references")) {
            log.accept(addReferences(base, keptHits, scan.typesByFqn(), keep, entries) + " referenzierte Klassen ergänzt.");
        }
        entries.sort(Comparator.comparing(Entry::phase).thenComparing(Entry::relPath));
        return new Gathered(entries, scan);
    }

    /**
     * Adds the project types used by the hits. Hits' direct references of any kind are added once; data
     * structures (TOs, POs, DTOs, entities …) are followed further, so nested structures such as
     * RisikoTO → DeckungTO → SelbstbeteiligungTO arrive complete.
     */
    private static int addReferences(Path base, List<Hit> hits, Map<String, Path> types, Set<Phase> keep,
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
                Entry entry = entry(base, path, content, List.of(), "Referenz von " + ref.getValue().getFileName())
                        .withDepth(depth + 1);
                // only types recognised as data structures are followed further
                boolean model = entry.phase() == Phase.MODEL;
                // direct references without a clearer role are almost always DTOs/value objects
                entry = entry.phase() == Phase.OTHER ? entry.withPhase(Phase.MODEL) : entry;
                if ((depth == 0 || model) && keep.contains(entry.phase())) {
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

    /**
     * DDL first (if given and it has relevant tables), otherwise the fields of the collected data structures.
     * "Tabellen der Entität" select the core tables; without DDL they have no effect.
     */
    private static AttributeIndex.Sources sources(Config config, List<String> terms, List<Entry> entries,
                                                  Consumer<String> log) throws IOException {
        Path ddl = config.path(DDL);
        List<String> tables = config.list(Keys.TABLES);
        if (ddl != null) {
            if (!Files.exists(ddl)) {
                throw new IllegalArgumentException("DDL nicht gefunden: " + ddl);
            }
            AttributeIndex.Sources sources = AttributeIndex.fromDdl(DdlReader.read(ddl), terms, tables, entries);
            if (sources != null) {
                return sources;
            }
        } else if (!tables.isEmpty()) {
            log.accept("Hinweis: „Tabellen der Entität“ wirkt nur mit DDL (Struktur der Altdaten) – ohne DDL ignoriert.");
        }
        return AttributeIndex.fromCode(entries);
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
