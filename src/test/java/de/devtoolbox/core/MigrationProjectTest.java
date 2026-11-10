package de.devtoolbox.core;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Works on a copy of migrationsprojekte in a temp dir (system property devtoolbox.projekte). */
class MigrationProjectTest {

    private static final String PROP = "devtoolbox.projekte";

    @TempDir
    Path tmp;

    private Path projekte;
    private String previous;

    @BeforeEach
    void setUp() throws Exception {
        previous = System.getProperty(PROP);
        projekte = tmp.resolve("migrationsprojekte");
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

    @Test
    void listsProjectsSortedByName() {
        List<String> ids = MigrationProject.all().stream().map(MigrationProject::id).toList();
        assertTrue(ids.containsAll(List.of("plg", "test", "testziel", "vgb", "vhv")), ids.toString());
        assertEquals(projekte, MigrationProject.directory());
        assertTrue(MigrationProject.find("").isEmpty());
        assertTrue(MigrationProject.find(null).isEmpty());
        assertTrue(MigrationProject.find("nix").isEmpty());
        assertEquals("TESTZIEL", MigrationProject.find(" TestZiel ").orElseThrow().name());
    }

    @Test
    void referenceByProjectId() {
        MigrationProject testziel = MigrationProject.find("testziel").orElseThrow();
        MigrationProject test = MigrationProject.find("test").orElseThrow();
        assertNotNull(testziel.reference());
        assertEquals(test.root(), testziel.reference());
        assertTrue(testziel.referenceLabel().startsWith("TEST"), testziel.referenceLabel());
        assertEquals("TESTZIEL – Wohngebäude neu – Testdaten", testziel.label());
        Map<String, String> values = testziel.values();
        assertEquals(testziel.reference().toString(), values.get("base"));
        assertEquals(testziel.targetModel().toString(), values.get("targetModel"));
        assertEquals(testziel.standardsDirectory().toString(), values.get("standards"));
        assertEquals(testziel.ddl().toString(), values.get("ddl"));
        assertTrue(Files.isRegularFile(testziel.standardsDirectory().resolve("STANDARDS.md")));
    }

    @Test
    void unconfiguredProjectHasNoValues() {
        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        assertNull(vgb.reference());
        assertEquals("", vgb.referenceLabel());
        assertNull(vgb.root());
        assertFalse(vgb.values().containsKey("base"));
        assertFalse(vgb.values().containsKey("target"));
        assertEquals(vgb.folder().resolve("ddl"), vgb.schemaDirectory());
    }

    @Test
    void orphanedReference() throws Exception {
        TestSupport.class.getName();
        deleteTree(projekte.resolve("test"));
        MigrationProject testziel = MigrationProject.find("testziel").orElseThrow();
        assertNull(testziel.reference());
        assertEquals("test", testziel.referenceLabel());
        assertFalse(testziel.values().containsKey("base"));
    }

    @Test
    void referenceByPath() throws Exception {
        Path json = projekte.resolve("vgb/projekt.json");
        Map<String, Object> m = new java.util.LinkedHashMap<>(Json.parse(Files.readString(json)));
        m.put("referenz", "../../testprojekt");
        Files.writeString(json, Json.write(m));
        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        assertEquals(projekte.resolve("vgb").resolve("../../testprojekt").normalize(), vgb.reference());
        assertEquals(vgb.reference().toString(), vgb.referenceLabel());
    }

    @Test
    void ensureDdlEntry() throws Exception {
        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        Path other = vgb.folder().resolve("schema2");
        Files.createDirectories(other);
        vgb.ensureDdlEntry(other);
        MigrationProject reloaded = MigrationProject.find("vgb").orElseThrow();
        assertEquals(other, reloaded.ddl());
        assertEquals("schema2", Json.parse(Files.readString(vgb.folder().resolve("projekt.json"))).get("ddl"));
        assertEquals("VGB", reloaded.name(), "other entries kept");

        // already pointing there: file untouched
        String before = Files.readString(vgb.folder().resolve("projekt.json"));
        reloaded.ensureDdlEntry(other);
        assertEquals(before, Files.readString(vgb.folder().resolve("projekt.json")));
    }

    @Test
    void schemaDirectoryOfSingleFile() throws Exception {
        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        Path ddlDir = vgb.folder().resolve("ddl");
        Path file = ddlDir.resolve("a.ddl");
        Files.writeString(file, "CREATE TABLE T (A INT);");
        vgb.ensureDdlEntry(file);
        assertEquals(ddlDir, MigrationProject.find("vgb").orElseThrow().schemaDirectory());
    }

    @Test
    void missingProjectsFolder() {
        System.setProperty(PROP, tmp.resolve("gibtsnicht").toString());
        assertNull(MigrationProject.directory());
        assertTrue(MigrationProject.all().isEmpty());
    }

    private static void deleteTree(Path dir) throws Exception {
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
