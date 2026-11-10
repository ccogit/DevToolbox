package de.devtoolbox.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The common job shape: a tool produces the content, the job adds a skill file (AI instructions) next to it.
 * Subclasses only describe themselves, add their own params and render the skill.
 */
public abstract class SkillJob implements Job {

    /** Copilot accepts 3 files per prompt. */
    private static final int MAX_UPLOADS = 3;

    private final Tool tool;

    protected SkillJob(Tool tool) {
        this.tool = tool;
    }

    /** Params of the job itself (beyond the tool's); keys already used by the tool are ignored. */
    protected abstract List<Param> ownParams();

    protected abstract String skillFileName(Config config);

    /** Renders the skill Markdown; it can read the tool's params too (e.g. search terms). */
    protected abstract String renderSkill(Config config) throws Exception;

    @Override
    public final List<Param> params() {
        Set<String> toolKeys = tool.params().stream().map(Param::key).collect(Collectors.toSet());
        return Stream.concat(tool.params().stream(), ownParams().stream().filter(p -> !toolKeys.contains(p.key())))
                .toList();
    }

    @Override
    public final List<Path> run(Config config, Consumer<String> log) throws Exception {
        List<Path> files = new ArrayList<>(tool.run(config, log));
        if (files.isEmpty()) {
            log.accept("Kein Inhalt – Skill-Datei nicht erzeugt.");
            return files;
        }
        Path skillFile = files.getFirst().resolveSibling(skillFileName(config));
        Files.writeString(skillFile, renderSkill(config), StandardCharsets.UTF_8);
        files.addFirst(skillFile);
        log.accept("Skill geschrieben: " + skillFile);
        log.accept("Hochladen (" + files.size() + " Dateien): "
                + files.stream().map(p -> p.getFileName().toString()).collect(Collectors.joining(", ")));
        if (files.size() > MAX_UPLOADS) {
            log.accept("WARNUNG: Mehr als " + MAX_UPLOADS + " Dateien – 'Max. Tokens/Datei' erhöhen.");
        }
        return files;
    }
}
