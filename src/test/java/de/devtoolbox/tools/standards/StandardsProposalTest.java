package de.devtoolbox.tools.standards;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandardsProposalTest {

    private static void write(Path root, String name) throws Exception {
        Path file = root.resolve("src/main/java/de/x/" + name + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package de.x;\npublic class " + name + " {}");
    }

    @Test
    void proposesTheSourceWithTheMostTemplates(@TempDir Path tmp) throws Exception {
        Path sparse = tmp.resolve("sparse");
        write(sparse, "RisikoTO");
        Path rich = tmp.resolve("rich");
        for (String name : List.of("VertragTO", "VertragReader", "VertragMapper", "VertragWriter")) {
            write(rich, name);
        }
        var proposal = StandardsProposal.best(List.of(sparse, rich)).orElseThrow();
        assertEquals(rich, proposal.source());
        assertEquals("Vertrag", proposal.preview().entity());
        assertEquals(4, proposal.preview().templates().size());
    }

    @Test
    void earlierSourceWinsOnEqualScore(@TempDir Path tmp) throws Exception {
        Path first = tmp.resolve("first");
        write(first, "RisikoTO");
        Path second = tmp.resolve("second");
        write(second, "VertragTO");
        assertEquals(first, StandardsProposal.best(List.of(first, second)).orElseThrow().source());
        assertEquals(second, StandardsProposal.best(List.of(second, first)).orElseThrow().source());
    }

    @Test
    void skipsMissingAndUnsuitableSources(@TempDir Path tmp) throws Exception {
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        assertTrue(StandardsProposal.best(Arrays.asList(null, tmp.resolve("missing"), empty)).isEmpty());
        try (var content = Files.list(empty)) {
            assertEquals(0, content.count(), "the scan must not write");
        }
    }
}
