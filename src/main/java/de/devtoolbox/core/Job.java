package de.devtoolbox.core;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the user selects and runs. A job produces content (and usually a skill for the AI) with the help of
 * {@link Tool}s. To add one: implement this interface (or extend {@link SkillJob}) and register it in
 * {@link JobRegistry}.
 */
public interface Job {

    /** Stable id, used for the CLI, JSON files and remembering the last inputs. */
    String id();

    String name();

    String description();

    /** All inputs of the job, including those of the tools it uses – shown in one form. */
    List<Param> params();

    /**
     * Runs the job. Progress goes to {@code log}; called off the UI thread.
     *
     * @return the produced files (may be empty)
     */
    List<Path> run(Config config, Consumer<String> log) throws Exception;
}
