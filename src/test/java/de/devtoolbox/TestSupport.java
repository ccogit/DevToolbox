package de.devtoolbox;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared helpers. Test data is read from the repository (never written); outputs go to a temp dir. */
public final class TestSupport {

    /** Surefire runs with the project root as working directory. */
    public static final Path ROOT = Path.of("").toAbsolutePath();
    public static final Path TESTPROJEKT = ROOT.resolve("testprojekt");
    public static final Path ZIEL = ROOT.resolve("testprojekt-ziel");
    public static final Path ZIELMODELL = ZIEL.resolve("zielmodell");
    public static final Path SPEC_PDF = ZIEL.resolve("spezifikation/risiko-spezifikation.pdf");
    public static final Path PROJEKTE = ROOT.resolve("migrationsprojekte");

    private TestSupport() {
    }

    public record Result(List<String> log, List<Path> files, Path out) {
        public String logText() {
            return String.join("\n", log);
        }

        public String read(String name) throws IOException {
            return Files.readString(out.resolve(name), StandardCharsets.UTF_8);
        }

        public boolean has(String name) {
            return Files.exists(out.resolve(name));
        }

        public List<String> names() {
            return files.stream().map(p -> p.getFileName().toString()).toList();
        }
    }

    /** Standard specification values (case 1 without DDL). */
    public static Map<String, String> specValues(Path out) {
        Map<String, String> v = new HashMap<>();
        v.put("base", TESTPROJEKT.toString());
        v.put("terms", "Risiko, Risk");
        v.put("targetModel", ZIELMODELL.toString());
        v.put("variant", "tables"); // the tests of the variants with a Logikprotokoll set their own
        v.put("output", out.toString());
        return v;
    }

    public static Result run(String jobId, Map<String, String> values, Path out) throws Exception {
        Job job = JobRegistry.find(jobId).orElseThrow();
        List<String> log = new ArrayList<>();
        values.put("output", out.toString());
        List<Path> files = job.run(Config.of(job.params(), values), log::add);
        return new Result(log, files, files.isEmpty() ? out : files.getFirst().getParent());
    }

    public static Result spec(Path out, Map<String, String> overrides) throws Exception {
        Map<String, String> v = specValues(out);
        v.putAll(overrides);
        return run("phase-spec", v, out);
    }

    public static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals(de.devtoolbox.core.ProjectLayout.SETTINGS_FILE)) {
                    return FileVisitResult.CONTINUE; // the machine's local settings do not belong in test copies
                }
                Files.copy(file, to.resolve(from.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }
}
