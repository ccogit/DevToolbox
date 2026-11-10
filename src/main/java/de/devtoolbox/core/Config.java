package de.devtoolbox.core;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The values entered by the user, with typed accessors. */
public record Config(Map<String, String> values) {

    /** Defaults of {@code params}, overlaid by each layer in order (e.g. JSON file, then command line). */
    @SafeVarargs
    public static Config of(List<Param> params, Map<String, String>... layers) {
        Map<String, String> values = new HashMap<>();
        params.forEach(p -> values.put(p.key(), p.defaultValue()));
        for (Map<String, String> layer : layers) {
            values.putAll(layer);
        }
        return new Config(values);
    }

    public String text(String key) {
        String value = values.get(key);
        return value == null ? "" : value.trim();
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(text(key));
    }

    public Path path(String key) {
        String value = text(key);
        return value.isEmpty() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    /** Comma- or semicolon-separated list, blanks removed. */
    public List<String> list(String key) {
        return split(text(key));
    }

    /** Positive number, or 0 if empty. */
    public int number(String key) {
        String value = text(key).replace(".", "").replace("_", "");
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Keine Zahl bei '" + key + "': " + text(key));
        }
    }

    /** Labels of required params that are still empty. */
    public List<String> missing(List<Param> params) {
        return params.stream()
                .filter(p -> p.required() && text(p.key()).isEmpty())
                .map(Param::label)
                .toList();
    }

    public static List<String> split(String value) {
        return Arrays.stream(value.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
