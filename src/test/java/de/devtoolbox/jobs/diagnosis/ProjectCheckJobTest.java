package de.devtoolbox.jobs.diagnosis;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.JobRegistry;
import de.devtoolbox.core.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ProjectCheckJobTest {

    @Test
    void singleConfiguredProjectNeedsNoManualFieldsAndNeverModifiesSources(@TempDir Path tmp) throws Exception {
        Path projects = Files.createDirectories(tmp.resolve("projects"));
        Path project = Files.createDirectories(projects.resolve("sample"));
        Path code = Files.createDirectories(tmp.resolve("migration"));
        Path marker = code.resolve("important.txt");
        Files.writeString(marker, "unchanged");
        Path model = Files.createDirectories(tmp.resolve("model"));
        Files.writeString(model.resolve("Risiko.json"), """
                {"entitaet":"Risiko","attribute":[{"attributName":"id"}]}
                """);
        Files.writeString(project.resolve("projekt.json"),
                "{\"name\":\"Sample\",\"root\":\"" + code.toString().replace("\\", "\\\\")
                + "\",\"zielmodell\":\"" + model.toString().replace("\\", "\\\\") + "\"}");

        String previous = System.getProperty("devtoolbox.projekte");
        System.setProperty("devtoolbox.projekte", projects.toString());
        try {
            var job = new ProjectCheckJob();
            assertEquals(ProjectCheckJob.ID, JobRegistry.find(ProjectCheckJob.ID).orElseThrow().id());
            assertEquals(1, job.params().size());
            assertFalse(job.params().getFirst().required());
            var log = new ArrayList<String>();
            List<Path> files = job.run(new Config(Map.of(Keys.OUTPUT, tmp.resolve("output").toString())), log::add);
            assertEquals(1, files.size());
            String report = Files.readString(files.getFirst());
            assertTrue(report.contains("# Projekt-Check: Sample"), report);
            assertTrue(report.contains("Zielmodell"), report);
            assertTrue(report.contains("Risiko"), report);
            assertTrue(report.contains("Nächste Schritte"), report);
            assertTrue(report.contains("Altdaten-Schema"), report);
            assertEquals("unchanged", Files.readString(marker));
            List<Path> second = job.run(new Config(Map.of(Keys.OUTPUT, tmp.resolve("output").toString())), log::add);
            assertNotEquals(files.getFirst().getParent(), second.getFirst().getParent());
            assertTrue(Files.exists(files.getFirst()));
        } finally {
            if (previous == null) System.clearProperty("devtoolbox.projekte");
            else System.setProperty("devtoolbox.projekte", previous);
        }
    }

    @Test
    void ambiguousProjectSelectionExplainsWhatToDo(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("projects"));
        for (String id : List.of("one", "two")) {
            Path folder = Files.createDirectories(root.resolve(id));
            Files.writeString(folder.resolve("projekt.json"), "{\"name\":\"" + id + "\"}");
        }
        String previous = System.getProperty("devtoolbox.projekte");
        System.setProperty("devtoolbox.projekte", root.toString());
        try {
            var ex = assertThrows(IllegalArgumentException.class,
                    () -> new ProjectCheckJob().run(new Config(Map.of()), message -> {}));
            assertTrue(ex.getMessage().contains("mehrere Projekte"), ex.getMessage());
        } finally {
            if (previous == null) System.clearProperty("devtoolbox.projekte");
            else System.setProperty("devtoolbox.projekte", previous);
        }
    }
}
