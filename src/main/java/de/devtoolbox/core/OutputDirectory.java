package de.devtoolbox.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Where jobs and tools write their files when no output folder is given. */
public final class OutputDirectory {

    /** Name of the folder below the output folder that holds all run folders. */
    public static final String ROOT_FOLDER = "devtoolbox";
    private static final String NO_PROJECT = "ohne-projekt";

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

    /**
     * The folder of one run: {@code <output>/devtoolbox/<project id or ohne-projekt>-<fileTag>-<entity slug>}.
     * Not created here.
     */
    public static Path runFolder(Config config, String fileTag, String entity) {
        String project = slug(config.text(Keys.PROJECT));
        String name = (project.isEmpty() ? NO_PROJECT : project) + "-" + fileTag + "-" + slug(entity);
        return of(config).resolve(ROOT_FOLDER).resolve(name);
    }

    /** Creates the run folder and deletes the regular files in it (nothing else, no sub folders, nothing outside). */
    public static void prepare(Path runFolder) throws IOException {
        Files.createDirectories(runFolder);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(runFolder, Files::isRegularFile)) {
            for (Path file : old) {
                Files.deleteIfExists(file);
            }
        }
    }

    /** Lower case, letters and digits only, other runs replaced by one hyphen. */
    public static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9äöüß]+", "-").replaceAll("^-|-$", "");
    }
}
