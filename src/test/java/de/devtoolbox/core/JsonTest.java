package de.devtoolbox.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parsesScalarsAndArrays() {
        Map<String, String> m = Json.parse("""
                {"a": "x", "b": true, "c": 12, "d": null, "e": ["READ", "WRITE"], "f": [], "g": "\\u00e4\\n\\"q\\""}
                """);
        assertEquals("x", m.get("a"));
        assertEquals("true", m.get("b"));
        assertEquals("12", m.get("c"));
        assertEquals("", m.get("d"));
        assertEquals("READ, WRITE", m.get("e"));
        assertEquals("", m.get("f"));
        assertEquals("ä\n\"q\"", m.get("g"));
    }

    @Test
    void ignoresBom() {
        assertEquals("1", Json.parse("﻿{\"a\": 1}").get("a"));
    }

    @Test
    void roundTrip() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("text", "Zeile 1\nZeile \"2\"\t\\ Ümlaut ß");
        in.put("flag", true);
        in.put("n", 3);
        in.put("liste", List.of("A", "B"));
        in.put("leer", "");
        Map<String, String> out = Json.parse(Json.write(in));
        assertEquals("Zeile 1\nZeile \"2\"\t\\ Ümlaut ß", out.get("text"));
        assertEquals("true", out.get("flag"));
        assertEquals("3", out.get("n"));
        assertEquals("A, B", out.get("liste"));
        assertEquals("", out.get("leer"));
        assertEquals(List.copyOf(in.keySet()), List.copyOf(out.keySet()), "order kept");
    }

    @Test
    void errors() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": 1} x"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": {\"b\": 1}}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": \"offen}"));
        assertTrue(Json.parse("{}").isEmpty());
    }

    @Test
    void configLayersAndAccessors() {
        List<Param> params = List.of(Param.text("a", "A", "def", true, ""), Param.flag("f", "F", true, ""),
                Param.text("n", "N", "1.000", false, ""));
        Config c = Config.of(params, Map.of("a", "layer1"), Map.of("a", " layer2 "));
        assertEquals("layer2", c.text("a"));
        assertTrue(c.flag("f"));
        assertEquals(1000, c.number("n"));
        assertEquals(List.of("x", "y"), Config.split("x; y,, "));
        assertTrue(Config.of(params).missing(params).isEmpty());
        assertEquals(List.of("A"), Config.of(params, Map.of("a", " ")).missing(params));
        assertNull(c.path("none"));
    }
}
