package de.devtoolbox.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
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
        Path chosen = configured != null ? configured : fallback();
        Path realOutput = resolveExistingAncestor(chosen);
        for (String key : java.util.List.of(Keys.BASE, Keys.TARGET)) {
            Path source = config.path(key);
            if (source != null && realOutput.startsWith(resolveExistingAncestor(source))) {
                throw new IllegalArgumentException("Der Ausgabeordner darf nicht im Migrations- oder Referenzprojekt liegen: "
                        + chosen);
            }
        }
        return chosen;
    }

    /** Resolves existing symlink ancestors to prevent output paths disguised as source subfolders. */
    private static Path resolveExistingAncestor(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path existing = absolute;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return absolute;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
        } catch (IOException e) {
            return absolute;
        }
    }

    /** The user's "Downloads" folder, or the home folder if there is none. */
    public static Path fallback() {
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return downloads;
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

    /**
     * Creates a new, independent run directory. Existing results are never overwritten or removed.
     * A numeric suffix is chosen atomically, so concurrent runs cannot claim the same directory.
     */
    public static Path createRunFolder(Config config, String fileTag, String entity) throws IOException {
        Path base = runFolder(config, fileTag, entity);
        Files.createDirectories(base.getParent());
        for (int suffix = 1; suffix < 100_000; suffix++) {
            Path candidate = suffix == 1 ? base : base.resolveSibling(base.getFileName() + "-" + suffix);
            try {
                return Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException e) {
                // Keep earlier runs intact; another run may have claimed this name concurrently.
            }
        }
        throw new IOException("Kein freier Ausgabeordner für " + base);
    }

    /** Creates a directory only if it does not already exist, without clearing existing files. */
    public static void prepare(Path runFolder) throws IOException {
        Files.createDirectories(runFolder.getParent());
        Files.createDirectory(runFolder);
    }

    /** Lower case, letters and digits only, other runs replaced by one hyphen. */
    public static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9äöüß]+", "-").replaceAll("^-|-$", "");
    }
}
