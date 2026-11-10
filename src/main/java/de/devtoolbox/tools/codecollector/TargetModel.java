package de.devtoolbox.tools.codecollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The target model of an entity: one JSON file per entity in the project's target model folder. Only the attribute
 * names matter – the values of the keys {@code attributname} / {@code attributeName} (any case, any nesting level);
 * all other content is ignored, so the file format may change without breaking this.
 */
public final class TargetModel {

    /** {@code file} is null if no file matches the entity. */
    public record Result(Path file, List<String> attributes, List<Path> otherCandidates) {
    }

    private static final Pattern ATTRIBUTE_NAME =
            Pattern.compile("\"(?i:attribute?_?name)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private TargetModel() {
    }

    /**
     * The phase Process needs the target model: fails with a message for the user if {@code folder} is not given or
     * is no existing folder.
     */
    public static void requireFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException(folder == null
                    ? "Für die Phase Process ist das Zielmodell (Ordner mit einer JSON-Datei je Entität) nötig."
                    : "Zielmodell-Ordner nicht gefunden: " + folder);
        }
    }

    /** All JSON files of the folder (one per entity). */
    public static List<Path> files(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(folder)) {
            return list.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted().toList();
        }
    }

    /**
     * The file whose name is the entity or one of its search terms (exact match first, then "contains"), and its
     * attribute names in file order, without duplicates.
     */
    public static Result find(Path folder, List<String> terms) throws IOException {
        List<Path> files = files(folder);
        List<Path> exact = new ArrayList<>();
        List<Path> partial = new ArrayList<>();
        for (Path f : files) {
            String name = f.getFileName().toString().replaceFirst("(?i)\\.json$", "").toLowerCase(Locale.ROOT);
            if (terms.stream().anyMatch(t -> name.equals(t.toLowerCase(Locale.ROOT)))) {
                exact.add(f);
            } else if (terms.stream().anyMatch(t -> name.contains(t.toLowerCase(Locale.ROOT)))) {
                partial.add(f);
            }
        }
        List<Path> candidates = new ArrayList<>(exact);
        candidates.addAll(partial);
        if (candidates.isEmpty()) {
            return new Result(null, List.of(), List.of());
        }
        Path file = candidates.getFirst();
        return new Result(file, attributes(Files.readString(file)), candidates.subList(1, candidates.size()));
    }

    static List<String> attributes(String json) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = ATTRIBUTE_NAME.matcher(json);
        while (m.find()) {
            String name = m.group(1).replace("\\\"", "\"").strip();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }
}
