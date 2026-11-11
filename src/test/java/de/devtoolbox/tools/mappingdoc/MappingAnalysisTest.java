package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.tools.codecollector.CodeCollectorTool.SourceFile;
import de.devtoolbox.tools.codecollector.Phase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MappingAnalysisTest {

    private static final String MAPPER = """
            package de.x.mapping;

            /** Mappt den Zuschlag. Schwebe wird im Kommentar erwaehnt und ignoriert. */
            public class ZuschlagMapper {

                public Zuschlag mappe(AltZuschlagTO to) {
                    Zuschlag z = new Zuschlag();
                    z.setBetrag(to.getBetrag());
                    z.setArt(umschluesseln(to.getArt()));
                    return z;
                }

                public Zuschlag mappeSchwebe(AltZuschlagTO to) {
                    Zuschlag z = mappe(to);
                    z.setArt("SCHWEBEND");
                    return z;
                }

                private String umschluesseln(String art) {
                    if (art == null) {
                        return null;
                    }
                    String result = art.equals("Z") ? "ZUSCHLAG" : "NACHLASS";
                    return result;
                }
            }
            """;

    @Test
    void locationsPerAttributeAndSchwebeContext() {
        var files = List.of(new SourceFile("src/de/x/mapping/ZuschlagMapper.java", MAPPER, Phase.PROCESS),
                new SourceFile("src/de/x/Zuschlag.java", "class Zuschlag { void setBetrag(Object b) {} }", Phase.MODEL));
        var r = MappingAnalysis.analyse(files, List.of("Betrag", "Art", "Prozent"));

        assertEquals(1, r.processFiles());
        var betrag = r.byAttribute().get("Betrag");
        assertEquals(1, betrag.size(), "model classes are not searched");
        assertEquals("ZuschlagMapper.mappe:8", betrag.getFirst().where());
        assertFalse(betrag.getFirst().schwebe());

        var art = r.byAttribute().get("Art");
        assertEquals(List.of("ZuschlagMapper.mappe:9", "ZuschlagMapper.mappeSchwebe:15"),
                art.stream().map(MappingAnalysis.Location::where).toList());
        assertEquals(List.of(false, true), art.stream().map(MappingAnalysis.Location::schwebe).toList());
        assertTrue(r.byAttribute().get("Prozent").isEmpty());
        assertEquals(2, r.attributesFound());
        assertEquals(1, r.attributesWithSchwebe());

        // comment lines are not counted; the method name and the literal are
        assertEquals(List.of("ZuschlagMapper.mappeSchwebe:13", "ZuschlagMapper.mappeSchwebe:15"),
                r.schwebeLines().stream().map(MappingAnalysis.Location::where).toList());

        String md = MappingAnalysis.markdown(r);
        assertTrue(md.contains("| 2 | Art | `ZuschlagMapper.mappe:9`, `ZuschlagMapper.mappeSchwebe:15` | ja (1 von 2) |"), md);
        assertTrue(md.contains("| 3 | Prozent | – keine gefunden | – |"), md);
    }

    @Test
    void assignmentPatterns() {
        var p = MappingAnalysis.assignment("NachlassZuschlagArt");
        assertTrue(p.matcher("x.setNachlassZuschlagArt(a);").find());
        assertTrue(p.matcher(".nachlassZuschlagArt(a)").find());
        assertTrue(p.matcher("this.nachlassZuschlagArt = a;").find());
        assertFalse(p.matcher("if (nachlassZuschlagArt == null)").find());
        assertFalse(p.matcher("x.getNachlassZuschlagArt()").find());
        assertFalse(p.matcher("x.setNachlassZuschlagArtNeu(a)").find());
    }
}
