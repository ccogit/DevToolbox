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
 * the AI) and the <em>Skill</em> needed to work on it (method and table formats for this kind of task).
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

    /** Renders the task; {@code content} holds the tool's output files it refers to and its target lists. */
    protected abstract String renderTask(Config config, Tool.Output content) throws Exception;

    /** The skill's file name is the same for every task of this kind. */
    protected abstract String skillFileName();

    /** Renders the skill; it may depend on the configuration (e.g. only the sections of the selected phases). */
    protected abstract String renderSkill(Config config) throws Exception;

    /**
     * The prompts for Copilot, one log line each (with its prefix). Default: one sentence naming task and skill.
     */
    protected List<String> prompts(Config config, Path skill, Path task, List<Path> content) {
        return List.of("Prompt: Bearbeite die Aufgabe in " + task.getFileName() + " mit dem Skill "
                + skill.getFileName() + ".");
    }

    /** Further hints for the log after the prompts (e.g. recommendations); default: none. */
    protected List<String> notes(Config config, Tool.Output content) {
        return List.of();
    }

    /** What to do if the files exceed the Copilot limit; part of the WARNUNG. */
    protected String limitSuggestions(Config config, Tool.Output content) {
        return "Code verkleinern: weniger Phasen, Umfang „Nur Klassen mit dem Begriff“.";
    }

    /** Keys of tool params that this job fixes itself and therefore does not show; default: none. */
    protected Set<String> hiddenKeys() {
        return Set.of();
    }

    /** The configuration of a run after the job applied its fixed values; default: unchanged. */
    protected Config effective(Config config) {
        return config;
    }

    @Override
    public final List<Param> params() {
        Set<String> toolKeys = tool.params().stream().map(Param::key).collect(Collectors.toSet());
        return Stream.concat(tool.params().stream(), ownParams().stream().filter(p -> !toolKeys.contains(p.key())))
                .filter(p -> !hiddenKeys().contains(p.key()))
                .toList();
    }

    /** The business entity of a run; it names the run folder. */
    protected String entityName(Config config) {
        List<String> terms = config.list(Keys.TERMS);
        return terms.isEmpty() ? "" : terms.getFirst();
    }

    @Override
    public final List<Path> run(Config given, Consumer<String> log) throws Exception {
        Config config = effective(given);
        Path dir = OutputDirectory.createRunFolder(config, fileTag(), entityName(config));
        Map<String, String> values = new HashMap<>(config.values());
        values.put(Keys.OUTPUT, dir.toString());
        Config runConfig = new Config(values);
        // The tool writes into its own run directory; previous results remain untouched.
        Tool.Output content = tool.produce(runConfig, log);
        if (content.files().isEmpty()) {
            log.accept("Kein Inhalt – keine Aufgabe erzeugt.");
            return content.files();
        }
        Path skill = dir.resolve(skillFileName());
        Path task = dir.resolve(taskFileName(config));
        Files.writeString(skill, renderSkill(config), StandardCharsets.UTF_8);
        Files.writeString(task, renderTask(config, content), StandardCharsets.UTF_8);

        List<Path> files = new ArrayList<>(List.of(skill, task));
        files.addAll(content.files());
        log.accept("Ordner: " + dir);
        log.accept("Aufgabe geschrieben: " + task);
        log.accept("Benötigter Skill: " + skill.getFileName());
        log.accept("Hochladen (" + files.size() + " Dateien): "
                + files.stream().map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));
        prompts(config, skill, task, content.files()).forEach(log);
        notes(config, content).forEach(log);
        if (files.size() > MAX_UPLOADS) {
            log.accept("WARNUNG: Copilot nimmt nur " + MAX_UPLOADS + " Dateien (Skill + Aufgabe + 1 Code-Datei).");
        }
        CopilotLimit.report(files, content.files().getFirst().getFileName().toString(), content.largestSources(), log);
        CopilotLimit.check(config, files, false, limitSuggestions(config, content), log);
        return files;
    }
}
