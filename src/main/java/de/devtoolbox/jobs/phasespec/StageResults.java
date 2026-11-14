package de.devtoolbox.jobs.phasespec;

import de.devtoolbox.core.Texts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The variant "in Etappen": prepares the last stage. After several answers Copilot no longer has the uploaded
 * files, so the last stage runs in a new chat. Its task is a copy of the task with the Logikprotokoll of the
 * earlier stages appended – skill, this task and the code file are again three uploads.
 */
public final class StageResults {

    private static final Pattern STAGE_FILE = Pattern.compile(".*-logik-\\d+\\.md");

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
        for (int i = 0; i < stageFiles.size(); i++) {
            String content = Files.readString(stageFiles.get(i), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
            md.append(Texts.fileBlock(names.get(i), "markdown", content)).append('\n');
            Files.copy(stageFiles.get(i), folder.resolve(names.get(i)), StandardCopyOption.REPLACE_EXISTING);
        }
        Path closing = folder.resolve(closingTaskName(task.getFileName().toString()));
        Files.writeString(closing, md.toString(), StandardCharsets.UTF_8);
        return closing;
    }
}
