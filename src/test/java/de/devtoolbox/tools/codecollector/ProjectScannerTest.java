package de.devtoolbox.tools.codecollector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProjectScannerTest {

    @Test
    void ambiguousJavaTypeIsNotResolvedArbitrarily(@TempDir Path tmp) throws Exception {
        for (String module : List.of("first", "second")) {
            Path dir = Files.createDirectories(tmp.resolve(module + "/src/main/java/de/x"));
            Files.writeString(dir.resolve("Risiko.java"), """
                    package de.x;
                    public class Risiko { }
                    """);
        }
        var settings = new ProjectScanner.Settings(List.of("Risiko"), Set.of("java"),
                Set.copyOf(SourceTree.DEFAULT_EXCLUDES), false, true, null);
        for (int round = 0; round < 5; round++) {
            var result = ProjectScanner.scan(tmp, settings);
            assertEquals(2, result.scannedFiles());
            assertEquals(2, result.hits().size());
            assertEquals(List.of("de.x.Risiko"), result.ambiguousTypes());
            assertFalse(result.typesByFqn().containsKey("de.x.Risiko"));
        }
    }

    @Test
    void buildAndVersionControlDirectoriesAreSkipped(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("Risiko.java"), "class Risiko { }");
        for (String excluded : List.of("target", ".git", "node_modules")) {
            Path dir = Files.createDirectories(tmp.resolve(excluded));
            Files.writeString(dir.resolve("Risiko.java"), "class Risiko { }");
        }
        var settings = new ProjectScanner.Settings(List.of("Risiko"), Set.of("java"),
                Set.copyOf(SourceTree.DEFAULT_EXCLUDES), false, true, null);
        assertEquals(1, ProjectScanner.scan(tmp, settings).scannedFiles());
    }
}
