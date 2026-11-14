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

/**
 * Works on a copy of migrationsprojekte in a temp dir (system property devtoolbox.projekte).
 */
class MigrationProjectTest {

    private static final String PROP = "devtoolbox.projekte";

    @TempDir
    Path tmp;

    private Path projekte;
    private String previous;

    private static void deleteTree(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

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
    void referenceAsBitbucketAddress() throws Exception {
        String url = "https://bitbucket.firma.de/bitbucket/projects/SACH/repos/firma-sachvertrag/browse"
                + "?at=refs%2Fheads%2Fmigration%2Fbeispiel_branch";
        Files.createDirectories(projekte.resolve("bb"));
        Files.writeString(projekte.resolve("bb/projekt.json"), Json.write(Map.of("name", "BB", "referenz", url)));
        MigrationProject bb = MigrationProject.find("bb").orElseThrow();
        assertNull(bb.reference());
        assertEquals(url, bb.referenceUrl());
        assertEquals(url, bb.values().get("base"));
        assertTrue(bb.isReference(url));
        assertFalse(bb.isReference("C:/anderer/ordner"));
        // a local reference has no address
        assertEquals("", MigrationProject.find("testziel").orElseThrow().referenceUrl());
    }

    @Test
    void sharedStandardsApplyUnlessProjectHasOwn() throws Exception {
        // the copy may contain the user's own shared data (old _standards / _basisdaten) – start without it
        deleteTree(projekte.resolve("_standards"));
        deleteTree(projekte.resolve("_basisdaten"));
        assertEquals(projekte.resolve("_basisdaten").resolve("standards"), MigrationProject.sharedStandardsDirectory());
        Path shared = Files.createDirectories(projekte.resolve("_basisdaten").resolve("standards"));
        assertEquals(shared, MigrationProject.sharedStandardsDirectory());
        assertTrue(MigrationProject.find("_basisdaten").isEmpty(), "_basisdaten is no project");

        MigrationProject vgb = MigrationProject.find("vgb").orElseThrow();
        assertFalse(vgb.hasOwnStandards());
        assertEquals(shared, vgb.standardsDirectory());
        assertEquals(shared.toString(), vgb.values().get("standards"));

        MigrationProject testziel = MigrationProject.find("testziel").orElseThrow();
        assertTrue(testziel.hasOwnStandards());
        assertEquals(testziel.ownStandardsDirectory(), testziel.standardsDirectory());

        deleteTree(testziel.ownStandardsDirectory());
        MigrationProject after = MigrationProject.find("testziel").orElseThrow();
        assertFalse(after.hasOwnStandards());
        assertEquals(shared, after.standardsDirectory());

        deleteTree(shared);
        assertNull(MigrationProject.find("vgb").orElseThrow().values().get("standards"));
    }

    @Test
    void sharedStandardsFallBackToFormerFolder() throws Exception {
        Path legacy = Files.createDirectories(projekte.resolve("_standards"));
        assertEquals(legacy, MigrationProject.sharedStandardsDirectory());
        assertTrue(MigrationProject.find("_standards").isEmpty(), "_standards is no project");
        Path current = Files.createDirectories(projekte.resolve("_basisdaten").resolve("standards"));
        assertEquals(current, MigrationProject.sharedStandardsDirectory(), "new folder wins");
    }

    @Test
    void sharedEntitiesRoundTrip() throws Exception {
        assertTrue(MigrationProject.sharedEntities().isEmpty());
        MigrationProject.saveSharedEntities(List.of("Risiko", " Vertrag ", "Schaden, Partner"));
        assertEquals(List.of("Risiko", "Vertrag", "Schaden", "Partner"), MigrationProject.sharedEntities());
        Path file = projekte.resolve("_basisdaten").resolve("entitaeten.json");
        assertTrue(Files.isRegularFile(file));
        assertEquals(List.of("Risiko", "Vertrag", "Schaden", "Partner"),
                MigrationProject.parseEntities(Json.parse(Files.readString(file)).get("entitaeten")));
        assertTrue(MigrationProject.find("_basisdaten").isEmpty());
    }

    @Test
    void sharedEntitiesFromSeveralReferenceFolders() throws Exception {
        Path other = tmp.resolve("other/src/de/x");
        Files.createDirectories(other);
        Files.writeString(other.resolve("NeuPartnerDto.java"), "package de.x; public class NeuPartnerDto {}");
        List<String> single = EntitySuggestions.fromFolders(List.of(TestSupport.TESTPROJEKT));
        assertFalse(single.isEmpty());
        List<String> merged = EntitySuggestions.fromFolders(List.of(TestSupport.TESTPROJEKT, TestSupport.TESTPROJEKT, tmp.resolve("other"),
                tmp.resolve("gibtsnicht")));
        assertTrue(merged.containsAll(single));
        assertTrue(merged.contains("Partner"), merged.toString());
        assertEquals(merged.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), merged);
        assertEquals(merged.size(), merged.stream().map(String::toLowerCase).distinct().count());
    }

    @Test
    void unconfiguredProjectHasNoValues() {
        // plg has neither root nor reference (vgb now points at vhv)
        MigrationProject plg = MigrationProject.find("plg").orElseThrow();
        assertNull(plg.reference());
        assertEquals("", plg.referenceLabel());
        assertNull(plg.root());
        assertFalse(plg.values().containsKey("base"));
        assertFalse(plg.values().containsKey("target"));
        assertEquals(plg.folder().resolve("ddl"), plg.schemaDirectory());
    }

    /** The synthetic test projects: VGB-Test uses the code of VHV-Test as reference; paths point at vgb/ and vhv/. */
    @Test
    void syntheticTestProjects() {
        MigrationProject vhv = MigrationProject.find("vhv-test").orElseThrow();
        MigrationProject vgb = MigrationProject.find("vgb-test").orElseThrow();
        Path repo = TestSupport.PROJEKTE.toAbsolutePath().getParent();
        assertEquals(vhv.root(), vgb.reference());
        assertTrue(vgb.referenceLabel().startsWith("VHV-Test"), vgb.referenceLabel());
        assertEquals(projekte.resolve("vgb-test").resolve("../../vgb").normalize(), vgb.root());
        assertTrue(Files.isDirectory(repo.resolve("vhv/src/main/java")), "VHV code exists");
        assertTrue(Files.isRegularFile(repo.resolve("vgb/zielmodell/Risiko.json")), "VGB target model exists");
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
}
