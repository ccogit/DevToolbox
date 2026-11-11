package de.devtoolbox.jobs.mappingdoc;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.CopilotLimit;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.OutputDirectory;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Texts;
import de.devtoolbox.tools.mappingdoc.MappingAnalysis;
import de.devtoolbox.tools.mappingdoc.MappingDocTool;
import de.devtoolbox.tools.mappingdoc.MappingDocTool.Prepared;
import de.devtoolbox.tools.mappingdoc.MappingTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Documents the implemented mapping of an entity: the {@link MappingDocTool} finds the entity's template of the
 * mapping documentation and collects and pre-analyses the Process code of the migration project; Copilot fills the
 * columns for the regular stock and for pending changes (Schwebe) and returns the filled template for download.
 * Upload: skill + task (with analysis and code) + template.
 */
public final class MappingDocJob implements Job {

    private static final String SKILL_FILE = "skill-mapping-dokumentation.md";

    private final MappingDocTool tool = new MappingDocTool();

    @Override
    public String id() {
        return "mapping-doc";
    }

    @Override
    public String name() {
        return "Mapping-Dokumentation aus implementiertem Code (je Entität)";
    }

    @Override
    public String fileTag() {
        return "mapping-doku";
    }

    @Override
    public String description() {
        return "Analysiert im Code des Migrationsprojekts das implementierte Mapping (Process-Phase) einer Entität und "
                + "erzeugt eine Aufgabe für die KI, die Mapping-Vorlage der Entität auszufüllen – getrennt nach Bestand "
                + "und Schwebe. Ergebnis zum Hochladen in Copilot: Skill + Aufgabe (mit Voranalyse und Code) + Vorlage; "
                + "Copilot stellt die ausgefüllte Vorlage zum Download bereit.";
    }

    @Override
    public List<Param> params() {
        return Stream.concat(tool.params().stream(), Stream.of(
                Param.text(Keys.CONTEXT, "Zusatzkontext (KI)", "", false,
                        "Optional, z. B. wie Schwebe im Projekt erkannt wird"),
                CopilotLimit.param())).toList();
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Prepared prepared = tool.prepare(config, log);
        MappingTemplate template = prepared.template();
        List<String> terms = config.list(Keys.TERMS);
        String entity = terms.getFirst();
        String slug = OutputDirectory.slug(entity);

        Path dir = OutputDirectory.runFolder(config, fileTag(), slug);
        OutputDirectory.prepare(dir);
        Path skill = dir.resolve(SKILL_FILE);
        Path task = dir.resolve("aufgabe-mapping-dokumentation-" + slug + ".md");
        Path templateCopy = dir.resolve(MappingDocTool.templateFileName(template, slug));
        String resultFile = MappingDocTool.resultFileName(template, slug);
        Files.writeString(skill, Texts.resource("/skills/mapping-doc/skill.md"), StandardCharsets.UTF_8);
        Files.writeString(templateCopy, template.text(), StandardCharsets.UTF_8);
        Files.writeString(task, renderTask(config, entity, terms, prepared, templateCopy.getFileName().toString(),
                resultFile), StandardCharsets.UTF_8);

        List<Path> files = new ArrayList<>(List.of(skill, task, templateCopy));
        log.accept("Ordner: " + dir);
        log.accept("Aufgabe geschrieben: " + task);
        log.accept("Hochladen (3 Dateien): " + files.stream().map(p -> p.getFileName().toString())
                .collect(Collectors.joining(", ")));
        log.accept("Prompt: Bearbeite die Aufgabe in " + task.getFileName() + " mit dem Skill " + SKILL_FILE
                + "; die auszufüllende Vorlage ist " + templateCopy.getFileName() + ".");
        log.accept("Ergebnis von Copilot: " + resultFile + " (Download)");
        CopilotLimit.check(config, files, false,
                "Code verkleinern: Umfang „Nur Klassen mit dem Begriff“, „Kompakt“.", log);
        return files;
    }

    private static String renderTask(Config config, String entity, List<String> terms, Prepared p, String templateFile,
                                     String resultFile) throws Exception {
        MappingTemplate t = p.template();
        String context = config.text(Keys.CONTEXT);
        String project = MigrationProject.find(config.text(Keys.PROJECT))
                .map(m -> m.label() + (m.sparte().isEmpty() ? "" : " (" + m.sparte() + ")")).orElse("–");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("SKILL_FILE", SKILL_FILE);
        values.put("TEMPLATE_FILE", templateFile);
        values.put("TEMPLATE_ORIGINAL", t.file().getFileName().toString());
        values.put("RESULT_FILE", resultFile);
        values.put("MAPPING_COLUMN", t.mappingColumn() < 0 ? "Mapping" : t.header().get(t.mappingColumn()));
        values.put("SCHWEBE_COLUMN", t.schwebeColumn() < 0 ? "Schwebe Mapping" : t.header().get(t.schwebeColumn()));
        values.put("ATTRIBUTE_COUNT", String.valueOf(t.rows().size()));
        values.put("COLUMNS", String.join(" · ", t.header()));
        values.put("PROJECT", project);
        values.put("TERMS", String.join(", ", terms));
        values.put("ENTITY", entity);
        values.put("CONTEXT", context.isEmpty() ? "" : "**Zusatzkontext:** " + context);
        values.put("ANALYSIS", MappingAnalysis.markdown(p.analysis()).strip());
        String text = Texts.collapseBlankLines(Texts.fill(Texts.resource("/aufgaben/mapping-doc/aufgabe.md"), values));
        return text.strip() + "\n\n" + p.code().markdown();
    }
}
