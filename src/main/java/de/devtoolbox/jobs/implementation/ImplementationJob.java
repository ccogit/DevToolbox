package de.devtoolbox.jobs.implementation;

import de.devtoolbox.core.JobInfo;
import de.devtoolbox.core.Config;
import de.devtoolbox.core.CopilotLimit;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.OutputDirectory;
import de.devtoolbox.core.Param.Option;
import de.devtoolbox.core.Texts;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;
import de.devtoolbox.tools.codecollector.SourceReader;
import de.devtoolbox.tools.codecollector.TargetModel;
import de.devtoolbox.tools.standards.StandardsTool;
import de.devtoolbox.tools.standards.StandardsTool.Row;
import de.devtoolbox.tools.standards.StandardsTool.Stored;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the selected batch phases of an entity from an approved specification (Markdown or PDF, the
 * reviewed result of the specification job), applying the project's stored and possibly edited {@linkplain StandardsTool standards}.
 * Output for Copilot: skill + task (incl. standards) + the specification.
 */
public final class ImplementationJob implements Job {

    public static final String STANDARDS = Keys.STANDARDS;
    private static final String SPEC = Keys.SPEC;
    private static final String ENTITY = Keys.ENTITY;
    private static final String SKILL_FILE = "skill-implementierung.md";

    @Override
    public String id() {
        return "implementation";
    }

    @Override
    public String name() {
        return "Implementierung nach Spezifikation (je Phase, fachliche Entität)";
    }

    @Override
    public String fileTag() {
        return "implementierung";
    }

    @Override
    public JobInfo info() {
        return new JobInfo(
                "Unterstützt dich dabei, eine abgestimmte Spezifikation für eine Entität nach den Standards des Migrationsprojekts umzusetzen – Copilot implementiert die gewählten Phasen (Read, Process, Write) nach diesen Vorlagen.",
                "Standards-Ordner des Projekts, abgestimmte Spezifikation (Markdown oder PDF), fachliche Entität, zu implementierende Phasen; optional Zielmodell (JSON je Entität) und Zusatzkontext.",
                "Die Standards liegen vor (im Projekt oder übergreifend erzeugt). Die Spezifikation ist vom Fachbereich abgestimmt. Bei Phase Process ist das Zielmodell nötig. Copilot-Zugriff mit drei Dateien pro Prompt.",
                "Drei Dateien zum Hochladen in Copilot: Skill, Aufgabe (mit den Standards) und die Spezifikation. Copilot liefert die implementierten Klassen der gewählten Phasen als ZIP samt Abdeckungstabelle und Offene-Punkte-Liste.");
    }

    @Override
    public List<Param> params() {
        return List.of(
                Param.directory(STANDARDS, "Standards (Ordner)", true,
                        "Standards des Projekts (migrationsprojekte/<id>/standards) – im Projekt-Editor erzeugen"),
                Param.file(SPEC, "Spezifikation (Markdown oder PDF)", true,
                        "Vom Fachbereich abgestimmtes Ergebnis der Spezifikation (.md oder .pdf)", "md", "pdf"),
                Param.text(ENTITY, "Fachliche Entität", "", true, "Die neu zu implementierende Entität, z. B. Risiko"),
                Param.multi(Keys.PHASES, "Zu implementierende Phasen", "READ,PROCESS,WRITE",
                        "Es werden die Standards dieser Phasen angewendet",
                        new Option("READ", "Read"), new Option("PROCESS", "Process"), new Option("WRITE", "Write")),
                Param.directory(Keys.TARGET_MODEL, "Zielmodell (JSON je Entität)", false,
                        "Pflicht bei Phase Process: Ordner mit einer JSON-Datei je Entität – legt die Zielattribute fest"),
                Param.text(Keys.CONTEXT, "Zusatzkontext (KI)", "", false,
                        "Optional, z. B. Besonderheiten, bekannte Abweichungen"),
                Param.directory(Keys.OUTPUT, "Ausgabeordner", false, "Leer = Downloads").advanced(),
                CopilotLimit.param());
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        String entity = StandardsTool.className(config.text(ENTITY));
        if (entity.isEmpty()) {
            throw new IllegalArgumentException("Die fachliche Entität fehlt.");
        }
        Path spec = config.path(SPEC);
        if (spec == null || !Files.isRegularFile(spec)) {
            throw new IllegalArgumentException("Spezifikation nicht gefunden: " + config.text(SPEC));
        }
        String ext = checkSpec(spec);
        // read before the run folder is cleaned: the source may lie in it
        byte[] specBytes = Files.readAllBytes(spec);
        List<String> phases = phases(config);
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("Mindestens eine Phase auswählen.");
        }
        Path standardsDir = config.path(STANDARDS);
        if (standardsDir == null) {
            throw new IllegalArgumentException("Standards fehlen – im Projekt-Editor über „Standards erzeugen…“ anlegen.");
        }
        Stored standards = StandardsTool.load(standardsDir);
        List<Row> rows = standards.rows().stream().filter(r -> inPhases(r, phases)).toList();
        log.accept("Standards: " + rows.size() + " von " + standards.rows().size() + " Vorlagen für " + String.join(", ", phases)
                + " (Beispiel-Entität " + standards.example() + ")");
        if (rows.isEmpty()) {
            log.accept("WARNUNG: Für die gewählten Phasen enthalten die Standards keine Vorlagen.");
        }

