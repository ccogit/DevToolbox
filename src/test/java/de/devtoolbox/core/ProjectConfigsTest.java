package de.devtoolbox.core;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Job configurations per project and the export/import of a whole project (copy of migrationsprojekte in a temp dir). */
class ProjectConfigsTest {

    private static final String PROP = "devtoolbox.projekte";

    @TempDir
    Path tmp;

    private String previous;

    @BeforeEach
    void setUp() throws Exception {
        previous = System.getProperty(PROP);
        Path projekte = tmp.resolve("migrationsprojekte");
        TestSupport.copyTree(TestSupport.PROJEKTE, projekte);
        System.setProperty(PROP, projekte.toString());
    }

    @AfterEach
    void tearDown() {
        if (previous == null) {
            System.clearProperty(PROP);
        } else {
            System.setProperty(PROP, previous);
        }
    }

    private static Map<String, Object> values(MigrationProject p, String entity) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("entity", entity);
        v.put("phases", List.of("READ", "WRITE"));
        v.put("compact", true);
        v.put("standards", p.values().getOrDefault("standards", "")); // equals the project's value: not stored
        v.put("context", "");
        return v;
    }

    @Test
    void storedPerProjectAndJob() throws Exception {
        MigrationProject testziel = MigrationProject.find("testziel").orElseThrow();
        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        assertTrue(ProjectConfigs.load(testziel, "implementation").isEmpty());

        ProjectConfigs.save(testziel, "implementation", values(testziel, "Risiko"));
        ProjectConfigs.save(vgb, "implementation", values(vgb, "Deckung"));
        assertTrue(Files.isRegularFile(testziel.folder().resolve("konfigurationen/implementation.json")));

        Map<String, String> loaded = ProjectConfigs.load(testziel, "implementation");
        assertEquals("Risiko", loaded.get("entity"));
        assertEquals("READ, WRITE", loaded.get("phases"));
        assertEquals("true", loaded.get("compact"));
        assertFalse(loaded.containsKey("standards"), "values of the project itself are not stored");
        assertEquals("Deckung", ProjectConfigs.load(vgb, "implementation").get("entity"));
        assertTrue(ProjectConfigs.load(testziel, "phase-spec").isEmpty());

        // the file is a job configuration the CLI can run: job and project are named in it
        Map<String, String> file = Json.parse(Files.readString(ProjectConfigs.file(testziel, "implementation")));
        assertEquals("Risiko", file.get("entity"));
        assertEquals("implementation", file.get("job"));
        assertEquals("testziel", file.get("project"));
    }

    @Test
    void notStoredWithoutProjectFolder() throws Exception {
        MigrationProject gone = new MigrationProject("weg", "WEG", "", "", null, null, "", null, null, null, "",
                tmp.resolve("nicht-da"));
        ProjectConfigs.save(gone, "implementation", Map.of("entity", "X"));
        assertFalse(Files.exists(tmp.resolve("nicht-da")));
        assertTrue(ProjectConfigs.load(gone, "implementation").isEmpty());
    }

    @Test
    void exportImportRoundTrip() throws Exception {
        MigrationProject testziel = MigrationProject.find("testziel").orElseThrow();
        ProjectConfigs.save(testziel, "implementation", values(testziel, "Risiko"));
        ProjectConfigs.save(testziel, "phase-spec", Map.of("terms", "Risiko, Risk"));
        Path file = tmp.resolve("export.json");
        ProjectConfigs.export(testziel, file);
        String text = Files.readString(file);
        assertTrue(text.contains("\"projekt.name\""), text);
        assertTrue(text.contains("\"job.implementation.entity\": \"Risiko\""), text);
        assertEquals("testziel", ProjectConfigs.exportedId(file));

        ProjectConfigs.importInto(file, "kopie");
        MigrationProject copy = MigrationProject.find("kopie").orElseThrow();
        assertEquals(testziel.name(), copy.name());
        assertEquals(testziel.entities(), copy.entities());
        assertEquals(testziel.sparte(), copy.sparte());
        assertEquals("Risiko", ProjectConfigs.load(copy, "implementation").get("entity"));
        assertEquals("READ, WRITE", ProjectConfigs.load(copy, "implementation").get("phases"));
        assertEquals("Risiko, Risk", ProjectConfigs.load(copy, "phase-spec").get("terms"));
        assertEquals("kopie", ProjectConfigs.load(copy, "implementation").get("project"));

        // import into an existing project replaces its definition and the configurations of the file
        ProjectConfigs.save(copy, "implementation", values(copy, "Anderes"));
        ProjectConfigs.importInto(file, "kopie");
        assertEquals("Risiko", ProjectConfigs.load(MigrationProject.find("kopie").orElseThrow(), "implementation").get("entity"));
    }

    @Test
    void rejectsOtherFiles() throws Exception {
        Path other = tmp.resolve("andere.json");
        Files.writeString(other, "{\"job\": \"implementation\", \"entity\": \"Risiko\"}");
        assertThrows(IllegalArgumentException.class, () -> ProjectConfigs.exportedId(other));
        Files.writeString(other, "kein json");
        assertThrows(IllegalArgumentException.class, () -> ProjectConfigs.exportedId(other));
        Path bad = tmp.resolve("bad.json");
        Files.writeString(bad, "{\"format\": \"" + ProjectConfigs.FORMAT + "\", \"id\": \"../x\"}");
        assertThrows(IllegalArgumentException.class, () -> ProjectConfigs.exportedId(bad));
    }
}
