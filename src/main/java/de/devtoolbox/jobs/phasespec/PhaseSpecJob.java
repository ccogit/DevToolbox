package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.TaskJob;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Collects the code for a business term (via {@link CodeCollectorTool}) and writes the task
 * "Spezifikation aus Referenzprojekt" plus the generic skill needed to work on it.
 * Texts live in {@code src/main/resources/skills/phase-spec/} (skill) and
 * {@code src/main/resources/aufgaben/phase-spec/} (task) and can be edited without touching Java.
 */
public final class PhaseSpecJob extends TaskJob {

    private static final String SKILL_RESOURCES = "/skills/phase-spec/";
    private static final String TASK_RESOURCES = "/aufgaben/phase-spec/";
    /** Phase value → resource file and display name, in batch order. */
    private static final List<String[]> PHASES = List.of(
            new String[]{"READ", "read.md", "Read"},
            new String[]{"PROCESS", "process.md", "Process"},
            new String[]{"WRITE", "write.md", "Write"});

    private static final String DETAIL = "detail";
    private static final String COMPACT = "compact";
    private static final String DETAILED = "detailed";

    public PhaseSpecJob() {
        super(new CodeCollectorTool());
    }

    @Override
    public String id() {
        return "phase-spec";
    }

    @Override
    public String name() {
        return "Spezifikation aus Referenzprojekt (je Phase, fachliche Entität)";
    }

    @Override
    public String fileTag() {
        return "spezifikation";
    }

    @Override
    public String description() {
        return "Sammelt im Referenzprojekt (bereits umgesetzte Migration) Attribute und Code zu einer fachlichen Entität "
                + "und erzeugt daraus eine Aufgabe für die KI: eine attributweise Spezifikation für das Zielprojekt, je gewählter Phase "
                + "(Read/Process/Write) ein Abschnitt. Ergebnis zum Hochladen in Copilot: Skill + Aufgabe + Code-Datei.";
    }

    @Override
    protected List<Param> ownParams() {
        return List.of(
                Param.choice(DETAIL, "Detailgrad der Spezifikation", DETAILED,
                        "Ausführlich: zusätzlich die einzelnen Schritte des Einlesens, Verarbeitens (je Attribut) und Schreibens",
                        new Param.Option(COMPACT, "Kompakt – Attributtabellen"),
                        new Param.Option(DETAILED, "Ausführlich – zusätzlich Schritte je Attribut")),
                Param.text("context", "Zusatzkontext (KI)", "", false,
                        "Optional, z. B. Zielprodukt, Besonderheiten der neuen Migration, Ansprechpartner"));
    }

    @Override
    protected String taskFileName(Config config) {
        return "aufgabe-" + CodeCollectorTool.slug(config.list(CodeCollectorTool.TERMS).getFirst()) + ".md";
    }

    @Override
    protected String skillFileName() {
        return "skill-phasen-spezifikation.md";
    }

    @Override
    protected String renderSkill() throws IOException {
        StringBuilder sections = new StringBuilder();
        for (String[] phase : PHASES) {
            sections.append(resource(SKILL_RESOURCES + phase[1]).strip()).append("\n\n");
        }
        return resource(SKILL_RESOURCES + "skill.md").replace("{{PHASE_SECTIONS}}", sections.toString().strip());
    }

