package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.OutputDirectory;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Tool;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;
import de.devtoolbox.tools.codecollector.CodeCollectorTool.Collected;
import de.devtoolbox.tools.codecollector.Phase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Building block for the mapping documentation: finds the template of the entity, collects the mapping code of the
 * migration project (Process phase plus the data structures it uses) and pre-analyses it per template attribute,
 * separately for the regular stock and pending changes ("Schwebe").
 */
public final class MappingDocTool implements Tool {

    /** Process code and the data structures it reads and fills; Read, Write, data access and config are left out. */
    static final Set<Phase> CATEGORIES = EnumSet.of(Phase.PROCESS, Phase.MODEL, Phase.OTHER);

    /** Everything a job needs to write the task. */
    public record Prepared(MappingTemplate template, List<String> otherTemplates, Collected code,
                           MappingAnalysis.Result analysis) {
    }

    @Override
    public String id() {
        return "mapping-doc";
    }

    @Override
    public String name() {
        return "Mapping-Analyse (Process-Code → Mapping-Vorlage)";
    }

    @Override
    public List<Param> params() {
        List<String> searchKeys = List.of(Keys.SCOPE, Keys.IGNORE_DECLARATIONS, Keys.COMPACT, Keys.TESTS,
                Keys.EXTENSIONS, Keys.EXCLUDE, Keys.OUTPUT);
        List<Param> search = new CodeCollectorTool().params().stream().filter(p -> searchKeys.contains(p.key())).toList();
        List<Param> own = List.of(
                Param.directory(Keys.TARGET, "Code des Migrationsprojekts", true,
                        "Root des Zielprojekts – dort wird die Process-Phase (Mapping) der Entität analysiert"),
                Param.text(Keys.TERMS, "Fachliche Entität", "", true,
                        "Name zuerst (wie in der Mapping-Vorlage), dann Synonyme als Suchbegriffe, kommagetrennt"),
                Param.directory(Keys.MAPPING_TEMPLATES, "Mapping-Vorlagen (Ordner)", true,
                        "Ordner mit einer Vorlage der Mapping-Dokumentation je Entität – aus dem Projekt vorbelegt"));
        return java.util.stream.Stream.concat(own.stream(), search.stream()).toList();
    }

    /** Finds the template, collects and analyses the code. Fails with a message for the user if something is missing. */
    public Prepared prepare(Config config, Consumer<String> log) throws IOException {
        List<String> terms = config.list(Keys.TERMS);
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("Die fachliche Entität fehlt.");
        }
        Path folder = config.path(Keys.MAPPING_TEMPLATES);
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException(folder == null
                    ? "Der Ordner der Mapping-Vorlagen fehlt – im Migrationsprojekt („Bearbeiten…“) eintragen."
                    : "Ordner der Mapping-Vorlagen nicht gefunden: " + folder);
        }
        MappingTemplate.Found found = MappingTemplate.find(folder, terms);
        MappingTemplate template = found.template();
        if (template == null) {
            throw new IllegalArgumentException("Keine Mapping-Vorlage für " + terms.getFirst() + " in " + folder
                    + " (gesucht: Entität in der Tabelle bzw. Dateiname).");
        }
        log.accept("Mapping-Vorlage: " + template.file().getFileName() + " – " + String.join(", ", template.entities())
                + ", " + template.rows().size() + " Attribute"
                + (found.otherCandidates().isEmpty() ? "" : " (weitere passende Vorlagen ignoriert: "
                + String.join(", ", found.otherCandidates()) + ")"));
        if (template.mappingColumn() < 0 || template.schwebeColumn() < 0) {
            log.accept("WARNUNG: Die Vorlage hat keine Spalte " + (template.mappingColumn() < 0 ? "„Mapping“" : "„Schwebe Mapping“")
                    + " – Spalten: " + String.join(", ", template.header()));
        }

        Collected code = CodeCollectorTool.collect(config.path(Keys.TARGET), terms, CATEGORIES, config,
                "Migrationsprojekt", log);
        if (code.files().isEmpty()) {
            throw new IllegalArgumentException("Im Code des Migrationsprojekts gibt es keine Treffer für "
                    + String.join(", ", terms) + " – Suchbegriffe prüfen.");
        }
        if (code.files().stream().noneMatch(f -> f.phase() == Phase.PROCESS)) {
            log.accept("WARNUNG: Keine Klasse der Process-Phase für " + terms.getFirst() + " gefunden – ist das Mapping "
                    + "schon implementiert? Suchbegriffe prüfen.");
        }
        MappingAnalysis.Result analysis = MappingAnalysis.analyse(code.files(), template.attributes());
        log.accept("Voranalyse: %d von %d Attributen mit Fundstelle, %d mit Schwebe-Bezug, %d Schwebe-Stellen im Process-Code"
                .formatted(analysis.attributesFound(), template.rows().size(), analysis.attributesWithSchwebe(),
                        analysis.schwebeLines().size()));
        return new Prepared(template, found.otherCandidates(), code, analysis);
    }

    /** Standalone use: writes the template copy and the analysis with the code into the output folder. */
    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Prepared p = prepare(config, log);
        String slug = OutputDirectory.slug(config.list(Keys.TERMS).getFirst());
        Path out = OutputDirectory.of(config);
        Files.createDirectories(out);
        Path template = out.resolve(templateFileName(p.template(), slug));
        Files.writeString(template, p.template().text(), StandardCharsets.UTF_8);
        Path analysis = out.resolve("mapping-analyse-" + slug + ".md");
        Files.writeString(analysis, "# Mapping-Analyse: " + config.list(Keys.TERMS).getFirst() + "\n\n"
                + MappingAnalysis.markdown(p.analysis()) + "\n" + p.code().markdown(), StandardCharsets.UTF_8);
        return List.of(template, analysis);
    }

    /** Name of the template copy for the upload: Copilot reads .txt and .md, other extensions become .txt. */
    public static String templateFileName(MappingTemplate template, String slug) {
        return "mapping-vorlage-" + slug + "." + extension(template);
    }

    /** Name of the filled template Copilot returns. */
    public static String resultFileName(MappingTemplate template, String slug) {
        return "mapping-" + slug + "." + extension(template);
    }

    private static String extension(MappingTemplate template) {
        String name = template.file().getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.equals("md") ? "md" : "txt";
    }
}
