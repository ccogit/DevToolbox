package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.OutputDirectory;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.ProjectLayout;
import de.devtoolbox.core.Texts;
import de.devtoolbox.core.Tool;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;
import de.devtoolbox.tools.codecollector.CodeCollectorTool.Collected;
import de.devtoolbox.tools.codecollector.Phase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Building block for the mapping documentation of an entity. Two documents are filled: the entity's target model
 * JSON (the source the team maintains) and its Confluence table (mapping template). This tool finds both – by
 * default in the designer-services module next to the migration ({@link ProjectLayout}) –, enters what is fixed
 * (specification ticket, status FERTIG), collects the mapping code of the migration (Process phase; Read and data
 * access for the source columns; data structures) and pre-analyses it per attribute, separately for the regular
 * stock and pending changes ("Schwebe"). The mapping rules themselves are written by the AI from the code.
 */
public final class MappingDocTool implements Tool {

    /** Process code, the data structures, and Read/data access for the source columns (RowMapper, SQL). */
    static final Set<Phase> CATEGORIES = EnumSet.of(Phase.READ, Phase.PROCESS, Phase.PERSISTENCE, Phase.MODEL, Phase.OTHER);

    /** Status of a documented attribute and the macro of the specification ticket (Confluence wiki markup). */
    public static final String STATUS_DONE = "{status:colour=Green|title=FERTIG|subtle=true}";

    public static String jiraMacro(String issue) {
        return "{jiraissues:" + issue + "}";
    }

    /**
     * Everything a job needs. {@code json} / {@code template} are null if not found (at least one is present);
     * {@code jsonText} / {@code templateText} are the prefilled contents.
     */
    public record Prepared(MappingJson json, String jsonText, MappingTemplate template, String templateText,
                           String jiraIssue, List<String> attributes, Collected code, MappingAnalysis.Result analysis,
                           Reference reference) {
    }

    /**
     * Mapping code of the reference project – only for comparison, never the basis of the documentation. File
     * boundaries carry the prefix {@value #REFERENCE_PREFIX} so they cannot be mistaken for the migration's files.
     */
    public record Reference(String label, String markdown, MappingAnalysis.Result analysis) {
    }

    public static final String REFERENCE_PREFIX = "REFERENZ/";

    @Override
    public String id() {
        return "mapping-doc";
    }

    @Override
    public String name() {
        return "Mapping-Analyse (Code → Zielmodell-JSON und Mapping-Vorlage)";
    }

    @Override
    public List<Param> params() {
        List<String> searchKeys = List.of(Keys.SCOPE, Keys.IGNORE_DECLARATIONS, Keys.COMPACT, Keys.TESTS,
                Keys.EXTENSIONS, Keys.EXCLUDE, Keys.OUTPUT);
        List<Param> search = new CodeCollectorTool().params().stream().filter(p -> searchKeys.contains(p.key())).toList();
        List<Param> own = List.of(
                Param.directory(Keys.TARGET, "Code des Migrationsprojekts", true,
                        "Root des Migrationsmoduls – dort wird das Mapping (Process-Phase) der Entität analysiert"),
                Param.text(Keys.TERMS, "Fachliche Entität", "", true,
                        "Name zuerst (wie entityTypeName im Zielmodell), dann Synonyme als Suchbegriffe, kommagetrennt"),
                Param.text(Keys.JIRA, "Jira-Ticket (Spezifikation)", "", true,
                        "z. B. HAFT-6554 oder nur 6554 – Präfix aus der Sparte (Haftpflicht: HAFT, Sach: SACH); "
                                + "wird in die Spalte Spezifikation eingetragen"),
                Param.directory(Keys.BASE, "Referenzprojekt (nur Vergleich)", false,
                        "Optional: Code der Referenz-Migration – Abweichungen des Mappings werden als Hinweis vermerkt; "
                                + "Grundlage der Dokumentation ist ausschließlich das Migrationsprojekt"),
                Param.directory(Keys.TARGET_MODEL, "Zielmodell (JSON)", false,
                        "Leer = <parent>-designer-services/doc/migration/properties neben dem Migrationsmodul"),
                Param.directory(Keys.MAPPING_TEMPLATES, "Mapping-Vorlagen (Ordner)", false,
                        "Leer = <parent>-designer-services/resources/documentation neben dem Migrationsmodul"));
        return Stream.concat(own.stream(), search.stream()).toList();
    }

    /** Finds JSON and template, prefills them, collects and analyses the code. Fails with a message for the user. */
    public Prepared prepare(Config config, Consumer<String> log) throws IOException {
        List<String> terms = config.list(Keys.TERMS);
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Die fachliche Entität fehlt.");
        }
        Path root = config.path(Keys.TARGET);
        String sparte = MigrationProject.find(config.text(Keys.PROJECT)).map(MigrationProject::sparte).orElse("");
        String issue = ProjectLayout.jiraIssue(config.text(Keys.JIRA), ProjectLayout.jiraPrefix(sparte, root));
        String expected = ProjectLayout.jiraPrefix(sparte, root);
        if (!expected.isEmpty() && !issue.startsWith(expected + "-")) {
            log.accept("WARNUNG: Jira-Ticket " + issue + " – für diese Sparte beginnen die Tickets mit " + expected + ".");
        }

