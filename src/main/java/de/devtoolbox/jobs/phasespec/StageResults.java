package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.Texts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The variant "in Etappen": prepares the last stage. After several answers Copilot no longer has the uploaded
 * files, so the last stage runs in a new chat. Its task is a copy of the task with the Logikprotokoll of the
 * earlier stages appended – skill, this task and the code file are again three uploads.
 */
public final class StageResults {

    private static final Pattern STAGE_FILE = Pattern.compile(".*-logik-\\d+\\.md");
    /** A row of a rule table: "| L4.2 | … | P2 |". */
    private static final Pattern RULE_ROW = Pattern.compile("^\\|\\s*(L\\d+\\.\\d+)\\s*\\|.*\\|\\s*$");
    /** A target row named in "Betrifft": R3, P12, W4. */
    private static final Pattern TARGET_ROW = Pattern.compile("\\b[RPW]\\d+\\b");
    private static final Pattern PROTOCOL_HEADING = Pattern.compile("(?m)^#{1,2}\\s*Logikprotokoll.*$");
    private static final Pattern OPEN_POINTS = Pattern.compile("(?m)^#{1,3}\\s*Offene Punkte.*$");
    private static final Pattern PHASE_HEADING = Pattern.compile("(?m)^#{1,2}\\s*Phase\\b.*$");
    private static final Pattern OPL_HEADING = Pattern.compile("(?m)^#{1,2}\\s*(?:Offene-Punkte-Liste|OPL)\\b.*$");

    private StageResults() {
    }

    /** Whether {@code name} is the answer of a Logikprotokoll stage ("spezifikation-x-e-logik-2.md"). */
    public static boolean isStageFile(String name) {
        return STAGE_FILE.matcher(name).matches();
    }

    /** "aufgabe-x.md" → "aufgabe-x-abschluss.md". */
    public static String closingTaskName(String taskName) {
        return taskName.replaceFirst("\\.md$", "") + "-abschluss.md";
    }

