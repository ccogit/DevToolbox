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