        Path modelDir = folder(config, Keys.TARGET_MODEL, root, ProjectLayout::targetModel, "Zielmodell", log);
        Path templateDir = folder(config, Keys.MAPPING_TEMPLATES, root, ProjectLayout::mappingTemplates, "Mapping-Vorlagen", log);
        MappingJson json = modelDir == null ? null : MappingJson.find(modelDir, terms);
        MappingTemplate.Found found = templateDir == null ? new MappingTemplate.Found(null, List.of())
                : MappingTemplate.find(templateDir, terms);
        MappingTemplate template = found.template();
        if (json == null && template == null) {
            throw new IllegalArgumentException("Weder Zielmodell-JSON noch Mapping-Vorlage für " + terms.getFirst()
                    + " gefunden (Zielmodell: " + modelDir + ", Vorlagen: " + templateDir + ").");
        }
        log.accept(json == null ? "WARNUNG: Kein Zielmodell-JSON für " + terms.getFirst() + " in " + modelDir
                : "Zielmodell-JSON: " + json.file().getFileName() + " – " + json.entity() + ", "
                + json.attributes().size() + " Attribute");
        log.accept(template == null ? "WARNUNG: Keine Mapping-Vorlage für " + terms.getFirst() + " in " + templateDir
                : "Mapping-Vorlage: " + template.file().getFileName() + " – " + String.join(", ", template.entities())
                + ", " + template.rows().size() + " Attribute"
                + (found.otherCandidates().isEmpty() ? "" : " (weitere passende Vorlagen ignoriert: "
                + String.join(", ", found.otherCandidates()) + ")"));
        if (template != null && (template.mappingColumn() < 0 || template.schwebeColumn() < 0)) {
            log.accept("WARNUNG: Die Vorlage hat keine Spalte " + (template.mappingColumn() < 0 ? "„Mapping“" : "„Schwebe Mapping“")
                    + " – Spalten: " + String.join(", ", template.header()));
        }
        List<String> attributes = json != null ? json.attributes() : template.attributes();
        if (json != null && template != null && !new java.util.HashSet<>(json.attributes()).equals(new java.util.HashSet<>(template.attributes()))) {
            log.accept("WARNUNG: Attribute von JSON und Vorlage weichen ab – maßgeblich ist das JSON.");
        }

        // fixed for every attribute: the specification ticket and the status
        String jsonText = json == null ? null
                : json.withAll(Map.of("specification", jiraMacro(issue), "status", STATUS_DONE)).text();
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("spezifikation", jiraMacro(issue));
        columns.put("status", STATUS_DONE);
        String templateEntity = json == null ? terms.getFirst() : json.entity();
        if (template != null && template.entities().stream().noneMatch(e -> e.equalsIgnoreCase(templateEntity))) {
            if (template.entities().size() == 1) {
                templateEntity = template.entities().getFirst();
            } else {
                throw new IllegalArgumentException("Mapping-Vorlage enthält mehrere Entitäten, aber nicht „"
                        + templateEntity + "“: " + String.join(", ", template.entities()));
            }
        }
        String templateText = template == null ? null : template.withColumns(columns, templateEntity);
        log.accept("Spezifikation: " + jiraMacro(issue) + ", Status: FERTIG (grün) – vorab eingetragen");

