package de.devtoolbox.tools.codecollector;

import de.devtoolbox.core.*;
import de.devtoolbox.core.Param.Option;
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
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Building block: searches a project for business terms and merges all matching files into Markdown.
 */
public final class CodeCollectorTool implements Tool {

    public static final String ID = "code-collector";

    /**
     * Keys other jobs (skills) may read from the shared configuration.
     */
    public static final String TERMS = Keys.TERMS;
    public static final String PHASES = Keys.PHASES;
    public static final String DDL = Keys.DDL;
    public static final String TARGET_MODEL = Keys.TARGET_MODEL;
    /**
     * Share of the Copilot limit for the code file (Startwert, zu kalibrieren).
     */
    static final double CODE_SHARE = 0.8;
    /**
     * How deep nested data structures are followed.
     */
    private static final int MAX_MODEL_DEPTH = 5;
    private static final String DEFAULT_EXTENSIONS = "java,xml,sql,properties,yml,yaml";
    private static final String DEFAULT_EXCLUDES = String.join(",", SourceTree.DEFAULT_EXCLUDES);
    /**
     * How many file names a log line lists at most.
     */
    private static final int LISTED_FILES = 10;
    /**
     * How many of the largest files the size report and the suggestions name.
     */
    private static final int LARGEST = 5;

    /**
     * "Referenzprojekt", for a repository with what was checked out (the folder is then only the local clone).
     */
    public static String sourceLabel(String label, CodeSource.Resolved source) {
        return source == null || source.origin() == null ? label : label + " (Bitbucket: " + source.origin() + ")";
    }

