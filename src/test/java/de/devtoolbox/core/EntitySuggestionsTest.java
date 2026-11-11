package de.devtoolbox.core;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntitySuggestionsTest {

    private String before;

    @BeforeEach
    void setUp(@TempDir Path projekte) {
        before = System.getProperty("devtoolbox.projekte");
        System.setProperty("devtoolbox.projekte", projekte.toString());
    }

    @AfterEach
    void tearDown() {
        if (before == null) {
            System.clearProperty("devtoolbox.projekte");
        } else {
            System.setProperty("devtoolbox.projekte", before);
        }
    }

    private static void project(String id, String json) throws Exception {
        Path folder = Files.createDirectories(Path.of(System.getProperty("devtoolbox.projekte")).resolve(id));
        Files.writeString(folder.resolve("projekt.json"), json);
    }

    @Test
    void targetModelFitsExactly() {
        var s = EntitySuggestions.suggest(TestSupport.ZIELMODELL, TestSupport.TESTPROJEKT, null);
        assertEquals(EntitySuggestions.Source.TARGET_MODEL, s.source());
        assertTrue(s.names().containsAll(List.of("Risiko", "Vertrag")), s.names().toString());
    }

    @Test
    void referenceCodeWithoutPrefixesAndSuffixes() {
        var s = EntitySuggestions.suggest(null, TestSupport.TESTPROJEKT, null);
        assertEquals(EntitySuggestions.Source.REFERENCE, s.source());
        assertTrue(s.names().contains("Risiko"), s.names().toString());
        assertTrue(s.names().stream().noneMatch(n -> n.startsWith("Alt") || n.startsWith("Neu")
                || n.endsWith("Dto") || n.endsWith("Entity")), s.names().toString());
        assertEquals(s.names().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), s.names());
    }

    @Test
    void otherProjectsWhenNothingElseIsGiven() throws Exception {
        project("a", "{\"name\":\"A\",\"entitaeten\":\"Zeta, Alpha\"}");
        project("b", "{\"name\":\"B\",\"entitaeten\":\"Alpha, Beta\"}");
        project("c", "{\"name\":\"C\",\"entitaeten\":\"Nur-C\"}");
        var s = EntitySuggestions.suggest(null, null, "c");
        assertEquals(EntitySuggestions.Source.OTHER_PROJECTS, s.source());
        assertEquals(List.of("Alpha", "Beta", "Zeta"), s.names());
    }

    @Test
    void nothingToDeriveFrom() {
        var s = EntitySuggestions.suggest(null, null, null);
        assertEquals(EntitySuggestions.Source.NONE, s.source());
        assertTrue(s.names().isEmpty());
    }

    @Test
    void storedListRoundTripAndChoices() throws Exception {
        project("x", "{\"name\":\"X\",\"entitaeten\":\"Risiko, NachlassZuschlag ,Risiko\"}");
        project("y", "{\"name\":\"Y\",\"zielmodell\":\"" + TestSupport.ZIELMODELL.toString().replace('\\', '/') + "\"}");
        MigrationProject x = MigrationProject.find("x").orElseThrow();
        assertEquals(List.of("Risiko", "NachlassZuschlag"), x.entities());
        assertEquals(x.entities(), x.entityChoices());
        MigrationProject y = MigrationProject.find("y").orElseThrow();
        assertTrue(y.entities().isEmpty());
        assertTrue(y.entityChoices().contains("Vertrag"), "falls back to the target model");
    }
}
