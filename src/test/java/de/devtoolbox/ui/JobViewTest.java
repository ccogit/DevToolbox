package de.devtoolbox.ui;

import de.devtoolbox.core.Keys;
import de.devtoolbox.core.Param;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JobViewTest {

    private static final List<Param> PARAMS = List.of(
            Param.directory(Keys.BASE, "Referenzprojekt (Code)", true, ""),
            Param.directory(Keys.DDL, "Struktur der Altdaten (DDL)", false, ""),
            Param.text(Keys.TERMS, "Fachliche Entität", "", true, ""));

    @Test
    void valuesOfTheProjectAreNotAskedAgain() {
        Map<String, String> project = Map.of(Keys.BASE, "C:\\code\\referenz", Keys.DDL, "C:\\code\\ddl");
        Map<String, String> form = Map.of(Keys.BASE, "C:\\code\\referenz", Keys.DDL, " C:\\code\\ddl ", Keys.TERMS, "");
        assertEquals(Set.of(Keys.BASE, Keys.DDL), JobView.providedByProject(PARAMS, project, form::get));
    }

    @Test
    void overriddenValueStaysVisible() {
        Map<String, String> project = Map.of(Keys.BASE, "C:\\code\\referenz");
        Map<String, String> form = Map.of(Keys.BASE, "C:\\code\\andere-referenz", Keys.DDL, "", Keys.TERMS, "");
        assertEquals(Set.of(), JobView.providedByProject(PARAMS, project, form::get));
    }

    @Test
    void projectWithoutValueLeavesTheFieldToFillIn() {
        Map<String, String> form = Map.of(Keys.BASE, "", Keys.DDL, "", Keys.TERMS, "Vertrag");
        assertEquals(Set.of(), JobView.providedByProject(PARAMS, Map.of(), form::get));
        // a value of the project for a field this job does not have is ignored
        assertEquals(Set.of(), JobView.providedByProject(PARAMS, Map.of(Keys.TARGET, "C:\\code"), form::get));
    }
}