    /**
     * The files that may be left out, in the order they are left out (B4): references taken as data structures
     * only for lack of a clearer role, configuration references (both largest first), data structure references
     * from the second level on (deepest first), then the hits that only mention the entity (configuration first,
     * then data structures and others, fewest hit lines first) and last the hits named after the entity – its own
     * entities, TOs and helpers are the core and go last. Protected – never left out – are the files of the selected
     * phases, data access and the data structures referenced directly by a hit.
     */
    private static List<Omission> omissionOrder(List<Entry> entries, List<String> terms) {
        java.util.function.Predicate<Entry> core = e -> Excerpt.namedAfter(e.relPath(), terms);
        Comparator<Entry> largestFirst = Comparator.comparingInt((Entry e) -> e.content().length()).reversed();
        Comparator<Entry> fewestHitLines = Comparator.comparingInt((Entry e) -> e.hitLines().size())
                .thenComparing(largestFirst);
        List<Omission> order = new ArrayList<>();
        add(order, entries, e -> e.isReference() && e.phase() == Phase.MODEL && e.origin() == Phase.OTHER, largestFirst,
                _ -> "Referenz ohne erkennbare Rolle (als Modell eingestuft)");
        add(order, entries, e -> e.isReference() && e.phase() == Phase.CONFIG, largestFirst,
                _ -> "Referenz der Kategorie Konfiguration");
        add(order, entries, e -> e.isReference() && e.phase() == Phase.MODEL && e.origin() == Phase.MODEL
                        && e.depth() >= 2, Comparator.comparingInt(Entry::depth).reversed().thenComparing(largestFirst),
                e -> "Modell-Referenz der Ebene " + e.depth());
        add(order, entries, e -> !e.isReference() && !core.test(e) && e.phase() == Phase.CONFIG, fewestHitLines,
                e -> "Treffer der Kategorie Konfiguration mit " + hitLines(e));
        add(order, entries, e -> !e.isReference() && !core.test(e)
                        && (e.phase() == Phase.MODEL || e.phase() == Phase.OTHER), fewestHitLines,
                e -> "Treffer der Kategorie " + e.phase().shortName + " mit " + hitLines(e));
        add(order, entries, e -> !e.isReference() && core.test(e)
                        && (e.phase() == Phase.CONFIG || e.phase() == Phase.MODEL || e.phase() == Phase.OTHER),
                largestFirst, e -> "nach der Entität benannte Datei der Kategorie " + e.phase().shortName
                        + " (zuletzt weggelassen)");
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
        Settings settings = new Settings(terms, extensionSet(config.list(Keys.EXTENSIONS)), folderSet(config.list(Keys.EXCLUDE)),
                config.flag(Keys.TESTS), config.flag(Keys.IGNORE_DECLARATIONS), outDir, config.flag(Keys.IGNORE_COMMENTS),
                config.list(Keys.EXCLUDE_FILES));
        log.accept("Durchsuche " + base + " nach: " + String.join(", ", terms));
        Result scan = ProjectScanner.scan(base, settings);
        log.accept("%,d Dateien durchsucht (davon %,d Java), %d Treffer.".formatted(scan.scannedFiles(),
                scan.javaFiles(), scan.hits().size()));
        if (!scan.unreadable().isEmpty()) {
            log.accept("WARNUNG: %d Dateien/Ordner nicht lesbar – ihr Inhalt fehlt in der Suche: %s".formatted(
                    scan.unreadable().size(), scan.unreadable().stream().limit(LISTED_FILES)
                            .map(p -> base.relativize(p).toString()).collect(Collectors.joining(", "))
                            + (scan.unreadable().size() > LISTED_FILES ? ", …" : "")));
        }
        if (scan.javaFiles() == 0) {
            log.accept("WARNUNG: Keine einzige .java-Datei durchsucht – ist " + base + " wirklich der Code-Ordner? "
                    + "Prüfe auch „Ordner ausschließen“ und „Dateiendungen“.");
        }
        if (scan.excludedFiles() > 0) {
            log.accept(scan.excludedFiles() + " Dateien über „Dateien ausschließen“ ausgeschlossen (weder Treffer noch Referenz).");
        }
        if (!scan.commentOnly().isEmpty()) {
            log.accept("%d Dateien enthalten den Begriff nur in Kommentaren und sind kein Treffer: %s".formatted(
                    scan.commentOnly().size(), scan.commentOnly().stream()
                            .sorted(Comparator.comparing((Path f) -> f.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER))
                            .limit(LISTED_FILES).map(f -> f.getFileName().toString()).collect(Collectors.joining(", "))
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
        int excerpts = 0;
        for (Hit scanned : scan.hits()) {
            Hit hit = scanned;
            int n = hit.lines().size();
            String reason = n == 0 ? "Treffer im Dateinamen" : n + (n == 1 ? " Trefferzeile" : " Trefferzeilen");
            Entry entry = entry(base, hit.path(), hit.content(), hit.lines(), reason);
            if (keep.contains(entry.phase())) {
                // a class that only mentions the entity is cut down to the members that do – and only those
                // members decide which classes count as referenced
                Excerpt.Result excerpt = entry.relPath().endsWith(".java") && !Excerpt.namedAfter(entry.relPath(), terms)
                        ? Excerpt.of(hit.content(), hit.lines()) : null;
                if (excerpt != null) {
                    entry = entry.withExcerpt(excerpt.content(), reason + " · " + MarkdownWriter.EXCERPT + ": "
                            + excerpt.kept() + " von " + excerpt.total() + " Membern");
                    hit = new Hit(hit.path(), excerpt.content(), hit.lines());
                    excerpts++;
                }
                entries.add(entry);
                keptHits.add(hit);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            log.accept(dropped + " Treffer aus nicht gewählten Phasen weggelassen.");
        }
        if (excerpts > 0) {
            log.accept(excerpts + " Klassen erwähnen die Entität nur am Rand – als Auszug übernommen (nur Member mit "
                    + "Suchbegriff und die von ihnen verwendeten): " + entries.stream().filter(Entry::isExcerpt)
                    .map(e -> fileName(e.relPath())).limit(LISTED_FILES).collect(Collectors.joining(", "))
                    + (excerpts > LISTED_FILES ? ", …" : ""));
        }
        if (!entries.isEmpty() && config.text(Keys.SCOPE).equals("references")) {
            log.accept(addReferences(base, keptHits, scan.typesByFqn(), keep, entries) + " referenzierte Klassen ergänzt.");
        }
        entries.sort(Comparator.comparing(Entry::phase).thenComparing(Entry::relPath));
        if (!entries.isEmpty() && entries.stream().noneMatch(e -> e.relPath().endsWith(".java"))) {
            log.accept("WARNUNG: Keine Java-Klasse enthält „" + String.join("“, „", terms) + "“ – die Code-Datei "
                    + "enthält nur " + entries.stream().map(e -> fileName(e.relPath())).limit(LISTED_FILES)
                    .collect(Collectors.joining(", ")) + ". Ordner des Referenzprojekts und Suchbegriffe prüfen.");
        }
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
        Map<String, Set<String>> users = new HashMap<>();
        List<Hit> frontier = hits;
        int added = 0;
        for (int depth = 0; depth < MAX_MODEL_DEPTH && !frontier.isEmpty(); depth++) {
            List<Hit> next = new ArrayList<>();
            for (Map.Entry<Path, List<Path>> ref : ReferenceResolver.resolve(frontier, types).entrySet()) {
                Path path = ref.getKey();
                Set<String> usedBy = users.computeIfAbsent(relative(base, path), _ -> new LinkedHashSet<>());
                ref.getValue().forEach(user -> usedBy.add(relative(base, user)));
                String content = known.add(path) ? SourceReader.read(path) : null;
                if (content == null) {
                    continue;
                }
                Entry entry = entry(base, path, content, List.of(), "Referenz von " + ref.getValue().getFirst().getFileName())
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
        entries.replaceAll(e -> e.isReference() ? e.withUsedBy(List.copyOf(users.getOrDefault(e.relPath(), Set.of()))) : e);
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
            List<DdlReader.Table> all = DdlReader.read(ddl);
            AttributeIndex.Sources sources = AttributeIndex.fromDdl(all, terms, tables, entries);
            if (sources != null) {
                return sources;
            }
            String note = all.isEmpty()
                    ? "DDL angegeben, enthält aber kein CREATE TABLE / CREATE VIEW: " + ddl
                    : "DDL mit %d Tabellen angegeben, aber keine passt zu „%s“ – weder Name noch Bezeichnung/Kommentar "
                    .formatted(all.size(), String.join("“, „", terms)) + "enthält einen Suchbegriff, und der Code "
                    + "verwendet keine davon";
            log.accept("WARNUNG: " + note + ". Attribute stattdessen aus den Datenstrukturen im Code."
                    + (all.isEmpty() ? "" : " Bitte unter „Tabellen der Entität (DDL)“ die passenden Tabellen wählen. "
                    + "Vorhanden: " + tableList(all)));
            return AttributeIndex.fromCode(entries).withNote(note);
        } else if (!tables.isEmpty()) {
            log.accept("Hinweis: „Tabellen der Entität“ wirkt nur mit DDL (Struktur der Altdaten) – ohne DDL ignoriert.");
        }
        return AttributeIndex.fromCode(entries);
    }

    /**
     * "T91AV5 (Sach-Vertragskopf), …" – at most 15 tables.
     */
    private static String tableList(List<DdlReader.Table> tables) {
        int max = 15;
        String list = tables.stream().limit(max)
                .map(t -> t.name() + (t.description().isEmpty() ? "" : " (" + t.description() + ")"))
                .collect(Collectors.joining(", "));
        return list + (tables.size() > max ? ", … (" + (tables.size() - max) + " weitere)" : "");
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

    private static String relative(Path base, Path file) {
        return base.relativize(file).toString().replace('\\', '/');
    }

    /**
     * Removes, repeatedly, the references none of whose users is delivered any more.
     *
     * @return the removed entries
     */
    private static List<Entry> dropUnused(List<Entry> kept) {
        List<Entry> removed = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            Set<String> present = kept.stream().map(Entry::relPath).collect(Collectors.toSet());
            List<Entry> unused = kept.stream().filter(e -> e.isReference() && !e.usedBy().isEmpty()
                    && e.usedBy().stream().noneMatch(present::contains)).toList();
            changed = kept.removeAll(unused);
            removed.addAll(unused);
        }
        return removed;
    }

    private static Entry entry(Path base, Path file, String content, List<Integer> lines, String reason) {
        String rel = relative(base, file);
        return new Entry(rel, content, PhaseClassifier.classify(rel, content), lines, reason);
    }

    /**
     * Extensions without a leading "*." or ".": "*.java", ".java" and "java" are the same.
     */
    static Set<String> extensionSet(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT).replaceFirst("^\\*?\\.", ""))
                .collect(Collectors.toSet());
    }

    /**
     * Folder names as typed: the leading dot of ".idea" or ".git" is part of the name and must stay.
     */
    static Set<String> folderSet(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT).replace('\\', '/').replaceAll("^/+|/+$", ""))
                .filter(v -> !v.isEmpty()).collect(Collectors.toSet());
    }

    public static String slug(String text) {
        return OutputDirectory.slug(text);
    }

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
                        "Code der bereits umgesetzten Migration, die als Vorlage dient – wird rekursiv durchsucht. "
                                + "Ordner oder Bitbucket-Adresse (Link aus dem Browser, auch eines Unterordners und "
                                + "Branches) – wird dann geklont"),
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
        CodeSource.Resolved source = CodeSource.resolve(config.text(Keys.BASE), log);
        Path base = source == null ? null : source.folder();
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
            return Output.none("keine Treffer für „" + String.join("“, „", terms) + "“ in " + base
                    + " (gewählte Phasen: " + phases.stream().map(p -> p.shortName).collect(Collectors.joining(", "))
                    + "). Suchbegriffe/Synonyme, Referenzprojekt und Phasen prüfen.");
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
        List<Entry> unused = new ArrayList<>();
        Meta meta = new Meta(sourceLabel("Referenzprojekt", source), base, terms, phaseNames, withReferences, scan.scannedFiles(),
                config.flag(Keys.COMPACT), true, omitted);
        String document = MarkdownWriter.render(meta, kept);
        if (budget > 0 && document.length() / 4 > budget) {
            // B4: leave out unprotected files in a fixed order until the code file fits its budget
            for (Omission o : omissionOrder(entries, terms)) {
                if (document.length() / 4 <= budget) {
                    break;
                }
                if (!kept.remove(o.entry())) {
                    continue; // already gone together with the only file that used it
                }
                omitted.add(new Omitted(o.entry().relPath(), o.entry().phase(), o.reason()));
                unused.addAll(dropUnused(kept));
                document = MarkdownWriter.render(meta, kept);
            }
            log.accept("Budget der Code-Datei: ca. %,d Tokens (%d %% des Copilot-Limits) – %d Dateien nicht enthalten:"
                    .formatted(budget, Math.round(CODE_SHARE * 100), omitted.size()));
            omitted.forEach(o -> log.accept("- nicht enthalten: " + o.relPath() + " (" + o.phase().shortName + ") – "
                    + o.reason()));
            if (!unused.isEmpty()) {
                log.accept(unused.size() + " Referenzen entfallen mit, weil nur nicht enthaltene Dateien sie verwenden: "
                        + unused.stream().map(e -> fileName(e.relPath())).limit(LISTED_FILES)
                        .collect(Collectors.joining(", ")) + (unused.size() > LISTED_FILES ? ", …" : ""));
            }
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
        List<String> candidates = omissionOrder(entries, terms).stream().map(Omission::entry)
                .sorted(Comparator.comparingInt((Entry e) -> e.content().length()).reversed())
                .limit(LARGEST).map(e -> fileName(e.relPath())).toList();
        return new Output(List.of(file), inventory.markdown(), inventory.targetRows(), sources.core().size(),
                largest, candidates, "");
    }

    /**
     * A file that may be left out for the budget, with the reason shown in the list "Nicht enthaltene Dateien".
     */
    private record Omission(Entry entry, String reason) {
    }

    /**
     * A collected file, for tools that analyse the code further.
     */
    public record SourceFile(String relPath, String content, Phase phase) {
    }

    /**
     * Result of {@link #collect}: the files (empty if there was no hit) and the rendered Markdown.
     */
    public record Collected(List<SourceFile> files, String markdown) {
    }

    private record Gathered(List<Entry> entries, Result scan) {
    }
}
