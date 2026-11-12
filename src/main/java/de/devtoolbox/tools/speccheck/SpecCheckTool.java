package de.devtoolbox.tools.speccheck;

import de.devtoolbox.tools.speccheck.SpecChecker.Result;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Reads the Markdown files of a run folder (task, code context, results), merges the results and checks them.
 * Stateless: everything lives in the arguments, so runs can happen concurrently.
 */
public final class SpecCheckTool {

    private static final String TASK_PREFIX = "aufgabe-";
    private static final String CODE_PREFIX = "code-";
    private static final String SPEC_PREFIX = "spezifikation-";
    private static final String MERGED_SUFFIX = "-gesamt.md";

    /** Entity slug from the task file name (the shortest one if there are several). */
    static String entity(List<Path> tasks) {
        String name = tasks.stream().map(p -> p.getFileName().toString()).min(
                Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder())).orElseThrow();
        return name.substring(TASK_PREFIX.length(), name.length() - ".md".length());
    }

    /**
     * Checks the run folder and writes {@code pruefbericht-<e>.md} (and {@code spezifikation-<e>-gesamt.md} if there
     * is more than one result file) into it.
     *
     * @return the written files
     * @throws IllegalArgumentException if the folder, the task, the code context or a result is missing
     */
    public List<Path> run(Path dir, Consumer<String> log) throws IOException {
        return run(dir, List.of(), log);
    }

    /**
     * Like {@link #run(Path, Consumer)}, but also reads the task, code context and results of {@code more} (the run
     * folders of other phases of the same entity) and merges them into one specification. Files with the same name in
     * different folders are expected. The outputs go to {@code dir}.
     *
     * @throws IllegalArgumentException if a folder does not exist or lacks task, code context or result
     */
    public List<Path> run(Path dir, List<Path> more, Consumer<String> log) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Ordner des Laufs nicht gefunden: " + dir);
        }
        List<Path> folders = new ArrayList<>(List.of(dir));
        for (Path extra : more) {
            if (!Files.isDirectory(extra)) {
                throw new IllegalArgumentException("Weiterer Ordner nicht gefunden: " + extra);
            }
            if (!folders.contains(extra)) {
                folders.add(extra);
            }
        }
        List<Path> tasks = new ArrayList<>();
        List<Path> codes = new ArrayList<>();
        List<List<Path>> resultsByFolder = new ArrayList<>();
        for (Path folder : folders) {
            List<Path> folderTasks = list(folder, TASK_PREFIX);
            List<Path> folderCodes = list(folder, CODE_PREFIX);
            List<Path> folderResults = list(folder, SPEC_PREFIX).stream()
                    .filter(p -> !p.getFileName().toString().endsWith(MERGED_SUFFIX)).toList();
            if (folderTasks.isEmpty()) {
                throw new IllegalArgumentException("Im Ordner fehlt die Aufgabe (aufgabe-*.md): " + folder);
            }
            if (folderCodes.isEmpty()) {
                throw new IllegalArgumentException("Im Ordner fehlt der Code-Kontext (code-*.md): " + folder);
            }
            if (folderResults.isEmpty()) {
                throw new IllegalArgumentException("Im Ordner liegt kein Ergebnis von Copilot (spezifikation-*.md): "
                        + folder + " – die Ergebnisdatei zuerst in den Ordner des Laufs legen.");
            }
            tasks.addAll(folderTasks);
            codes.addAll(folderCodes);
            resultsByFolder.add(folderResults);
        }
        String entity = entity(tasks);
        // per folder the main file first, then the others; the folders in the given order
        List<Path> results = new ArrayList<>();
        for (List<Path> folderResults : resultsByFolder) {
            results.addAll(order(folderResults, SPEC_PREFIX + entity + ".md"));
        }

        TaskLists soll = TaskLists.parse(read(tasks));
        CodeContext code = CodeContext.parse(read(codes));
        List<Spec> specs = read(results).stream().map(Spec::parse).toList();
        boolean merged = specs.size() > 1;
        Spec state = merged ? SpecMerger.merge(specs, soll) : specs.getFirst();
        Result result = SpecChecker.check(state, soll, code);

        boolean several = folders.size() > 1;
        List<String> names = results.stream().map(p -> several
                ? p.getParent().getFileName() + "/" + p.getFileName() : p.getFileName().toString()).toList();
        log.accept("Prüfung der Spezifikation „" + entity + "“: " + names.size() + " Ergebnisdatei(en) ("
                + String.join(", ", names) + "), " + code.size() + " Dateien im Code-Kontext"
                + (several ? " aus " + folders.size() + " Ordnern" : ""));
        List<Path> written = new ArrayList<>();
        if (merged) {
            Path all = dir.resolve(SPEC_PREFIX + entity + MERGED_SUFFIX);
            Files.writeString(all, SpecReport.merged(entity, state, result), StandardCharsets.UTF_8);
            written.add(all);
            log.accept("Zusammengeführt: " + all);
        }
        Path report = dir.resolve("pruefbericht-" + entity + ".md");
        Files.writeString(report, SpecReport.report(entity, names, result, merged), StandardCharsets.UTF_8);
        written.addFirst(report);

        SpecReport.statLines(result).forEach(log);
        log.accept("OPL-Punkte: " + result.openPointCount() + " · Befunde: " + SpecReport.countLine(result));
        log.accept("Prüfbericht: " + report);
        log.accept("Geprüft sind Form und Auffindbarkeit der Belege, nicht die fachliche Richtigkeit.");
        Optional<SpecReport.Prompt> prompt = SpecReport.correctionPrompt(entity, result);
        if (prompt.isPresent()) {
            log.accept("Nachbesserungsprompt (selber Chat, keine Datei): " + prompt.get().text());
            if (prompt.get().truncated()) {
                log.accept("Der Prompt nennt nur die ersten Befunde (Grenze " + SpecReport.MAX_PROMPT_LENGTH
                        + " Zeichen); nach der Korrektur ist ein weiterer Durchgang nötig.");
            }
        } else if (result.findings().stream().noneMatch(f -> !f.notice())) {
            log.accept("Keine Befunde.");
        } else {
            log.accept("Kein Nachbesserungsprompt nötig (keine Befunde in D1, D2, D4); siehe Prüfbericht.");
        }
        return written;
    }

    /** Files {@code <prefix>*.md} of the folder, sorted by name. */
    private static List<Path> list(Path dir, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.startsWith(prefix) && n.endsWith(".md");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    /** The file without suffix first, then the others by modification time (oldest first), then by name. */
    private static List<Path> order(List<Path> results, String mainName) throws IOException {
        List<Path> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.<Path, Boolean>comparing(p -> !p.getFileName().toString().equals(mainName))
                .thenComparing(p -> modified(p)).thenComparing(p -> p.getFileName().toString()));
        return sorted;
    }

    private static long modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static List<String> read(List<Path> files) throws IOException {
        List<String> texts = new ArrayList<>();
        for (Path f : files) {
            byte[] bytes = Files.readAllBytes(f);
            try {
                texts.add(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString());
            } catch (CharacterCodingException e) {
                texts.add(new String(bytes, java.nio.charset.Charset.forName("windows-1252")));
            }
        }
        return texts;
    }
}
