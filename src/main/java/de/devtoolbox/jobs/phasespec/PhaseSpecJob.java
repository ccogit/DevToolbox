package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.JobInfo;
import de.devtoolbox.core.Config;
import de.devtoolbox.core.CopilotLimit;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.TaskJob;
import de.devtoolbox.core.Texts;
import de.devtoolbox.core.Tool;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
            new String[]{"READ", "read.md", "Read", "Einlesen"},
            new String[]{"PROCESS", "process.md", "Process", "Mapping"},
            new String[]{"WRITE", "write.md", "Write", "Schreiben"});

    /** Target rows per run above which one run per phase or per table is recommended (Startwert, zu kalibrieren). */
    static final int MAX_TARGET_ROWS = 60;

    private static final String DETAIL = Keys.DETAIL;
    private static final String COMPACT = "compact";
    private static final String DETAILED = "detailed";

    /** The one phase this job is fixed to (READ, PROCESS or WRITE); null = the phases are a parameter. */
    private final String fixedPhase;

    /** The combined job: the phases are chosen with the parameter {@code phases} (CLI id "phase-spec"). */
    public PhaseSpecJob() {
        this(null);
    }

    private PhaseSpecJob(String fixedPhase) {
        super(new CodeCollectorTool());
        this.fixedPhase = fixedPhase;
    }

    /** The job for exactly one phase: no "phases" field, skill, task and code only for that phase. */
    public static PhaseSpecJob forPhase(String phase) {
        return new PhaseSpecJob(phase.toUpperCase(Locale.ROOT));
    }

    private String[] phaseInfo() {
        return PHASES.stream().filter(p -> p[0].equals(fixedPhase)).findFirst().orElseThrow();
    }

    @Override
    public String id() {
        return fixedPhase == null ? "phase-spec" : "spec-" + phaseInfo()[2].toLowerCase(Locale.ROOT);
    }

    @Override
    public String name() {
        return fixedPhase == null ? "Spezifikation aus Referenzprojekt (je Phase, fachliche Entität)"
                : "Spezifikation " + phaseInfo()[2] + " (" + phaseInfo()[3] + ")";
    }

    /** With a fixed phase the run folder names the phase, so three runs for one entity stay distinguishable. */
    @Override
    public String fileTag() {
        return fixedPhase == null ? "spezifikation" : "spezifikation-" + phaseInfo()[2].toLowerCase(Locale.ROOT);
    }

    @Override
    protected Set<String> hiddenKeys() {
        if (fixedPhase == null) {
            return Set.of();
        }
        // the target model is only needed (and only used) for Process
        return fixedPhase.equals("PROCESS") ? Set.of(Keys.PHASES) : Set.of(Keys.PHASES, Keys.TARGET_MODEL);
    }

    @Override
    protected Config effective(Config config) {
        if (fixedPhase == null) {
            return config;
        }
        Map<String, String> values = new java.util.HashMap<>(config.values());
        values.put(Keys.PHASES, fixedPhase);
        return new Config(values);
    }

    @Override
    public JobInfo info() {
        if (fixedPhase != null) {
            return phaseJobInfo();
        }
        return new JobInfo(
                "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) eine attributweise Spezifikation für eine fachliche Entität im Zielprojekt zu erstellen – je gewählter Phase (Read, Process, Write) ein Abschnitt.",
                "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, betrachtete Phasen, Detailgrad; optional DDL der Altdatenbank mit den Tabellen der Entität, Zielmodell (JSON je Entität, bei Phase Process) und Zusatzkontext.",
                "Das Referenzprojekt enthält die Entität bereits umgesetzt. Bei Phase Process ist das Zielmodell nötig. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                "Drei Dateien zum Hochladen in Copilot: Skill (nur die gewählten Phasen), Aufgabe mit den nummerierten Soll-Listen (Read aus der DDL bzw. den Datenstrukturen, Process aus dem Zielmodell) und Code-Datei der Entität; im Protokoll Größe der Dateien, Start- und Fortsetzungsprompt. Copilot liefert eine Spezifikation (Markdown) mit Statuszeile und Zusammenfassung, Ablaufübersicht, je Phase Attribut- bzw. Feldtabelle und Satzregeln sowie einer Offene-Punkte-Liste.");
    }

    private JobInfo phaseJobInfo() {
        return switch (fixedPhase) {
            case "READ" -> new JobInfo(
                    "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) die Spezifikation der Phase Read (Einlesen) für eine fachliche Entität zu erstellen – eine Zeile je Attribut der Altdaten.",
                    "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, Detailgrad; optional DDL der Altdatenbank mit den Tabellen der Entität und Zusatzkontext.",
                    "Das Referenzprojekt enthält die Entität bereits umgesetzt. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                    "Drei Dateien zum Hochladen in Copilot: Skill (nur Phase Read), Aufgabe mit der Soll-Liste Read (aus der DDL bzw. den Datenstrukturen) und Code-Datei der Entität; im Protokoll Größe, Start- und Fortsetzungsprompt. Copilot liefert die Spezifikation Read (Markdown) mit Statuszeile, Attributtabelle, Satzregeln und Offene-Punkte-Liste.");
            case "PROCESS" -> new JobInfo(
                    "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) die Spezifikation der Phase Process (Mapping) für eine fachliche Entität zu erstellen – eine Zeile je Zielattribut.",
                    "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, Zielmodell (JSON je Entität), Detailgrad; optional DDL der Altdatenbank und Zusatzkontext.",
                    "Das Referenzprojekt enthält die Entität bereits umgesetzt, das Zielmodell liegt vor. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                    "Drei Dateien zum Hochladen in Copilot: Skill (nur Phase Process), Aufgabe mit der Soll-Liste Process (aus dem Zielmodell) und Code-Datei der Entität; im Protokoll Größe, Start- und Fortsetzungsprompt. Copilot liefert die Spezifikation Process (Markdown) mit Statuszeile, Attributtabelle, Satzregeln und Offene-Punkte-Liste.");
            default -> new JobInfo(
                    "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) die Spezifikation der Phase Write (Schreiben) für eine fachliche Entität zu erstellen – eine Zeile je geschriebenem Zielfeld.",
                    "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, Detailgrad; optional DDL der Altdatenbank und Zusatzkontext.",
                    "Das Referenzprojekt enthält die Entität bereits umgesetzt. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                    "Drei Dateien zum Hochladen in Copilot: Skill (nur Phase Write), Aufgabe und Code-Datei der Entität; im Protokoll Größe, Start- und Fortsetzungsprompt. Copilot liefert die Spezifikation Write (Markdown) mit Statuszeile, Feldtabelle, Satzregeln und Offene-Punkte-Liste.");
        };
    }

    @Override
    protected List<Param> ownParams() {
        return List.of(
                Param.choice(DETAIL, "Detailgrad der Spezifikation", DETAILED,
                        "Ausführlich: zusätzlich die einzelnen Schritte des Einlesens, Verarbeitens (je Attribut) und Schreibens",
                        new Param.Option(COMPACT, "Kompakt – Attributtabellen"),
                        new Param.Option(DETAILED, "Ausführlich – zusätzlich Schritte je Attribut")),
                Param.text(Keys.CONTEXT, "Zusatzkontext (KI)", "", false,
                        "Optional, z. B. Zielprodukt, Besonderheiten der neuen Migration, Ansprechpartner"),
                CopilotLimit.param());
    }

    @Override
    protected String taskFileName(Config config) {
        return "aufgabe-" + CodeCollectorTool.slug(config.list(Keys.TERMS).getFirst()) + ".md";
    }

    @Override
    protected String skillFileName() {
        return "skill-phasen-spezifikation.md";
    }

    /** Only the sections of the selected phases, in the order Read, Process, Write. */
    @Override
    protected String renderSkill(Config config) throws IOException {
        List<String> selected = selected(config);
        StringBuilder sections = new StringBuilder();
        for (String[] phase : PHASES) {
            if (selected.contains(phase[0])) {
                sections.append(Texts.resource(SKILL_RESOURCES + phase[1]).strip()).append("\n\n");
            }
        }
        return Texts.fill(Texts.resource(SKILL_RESOURCES + "skill.md"), Map.of("PHASE_SECTIONS", sections.toString().strip()));
    }

    /** Start prompt and the prompt for an incomplete result (same chat, no file), with the real file names. */
    @Override
    protected List<String> prompts(Config config, Path skill, Path task, List<Path> content) {
        String code = content.getFirst().getFileName().toString();
        String result = resultFileName(config);
        String start = "Bearbeite die Aufgabe in " + task.getFileName() + " nach den Regeln in " + skill.getFileName()
                + ". " + code + " ist nur Belegmaterial. Arbeite die Soll-Listen der Aufgabe Zeile für Zeile ab: "
                + "Jede Nr bekommt genau eine Tabellenzeile, ohne Auslassung und ohne Sammelzeile. Jede Regel braucht "
                + "einen Beleg Klasse.Methode aus " + code + "; was dort nicht steht, wird ein OPL-Punkt. Stelle das "
                + "Ergebnis als Markdown-Datei " + result + " zum Download bereit. Kannst du nicht alles liefern, "
                + "schreibe „Status: UNVOLLSTÄNDIG“ und die letzte vollständige Nr in die Zusammenfassung.";
        String next = "Die Spezifikation ist unvollständig. Setze sie mit der ersten fehlenden Nr der Soll-Liste fort, "
                + "im selben Tabellenformat. Wiederhole und ändere keine Zeile, die du schon geliefert hast. Liefere nur "
                + "die fehlenden Tabellenzeilen, die noch fehlenden Satzregeln und die neuen OPL-Punkte; zähle die "
                + "OPL-Nummern weiter. Stelle sie als Markdown-Datei " + result.replaceFirst("\\.md$", "-teil2.md")
                + " zum Download bereit, mit denselben Überschriften wie in der Aufgabe. Nenne im Chat die letzte "
                + "gelieferte Nr und ob jetzt alle Soll-Zeilen vorliegen. Liegen dir die hochgeladenen Dateien nicht "
                + "mehr vor, sag das, statt aus dem Gedächtnis zu ergänzen.";
        return List.of("Prompt: " + start, "Prompt bei UNVOLLSTÄNDIG: " + next);
    }

    /** C3: many target rows in one run – one run per phase or per table, merged by the job "Spezifikation prüfen". */
    @Override
    protected List<String> notes(Config config, Tool.Output content) {
        List<String> lines = new ArrayList<>();
        int rows = content.targetRows().values().stream().mapToInt(Integer::intValue).sum();
        if (rows > MAX_TARGET_ROWS) {
            String split = selected(config).size() > 1 ? "einen Lauf je Phase"
                    : content.coreTables() > 1 ? "einen Lauf je Tabelle über „Tabellen der Entität“" : null;
            if (split != null) {
                lines.add("Empfehlung: %d Soll-Zeilen in einem Lauf (mehr als %d) – %s starten; die Ergebnisse führt der "
                        .formatted(rows, MAX_TARGET_ROWS, split) + "Job „Spezifikation prüfen“ zusammen.");
            }
        }
        return lines;
    }

    /** C2: the recommendations not used yet, in this order. */
    @Override
    protected String limitSuggestions(Config config, Tool.Output content) {
        List<String> steps = new ArrayList<>();
        if (selected(config).size() > 1) {
            steps.add("nur eine Phase wählen");
        }
        if (!config.text(Keys.DDL).isEmpty() && config.list(Keys.TABLES).isEmpty()) {
            steps.add("Tabellen der Entität angeben");
        }
        if (config.list(Keys.EXCLUDE_FILES).isEmpty()) {
            steps.add("Dateien ausschließen" + (content.excludeCandidates().isEmpty() ? ""
                    : " (größte nicht geschützte Dateien: " + String.join(", ", content.excludeCandidates()) + ")"));
        }
        if (!config.text(Keys.SCOPE).equals("hits")) {
            steps.add("Umfang „Nur Klassen mit dem Begriff“");
        }
        if (steps.isEmpty()) {
            return "Alle Empfehlungen sind schon genutzt.";
        }
        StringBuilder text = new StringBuilder("Empfehlungen:");
        for (int i = 0; i < steps.size(); i++) {
            text.append(' ').append(i + 1).append(". ").append(steps.get(i)).append(i + 1 < steps.size() ? ";" : ".");
        }
        return text.toString();
    }

    @Override
    protected String renderTask(Config config, Tool.Output content) throws IOException {
        List<String> selected = selected(config);
        List<String> phases = PHASES.stream().filter(p -> selected.contains(p[0])).map(p -> p[2]).toList();
        List<String> excluded = PHASES.stream().filter(p -> !selected.contains(p[0])).map(p -> p[2]).toList();
        String terms = String.join(", ", config.list(Keys.TERMS));
        String files = content.files().stream().map(p -> "`" + p.getFileName() + "`").collect(Collectors.joining(", "));
        String context = config.text(Keys.CONTEXT);
        boolean detailed = !config.text(DETAIL).equals(COMPACT);
        java.util.Optional<MigrationProject> project = MigrationProject.find(config.text(Keys.PROJECT));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("OUTPUT", outputOutline(phases, detailed));
        values.put("DETAIL", detailed
                ? "**ausführlich** – Regeln als einzelne Schritte in den Zellen"
                : "**kompakt** – Regeln in einem Satz je Zelle");
        values.put("CONTEXT", context.isEmpty() ? "" : "**Zusatzkontext:** " + context);
        values.put("SKILL_FILE", skillFileName());
        values.put("RESULT_FILE", resultFileName(config));
        values.put("PHASES", String.join(", ", phases));
        values.put("EXCLUDED", excluded.isEmpty() ? "–" : String.join(", ", excluded));
        values.put("FILES", files);
        values.put("PROJECT", project.map(p -> p.label() + (p.sparte().isEmpty() ? "" : " (" + p.sparte() + ")"))
                .orElse("– (nicht angegeben)"));
        values.put("REFERENCE", project.filter(p -> !p.referenceLabel().isEmpty()
                        && p.reference() != null && p.reference().equals(config.path(Keys.BASE)))
                .map(p -> p.referenceLabel() + " · `" + config.text(Keys.BASE) + "`")
                .orElse("`" + config.text(Keys.BASE) + "`"));
        // after OUTPUT: the outline itself contains {{ENTITY}}
        values.put("ENTITY", config.list(Keys.TERMS).getFirst());
        values.put("TERMS", terms);
        // last: generated from DDL and target model, it is not scanned for placeholders
        values.put("INVENTORY", content.inventory());
        return Texts.collapseBlankLines(Texts.fill(Texts.resource(TASK_RESOURCES + "aufgabe.md"), values));
    }

    private static List<String> selected(Config config) {
        return config.list(Keys.PHASES).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
    }

    private static String resultFileName(Config config) {
        return "spezifikation-" + CodeCollectorTool.slug(config.list(Keys.TERMS).getFirst()) + ".md";
    }

    /** Numbered outline of the expected answer – depends on the selected phases and the level of detail. */
    private static String outputOutline(List<String> phases, boolean detailed) {
        List<String> items = new ArrayList<>();
        items.add("**Zusammenfassung** – erste Zeile „Status: VOLLSTÄNDIG“ oder „Status: UNVOLLSTÄNDIG – letzte "
                + "vollständige Zeile: <Nr>“. Danach " + (detailed ? "5–10 Sätze" : "höchstens 5 Sätze")
                + ": Was passiert mit der Entität „{{ENTITY}}“ im Referenzprojekt, welche Klassen tragen die Logik? "
                + "Danach die Kennzahlen je Phase: Sollzahl, gelieferte Zeilen, Hilfswerte, Zeilen je Prüfbedarf "
                + "([PRÜFEN] / [TECHNISCH] / [1:1]); dazu die Anzahl der OPL-Punkte.");
        items.add("**Ablaufübersicht** – Tabelle | Schritt | Klasse | Aufgabe |: Job und Steps in ihrer Reihenfolge "
                + "(nur der Ablauf, keine Attributregeln).");
        for (String phase : phases) {
            String tables = phase.equals("Write")
                    ? "die Feldtabelle Write (eine Zeile je geschriebenem Zielfeld) und die Satzregeln Write."
                    : "die Attributtabelle " + phase + " (eine Zeile je Nr der Liste „Soll " + phase + "“) und die "
                    + "Satzregeln " + phase + ".";
            items.add("**Phase " + phase + "** – genau zwei Tabellen wie im Skill, Abschnitt „Phase " + phase + "“: "
                    + tables);
        }
        items.add("**Offene-Punkte-Liste (OPL)** – alle Fragen, Annahmen und Lücken aus allen Phasen, "
                + "nummeriert OPL-1, OPL-2, …: | OPL-Nr | Frage | Betrifft (Nr der Zeile bzw. Satzregel) | "
                + "Warum offen | Annahme bis zur Klärung |.");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            out.append(i + 1).append(". ").append(items.get(i)).append('\n');
        }
        // the example names a heading that exists in this task
        out.append("\nJeder Punkt ist eine Überschrift der Ebene 2 mit genau diesem Namen (z. B. „## Phase ")
                .append(phases.getFirst()).append("“).\n");
        out.append("\nKeine weiteren Abschnitte oder Tabellen: Jede Information steht genau einmal.");
        return out.toString().strip();
    }
}
