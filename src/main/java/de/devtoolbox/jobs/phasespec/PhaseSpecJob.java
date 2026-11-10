package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.SkillJob;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collects the code for a business term (via {@link CodeCollectorTool}) and generates a Copilot skill that
 * turns it into a specification, one dedicated section per selected batch phase. Skill texts live in
 * {@code src/main/resources/skills/phase-spec/} and can be edited without touching Java.
 */
public final class PhaseSpecJob extends SkillJob {

    private static final String RESOURCES = "/skills/phase-spec/";
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
        return "Sammelt den Code zu einem Fachbegriff und erzeugt dazu eine Anleitung für die KI, die daraus eine "
                + "Spezifikation erstellt – je gewählter Phase (Read/Process/Write) ein eigener Abschnitt. "
                + "Ergebnis: Skill-Datei + Code-Datei(en) zum Hochladen in Copilot.";
    }

    @Override
    protected List<Param> ownParams() {
        return List.of(
                Param.text("context", "Zusatzkontext (KI)", "", false,
                        "Optional, z. B. Zielprodukt, Besonderheiten der neuen Migration, Ansprechpartner"));
    }

    @Override
    protected String skillFileName(Config config) {
        return "skill-" + CodeCollectorTool.slug(config.list(CodeCollectorTool.TERMS).getFirst()) + ".md";
    }

    @Override
    protected String renderSkill(Config config) throws IOException {
        List<String> selected = config.list(CodeCollectorTool.PHASES).stream()
                .map(s -> s.toUpperCase(Locale.ROOT)).toList();
        List<String[]> phases = PHASES.stream().filter(p -> selected.contains(p[0])).toList();
        String phaseNames = String.join(", ", phases.stream().map(p -> p[2]).toList());
        String terms = String.join(", ", config.list(CodeCollectorTool.TERMS));

        StringBuilder sections = new StringBuilder();
        for (String[] phase : phases) {
            sections.append(resource(phase[1]).strip()).append("\n\n");
        }
        String context = config.text("context");

        String md = resource("skill.md")
                .replace("{{PHASE_SECTIONS}}", sections.toString().strip())
                .replace("{{CROSS_PHASE}}", phases.size() > 1 ? resource("cross-phase.md").stripTrailing() : "")
                .replace("{{OUTPUT}}", outputOutline(phases))
                .replace("{{CONTEXT}}", context.isEmpty() ? "" : "**Zusatzkontext:** " + context);
        return fill(md, Map.of("{{TERMS}}", terms, "{{PHASES}}", phaseNames))
                .replaceAll("\n{3,}", "\n\n");
    }

    /** Numbered outline of the expected answer – numbering depends on the selected phases. */
    private static String outputOutline(List<String[]> phases) {
        List<String> items = new ArrayList<>();
        items.add("**Zusammenfassung** – 5–10 Sätze: Was passiert mit „{{TERMS}}“ in der bestehenden Migration, "
                + "welche Klassen tragen die Logik?");
        items.add("**Ablaufübersicht** – Job, Steps und deren Reihenfolge, soweit aus dem Code erkennbar "
                + "(Liste oder Mermaid-Flowchart).");
        for (String[] phase : phases) {
            items.add("**Phase " + phase[2] + "** – Gliederung wie im Abschnitt „Phase " + phase[2] + "“ vorgegeben.");
        }
        if (phases.size() > 1) {
            items.add("**Durchgängigkeit** – Feldverfolgung über die Phasen (siehe Vorgehen).");
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

    private static String fill(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = PhaseSpecJob.class.getResourceAsStream(RESOURCES + name)) {
            if (in == null) {
                throw new IOException("Skill-Vorlage fehlt: " + RESOURCES + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
