package de.devtoolbox.ui;

import de.devtoolbox.core.MigrationProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectOverviewTest {

    @TempDir
    Path temporary;

    @Test
    void missingResourcesProduceWarningSummary() {
        MigrationProject project = project(null);
        ProjectOverview.Summary summary = ProjectOverview.of(project);

        assertTrue(summary.warning());
        assertTrue(summary.description().contains("kein Zielmodell"));
        assertTrue(summary.description().contains("Referenzprojekt nicht konfiguriert"));
    }

    @Test
    void configuredReferenceIsReported() throws Exception {
        Path reference = Files.createDirectory(temporary.resolve("reference"));
        ProjectOverview.Summary summary = ProjectOverview.of(project(reference));

        assertTrue(summary.description().contains("Referenz: " + reference));
        assertFalse(summary.description().contains("Referenzprojekt nicht konfiguriert"));
    }

    private MigrationProject project(Path reference) {
        return new MigrationProject("test", "Test", "", "", null, reference,
                reference == null ? "" : reference.toString(), null, null, null, "", temporary);
    }
}