        boolean process = phases.contains("Process");
        Path targetModel = config.path(Keys.TARGET_MODEL);
        if (process) {
            TargetModel.requireFolder(targetModel);
        }
        TargetModel.Result target = process ? TargetModel.find(targetModel, List.of(entity))
                : TargetModel.Result.none();
        if (process) {
            log.accept(target.file() == null ? "WARNUNG: Keine Zielmodell-Datei für " + entity + " in " + targetModel
                    : "Zielattribute: " + target.attributes().size() + " von " + target.entity() + " aus "
                    + target.file().getFileName());
        }

        String slug = CodeCollectorTool.slug(entity);
        Path dir = OutputDirectory.runFolder(config, fileTag(), slug);
        OutputDirectory.prepare(dir);
        Path skill = dir.resolve(SKILL_FILE);
        Path task = dir.resolve("aufgabe-implementierung-" + slug + ".md");
        Path specCopy = dir.resolve("spezifikation-" + slug + "." + ext);
        Files.write(specCopy, specBytes);
        Files.writeString(skill, Texts.resource("/skills/implementation/skill.md"), StandardCharsets.UTF_8);
        Files.writeString(task, renderTask(config, entity, phases, standards, rows, target, process,
                specCopy.getFileName().toString(), log), StandardCharsets.UTF_8);

