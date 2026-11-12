package de.devtoolbox.tools.speccheck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MdTableTest {

    @Test
    void splitsAtUnescapedPipesOnly() {
        assertEquals(List.of("a", "b | c", "", "d"), MdTable.splitRow("| a | b \\| c |  | d |"));
    }

    @Test
    void parsesTablesAndPadsShortRows() {
        List<MdTable> tables = MdTable.parseAll(List.of("Text", "| Nr | Attribut (Schlüssel) | Beleg |", "|---|:--:|---|",
                "| R1 | X.Y |", "", "| A |", "|---|", "| 1 |"));
        assertEquals(2, tables.size());
        MdTable t = tables.getFirst();
        assertEquals(1, t.col("attribut"));
        assertEquals(0, t.colExact("nr"));
        assertEquals(-1, t.colExact("schlüssel"), "parenthetical is not part of the name");
        assertEquals(2, t.colExact("beleg"));
        assertEquals(List.of("R1", "X.Y", ""), t.rows().getFirst());
    }

    @Test
    void ignoresPipeLinesWithoutSeparator() {
        assertTrue(MdTable.parseAll(List.of("| a | b |", "| c | d |")).isEmpty());
    }

    @Test
    void rendersWithEscapedPipes() {
        MdTable t = new MdTable(List.of("A", "B"), List.of(List.of("x|y", "z")));
        assertEquals("| A | B |\n|---|---|\n| x\\|y | z |", t.render());
        assertEquals(t.rows(), MdTable.parseAll(t.render().lines().toList()).getFirst().rows());
    }
}
