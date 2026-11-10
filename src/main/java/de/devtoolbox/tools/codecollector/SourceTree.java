package de.devtoolbox.tools.codecollector;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

/** Walks a source tree, skipping build/tool folders, {@code src/test} (optionally) and one further folder. */
public final class SourceTree {

    /** Folders that never contain sources worth searching (build output, VCS and IDE metadata). */
    public static final List<String> DEFAULT_EXCLUDES =
            List.of("target", "build", "out", "bin", ".git", ".idea", ".gradle", ".mvn", "node_modules");

    private SourceTree() {
    }

    /** Files below {@code root} accepted by {@code accept}, with the default exclusions and without tests. */
    public static List<Path> files(Path root, BiPredicate<Path, BasicFileAttributes> accept) throws IOException {
        return files(root, Set.copyOf(DEFAULT_EXCLUDES), false, null, accept);
    }

    /**
     * Files below {@code root} accepted by {@code accept}, in walk order. Unreadable files are skipped.
     *
     * @param excludedDirs folder names (lower case) whose subtree is skipped
     * @param includeTests whether {@code src/test} is walked
     * @param skipDir      absolute, normalized folder that is skipped as well (e.g. the output folder); may be null
     */
    public static List<Path> files(Path root, Set<String> excludedDirs, boolean includeTests, Path skipDir,
                                   BiPredicate<Path, BasicFileAttributes> accept) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString().toLowerCase(Locale.ROOT);
                Path parent = dir.getParent().getFileName();
                boolean testDir = !includeTests && name.equals("test") && parent != null
                        && parent.toString().equals("src");
                boolean skipped = dir.toAbsolutePath().normalize().equals(skipDir);
                return excludedDirs.contains(name) || testDir || skipped
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (accept.test(file, attrs)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
}