        Collected code = CodeCollectorTool.collect(root, terms, CATEGORIES, config, "Migrationsprojekt", log);
        if (code.files().isEmpty()) {
            throw new IllegalArgumentException("Im Code des Migrationsprojekts gibt es keine Treffer für "
                    + String.join(", ", terms) + " – Suchbegriffe prüfen.");
        }
        if (code.files().stream().noneMatch(f -> f.phase() == Phase.PROCESS)) {
            log.accept("WARNUNG: Keine Klasse der Process-Phase für " + terms.getFirst() + " gefunden – ist das Mapping "
                    + "schon implementiert? Suchbegriffe prüfen.");
        }
        MappingAnalysis.Result analysis = MappingAnalysis.analyse(code.files(), attributes);
        log.accept("Voranalyse: %d von %d Attributen mit Fundstelle, %d mit Schwebe-Bezug, %d Schwebe-Stellen im Process-Code"
                .formatted(analysis.attributesFound(), attributes.size(), analysis.attributesWithSchwebe(),
                        analysis.schwebeLines().size()));
        return new Prepared(json, jsonText, template, templateText, issue, attributes, code, analysis,
                reference(config, root, terms, attributes, log));
    }

    /** The Process code of the reference project for the same entity, or null if none is given or found. */
    private static Reference reference(Config config, Path root, List<String> terms, List<String> attributes,
                                       Consumer<String> log) throws IOException {
        Path base = config.path(Keys.BASE);
        if (base == null) {
            return null;
        }
        if (!Files.isDirectory(base) || (root != null && base.toAbsolutePath().normalize()
                .equals(root.toAbsolutePath().normalize()))) {
            log.accept("Referenzprojekt ignoriert (fehlt oder ist das Migrationsprojekt selbst): " + base);
            return null;
        }
        Collected ref = CodeCollectorTool.collect(base, terms, EnumSet.of(Phase.PROCESS), config, "Referenzprojekt", log);
        if (ref.files().isEmpty()) {
            log.accept("Referenzprojekt: kein Mapping-Code für " + terms.getFirst() + " – kein Abgleich.");
            return null;
        }
        MappingAnalysis.Result analysis = MappingAnalysis.analyse(ref.files(), attributes);
        String label = MigrationProject.find(config.text(Keys.PROJECT)).map(MigrationProject::referenceLabel)
                .filter(l -> !l.isBlank()).orElse(base.getFileName().toString());
        log.accept("Referenzprojekt " + label + ": " + ref.files().size() + " Dateien der Process-Phase zum Abgleich, "
                + analysis.attributesFound() + " Attribute mit Fundstelle");
        String markdown = ref.markdown()
                .replace("===== BEGIN FILE: ", "===== BEGIN FILE: " + REFERENCE_PREFIX)
                .replace("===== END FILE: ", "===== END FILE: " + REFERENCE_PREFIX);
        return new Reference(label, markdown, analysis);
    }

    /** The configured folder, else the one derived from root; null (with a note) if neither exists. */
    private static Path folder(Config config, String key, Path root, Function<Path, Path> derive, String label,
                               Consumer<String> log) {
        Path configured = config.path(key);
        if (configured != null) {
            if (!Files.isDirectory(configured)) {
                throw new IllegalArgumentException(label + "-Ordner nicht gefunden: " + configured);
            }
            return configured;
        }
        Path derived = derive.apply(root);
        if (derived != null && Files.isDirectory(derived)) {
            log.accept(label + ": " + derived + " (aus dem Migrationsmodul abgeleitet)");
            return derived;
        }
        log.accept(label + ": nicht angegeben und nicht ableitbar"
                + (derived == null ? " (kein Modul *" + ProjectLayout.DESIGNER_SUFFIX + " neben " + root + ")"
                : " (" + derived + " fehlt)"));
        return null;
    }

    /** Standalone use: writes the prefilled documents and the analysis with the code into the output folder. */
    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Prepared p = prepare(config, log);
        String slug = OutputDirectory.slug(config.list(Keys.TERMS).getFirst());
        Path out = OutputDirectory.of(config);
        Files.createDirectories(out);
        Path documents = out.resolve(documentsFileName(slug));
        Files.writeString(documents, documents(p), StandardCharsets.UTF_8);
        Path analysis = out.resolve("mapping-analyse-" + slug + ".md");
        Files.writeString(analysis, "# Mapping-Analyse: " + config.list(Keys.TERMS).getFirst() + "\n\n"
                + MappingAnalysis.markdown(p.analysis()) + "\n" + p.code().markdown(), StandardCharsets.UTF_8);
        return List.of(documents, analysis);
    }

    public static String documentsFileName(String slug) {
        return "vorlagen-mapping-" + slug + ".md";
    }

    /** The prefilled documents, each as a file block named like the result file Copilot returns. */
    public static String documents(Prepared p) {
        StringBuilder md = new StringBuilder("# Auszufüllende Dokumente\n\n")
                .append("Spezifikation (Jira-Ticket) und Status sind bereits eingetragen. Auszufüllen sind nur ")
                .append("die Mapping-Regeln für Bestand und Schwebe. Jede Datei steht zwischen ")
                .append("`===== BEGIN FILE: <Name> =====` und `===== END FILE: <Name> =====`; der Name ist der ")
                .append("Dateiname des Ergebnisses.\n");
        List<String> names = new ArrayList<>(resultFileNames(p));
        if (p.json() != null) {
            md.append("\n## Zielmodell-JSON (Quelle der Dokumentation)\n\n")
                    .append(Texts.fileBlock(names.removeFirst(), "json", p.jsonText().strip()));
        }
        if (p.template() != null) {
            md.append("\n## Confluence-Tabelle (Mapping-Vorlage, Wiki-Markup)\n\n")
                    .append(Texts.fileBlock(names.removeFirst(), "text", p.templateText().strip()));
        }
        return md.toString();
    }

    /** Names of the files Copilot returns: the JSON under its original name, the table as .txt/.md. */
    public static List<String> resultFileNames(Prepared p) {
        List<String> names = new ArrayList<>();
        if (p.json() != null) {
            names.add(p.json().file().getFileName().toString());
        }
        if (p.template() != null) {
            String name = p.template().file().getFileName().toString();
            int dot = name.lastIndexOf('.');
            String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            names.add(ext.equals("md") || ext.equals("txt") ? name : (dot < 0 ? name : name.substring(0, dot)) + ".txt");
        }
        return names;
    }
}
