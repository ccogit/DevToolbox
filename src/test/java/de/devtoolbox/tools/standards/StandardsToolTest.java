package de.devtoolbox.tools.standards;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** In the package of StandardsTool because toTemplate is package-private. */
class StandardsToolTest {

    @Test
    void toTemplate() {
        assertEquals("{{Entitaet}}TO", StandardsTool.toTemplate("VertragTO", "Vertrag"));
        assertEquals("package de.vers.neu.{{entitaet}}.to;",
                StandardsTool.toTemplate("package de.vers.neu.vertrag.to;", "Vertrag"));
        assertEquals("ALT_{{ENTITAET}}", StandardsTool.toTemplate("ALT_VERTRAG", "Vertrag"));
        assertEquals("Vertragsbeginn vertragsnummer VERTRAGSNUMMER",
                StandardsTool.toTemplate("Vertragsbeginn vertragsnummer VERTRAGSNUMMER", "Vertrag"));
        assertEquals("src/main/java/de/vers/neu/{{entitaet}}/to/{{Entitaet}}TO.java",
                StandardsTool.toTemplate("src/main/java/de/vers/neu/vertrag/to/VertragTO.java", "Vertrag"));
    }

    @Test
    void classNameAndApply() {
        assertEquals("Risiko", StandardsTool.className("risiko"));
        assertEquals("RisikoDeckung", StandardsTool.className("Risiko-Deckung"));
        assertEquals("Risiko", StandardsTool.className("  Risiko "));
        assertEquals("", StandardsTool.className(" "));
        assertEquals("RisikoTO de.x.risiko ALT_RISIKO",
                StandardsTool.apply("{{Entitaet}}TO de.x.{{entitaet}} ALT_{{ENTITAET}}", "risiko"));
    }

    @Test
    void candidatesOfTargetProject() throws Exception {
        var candidates = StandardsTool.candidates(TestSupport.ZIEL);
        assertFalse(candidates.isEmpty());
        assertEquals("Vertrag", candidates.getFirst().name());
        assertEquals(8, candidates.getFirst().artefacts());
        assertTrue(candidates.stream().anyMatch(c -> c.name().equals("Objekt")));
        assertTrue(candidates.getFirst().label().startsWith("Vertrag – 8 Artefakte"));
        assertTrue(StandardsTool.candidates(TestSupport.TESTPROJEKT).stream().anyMatch(c -> c.name().equals("Risiko")));
    }

    @Test
    void generateLoadAndBackup(@TempDir Path tmp) throws Exception {
        Path dir = tmp.resolve("standards");
        List<String> log = new ArrayList<>();
        var s = StandardsTool.generate(TestSupport.ZIEL, "Vertrag", dir, "TESTZIEL", log::add);
        assertEquals("Vertrag", s.example());
        assertEquals(8, s.templates());
        assertEquals(3, s.baseTypes());
        assertNull(s.backup());

        Path to = dir.resolve("vorlagen/{{Entitaet}}TO.java");
        assertTrue(Files.isRegularFile(to));
        String code = Files.readString(to);
        assertTrue(code.contains("{{Entitaet}}TO"));
        assertTrue(code.contains("package de.vers.neu.{{entitaet}}.to"));
        assertTrue(code.contains("Vertragsbeginn") || !code.contains("{{Entitaet}}sbeginn"));
        assertFalse(code.contains("VertragTO"));
        assertTrue(Files.readString(dir.resolve("vorlagen/{{Entitaet}}PO.java")).contains("ALT_{{ENTITAET}}")
                || Files.readString(dir.resolve("vorlagen/{{Entitaet}}Entity.java")).contains("ALT_{{ENTITAET}}"));
        for (String base : List.of("AbstractTO", "AbstractPO", "Anreicherer")) {
            assertTrue(Files.isRegularFile(dir.resolve("basis/" + base + ".java")), base);
        }

        var stored = StandardsTool.load(dir);
        assertEquals("Vertrag", stored.example());
        assertEquals(8, stored.rows().size());
        assertEquals(3, stored.baseFiles().size());
        var first = stored.rows().getFirst();
        assertEquals("{{Entitaet}}TO", first.className());
        assertEquals("de.vers.neu.{{entitaet}}.to", first.pkg());
        assertEquals("src/main/java/de/vers/neu/{{entitaet}}/to/{{Entitaet}}TO.java", first.pathOrDefault());
        assertEquals("src/main/java/de/vers/neu/risiko/to/RisikoTO.java", StandardsTool.apply(first.pathOrDefault(), "Risiko"));
        assertEquals(1, stored.rows().stream().filter(r -> r.phase().equals("Write")).count());
        assertEquals(1, stored.rows().stream().filter(r -> r.phase().equals("Process")).count());

        // generating again moves the existing standards aside
        var again = StandardsTool.generate(TestSupport.ZIEL, "Vertrag", dir, "TESTZIEL", log::add);
        assertNotNull(again.backup());
        assertTrue(Files.isRegularFile(again.backup().resolve("STANDARDS.md")));
        assertTrue(Files.isRegularFile(dir.resolve("STANDARDS.md")));
    }

