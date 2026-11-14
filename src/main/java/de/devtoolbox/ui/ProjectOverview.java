package de.devtoolbox.ui;

import de.devtoolbox.core.CodeSource;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.tools.codecollector.DdlReader;
import de.devtoolbox.tools.codecollector.TargetModel;
import de.devtoolbox.tools.mappingdoc.MappingTemplate;
import de.devtoolbox.tools.standards.StandardsTool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the potentially I/O-heavy project overview without depending on Swing.
 * Call from a background thread, then publish the immutable summary on the EDT.
 */
final class ProjectOverview {

    record Summary(String description, boolean warning) {
    }

    private ProjectOverview() {
    }

    static Summary of(MigrationProject p) {
        List<String> parts = new ArrayList<>();
        if (!p.sparte().isEmpty()) {
            parts.add(p.sparte());
        }
        parts.add(schemaSummary(p));
        parts.add(targetModelSummary(p));
        parts.add(mappingTemplatesSummary(p));
        parts.add(standardsSummary(p));
        parts.add(referenceSummary(p));
        if (!p.description().isEmpty()) {
            parts.add("<i>" + p.description() + "</i>");
        }
        // Existing UI markup uses <b> for missing or invalid project resources.
        return new Summary(String.join("<br>", parts), parts.stream().anyMatch(t -> t.contains("<b>")));
    }

    private static String referenceSummary(MigrationProject p) {
        if (!p.referenceUrl().isEmpty()) {
            // a referenced project is named by its label, a direct address only by the repository
            return "Referenz: " + (p.referenceLabel().equals(p.referenceUrl()) ? "" : p.referenceLabel() + " · ")
                    + "Bitbucket " + CodeSource.parse(p.referenceUrl())
                    .map(CodeSource.Remote::describe).orElse(p.referenceUrl())
                    + (p.referenceCode() == null ? " (wird beim ersten Lauf geklont)" : "");
        }
        if (p.reference() == null && !p.referenceLabel().isEmpty()) {
            return "<b>Referenzprojekt „" + p.referenceLabel() + "“ nicht gefunden</b> (Bearbeiten…)";
        }
        if (p.reference() == null) {
            return "<b>Referenzprojekt nicht konfiguriert</b> (Bearbeiten…)";
        }
        if (!Files.isDirectory(p.reference())) {
            return "<b>Referenzprojekt fehlt:</b> " + p.reference();
        }
        return "Referenz: " + (p.referenceLabel().equals(p.reference().toString())
                ? p.reference() : p.referenceLabel() + " · " + p.reference());
    }

    /**
     * e.g. "Standards: übergreifend, 8 Vorlagen (Vertrag)" or "Standards: projekteigen, …".
     */
    private static String standardsSummary(MigrationProject p) {
        Path dir = p.standardsDirectory();
        if (dir == null || !Files.exists(dir.resolve(StandardsTool.INDEX))) {
            return "keine Standards (Basisdaten in der Seitenleiste)";
        }
        try {
            StandardsTool.Stored s = StandardsTool.load(dir);
            return "Standards: " + (p.hasOwnStandards() ? "projekteigen" : "übergreifend") + ", "
                    + s.rows().size() + " Vorlagen (" + s.example() + ")";
        } catch (Exception e) {
            return "<b>Standards nicht lesbar</b>";
        }
    }

    /**
     * e.g. "Zielmodell: 12 Entitäten" – JSON files in the project's target model folder.
     */
    private static String targetModelSummary(MigrationProject p) {
        if (p.targetModel() == null) {
            return "kein Zielmodell (Bearbeiten…)";
        }
        try {
            int n = TargetModel.entityCount(p.targetModel());
            return n == 0 ? "<b>Zielmodell leer/fehlt:</b> " + p.targetModel() : "Zielmodell: " + n + " Entitäten";
        } catch (Exception e) {
            return "<b>Zielmodell nicht lesbar</b>";
        }
    }

    /**
     * e.g. "Mapping-Vorlagen: 12" – templates of the mapping documentation.
     */
    private static String mappingTemplatesSummary(MigrationProject p) {
        if (p.mappingTemplates() == null) {
            return "keine Mapping-Vorlagen (Bearbeiten…)";
        }
        try {
            int n = MappingTemplate.all(p.mappingTemplates()).size();
            return n == 0 ? "<b>Mapping-Vorlagen leer/fehlen:</b> " + p.mappingTemplates() : "Mapping-Vorlagen: " + n;
        } catch (Exception e) {
            return "<b>Mapping-Vorlagen nicht lesbar</b>";
        }
    }

    /**
     * e.g. "Altdaten-Struktur: 4 Tabellen, 21 Spalten" – read from the project's DDL.
     */
    private static String schemaSummary(MigrationProject p) {
        if (p.ddl() == null || !Files.exists(p.ddl())) {
            return "<b>keine Struktur der Altdaten</b> (DDL importieren…)";
        }
        try {
            List<DdlReader.Table> tables =
                    DdlReader.read(p.ddl());
            if (tables.isEmpty()) {
                return "<b>keine Struktur der Altdaten</b> (DDL importieren…)";
            }
            int columns = tables.stream().mapToInt(t -> t.columns().size()).sum();
            return "Altdaten-Struktur: " + tables.size() + " Tabellen, " + columns + " Spalten";
        } catch (Exception e) {
            return "<b>Struktur der Altdaten nicht lesbar:</b> " + e.getMessage();
        }
    }

}
