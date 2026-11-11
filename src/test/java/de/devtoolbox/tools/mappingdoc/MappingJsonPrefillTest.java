package de.devtoolbox.tools.mappingdoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MappingJsonPrefillTest {

    @Test
    void updatesOnlyAttributeFieldsOfSelectedEntity() {
        String json = """
                [
                  {
                    "entityTypeName": "Risiko",
                    "status": "entity-status-must-stay",
                    "typeAttributeDocumentationList": [
                      {"attributeName":"A","specification":["old"],"status":["open"],
                       "metadata":{"status":"nested-must-stay"}}
                    ]
                  },
                  {"entityTypeName":"Vertrag",
                   "typeAttributeDocumentationList":[
                     {"attributeName":"B","specification":["verbatim"],"status":["verbatim"]}
                   ]}
                ]
                """;
        MappingJson model = new MappingJson(null, "Risiko", json, List.of("A"));
        String result = model.withAll(Map.of("specification", "{jiraissues:HAFT-7}",
                "status", "{status:colour=Green|title=FERTIG}")).text();
        assertTrue(result.contains("\"status\": \"entity-status-must-stay\""), result);
        assertTrue(result.contains("\"status\":\"nested-must-stay\""), result);
        assertTrue(result.contains("\"specification\":[\"verbatim\"],\"status\":[\"verbatim\"]"), result);
        assertTrue(result.contains("\"specification\":[ \"{jiraissues:HAFT-7}\" ]"), result);
        assertTrue(result.contains("\"status\":[ \"{status:colour=Green|title=FERTIG}\" ]"), result);
        assertTrue(result.contains("\"attributeName\":\"A\""), result);
    }

    @Test
    void createsMissingStatusAndSpecificationInAttributeObject() {
        String original = """
                {"entityTypeName":"Risiko","typeAttributeDocumentationList":[
                  {"attributeName":"A","mapping":"preserved"}
                ]}
                """;
        String updated = new MappingJson(null, "Risiko", original, List.of("A"))
                .withAll(Map.of("specification", "{jiraissues:HAFT-1}", "status", "FERTIG")).text();
        assertTrue(updated.contains("\"mapping\":\"preserved\""), updated);
        assertTrue(updated.contains("\"specification\" : [ \"{jiraissues:HAFT-1}\" ]"), updated);
        assertTrue(updated.contains("\"status\" : [ \"FERTIG\" ]"), updated);
        assertEquals(1, updated.split("\"attributeName\"", -1).length - 1);
    }

    @Test
    void stringContentContainingBracesDoesNotBreakObjectScopes() {
        String json = """
                {"entityTypeName":"Risiko","typeAttributeDocumentationList":[
                   {"attributeName":"A","mapping":"{nested: text with } and { braces}",
                    "status":null}
                ]}
                """;
        String updated = new MappingJson(null, "Risiko", json, List.of("A"))
                .with("status", "FERTIG");
        assertTrue(updated.contains("\"mapping\":\"{nested: text with } and { braces}\""), updated);
        assertTrue(updated.contains("\"status\":\"FERTIG\""), updated);
    }
}
