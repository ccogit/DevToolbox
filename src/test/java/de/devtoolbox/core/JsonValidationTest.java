package de.devtoolbox.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonValidationTest {

    @Test
    void rejectsMalformedUnquotedLiterals() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"key\": missing}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"key\": +1}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"key\": 01}"));
    }

    @Test
    void parsesLegalNumbersAndBooleans() {
        assertEquals("-1.5e+2", Json.parse("{\"key\": -1.5e+2}").get("key"));
        assertEquals("false", Json.parse("{\"key\": false}").get("key"));
    }

    @Test
    void reportsMalformedStringEscapesConsistently() {
        String prefix = "{\"key\":\"";
        assertThrows(IllegalArgumentException.class, () -> Json.parse(prefix + "\\"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(prefix + "\\q\"}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(prefix + "\\u12\"}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(prefix + "\n\"}"));
    }
}
