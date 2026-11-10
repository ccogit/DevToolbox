package de.devtoolbox.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The common job shape: a tool produces the content, the job writes an <em>Aufgabe</em> (the concrete task for
 * the AI) and the <em>Skill</em> needed to work on it (generic method, the same for every task of this kind).
 * Subclasses describe themselves, add their own params and render task and skill.
 */
public abstract class TaskJob implements Job {

    /** Copilot accepts 3 files per prompt: skill + task + content. */
    private static final int MAX_UPLOADS = 3;

    private final Tool tool;

    protected TaskJob(Tool tool) {
        this.tool = tool;
    }

    /** Params of the job itself (beyond the tool's); keys already used by the tool are ignored. */
    protected abstract List<Param> ownParams();

    protected abstract String taskFileName(Config config);

    /** Renders the task; {@code contentFiles} are the tool's output files it refers to. */
    protected abstract String renderTask(Config config, List<Path> contentFiles) throws Exception;

    /** The skill does not depend on the configuration, so it can be reused for every task. */
    protected abstract String skillFileName();

    protected abstract String renderSkill() throws Exception;

    @Override
    public final List<Param> params() {
        Set<String> toolKeys = tool.params().stream().map(Param::key).collect(Collectors.toSet());
        return Stream.concat(tool.params().stream(), ownParams().stream().filter(p -> !toolKeys.contains(p.key())))
                .toList();
    }

    /** The business entity of a run; it names the run folder. */
    protected String entityName(Config config) {
        List<String> terms = config.list(Keys.TERMS);
        return terms.isEmpty() ? "" : terms.getFirst();
    }

    @Override
    public final List<Path> run(Config config, Consumer<String> log) throws Exception {
        Path dir = OutputDirectory.runFolder(config, fileTag(), entityName(config));
        Map<String, String> values = new HashMap<>(config.values());
        values.put(Keys.OUTPUT, dir.toString());
        Config runConfig = new Config(values);
        // the tool writes into the run folder; a run without hits leaves it empty
        if (!config.list(Keys.TERMS).isEmpty()) {
            OutputDirectory.prepare(dir);
        }
        List<Path> content = tool.run(runConfig, log);
        if (content.isEmpty()) {
            log.accept("Kein Inhalt – keine Aufgabe erzeugt.");
            return content;
        }
        Path skill = dir.resolve(skillFileName());
        Path task = dir.resolve(taskFileName(config));
        Files.writeString(skill, renderSkill(), StandardCharsets.UTF_8);
        Files.writeString(task, renderTask(config, content), StandardCharsets.UTF_8);

        List<Path> files = new ArrayList<>(List.of(skill, task));
        files.addAll(content);
        log.accept("Ordner: " + dir);
        log.accept("Aufgabe geschrieben: " + task);
        log.accept("Benötigter Skill: " + skill.getFileName());
        log.accept("Hochladen (" + files.size() + " Dateien): "
                + files.stream().map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));
        log.accept("Prompt: Bearbeite die Aufgabe in " + task.getFileName() + " mit dem Skill " + skill.getFileName() + ".");
        if (files.size() > MAX_UPLOADS) {
            log.accept("WARNUNG: Copilot nimmt nur " + MAX_UPLOADS + " Dateien (Skill + Aufgabe + 1 Code-Datei).");
        }
        CopilotLimit.check(config, files, false,
                "Code verkleinern: weniger Phasen, Umfang „Nur Klassen mit dem Begriff“, „Kompakt“.", log);
        return files;
    }
}