    /**
     * Copies the answers of the stages into the folder of the run (under {@code names}, without a counter of the
     * browser) and writes the task of the last stage next to {@code task}.
     *
     * @param task       the task of the run
     * @param stageFiles the downloaded answers of the Logikprotokoll stages, in their order
     * @param names      the names the answers were asked for, same order
     * @return the task of the last stage
     */
    public static Path prepareClosing(Path task, List<Path> stageFiles, List<String> names) throws IOException {
        Path folder = task.toAbsolutePath().getParent();
        StringBuilder md = new StringBuilder(Files.readString(task, StandardCharsets.UTF_8).stripTrailing());
        md.append("\n\n## Logikprotokoll der Etappen 1 bis ").append(stageFiles.size()).append("\n\n");
        md.append("Das Ergebnis der früheren Etappen, unverändert, je Etappe zwischen `===== BEGIN FILE` und "
                + "`===== END FILE`. Es ist die Grundlage der letzten Etappe: Die Tabellen der Phasen belegen ihre "
                + "Zeilen mit den Regelnummern daraus (`L3.1`, `L7.2` …), jede Regel mit „Betrifft: Satz“ wird "
                + "eine Satzregel, und die offenen Punkte am Ende jeder Etappe gehen in die OPL. Wiederhole das "
                + "Logikprotokoll im Ergebnis nicht.\n\n");
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < stageFiles.size(); i++) {
            String content = read(stageFiles.get(i));
            contents.add(content);
            md.append(Texts.fileBlock(names.get(i), "markdown", content)).append('\n');
            Files.copy(stageFiles.get(i), folder.resolve(names.get(i)), StandardCopyOption.REPLACE_EXISTING);
        }
        md.append(ruleIndex(contents));
        Path closing = folder.resolve(closingTaskName(task.getFileName().toString()));
        Files.writeString(closing, md.toString(), StandardCharsets.UTF_8);
        return closing;
    }

    /**
     * The rules of the Logikprotokoll by what they concern (column "Betrifft"): per target row its rule numbers,
     * and the rules about the whole record. Counted here, so the last stage does not have to search for them.
     */
    static String ruleIndex(List<String> stageContents) {
        Map<String, List<String>> byRow = new TreeMap<>(StageResults::compareNumbers);
        List<String> record = new ArrayList<>();
        for (String content : stageContents) {
            for (String line : content.split("\n")) {
                Matcher m = RULE_ROW.matcher(line);
                if (!m.matches()) {
                    continue;
                }
                String[] cells = line.split("(?<!\\\\)\\|", -1);
                String concerns = cells[cells.length - 2];
                Matcher row = TARGET_ROW.matcher(concerns);
                while (row.find()) {
                    byRow.computeIfAbsent(row.group(), k -> new ArrayList<>()).add(m.group(1));
                }
                if (concerns.contains("Satz")) {
                    record.add(m.group(1));
                }
            }
        }
        if (byRow.isEmpty() && record.isEmpty()) {
            return "";
        }
        StringBuilder md = new StringBuilder("\n### Regeln je Soll-Zeile (aus dem Logikprotokoll ausgezählt)\n\n");
        md.append("Jede dieser Regeln steht in der Spalte „Beleg“ der genannten Zeile. Soll-Zeilen, die hier "
                + "fehlen, bestimmt keine Regel des Logikprotokolls: Belege sie aus dem Code-Kontext mit "
                + "`Klasse.Methode` oder trage `nicht befüllt` bzw. `nicht belegt` und einen OPL-Punkt ein.\n\n");
        md.append("| Soll-Nr | Regeln |\n|---|---|\n");
        byRow.forEach((row, rules) -> md.append("| ").append(row).append(" | ").append(String.join(", ", rules))
                .append(" |\n"));
        if (!record.isEmpty()) {
            md.append("\nRegeln über den ganzen Satz (jede in genau einer Satzregel belegen): ")
                    .append(String.join(", ", record)).append('\n');
        }
        return md.toString();
    }

    /**
     * The complete specification of a run in stages: the answer of the last stage with the Logikprotokoll of the
     * earlier stages put in as its own section, before the tables of the phases.
     *
     * @param closingAnswer the downloaded answer of the last stage (summary, tables, OPL)
     * @param stageFiles    the downloaded answers of the Logikprotokoll stages, in their order
     * @param folder        the folder of the run; the result is written there
     * @param resultName    the name the last answer was asked for ("spezifikation-x-e.md")
     * @return the merged file ("spezifikation-x-e-gesamt.md")
     */
    public static Path merge(Path closingAnswer, List<Path> stageFiles, Path folder, String resultName)
            throws IOException {
        StringBuilder protocol = new StringBuilder("## Logikprotokoll\n");
        StringBuilder open = new StringBuilder();
        for (int i = 0; i < stageFiles.size(); i++) {
            String content = read(stageFiles.get(i));
            Matcher openHeading = OPEN_POINTS.matcher(content);
            String rules = openHeading.find() ? content.substring(0, openHeading.start()) : content;
            protocol.append('\n').append(PROTOCOL_HEADING.matcher(rules).replaceFirst("").strip()).append('\n');
            if (openHeading.find(0)) {
                String points = content.substring(openHeading.end()).strip();
                if (!points.isEmpty()) {
                    open.append("\n**Etappe ").append(i + 1).append("**\n\n").append(points).append('\n');
                }
            }
        }
        if (!open.isEmpty()) {
            protocol.append("\n### Offene Punkte der Etappen (wie gemeldet)\n").append(open);
        }
        String closing = read(closingAnswer);
        // before the tables of the phases; without such a heading before the OPL, else at the end
        Matcher at = PHASE_HEADING.matcher(closing);
        if (!at.find()) {
            at = OPL_HEADING.matcher(closing);
        }
        String merged = at.find(0)
                ? closing.substring(0, at.start()).stripTrailing() + "\n\n" + protocol.toString().strip() + "\n\n"
                + closing.substring(at.start())
                : closing + "\n\n" + protocol.toString().strip();
        Path file = folder.resolve(resultName.replaceFirst("\\.md$", "") + "-gesamt.md");
        Files.writeString(file, merged.strip() + "\n", StandardCharsets.UTF_8);
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }

    /** "P2" before "P10", "P…" before "R…" only by letter. */
    private static int compareNumbers(String a, String b) {
        int byLetter = Character.compare(a.charAt(0), b.charAt(0));
        return byLetter != 0 ? byLetter
                : Integer.compare(Integer.parseInt(a.substring(1)), Integer.parseInt(b.substring(1)));
    }
}
