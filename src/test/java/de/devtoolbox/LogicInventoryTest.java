package de.devtoolbox;

import de.devtoolbox.core.Texts;
import de.devtoolbox.jobs.phasespec.LogicInventory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The target list "Soll Logik" counted from a code file. */
class LogicInventoryTest {

    private static final String MAPPER = """
            package de.x;

            /** if (a) { throw } in a comment does not count */
            public class ArtMapper {
                private static final Set<Integer> NICHT_MIGRIEREN = Set.of(163, 605, 620);
                private static final Logger LOG = LoggerFactory.getLogger(ArtMapper.class);
                private final Service service;

                public ArtMapper(Service service) {
                    this.service = service;
                }

                @Override
                public Art mappe(Alt alt) throws BusinessException {
                    if (alt.tarifgeneration() < 2008) {
                        return Art.UNDEFINIERT;
                    } else if (NICHT_MIGRIEREN.contains(alt.art())) {
                        return null;
                    }
                    String text = "kein { Treffer } fuer if";
                    return switch (alt.code()) {
                        case "FE" -> Art.FEUER;
                        case "LW" -> Art.WASSER;
                        default -> throw new BusinessException(text);
                    };
                }

                public BigDecimal prozent(BigDecimal wert) {
                    return wert.divide(BigDecimal.valueOf(100));
                }

                public String name() {
                    return service.name();
                }

                enum Art { FEUER("F"), WASSER("W"), UNDEFINIERT("U"); private final String code; Art(String c) { code = c; } }
            }
            """;

    private static LogicInventory inventory() {
        return LogicInventory.of("# Code\n\n" + Texts.fileBlock("src/ArtMapper.java", "java", MAPPER)
                + Texts.fileBlock("src/job.xml", "xml", "<job id=\"if\"/>"));
    }

    @Test
    void countsBranchesErrorsAndValuesPerMethod() {
        LogicInventory inv = inventory();
        assertEquals(2, inv.files());
        assertEquals(List.of("ArtMapper.mappe", "ArtMapper.prozent"),
                inv.methods().stream().map(LogicInventory.Method::key).toList());
        LogicInventory.Method mappe = inv.methods().getFirst();
        assertEquals(5, mappe.branches()); // if, else if, case, case, default
        assertEquals(1, mappe.errors());
        assertEquals(List.of("2008", "\"FE\"", "\"LW\"", "NICHT_MIGRIEREN"), mappe.values());
        assertEquals(List.of("100"), inv.methods().get(1).values());
    }

    @Test
    void listsConstantsAndEnumValuesButNoLogger() {
        List<LogicInventory.Constant> constants = inventory().constants();
        assertEquals(List.of("NICHT_MIGRIEREN", "(Enum-Werte)"), constants.stream().map(LogicInventory.Constant::name).toList());
        assertEquals("Set.of(163, 605, 620)", constants.getFirst().value());
        assertEquals("FEUER(\"F\"), WASSER(\"W\"), UNDEFINIERT(\"U\")", constants.get(1).value());
    }

    @Test
    void markdownNamesNumbersAndPlainMethods() {
        String md = inventory().markdown();
        assertTrue(md.startsWith("### Soll Logik – 2 Methoden, 5 Verzweigungen, 1 Fehlerfälle"), md);
        assertTrue(md.contains("| L1 | `ArtMapper.mappe` | 5 | 1 | `2008, \"FE\", \"LW\", NICHT_MIGRIEREN` |"), md);
        assertTrue(md.contains("Weitere 3 Methoden"), md); // constructor, name(), enum constructor
        assertTrue(md.contains("| K1 | `ArtMapper.NICHT_MIGRIEREN` | `Set.of(163, 605, 620)` |"), md);
    }

    @Test
    void stagesHaveAboutTheSameWeight() {
        LogicInventory inv = inventory(); // weights 7 and 1
        assertEquals(1, inv.stages(30).size());
        assertArrayEquals(new int[]{1, 2}, inv.stages(30).getFirst());
        List<int[]> two = inv.stages(4);
        assertEquals(2, two.size());
        assertArrayEquals(new int[]{1, 1}, two.getFirst());
        assertArrayEquals(new int[]{2, 2}, two.get(1));
        assertTrue(LogicInventory.of("kein Code").isEmpty());
    }
}
