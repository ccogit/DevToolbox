package de.devtoolbox.core;

import java.nio.file.Path;
import java.util.List;
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
}
