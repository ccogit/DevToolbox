package de.devtoolbox.core;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the user selects and runs. A job produces a task for the AI (plus the skill needed to work on it) with the
 * help of {@link Tool}s. To add one: implement this interface (or extend {@link TaskJob}) and register it in
 * {@link JobRegistry}.
 */
public interface Job {

    /**
     * A job that asks the AI for files logs one line with this prefix, followed by their names (comma-separated).
     * The UI then links each of them as soon as it appears in the download folder.
     */
    String ANSWERS_PREFIX = "Antwortdateien von Copilot: ";

    /** Stable id, used for the CLI, JSON files and remembering the last inputs. */
    String id();

    String name();

    /** Uniform description shown on the run page and in the help. */
    JobInfo info();

    /** Short word for file names of saved configurations, e.g. "spezifikation". */
    default String fileTag() {
        return id();
    }

    /** All inputs of the job, including those of the tools it uses – shown in one form. */
    List<Param> params();

    /**
     * Runs the job. Progress goes to {@code log}; called off the UI thread.
     *
     * @return the produced files (may be empty)
     */
    List<Path> run(Config config, Consumer<String> log) throws Exception;
}
