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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * One-click migration readiness check. No Copilot upload, ticket, entity, or phase is needed.
 * Missing sources become actionable findings, not exceptions that stop the entire diagnosis.
 */
public final class ProjectCheckJob implements Job {
    public static final String ID = "projekt-check";

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
        List<String> findings = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        StringBuilder md = new StringBuilder("# Projekt-Check: " + safe(project.label()) + "\n\n");
        md.append("Ausgangslage und nächste Schritte für die Migration. Es wurden **keine** Projektdateien verändert.\n\n");
        md.append("| Baustein | Ergebnis | Empfehlung |\n|---|---|---|\n");
        addPath(md, "Migrationscode", project.root(), findings, actions,
                "Zielprojekt/Code in den Projektstammdaten zuordnen");
        addPath(md, "Referenzcode", project.reference(), findings, actions,
                "Referenzprojekt festlegen, damit Read/Process/Write abgeleitet werden können");

        int tables = 0;
        if (project.ddl() != null && Files.exists(project.ddl())) {
            try {
                tables = DdlReader.read(project.ddl()).size();
                md.append("| Altdaten-Schema (DDL) | ").append(tables).append(" Tabellen/Views | ")
                        .append(tables == 0 ? "DDL importieren und Tabellen prüfen" : "vorhanden").append(" |\n");
                if (tables == 0) {
                    findings.add("DDL ohne erkannte Tabellen");
                    actions.add("DDL mit Tabellen-/View-Definitionen importieren");
                }
            } catch (Exception ex) {
                md.append("| Altdaten-Schema (DDL) | nicht lesbar | DDL-Dateien prüfen |\n");
                findings.add("DDL nicht lesbar: " + ex.getMessage());
                actions.add("DDL-Dateien überprüfen");
            }
        } else {
            md.append("| Altdaten-Schema (DDL) | fehlt | Schema importieren |\n");
            findings.add("Kein Altdaten-Schema");
            actions.add("DDL auf der Projektseite importieren");
        }

        List<String> targetEntities = List.of();
        if (project.targetModel() != null && Files.isDirectory(project.targetModel())) {
            try {
                targetEntities = TargetModel.entityNames(project.targetModel());
            } catch (Exception ex) {
                findings.add("Zielmodell nicht lesbar: " + ex.getMessage());
            }
        }
        md.append("| Zielmodell | ").append(targetEntities.size()).append(" Entitäten | ")
                .append(targetEntities.isEmpty() ? "Zielmodell zuordnen oder prüfen" : "vorhanden").append(" |\n");
        if (targetEntities.isEmpty()) {
            actions.add("Zielmodell in den Projektstammdaten eintragen");
        }

        int mappingDocuments = 0;
        if (project.mappingTemplates() != null && Files.isDirectory(project.mappingTemplates())) {
            try {
                mappingDocuments = MappingTemplate.all(project.mappingTemplates()).size();
            } catch (Exception ex) {
                findings.add("Mapping-Vorlagen nicht lesbar: " + ex.getMessage());
            }
        }
        md.append("| Mapping-Vorlagen | ").append(mappingDocuments).append(" Dokumente | ")
                .append(mappingDocuments == 0 ? "Vorlagenordner prüfen" : "vorhanden").append(" |\n");
        if (mappingDocuments == 0) {
            actions.add("Mapping-Vorlagen ordnen oder zuordnen");
        }

        int standardRows = 0;
        int sharedBases = 0;
        Path standards = project.standardsDirectory();
        if (standards != null && Files.isRegularFile(standards.resolve(StandardsTool.INDEX))) {
            try {
                var stored = StandardsTool.load(standards);
                standardRows = stored.rows().size();
                sharedBases = stored.baseFiles().size();
            } catch (Exception ex) {
                findings.add("Standards nicht lesbar: " + ex.getMessage());
            }
        }
        md.append("| Implementierungs-Standards | ").append(standardRows)
                .append(" Vorlagen, ").append(sharedBases).append(" Basis-Klassen | ")
                .append(standardRows == 0 ? "Standards aus der Referenz erzeugen" : "vorhanden").append(" |\n");
        if (standardRows == 0) {
            actions.add("Basisdaten → Standards aus Referenzcode automatisch erzeugen");
        }
        EntitySuggestions.Suggestion entities = EntitySuggestions.suggest(project.ddl(),
                project.targetModel(), project.mappingTemplates(), project.reference(), project.id());

        md.append("\n## Vorgeschlagene Entitäten\n\n");
        if (entities.isEmpty()) {
            md.append("Noch keine belastbaren Vorschläge. Zuerst Referenzcode oder Zielmodell zuordnen.\n");
        } else {
            md.append("| Entität | Quellennachweis | Priorität |\n|---|---|---|\n");
            for (var e : entities.entries()) {
                String priority = e.legacy() && e.target() ? "hoch – Quelle und Ziel"
                        : e.target() ? "mittel – Ziel vorhanden" : e.legacy() ? "mittel – Quelle vorhanden" : "niedrig";
                md.append("| ").append(safe(e.name())).append(" | ").append(e.provenance())
                        .append(" | ").append(priority).append(" |\n");
            }
        }
        md.append("\n## Nächste Schritte\n\n");
        Set<String> unique = new HashSet<>();
        int n = 1;
        for (String action : actions) {
            if (unique.add(action)) {
                md.append(n++).append(". ").append(action).append('\n');
            }
        }
        if (entities.entries().stream().anyMatch(e -> e.legacy() && e.target())) {
            md.append(n++).append(". Eine Entität mit Quell- und Zielnachweis wählen, dann ")
                    .append("„Spezifikation aus Referenzprojekt“ ausführen.\n");
        }
        if (actions.isEmpty()) {
            md.append("Alle geprüften Voraussetzungen sind vorhanden. Mit der Spezifikation der nächsten ")
                    .append("Entität fortfahren.\n");
        }
        md.append("\n## Offene Hinweise\n\n");
        if (findings.isEmpty()) {
            md.append("Keine fehlenden Pflichtquellen erkannt. Die fachliche Richtigkeit muss weiterhin geprüft werden.\n");
        } else {
            for (String finding : findings) {
                md.append("- ").append(safe(finding)).append('\n');
            }
        }
        md.append("\n*Der Check liest lokale Projektdateien, verwendet keine KI und schreibt nichts in die Quellen.*\n");
        Path directory = OutputDirectory.createRunFolder(config, fileTag(), project.id());
        Path output = directory.resolve("projektcheck-" + OutputDirectory.slug(project.id()) + ".md");
        Files.writeString(output, md.toString(), StandardCharsets.UTF_8);
        log.accept("Projekt-Check erstellt: " + output);
        log.accept(entities.entries().size() + " Entitätsvorschläge, " + findings.size() + " Hinweise.");
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

    private static void addPath(StringBuilder md, String label, Path path, List<String> findings,
                                List<String> actions, String action) {
        boolean exists = path != null && Files.isDirectory(path);
        md.append("| ").append(label).append(" | ").append(exists ? "vorhanden" : "fehlt")
                .append(" | ").append(exists ? "vorhanden" : action).append(" |\n");
        if (!exists) {
            findings.add(label + " fehlt");
            actions.add(action);
        }
    }

    private static String safe(String s) {
        return s.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }
}
