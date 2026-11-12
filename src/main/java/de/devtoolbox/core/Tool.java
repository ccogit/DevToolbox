package de.devtoolbox.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A reusable building block (search, collect, convert …) that jobs use. Tools are not selectable themselves;
 * a job offers the tool's params in its own form and calls {@link #run}.
 */
public interface Tool {

    String id();

    String name();

    List<Param> params();

    /**
     * @return the produced files (may be empty)
     */
    List<Path> run(Config config, Consumer<String> log) throws Exception;

    /**
     * Like {@link #run}, plus what a {@link TaskJob} puts into its task besides the files. The default has only the
     * files; tools with more to say override it.
     */
    default Output produce(Config config, Consumer<String> log) throws Exception {
        return Output.of(run(config, log));
    }

    /**
     * Result of {@link #produce}.
     *
     * @param files              the produced files (may be empty)
     * @param inventory          Markdown of the target lists (Soll-Listen) for the task, empty if there are none
     * @param targetRows         number of target rows per phase display name ("Read", "Process"), selected phases only
     * @param coreTables         number of tables (or data structures) the Read target list is built from
     * @param largestSources     the largest source files in the content file, for the size report ("`A.java` 1.234 Zeichen")
     * @param excludeCandidates  file names of the largest files that may be excluded (not protected)
     */
    record Output(List<Path> files, String inventory, Map<String, Integer> targetRows, int coreTables,
                  List<String> largestSources, List<String> excludeCandidates) {

        public static Output of(List<Path> files) {
            return new Output(files, "", Map.of(), 0, List.of(), List.of());
        }
    }
}
