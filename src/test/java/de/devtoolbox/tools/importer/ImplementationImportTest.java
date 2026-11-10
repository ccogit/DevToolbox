package de.devtoolbox.tools.importer;

import de.devtoolbox.tools.importer.ImplementationImport.Plan;
import de.devtoolbox.tools.importer.ImplementationImport.Status;
import de.devtoolbox.tools.standards.StandardsTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImplementationImportTest {

    private static final String TO = "src/main/java/de/vers/neu/risiko/to/RisikoTO.java";
    private static final String PO = "src/main/java/de/vers/neu/risiko/po/RisikoPO.java";

    @TempDir
    Path tmp;

    /** Writes a ZIP; a null value creates a directory entry. */
    private Path zip(String name, Map<String, String> files) throws Exception {
        Path z = tmp.resolve(name);
        try (OutputStream out = Files.newOutputStream(z); ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                if (e.getValue() != null) {
                    zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return z;
    }

    private static String java(String pkg, String cls, String body) {
        return "package " + pkg + ";\n\npublic class " + cls + " {\n" + body + "\n}\n";
    }

    private Path root() throws Exception {
        return Files.createDirectories(tmp.resolve("projekt"));
    }

    private static Entry0 find(Plan p, String path) {
        return new Entry0(p.entries().stream().filter(e -> e.path().equals(path)).findFirst().orElseThrow().status());
    }

    private record Entry0(Status status) {
    }

    private static boolean hasCheck(Plan p, String text, boolean problem) {
        return p.checks().stream().anyMatch(c -> c.problem() == problem && c.message().contains(text));
    }

    @Test
    void classifiesNewOverwriteIdenticalUnexpected() throws Exception {
        Path root = root();
        String same = java("de.x", "Same", "");
        Files.createDirectories(root.resolve("src/main/java/de/x"));
        Files.writeString(root.resolve("src/main/java/de/x/Same.java"), same);
        Files.writeString(root.resolve("src/main/java/de/x/Old.java"), "old");
        Map<String, String> files = new LinkedHashMap<>();
        files.put("IMPLEMENTIERUNG.md", "# x");
        files.put("src/main/java/de/x/", null);
        files.put("src/main/java/de/x/New.java", java("de.x", "New", ""));
        files.put("src/main/java/de/x/Old.java", java("de.x", "Old", ""));
        files.put("src/main/java/de/x/Same.java", same);
        Plan p = ImplementationImport.plan(zip("a.zip", files), root, "Risiko", null, null, null);
        assertTrue(p.hasReport());
        assertEquals(3, p.entries().size());
        assertEquals(Status.NEW, find(p, "src/main/java/de/x/New.java").status());
        assertEquals(Status.OVERWRITE, find(p, "src/main/java/de/x/Old.java").status());
        assertEquals(Status.IDENTICAL, find(p, "src/main/java/de/x/Same.java").status());
        assertFalse(p.hasProblems());
    }

    @Test
    void rejectsUnsafePaths() throws Exception {
        Path root = root();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("IMPLEMENTIERUNG.md", "x");
        files.put("../evil.java", "class Evil {}");
        files.put("a/../../evil2.java", "class Evil {}");
        files.put("/abs/evil3.java", "class Evil {}");
        files.put("C:/evil4.java", "class Evil {}");
        files.put("ok/Fine.txt", "fine");
        Plan p = ImplementationImport.plan(zip("b.zip", files), root, "Risiko", null, null, null);
        assertEquals(4, p.count(Status.REJECTED));
        assertTrue(p.hasProblems());
        List<Path> written = ImplementationImport.apply(p, tmp.resolve("bak"), true);
        assertEquals(List.of(root.resolve("ok/Fine.txt")), written);
        assertFalse(Files.exists(tmp.resolve("evil.java")));
        assertFalse(Files.exists(tmp.resolve("evil2.java")));
    }

    @Test
    void backslashesDuplicatesAndUtf8() throws Exception {
        Path root = root();
        Map<String, String> files = new LinkedHashMap<>();
        files.put("IMPLEMENTIERUNG.md", "x");
        files.put("src\\main\\java\\de\\x\\Ü.java", java("de.x", "Ue", "// erste Fassung"));
        Path z = tmp.resolve("c.zip");
        try (OutputStream out = Files.newOutputStream(z); ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            // a ZIP can hold the same name twice; the last one wins
            zos.putNextEntry(new ZipEntry("src/main/java/de/x/Ü.java"));
            zos.write(java("de.x", "Ue", "// zweite Fassung").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Plan p = ImplementationImport.plan(z, root, "Risiko", null, null, null);
        assertEquals(1, p.entries().size());
        assertEquals("src/main/java/de/x/Ü.java", p.entries().get(0).path());
        ImplementationImport.apply(p, tmp.resolve("bak"), false);
        assertTrue(Files.readString(root.resolve("src/main/java/de/x/Ü.java")).contains("zweite Fassung"));
    }

    @Test
    void missingReportAndPackageMismatchAndOpl() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("src/main/java/de/x/A.java", java("de.y", "A", "// TODO OPL-1 klären\n// TODO OPL-2"));
        Plan p = ImplementationImport.plan(zip("d.zip", files), root(), "Risiko", null, null, null);
        assertFalse(p.hasReport());
        assertTrue(hasCheck(p, "IMPLEMENTIERUNG.md fehlt", true));
        assertTrue(hasCheck(p, "Package passt nicht", true));
        assertTrue(hasCheck(p, "2 offene Punkte", false));
    }

    @Test
    void targetAttributes() throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("IMPLEMENTIERUNG.md", "x");
        files.put("src/main/java/de/x/A.java", java("de.x", "A", "private String RisikoNr; // gefahrenklasse"));
        Plan p = ImplementationImport.plan(zip("e.zip", files), root(), "Risiko", null, null,
                List.of("risikonr", "Gefahrenklasse", "Praemie"));
        assertFalse(hasCheck(p, "„risikonr“", true));
        assertFalse(hasCheck(p, "„Gefahrenklasse“", true));
        assertTrue(hasCheck(p, "Zielattribut „Praemie“ kommt in keiner Datei vor", true));
    }

    // ---- with real standards (Risiko) ----

    private StandardsTool.Stored standards() throws Exception {
        return StandardsTool.load(Path.of("migrationsprojekte/testziel/standards"));
    }

    /** Realistic result ZIP for Risiko: all artifact paths of the standards plus the report. */
    private Path sampleZip(StandardsTool.Stored st, List<String> phases, String skip) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("IMPLEMENTIERUNG.md", "# Implementierung Risiko\n\n## Umsetzung je Attribut\n\n## Artefakte\n\n## OPL\n");
        for (StandardsTool.Row row : st.rows()) {
            if (phases != null && phases.stream().noneMatch(ph -> ph.equalsIgnoreCase(row.phase()))) {
                continue;
            }
            String path = StandardsTool.apply(row.pathOrDefault(), "Risiko");
            if (path.equals(skip)) {
                continue;
            }
            String cls = StandardsTool.apply(row.className(), "Risiko");
            String pkg = StandardsTool.apply(row.pkg(), "Risiko");
            files.put(path, java(pkg, cls, "    private String risikoNr;\n    private String gefahr; // TODO OPL-1"));
        }
        return zip("implementierung-risiko.zip", files);
    }

    @Test
    void sampleZipIsComplete() throws Exception {
        StandardsTool.Stored st = standards();
        Plan p = ImplementationImport.plan(sampleZip(st, null, null), root(), "Risiko", st, null,
                List.of("risikoNr", "Gefahr"));
        assertEquals(8, p.count(Status.NEW));
        assertEquals(0, p.count(Status.UNEXPECTED));
        assertFalse(p.hasProblems(), p.checks().toString());
        assertTrue(hasCheck(p, "8 offene Punkte", false));
    }

    @Test
    void phasesRestrictExpectation() throws Exception {
        StandardsTool.Stored st = standards();
        Path z = sampleZip(st, null, null);
        Plan p = ImplementationImport.plan(z, root(), "Risiko", st, List.of("Read", "Process"), null);
        // the 7 Read/Process artifacts are expected, the Writer is unexpected but no problem
        assertEquals(7, p.count(Status.NEW));
        assertEquals(1, p.count(Status.UNEXPECTED));
        assertTrue(hasCheck(p, "Writer.java", false));
        assertFalse(p.hasProblems());
    }

    @Test
    void missingExpectedArtifact() throws Exception {
        StandardsTool.Stored st = standards();
        Plan p = ImplementationImport.plan(sampleZip(st, null, TO), root(), "Risiko", st, null, null);
        assertTrue(hasCheck(p, "Erwartetes Artefakt fehlt im ZIP: " + TO, true));
        assertTrue(p.hasProblems());
    }

    @Test
    void backupAndOverwriteFlag() throws Exception {
        StandardsTool.Stored st = standards();
        Path root = root();
        Files.createDirectories(root.resolve(PO).getParent());
        Files.writeString(root.resolve(PO), "ALT");
        Plan p = ImplementationImport.plan(sampleZip(st, null, null), root, "Risiko", st, null, null);
        assertEquals(Status.OVERWRITE, find(p, PO).status());
        Path backup = tmp.resolve("backup");

        List<Path> written = ImplementationImport.apply(p, backup, false);
        assertEquals(7, written.size());
        assertEquals("ALT", Files.readString(root.resolve(PO)));
        assertFalse(Files.exists(backup.resolve(PO)));
        assertTrue(Files.exists(root.resolve(TO)));
        assertFalse(Files.exists(root.resolve("IMPLEMENTIERUNG.md")));

        // second run: only the overwrite entry is left to do
        Plan p2 = ImplementationImport.plan(p.zip(), root, "Risiko", st, null, null);
        assertEquals(7, p2.count(Status.IDENTICAL));
        written = ImplementationImport.apply(p2, backup, true);
        assertEquals(List.of(root.resolve(PO)), written);
        assertEquals("ALT", Files.readString(backup.resolve(PO)));
        assertTrue(Files.readString(root.resolve(PO)).contains("class RisikoPO"));
        assertEquals(List.of(), new ArrayList<>(ImplementationImport.apply(
                ImplementationImport.plan(p.zip(), root, "Risiko", st, null, null), backup, true)));
    }

    @Test
    void unexpectedAdditionalArtefactIsWrittenWhenNew() throws Exception {
        Path root = root();
        var standards = de.devtoolbox.tools.standards.StandardsTool.load(Path.of("migrationsprojekte/testziel/standards"));
        String extra = "src/main/java/de/vers/neu/risiko/anreicherung/RisikoZusatzAnreicherer.java";
        Map<String, String> files = new LinkedHashMap<>();
        files.put("IMPLEMENTIERUNG.md", "# x");
        files.put(extra, java("de.vers.neu.risiko.anreicherung", "RisikoZusatzAnreicherer", ""));
        Plan p = ImplementationImport.plan(zip("extra.zip", files), root, "Risiko", standards, java.util.List.of("Read"), null);
        assertEquals(Status.UNEXPECTED, find(p, extra).status());
        var written = ImplementationImport.apply(p, tmp.resolve("backup"), false);
        assertEquals(1, written.size());
        assertTrue(Files.exists(root.resolve(extra)));
        // existing additional file: only replaced with overwrite
        Files.writeString(root.resolve(extra), "geaendert");
        Plan again = ImplementationImport.plan(zip("extra2.zip", files), root, "Risiko", standards, java.util.List.of("Read"), null);
        assertEquals(0, ImplementationImport.apply(again, tmp.resolve("backup"), false).size());
        assertEquals(1, ImplementationImport.apply(again, tmp.resolve("backup"), true).size());
    }
}