        List<Path> files = new ArrayList<>(List.of(skill, task, specCopy));
        log.accept("Ordner: " + dir);
        log.accept("Aufgabe geschrieben: " + task);
        log.accept("Hochladen (3 Dateien): " + files.stream().map(p -> p.getFileName().toString())
                .collect(Collectors.joining(", ")));
        log.accept("Prompt: Bearbeite die Aufgabe in " + task.getFileName() + " mit dem Skill " + SKILL_FILE
                + "; die Spezifikation steht in " + specCopy.getFileName() + ".");
        boolean pdf = ext.equals("pdf");
        CopilotLimit.check(config, pdf ? List.of(skill, task) : List.of(skill, task, specCopy), pdf,
                "Aufgabe verkleinern: weniger Phasen wählen oder weniger Zeilen in den Standards (Vorlagen) behalten.", log);
        return files;
    }

    private String renderTask(Config config, String entity, List<String> phases, Stored standards, List<Row> rows,
                              TargetModel.Result target, boolean process, String specFile, Consumer<String> log)
            throws IOException {
        String context = config.text(Keys.CONTEXT);
        String project = MigrationProject.find(config.text(Keys.PROJECT))
                .map(p -> p.label() + (p.sparte().isEmpty() ? "" : " (" + p.sparte() + ")")).orElse("–");
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ARTIFACTS", artifactsTable(rows, entity));
        values.put("EXAMPLE_PATH", rows.isEmpty() ? "src/main/java/…"
                : "`" + StandardsTool.apply(rows.getFirst().pathOrDefault(), entity) + "`");
        values.put("TARGET_ATTRIBUTES", targetSection(target, process));
        values.put("COVERAGE", target.file() != null
                ? "eine Zeile je **Zielattribut** aus dem Abschnitt „Zielattribute (Zielmodell)“: | Zielattribut | "
                + "Herkunft (Attribut der Spezifikation bzw. „abgeleitet“) | je implementierter Phase eine Spalte "
                + "(Read: Feld je Datenobjekt + befüllt in Klasse.Methode; Process: Zielfeld + Klasse.Methode; "
                + "Write: Zielfeld + Klasse.Methode) | OPL |. Zielattribute ohne Umsetzung und Attribute der "
                + "Spezifikation, die in keinem Zielattribut ankommen, sind OPL-Punkte."
                : "eine Zeile je Attribut der Spezifikation: | Attribut | je implementierter Phase eine Spalte "
                + "(Read: Feld je Datenobjekt + befüllt in Klasse.Methode; Process: Zielfeld + Klasse.Methode; "
                + "Write: Zielfeld + Klasse.Methode) | OPL |");
        values.put("CONTEXT", context.isEmpty() ? "" : "**Zusatzkontext:** " + context);
        values.put("SKILL_FILE", SKILL_FILE);
        values.put("RESULT_FILE", "implementierung-" + CodeCollectorTool.slug(entity) + ".zip");
        values.put("SPEC_FILE", specFile);
        values.put("PROJECT", project);
        values.put("EXAMPLE", standards.example().isEmpty() ? "–" : standards.example());
        values.put("PHASES", String.join(", ", phases));
        values.put("ENTITY", entity);
        String task = Texts.collapseBlankLines(Texts.fill(Texts.resource("/aufgaben/implementation/aufgabe.md"), values));
        return task.strip() + "\n\n" + standardsSection(standards, rows, entity, log);
    }

    /** One row per artefact to create; patterns resolved for the new entity. */
    private static String artifactsTable(List<Row> rows, String entity) {
        if (rows.isEmpty()) {
            return "Die Standards enthalten für die gewählten Phasen keine Vorlagen. Orientiere dich an den Konventionen "
                    + "des Projekts, soweit erkennbar; jede Annahme wird ein OPL-Punkt.";
        }
        StringBuilder t = new StringBuilder("| Nr | Phase | Artefakt | Neue Klasse | Datei (Pfad im Zielprojekt) | Vorlage | "
                + "Basisklasse / Schnittstellen | Klassen-Annotationen | Hinweise |\n|---|---|---|---|---|---|---|---|---|\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            t.append("| ").append(i + 1).append(" | ").append(r.phase()).append(" | ").append(r.kind()).append(" | `")
                    .append(StandardsTool.apply(r.className(), entity)).append("` | `")
                    .append(StandardsTool.apply(r.pathOrDefault(), entity)).append("` | Vorlage ").append(i + 1).append(" | ")
                    .append(r.supers().isEmpty() ? "–" : "`" + StandardsTool.apply(r.supers(), entity) + "`").append(" | ")
                    .append(r.annotations().isEmpty() ? "–" : r.annotations()).append(" | ")
                    .append(r.notes().isEmpty() ? "–" : r.notes()).append(" |\n");
        }
        return t.toString().strip();
    }

    /** The templates as stored (with placeholders) and the base types. */
    private static String standardsSection(Stored standards, List<Row> rows, String entity, Consumer<String> log) {
        StringBuilder md = new StringBuilder("## Standards (Vorlagen des Projekts)\n\n");
        md.append("Abgeleitet von der Beispiel-Entität **").append(standards.example()).append("** und ggf. vom Team ")
                .append("überarbeitet. Platzhalter: `").append(StandardsTool.CLASS).append("` = ").append(entity)
                .append(", `").append(StandardsTool.LOWER).append("` = ").append(entity.toLowerCase(Locale.ROOT))
                .append(", `").append(StandardsTool.UPPER).append("` = ").append(entity.toUpperCase(Locale.ROOT))
                .append(". Felder und fachliche Logik in den Vorlagen sind Beispiele – sie werden durch die Attribute und ")
                .append("Regeln der Spezifikation ersetzt.\n");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            md.append("\n### Vorlage ").append(i + 1).append(": ").append(r.template()).append("\n\n");
            Path file = standards.dir().resolve(r.template());
            String code = r.template().isEmpty() ? null : SourceReader.read(file);
            if (code == null) {
                log.accept("WARNUNG: Vorlagendatei fehlt: " + file);
                md.append("(Vorlagendatei fehlt – nach Klassenmuster und Konventionen anlegen.)\n");
            } else {
                appendCode(md, r.template(), code);
            }
        }
        if (!standards.baseFiles().isEmpty()) {
            md.append("\n### Basisklassen und Schnittstellen\n\n")
                    .append("Neue Klassen verwenden sie genauso; sie werden nicht neu angelegt.\n");
            for (Path base : standards.baseFiles()) {
                String code = SourceReader.read(base);
                if (code != null) {
                    md.append("\n#### ").append(base.getFileName().toString().replaceFirst("\\.java$", "")).append("\n\n");
                    appendCode(md, "basis/" + base.getFileName(), code);
                }
            }
        }
        return md.toString();
    }

    private static void appendCode(StringBuilder md, String name, String code) {
        md.append(Texts.fileBlock(name, "java", code.strip()));
    }

    /** A row belongs to the task if its phase cell names one of the selected phases ("Read", "Read/Write" …). */
    private static boolean inPhases(Row row, List<String> phases) {
        String cell = row.phase().toLowerCase(Locale.ROOT);
        return phases.stream().anyMatch(p -> cell.contains(p.toLowerCase(Locale.ROOT)));
    }

    /** Selected phases as display names in batch order: "Read", "Process", "Write". */
    private static List<String> phases(Config config) {
        List<String> selected = config.list(Keys.PHASES).stream().map(String::toUpperCase).toList();
        return Stream.of("READ", "PROCESS", "WRITE").filter(selected::contains)
                .map(p -> p.charAt(0) + p.substring(1).toLowerCase(Locale.ROOT)).toList();
    }

    private static String targetSection(TargetModel.Result target, boolean process) {
        if (!process) {
            return "";
        }
        StringBuilder md = new StringBuilder("## Zielattribute (Zielmodell)\n\n");
        if (target.file() == null) {
            return md.append("Zu dieser Entität gibt es im Zielmodell keine JSON-Datei. Die Zielfelder ergeben sich aus ")
                    .append("der Spezifikation; das fehlende Zielmodell ist ein OPL-Punkt.").toString();
        }
        md.append("Entität **").append(target.entity()).append("** aus `").append(target.file().getFileName())
                .append("` (").append(target.attributes().size())
                .append(" Attribute). Diese Liste legt die Felder der Zielobjekte und alles fest, was das Mapping ")
                .append("befüllen muss.\n\n");
        boolean details = target.hasDetails();
        md.append(details ? "| Nr | Zielattribut | Angaben im Zielmodell |\n|---|---|---|\n" : "| Nr | Zielattribut |\n|---|---|\n");
        for (int i = 0; i < target.attributes().size(); i++) {
            String name = target.attributes().get(i);
            md.append("| ").append(i + 1).append(" | ").append(name.replace("|", "\\|"));
            md.append(details ? " | " + target.details().getOrDefault(name, "").replace("|", "\\|") + " |\n" : " |\n");
        }
        return md.toString().strip();
    }

    /** Validates the specification by its extension and content; returns the extension ("md" or "pdf"). */
    private static String checkSpec(Path spec) throws IOException {
        String name = spec.getFileName().toString().toLowerCase(Locale.ROOT);
        byte[] head;
        try (InputStream in = Files.newInputStream(spec)) {
            head = in.readNBytes(8192);
        }
        if (name.endsWith(".pdf")) {
            if (!new String(head, 0, Math.min(5, head.length), StandardCharsets.ISO_8859_1).equals("%PDF-")) {
                throw new IllegalArgumentException("Spezifikation ist keine gültige PDF-Datei: " + spec);
            }
            return "pdf";
        }
        if (name.endsWith(".md")) {
            for (byte b : head) {
                if (b == 0) {
                    throw new IllegalArgumentException("Spezifikation ist keine lesbare Markdown-Datei: " + spec);
                }
            }
            return "md";
        }
        throw new IllegalArgumentException("Spezifikation muss eine Markdown- (.md) oder PDF-Datei sein: " + spec);
    }
}
