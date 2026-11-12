package de.devtoolbox.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class OutputDirectoryTest {

    @Test
    void repeatedAndParallelRunsNeverOverwriteOneAnother(@TempDir Path tmp) throws Exception {
        Config config = new Config(Map.of(Keys.OUTPUT, tmp.toString(), Keys.PROJECT, "test"));
        Path first = OutputDirectory.createRunFolder(config, "spezifikation", "Risiko");
        Files.writeString(first.resolve("important.md"), "do not delete");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var jobs = new java.util.ArrayList<Future<Path>>();
            for (int i = 0; i < 30; i++) {
                jobs.add(pool.submit(() -> OutputDirectory.createRunFolder(config, "spezifikation", "Risiko")));
            }
            var paths = new HashSet<Path>();
            for (Future<Path> job : jobs) {
                Path newDir = job.get();
                assertTrue(Files.isDirectory(newDir));
                assertNotEquals(first, newDir);
                paths.add(newDir);
            }
            assertEquals(30, paths.size());
        }
        assertEquals("do not delete", Files.readString(first.resolve("important.md")));
    }

    @Test
    void refusesToWriteIntoSourceProject(@TempDir Path tmp) throws Exception {
        Path migration = Files.createDirectories(tmp.resolve("migration"));
        Path inside = Files.createDirectories(migration.resolve("output"));
        Config config = new Config(Map.of(Keys.TARGET, migration.toString(), Keys.OUTPUT, inside.toString()));
        var error = assertThrows(IllegalArgumentException.class, () -> OutputDirectory.of(config));
        assertTrue(error.getMessage().contains("Ausgabeordner"), error.getMessage());
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        assertEquals(outside, OutputDirectory.of(new Config(Map.of(
                Keys.TARGET, migration.toString(), Keys.OUTPUT, outside.toString()))));
    }

    @Test
    void outputFolderCannotEscapeThroughProjectName(@TempDir Path tmp) throws Exception {
        Config config = new Config(Map.of(Keys.OUTPUT, tmp.toString(), Keys.PROJECT, "../../test"));
        Path dir = OutputDirectory.createRunFolder(config, "spezifikation", "../Risiko");
        assertTrue(dir.startsWith(tmp.resolve("devtoolbox")));
        assertTrue(Files.isDirectory(dir));
    }
}
