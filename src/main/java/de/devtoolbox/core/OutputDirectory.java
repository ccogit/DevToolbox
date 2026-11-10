package de.devtoolbox.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Where jobs and tools write their files when no output folder is given. */
public final class OutputDirectory {

    private OutputDirectory() {
    }

    /** The configured {@link Keys#OUTPUT} folder, otherwise {@link #fallback()}. */
    public static Path of(Config config) {
        Path configured = config.path(Keys.OUTPUT);
        return configured != null ? configured : fallback();
    }

    /** The user's "Downloads" folder, or the home folder if there is none. */
    public static Path fallback() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }
}
