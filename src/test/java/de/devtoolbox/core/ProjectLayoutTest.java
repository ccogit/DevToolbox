package de.devtoolbox.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectLayoutTest {

    /** Neutral Jira projects, independent of the local settings file of the machine running the tests. */
    private final java.util.Map<String, String> savedJira = new java.util.HashMap<>();

    @org.junit.jupiter.api.BeforeEach
    void neutralJiraProjects() {
        for (String key : java.util.List.of(ProjectLayout.JIRA_LIABILITY_PROPERTY, ProjectLayout.JIRA_PROPERTY_PROPERTY)) {
            savedJira.put(key, System.getProperty(key));
        }
        System.setProperty(ProjectLayout.JIRA_LIABILITY_PROPERTY, "HAFT");
        System.setProperty(ProjectLayout.JIRA_PROPERTY_PROPERTY, "SACH");
    }

    @org.junit.jupiter.api.AfterEach
    void restoreJiraProjects() {
        savedJira.forEach(ProjectLayoutTest::restore);
    }

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

    /** A development folder with non-uniform module names (prefix "firma-"); Sach holds VGB and the general module of VHV. */
    @Test
    void everythingFromTheDevelopmentFolder(@TempDir Path develop) throws Exception {
        Path haft = Files.createDirectories(develop.resolve("firma-haftpflichtvertrag"));
        Path plg = Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-plg-migration"));
        Path haftDesigner = Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-designer-services"));
        Files.createDirectories(haft.resolve("firma-haftpflichtvertrag-service"));
        Path sach = Files.createDirectories(develop.resolve("firma-sachvertrag"));
        Path vgb = Files.createDirectories(sach.resolve("firma-sachvertrag-vgb-migration-batch"));
        Path vhv = Files.createDirectories(sach.resolve("firma-sachvertrag-migration"));
        Files.createDirectories(sach.resolve("firma-sachvertrag-designer-services"));
        Files.createDirectories(develop.resolve("firma-sonstwas"));

        String before = System.getProperty(ProjectLayout.DEVELOP_PROPERTY);
        String beforePrefix = System.getProperty(ProjectLayout.PREFIX_PROPERTY);
        System.setProperty(ProjectLayout.DEVELOP_PROPERTY, develop.toString());
        System.setProperty(ProjectLayout.PREFIX_PROPERTY, "firma-");
        try {
            assertEquals(haft, ProjectLayout.lineProject("Haftpflichtversicherung"));
            assertEquals(sach, ProjectLayout.lineProject("Sachversicherung"));
            assertNull(ProjectLayout.lineProject("Kraftfahrt"));
            assertEquals(plg, ProjectLayout.root("Haftpflichtversicherung", "plg"));
            assertEquals(vgb, ProjectLayout.root("Sachversicherung", "vgb"));
            assertEquals(vhv, ProjectLayout.root("Sachversicherung", "vhv"), "general module <parent>-migration");
            assertEquals(haftDesigner, ProjectLayout.designer(null, "Haftpflichtversicherung"), "without root");
            assertEquals(haftDesigner.resolve("doc/migration/properties"), ProjectLayout.targetModel(plg));
        } finally {
            restore(ProjectLayout.DEVELOP_PROPERTY, before);
            restore(ProjectLayout.PREFIX_PROPERTY, beforePrefix);
        }
    }

    @Test
    void migrationModuleFallbacks(@TempDir Path line) throws Exception {
        Path only = Files.createDirectories(line.resolve("x-migration-batch"));
        Files.createDirectories(line.resolve("x-service"));
        assertEquals(only, ProjectLayout.migrationModule(line, "abc"), "the only migration module");
        Files.createDirectories(line.resolve("y-migration"));
        assertNull(ProjectLayout.migrationModule(line, "abc"), "two migration modules, none with the id");
        Path withId = Files.createDirectories(line.resolve("x-abc-migration"));
        Files.createDirectories(line.resolve("x-abc-tools"));
        assertEquals(withId, ProjectLayout.migrationModule(line, "abc"), "with the id, the migration module wins");
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
        String beforePrefix = System.getProperty(ProjectLayout.PREFIX_PROPERTY);
        System.setProperty(ProjectLayout.DEVELOP_PROPERTY, develop.toString());
        System.setProperty(ProjectLayout.PREFIX_PROPERTY, "firma-");
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
            restore(ProjectLayout.PREFIX_PROPERTY, beforePrefix);
        }
    }

    /**
     * Local settings: stored next to the projects (migrationsprojekte/umgebung.properties), the profile file is only a
     * fallback; system properties win; without any of them the neutral defaults apply. Saving round-trips.
     */
    @Test
    void localSettings(@TempDir Path tmp) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path projects = Files.createDirectories(tmp.resolve("migrationsprojekte"));
        List<String> keys = List.of("user.home", "devtoolbox.projekte", ProjectLayout.DEVELOP_PROPERTY,
                ProjectLayout.PREFIX_PROPERTY, ProjectLayout.JIRA_LIABILITY_PROPERTY, ProjectLayout.JIRA_PROPERTY_PROPERTY);
        java.util.Map<String, String> before = new java.util.HashMap<>();
        keys.forEach(k -> before.put(k, System.getProperty(k)));
        System.setProperty("user.home", home.toString());
        System.setProperty("devtoolbox.projekte", projects.toString());
        keys.subList(2, keys.size()).forEach(System::clearProperty);
        try {
            assertEquals(projects.resolve("umgebung.properties"), ProjectLayout.settingsFile(), "next to the projects");
            assertEquals(ProjectLayout.DEVELOP_ROOT, ProjectLayout.developRoot());
            assertEquals("", ProjectLayout.projectPrefix());
            assertEquals("HAFT", ProjectLayout.jiraLiability());

            // only the profile file (fallback)
            Files.createDirectories(ProjectLayout.profileSettingsFile().getParent());
            Files.writeString(ProjectLayout.profileSettingsFile(), "entwicklungsordner=D:/profil\nprojektpraefix=profil-\n");
            assertEquals(Path.of("D:/profil"), ProjectLayout.developRoot());
            assertEquals("profil-", ProjectLayout.projectPrefix());

            // saved next to the projects: wins over the profile, also with an empty value
            Path file = ProjectLayout.saveSettings(new ProjectLayout.Settings("D:\\dev", "", "abc", "XYZ"));
            assertEquals(projects.resolve("umgebung.properties"), file);
            assertEquals(Path.of("D:/dev"), ProjectLayout.developRoot(), "backslashes survive as slashes");
            assertEquals("", ProjectLayout.projectPrefix(), "empty value overrides the profile");
            assertEquals("ABC", ProjectLayout.jiraPrefix("Haftpflichtversicherung", null));
            assertEquals("XYZ", ProjectLayout.jiraPrefix("Sachversicherung", null));
            assertEquals(new ProjectLayout.Settings("D:/dev", "", "ABC", "XYZ"), ProjectLayout.storedSettings());

            System.setProperty(ProjectLayout.PREFIX_PROPERTY, "andere-");
            assertEquals("andere-", ProjectLayout.projectPrefix(), "system property wins");
            assertEquals(List.of("-D" + ProjectLayout.PREFIX_PROPERTY), ProjectLayout.overridingProperties());
        } finally {
            before.forEach(ProjectLayoutTest::restore);
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