    @Test
    void generateErrors(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
                () -> StandardsTool.generate(TestSupport.ZIEL, "GibtEsNicht", tmp.resolve("s"), "", s -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> StandardsTool.generate(tmp.resolve("nix"), "", tmp.resolve("s"), "", s -> { }));
        assertThrows(IllegalArgumentException.class, () -> StandardsTool.load(tmp));
    }

    @Test
    void rowWithoutPathColumnDerivesPathFromPackage(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("STANDARDS.md"), """
                # Standards

                - Beispiel-Entität: **Vertrag**

                ## Artefakte

                | Nr | Phase | Artefakt | Vorlage | Klasse | Package |
                |---|---|---|---|---|---|
                | 1 | Read | TO | `vorlagen/{{Entitaet}}TO.java` | `{{Entitaet}}TO` | `de.vers.neu.{{entitaet}}.to` |
                """);
        var rows = StandardsTool.load(tmp).rows();
        assertEquals(1, rows.size());
        assertEquals("src/main/java/de/vers/neu/{{entitaet}}/to/{{Entitaet}}TO.java", rows.getFirst().pathOrDefault());
    }

    @Test
    void multiModulePathsKeepModuleFolder(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("proj");
        write(src.resolve("migration-model/src/main/java/de/x/vertrag/to/VertragTO.java"),
                "package de.x.vertrag.to;\npublic class VertragTO { String vertragsnummer; }\n");
        write(src.resolve("migration-read/src/main/java/de/x/vertrag/read/VertragItemReader.java"),
                "package de.x.vertrag.read;\npublic class VertragItemReader implements ItemReader<VertragTO> { }\n");
        write(src.resolve("migration-write/src/main/java/de/x/vertrag/write/VertragItemWriter.java"),
                "package de.x.vertrag.write;\npublic class VertragItemWriter implements ItemWriter<VertragTO> { }\n");
        Path dir = tmp.resolve("std");
        var s = StandardsTool.generate(src, "Vertrag", dir, "", x -> { });
        assertEquals(3, s.templates());
        var paths = StandardsTool.load(dir).rows().stream().map(StandardsTool.Row::pathOrDefault).toList();
        assertTrue(paths.contains("migration-model/src/main/java/de/x/{{entitaet}}/to/{{Entitaet}}TO.java"), paths.toString());
        assertTrue(paths.contains("migration-read/src/main/java/de/x/{{entitaet}}/read/{{Entitaet}}ItemReader.java"), paths.toString());
        assertTrue(paths.contains("migration-write/src/main/java/de/x/{{entitaet}}/write/{{Entitaet}}ItemWriter.java"), paths.toString());
    }

    @Test
    void repeatGenerationUsesUniqueBackupsEvenWithinSameSecond(@TempDir Path tmp) throws Exception {
        Path standards = tmp.resolve("std");
        var first = StandardsTool.generate(TestSupport.ZIEL, "Vertrag", standards, "T", x -> {});
        assertNull(first.backup());
        Files.writeString(standards.resolve("notes.txt"), "version one");
        var second = StandardsTool.generate(TestSupport.ZIEL, "Vertrag", standards, "T", x -> {});
        assertNotNull(second.backup());
        assertEquals("version one", Files.readString(second.backup().resolve("notes.txt")));
        Files.writeString(standards.resolve("notes.txt"), "version two");
        var third = StandardsTool.generate(TestSupport.ZIEL, "Vertrag", standards, "T", x -> {});
        assertNotEquals(second.backup(), third.backup());
        assertEquals("version two", Files.readString(third.backup().resolve("notes.txt")));
        assertTrue(Files.isRegularFile(standards.resolve("STANDARDS.md")));
    }

    @Test
    void generationFailureLeavesExistingStandardUntouched(@TempDir Path tmp) throws Exception {
        Path standards = Files.createDirectories(tmp.resolve("std"));
        Files.writeString(standards.resolve("STANDARDS.md"), "PRECIOUS");
        assertThrows(IllegalArgumentException.class,
                () -> StandardsTool.generate(TestSupport.ZIEL, "UnknownEntity", standards, "T", x -> {}));
        assertEquals("PRECIOUS", Files.readString(standards.resolve("STANDARDS.md")));
    }

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
