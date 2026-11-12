package de.devtoolbox.jobs.mappingdoc;

import de.devtoolbox.core.JobInfo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Documents the implemented mapping of an entity: the {@link MappingDocTool} finds the entity's target model JSON
 * and Confluence table, enters ticket and status, and collects and pre-analyses the code of the migration; Copilot
 * writes the mapping rules for the regular stock and for pending changes (Schwebe) into both and returns them for
 * download. Upload: skill + task (analysis and code) + the prefilled documents.
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
    public JobInfo info() {
        return new JobInfo(
                "Unterstützt dich dabei, das implementierte Mapping einer Entität zu dokumentieren – Copilot trägt die Mapping-Regeln (getrennt nach Bestand und Schwebe) in Zielmodell-JSON und Confluence-Tabelle ein.",
                "Code des Migrationsprojekts, fachliche Entität mit Synonymen, Jira-Ticket der Spezifikation; optional Referenzprojekt (Vergleich), Zielmodell (JSON), Mapping-Vorlagen und Zusatzkontext.",
                "Das Mapping der Entität (Process-Phase) ist im Code umgesetzt. Zielmodell-JSON und Mapping-Vorlage der Entität liegen vor. Copilot-Zugriff mit drei Dateien pro Prompt.",
                "Drei Dateien zum Hochladen in Copilot: Skill, Aufgabe (mit Voranalyse und Code) und die vorbefüllten Dokumente (Jira-Ticket und Status FERTIG schon eingetragen). Copilot liefert Zielmodell-JSON und Confluence-Tabelle mit den Mapping-Regeln (Bestand und Schwebe) zum Download.");
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
        List<String> terms = config.list(Keys.TERMS);
        String entity = prepared.json() != null ? prepared.json().entity() : terms.getFirst();
        String slug = OutputDirectory.slug(terms.getFirst());

        Path dir = OutputDirectory.createRunFolder(config, fileTag(), slug);
        Path skill = dir.resolve(SKILL_FILE);
        Path task = dir.resolve("aufgabe-mapping-dokumentation-" + slug + ".md");
        Path documents = dir.resolve(MappingDocTool.documentsFileName(slug));
        List<String> results = MappingDocTool.resultFileNames(prepared);
        Files.writeString(skill, Texts.resource("/skills/mapping-doc/skill.md"), StandardCharsets.UTF_8);
        Files.writeString(documents, MappingDocTool.documents(prepared), StandardCharsets.UTF_8);
        Files.writeString(task, renderTask(config, entity, terms, prepared, documents.getFileName().toString(), results),
                StandardCharsets.UTF_8);

        List<Path> files = List.of(skill, task, documents);
        log.accept("Ordner: " + dir);
        log.accept("Aufgabe geschrieben: " + task);
        log.accept("Hochladen (3 Dateien): " + files.stream().map(p -> p.getFileName().toString())
                .collect(Collectors.joining(", ")));
        log.accept("Prompt: Bearbeite die Aufgabe in " + task.getFileName() + " mit dem Skill " + SKILL_FILE
                + "; die auszufüllenden Dokumente stehen in " + documents.getFileName() + ".");
        log.accept("Ergebnis von Copilot (Download): " + String.join(", ", results));
        CopilotLimit.check(config, files, false,
                "Code verkleinern: Umfang „Nur Klassen mit dem Begriff“, „Kompakt“.", log);
        return files;
    }

    private static String renderTask(Config config, String entity, List<String> terms, Prepared p,
                                     String documentsFile, List<String> results) throws Exception {
        MappingTemplate t = p.template();
        String context = config.text(Keys.CONTEXT);
        String project = MigrationProject.find(config.text(Keys.PROJECT))
                .map(m -> m.label() + (m.sparte().isEmpty() ? "" : " (" + m.sparte() + ")")).orElse("–");
        StringBuilder docs = new StringBuilder("| Dokument | Original | Ergebnis (Download) |\n|---|---|---|\n");
        int i = 0;
        if (p.json() != null) {
            docs.append("| Zielmodell-JSON – Felder `mapping` (Bestand) und `schwebeMapping` (Schwebe) | `")
                    .append(p.json().file().getFileName()).append("` | `").append(results.get(i++)).append("` |\n");
        }
        if (t != null) {
            docs.append("| Confluence-Tabelle – Spalten „")
                    .append(t.mappingColumn() < 0 ? "Mapping" : t.header().get(t.mappingColumn())).append("“ (Bestand) und „")
                    .append(t.schwebeColumn() < 0 ? "Schwebe Mapping" : t.header().get(t.schwebeColumn()))
                    .append("“ (Schwebe) | `").append(t.file().getFileName()).append("` | `").append(results.get(i))
                    .append("` |\n");
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("SKILL_FILE", SKILL_FILE);
        values.put("DOCUMENTS_FILE", documentsFile);
        values.put("DOCUMENTS", docs.toString().strip());
        values.put("RESULTS", results.stream().map(r -> "`" + r + "`").collect(Collectors.joining(" und ")));
        values.put("JIRA", MappingDocTool.jiraMacro(p.jiraIssue()));
        values.put("ATTRIBUTE_COUNT", String.valueOf(p.attributes().size()));
        values.put("PROJECT", project);
        values.put("TERMS", String.join(", ", terms));
        values.put("ENTITY", entity);
        MappingDocTool.Reference ref = p.reference();
        values.put("REFERENCE", ref == null ? ""
                : "**Abgleich:** Am Ende steht das Mapping des Referenzprojekts **" + ref.label() + "** – nur zum "
                + "Vergleich. Mappt es ein Attribut anders, ergänze die Regel um den Hinweis laut Skill; Grundlage der "
                + "Regeln ist ausschließlich der Code des Migrationsprojekts.");
        values.put("CONTEXT", context.isEmpty() ? "" : "**Zusatzkontext:** " + context);
        values.put("ANALYSIS", MappingAnalysis.markdown(p.analysis()).strip());
        String text = Texts.collapseBlankLines(Texts.fill(Texts.resource("/aufgaben/mapping-doc/aufgabe.md"), values));
        String result = text.strip() + "\n\n" + p.code().markdown();
        if (ref != null) {
            result = result.strip() + "\n\n# Referenzprojekt " + ref.label() + " – nur zum Vergleich\n\n"
                    + "**Keine Grundlage der Dokumentation.** Nur um Abweichungen des Migrationsprojekts vom "
                    + "Referenzprojekt als Hinweis zu vermerken. Dateien beginnen mit `" + MappingDocTool.REFERENCE_PREFIX
                    + "`.\n\n## Voranalyse Referenzprojekt\n\n" + MappingAnalysis.markdown(ref.analysis()).strip()
                    + "\n\n" + ref.markdown();
        }
        return result;
    }
}
