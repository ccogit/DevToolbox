package de.devtoolbox.jobs.diagnosis;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.EntitySuggestions;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobInfo;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.OutputDirectory;
import de.devtoolbox.core.Param;
import de.devtoolbox.tools.codecollector.DdlReader;
import de.devtoolbox.tools.codecollector.TargetModel;
import de.devtoolbox.tools.mappingdoc.MappingTemplate;
import de.devtoolbox.tools.standards.StandardsTool;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One-click migration readiness check. No Copilot upload, ticket, entity, or phase is needed.
 * Missing sources become actionable findings, not exceptions that stop the entire diagnosis.
 */
public final class ProjectCheckJob implements Job {
    public static final String ID = "projekt-check";

    /** The report under construction: table rows, what is missing and what to do about it (in first-seen order). */
    private static final class Report {
        final StringBuilder md = new StringBuilder();
        final List<String> findings = new ArrayList<>();
        final Set<String> actions = new LinkedHashSet<>();

        void row(String component, String result, String recommendation) {
            md.append("| ").append(component).append(" | ").append(result).append(" | ").append(recommendation)
                    .append(" |\n");
        }

        void missing(String finding, String action) {
            findings.add(finding);
            actions.add(action);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String name() {
        return "Projekt prüfen – automatisch nächste Schritte erkennen";
    }

    @Override
    public String fileTag() {
        return "projektcheck";
    }

    @Override
    public JobInfo info() {
        return new JobInfo(
                "Analysiert vorhandene Codequellen, Altdaten-Schema, Zielmodell, Mapping-Vorlagen und Standards. "
                        + "Schlägt die nächsten sinnvollen Migrationsschritte vor.",
                "Nur ein Migrationsprojekt auswählen; bei genau einem vorhandenen Projekt automatisch.",
                "Ein Projektordner mit projekt.json genügt. Fehlende Quellen werden als Hinweise dokumentiert.",
                "Eine übersichtliche Markdown-Datei mit Fundstellen, Lücken und priorisierten Empfehlungen; "
                        + "kein Copilot-Upload notwendig.");
    }

    @Override
    public List<Param> params() {
        return List.of(Param.directory(Keys.OUTPUT, "Ausgabeordner", false,
                "Leer = Downloads; Ergebnis wird in einem neuen, getrennten Ordner abgelegt").advanced());
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        MigrationProject project = selected(config);
        log.accept("Prüfe Migrationsprojekt: " + project.label());
        Report report = new Report();
        report.md.append("# Projekt-Check: ").append(safe(project.label())).append("\n\n")
                .append("Ausgangslage und nächste Schritte für die Migration. ")
                .append("Es wurden **keine** Projektdateien verändert.\n\n")
                .append("| Baustein | Ergebnis | Empfehlung |\n|---|---|---|\n");
        checkFolder(report, "Migrationscode", project.root(), "Zielprojekt/Code in den Projektstammdaten zuordnen");
        checkFolder(report, "Referenzcode", project.reference(),
                "Referenzprojekt festlegen, damit Read/Process/Write abgeleitet werden können");
        checkSchema(report, project.ddl());
        checkTargetModel(report, project.targetModel());
        checkMappingTemplates(report, project.mappingTemplates());
        checkStandards(report, project.standardsDirectory());

        EntitySuggestions.Suggestion entities = EntitySuggestions.suggest(project.ddl(), project.targetModel(),
                project.mappingTemplates(), project.reference());
        appendEntities(report.md, entities);
        appendNextSteps(report, entities);
        appendFindings(report);

        Path directory = OutputDirectory.createRunFolder(config, fileTag(), project.id());
        Path output = directory.resolve("projektcheck-" + OutputDirectory.slug(project.id()) + ".md");
        Files.writeString(output, report.md.toString(), StandardCharsets.UTF_8);
        log.accept("Projekt-Check erstellt: " + output);
        log.accept(entities.entries().size() + " Entitätsvorschläge, " + report.findings.size() + " Hinweise.");
        return List.of(output);
    }

    private static MigrationProject selected(Config config) {
        String id = config.text(Keys.PROJECT);
        if (!id.isBlank()) {
            return MigrationProject.find(id).orElseThrow(
                    () -> new IllegalArgumentException("Migrationsprojekt nicht gefunden: " + id));
        }
        List<MigrationProject> projects = MigrationProject.all();
        if (projects.size() == 1) {
            return projects.getFirst();
        }
        throw new IllegalArgumentException("Bitte ein Migrationsprojekt auswählen"
                + (projects.isEmpty() ? " (noch keine Projekte vorhanden)." : " – mehrere Projekte vorhanden."));
    }

    private static void checkFolder(Report report, String label, Path path, String action) {
        boolean exists = path != null && Files.isDirectory(path);
        report.row(label, exists ? "vorhanden" : "fehlt", exists ? "vorhanden" : action);
        if (!exists) {
            report.missing(label + " fehlt", action);
        }
    }

    private static void checkSchema(Report report, Path ddl) {
        String label = "Altdaten-Schema (DDL)";
        if (ddl == null || !Files.exists(ddl)) {
            report.row(label, "fehlt", "Schema importieren");
            report.missing("Kein Altdaten-Schema", "DDL auf der Projektseite importieren");
            return;
        }
        try {
            int tables = DdlReader.read(ddl).size();
            report.row(label, tables + " Tabellen/Views",
                    tables == 0 ? "DDL importieren und Tabellen prüfen" : "vorhanden");
            if (tables == 0) {
                report.missing("DDL ohne erkannte Tabellen", "DDL mit Tabellen-/View-Definitionen importieren");
            }
        } catch (Exception ex) {
            report.row(label, "nicht lesbar", "DDL-Dateien prüfen");
            report.missing("DDL nicht lesbar: " + ex.getMessage(), "DDL-Dateien überprüfen");
        }
    }

    private static void checkTargetModel(Report report, Path targetModel) {
        int entities = 0;
        if (targetModel != null && Files.isDirectory(targetModel)) {
            try {
                entities = TargetModel.entityNames(targetModel).size();
            } catch (Exception ex) {
                report.findings.add("Zielmodell nicht lesbar: " + ex.getMessage());
            }
        }
        report.row("Zielmodell", entities + " Entitäten",
                entities == 0 ? "Zielmodell zuordnen oder prüfen" : "vorhanden");
        if (entities == 0) {
            report.actions.add("Zielmodell in den Projektstammdaten eintragen");
        }
    }

    private static void checkMappingTemplates(Report report, Path mappingTemplates) {
        int documents = 0;
        if (mappingTemplates != null && Files.isDirectory(mappingTemplates)) {
            try {
                documents = MappingTemplate.all(mappingTemplates).size();
            } catch (Exception ex) {
                report.findings.add("Mapping-Vorlagen nicht lesbar: " + ex.getMessage());
            }
        }
        report.row("Mapping-Vorlagen", documents + " Dokumente", documents == 0 ? "Vorlagenordner prüfen" : "vorhanden");
        if (documents == 0) {
            report.actions.add("Mapping-Vorlagen ordnen oder zuordnen");
        }
    }

    private static void checkStandards(Report report, Path standards) {
        int templates = 0;
        int bases = 0;
        if (standards != null && Files.isRegularFile(standards.resolve(StandardsTool.INDEX))) {
            try {
                StandardsTool.Stored stored = StandardsTool.load(standards);
                templates = stored.rows().size();
                bases = stored.baseFiles().size();
            } catch (Exception ex) {
                report.findings.add("Standards nicht lesbar: " + ex.getMessage());
            }
        }
        report.row("Implementierungs-Standards", templates + " Vorlagen, " + bases + " Basis-Klassen",
                templates == 0 ? "Standards aus der Referenz erzeugen" : "vorhanden");
        if (templates == 0) {
            report.actions.add("Basisdaten → Standards aus Referenzcode automatisch erzeugen");
        }
    }

    private static void appendEntities(StringBuilder md, EntitySuggestions.Suggestion entities) {
        md.append("\n## Vorgeschlagene Entitäten\n\n");
        if (entities.isEmpty()) {
            md.append("Noch keine belastbaren Vorschläge. Zuerst Referenzcode oder Zielmodell zuordnen.\n");
            return;
        }
        md.append("| Entität | Quellennachweis | Priorität |\n|---|---|---|\n");
        for (EntitySuggestions.Entry e : entities.entries()) {
            String priority = e.legacy() && e.target() ? "hoch – Quelle und Ziel"
                    : e.target() ? "mittel – Ziel vorhanden" : "mittel – Quelle vorhanden";
            md.append("| ").append(safe(e.name())).append(" | ").append(e.provenance())
                    .append(" | ").append(priority).append(" |\n");
        }
    }

    private static void appendNextSteps(Report report, EntitySuggestions.Suggestion entities) {
        StringBuilder md = report.md.append("\n## Nächste Schritte\n\n");
        int n = 1;
        for (String action : report.actions) {
            md.append(n++).append(". ").append(action).append('\n');
        }
        if (entities.entries().stream().anyMatch(e -> e.legacy() && e.target())) {
            md.append(n).append(". Eine Entität mit Quell- und Zielnachweis wählen, dann ")
                    .append("die Spezifikation aus dem Referenzprojekt erzeugen.\n");
        }
        if (report.actions.isEmpty()) {
            md.append("Alle geprüften Voraussetzungen sind vorhanden. Mit der Spezifikation der nächsten ")
                    .append("Entität fortfahren.\n");
        }
    }

    private static void appendFindings(Report report) {
        StringBuilder md = report.md.append("\n## Offene Hinweise\n\n");
        if (report.findings.isEmpty()) {
            md.append("Keine fehlenden Pflichtquellen erkannt. Die fachliche Richtigkeit muss weiterhin geprüft werden.\n");
        } else {
            report.findings.forEach(finding -> md.append("- ").append(safe(finding)).append('\n'));
        }
        md.append("\n*Der Check liest lokale Projektdateien, verwendet keine KI und schreibt nichts in die Quellen.*\n");
    }

    private static String safe(String s) {
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
