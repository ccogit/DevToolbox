package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MappingTemplateTest {

    private static final Path VORLAGEN = TestSupport.ZIEL.resolve("mapping-vorlagen");

    /** Format of the real project: Confluence table, entity only in the first row, rows wrapped inside macros. */
    @Test
    void confluenceTableWithWrappedRows() throws Exception {
        MappingTemplate t = MappingTemplate.all(VORLAGEN).getFirst();
        assertEquals(List.of("Entität", "Attribut", "Mapping", "Schwebe Mapping", "Spezifikation", "Status"), t.header());
        assertEquals(List.of("Vertrag"), t.entities());
        assertEquals(List.of("vertragsnummer", "produkt", "beginn", "kuendigungsgrund"), t.attributes());
        assertEquals(2, t.mappingColumn());
        assertEquals(3, t.schwebeColumn());
        MappingTemplate.Row first = t.rows().getFirst();
        assertEquals(6, first.cells().size());
        assertEquals("{status:colour=Red|title=TBD|subtle=true}", first.cells().get(3), "wrapped macro joined");
        assertEquals("{status:colour=Red|title=JIRA|subtle=true}", t.rows().get(1).cells().get(4));
        assertEquals("Vertrag", t.rows().get(2).entity(), "empty entity cell continues the entity above");
        assertTrue(t.text().startsWith("Mapping Export:"), "text is kept unchanged for the AI");
    }

    @Test
    void findByEntityInTableBeforeFileName(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("export-17.txt"), """
                ||Entität||Attribut||Mapping||Schwebe Mapping||
                |NachlassZuschlag|DiffStatus|x|y|
                """);
        Files.writeString(tmp.resolve("Zuschlag.txt"), """
                ||Entität||Attribut||Mapping||Schwebe Mapping||
                |Zuschlag|Betrag|x|y|
                """);
        Files.writeString(tmp.resolve("notizen.txt"), "keine Tabelle");
        assertEquals(2, MappingTemplate.all(tmp).size());

        var found = MappingTemplate.find(tmp, List.of("NachlassZuschlag"));
        assertEquals("export-17.txt", found.template().file().getFileName().toString());
        assertTrue(found.otherCandidates().isEmpty());

        var zuschlag = MappingTemplate.find(tmp, List.of("Zuschlag"));
        assertEquals("Zuschlag.txt", zuschlag.template().file().getFileName().toString());
        assertEquals(1, zuschlag.otherCandidates().size(), "NachlassZuschlag contains the term");

        assertNull(MappingTemplate.find(tmp, List.of("Risiko")).template());
        assertNull(MappingTemplate.find(tmp.resolve("fehlt"), List.of("Risiko")).template());
    }

    @Test
    void cellsKeepMacrosLinksAndEscapedPipes() {
        assertEquals(List.of("a", "{status:colour=Red|title=TBD}", "[Text|http://x]", "b \\| c", ""),
                MappingTemplate.cells("|a|{status:colour=Red|title=TBD}|[Text|http://x]|b \\| c| |"));
    }
}
