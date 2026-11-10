package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.Config;
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
 * "Migrationsspezifikation je Phase" plus the generic skill needed to work on it.
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

    public PhaseSpecJob() {
        super(new CodeCollectorTool());
    }

    @Override
    public String id() {
        return "phase-spec";
    }

    @Override
    public String name() {
        return "Migrationsspezifikation je Phase (Fachbegriff)";
    }

    @Override
    public String description() {
        return "Sammelt den Code zu einem Fachbegriff und erzeugt daraus eine Aufgabe für die KI: eine Spezifikation, "
                + "je gewählter Phase (Read/Process/Write) ein eigener Abschnitt. Ergebnis zum Hochladen in Copilot: "
                + "Skill + Aufgabe + Code-Datei.";
    }

    @Override
    protected List<Param> ownParams() {
        return List.of(
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

        return resource(TASK_RESOURCES + "aufgabe.md")
                .replace("{{OUTPUT}}", outputOutline(phases))
                .replace("{{CONTEXT}}", context.isEmpty() ? "" : "**Zusatzkontext:** " + context)
                .replace("{{SKILL_FILE}}", skillFileName())
                .replace("{{PHASES}}", String.join(", ", phases))
                .replace("{{EXCLUDED}}", excluded.isEmpty() ? "–" : String.join(", ", excluded))
                .replace("{{FILES}}", files)
                .replace("{{TERMS}}", terms)
                .replaceAll("\n{3,}", "\n\n");
    }

    /** Numbered outline of the expected answer – depends on the selected phases. */
    private static String outputOutline(List<String> phases) {
        List<String> items = new ArrayList<>();
        items.add("**Zusammenfassung** – 5–10 Sätze: Was passiert mit „{{TERMS}}“ in der bestehenden Migration, "
                + "welche Klassen tragen die Logik?");
        items.add("**Ablaufübersicht** – Job, Steps und deren Reihenfolge, soweit aus dem Code erkennbar "
                + "(Liste oder Mermaid-Flowchart).");
        for (String phase : phases) {
            items.add("**Phase " + phase + "** – Gliederung wie im Skill, Abschnitt „Phase " + phase + "“.");
        }
        if (phases.size() > 1) {
            items.add("**Durchgängigkeit** – Feldverfolgung über die Phasen " + String.join(", ", phases)
                    + " (siehe Skill, Vorgehen).");
        }
        items.add("**Offene Fragen** – nummeriert, je mit Begründung, warum der Code die Antwort nicht liefert.");
        items.add("**Übertragungshinweise für das neue Produkt** – alle Stellen mit [PRODUKTSPEZIFISCH], "
                + "je mit Empfehlung: übernehmen / anpassen / fachlich klären.");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            out.append(i + 1).append(". ").append(items.get(i)).append('\n');
        }
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
