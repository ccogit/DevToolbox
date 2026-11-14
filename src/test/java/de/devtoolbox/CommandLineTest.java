package de.devtoolbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineTest {

    @TempDir
    Path temporary;

    @Test
    void readsJobIdFromFirstArgumentAndKeepsValuesContainingEquals() throws Exception {
        CommandLine input = CommandLine.parse(new String[]{"spec-read", "terms=Risiko", "context=a=b", "invalid"});

        assertEquals("spec-read", input.jobId());
        assertTrue(input.fileValues().isEmpty());
        assertEquals("Risiko", input.overrides().get("terms"));
        assertEquals("a=b", input.overrides().get("context"));
        assertFalse(input.overrides().containsKey("invalid"));
    }

    @Test
    void readsConfigFileAndPrefersCommandLineProject() throws Exception {
        Path config = temporary.resolve("job.json");
        Files.writeString(config, """
                {"job": "phase-spec", "project": "vgb", "terms": "Risiko"}
                """);

        CommandLine input = CommandLine.parse(new String[]{config.toString(), "project=vhv", "terms=Wagnis"});
        assertEquals("phase-spec", input.jobId());
        assertEquals("vgb", input.fileValues().get("project"));
        assertFalse(input.fileValues().containsKey("job"));
        assertEquals("vhv", input.projectId());
        assertEquals("Wagnis", input.overrides().get("terms"));
    }

    @Test
    void requiresACommandAndExposesImmutableLayers() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse(new String[0]));
        CommandLine input = CommandLine.parse(new String[]{"phase-spec", "project=vgb"});
        assertEquals("vgb", input.projectId());
        assertThrows(UnsupportedOperationException.class, () -> input.overrides().put("project", "vhv"));
    }
}
