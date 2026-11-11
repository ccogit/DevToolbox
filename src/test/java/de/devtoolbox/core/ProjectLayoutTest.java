package de.devtoolbox.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectLayoutTest {

    @Test
    void designerServicesNextToTheMigrationModule(@TempDir Path tmp) throws Exception {
        Path parent = tmp.resolve("firma-sachvertrag");
        Path root = Files.createDirectories(parent.resolve("firma-sachvertrag-vgb-migration"));
        assertNull(ProjectLayout.designerModule(root));
        assertNull(ProjectLayout.targetModel(root));
        assertTrue(ProjectLayout.derived(root).isEmpty());

        Path designer = Files.createDirectories(parent.resolve("firma-sachvertrag-designer-services"));
        assertEquals(designer, ProjectLayout.designerModule(root));
        assertEquals(designer.resolve("doc/migration/properties"), ProjectLayout.targetModel(root));
        assertEquals(designer.resolve("resources/documentation"), ProjectLayout.mappingTemplates(root),
                "default even if the folder does not exist yet");
        Path alternative = Files.createDirectories(designer.resolve("src/main/resources/documentation"));
        assertEquals(alternative, ProjectLayout.mappingTemplates(root), "existing alternative wins");
        assertEquals(designer.resolve("doc/migration/properties").toString(),
                ProjectLayout.derived(root).get(Keys.TARGET_MODEL));
        assertEquals("Sachversicherung", ProjectLayout.sparte(root));
    }

    @Test
    void otherParentNameStillFindsTheOnlyDesignerModule(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("checkout/migration"));
        Path designer = Files.createDirectories(tmp.resolve("checkout/firma-haftpflichtvertrag-designer-services"));
        assertEquals(designer, ProjectLayout.designerModule(root));
    }

    /** C:/firma-develop with both line projects; Sach holds two migrations (VGB, VHV). */
    @Test
    void everythingFromTheDevelopmentFolder(@TempDir Path develop) throws Exception {
        Path haft = Files.createDirectories(develop.resolve("firma-haftpflichtvertrag"));
        Path plg = Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-migration"));
        Path haftDesigner = Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-designer-services"));
        Path sach = Files.createDirectories(develop.resolve("firma-sachvertrag"));
        Path vgb = Files.createDirectories(sach.resolve("firma-sachvertrag-vgb-migration"));
        Path vhv = Files.createDirectories(sach.resolve("firma-sachvertrag-vhv-migration"));
        Files.createDirectories(sach.resolve("firma-sachvertrag-designer-services"));
        Files.createDirectories(develop.resolve("firma-sonstwas"));

        String before = System.getProperty(ProjectLayout.DEVELOP_PROPERTY);
        System.setProperty(ProjectLayout.DEVELOP_PROPERTY, develop.toString());
        try {
            assertEquals(haft, ProjectLayout.lineProject("Haftpflichtversicherung"));
            assertEquals(sach, ProjectLayout.lineProject("Sachversicherung"));
            assertNull(ProjectLayout.lineProject("Kraftfahrt"));
            assertEquals(plg, ProjectLayout.root("Haftpflichtversicherung", "plg"), "only migration module");
            assertEquals(vgb, ProjectLayout.root("Sachversicherung", "vgb"));
            assertEquals(vhv, ProjectLayout.root("Sachversicherung", "vhv"));
            assertNull(ProjectLayout.root("Sachversicherung", "xyz"), "two migrations, none named xyz");
            assertEquals(haftDesigner, ProjectLayout.designer(null, "Haftpflichtversicherung"), "without root");
            assertEquals(haftDesigner.resolve("doc/migration/properties"), ProjectLayout.targetModel(plg));
        } finally {
            restore(ProjectLayout.DEVELOP_PROPERTY, before);
        }
    }

    /** projekt.json without root, zielmodell, mappingvorlagen: everything is derived; explicit entries win. */
    @Test
    void migrationProjectDerivesItsPaths(@TempDir Path tmp) throws Exception {
        Path develop = tmp.resolve("firma-develop");
        Path haft = develop.resolve("firma-haftpflichtvertrag");
        Path plg = Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-plg"));
        Path designer = Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-designer-services"));
        Path vgb = Files.createDirectories(develop.resolve("firma-sachvertrag/firma-sachvertrag-vgb"));
        Path projects = Files.createDirectories(tmp.resolve("migrationsprojekte"));
        Files.createDirectories(projects.resolve("plg"));
        Files.writeString(projects.resolve("plg/projekt.json"), """
                {"name": "PLG", "sparte": "Haftpflichtversicherung", "root": "", "referenz": "vgb", "zielmodell": "",
                 "mappingvorlagen": "C:/anders/doku"}
                """);
        Files.createDirectories(projects.resolve("vgb"));
        Files.writeString(projects.resolve("vgb/projekt.json"), "{\"name\": \"VGB\", \"sparte\": \"Sachversicherung\"}");

        String beforeDevelop = System.getProperty(ProjectLayout.DEVELOP_PROPERTY);
        String beforeProjects = System.getProperty("devtoolbox.projekte");
        System.setProperty(ProjectLayout.DEVELOP_PROPERTY, develop.toString());
        System.setProperty("devtoolbox.projekte", projects.toString());
        try {
            MigrationProject p = MigrationProject.find("plg").orElseThrow();
            assertEquals(plg, p.root());
            assertEquals(vgb, p.reference(), "reference by id uses the derived root of VGB");
            assertEquals(designer.resolve("doc/migration/properties"), p.targetModel());
            assertEquals(Path.of("C:/anders/doku"), p.mappingTemplates(), "explicit entry wins");
        } finally {
            restore(ProjectLayout.DEVELOP_PROPERTY, beforeDevelop);
            restore("devtoolbox.projekte", beforeProjects);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void jiraTickets() {
        assertEquals("HAFT", ProjectLayout.jiraPrefix("Haftpflichtversicherung", null));
        assertEquals("SACH", ProjectLayout.jiraPrefix("Sachversicherung", null));
        assertEquals("HAFT", ProjectLayout.jiraPrefix("", Path.of("C:/x/firma-haftpflichtvertrag/migration")));
        assertEquals("", ProjectLayout.jiraPrefix("", null));
        assertEquals("HAFT-6554", ProjectLayout.jiraIssue("6554", "HAFT"));
        assertEquals("SACH-12", ProjectLayout.jiraIssue(" sach-12 ", "HAFT"));
        assertEquals("HAFT-1", ProjectLayout.jiraIssue("{jiraissues:HAFT-1}", ""));
        assertThrows(IllegalArgumentException.class, () -> ProjectLayout.jiraIssue("6554", ""));
        assertThrows(IllegalArgumentException.class, () -> ProjectLayout.jiraIssue("HAFT 6554", "HAFT"));
        assertThrows(IllegalArgumentException.class, () -> ProjectLayout.jiraIssue("", "HAFT"));
    }
}
