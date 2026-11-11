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

    /** Format of the real project: entityTypeName + typeAttributeDocumentationList, file name irrelevant. */
    @Test
    void entityTypeNameFormat(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("export-01.json"), """
                {
                "entityTypeName" : "NachlassZuschlag",
                "typeAttributeDocumentationList" : [ {
                "attributeName" : "DiffStatus",
                "mapping" : "{status:colour=Red|title=TBD|subtle=true}",
                "schwebeMapping" : "{status:colour=Red|title=TBD|subtle=true}",
                "specification" : [ "{status:colour=Red|title=JIRA|subtle=true}" ],
                "status" : [ "{status:colour=Red|title=TBD|subtle=true}" ]
                }, {
                "attributeName" : "NachlassZuschlagArt",
                "mapping" : "T951V0.BSZ_ZUS_RAB_GRUND",
                "schwebeMapping" : "",
                "specification" : [ ],
                "status" : [ "{status:colour=Green|title=OK|subtle=true}" ]
                } ]
                }
                """);
        Files.writeString(tmp.resolve("export-02.json"), """
                [ {"entityTypeName": "Nachlass", "typeAttributeDocumentationList": [{"attributeName": "X"}]},
                  {"entityTypeName": "Zuschlag", "typeAttributeDocumentationList": [{"attributeName": "Y"}]} ]
                """);
        assertEquals(3, TargetModel.entityCount(tmp));

        var r = TargetModel.find(tmp, List.of("NachlassZuschlag"));
        assertEquals("export-01.json", r.file().getFileName().toString());
        assertEquals("NachlassZuschlag", r.entity());
        assertEquals(List.of("DiffStatus", "NachlassZuschlagArt"), r.attributes());
        assertEquals("mapping: TBD · schwebeMapping: TBD · specification: JIRA · status: TBD", r.details().get("DiffStatus"));
        assertEquals("mapping: T951V0.BSZ_ZUS_RAB_GRUND · status: OK", r.details().get("NachlassZuschlagArt"));
        assertTrue(r.otherCandidates().isEmpty(), "Nachlass/Zuschlag do not contain the term");

        var z = TargetModel.find(tmp, List.of("Zuschlag"));
        assertEquals("Zuschlag", z.entity(), "exact entity name before partial match");
        assertEquals(List.of("Y"), z.attributes());
        assertEquals(1, z.otherCandidates().size());
    }

    @Test
    void noMatchOrNoFolder(@TempDir Path tmp) throws Exception {
        assertNull(TargetModel.find(tmp, List.of("Risiko")).file());
        assertNull(TargetModel.find(tmp.resolve("gibtsnicht"), List.of("Risiko")).file());
        assertNull(TargetModel.find(null, List.of("Risiko")).file());
    }
}
