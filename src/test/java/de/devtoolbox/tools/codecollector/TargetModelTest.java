package de.devtoolbox.tools.codecollector;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetModelTest {

    @Test
    void realModel() throws Exception {
        var r = TargetModel.find(TestSupport.ZIELMODELL, List.of("Risiko", "Risk"));
        assertEquals("Risiko.json", r.file().getFileName().toString());
        assertEquals(11, r.attributes().size());
        assertEquals("risikoId", r.attributes().getFirst());
        assertTrue(r.attributes().containsAll(List.of("lage.strasse", "lage.plz", "lage.ort", "versicherungssumme")));
        assertTrue(r.otherCandidates().isEmpty());
        assertEquals(2, TargetModel.files(TestSupport.ZIELMODELL).size());
    }

    @Test
    void keyVariantsNestingAndDuplicates(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("Foo.json"), """
                {"attribute": [
                  {"attributName": "a", "kinder": [{"attributeName": "b"}, {"ATTRIBUTNAME": "c"}]},
                  {"attributname": "a"},
                  {"attributName": "  "},
                  {"attribute_name": "d"},
                  {"andererKey": "ignored"}
                ]}
                """);
        var r = TargetModel.find(tmp, List.of("foo"));
        assertEquals(List.of("a", "b", "c", "d"), r.attributes());
    }

    @Test
    void exactMatchBeforePartial(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("RisikoDeckung.json"), "{\"attributName\": \"x\"}");
        Files.writeString(tmp.resolve("Risiko.json"), "{\"attributName\": \"y\"}");
        var r = TargetModel.find(tmp, List.of("Risiko"));
        assertEquals("Risiko.json", r.file().getFileName().toString());
        assertEquals(List.of("y"), r.attributes());
        assertEquals(1, r.otherCandidates().size());
    }

    @Test
    void noMatchOrNoFolder(@TempDir Path tmp) throws Exception {
        assertNull(TargetModel.find(tmp, List.of("Risiko")).file());
        assertNull(TargetModel.find(tmp.resolve("gibtsnicht"), List.of("Risiko")).file());
        assertNull(TargetModel.find(null, List.of("Risiko")).file());
    }
}