    @Override
    protected String renderTask(Config config, List<Path> contentFiles) throws IOException {
        List<String> selected = config.list(CodeCollectorTool.PHASES).stream()
                .map(s -> s.toUpperCase(Locale.ROOT)).toList();
        List<String> phases = PHASES.stream().filter(p -> selected.contains(p[0])).map(p -> p[2]).toList();
        List<String> excluded = PHASES.stream().filter(p -> !selected.contains(p[0])).map(p -> p[2]).toList();
        String terms = String.join(", ", config.list(CodeCollectorTool.TERMS));
        String files = contentFiles.stream().map(p -> "`" + p.getFileName() + "`").collect(Collectors.joining(", "));
        String context = config.text("context");
        boolean detailed = !config.text(DETAIL).equals(COMPACT);
        java.util.Optional<MigrationProject> project = MigrationProject.find(config.text(MigrationProject.KEY));

        return resource(TASK_RESOURCES + "aufgabe.md")
                .replace("{{OUTPUT}}", outputOutline(phases, detailed))
                .replace("{{DETAIL}}", detailed
                        ? "**ausführlich** – Regeln als einzelne Schritte in den Zellen"
                        : "**kompakt** – Regeln in einem Satz je Zelle")
                .replace("{{CONTEXT}}", context.isEmpty() ? "" : "**Zusatzkontext:** " + context)
                .replace("{{SKILL_FILE}}", skillFileName())
                .replace("{{RESULT_FILE}}", "spezifikation-"
                        + CodeCollectorTool.slug(config.list(CodeCollectorTool.TERMS).getFirst()) + ".md")
                .replace("{{PHASES}}", String.join(", ", phases))
                .replace("{{EXCLUDED}}", excluded.isEmpty() ? "–" : String.join(", ", excluded))
                .replace("{{FILES}}", files)
                .replace("{{PROJECT}}", project.map(p -> p.label() + (p.sparte().isEmpty() ? "" : " (" + p.sparte() + ")"))
                        .orElse("– (nicht angegeben)"))
                .replace("{{REFERENCE}}", project.filter(p -> !p.referenceLabel().isEmpty()
                                && p.reference() != null && p.reference().equals(config.path("base")))
                        .map(p -> p.referenceLabel() + " · `" + config.text("base") + "`")
                        .orElse("`" + config.text("base") + "`"))
                .replace("{{ENTITY}}", config.list(CodeCollectorTool.TERMS).getFirst())
                .replace("{{TERMS}}", terms)
                .replaceAll("\n{3,}", "\n\n");
    }

    /** Numbered outline of the expected answer – depends on the selected phases and the level of detail. */
    private static String outputOutline(List<String> phases, boolean detailed) {
        List<String> items = new ArrayList<>();
        items.add("**Zusammenfassung** – " + (detailed ? "5–10 Sätze" : "höchstens 5 Sätze")
                + ": Was passiert mit der Entität „{{ENTITY}}“ im Referenzprojekt, welche Klassen tragen die Logik? "
                + "Dazu die Kennzahlen: Anzahl Attribute, Regeln je Prüfbedarf ([PRÜFEN] / [TECHNISCH] / [1:1]) je Phase, "
                + "Anzahl OPL-Punkte.");
        items.add("**Ablaufübersicht** – Tabelle | Schritt | Klasse | Aufgabe |: Job und Steps in ihrer Reihenfolge "
                + "(nur der Ablauf, keine Attributregeln).");
        for (String phase : phases) {
            String main = phase.equals("Write") ? "Feldtabelle Write" : "Attributtabelle " + phase;
            items.add("**Phase " + phase + "** – genau zwei Tabellen wie im Skill, Abschnitt „Phase " + phase + "“: "
                    + "die " + main + " (eine Zeile je " + (phase.equals("Write") ? "Zielfeld" : "Attribut")
                    + ", mit Prüfbedarf, OPL-Bezug und Beleg) und die Satzregeln " + phase + ".");
        }
        items.add("**Offene-Punkte-Liste (OPL)** – alle Fragen, Annahmen und Lücken aus allen Phasen, "
                + "nummeriert OPL-1, OPL-2, …: | OPL-Nr | Frage | Betrifft (Phase · Attribut bzw. Satzregel) | "
                + "Warum offen | Annahme bis zur Klärung |.");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            out.append(i + 1).append(". ").append(items.get(i)).append('\n');
        }
        out.append("\nKeine weiteren Abschnitte oder Tabellen: Jede Information steht genau einmal.");
        return out.toString().strip();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = PhaseSpecJob.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Vorlage fehlt: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
