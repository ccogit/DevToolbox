package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.JobInfo;
import de.devtoolbox.core.CodeSource;
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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    private static final String VARIANT = "variant";
    /** Log line naming the files Copilot delivers ({@link de.devtoolbox.core.Job#ANSWERS_PREFIX}). */
    private static final String ANSWERS_PREFIX = de.devtoolbox.core.Job.ANSWERS_PREFIX;
    /** Branches and errors one answer of the variant "in Etappen" has to describe (Startwert, zu kalibrieren). */
    static final int STAGE_WEIGHT = 30;

    /**
     * How the result gets its depth. {@link #TABLES} relies on the table cells; the others add the target list
     * "Soll Logik" counted from the code ({@link LogicInventory}) and a "Logikprotokoll" written method by method.
     * The letters name the attempts (A–C were the first round) and end up in the name of the result file.
     */
    enum Variant {
        /** Two tables per phase; the complete logic stands in the table cells. */
        TABLES("tables", ""),
        /** Logikprotokoll and tables in one answer, as a file. */
        LOGIC("logic", "d"),
        /** Logikprotokoll in several answers (one prompt per group of methods), tables in the last one. */
        STAGES("stages", "e"),
        /** As {@link #LOGIC}, but the answer is written into the chat instead of a file. */
        CHAT("chat", "f");

        private final String value;
        private final String letter;

        Variant(String value, String letter) {
            this.value = value;
            this.letter = letter;
        }

        static Variant of(Config config) {
            String value = config.text(VARIANT);
            for (Variant v : values()) {
                if (v.value.equals(value)) {
                    return v;
                }
            }
            return TABLES;
        }

        boolean logic() {
            return this != TABLES;
        }
    }

    /** The one phase this job is fixed to (READ, PROCESS or WRITE); null = the phases are a parameter. */
    private final String fixedPhase;

    /** The job of the UI: the phases are chosen with the parameter {@code phases} (id "phase-spec"). */
    public PhaseSpecJob() {
        this(null);
    }

    private PhaseSpecJob(String fixedPhase) {
        super(new CodeCollectorTool());
        this.fixedPhase = fixedPhase;
    }

    /** The job for exactly one phase (command line only): no "phases" field, everything only for that phase. */
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
                "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) eine attributweise Spezifikation für eine fachliche Entität im Zielprojekt zu erstellen – je gewählter Phase (Read, Process, Write) ein Abschnitt. Die Variante bestimmt, wie die Logik in die Tiefe beschrieben wird.",
                "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, betrachtete Phasen, Variante, Detailgrad; optional Struktur der Altdaten (DDL) mit den Tabellen der Entität, Zielmodell (JSON je Entität, bei Phase Process) und Zusatzkontext.",
                "Das Referenzprojekt enthält die Entität bereits umgesetzt. Bei Phase Process ist das Zielmodell nötig. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                "Drei Dateien zum Hochladen in Copilot: Skill (nur die gewählten Phasen), Aufgabe mit den nummerierten Soll-Listen (Read aus der DDL bzw. den Datenstrukturen, Process aus dem Zielmodell) und Code-Datei der Entität; im Protokoll Größe der Dateien, Start- und Fortsetzungsprompt. Copilot liefert eine Spezifikation (Markdown) mit Statuszeile und Zusammenfassung, Ablaufübersicht, je Phase Attribut- bzw. Feldtabelle und Satzregeln sowie einer Offene-Punkte-Liste. Bei den Varianten mit Logikprotokoll enthält die Aufgabe zusätzlich die aus dem Code ausgezählte Liste „Soll Logik“, und Copilot beschreibt jede Methode mit ihren Verzweigungen, bevor es die Tabellen füllt.");
    }

    private JobInfo phaseJobInfo() {
        return switch (fixedPhase) {
            case "READ" -> new JobInfo(
                    "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) die Spezifikation der Phase Read (Einlesen) für eine fachliche Entität zu erstellen – eine Zeile je Attribut der Altdaten.",
                    "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, Detailgrad; optional Struktur der Altdaten (DDL) mit den Tabellen der Entität und Zusatzkontext.",
                    "Das Referenzprojekt enthält die Entität bereits umgesetzt. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                    "Drei Dateien zum Hochladen in Copilot: Skill (nur Phase Read), Aufgabe mit der Soll-Liste Read (aus der DDL bzw. den Datenstrukturen) und Code-Datei der Entität; im Protokoll Größe, Start- und Fortsetzungsprompt. Copilot liefert die Spezifikation Read (Markdown) mit Statuszeile, Attributtabelle, Satzregeln und Offene-Punkte-Liste.");
            case "PROCESS" -> new JobInfo(
                    "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) die Spezifikation der Phase Process (Mapping) für eine fachliche Entität zu erstellen – eine Zeile je Zielattribut.",
                    "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, Zielmodell (JSON je Entität), Detailgrad; optional Struktur der Altdaten (DDL) und Zusatzkontext.",
                    "Das Referenzprojekt enthält die Entität bereits umgesetzt, das Zielmodell liegt vor. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                    "Drei Dateien zum Hochladen in Copilot: Skill (nur Phase Process), Aufgabe mit der Soll-Liste Process (aus dem Zielmodell) und Code-Datei der Entität; im Protokoll Größe, Start- und Fortsetzungsprompt. Copilot liefert die Spezifikation Process (Markdown) mit Statuszeile, Attributtabelle, Satzregeln und Offene-Punkte-Liste.");
            default -> new JobInfo(
                    "Unterstützt dich dabei, aus einer bereits umgesetzten Migration (Referenzprojekt) die Spezifikation der Phase Write (Schreiben) für eine fachliche Entität zu erstellen – eine Zeile je geschriebenem Zielfeld.",
                    "Referenzprojekt (Code), fachliche Entität mit Synonymen als Suchbegriffen, Detailgrad; optional Struktur der Altdaten (DDL) und Zusatzkontext.",
                    "Das Referenzprojekt enthält die Entität bereits umgesetzt. Du hast Zugriff auf Copilot und kannst dort drei Dateien hochladen.",
                    "Drei Dateien zum Hochladen in Copilot: Skill (nur Phase Write), Aufgabe und Code-Datei der Entität; im Protokoll Größe, Start- und Fortsetzungsprompt. Copilot liefert die Spezifikation Write (Markdown) mit Statuszeile, Feldtabelle, Satzregeln und Offene-Punkte-Liste.");
        };
    }

    @Override
    protected List<Param> ownParams() {
        return List.of(
                Param.choice(VARIANT, "Variante (Versuch)", Variant.STAGES.value,
                        "Wie die Logik in die Tiefe beschrieben wird. D–F zählen die Methoden und Verzweigungen aus "
                                + "dem Code aus (Soll Logik) und verlangen je Methode ein Logikprotokoll",
                        new Param.Option(Variant.TABLES.value, "A – Tabellen (Logik in den Zellen)"),
                        new Param.Option(Variant.LOGIC.value, "D – Logikprotokoll je Methode, eine Antwort als Datei"),
                        new Param.Option(Variant.STAGES.value, "E – Logikprotokoll in Etappen (ein Prompt je Methodengruppe)"),
                        new Param.Option(Variant.CHAT.value, "F – Logikprotokoll, Ausgabe im Chat statt als Datei")),
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
        String depthSections = Variant.of(config).logic()
                ? Texts.resource(SKILL_RESOURCES + "logikprotokoll.md").strip() : "";
        return Texts.collapseBlankLines(Texts.fill(Texts.resource(SKILL_RESOURCES + "skill.md"), Map.of(
                "PHASE_SECTIONS", sections.toString().strip(), "DEPTH_SECTIONS", depthSections)));
    }

    /**
     * The prompts with the real file names: start and continuation, for the variant "in Etappen" one per stage.
     * The first line starts with "Prompt: " (the UI copies it), the others name what they are for.
     */
    @Override
    protected List<String> prompts(Config config, Path skill, Path task, List<Path> content) {
        String code = content.getFirst().getFileName().toString();
        String result = resultFileName(config);
        Variant variant = Variant.of(config);
        LogicInventory logic = logic(content.getFirst());
        String intro = "Bearbeite die Aufgabe in " + task.getFileName() + " nach den Regeln in " + skill.getFileName()
                + ". " + code + " ist nur Belegmaterial. Lies sie zuerst vollständig (" + logic.files()
                + " Dateien zwischen BEGIN FILE und END FILE); kannst du nicht alle lesen, sag das als Erstes, "
                + "statt zu raten. ";
        String lost = " Liegen dir die hochgeladenen Dateien nicht mehr vor, sag das, statt aus dem Gedächtnis "
                + "zu ergänzen.";
        if (!variant.logic() || logic.isEmpty()) {
            String start = intro + "Arbeite die Soll-Listen der Aufgabe Zeile für Zeile ab: "
                    + "Jede Nr bekommt genau eine Tabellenzeile, ohne Auslassung und ohne Sammelzeile. Jede Regel "
                    + "braucht einen Beleg Klasse.Methode aus " + code + "; was dort nicht steht, wird ein OPL-Punkt. "
                    + "Beschreibe die Logik vollständig und kürze nicht: jede Verzweigung und jeder Sonderfall mit "
                    + "Bedingung und Ergebnis, Werte und Codelisten ausgeschrieben, je Lookup ohne sichtbare Werte "
                    + "ein OPL-Punkt. Stelle das Ergebnis als Markdown-Datei " + result + " zum Download bereit; "
                    + "die Datei enthält den vollständigen Text, keine Verweise auf den Chat. Kannst du nicht alles "
                    + "liefern, schreibe „Status: UNVOLLSTÄNDIG“ und die letzte vollständige Nr in die "
                    + "Zusammenfassung.";
            String next = "Die Spezifikation ist unvollständig. Setze sie mit der ersten fehlenden Nr der Soll-Liste "
                    + "fort, im selben Tabellenformat. Wiederhole und ändere keine Zeile, die du schon geliefert "
                    + "hast. Liefere nur die fehlenden Tabellenzeilen, die noch fehlenden Satzregeln und die neuen "
                    + "OPL-Punkte; zähle die OPL-Nummern weiter. Stelle sie als Markdown-Datei "
                    + part(result, "teil2") + " zum Download bereit, mit denselben Überschriften wie in der "
                    + "Aufgabe. Nenne im Chat die letzte gelieferte Nr und ob jetzt alle Soll-Zeilen vorliegen."
                    + lost;
            return List.of("Prompt: " + start, "Prompt bei UNVOLLSTÄNDIG: " + next);
        }
        String protocol = "je Nr der Liste „Soll Logik“ ein Abschnitt, je Verzweigung und Fehlerfall eine "
                + "Regelzeile mit dem Original-Ausdruck aus " + code + ", alle Werte wörtlich";
        String noCut = " Kürze nicht und fasse keine Zweige zusammen; für die Länge gibt es keine Obergrenze.";
        if (variant == Variant.STAGES) {
            List<int[]> stages = logic.stages(STAGE_WEIGHT);
            int total = stages.size() + 1;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < stages.size(); i++) {
                String range = range(stages.get(i));
                String file = part(result, "logik-" + (i + 1));
                lines.add(i == 0
                        ? "Prompt: " + intro + "Die Aufgabe wird in " + total + " Etappen bearbeitet (Abschnitt "
                        + "„Etappen“). Bearbeite jetzt nur Etappe 1: das Logikprotokoll für " + range + " – "
                        + protocol + ". Keine Zusammenfassung und keine Tabellen der Phasen." + noCut
                        + " Stelle das Ergebnis als Markdown-Datei " + file + " zum Download bereit; die Datei "
                        + "enthält den vollständigen Text. Nenne im Chat die letzte vollständige Nr."
                        : "Prompt Etappe " + (i + 1) + " von " + total + ": Bearbeite jetzt Etappe " + (i + 1)
                        + " der Aufgabe: das Logikprotokoll für " + range + ", in Form und Tiefe wie in Etappe 1 – "
                        + protocol + ". Wiederhole nichts aus den früheren Etappen." + noCut
                        + " Stelle das Ergebnis als Markdown-Datei " + file + " zum Download bereit. Nenne im "
                        + "Chat die letzte vollständige Nr." + lost);
            }
            lines.add("Prompt Etappe " + total + " von " + total + " (Abschluss): Bearbeite jetzt die letzte Etappe "
                    + "der Aufgabe: Zusammenfassung, Ablaufübersicht, die Tabellen der Phasen und die OPL. Jede Nr "
                    + "der Soll-Listen bekommt genau eine Zeile; die Spalte „Beleg“ nennt die Regelnummern aus "
                    + "deinen Logikprotokollen der Etappen 1 bis " + stages.size() + ". Übernimm die offenen "
                    + "Punkte aller Etappen in die OPL und nummeriere sie OPL-1, OPL-2, … Wiederhole das "
                    + "Logikprotokoll nicht. Stelle das Ergebnis als Markdown-Datei " + result + " zum Download "
                    + "bereit." + lost);
            lines.add("Prompt, wenn eine Etappe abbricht: Die Etappe ist unvollständig. Setze sie mit der ersten "
                    + "fehlenden Nr fort, im selben Format, und wiederhole nichts. Stelle die Fortsetzung als "
                    + "eigene Markdown-Datei mit dem Zusatz -fortsetzung im Namen bereit." + lost);
            return lines;
        }
        String order = "Arbeite in dieser Reihenfolge: 1. Logikprotokoll – " + protocol + ". 2. Tabellen der "
                + "Phasen – je Nr der Soll-Listen genau eine Zeile, belegt mit den Regelnummern des "
                + "Logikprotokolls. 3. OPL – was " + code + " nicht zeigt, je Lookup ein Punkt." + noCut;
        if (variant == Variant.CHAT) {
            String start = intro + order + " Gib das Ergebnis direkt im Chat als Markdown aus, nicht als Datei "
                    + "und ohne Vorrede. Reicht eine Antwort nicht, höre nach einem vollständigen Abschnitt auf "
                    + "und schreibe als letzte Zeile „WEITER ab <Nr>“; am Ende des ganzen Ergebnisses steht "
                    + "„FERTIG“.";
            String next = "Weiter ab der genannten Nr, im selben Format und in derselben Tiefe. Wiederhole nichts, "
                    + "was du schon ausgegeben hast. Schreibe als letzte Zeile wieder „WEITER ab <Nr>“ oder, wenn "
                    + "alles geliefert ist, „FERTIG“." + lost;
            return List.of("Prompt: " + start, "Prompt für jede Fortsetzung: " + next);
        }
        String start = intro + order + " Stelle das Ergebnis als Markdown-Datei " + result + " zum Download bereit; "
                + "die Datei enthält den vollständigen Text, keine Verweise auf den Chat. Kannst du nicht alles "
                + "liefern, höre nach einem vollständigen Abschnitt auf und schreibe „Status: UNVOLLSTÄNDIG“ und die "
                + "letzte vollständige Nr in die Zusammenfassung.";
        String next = "Die Spezifikation ist unvollständig. Setze sie mit der ersten fehlenden Nr fort: zuerst die "
                + "fehlenden Abschnitte des Logikprotokolls, dann die fehlenden Tabellenzeilen und Satzregeln, dann "
                + "die neuen OPL-Punkte (Nummern weiterzählen). Wiederhole und ändere nichts, was du schon geliefert "
                + "hast. Stelle die Fortsetzung als Markdown-Datei " + part(result, "teil2") + " zum Download "
                + "bereit, mit denselben Überschriften wie in der Aufgabe. Nenne im Chat die letzte gelieferte Nr."
                + lost;
        return List.of("Prompt: " + start, "Prompt bei UNVOLLSTÄNDIG: " + next);
    }

    /** "spezifikation-x.md" → "spezifikation-x-teil2.md". */
    private static String part(String result, String suffix) {
        return result.replaceFirst("\\.md$", "-" + suffix + ".md");
    }

    private static String range(int[] stage) {
        String first = LogicInventory.number(stage[0]);
        return stage[0] == stage[1] ? first : first + " bis " + LogicInventory.number(stage[1]);
    }

    /** The target list "Soll Logik" of the code file the tool wrote. */
    private static LogicInventory logic(Path codeFile) {
        try {
            return LogicInventory.of(Files.readString(codeFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
        List<String> answers = answerFiles(config, content);
        if (!answers.isEmpty()) {
            // the UI links these files as soon as they appear in the download folder
            lines.add(ANSWERS_PREFIX + String.join(", ", answers));
        }
        return lines;
    }

    /** The files Copilot is asked to deliver, in their order; none if the answer goes into the chat. */
    private static List<String> answerFiles(Config config, Tool.Output content) {
        String result = resultFileName(config);
        Variant variant = Variant.of(config);
        if (variant == Variant.CHAT) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (variant == Variant.STAGES) {
            int stages = logic(content.files().getFirst()).stages(STAGE_WEIGHT).size();
            for (int i = 1; i <= stages; i++) {
                names.add(part(result, "logik-" + i));
            }
        }
        names.add(result);
        return names;
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
        LogicInventory logic = logic(content.files().getFirst());
        // nothing to count (no Java with logic): the tables carry everything
        Variant variant = logic.isEmpty() ? Variant.TABLES : Variant.of(config);
        boolean detailed = variant.logic() || !config.text(DETAIL).equals(COMPACT);
        String result = resultFileName(config);
        java.util.Optional<MigrationProject> project = MigrationProject.find(config.text(Keys.PROJECT));

        Map<String, String> values = new LinkedHashMap<>();
        values.put("OUTPUT", outputOutline(phases, detailed, variant, logic));
        values.put("STAGES", variant == Variant.STAGES ? stagesSection(logic, result) : "");
        values.put("DELIVERY", delivery(variant, result));
        values.put("DEPTH_CHECKS", depthChecks(detailed, variant));
        values.put("DETAIL", !detailed ? "**kompakt** – Regeln in einem Satz je Zelle"
                : !variant.logic() ? "**ausführlich** – Regeln als einzelne Schritte in den Zellen"
                : "**ausführlich** – vollständige Logik im Logikprotokoll, Zusammenfassung in den Zellen");
        values.put("CONTEXT", context.isEmpty() ? "" : "**Zusatzkontext:** " + context);
        values.put("SKILL_FILE", skillFileName());
        values.put("RESULT_FILE", result);
        values.put("PHASES", String.join(", ", phases));
        values.put("EXCLUDED", excluded.isEmpty() ? "–" : String.join(", ", excluded));
        values.put("FILES", files + " – " + logic.files() + " Quelldateien, jede zwischen `===== BEGIN FILE` und "
                + "`===== END FILE`");
        values.put("PROJECT", project.map(p -> p.label() + (p.sparte().isEmpty() ? "" : " (" + p.sparte() + ")"))
                .orElse("– (nicht angegeben)"));
        String base = CodeSource.mask(config.text(Keys.BASE));
        values.put("REFERENCE", project.filter(p -> !p.referenceLabel().isEmpty() && p.referenceUrl().isEmpty()
                        && p.isReference(config.text(Keys.BASE)))
                .map(p -> p.referenceLabel() + " · `" + base + "`")
                .orElse("`" + base + "`"));
        // after OUTPUT: the outline itself contains {{ENTITY}}
        values.put("ENTITY", config.list(Keys.TERMS).getFirst());
        values.put("TERMS", terms);
        // last: generated from DDL, target model and code, they are not scanned for placeholders
        values.put("INVENTORY", content.inventory());
        values.put("LOGIC", variant.logic() ? logic.markdown() : "");
        return Texts.collapseBlankLines(Texts.fill(Texts.resource(TASK_RESOURCES + "aufgabe.md"), values));
    }

    private static List<String> selected(Config config) {
        return config.list(Keys.PHASES).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
    }

    /** The attempts D–F carry their letter, so results of different variants stay distinguishable. */
    private static String resultFileName(Config config) {
        String letter = Variant.of(config).letter;
        return "spezifikation-" + CodeCollectorTool.slug(config.list(Keys.TERMS).getFirst())
                + (letter.isEmpty() ? "" : "-" + letter) + ".md";
    }

    /** The section "Abgabe" of the task. */
    private static String delivery(Variant variant, String result) {
        return switch (variant) {
            case CHAT -> "Gib das Ergebnis **direkt im Chat als Markdown** aus, nicht als Datei und ohne Vorrede. "
                    + "Reicht eine Antwort nicht, höre nach einem vollständigen Abschnitt auf und schreibe als "
                    + "letzte Zeile `WEITER ab <Nr>`; nach dem letzten Abschnitt steht `FERTIG`.";
            case STAGES -> "Jede Etappe wird als eigene **Markdown-Datei zum Download** bereitgestellt (Namen im "
                    + "Abschnitt „Etappen“). Jede Datei enthält den vollständigen Text ihrer Etappe – keine "
                    + "Platzhalter und keine Verweise auf den Chat oder auf andere Dateien. Im Chat steht nur die "
                    + "letzte vollständige Nr.";
            default -> "Stelle `" + result + "` als **Markdown-Datei zum Download** bereit. Die Datei enthält den "
                    + "vollständigen Text – keine Platzhalter und keine Verweise auf den Chat. Im Chat stehen nur "
                    + "der Status und die Kennzahlen, nicht der Inhalt der Datei.";
        };
    }

    /** The section "Etappen" of the task: which answer delivers what. */
    private static String stagesSection(LogicInventory logic, String result) {
        List<int[]> stages = logic.stages(STAGE_WEIGHT);
        StringBuilder md = new StringBuilder("## Etappen\n\n");
        md.append("Das Ergebnis entsteht in ").append(stages.size() + 1).append(" Antworten, damit keine gekürzt "
                + "werden muss. Jeder Prompt nennt seine Etappe; bearbeite immer nur diese.\n\n");
        md.append("| Etappe | Inhalt | Datei |\n|---|---|---|\n");
        for (int i = 0; i < stages.size(); i++) {
            md.append("| ").append(i + 1).append(" | Logikprotokoll ").append(range(stages.get(i))).append(" | `")
                    .append(part(result, "logik-" + (i + 1))).append("` |\n");
        }
        md.append("| ").append(stages.size() + 1).append(" | Zusammenfassung, Ablaufübersicht, Tabellen der Phasen, "
                + "OPL | `").append(result).append("` |\n\n");
        md.append("- Eine Etappe des Logikprotokolls enthält nur die Unterabschnitte ihrer Nummern, unter der "
                + "Überschrift `## Logikprotokoll`, und am Ende die Liste `## Offene Punkte dieser Etappe` (ohne "
                + "OPL-Nummern; jeder Punkt nennt die Regel-Nr, die er betrifft).\n");
        md.append("- Die letzte Etappe enthält das Logikprotokoll nicht noch einmal. Ihre Tabellen belegen jede "
                + "Zeile mit den Regelnummern der früheren Etappen, und ihre OPL sammelt die offenen Punkte aller "
                + "Etappen als OPL-1, OPL-2, …\n");
        md.append("- Die Punkte unter „Vor der Abgabe prüfen“ gelten für jede Etappe, soweit sie ihren Inhalt "
                + "betreffen.");
        return md.toString();
    }

    /** Further points of "Vor der Abgabe prüfen", numbered from 6. */
    private static String depthChecks(boolean detailed, Variant variant) {
        List<String> checks = new ArrayList<>();
        if (variant.logic()) {
            checks.add("Jede Nr der Soll Logik hat genau einen Unterabschnitt im Logikprotokoll.");
            checks.add("Jeder Unterabschnitt hat mindestens so viele Regelzeilen, wie die Soll Logik Verzweigungen "
                    + "plus Fehler nennt (sonst steht dort, warum), und jede Regelzeile hat ihren Original-Ausdruck "
                    + "aus dem Code-Kontext.");
            checks.add("Jeder Wert der Spalte „Werte im Code“ und jede verwendete Konstante der Soll Konstanten "
                    + "kommt im Ergebnis wörtlich vor.");
            checks.add("Jede Regelnummer in einer Spalte „Beleg“ gibt es im Logikprotokoll, und jede Regel, die eine "
                    + "Soll-Zeile betrifft, ist in deren Zeile belegt.");
            checks.add("Das Ergebnis enthält den vollständigen Text: keine Platzhalter, kein „siehe oben“, kein "
                    + "Verweis auf den Chat.");
        } else if (detailed) {
            checks.add("Jede Verzweigung, jeder Sonderfall, jeder Default und jede Validierung der belegten Methoden "
                    + "steht als eigener Schritt oder als Satzregel da, mit Bedingung und Ergebnis.");
            checks.add("Jeder Schritt hat seine eigene Fundstelle.");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < checks.size(); i++) {
            out.append(i + 6).append(". ").append(checks.get(i)).append('\n');
        }
        return out.toString().strip();
    }

    /** Numbered outline of the expected answer – depends on the selected phases and the level of detail. */
    private static String outputOutline(List<String> phases, boolean detailed, Variant variant, LogicInventory logic) {
        List<String> items = new ArrayList<>();
        items.add("**Zusammenfassung** – erste Zeile „Status: VOLLSTÄNDIG“ oder „Status: UNVOLLSTÄNDIG – letzte "
                + "vollständige Zeile: <Nr>“. Danach " + (detailed ? "8–15 Sätze" : "höchstens 5 Sätze")
                + ": Was passiert mit der Entität „{{ENTITY}}“ im Referenzprojekt, welche Klassen tragen die Logik"
                + (detailed ? ", welche Sonderregeln und Ausnahmen gibt es" : "") + "? "
                + "Danach die Kennzahlen je Phase: Sollzahl, gelieferte Zeilen, Hilfswerte, Zeilen je Prüfbedarf "
                + "([PRÜFEN] / [TECHNISCH] / [1:1]); dazu die Anzahl der OPL-Punkte und die Zahl der gelesenen "
                + "Quelldateien des Code-Kontexts (von " + logic.files() + ")."
                + (variant.logic() ? " Dazu die Kennzahlen des Logikprotokolls: Methoden (Soll "
                + logic.methods().size() + "), Regelzeilen (Soll mindestens "
                + logic.methods().stream().mapToInt(m -> m.branches() + m.errors()).sum() + ")." : ""));
        items.add("**Ablaufübersicht** – Tabelle | Schritt | Klasse | Aufgabe |: Job und Steps in ihrer Reihenfolge "
                + "(nur der Ablauf, keine Attributregeln).");
        if (variant.logic()) {
            items.add("**Logikprotokoll** – je Nr der Liste „Soll Logik“ ein Unterabschnitt (Ebene 3) wie im Skill, "
                    + "Abschnitt „Logikprotokoll“: Zweck, Regeln (eine Zeile je Verzweigung und Fehlerfall, mit "
                    + "Original-Ausdruck), Werte, Fehler und Abbruch, Aufrufe. Schreibe diesen Abschnitt zuerst.");
        }
        for (String phase : phases) {
            String tables = phase.equals("Write")
                    ? "die Feldtabelle Write (eine Zeile je geschriebenem Zielfeld) und die Satzregeln Write."
                    : "die Attributtabelle " + phase + " (eine Zeile je Nr der Liste „Soll " + phase + "“) und die "
                    + "Satzregeln " + phase + ".";
            String more = variant.logic() ? " Die Spalte „Beleg“ nennt die Regelnummern des Logikprotokolls." : "";
            items.add("**Phase " + phase + "** – genau zwei Tabellen wie im Skill, Abschnitt „Phase " + phase + "“: "
                    + tables + more);
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
        out.append(variant.logic()
                ? "\nKeine weiteren Abschnitte oder Tabellen. Für die Länge gibt es keine Obergrenze."
                : "\nKeine weiteren Abschnitte oder Tabellen: Jede Information steht genau einmal.");
        return out.toString().strip();
    }
}
